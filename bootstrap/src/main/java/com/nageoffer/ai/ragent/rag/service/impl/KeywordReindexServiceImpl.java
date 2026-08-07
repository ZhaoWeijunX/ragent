/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.knowledge.enums.DocumentStatus;
import com.nageoffer.ai.ragent.rag.controller.request.KeywordReindexRequest;
import com.nageoffer.ai.ragent.rag.controller.vo.KeywordReindexCreatedVO;
import com.nageoffer.ai.ragent.rag.controller.vo.KeywordReindexJobVO;
import com.nageoffer.ai.ragent.rag.core.keyword.KeywordIndexService;
import com.nageoffer.ai.ragent.rag.core.keyword.model.KeywordIndexDocument;
import com.nageoffer.ai.ragent.rag.enums.KeywordReindexJobStatus;
import com.nageoffer.ai.ragent.rag.service.KeywordReindexService;
import com.nageoffer.ai.ragent.rag.service.model.KeywordReindexJob;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.keyword", name = "type", havingValue = "es")
public class KeywordReindexServiceImpl implements KeywordReindexService {

    private final KeywordIndexService keywordIndexService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    @Qualifier("keywordReindexExecutor")
    private final Executor keywordReindexExecutor;

    private final Map<String, KeywordReindexJob> jobs = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeJobId = new AtomicReference<>();

    @Override
    public synchronized KeywordReindexCreatedVO create(KeywordReindexRequest request) {
        validateRequest(request);
        String activeId = activeJobId.get();
        if (activeId != null) {
            KeywordReindexJob active = jobs.get(activeId);
            if (active != null && !isTerminal(active.getStatus())) {
                throw new ClientException("已有关键词索引回填任务正在执行: " + activeId);
            }
            activeJobId.compareAndSet(activeId, null);
        }

        String jobId = "keyword-reindex-" + IdUtil.getSnowflakeNextIdStr();
        int batchSize = request.getBatchSize() == null ? 500 : request.getBatchSize();
        KeywordReindexJob job = new KeywordReindexJob(
                jobId,
                trimToNull(request.getKnowledgeBaseId()),
                trimToNull(request.getDocumentId()),
                batchSize);
        jobs.put(jobId, job);
        activeJobId.set(jobId);
        try {
            keywordReindexExecutor.execute(() -> run(job));
        } catch (RejectedExecutionException e) {
            activeJobId.compareAndSet(jobId, null);
            job.setStatus(KeywordReindexJobStatus.FAILED);
            job.setErrorMessage("关键词索引回填执行器繁忙");
            throw new ClientException("关键词索引回填执行器繁忙");
        }
        return new KeywordReindexCreatedVO(jobId, job.getStatus().name());
    }

    @Override
    public KeywordReindexJobVO get(String jobId) {
        KeywordReindexJob job = jobs.get(jobId);
        if (job == null) {
            throw new ClientException("关键词索引回填任务不存在: " + jobId);
        }
        return job.toVO();
    }

    private void run(KeywordReindexJob job) {
        job.setStatus(KeywordReindexJobStatus.RUNNING);
        try {
            List<KnowledgeDocumentDO> documents = loadDocuments(job);
            job.getTotalDocuments().set(documents.size());
            for (KnowledgeDocumentDO document : documents) {
                processDocument(job, document);
            }
            if (job.getFailedDocuments().get() == 0) {
                job.setStatus(KeywordReindexJobStatus.SUCCESS);
            } else if (job.getSuccessDocuments().get() == 0) {
                job.setStatus(KeywordReindexJobStatus.FAILED);
            } else {
                job.setStatus(KeywordReindexJobStatus.PARTIAL_SUCCESS);
            }
        } catch (Exception e) {
            job.setStatus(KeywordReindexJobStatus.FAILED);
            job.setErrorMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            activeJobId.compareAndSet(job.getJobId(), null);
        }
    }

    private List<KnowledgeDocumentDO> loadDocuments(KeywordReindexJob job) {
        LambdaQueryWrapper<KnowledgeDocumentDO> query = new LambdaQueryWrapper<KnowledgeDocumentDO>()
                .eq(KnowledgeDocumentDO::getEnabled, 1)
                .ne(KnowledgeDocumentDO::getStatus, DocumentStatus.RUNNING.getCode())
                .orderByAsc(KnowledgeDocumentDO::getId);
        if (StringUtils.hasText(job.getDocumentId())) {
            query.eq(KnowledgeDocumentDO::getId, job.getDocumentId());
        } else if (StringUtils.hasText(job.getKnowledgeBaseId())) {
            query.eq(KnowledgeDocumentDO::getKbId, job.getKnowledgeBaseId());
        }
        return knowledgeDocumentMapper.selectList(query);
    }

    private void processDocument(KeywordReindexJob job, KnowledgeDocumentDO document) {
        try {
            KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectById(document.getKbId());
            if (knowledgeBase == null || !StringUtils.hasText(knowledgeBase.getCollectionName())) {
                throw new ClientException("知识库不存在或缺少 collectionName");
            }

            List<KnowledgeChunkDO> chunks = knowledgeChunkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunkDO>()
                            .eq(KnowledgeChunkDO::getDocId, document.getId())
                            .eq(KnowledgeChunkDO::getEnabled, 1)
                            .orderByAsc(KnowledgeChunkDO::getId));
            job.getTotalChunks().addAndGet(chunks.size());

            keywordIndexService.deleteDocumentIndex(knowledgeBase.getCollectionName(), document.getId());
            for (int from = 0; from < chunks.size(); from += job.getBatchSize()) {
                int to = Math.min(from + job.getBatchSize(), chunks.size());
                List<KeywordIndexDocument> indexDocuments = new ArrayList<>(to - from);
                for (KnowledgeChunkDO chunk : chunks.subList(from, to)) {
                    indexDocuments.add(new KeywordIndexDocument(
                            chunk.getId(),
                            knowledgeBase.getCollectionName(),
                            document.getId(),
                            chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex(),
                            chunk.getContent()));
                }
                keywordIndexService.indexRawChunks(indexDocuments);
                job.getIndexedChunks().addAndGet(indexDocuments.size());
            }
            job.getSuccessDocuments().incrementAndGet();
        } catch (Exception e) {
            job.getFailedDocuments().incrementAndGet();
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            job.addFailure(document.getId() + ": " + reason);
        }
    }

    private void validateRequest(KeywordReindexRequest request) {
        if (request == null) {
            throw new ClientException("回填请求不能为空");
        }
        String kbId = trimToNull(request.getKnowledgeBaseId());
        String docId = trimToNull(request.getDocumentId());
        int batchSize = request.getBatchSize() == null ? 500 : request.getBatchSize();
        if (batchSize < 1 || batchSize > 1000) {
            throw new ClientException("batchSize 必须在 1 到 1000 之间");
        }
        if (kbId != null && knowledgeBaseMapper.selectById(kbId) == null) {
            throw new ClientException("知识库不存在: " + kbId);
        }
        if (docId != null) {
            KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(docId);
            if (document == null) {
                throw new ClientException("文档不存在: " + docId);
            }
            if (kbId != null && !kbId.equals(document.getKbId())) {
                throw new ClientException("文档不属于指定知识库: " + docId);
            }
            if (!Integer.valueOf(1).equals(document.getEnabled())) {
                throw new ClientException("文档未启用: " + docId);
            }
            if (DocumentStatus.RUNNING.getCode().equals(document.getStatus())) {
                throw new ClientException("文档正在处理中: " + docId);
            }
        }
    }

    private static boolean isTerminal(KeywordReindexJobStatus status) {
        return status == KeywordReindexJobStatus.SUCCESS
                || status == KeywordReindexJobStatus.PARTIAL_SUCCESS
                || status == KeywordReindexJobStatus.FAILED;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

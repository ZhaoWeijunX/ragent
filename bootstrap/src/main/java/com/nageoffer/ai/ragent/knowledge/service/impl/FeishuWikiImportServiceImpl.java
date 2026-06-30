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

package com.nageoffer.ai.ragent.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.mq.producer.MessageQueueProducer;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.FeishuAuthService;
import com.nageoffer.ai.ragent.knowledge.config.FeishuCredentialsProvider;
import com.nageoffer.ai.ragent.knowledge.controller.request.FeishuWikiImportRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiDiscoverVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiImportItemVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiImportJobVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiPageVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.FeishuWikiSkippedVO;
import com.nageoffer.ai.ragent.knowledge.controller.vo.KnowledgeDocumentVO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.FeishuWikiImportItemDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.FeishuWikiImportJobDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.FeishuWikiImportItemMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.FeishuWikiImportJobMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.feishu.FeishuWikiDiscoveryResult;
import com.nageoffer.ai.ragent.knowledge.feishu.FeishuWikiImportItemStatus;
import com.nageoffer.ai.ragent.knowledge.feishu.FeishuWikiImportJobStatus;
import com.nageoffer.ai.ragent.knowledge.feishu.FeishuWikiImportScope;
import com.nageoffer.ai.ragent.knowledge.feishu.FeishuWikiImportablePage;
import com.nageoffer.ai.ragent.knowledge.feishu.FeishuWikiSkippedNode;
import com.nageoffer.ai.ragent.knowledge.feishu.FeishuWikiTreeWalker;
import com.nageoffer.ai.ragent.knowledge.mq.event.FeishuWikiImportEvent;
import com.nageoffer.ai.ragent.knowledge.service.FeishuWikiImportService;
import com.nageoffer.ai.ragent.knowledge.service.FeishuWikiPageImportResult;
import com.nageoffer.ai.ragent.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuWikiImportServiceImpl implements FeishuWikiImportService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final FeishuWikiImportJobMapper jobMapper;
    private final FeishuWikiImportItemMapper itemMapper;
    private final FeishuCredentialsProvider feishuCredentialsProvider;
    private final FeishuAuthService feishuAuthService;
    private final FeishuWikiTreeWalker treeWalker;
    private final KnowledgeDocumentService documentService;
    private final MessageQueueProducer messageQueueProducer;

    @Value("feishu-wiki-import_topic${unique-name:}")
    private String importTopic;

    @Override
    public FeishuWikiDiscoverVO discover(String kbId, FeishuWikiImportRequest request) {
        validateKb(kbId);
        validateRequest(request);
        feishuCredentialsProvider.validateConfigured();

        FeishuWikiImportScope scope = FeishuWikiImportScope.normalize(request.getScope());
        Map<String, String> headers = feishuAuthService.buildAuthHeaders(feishuCredentialsProvider.resolve());
        FeishuWikiDiscoveryResult result = treeWalker.discover(request.getRootUrl().trim(), scope, headers);
        return toDiscoverVO(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeishuWikiImportJobVO startImport(String kbId, FeishuWikiImportRequest request) {
        validateKb(kbId);
        validateRequest(request);
        feishuCredentialsProvider.validateConfigured();

        FeishuWikiImportScope scope = FeishuWikiImportScope.normalize(request.getScope());
        Map<String, String> headers = feishuAuthService.buildAuthHeaders(feishuCredentialsProvider.resolve());
        FeishuWikiDiscoveryResult discovery = treeWalker.discover(request.getRootUrl().trim(), scope, headers);

        if (discovery.pages().isEmpty()) {
            throw new ClientException("未发现可导入的 docx 页面");
        }

        FeishuWikiImportJobDO job = FeishuWikiImportJobDO.builder()
                .kbId(kbId)
                .rootUrl(request.getRootUrl().trim())
                .scope(scope.name())
                .spaceId(discovery.spaceId())
                .status(FeishuWikiImportJobStatus.IMPORTING.getCode())
                .totalCount(discovery.pages().size())
                .successCount(0)
                .failedCount(0)
                .skippedCount(discovery.skipped().size())
                .autoChunk(Boolean.TRUE.equals(request.getAutoChunk()) ? 1 : 0)
                .processMode(StringUtils.hasText(request.getProcessMode()) ? request.getProcessMode() : "chunk")
                .chunkStrategy(StringUtils.hasText(request.getChunkStrategy()) ? request.getChunkStrategy() : "fixed_size")
                .chunkConfig(request.getChunkConfig())
                .pipelineId(request.getPipelineId())
                .scheduleEnabled(Boolean.TRUE.equals(request.getScheduleEnabled()) ? 1 : 0)
                .scheduleCron(StrUtil.trimToNull(request.getScheduleCron()))
                .createdBy(UserContext.getUsername())
                .build();
        jobMapper.insert(job);

        int order = 0;
        for (FeishuWikiImportablePage page : discovery.pages()) {
            FeishuWikiImportItemDO item = FeishuWikiImportItemDO.builder()
                    .jobId(job.getId())
                    .nodeToken(page.nodeToken())
                    .wikiUrl(page.wikiUrl())
                    .title(page.title())
                    .status(FeishuWikiImportItemStatus.PENDING.getCode())
                    .sortOrder(order++)
                    .build();
            itemMapper.insert(item);
        }

        messageQueueProducer.send(
                importTopic,
                job.getId(),
                "飞书Wiki批量导入",
                FeishuWikiImportEvent.builder()
                        .jobId(job.getId())
                        .operator(UserContext.getUsername())
                        .build()
        );

        return toJobVO(job);
    }

    @Override
    public void processNextItem(String jobId) {
        FeishuWikiImportJobDO job = jobMapper.selectById(jobId);
        if (job == null) {
            log.warn("飞书 Wiki 导入任务不存在, jobId={}", jobId);
            return;
        }
        if (isTerminalJobStatus(job.getStatus())) {
            return;
        }

        FeishuWikiImportItemDO item = itemMapper.selectOne(
                new LambdaQueryWrapper<FeishuWikiImportItemDO>()
                        .eq(FeishuWikiImportItemDO::getJobId, jobId)
                        .eq(FeishuWikiImportItemDO::getStatus, FeishuWikiImportItemStatus.PENDING.getCode())
                        .orderByAsc(FeishuWikiImportItemDO::getSortOrder)
                        .last("LIMIT 1")
        );

        if (item == null) {
            finalizeJob(jobId);
            return;
        }

        markItemImporting(item.getId());
        KnowledgeDocumentUploadRequest uploadRequest = buildUploadRequest(job);

        try {
            FeishuWikiPageImportResult importResult = documentService.importFeishuWikiPage(
                    job.getKbId(), item.getWikiUrl(), item.getNodeToken(), uploadRequest);
            KnowledgeDocumentVO doc = importResult.document();
            String itemStatus = importResult.updated()
                    ? FeishuWikiImportItemStatus.UPDATED.getCode()
                    : FeishuWikiImportItemStatus.SUCCESS.getCode();
            markItemDone(item.getId(), itemStatus, doc.getId(), null);
            incrementJobSuccess(jobId);

            if (job.getAutoChunk() != null && job.getAutoChunk() == 1) {
                documentService.startChunk(doc.getId());
            }
        } catch (Exception e) {
            log.warn("飞书 Wiki 页面导入失败, jobId={}, nodeToken={}", jobId, item.getNodeToken(), e);
            markItemDone(item.getId(), FeishuWikiImportItemStatus.FAILED.getCode(), null, e.getMessage());
            incrementJobFailed(jobId);
        }

        messageQueueProducer.send(
                importTopic,
                jobId,
                "飞书Wiki批量导入续链",
                FeishuWikiImportEvent.builder()
                        .jobId(jobId)
                        .operator(job.getCreatedBy())
                        .build()
        );
    }

    @Override
    public FeishuWikiImportJobVO getJob(String jobId) {
        FeishuWikiImportJobDO job = jobMapper.selectById(jobId);
        Assert.notNull(job, () -> new ClientException("导入任务不存在"));
        return toJobVO(job);
    }

    @Override
    public IPage<FeishuWikiImportItemVO> listItems(String jobId, Page<FeishuWikiImportItemVO> page) {
        Page<FeishuWikiImportItemDO> mpPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<FeishuWikiImportItemDO> result = itemMapper.selectPage(
                mpPage,
                new LambdaQueryWrapper<FeishuWikiImportItemDO>()
                        .eq(FeishuWikiImportItemDO::getJobId, jobId)
                        .orderByAsc(FeishuWikiImportItemDO::getSortOrder)
        );
        Page<FeishuWikiImportItemVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toItemVO).toList());
        return voPage;
    }

    private void finalizeJob(String jobId) {
        FeishuWikiImportJobDO job = jobMapper.selectById(jobId);
        if (job == null || isTerminalJobStatus(job.getStatus())) {
            return;
        }
        int failed = job.getFailedCount() == null ? 0 : job.getFailedCount();
        String status = failed > 0
                ? FeishuWikiImportJobStatus.PARTIAL.getCode()
                : FeishuWikiImportJobStatus.COMPLETED.getCode();
        jobMapper.update(null, new LambdaUpdateWrapper<FeishuWikiImportJobDO>()
                .set(FeishuWikiImportJobDO::getStatus, status)
                .set(FeishuWikiImportJobDO::getUpdateTime, new Date())
                .eq(FeishuWikiImportJobDO::getId, jobId));
    }

    private void markItemImporting(String itemId) {
        itemMapper.update(null, new LambdaUpdateWrapper<FeishuWikiImportItemDO>()
                .set(FeishuWikiImportItemDO::getStatus, FeishuWikiImportItemStatus.IMPORTING.getCode())
                .set(FeishuWikiImportItemDO::getUpdateTime, new Date())
                .eq(FeishuWikiImportItemDO::getId, itemId));
    }

    private void markItemDone(String itemId, String status, String docId, String errorMessage) {
        itemMapper.update(null, new LambdaUpdateWrapper<FeishuWikiImportItemDO>()
                .set(FeishuWikiImportItemDO::getStatus, status)
                .set(FeishuWikiImportItemDO::getDocId, docId)
                .set(FeishuWikiImportItemDO::getErrorMessage, errorMessage)
                .set(FeishuWikiImportItemDO::getUpdateTime, new Date())
                .eq(FeishuWikiImportItemDO::getId, itemId));
    }

    private void incrementJobSuccess(String jobId) {
        FeishuWikiImportJobDO job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        int success = (job.getSuccessCount() == null ? 0 : job.getSuccessCount()) + 1;
        jobMapper.update(null, new LambdaUpdateWrapper<FeishuWikiImportJobDO>()
                .set(FeishuWikiImportJobDO::getSuccessCount, success)
                .set(FeishuWikiImportJobDO::getUpdateTime, new Date())
                .eq(FeishuWikiImportJobDO::getId, jobId));
    }

    private void incrementJobFailed(String jobId) {
        FeishuWikiImportJobDO job = jobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        int failed = (job.getFailedCount() == null ? 0 : job.getFailedCount()) + 1;
        jobMapper.update(null, new LambdaUpdateWrapper<FeishuWikiImportJobDO>()
                .set(FeishuWikiImportJobDO::getFailedCount, failed)
                .set(FeishuWikiImportJobDO::getUpdateTime, new Date())
                .eq(FeishuWikiImportJobDO::getId, jobId));
    }

    private static boolean isTerminalJobStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return FeishuWikiImportJobStatus.COMPLETED.getCode().equals(status)
                || FeishuWikiImportJobStatus.PARTIAL.getCode().equals(status)
                || FeishuWikiImportJobStatus.FAILED.getCode().equals(status);
    }

    private KnowledgeDocumentUploadRequest buildUploadRequest(FeishuWikiImportJobDO job) {
        KnowledgeDocumentUploadRequest request = new KnowledgeDocumentUploadRequest();
        request.setSourceType("url");
        String processMode = StringUtils.hasText(job.getProcessMode()) ? job.getProcessMode() : "chunk";
        request.setProcessMode(processMode);
        request.setChunkStrategy(StringUtils.hasText(job.getChunkStrategy()) ? job.getChunkStrategy() : "fixed_size");
        request.setChunkConfig(job.getChunkConfig());
        request.setPipelineId(job.getPipelineId());
        request.setScheduleEnabled(job.getScheduleEnabled() != null && job.getScheduleEnabled() == 1);
        request.setScheduleCron(job.getScheduleCron());
        return request;
    }

    private void validateKb(String kbId) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(kbId);
        Assert.notNull(kb, () -> new ClientException("知识库不存在"));
    }

    private void validateRequest(FeishuWikiImportRequest request) {
        Assert.notNull(request, () -> new ClientException("请求不能为空"));
        if (!StringUtils.hasText(request.getRootUrl())) {
            throw new ClientException("Wiki 根链接不能为空");
        }
    }

    private FeishuWikiDiscoverVO toDiscoverVO(FeishuWikiDiscoveryResult result) {
        FeishuWikiDiscoverVO vo = new FeishuWikiDiscoverVO();
        vo.setSpaceId(result.spaceId());
        vo.setRootNodeToken(result.rootNodeToken());
        vo.setPages(result.pages().stream().map(page -> {
            FeishuWikiPageVO pageVO = new FeishuWikiPageVO();
            pageVO.setNodeToken(page.nodeToken());
            pageVO.setTitle(page.title());
            pageVO.setWikiUrl(page.wikiUrl());
            pageVO.setObjType(page.objType());
            return pageVO;
        }).toList());
        vo.setSkipped(result.skipped().stream().map(this::toSkippedVO).toList());
        return vo;
    }

    private FeishuWikiSkippedVO toSkippedVO(FeishuWikiSkippedNode node) {
        FeishuWikiSkippedVO vo = new FeishuWikiSkippedVO();
        vo.setNodeToken(node.nodeToken());
        vo.setTitle(node.title());
        vo.setObjType(node.objType());
        vo.setReason(node.reason());
        return vo;
    }

    private FeishuWikiImportJobVO toJobVO(FeishuWikiImportJobDO job) {
        FeishuWikiImportJobVO vo = BeanUtil.toBean(job, FeishuWikiImportJobVO.class);
        vo.setAutoChunk(job.getAutoChunk() != null && job.getAutoChunk() == 1);
        return vo;
    }

    private FeishuWikiImportItemVO toItemVO(FeishuWikiImportItemDO item) {
        return BeanUtil.toBean(item, FeishuWikiImportItemVO.class);
    }
}

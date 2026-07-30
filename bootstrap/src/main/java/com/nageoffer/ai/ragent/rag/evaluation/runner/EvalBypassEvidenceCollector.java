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

package com.nageoffer.ai.ragent.rag.evaluation.runner;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.rag.core.intent.IntentResolver;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrievalEngine;
import com.nageoffer.ai.ragent.rag.core.rewrite.QueryRewriteService;
import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.eval.EvalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 旁路证据采集：与 {@code GET /rag/eval} 同口径，不改 EvalController。
 */
@Component
@RequiredArgsConstructor
public class EvalBypassEvidenceCollector {

    private static final String SKIP_REASON_SYSTEM_ONLY = "SYSTEM_ONLY";

    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final RetrievalEngine retrievalEngine;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    public EvalResponse collect(String question) {
        long start = System.currentTimeMillis();
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(question, List.of());
        List<SubQuestionIntent> subIntents = intentResolver.resolve(rewriteResult);
        if (intentResolver.areAllSystemOnly(subIntents)) {
            return buildResponse(null, subIntents, System.currentTimeMillis() - start, true, SKIP_REASON_SYSTEM_ONLY);
        }
        RetrievalContext rc = retrievalEngine.retrieve(subIntents);
        return buildResponse(rc, subIntents, System.currentTimeMillis() - start, false, null);
    }

    private EvalResponse buildResponse(RetrievalContext rc,
                                       List<SubQuestionIntent> subIntents,
                                       long latencyMs,
                                       boolean retrievalSkipped,
                                       String skipReason) {
        List<RetrievedChunk> uniqueChunks = flattenChunks(rc);
        List<String> chunkIds = uniqueChunks.stream()
                .map(RetrievedChunk::getId)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        List<String> contexts = uniqueChunks.stream()
                .map(RetrievedChunk::getText)
                .collect(Collectors.toList());
        List<String> contextDocIds = resolveContextDocIds(uniqueChunks);
        List<String> docIds = dedupNonBlank(contextDocIds);

        return EvalResponse.builder()
                .retrievedDocIds(docIds)
                .retrievedChunkIds(chunkIds)
                .retrievedContexts(contexts)
                .retrievedContextDocIds(contextDocIds)
                .mcpContext(rc == null ? null : rc.getMcpContext())
                .hasMcp(rc != null && rc.hasMcp())
                .hasKb(rc != null && rc.hasKb())
                .retrievalSkipped(retrievalSkipped)
                .skipReason(skipReason)
                .subIntents(extractSubIntents(subIntents))
                .intentLeafIds(extractTopLeafIds(subIntents))
                .latencyMs(latencyMs)
                .build();
    }

    private List<RetrievedChunk> flattenChunks(RetrievalContext rc) {
        if (rc == null || CollUtil.isEmpty(rc.getIntentChunks())) {
            return Collections.emptyList();
        }
        Set<String> seen = new LinkedHashSet<>();
        return rc.getIntentChunks().values().stream()
                .filter(CollUtil::isNotEmpty)
                .flatMap(List::stream)
                .filter(c -> c != null && StrUtil.isNotBlank(c.getId()))
                .filter(c -> seen.add(c.getId()))
                .collect(Collectors.toList());
    }

    private List<String> resolveContextDocIds(List<RetrievedChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            return Collections.emptyList();
        }
        List<String> chunkIdsForLookup = chunks.stream()
                .map(RetrievedChunk::getId)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (chunkIdsForLookup.isEmpty()) {
            return new java.util.ArrayList<>(Collections.nCopies(chunks.size(), null));
        }
        Map<String, String> chunkIdToInternalDocId = knowledgeChunkMapper.selectByIds(chunkIdsForLookup).stream()
                .filter(c -> StrUtil.isNotBlank(c.getId()) && StrUtil.isNotBlank(c.getDocId()))
                .collect(Collectors.toMap(KnowledgeChunkDO::getId, KnowledgeChunkDO::getDocId, (a, b) -> a));
        List<String> internalDocIds = chunkIdToInternalDocId.values().stream().distinct().collect(Collectors.toList());
        Map<String, String> internalToBizDocId = internalDocIds.isEmpty()
                ? Map.of()
                : knowledgeDocumentMapper.selectByIds(internalDocIds).stream()
                .filter(d -> StrUtil.isNotBlank(d.getId()) && StrUtil.isNotBlank(d.getDocName()))
                .collect(Collectors.toMap(
                        KnowledgeDocumentDO::getId,
                        d -> stripExtension(d.getDocName()),
                        (a, b) -> a));
        return chunks.stream()
                .map(c -> {
                    if (StrUtil.isBlank(c.getId())) {
                        return null;
                    }
                    String internal = chunkIdToInternalDocId.get(c.getId());
                    if (StrUtil.isBlank(internal)) {
                        return null;
                    }
                    return internalToBizDocId.get(internal);
                })
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    private static String stripExtension(String docName) {
        if (docName == null) {
            return null;
        }
        int dot = docName.lastIndexOf('.');
        return (dot > 0 && dot < docName.length() - 1) ? docName.substring(0, dot) : docName;
    }

    private List<String> dedupNonBlank(List<String> in) {
        if (CollUtil.isEmpty(in)) {
            return Collections.emptyList();
        }
        Set<String> seen = new LinkedHashSet<>();
        return in.stream().filter(StrUtil::isNotBlank).filter(seen::add).collect(Collectors.toList());
    }

    private List<String> extractSubIntents(List<SubQuestionIntent> intents) {
        if (CollUtil.isEmpty(intents)) {
            return Collections.emptyList();
        }
        return intents.stream().map(SubQuestionIntent::subQuestion).filter(StrUtil::isNotBlank).collect(Collectors.toList());
    }

    private List<String> extractTopLeafIds(List<SubQuestionIntent> intents) {
        if (CollUtil.isEmpty(intents)) {
            return Collections.emptyList();
        }
        return intents.stream()
                .map(si -> {
                    if (CollUtil.isEmpty(si.nodeScores())) {
                        return null;
                    }
                    return si.nodeScores().get(0).getNode().getId();
                })
                .collect(Collectors.toList());
    }
}

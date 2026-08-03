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

package com.nageoffer.ai.ragent.rag.evaluation.support;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import com.nageoffer.ai.ragent.knowledge.enums.DocumentStatus;
import com.nageoffer.ai.ragent.rag.config.GraphProperties;
import com.nageoffer.ai.ragent.rag.config.KeywordProperties;
import com.nageoffer.ai.ragent.rag.config.RAGConfigProperties;
import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.constant.RAGConstant;
import com.nageoffer.ai.ragent.rag.dao.entity.IntentNodeDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.IntentNodeMapper;
import com.nageoffer.ai.ragent.rag.eval.EvalProperties;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalDatasetVersionDO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 创建 Run 时冻结可复现配置快照：模型档位、Embedding/Rerank、检索参数、意图树与知识库库存指纹。
 * <p>
 * 无法精确版本化的字段显式为 {@code null}，不伪造可复现性；密钥不入快照。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.eval", name = "workbench-enabled", havingValue = "true")
public class EvalConfigSnapshotSupport {

    private static final List<String> PROMPT_PATHS = List.of(
            RAGConstant.RAG_ENTERPRISE_PROMPT_PATH,
            RAGConstant.CHAT_SYSTEM_PROMPT_PATH,
            RAGConstant.QUERY_REWRITE_AND_SPLIT_PROMPT_PATH,
            RAGConstant.INTENT_CLASSIFIER_PROMPT_PATH,
            RAGConstant.ANSWER_CITATION_RULES_PROMPT_PATH,
            RAGConstant.CONTEXT_FORMAT_PATH
    );

    private final EvalProperties evalProperties;
    private final AIModelProperties aiModelProperties;
    private final SearchChannelProperties searchChannelProperties;
    private final RAGConfigProperties ragConfigProperties;
    private final RAGDefaultProperties ragDefaultProperties;
    private final KeywordProperties keywordProperties;
    private final GraphProperties graphProperties;
    private final IntentNodeMapper intentNodeMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Value("${rag.vector.type:pg}")
    private String vectorBackend;

    public Map<String, Object> build(EvalDatasetVersionDO version, int sampleCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", "1.1.0");
        snapshot.put("datasetVersionId", version.getId());
        snapshot.put("datasetVersion", version.getVersion());
        snapshot.put("datasetContentHash", version.getContentHash());
        snapshot.put("datasetPublishedAt", toIso(version.getPublishedAt()));
        snapshot.put("sampleCount", sampleCount);
        snapshot.put("recordConcurrency", evalProperties.getRecordConcurrency());
        snapshot.put("sampleTimeoutSeconds", evalProperties.getSampleTimeoutSeconds());
        snapshot.put("sampleRetryTimes", evalProperties.getSampleRetryTimes());
        snapshot.put("recordThinking", evalProperties.isRecordThinking());
        snapshot.put("evidenceSource", EvalWorkbenchConstants.EVIDENCE_DUAL_PATH);
        snapshot.put("algorithmVersion", "dual-path-recording-1.0.0");
        snapshot.put("model", snapshotChatModel());
        snapshot.put("embedding", snapshotEmbedding());
        snapshot.put("rerank", snapshotRerank());
        snapshot.put("retrieval", snapshotRetrieval());
        snapshot.put("prompt", snapshotPrompt());
        snapshot.put("intentTree", snapshotIntentTree());
        snapshot.put("knowledgeSnapshot", snapshotKnowledge());
        snapshot.put("frozenAt", Instant.now().toString());
        return snapshot;
    }

    private Map<String, Object> snapshotChatModel() {
        AIModelProperties.ModelGroup chat = aiModelProperties.getChat();
        String defaultTier = chat.getDefaultTier();
        // 评测录制固定 deepThinking=false，实际走 default-tier
        String resolvedTier = StrUtil.blankToDefault(defaultTier, "standard");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("defaultTier", defaultTier);
        out.put("deepThinkingTier", chat.getDeepThinkingTier());
        out.put("evalDeepThinking", false);
        out.put("resolvedTier", resolvedTier);
        AIModelProperties.TierConfig tier = chat.getTiers() == null ? null : chat.getTiers().get(resolvedTier);
        out.put("tierCandidates", tier == null ? List.of() : List.copyOf(tier.getCandidates()));
        out.put("tierTimeoutMs", tier == null ? null : tier.getTimeoutMs());
        out.put("registry", sanitizeCandidates(chat.getCandidates()));
        return out;
    }

    private Map<String, Object> snapshotEmbedding() {
        AIModelProperties.ModelGroup emb = aiModelProperties.getEmbedding();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("defaultModelId", emb.getDefaultModel());
        out.put("candidates", sanitizeCandidates(emb.getCandidates()));
        return out;
    }

    private Map<String, Object> snapshotRerank() {
        AIModelProperties.ModelGroup rerank = aiModelProperties.getRerank();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", Boolean.TRUE.equals(ragConfigProperties.getRerankEnabled()));
        out.put("defaultModelId", rerank.getDefaultModel());
        out.put("candidates", sanitizeCandidates(rerank.getCandidates()));
        return out;
    }

    private Map<String, Object> snapshotRetrieval() {
        SearchChannelProperties.Channels channels = searchChannelProperties.getChannels();
        SearchChannelProperties.Fusion fusion = searchChannelProperties.getFusion();
        SearchChannelProperties.ChannelWeights weights = fusion.getChannelWeights();

        Map<String, Object> vector = new LinkedHashMap<>();
        vector.put("enabled", channels.getVector().isEnabled());
        vector.put("minIntentScore", channels.getVector().getIntentDirected().getMinIntentScore());
        vector.put("confidenceThreshold", channels.getVector().getGlobal().getConfidenceThreshold());
        vector.put("singleIntentSupplementThreshold",
                channels.getVector().getGlobal().getSingleIntentSupplementThreshold());
        vector.put("candidateBudget", channels.getVector().getGlobal().getCandidateBudget());

        Map<String, Object> webSearch = new LinkedHashMap<>();
        webSearch.put("enabled", channels.getWebSearch().isEnabled());
        webSearch.put("count", channels.getWebSearch().getCount());
        webSearch.put("timeoutSeconds", channels.getWebSearch().getTimeoutSeconds());
        // 不写入 apiKey

        Map<String, Object> channelMap = new LinkedHashMap<>();
        channelMap.put("vector", vector);
        channelMap.put("keyword", Map.of("enabled", channels.getKeyword().isEnabled()));
        channelMap.put("graph", Map.of("enabled", channels.getGraph().isEnabled()));
        channelMap.put("webSearch", webSearch);

        Map<String, Object> fusionMap = new LinkedHashMap<>();
        fusionMap.put("strategy", fusion.getStrategy());
        fusionMap.put("rrfK", fusion.getRrfK());
        fusionMap.put("rerankCandidateLimit", fusion.getRerankCandidateLimit());
        Map<String, Object> weightMap = new LinkedHashMap<>();
        weightMap.put("vector", weights.getVector());
        weightMap.put("keyword", weights.getKeyword());
        weightMap.put("graph", weights.getGraph());
        weightMap.put("webSearch", weights.getWebSearch());
        weightMap.put("defaultWeight", weights.getDefaultWeight());
        fusionMap.put("channelWeights", weightMap);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("vectorBackend", vectorBackend);
        out.put("keywordBackend", keywordProperties.getType());
        out.put("graphBackend", graphProperties.getType());
        out.put("defaultTopK", searchChannelProperties.getDefaultTopK());
        out.put("recallBudget", searchChannelProperties.getRecallBudget());
        out.put("channels", channelMap);
        out.put("fusion", fusionMap);
        out.put("queryRewriteEnabled", Boolean.TRUE.equals(ragConfigProperties.getQueryRewriteEnabled()));
        out.put("rerankEnabled", Boolean.TRUE.equals(ragConfigProperties.getRerankEnabled()));
        out.put("contextEnrichEnabled", Boolean.TRUE.equals(ragConfigProperties.getContextEnrichEnabled()));
        out.put("citationEnabled", Boolean.TRUE.equals(ragConfigProperties.getCitationEnabled()));
        return out;
    }

    private Map<String, Object> snapshotPrompt() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", null);
        out.put("classpathPaths", PROMPT_PATHS);
        out.put("classpathContentSha256", hashClasspathPrompts(PROMPT_PATHS));
        out.put("citationRulesIncluded", Boolean.TRUE.equals(ragConfigProperties.getCitationEnabled()));
        return out;
    }

    private Map<String, Object> snapshotIntentTree() {
        List<IntentNodeDO> nodes = intentNodeMapper.selectList(Wrappers.lambdaQuery(IntentNodeDO.class)
                .orderByAsc(IntentNodeDO::getIntentCode));
        List<IntentNodeDO> enabled = nodes.stream()
                .filter(n -> n.getEnabled() != null && n.getEnabled() == 1)
                .toList();
        Date maxUpdate = nodes.stream()
                .map(IntentNodeDO::getUpdateTime)
                .filter(Objects::nonNull)
                .max(Date::compareTo)
                .orElse(null);

        StringBuilder digest = new StringBuilder();
        for (IntentNodeDO n : enabled) {
            digest.append(StrUtil.blankToDefault(n.getIntentCode(), ""))
                    .append('|').append(n.getEnabled())
                    .append('|').append(StrUtil.blankToDefault(n.getKbId(), ""))
                    .append('|').append(n.getCollectionNames() == null ? "" : String.join(",", n.getCollectionNames()))
                    .append('|').append(n.getTopK() == null ? "" : n.getTopK())
                    .append('|').append(StrUtil.blankToDefault(n.getPromptTemplate(), ""))
                    .append('|').append(StrUtil.blankToDefault(n.getPromptSnippet(), ""))
                    .append('\n');
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", null);
        out.put("nodeCount", nodes.size());
        out.put("enabledNodeCount", enabled.size());
        out.put("maxUpdateTime", toIso(maxUpdate));
        out.put("contentSha256", DigestUtil.sha256Hex(digest.toString()));
        return out;
    }

    private Map<String, Object> snapshotKnowledge() {
        List<KnowledgeBaseDO> bases = knowledgeBaseMapper.selectList(Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .orderByAsc(KnowledgeBaseDO::getId));
        List<KnowledgeDocumentDO> docs = knowledgeDocumentMapper.selectList(Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .select(KnowledgeDocumentDO::getId,
                        KnowledgeDocumentDO::getKbId,
                        KnowledgeDocumentDO::getDocName,
                        KnowledgeDocumentDO::getStatus,
                        KnowledgeDocumentDO::getEnabled,
                        KnowledgeDocumentDO::getChunkCount,
                        KnowledgeDocumentDO::getUpdateTime));

        Map<String, List<KnowledgeDocumentDO>> byKb = docs.stream()
                .filter(d -> d.getKbId() != null)
                .collect(Collectors.groupingBy(KnowledgeDocumentDO::getKbId));

        List<Map<String, Object>> baseRows = new ArrayList<>();
        StringBuilder inventory = new StringBuilder();
        for (KnowledgeBaseDO kb : bases) {
            List<KnowledgeDocumentDO> kbDocs = byKb.getOrDefault(kb.getId(), List.of());
            int successCount = 0;
            int totalChunks = 0;
            Date maxDocUpdate = null;
            for (KnowledgeDocumentDO d : kbDocs) {
                if (DocumentStatus.SUCCESS.getCode().equals(d.getStatus())
                        && (d.getEnabled() == null || d.getEnabled() == 1)) {
                    successCount++;
                }
                totalChunks += d.getChunkCount() == null ? 0 : d.getChunkCount();
                if (d.getUpdateTime() != null && (maxDocUpdate == null || d.getUpdateTime().after(maxDocUpdate))) {
                    maxDocUpdate = d.getUpdateTime();
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kbId", kb.getId());
            row.put("name", kb.getName());
            row.put("collectionName", kb.getCollectionName());
            row.put("embeddingModel", kb.getEmbeddingModel());
            row.put("updateTime", toIso(kb.getUpdateTime()));
            row.put("documentCount", kbDocs.size());
            row.put("successDocumentCount", successCount);
            row.put("totalChunkCount", totalChunks);
            row.put("maxDocumentUpdateTime", toIso(maxDocUpdate));
            baseRows.add(row);

            inventory.append(kb.getId()).append('|')
                    .append(StrUtil.blankToDefault(kb.getCollectionName(), "")).append('|')
                    .append(StrUtil.blankToDefault(kb.getEmbeddingModel(), "")).append('|')
                    .append(kbDocs.size()).append('|')
                    .append(successCount).append('|')
                    .append(totalChunks).append('|')
                    .append(toIso(maxDocUpdate) == null ? "" : toIso(maxDocUpdate))
                    .append('\n');
        }

        String inventorySha256 = DigestUtil.sha256Hex(inventory.toString());
        Map<String, Object> out = new LinkedHashMap<>();
        // 库存指纹：非精确向量索引版本；对比时可用于发现知识变更
        out.put("fingerprint", inventorySha256);
        out.put("fingerprintKind", "kb_inventory_sha256");
        out.put("vectorBackend", vectorBackend);
        out.put("defaultCollectionName", ragDefaultProperties.getCollectionName());
        out.put("defaultDimension", ragDefaultProperties.getDimension());
        out.put("defaultMetricType", ragDefaultProperties.getMetricType());
        out.put("bases", baseRows);
        out.put("inventorySha256", inventorySha256);
        return out;
    }

    static List<Map<String, Object>> sanitizeCandidates(List<AIModelProperties.ModelCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AIModelProperties.ModelCandidate c : candidates) {
            if (c == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("provider", c.getProvider());
            row.put("model", c.getModel());
            row.put("dimension", c.getDimension());
            row.put("priority", c.getPriority());
            row.put("enabled", c.getEnabled());
            row.put("supportsThinking", c.getSupportsThinking());
            // 故意不写入 url（可能含敏感主机信息）；密钥更不写入
            out.add(row);
        }
        return out;
    }

    static String hashClasspathPrompts(List<String> paths) {
        StringBuilder digest = new StringBuilder();
        List<String> sorted = paths.stream().sorted(Comparator.naturalOrder()).toList();
        for (String path : sorted) {
            digest.append(path).append('\n');
            digest.append(readClasspathText(path)).append('\n');
        }
        return DigestUtil.sha256Hex(digest.toString());
    }

    private static String readClasspathText(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return "";
            }
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String toIso(Date date) {
        return date == null ? null : date.toInstant().toString();
    }
}

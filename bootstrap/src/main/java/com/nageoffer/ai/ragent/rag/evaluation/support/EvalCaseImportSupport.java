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
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalCaseUpsertRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalImportIssueVO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalCaseDO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 评估样本导入解析与校验（兼容 camelCase API 与 snake_case ragenteval JSONL）。
 */
public final class EvalCaseImportSupport {

    public static final String LEVEL_ERROR = "ERROR";
    public static final String LEVEL_WARNING = "WARNING";

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private EvalCaseImportSupport() {
    }

    public record ParsedCase(EvalCaseUpsertRequest request, List<EvalImportIssueVO> issues) {
    }

    public record ParseFileResult(List<ParsedCase> cases, List<EvalImportIssueVO> fileIssues) {
    }

    public static ParseFileResult parseFile(String content) {
        List<EvalImportIssueVO> fileIssues = new ArrayList<>();
        List<ParsedCase> cases = new ArrayList<>();
        if (StrUtil.isBlank(content)) {
            fileIssues.add(issue(null, null, LEVEL_ERROR, "EMPTY_FILE", "导入内容为空"));
            return new ParseFileResult(cases, fileIssues);
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("[")) {
            try {
                JSONArray array = JSONUtil.parseArray(trimmed);
                for (int i = 0; i < array.size(); i++) {
                    Object item = array.get(i);
                    if (!(item instanceof JSONObject obj)) {
                        fileIssues.add(issue(i + 1, null, LEVEL_ERROR, "INVALID_ITEM", "数组元素必须是对象"));
                        continue;
                    }
                    cases.add(parseObject(obj, i + 1));
                }
            } catch (Exception ex) {
                fileIssues.add(issue(null, null, LEVEL_ERROR, "INVALID_JSON", "JSON 数组解析失败: " + ex.getMessage()));
            }
            return new ParseFileResult(cases, fileIssues);
        }

        String[] lines = content.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                JSONObject obj = JSONUtil.parseObj(line);
                cases.add(parseObject(obj, i + 1));
            } catch (Exception ex) {
                fileIssues.add(issue(i + 1, null, LEVEL_ERROR, "INVALID_JSONL", "JSONL 行解析失败: " + ex.getMessage()));
            }
        }
        if (cases.isEmpty() && fileIssues.isEmpty()) {
            fileIssues.add(issue(null, null, LEVEL_ERROR, "EMPTY_FILE", "未解析到任何样本"));
        }
        return new ParseFileResult(cases, fileIssues);
    }

    public static ParsedCase parseObject(JSONObject raw, Integer line) {
        EvalCaseUpsertRequest req = new EvalCaseUpsertRequest();
        req.setQueryId(firstString(raw, "queryId", "query_id"));
        req.setQuery(firstString(raw, "query"));
        req.setIntentL1(firstString(raw, "intentL1", "intent_l1"));
        req.setIntentL2(firstString(raw, "intentL2", "intent_l2"));
        req.setDifficulty(firstString(raw, "difficulty"));
        req.setRequiresRag(firstBoolean(raw, "requiresRag", "requires_rag"));
        req.setExpectedAnswerType(firstString(raw, "expectedAnswerType", "expected_answer_type"));
        req.setExpectedDocIds(firstStringList(raw, "expectedDocIds", "expected_doc_ids"));
        req.setNiceToHaveDocIds(firstStringList(raw, "niceToHaveDocIds", "expected_doc_ids_nice"));
        req.setGroundTruth(firstString(raw, "groundTruth", "ground_truth"));
        req.setTrapType(firstString(raw, "trapType", "trap_type"));
        req.setEnabledMetrics(firstStringList(raw, "enabledMetrics", "eval_metrics"));
        req.setTags(firstStringList(raw, "tags"));
        Object metadata = raw.get("metadata");
        if (metadata instanceof JSONObject metaObj) {
            req.setMetadata(new LinkedHashMap<>(metaObj));
        }
        // 文件级查重与环境映射在 Service 中二次校验
        List<EvalImportIssueVO> issues = validateRequest(req, line, null, Set.of(), Set.of(), false);
        return new ParsedCase(req, issues);
    }

    /**
     * @param knownQueryIds 同批次已出现的 queryId（用于查重）
     * @param intentCodes   当前意图树 intentCode；空集合表示跳过意图映射检查
     * @param bizDocIds     当前环境可解析业务码；空集合表示跳过文档映射检查
     * @param strictPublish 发布校验时 requiresRag=true 必须有 gold doc
     */
    public static List<EvalImportIssueVO> validateRequest(EvalCaseUpsertRequest req,
                                                          Integer line,
                                                          Set<String> knownQueryIds,
                                                          Set<String> intentCodes,
                                                          Set<String> bizDocIds,
                                                          boolean strictPublish) {
        List<EvalImportIssueVO> issues = new ArrayList<>();
        String queryId = StrUtil.trim(req.getQueryId());
        String query = StrUtil.trim(req.getQuery());

        if (StrUtil.isBlank(queryId)) {
            issues.add(issue(line, queryId, LEVEL_ERROR, "QUERY_ID_REQUIRED", "queryId 不能为空"));
        } else if (knownQueryIds != null && !knownQueryIds.add(queryId)) {
            issues.add(issue(line, queryId, LEVEL_ERROR, "QUERY_ID_DUPLICATE", "queryId 重复: " + queryId));
        }
        if (StrUtil.isBlank(query)) {
            issues.add(issue(line, queryId, LEVEL_ERROR, "QUERY_REQUIRED", "query 不能为空"));
        }
        if (req.getRequiresRag() == null) {
            issues.add(issue(line, queryId, LEVEL_ERROR, "REQUIRES_RAG_REQUIRED", "requiresRag 不能为空"));
        }
        String difficulty = StrUtil.blankToDefault(StrUtil.trim(req.getDifficulty()), "medium").toLowerCase(Locale.ROOT);
        if (!DIFFICULTIES.contains(difficulty)) {
            issues.add(issue(line, queryId, LEVEL_ERROR, "DIFFICULTY_INVALID", "difficulty 必须是 easy/medium/hard"));
        } else {
            req.setDifficulty(difficulty);
        }

        List<String> expected = nullToEmpty(req.getExpectedDocIds());
        List<String> nice = nullToEmpty(req.getNiceToHaveDocIds());
        req.setExpectedDocIds(expected);
        req.setNiceToHaveDocIds(nice);

        Boolean requiresRag = req.getRequiresRag();
        if (Boolean.TRUE.equals(requiresRag) && expected.isEmpty() && nice.isEmpty()) {
            String level = strictPublish ? LEVEL_ERROR : LEVEL_WARNING;
            issues.add(issue(line, queryId, level, "GOLD_DOC_MISSING",
                    "requiresRag=true 时建议提供 expectedDocIds 或 niceToHaveDocIds"));
        }

        String intentL2 = StrUtil.trim(req.getIntentL2());
        if (StrUtil.isNotBlank(intentL2) && intentCodes != null && !intentCodes.isEmpty() && !intentCodes.contains(intentL2)) {
            issues.add(issue(line, queryId, LEVEL_WARNING, "INTENT_UNMAPPED", "intentL2 无法映射到当前意图树: " + intentL2));
        }

        if (bizDocIds != null && !bizDocIds.isEmpty()) {
            for (String docId : expected) {
                if (!bizDocIds.contains(docId)) {
                    issues.add(issue(line, queryId, LEVEL_WARNING, "DOC_UNRESOLVED", "expectedDocId 无法解析: " + docId));
                }
            }
            for (String docId : nice) {
                if (!bizDocIds.contains(docId)) {
                    issues.add(issue(line, queryId, LEVEL_WARNING, "DOC_UNRESOLVED", "niceToHaveDocId 无法解析: " + docId));
                }
            }
        }

        String groundTruth = StrUtil.trim(req.getGroundTruth());
        if (StrUtil.isNotBlank(groundTruth) && looksLikeMetaInstruction(groundTruth)) {
            issues.add(issue(line, queryId, LEVEL_WARNING, "GROUND_TRUTH_META",
                    "groundTruth 疑似元指令（如“应推荐…”），可能拉低 RAGAS 分数"));
        }
        return issues;
    }

    public static EvalCaseDO toNewCaseDO(String versionId, EvalCaseUpsertRequest req) {
        return EvalCaseDO.builder()
                .datasetVersionId(versionId)
                .queryId(StrUtil.trim(req.getQueryId()))
                .query(StrUtil.trim(req.getQuery()))
                .intentL1(StrUtil.trim(req.getIntentL1()))
                .intentL2(StrUtil.trim(req.getIntentL2()))
                .difficulty(StrUtil.blankToDefault(StrUtil.trim(req.getDifficulty()), "medium"))
                .requiresRag(Boolean.TRUE.equals(req.getRequiresRag()))
                .expectedAnswerType(StrUtil.trim(req.getExpectedAnswerType()))
                .expectedDocIds(EvalJsonSupport.toJsonArray(nullToEmpty(req.getExpectedDocIds())))
                .niceToHaveDocIds(EvalJsonSupport.toJsonArray(nullToEmpty(req.getNiceToHaveDocIds())))
                .groundTruth(StrUtil.nullToDefault(req.getGroundTruth(), ""))
                .trapType(StrUtil.trim(req.getTrapType()))
                .enabledMetrics(EvalJsonSupport.toJsonArray(nullToEmpty(req.getEnabledMetrics())))
                .tags(EvalJsonSupport.toJsonArray(nullToEmpty(req.getTags())))
                .metadata(EvalJsonSupport.toJsonObject(req.getMetadata()))
                .build();
    }

    public static void applyUpdate(EvalCaseDO record, EvalCaseUpsertRequest req) {
        if (req.getQuery() != null) {
            record.setQuery(StrUtil.trim(req.getQuery()));
        }
        if (req.getIntentL1() != null) {
            record.setIntentL1(StrUtil.trim(req.getIntentL1()));
        }
        if (req.getIntentL2() != null) {
            record.setIntentL2(StrUtil.trim(req.getIntentL2()));
        }
        if (req.getDifficulty() != null) {
            record.setDifficulty(StrUtil.trim(req.getDifficulty()));
        }
        if (req.getRequiresRag() != null) {
            record.setRequiresRag(req.getRequiresRag());
        }
        if (req.getExpectedAnswerType() != null) {
            record.setExpectedAnswerType(StrUtil.trim(req.getExpectedAnswerType()));
        }
        if (req.getExpectedDocIds() != null) {
            record.setExpectedDocIds(EvalJsonSupport.toJsonArray(req.getExpectedDocIds()));
        }
        if (req.getNiceToHaveDocIds() != null) {
            record.setNiceToHaveDocIds(EvalJsonSupport.toJsonArray(req.getNiceToHaveDocIds()));
        }
        if (req.getGroundTruth() != null) {
            record.setGroundTruth(req.getGroundTruth());
        }
        if (req.getTrapType() != null) {
            record.setTrapType(StrUtil.trim(req.getTrapType()));
        }
        if (req.getEnabledMetrics() != null) {
            record.setEnabledMetrics(EvalJsonSupport.toJsonArray(req.getEnabledMetrics()));
        }
        if (req.getTags() != null) {
            record.setTags(EvalJsonSupport.toJsonArray(req.getTags()));
        }
        if (req.getMetadata() != null) {
            record.setMetadata(EvalJsonSupport.toJsonObject(req.getMetadata()));
        }
    }

    public static String contentHash(List<EvalCaseDO> cases) {
        StringBuilder sb = new StringBuilder();
        cases.stream()
                .sorted((a, b) -> StrUtil.compare(a.getQueryId(), b.getQueryId(), true))
                .forEach(c -> sb.append(c.getQueryId()).append('\n')
                        .append(c.getQuery()).append('\n')
                        .append(c.getRequiresRag()).append('\n')
                        .append(c.getExpectedDocIds()).append('\n')
                        .append(c.getGroundTruth()).append('\n'));
        return DigestUtil.sha256Hex(sb.toString());
    }

    public static String toExportJsonl(List<EvalCaseDO> cases) {
        StringBuilder sb = new StringBuilder();
        for (EvalCaseDO c : cases) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("schemaVersion", "1.0.0");
            row.put("queryId", c.getQueryId());
            row.put("query", c.getQuery());
            row.put("intentL1", c.getIntentL1());
            row.put("intentL2", c.getIntentL2());
            row.put("difficulty", c.getDifficulty());
            row.put("requiresRag", c.getRequiresRag());
            row.put("expectedAnswerType", c.getExpectedAnswerType());
            row.put("expectedDocIds", EvalJsonSupport.toStringList(c.getExpectedDocIds()));
            row.put("niceToHaveDocIds", EvalJsonSupport.toStringList(c.getNiceToHaveDocIds()));
            row.put("groundTruth", c.getGroundTruth());
            row.put("trapType", c.getTrapType());
            row.put("enabledMetrics", EvalJsonSupport.toStringList(c.getEnabledMetrics()));
            row.put("tags", EvalJsonSupport.toStringList(c.getTags()));
            row.put("metadata", EvalJsonSupport.toMap(c.getMetadata()));
            sb.append(JSONUtil.toJsonStr(row)).append('\n');
        }
        return sb.toString();
    }

    public static String stripDocExtension(String docName) {
        if (StrUtil.isBlank(docName)) {
            return docName;
        }
        int idx = docName.lastIndexOf('.');
        if (idx <= 0) {
            return docName;
        }
        return docName.substring(0, idx);
    }

    private static boolean looksLikeMetaInstruction(String groundTruth) {
        String text = groundTruth.trim();
        return text.startsWith("应") || text.startsWith("需要") || text.contains("应该回答") || text.contains("不要回答");
    }

    private static EvalImportIssueVO issue(Integer line, String queryId, String level, String code, String message) {
        return EvalImportIssueVO.builder()
                .line(line)
                .queryId(queryId)
                .level(level)
                .code(code)
                .message(message)
                .build();
    }

    private static String firstString(JSONObject obj, String... keys) {
        for (String key : keys) {
            Object value = obj.get(key);
            if (value != null && StrUtil.isNotBlank(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static Boolean firstBoolean(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.containsKey(key)) {
                return obj.getBool(key);
            }
        }
        return null;
    }

    private static List<String> firstStringList(JSONObject obj, String... keys) {
        for (String key : keys) {
            Object value = obj.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof JSONArray array) {
                List<String> list = new ArrayList<>();
                for (Object item : array) {
                    if (item != null && StrUtil.isNotBlank(String.valueOf(item))) {
                        list.add(String.valueOf(item).trim());
                    }
                }
                return list;
            }
        }
        return new ArrayList<>();
    }

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    public static Set<String> newQueryIdSet() {
        return new HashSet<>();
    }
}

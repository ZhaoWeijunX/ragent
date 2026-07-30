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

package com.nageoffer.ai.ragent.rag.evaluation.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalMetricReportVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalScoreBatchVO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalCaseDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalRecordDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalRunDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalScoreBatchDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.entity.EvalScoreDO;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalCaseMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalRecordMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalRunMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalScoreBatchMapper;
import com.nageoffer.ai.ragent.rag.evaluation.dao.mapper.EvalScoreMapper;
import com.nageoffer.ai.ragent.rag.evaluation.metric.DeterministicMetricEngine;
import com.nageoffer.ai.ragent.rag.evaluation.metric.EvalScoreSample;
import com.nageoffer.ai.ragent.rag.evaluation.metric.MetricResult;
import com.nageoffer.ai.ragent.rag.evaluation.metric.impl.BehaviorMetrics;
import com.nageoffer.ai.ragent.rag.evaluation.metric.impl.RetrievalMetrics;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalScoreService;
import com.nageoffer.ai.ragent.rag.evaluation.support.EvalJsonSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.eval", name = "workbench-enabled", havingValue = "true")
public class EvalScoreServiceImpl implements EvalScoreService {

    public static final String DIM_OVERALL = "OVERALL";
    public static final String DIM_INTENT_L1 = "INTENT_L1";
    public static final String DIM_INTENT_L2 = "INTENT_L2";
    public static final String DIM_DIFFICULTY = "DIFFICULTY";
    public static final String DIM_SAMPLE = "SAMPLE";

    public static final String BATCH_PENDING = "PENDING";
    public static final String BATCH_RUNNING = "RUNNING";
    public static final String BATCH_COMPLETED = "COMPLETED";
    public static final String BATCH_FAILED = "FAILED";

    private final EvalRunMapper runMapper;
    private final EvalRecordMapper recordMapper;
    private final EvalCaseMapper caseMapper;
    private final EvalScoreBatchMapper scoreBatchMapper;
    private final EvalScoreMapper scoreMapper;
    private final DeterministicMetricEngine metricEngine;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String scoreDeterministic(String runId) {
        EvalRunDO run = runMapper.selectById(runId);
        Assert.notNull(run, () -> new ClientException("Run 不存在"));

        List<EvalScoreSample> samples = loadSamples(run);
        String batchId = IdUtil.getSnowflakeNextIdStr();
        Date started = new Date();
        Map<String, Object> threshold = EvalJsonSupport.toMap(run.getThresholdSnapshot());
        if (threshold.isEmpty()) {
            threshold = defaultThreshold();
        }

        EvalScoreBatchDO batch = EvalScoreBatchDO.builder()
                .id(batchId)
                .runId(runId)
                .scoreType(EvalWorkbenchConstants.SCORE_DETERMINISTIC)
                .status(BATCH_RUNNING)
                .algorithmVersion(MetricResult.ALGORITHM_VERSION)
                .judgeConfigSnapshot("{}")
                .thresholdSnapshot(JSONUtil.toJsonStr(threshold))
                .sampleCount(samples.size())
                .tokenUsage("{}")
                .startedAt(started)
                .build();
        scoreBatchMapper.insert(batch);

        try {
            List<MetricResult> results = metricEngine.scoreAll(samples);
            Map<String, EvalScoreSample> byQuery = samples.stream()
                    .collect(Collectors.toMap(
                            s -> StrUtil.blankToDefault(s.getQueryId(), s.getRecordId()),
                            s -> s,
                            (a, b) -> a,
                            LinkedHashMap::new));

            List<EvalScoreDO> rows = new ArrayList<>();
            for (MetricResult mr : results) {
                rows.addAll(toScoreRows(batchId, runId, mr, byQuery));
            }
            for (EvalScoreDO row : rows) {
                scoreMapper.insert(row);
            }

            scoreBatchMapper.update(null, Wrappers.lambdaUpdate(EvalScoreBatchDO.class)
                    .eq(EvalScoreBatchDO::getId, batchId)
                    .set(EvalScoreBatchDO::getStatus, BATCH_COMPLETED)
                    .set(EvalScoreBatchDO::getFinishedAt, new Date())
                    .set(EvalScoreBatchDO::getSampleCount, samples.size()));
            return batchId;
        } catch (Exception ex) {
            log.error("确定性评分失败 runId={}", runId, ex);
            scoreBatchMapper.update(null, Wrappers.lambdaUpdate(EvalScoreBatchDO.class)
                    .eq(EvalScoreBatchDO::getId, batchId)
                    .set(EvalScoreBatchDO::getStatus, BATCH_FAILED)
                    .set(EvalScoreBatchDO::getFinishedAt, new Date())
                    .set(EvalScoreBatchDO::getErrorMessage, ex.getMessage()));
            throw ex;
        }
    }

    @Override
    public List<EvalScoreBatchVO> listBatches(String runId) {
        requireRun(runId);
        return scoreBatchMapper.selectList(Wrappers.lambdaQuery(EvalScoreBatchDO.class)
                        .eq(EvalScoreBatchDO::getRunId, runId)
                        .orderByDesc(EvalScoreBatchDO::getCreateTime))
                .stream()
                .map(this::toBatchVO)
                .collect(Collectors.toList());
    }

    @Override
    public EvalMetricReportVO getReport(String runId, String batchId) {
        requireRun(runId);
        EvalScoreBatchDO batch = resolveBatch(runId, batchId);
        List<EvalScoreDO> scores = scoreMapper.selectList(Wrappers.lambdaQuery(EvalScoreDO.class)
                .eq(EvalScoreDO::getScoreBatchId, batch.getId()));

        Map<String, List<EvalScoreDO>> byMetric = scores.stream()
                .collect(Collectors.groupingBy(EvalScoreDO::getMetricName, LinkedHashMap::new, Collectors.toList()));

        List<EvalMetricReportVO.MetricItemVO> metrics = new ArrayList<>();
        for (Map.Entry<String, List<EvalScoreDO>> e : byMetric.entrySet()) {
            EvalScoreDO overall = e.getValue().stream()
                    .filter(s -> DIM_OVERALL.equals(s.getDimensionType()))
                    .findFirst()
                    .orElse(null);
            Map<String, Double> l1 = dimMap(e.getValue(), DIM_INTENT_L1);
            Map<String, Double> l2 = dimMap(e.getValue(), DIM_INTENT_L2);
            Map<String, Double> diff = dimMap(e.getValue(), DIM_DIFFICULTY);
            Map<String, Object> meta = overall == null ? Map.of() : EvalJsonSupport.toMap(overall.getDetail());
            boolean pct = meta.get("pct") == null || Boolean.TRUE.equals(meta.get("pct"));
            metrics.add(EvalMetricReportVO.MetricItemVO.builder()
                    .name(e.getKey())
                    .overall(overall == null || overall.getScoreValue() == null ? null : overall.getScoreValue().doubleValue())
                    .pct(pct)
                    .sampleCount(overall == null ? null : overall.getSampleCount())
                    .byIntentL1(l1)
                    .byIntentL2(l2)
                    .byDifficulty(diff)
                    .meta(meta)
                    .build());
        }

        List<EvalScoreSample> samples = loadSamples(requireRun(runId));
        List<EvalMetricReportVO.SampleFailureVO> failures = buildFailures(samples, byMetric);

        return EvalMetricReportVO.builder()
                .runId(runId)
                .batchId(batch.getId())
                .scoreType(batch.getScoreType())
                .algorithmVersion(batch.getAlgorithmVersion())
                .status(batch.getStatus())
                .sampleCount(batch.getSampleCount())
                .intentTop1Note("MVP：多子问题取第一个非空预测意图（intentPred）与 intentL2 精确匹配")
                .metrics(metrics)
                .failures(failures)
                .build();
    }

    @Override
    public byte[] exportReport(String runId, String batchId, String format) {
        EvalMetricReportVO report = getReport(runId, batchId);
        String fmt = StrUtil.blankToDefault(format, "json").toLowerCase();
        if ("csv".equals(fmt)) {
            return toCsv(report).getBytes(StandardCharsets.UTF_8);
        }
        if ("jsonl".equals(fmt)) {
            StringBuilder sb = new StringBuilder();
            for (EvalMetricReportVO.MetricItemVO m : report.getMetrics()) {
                sb.append(JSONUtil.toJsonStr(m)).append('\n');
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
        return JSONUtil.toJsonPrettyStr(report).getBytes(StandardCharsets.UTF_8);
    }

    private List<EvalScoreSample> loadSamples(EvalRunDO run) {
        List<EvalRecordDO> records = recordMapper.selectList(Wrappers.lambdaQuery(EvalRecordDO.class)
                .eq(EvalRecordDO::getRunId, run.getId())
                .orderByAsc(EvalRecordDO::getCreateTime));
        Set<String> caseIds = records.stream().map(EvalRecordDO::getCaseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, EvalCaseDO> cases = caseIds.isEmpty()
                ? Map.of()
                : caseMapper.selectBatchIds(caseIds).stream()
                .collect(Collectors.toMap(EvalCaseDO::getId, c -> c, (a, b) -> a));

        List<EvalScoreSample> samples = new ArrayList<>();
        for (EvalRecordDO r : records) {
            EvalCaseDO c = cases.get(r.getCaseId());
            samples.add(EvalScoreSample.builder()
                    .recordId(r.getId())
                    .caseId(r.getCaseId())
                    .queryId(c == null ? null : c.getQueryId())
                    .question(r.getQuestion())
                    .response(r.getResponse())
                    .intentL1(c == null ? null : c.getIntentL1())
                    .intentL2(c == null ? null : c.getIntentL2())
                    .difficulty(c == null ? null : c.getDifficulty())
                    .requiresRag(c != null && Boolean.TRUE.equals(c.getRequiresRag()))
                    .referenceDocIds(c == null ? List.of() : EvalJsonSupport.toStringList(c.getExpectedDocIds()))
                    .referenceDocIdsNice(c == null ? List.of() : EvalJsonSupport.toStringList(c.getNiceToHaveDocIds()))
                    .groundTruth(c == null ? null : c.getGroundTruth())
                    .intentPred(r.getIntentPred())
                    .retrievedDocIds(EvalJsonSupport.toStringList(r.getRetrievedDocIds()))
                    .hasKb(r.getHasKb())
                    .retrievalSkipped(r.getRetrievalSkipped())
                    .ttftMs(r.getTtftMs())
                    .totalLatencyMs(r.getTotalLatencyMs())
                    .recordStatus(r.getStatus())
                    .errorCode(r.getErrorCode())
                    .errorMessage(r.getErrorMessage())
                    .traceId(r.getTraceId())
                    .failureReasons(new ArrayList<>())
                    .build());
        }
        annotateFailures(samples);
        return samples;
    }

    private void annotateFailures(List<EvalScoreSample> samples) {
        for (EvalScoreSample s : samples) {
            List<String> reasons = new ArrayList<>();
            if (EvalWorkbenchConstants.RECORD_ERROR.equals(s.getRecordStatus())) {
                reasons.add("chat_request_failed");
            }
            if (StrUtil.isNotBlank(s.getIntentL2()) && !StrUtil.equals(s.getIntentPred(), s.getIntentL2())) {
                reasons.add("intent_mismatch");
            }
            if (s.isRequiresRag() && !s.safeReference().isEmpty()) {
                if (RetrievalMetrics.hitAtK(s, 5) < 1.0) {
                    reasons.add("hit_at_5_miss");
                }
                if (RetrievalMetrics.recallAtK(s, 5, false) < 1.0) {
                    reasons.add("recall_at_5_low");
                }
            }
            if (s.isRequiresRag() && BehaviorMetrics.isRefusal(s)) {
                reasons.add("rag_required_but_not_used");
            }
            if (!s.isRequiresRag() && BehaviorMetrics.enteredKb(s)) {
                reasons.add("rag_not_required_but_used");
            }
            s.setFailureReasons(reasons);
        }
    }

    private List<EvalScoreDO> toScoreRows(String batchId,
                                          String runId,
                                          MetricResult mr,
                                          Map<String, EvalScoreSample> byQuery) {
        List<EvalScoreDO> rows = new ArrayList<>();
        Map<String, Object> overallDetail = new LinkedHashMap<>(mr.getMeta() == null ? Map.of() : mr.getMeta());
        overallDetail.put("pct", mr.isPct());
        rows.add(scoreRow(batchId, runId, null, mr.getName(), DIM_OVERALL, null,
                mr.getOverall(), sampleCount(mr), overallDetail));

        putDimRows(rows, batchId, runId, mr.getName(), DIM_INTENT_L1, mr.getByIntentL1());
        putDimRows(rows, batchId, runId, mr.getName(), DIM_INTENT_L2, mr.getByIntentL2());
        putDimRows(rows, batchId, runId, mr.getName(), DIM_DIFFICULTY, mr.getByDifficulty());

        if (mr.getPerSample() != null) {
            for (Map.Entry<String, Double> e : mr.getPerSample().entrySet()) {
                EvalScoreSample sample = byQuery.get(e.getKey());
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("queryId", e.getKey());
                detail.put("pct", mr.isPct());
                if (sample != null && sample.getFailureReasons() != null) {
                    detail.put("failureReasons", sample.getFailureReasons());
                }
                rows.add(scoreRow(batchId, runId,
                        sample == null ? null : sample.getRecordId(),
                        mr.getName(), DIM_SAMPLE, e.getKey(),
                        e.getValue(), e.getValue() == null ? 0 : 1, detail));
            }
        }
        return rows;
    }

    private void putDimRows(List<EvalScoreDO> rows, String batchId, String runId, String metric,
                            String dimType, Map<String, Double> map) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, Double> e : map.entrySet()) {
            rows.add(scoreRow(batchId, runId, null, metric, dimType, e.getKey(),
                    e.getValue(), e.getValue() == null ? 0 : 1, Map.of("pct", true)));
        }
    }

    private EvalScoreDO scoreRow(String batchId, String runId, String recordId, String metric,
                                 String dimType, String dimValue, Double value, Integer count,
                                 Map<String, Object> detail) {
        return EvalScoreDO.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                .scoreBatchId(batchId)
                .runId(runId)
                .recordId(recordId)
                .metricName(metric)
                .dimensionType(dimType)
                .dimensionValue(dimValue)
                .scoreValue(value == null ? null : BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP))
                .sampleCount(count)
                .detail(JSONUtil.toJsonStr(detail == null ? Map.of() : detail))
                .build();
    }

    private Integer sampleCount(MetricResult mr) {
        if (mr.getMeta() != null && mr.getMeta().get("sampleCount") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private Map<String, Double> dimMap(List<EvalScoreDO> rows, String dim) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (EvalScoreDO row : rows) {
            if (dim.equals(row.getDimensionType()) && row.getDimensionValue() != null) {
                out.put(row.getDimensionValue(),
                        row.getScoreValue() == null ? null : row.getScoreValue().doubleValue());
            }
        }
        return out;
    }

    private List<EvalMetricReportVO.SampleFailureVO> buildFailures(List<EvalScoreSample> samples,
                                                                   Map<String, List<EvalScoreDO>> byMetric) {
        Map<String, Double> hit5 = sampleScores(byMetric.get("hit@5"));
        Map<String, Double> intent = sampleScores(byMetric.get("intent_top1"));
        List<EvalMetricReportVO.SampleFailureVO> failures = new ArrayList<>();
        for (EvalScoreSample s : samples) {
            if (s.getFailureReasons() == null || s.getFailureReasons().isEmpty()) {
                continue;
            }
            String qid = StrUtil.blankToDefault(s.getQueryId(), s.getRecordId());
            List<String> top5 = prefixDocs(s.safeRetrieved(), 5);
            List<String> missed = diffMissing(s.safeReference(), top5);
            List<String> extra = diffMissing(top5, s.safeReference());
            failures.add(EvalMetricReportVO.SampleFailureVO.builder()
                    .recordId(s.getRecordId())
                    .queryId(s.getQueryId())
                    .question(s.getQuestion())
                    .response(truncate(s.getResponse(), 500))
                    .groundTruth(truncate(s.getGroundTruth(), 500))
                    .status(s.getRecordStatus())
                    .intentPred(s.getIntentPred())
                    .intentL2(s.getIntentL2())
                    .retrievedDocIds(s.safeRetrieved())
                    .expectedDocIds(s.safeReference())
                    .missedDocIds(missed)
                    .extraDocIds(extra)
                    .failureReasons(s.getFailureReasons())
                    .failureDetails(toFailureDetails(s, missed))
                    .errorCode(s.getErrorCode())
                    .errorMessage(s.getErrorMessage())
                    .traceId(s.getTraceId())
                    .hitAt5(hit5.get(qid))
                    .intentTop1(intent.get(qid))
                    .build());
        }
        return failures;
    }

    private List<EvalMetricReportVO.FailureReasonVO> toFailureDetails(EvalScoreSample s, List<String> missed) {
        List<EvalMetricReportVO.FailureReasonVO> details = new ArrayList<>();
        for (String code : s.getFailureReasons()) {
            details.add(EvalMetricReportVO.FailureReasonVO.builder()
                    .code(code)
                    .message(failureMessage(code, s, missed))
                    .build());
        }
        return details;
    }

    private static String failureMessage(String code, EvalScoreSample s, List<String> missed) {
        return switch (code) {
            case "chat_request_failed" -> StrUtil.isBlank(s.getErrorMessage())
                    ? "聊天请求失败"
                    : "聊天请求失败：" + truncate(s.getErrorMessage(), 120);
            case "intent_mismatch" -> "意图不匹配：预测 "
                    + StrUtil.blankToDefault(s.getIntentPred(), "空")
                    + "，期望 " + StrUtil.blankToDefault(s.getIntentL2(), "空");
            case "hit_at_5_miss" -> missed == null || missed.isEmpty()
                    ? "Top-5 未命中期望文档"
                    : "Top-5 未命中期望文档：" + String.join(", ", missed);
            case "recall_at_5_low" -> missed == null || missed.isEmpty()
                    ? "Top-5 召回未覆盖全部期望文档"
                    : "Top-5 召回未覆盖：" + String.join(", ", missed);
            case "rag_required_but_not_used" -> "需要检索但未使用知识库";
            case "rag_not_required_but_used" -> "无需检索却进入了知识库";
            default -> code;
        };
    }

    private static List<String> prefixDocs(List<String> docs, int k) {
        if (docs == null || docs.isEmpty() || k <= 0) {
            return List.of();
        }
        return docs.subList(0, Math.min(k, docs.size()));
    }

    private static List<String> diffMissing(List<String> expected, List<String> actual) {
        Set<String> have = new HashSet<>(actual == null ? List.of() : actual);
        List<String> missing = new ArrayList<>();
        for (String doc : expected == null ? List.<String>of() : expected) {
            if (!have.contains(doc)) {
                missing.add(doc);
            }
        }
        return missing;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }

    private Map<String, Double> sampleScores(List<EvalScoreDO> rows) {
        Map<String, Double> out = new HashMap<>();
        if (rows == null) {
            return out;
        }
        for (EvalScoreDO row : rows) {
            if (DIM_SAMPLE.equals(row.getDimensionType()) && row.getDimensionValue() != null) {
                out.put(row.getDimensionValue(),
                        row.getScoreValue() == null ? null : row.getScoreValue().doubleValue());
            }
        }
        return out;
    }

    private EvalScoreBatchDO resolveBatch(String runId, String batchId) {
        if (StrUtil.isNotBlank(batchId)) {
            EvalScoreBatchDO batch = scoreBatchMapper.selectById(batchId);
            Assert.notNull(batch, () -> new ClientException("评分批次不存在"));
            Assert.isTrue(runId.equals(batch.getRunId()), () -> new ClientException("批次不属于该 Run"));
            return batch;
        }
        EvalScoreBatchDO latest = scoreBatchMapper.selectOne(Wrappers.lambdaQuery(EvalScoreBatchDO.class)
                .eq(EvalScoreBatchDO::getRunId, runId)
                .eq(EvalScoreBatchDO::getScoreType, EvalWorkbenchConstants.SCORE_DETERMINISTIC)
                .eq(EvalScoreBatchDO::getStatus, BATCH_COMPLETED)
                .orderByDesc(EvalScoreBatchDO::getCreateTime)
                .last("LIMIT 1"));
        Assert.notNull(latest, () -> new ClientException("尚无已完成的确定性评分批次"));
        return latest;
    }

    private EvalRunDO requireRun(String runId) {
        EvalRunDO run = runMapper.selectById(runId);
        Assert.notNull(run, () -> new ClientException("Run 不存在"));
        return run;
    }

    private EvalScoreBatchVO toBatchVO(EvalScoreBatchDO batch) {
        return EvalScoreBatchVO.builder()
                .id(batch.getId())
                .runId(batch.getRunId())
                .scoreType(batch.getScoreType())
                .status(batch.getStatus())
                .algorithmVersion(batch.getAlgorithmVersion())
                .sampleCount(batch.getSampleCount())
                .thresholdSnapshot(EvalJsonSupport.toMap(batch.getThresholdSnapshot()))
                .startedAt(batch.getStartedAt())
                .finishedAt(batch.getFinishedAt())
                .errorMessage(batch.getErrorMessage())
                .createTime(batch.getCreateTime())
                .build();
    }

    private Map<String, Object> defaultThreshold() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("schemaVersion", "1.0.0");
        snap.put("policyVersion", "draft");
        snap.put("rules", List.of(
                Map.of("metric", "hit@5", "dimension", "OVERALL", "op", "gte", "value", 0.9),
                Map.of("metric", "intent_top1", "dimension", "OVERALL", "op", "gte", "value", 0.8)
        ));
        snap.put("onViolate", "WARN");
        return snap;
    }

    private String toCsv(EvalMetricReportVO report) {
        StringBuilder sb = new StringBuilder("metric,overall,sampleCount,pct\n");
        for (EvalMetricReportVO.MetricItemVO m : report.getMetrics()) {
            sb.append(csv(m.getName())).append(',')
                    .append(m.getOverall() == null ? "" : m.getOverall()).append(',')
                    .append(m.getSampleCount() == null ? "" : m.getSampleCount()).append(',')
                    .append(Boolean.TRUE.equals(m.getPct())).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}

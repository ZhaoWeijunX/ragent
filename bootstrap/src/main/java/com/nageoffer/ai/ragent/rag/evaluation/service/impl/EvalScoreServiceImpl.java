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
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import com.nageoffer.ai.ragent.infra.http.ModelUrlResolver;
import com.nageoffer.ai.ragent.rag.eval.EvalProperties;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import com.nageoffer.ai.ragent.rag.evaluation.controller.request.EvalRagasRescoreRequest;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalMetricReportVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRagasJudgeModelsVO;
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
import com.nageoffer.ai.ragent.rag.evaluation.judge.SemanticEvaluationProvider;
import com.nageoffer.ai.ragent.rag.evaluation.metric.DeterministicMetricEngine;
import com.nageoffer.ai.ragent.rag.evaluation.metric.EvalScoreSample;
import com.nageoffer.ai.ragent.rag.evaluation.metric.MetricResult;
import com.nageoffer.ai.ragent.rag.evaluation.metric.impl.BehaviorMetrics;
import com.nageoffer.ai.ragent.rag.evaluation.metric.impl.RetrievalMetrics;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalScoreService;
import com.nageoffer.ai.ragent.rag.evaluation.support.EvalJsonSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "ragent.eval", name = "workbench-enabled", havingValue = "true")
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
    public static final String BATCH_PARTIAL = "PARTIAL_SUCCESS";
    private static final int SCORE_INSERT_BATCH_SIZE = 200;
    private static final Log BATCH_LOG = LogFactory.getLog(EvalScoreServiceImpl.class);

    private final EvalRunMapper runMapper;
    private final EvalRecordMapper recordMapper;
    private final EvalCaseMapper caseMapper;
    private final EvalScoreBatchMapper scoreBatchMapper;
    private final EvalScoreMapper scoreMapper;
    private final DeterministicMetricEngine metricEngine;
    private final EvalProperties evalProperties;
    private final SemanticEvaluationProvider semanticEvaluationProvider;
    private final AIModelProperties aiModelProperties;
    private final Executor evalRagasExecutor;

    public EvalScoreServiceImpl(EvalRunMapper runMapper,
                                EvalRecordMapper recordMapper,
                                EvalCaseMapper caseMapper,
                                EvalScoreBatchMapper scoreBatchMapper,
                                EvalScoreMapper scoreMapper,
                                DeterministicMetricEngine metricEngine,
                                EvalProperties evalProperties,
                                SemanticEvaluationProvider semanticEvaluationProvider,
                                AIModelProperties aiModelProperties,
                                @Qualifier("evalRagasExecutor") Executor evalRagasExecutor) {
        this.runMapper = runMapper;
        this.recordMapper = recordMapper;
        this.caseMapper = caseMapper;
        this.scoreBatchMapper = scoreBatchMapper;
        this.scoreMapper = scoreMapper;
        this.metricEngine = metricEngine;
        this.evalProperties = evalProperties;
        this.semanticEvaluationProvider = semanticEvaluationProvider;
        this.aiModelProperties = aiModelProperties;
        this.evalRagasExecutor = evalRagasExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String scoreDeterministic(String runId) {
        EvalRunDO run = runMapper.selectById(runId);
        Assert.notNull(run, () -> new ClientException("Run 不存在"));

        List<EvalScoreSample> samples = loadSamples(run);
        String batchId = IdUtil.getSnowflakeNextIdStr();
        Date started = new Date();

        EvalScoreBatchDO batch = EvalScoreBatchDO.builder()
                .id(batchId)
                .runId(runId)
                .scoreType(EvalWorkbenchConstants.SCORE_DETERMINISTIC)
                .status(BATCH_RUNNING)
                .algorithmVersion(MetricResult.ALGORITHM_VERSION)
                .judgeConfigSnapshot("{}")
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
            insertScores(rows);

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
    public String scoreRagas(String runId) {
        return doScoreRagas(runId, false, null);
    }

    @Override
    public String submitRagasAsync(String runId, EvalRagasRescoreRequest request) {
        return doScoreRagas(runId, true, request);
    }

    @Override
    public void cancelRagasBatch(String runId, String batchId) {
        EvalScoreBatchDO batch = scoreBatchMapper.selectById(batchId);
        Assert.notNull(batch, () -> new ClientException("评分批次不存在"));
        if (!runId.equals(batch.getRunId())) {
            throw new ClientException("批次不属于该 Run");
        }
        if (!EvalWorkbenchConstants.SCORE_RAGAS.equals(batch.getScoreType())) {
            throw new ClientException("仅可取消 RAGAS 批次");
        }
        if (!BATCH_PENDING.equals(batch.getStatus()) && !BATCH_RUNNING.equals(batch.getStatus())) {
            throw new ClientException("批次已结束，无法取消");
        }
        if (StrUtil.isNotBlank(batch.getExternalJobId())) {
            semanticEvaluationProvider.cancel(batch.getExternalJobId());
        }
        failBatch(batchId, "用户取消");
    }

    private String doScoreRagas(String runId, boolean async, EvalRagasRescoreRequest request) {
        EvalRunDO run = runMapper.selectById(runId);
        Assert.notNull(run, () -> new ClientException("Run 不存在"));
        if (!Boolean.TRUE.equals(run.getRagasEnabled())) {
            if (async) {
                throw new ClientException("该 Run 未启用 RAGAS：请在创建评测时勾选「启用 RAGAS」后再试");
            }
            log.info("Run 未开启 ragasEnabled，跳过 RAGAS runId={}", runId);
            return null;
        }
        if (evalProperties.getRagas() == null || !evalProperties.getRagas().isEnabled()) {
            if (async) {
                throw new ClientException("全局 RAGAS 未启用：请将 ragent.eval.ragas.enabled / RAGAS_ENABLED 设为 true 并重启服务");
            }
            log.info("ragent.eval.ragas.enabled=false，跳过 RAGAS runId={}", runId);
            return null;
        }

        EvalScoreBatchDO active = findActiveRagasBatch(runId);
        if (active != null) {
            log.info("复用进行中的 RAGAS 批次 runId={} batchId={} status={}",
                    runId, active.getId(), active.getStatus());
            return active.getId();
        }

        ResolvedJudgeModels judgeModels = resolveJudgeModels(mergeRagasRequestWithRunPref(run, request));

        if (!semanticEvaluationProvider.isAvailable()) {
            String batchId = createRagasBatchSkeleton(runId, 0, "RAGAS service unavailable", judgeModels);
            failBatch(batchId, "RAGAS service unavailable or health check failed");
            return batchId;
        }

        List<EvalScoreSample> samples = loadSamples(run);
        int maxSamples = Math.max(1, evalProperties.getRagas().getMaxSamplesPerRun());
        if (samples.size() > maxSamples) {
            samples = samples.subList(0, maxSamples);
        }
        // 固定副本，避免异步线程看到被截断/复用的 list 视图问题
        List<EvalScoreSample> jobSamples = List.copyOf(samples);
        String batchId = createRagasBatchSkeleton(runId, jobSamples.size(), null, judgeModels);
        scoreBatchMapper.update(null, Wrappers.lambdaUpdate(EvalScoreBatchDO.class)
                .eq(EvalScoreBatchDO::getId, batchId)
                .set(EvalScoreBatchDO::getStatus, BATCH_RUNNING));
        // 不预写全 0 进度，避免前端在首次轮询前看到「跳过 0」覆盖真实口径

        if (async) {
            try {
                evalRagasExecutor.execute(() -> executeRagasJob(runId, batchId, jobSamples, judgeModels));
            } catch (RejectedExecutionException ex) {
                failBatch(batchId, "RAGAS executor saturated: " + ex.getMessage());
            }
            return batchId;
        }
        executeRagasJob(runId, batchId, jobSamples, judgeModels);
        return batchId;
    }

    private void executeRagasJob(String runId,
                                 String batchId,
                                 List<EvalScoreSample> samples,
                                 ResolvedJudgeModels judgeModels) {
        try {
            List<Map<String, Object>> records = toSnakeCaseRecords(samples);
            String idem = "run-" + runId + "-ragas-" + batchId;
            int ragasN = Math.max(1, Math.min(3, evalProperties.getRagas().getMaxIndependentRuns()));
            String jobId = semanticEvaluationProvider.submit(
                    idem,
                    records,
                    ragasN,
                    null,
                    judgeModels == null ? null : judgeModels.spec());
            scoreBatchMapper.update(null, Wrappers.lambdaUpdate(EvalScoreBatchDO.class)
                    .eq(EvalScoreBatchDO::getId, batchId)
                    .set(EvalScoreBatchDO::getExternalJobId, jobId)
                    .set(EvalScoreBatchDO::getStatus, BATCH_RUNNING));

            SemanticEvaluationProvider.JobSnapshot snap = pollUntilDone(batchId, jobId);
            persistRagasSnapshot(batchId, runId, samples, snap);
        } catch (Exception ex) {
            log.error("RAGAS 评分失败 runId={} batchId={}", runId, batchId, ex);
            failBatch(batchId, ex.getMessage());
        }
    }

    private EvalScoreBatchDO findActiveRagasBatch(String runId) {
        return scoreBatchMapper.selectOne(Wrappers.lambdaQuery(EvalScoreBatchDO.class)
                .eq(EvalScoreBatchDO::getRunId, runId)
                .eq(EvalScoreBatchDO::getScoreType, EvalWorkbenchConstants.SCORE_RAGAS)
                .in(EvalScoreBatchDO::getStatus, BATCH_PENDING, BATCH_RUNNING)
                .orderByDesc(EvalScoreBatchDO::getCreateTime)
                .last("LIMIT 1"));
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
    public EvalMetricReportVO getReport(String runId, String batchId, String scoreType) {
        requireRun(runId);
        EvalScoreBatchDO batch = resolveBatch(runId, batchId, scoreType);
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
            if (meta.get("is_pct") instanceof Boolean b) {
                pct = b;
            }
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
        List<EvalMetricReportVO.SampleFailureVO> failures =
                EvalWorkbenchConstants.SCORE_DETERMINISTIC.equals(batch.getScoreType())
                        ? buildFailures(samples, byMetric)
                        : List.of();

        return EvalMetricReportVO.builder()
                .runId(runId)
                .batchId(batch.getId())
                .scoreType(batch.getScoreType())
                .algorithmVersion(batch.getAlgorithmVersion())
                .status(batch.getStatus())
                .sampleCount(batch.getSampleCount())
                .intentTop1Note(null)
                .metrics(metrics)
                .failures(failures)
                .build();
    }

    @Override
    public byte[] exportReport(String runId, String batchId, String format) {
        EvalMetricReportVO report = getReport(runId, batchId, null);
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
                    .retrievedChunkIds(EvalJsonSupport.toStringList(r.getRetrievedChunkIds()))
                    .retrievedContexts(EvalJsonSupport.toStringList(r.getRetrievedContexts()))
                    .retrievedContextDocIds(EvalJsonSupport.toStringList(r.getRetrievedContextDocIds()))
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

    private EvalScoreBatchDO resolveBatch(String runId, String batchId, String scoreType) {
        if (StrUtil.isNotBlank(batchId)) {
            EvalScoreBatchDO batch = scoreBatchMapper.selectById(batchId);
            Assert.notNull(batch, () -> new ClientException("评分批次不存在"));
            Assert.isTrue(runId.equals(batch.getRunId()), () -> new ClientException("批次不属于该 Run"));
            return batch;
        }
        String type = StrUtil.blankToDefault(scoreType, EvalWorkbenchConstants.SCORE_DETERMINISTIC);
        EvalScoreBatchDO latest = scoreBatchMapper.selectOne(Wrappers.lambdaQuery(EvalScoreBatchDO.class)
                .eq(EvalScoreBatchDO::getRunId, runId)
                .eq(EvalScoreBatchDO::getScoreType, type)
                .in(EvalScoreBatchDO::getStatus, BATCH_COMPLETED, BATCH_PARTIAL, BATCH_FAILED, BATCH_RUNNING, BATCH_PENDING)
                .orderByDesc(EvalScoreBatchDO::getCreateTime)
                .last("LIMIT 1"));
        Assert.notNull(latest, () -> new ClientException("尚无评分批次：" + type));
        return latest;
    }

    private String createRagasBatchSkeleton(String runId,
                                            int sampleCount,
                                            String error,
                                            ResolvedJudgeModels judgeModels) {
        String batchId = IdUtil.getSnowflakeNextIdStr();
        Map<String, Object> judgeSnap = new LinkedHashMap<>();
        judgeSnap.put("provider", "ragenteval-http");
        judgeSnap.put("endpoint", evalProperties.getRagas() == null ? null : evalProperties.getRagas().getEndpoint());
        judgeSnap.put("maxIndependentRuns", evalProperties.getRagas() == null ? 1 : evalProperties.getRagas().getMaxIndependentRuns());
        if (judgeModels != null) {
            SemanticEvaluationProvider.JudgeEndpointSpec spec = judgeModels.spec();
            putSnap(judgeSnap, "chatModelId", spec.chatModelId());
            putSnap(judgeSnap, "chatModel", spec.chatModel());
            putSnap(judgeSnap, "chatProvider", spec.chatProvider());
            putSnap(judgeSnap, "chatBaseUrl", spec.chatBaseUrl());
            putSnap(judgeSnap, "embeddingModelId", spec.embeddingModelId());
            putSnap(judgeSnap, "embeddingModel", spec.embeddingModel());
            putSnap(judgeSnap, "embeddingProvider", spec.embeddingProvider());
            putSnap(judgeSnap, "embeddingBaseUrl", spec.embeddingBaseUrl());
            // 不落库明文 api key
        }
        EvalScoreBatchDO batch = EvalScoreBatchDO.builder()
                .id(batchId)
                .runId(runId)
                .scoreType(EvalWorkbenchConstants.SCORE_RAGAS)
                .status(BATCH_PENDING)
                .algorithmVersion("ragas-1.0.0")
                .judgeConfigSnapshot(JSONUtil.toJsonStr(judgeSnap))
                .sampleCount(sampleCount)
                .tokenUsage("{}")
                .startedAt(new Date())
                .errorMessage(error)
                .build();
        scoreBatchMapper.insert(batch);
        return batchId;
    }

    private static void putSnap(Map<String, Object> snap, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            snap.put(key, value);
        }
    }

    /**
     * 弹窗显式选择优先；否则回退创建 Run 时写入 configSnapshot.ragas 的模型偏好。
     */
    private EvalRagasRescoreRequest mergeRagasRequestWithRunPref(EvalRunDO run, EvalRagasRescoreRequest request) {
        String chatId = request == null ? null : StrUtil.trimToNull(request.getChatModelId());
        String embId = request == null ? null : StrUtil.trimToNull(request.getEmbeddingModelId());
        if (chatId != null && embId != null) {
            return request;
        }
        Map<String, Object> snap = EvalJsonSupport.toMap(run.getConfigSnapshot());
        Object ragasObj = snap.get("ragas");
        if (!(ragasObj instanceof Map<?, ?> raw)) {
            return request;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ragas = (Map<String, Object>) raw;
        EvalRagasRescoreRequest merged = request == null ? new EvalRagasRescoreRequest() : request;
        if (chatId == null) {
            Object v = ragas.get("chatModelId");
            if (v != null && StrUtil.isNotBlank(String.valueOf(v))) {
                merged.setChatModelId(String.valueOf(v).trim());
            }
        }
        if (embId == null) {
            Object v = ragas.get("embeddingModelId");
            if (v != null && StrUtil.isNotBlank(String.valueOf(v))) {
                merged.setEmbeddingModelId(String.valueOf(v).trim());
            }
        }
        return merged;
    }

    /**
     * 解析 Judge 端点。request 为空（Worker 自动 RAGAS）或字段为空时，回退
     * {@code judge-chat.default-model} / {@code ai.embedding.default-model}，
     * 与弹窗路径一致地下发 base_url + api_key，避免仅依赖 Python 环境默认。
     */
    private ResolvedJudgeModels resolveJudgeModels(EvalRagasRescoreRequest request) {
        String chatId = request == null ? null : StrUtil.trimToNull(request.getChatModelId());
        String embId = request == null ? null : StrUtil.trimToNull(request.getEmbeddingModelId());
        if (chatId == null) {
            chatId = defaultJudgeChatModelId();
        }
        if (embId == null) {
            embId = defaultEmbeddingModelId();
        }
        if (chatId == null && embId == null) {
            return null;
        }

        String chatProvider = null;
        String chatModel = null;
        String chatBaseUrl = null;
        String chatApiKey = null;
        if (chatId != null) {
            EvalProperties.JudgeModelCandidate chatCand = requireJudgeChatCandidate(chatId);
            chatProvider = StrUtil.trimToNull(chatCand.getProvider());
            chatModel = StrUtil.trimToNull(chatCand.getModel());
            ProviderEndpoint chatEp = resolveProviderEndpoint(chatProvider, null, ModelCapability.CHAT);
            chatBaseUrl = chatEp.baseUrl();
            chatApiKey = chatEp.apiKey();
        }

        String embProvider = null;
        String embModel = null;
        String embBaseUrl = null;
        String embApiKey = null;
        if (embId != null) {
            AIModelProperties.ModelCandidate embCand = requireEmbeddingCandidate(embId);
            embProvider = StrUtil.trimToNull(embCand.getProvider());
            embModel = StrUtil.trimToNull(embCand.getModel());
            ProviderEndpoint embEp = resolveProviderEndpoint(embProvider, embCand, ModelCapability.EMBEDDING);
            embBaseUrl = embEp.baseUrl();
            embApiKey = embEp.apiKey();
        }

        return new ResolvedJudgeModels(new SemanticEvaluationProvider.JudgeEndpointSpec(
                chatId,
                chatModel,
                chatProvider,
                chatBaseUrl,
                chatApiKey,
                embId,
                embModel,
                embProvider,
                embBaseUrl,
                embApiKey
        ));
    }

    private String defaultJudgeChatModelId() {
        EvalProperties.JudgeChat judgeChat = evalProperties.getRagas() == null
                ? null
                : evalProperties.getRagas().getJudgeChat();
        if (judgeChat == null || judgeChat.getCandidates() == null || judgeChat.getCandidates().isEmpty()) {
            return null;
        }
        String preferred = StrUtil.trimToNull(judgeChat.getDefaultModel());
        if (preferred != null && judgeChat.getCandidates().stream()
                .anyMatch(c -> c != null && preferred.equals(c.getId()) && !Boolean.FALSE.equals(c.getEnabled()))) {
            return preferred;
        }
        return judgeChat.getCandidates().stream()
                .filter(c -> c != null && StrUtil.isNotBlank(c.getId()) && !Boolean.FALSE.equals(c.getEnabled()))
                .map(EvalProperties.JudgeModelCandidate::getId)
                .findFirst()
                .orElse(null);
    }

    private String defaultEmbeddingModelId() {
        AIModelProperties.ModelGroup group = aiModelProperties.getEmbedding();
        if (group == null || group.getCandidates() == null || group.getCandidates().isEmpty()) {
            return null;
        }
        String preferred = StrUtil.trimToNull(group.getDefaultModel());
        if (preferred != null && group.getCandidates().stream()
                .anyMatch(c -> c != null && preferred.equals(c.getId()) && !Boolean.FALSE.equals(c.getEnabled()))) {
            return preferred;
        }
        return group.getCandidates().stream()
                .filter(c -> c != null && StrUtil.isNotBlank(c.getId()) && !Boolean.FALSE.equals(c.getEnabled()))
                .map(AIModelProperties.ModelCandidate::getId)
                .findFirst()
                .orElse(null);
    }

    private ProviderEndpoint resolveProviderEndpoint(String providerName,
                                                     AIModelProperties.ModelCandidate candidate,
                                                     ModelCapability capability) {
        if (StrUtil.isBlank(providerName)) {
            throw new ClientException("模型未配置 provider");
        }
        AIModelProperties.ProviderConfig provider = aiModelProperties.getProviders() == null
                ? null
                : aiModelProperties.getProviders().get(providerName);
        if (provider == null) {
            throw new ClientException("未知 AI provider: " + providerName);
        }
        try {
            String fullUrl = ModelUrlResolver.resolveUrl(provider, candidate, capability);
            String baseUrl = toOpenAiCompatibleBase(fullUrl);
            String apiKey = StrUtil.blankToDefault(StrUtil.trimToNull(provider.getApiKey()), "EMPTY");
            return new ProviderEndpoint(baseUrl, apiKey);
        } catch (IllegalStateException ex) {
            throw new ClientException("解析 " + capability.name().toLowerCase()
                    + " endpoint 失败（provider=" + providerName + "）: " + ex.getMessage());
        }
    }

    /**
     * LangChain OpenAI 客户端需要的是 base（…/v1），不是完整 /chat/completions 或 /embeddings。
     */
    static String toOpenAiCompatibleBase(String fullOrBase) {
        String u = StrUtil.removeSuffix(StrUtil.trimToEmpty(fullOrBase), "/");
        if (u.endsWith("/chat/completions")) {
            return u.substring(0, u.length() - "/chat/completions".length());
        }
        if (u.endsWith("/embeddings")) {
            return u.substring(0, u.length() - "/embeddings".length());
        }
        return u;
    }

    private EvalProperties.JudgeModelCandidate requireJudgeChatCandidate(String modelId) {
        EvalProperties.JudgeChat judgeChat = evalProperties.getRagas() == null
                ? null
                : evalProperties.getRagas().getJudgeChat();
        if (judgeChat == null || judgeChat.getCandidates() == null || judgeChat.getCandidates().isEmpty()) {
            throw new ClientException("未配置 RAGAS Judge 聊天模型（ragent.eval.ragas.judge-chat）");
        }
        return judgeChat.getCandidates().stream()
                .filter(c -> c != null && modelId.equals(c.getId()))
                .filter(c -> !Boolean.FALSE.equals(c.getEnabled()))
                .findFirst()
                .orElseThrow(() -> new ClientException("无效或已禁用的 Judge 聊天模型: " + modelId));
    }

    private AIModelProperties.ModelCandidate requireEmbeddingCandidate(String modelId) {
        AIModelProperties.ModelGroup group = aiModelProperties.getEmbedding();
        if (group == null || group.getCandidates() == null) {
            throw new ClientException("未配置 embedding 模型候选");
        }
        return group.getCandidates().stream()
                .filter(c -> c != null && modelId.equals(c.getId()))
                .filter(c -> !Boolean.FALSE.equals(c.getEnabled()))
                .findFirst()
                .orElseThrow(() -> new ClientException("无效或已禁用的 embedding 模型: " + modelId));
    }

    @Override
    public EvalRagasJudgeModelsVO listRagasJudgeModels() {
        EvalProperties.JudgeChat judgeChat = evalProperties.getRagas() == null
                ? new EvalProperties.JudgeChat()
                : evalProperties.getRagas().getJudgeChat();
        if (judgeChat == null) {
            judgeChat = new EvalProperties.JudgeChat();
        }
        List<EvalRagasJudgeModelsVO.ModelCandidateVO> chatCandidates = new ArrayList<>();
        if (judgeChat.getCandidates() != null) {
            for (EvalProperties.JudgeModelCandidate c : judgeChat.getCandidates()) {
                if (c == null || StrUtil.isBlank(c.getId()) || Boolean.FALSE.equals(c.getEnabled())) {
                    continue;
                }
                chatCandidates.add(EvalRagasJudgeModelsVO.ModelCandidateVO.builder()
                        .id(c.getId())
                        .provider(c.getProvider())
                        .model(c.getModel())
                        .enabled(c.getEnabled())
                        .build());
            }
        }

        AIModelProperties.ModelGroup embGroup = aiModelProperties.getEmbedding();
        List<EvalRagasJudgeModelsVO.ModelCandidateVO> embCandidates = new ArrayList<>();
        if (embGroup != null && embGroup.getCandidates() != null) {
            for (AIModelProperties.ModelCandidate c : embGroup.getCandidates()) {
                if (c == null || StrUtil.isBlank(c.getId()) || Boolean.FALSE.equals(c.getEnabled())) {
                    continue;
                }
                embCandidates.add(EvalRagasJudgeModelsVO.ModelCandidateVO.builder()
                        .id(c.getId())
                        .provider(c.getProvider())
                        .model(c.getModel())
                        .enabled(c.getEnabled())
                        .build());
            }
        }

        return EvalRagasJudgeModelsVO.builder()
                .chat(EvalRagasJudgeModelsVO.ModelGroupVO.builder()
                        .defaultModel(judgeChat.getDefaultModel())
                        .candidates(chatCandidates)
                        .build())
                .embedding(EvalRagasJudgeModelsVO.ModelGroupVO.builder()
                        .defaultModel(embGroup == null ? null : embGroup.getDefaultModel())
                        .candidates(embCandidates)
                        .build())
                .build();
    }

    private record ProviderEndpoint(String baseUrl, String apiKey) {
    }

    private record ResolvedJudgeModels(SemanticEvaluationProvider.JudgeEndpointSpec spec) {
    }

    private void failBatch(String batchId, String message) {
        EvalScoreBatchDO cur = scoreBatchMapper.selectById(batchId);
        if (cur != null && BATCH_FAILED.equals(cur.getStatus()) && isUserCancelledMessage(cur.getErrorMessage())) {
            return;
        }
        scoreBatchMapper.update(null, Wrappers.lambdaUpdate(EvalScoreBatchDO.class)
                .eq(EvalScoreBatchDO::getId, batchId)
                .set(EvalScoreBatchDO::getStatus, BATCH_FAILED)
                .set(EvalScoreBatchDO::getFinishedAt, new Date())
                .set(EvalScoreBatchDO::getErrorMessage, StrUtil.maxLength(message, 500)));
    }

    private static boolean isUserCancelledMessage(String message) {
        return message != null && message.contains("用户取消");
    }

    private SemanticEvaluationProvider.JobSnapshot pollUntilDone(String batchId, String jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis()
                + Math.max(60_000L, evalProperties.getRagas().getTimeoutSeconds() * 1000L * 10);
        long sleep = Math.max(1_000L, evalProperties.getRagas().getPollIntervalSeconds() * 1000L);
        int consecutiveFailures = 0;
        while (System.currentTimeMillis() < deadline) {
            EvalScoreBatchDO cur = scoreBatchMapper.selectById(batchId);
            if (cur != null && BATCH_FAILED.equals(cur.getStatus()) && isUserCancelledMessage(cur.getErrorMessage())) {
                throw new ClientException("用户取消");
            }
            try {
                SemanticEvaluationProvider.JobSnapshot snap = semanticEvaluationProvider.poll(jobId);
                consecutiveFailures = 0;
                writeProgress(batchId,
                        snap.total(),
                        snap.completed(),
                        snap.failed(),
                        snap.skipped(),
                        snap.evaluable(),
                        snap.workTotal(),
                        snap.workCompleted());
                if (snap.terminal()) {
                    return snap;
                }
            } catch (Exception ex) {
                if (ex instanceof ClientException && isUserCancelledMessage(ex.getMessage())) {
                    throw ex;
                }
                consecutiveFailures++;
                String human = humanizeRagasPollError(ex);
                log.warn("RAGAS poll 异常 jobId={} attempt={}: {}", jobId, consecutiveFailures, ex.getMessage());
                // 404 / 连接失败：服务中断或进程重启后内存 job 丢失
                if (consecutiveFailures >= 2 || isFatalRagasPollError(ex)) {
                    throw new ClientException(human);
                }
            }
            Thread.sleep(sleep);
        }
        try {
            semanticEvaluationProvider.cancel(jobId);
        } catch (Exception ignored) {
            // ignore
        }
        throw new ClientException("RAGAS 评分超时（评分服务可能仍在运行或已中断）jobId=" + jobId);
    }

    private static boolean isFatalRagasPollError(Throwable ex) {
        String msg = StrUtil.blankToDefault(ex.getMessage(), "").toLowerCase();
        return msg.contains("404")
                || msg.contains("connection refused")
                || msg.contains("connect timed out")
                || msg.contains("connection reset")
                || msg.contains("unknownhost")
                || msg.contains("failed to connect")
                || msg.contains("remotenotconnected");
    }

    private static String humanizeRagasPollError(Throwable ex) {
        String raw = StrUtil.blankToDefault(ex.getMessage(), ex.getClass().getSimpleName());
        String lower = raw.toLowerCase();
        if (lower.contains("404") || lower.contains("not found")) {
            return "RAGAS 评分服务中断或已重启，任务丢失（job 不存在）: " + raw;
        }
        if (lower.contains("connection refused")
                || lower.contains("connect timed out")
                || lower.contains("connection reset")
                || lower.contains("failed to connect")
                || lower.contains("remotenotconnected")
                || lower.contains("unknownhost")) {
            return "RAGAS 评分服务连接中断（进程可能已停止）: " + raw;
        }
        return "RAGAS 评分轮询失败: " + raw;
    }

    private void writeProgress(String batchId,
                               Integer total,
                               Integer completed,
                               Integer failed,
                               Integer skipped,
                               Integer evaluable,
                               Integer workTotal,
                               Integer workCompleted) {
        EvalScoreBatchDO batch = scoreBatchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        Map<String, Object> judge = new LinkedHashMap<>(EvalJsonSupport.toMap(batch.getJudgeConfigSnapshot()));
        Map<String, Object> progress = new LinkedHashMap<>();
        if (total != null) {
            progress.put("total", total);
        }
        if (completed != null) {
            progress.put("completed", completed);
        }
        if (failed != null) {
            progress.put("failed", failed);
        }
        if (skipped != null) {
            progress.put("skipped", skipped);
        }
        if (evaluable != null) {
            progress.put("evaluable", evaluable);
        }
        if (workTotal != null) {
            progress.put("work_total", workTotal);
        }
        if (workCompleted != null) {
            progress.put("work_completed", workCompleted);
        }
        // 合并已有进度，避免偶发 null 字段把已写入的 skipped 冲掉
        Object prev = judge.get("progress");
        if (prev instanceof Map<?, ?> prevMap) {
            for (Map.Entry<?, ?> e : prevMap.entrySet()) {
                if (e.getKey() != null && e.getValue() != null && !progress.containsKey(String.valueOf(e.getKey()))) {
                    progress.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        judge.put("progress", progress);
        batch.setJudgeConfigSnapshot(JSONUtil.toJsonStr(judge));
        scoreBatchMapper.updateById(batch);
    }

    private void persistRagasSnapshot(String batchId,
                                      String runId,
                                      List<EvalScoreSample> samples,
                                      SemanticEvaluationProvider.JobSnapshot snap) {
        Map<String, EvalScoreSample> byQuery = samples.stream()
                .collect(Collectors.toMap(
                        s -> StrUtil.blankToDefault(s.getQueryId(), s.getRecordId()),
                        s -> s,
                        (a, b) -> a,
                        LinkedHashMap::new));

        List<EvalScoreDO> rows = new ArrayList<>();
        if (snap.metrics() != null) {
            for (Map<String, Object> m : snap.metrics()) {
                rows.addAll(toScoreRows(batchId, runId, fromRagasMetricMap(m), byQuery));
            }
        }
        insertScores(rows);

        EvalScoreBatchDO cur = scoreBatchMapper.selectById(batchId);
        if (cur != null && BATCH_FAILED.equals(cur.getStatus()) && isUserCancelledMessage(cur.getErrorMessage())) {
            return;
        }
        String status;
        String errorMessage = snap.errorMessage();
        if ("CANCELLED".equals(snap.status())) {
            status = BATCH_FAILED;
            errorMessage = StrUtil.blankToDefault(errorMessage, "用户取消");
        } else if (snap.successLike()) {
            status = "PARTIAL_SUCCESS".equals(snap.status()) ? BATCH_PARTIAL : BATCH_COMPLETED;
        } else {
            status = BATCH_FAILED;
        }
        writeProgress(batchId,
                snap.total(),
                snap.completed(),
                snap.failed(),
                snap.skipped(),
                snap.evaluable(),
                snap.workTotal(),
                snap.workCompleted());
        Map<String, Object> token = snap.tokenUsage() == null ? Map.of() : snap.tokenUsage();
        // lambdaUpdate().set() 不会走 JsonbTypeHandler，需 CAST 为 jsonb
        scoreBatchMapper.update(null, Wrappers.lambdaUpdate(EvalScoreBatchDO.class)
                .eq(EvalScoreBatchDO::getId, batchId)
                .set(EvalScoreBatchDO::getStatus, status)
                .set(EvalScoreBatchDO::getFinishedAt, new Date())
                .setSql("token_usage = CAST({0} AS jsonb)", JSONUtil.toJsonStr(token))
                .set(EvalScoreBatchDO::getEstimatedCost, snap.estimatedCostUsd() == null
                        ? null : BigDecimal.valueOf(snap.estimatedCostUsd()).setScale(6, RoundingMode.HALF_UP))
                .set(EvalScoreBatchDO::getErrorMessage, errorMessage));
    }

    private void insertScores(List<EvalScoreDO> rows) {
        if (rows.isEmpty()) {
            return;
        }
        boolean inserted = SqlHelper.executeBatch(EvalScoreDO.class, BATCH_LOG, rows, SCORE_INSERT_BATCH_SIZE,
                (sqlSession, row) -> sqlSession.getMapper(EvalScoreMapper.class).insert(row));
        Assert.isTrue(inserted, () -> new ClientException("批量写入评测分数失败"));
    }

    @SuppressWarnings("unchecked")
    private MetricResult fromRagasMetricMap(Map<String, Object> m) {
        String name = String.valueOf(m.getOrDefault("name", "unknown"));
        Double overall = m.get("overall") instanceof Number n ? n.doubleValue() : null;
        boolean pct = m.get("is_pct") == null || Boolean.TRUE.equals(m.get("is_pct"));
        Map<String, Double> l1 = toDoubleMap(m.get("by_intent_l1"));
        Map<String, Double> l2 = toDoubleMap(m.get("by_intent_l2"));
        Map<String, Double> diff = toDoubleMap(m.get("by_difficulty"));
        Map<String, Double> per = toDoubleMap(m.get("per_sample"));
        Map<String, Object> meta = m.get("meta") instanceof Map<?, ?> mm
                ? new LinkedHashMap<>((Map<String, Object>) mm)
                : new LinkedHashMap<>();
        meta.put("pct", pct);
        meta.put("is_pct", pct);
        return MetricResult.builder()
                .name(name)
                .algorithmVersion(String.valueOf(m.getOrDefault("algorithm_version", "ragas-1.0.0")))
                .overall(overall)
                .byIntentL1(l1)
                .byIntentL2(l2)
                .byDifficulty(diff)
                .perSample(per)
                .meta(meta)
                .pct(pct)
                .build();
    }

    private Map<String, Double> toDoubleMap(Object raw) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            if (e.getValue() instanceof Number n) {
                out.put(String.valueOf(e.getKey()), n.doubleValue());
            } else {
                out.put(String.valueOf(e.getKey()), null);
            }
        }
        return out;
    }

    private List<Map<String, Object>> toSnakeCaseRecords(List<EvalScoreSample> samples) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EvalScoreSample s : samples) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("query_id", StrUtil.blankToDefault(s.getQueryId(), s.getRecordId()));
            row.put("user_input", StrUtil.blankToDefault(s.getQuestion(), ""));
            row.put("reference", StrUtil.blankToDefault(s.getGroundTruth(), ""));
            row.put("reference_doc_ids", s.safeReference());
            row.put("reference_doc_ids_nice", s.safeNice());
            row.put("intent_l1", StrUtil.blankToDefault(s.getIntentL1(), ""));
            row.put("intent_l2", StrUtil.blankToDefault(s.getIntentL2(), ""));
            row.put("difficulty", StrUtil.blankToDefault(s.getDifficulty(), "medium"));
            row.put("requires_rag", s.isRequiresRag());
            row.put("response", StrUtil.blankToDefault(s.getResponse(), ""));
            row.put("thinking", null);
            row.put("latency_ms", s.getTotalLatencyMs() == null ? 0 : s.getTotalLatencyMs().intValue());
            row.put("first_token_ms", s.getTtftMs());
            row.put("final_status", StrUtil.blankToDefault(s.getRecordStatus(), "unknown"));
            row.put("error", null);
            row.put("conversation_id", null);
            row.put("task_id", null);
            row.put("retrieved_doc_ids", s.safeRetrieved());
            row.put("retrieved_doc_ids_raw", s.safeRetrieved());
            row.put("retrieved_chunk_ids", s.safeChunkIds());
            row.put("retrieved_contexts", s.safeContexts());
            row.put("retrieved_context_doc_ids", s.safeContextDocIds());
            row.put("intent_pred", s.getIntentPred());
            row.put("intent_pred_all", s.getIntentPred() == null ? List.of() : List.of(s.getIntentPred()));
            row.put("has_kb", s.getHasKb());
            row.put("has_mcp", null);
            row.put("trace_id", s.getTraceId());
            row.put("retrieval_skipped", Boolean.TRUE.equals(s.getRetrievalSkipped()));
            row.put("skip_reason", null);
            out.add(row);
        }
        return out;
    }

    private EvalRunDO requireRun(String runId) {
        EvalRunDO run = runMapper.selectById(runId);
        Assert.notNull(run, () -> new ClientException("Run 不存在"));
        return run;
    }

    @SuppressWarnings("unchecked")
    private EvalScoreBatchVO toBatchVO(EvalScoreBatchDO batch) {
        Map<String, Object> judge = new LinkedHashMap<>(EvalJsonSupport.toMap(batch.getJudgeConfigSnapshot()));
        Map<String, Object> progress = judge.get("progress") instanceof Map<?, ?> raw
                ? new LinkedHashMap<>((Map<String, Object>) raw)
                : Map.of();
        return EvalScoreBatchVO.builder()
                .id(batch.getId())
                .runId(batch.getRunId())
                .scoreType(batch.getScoreType())
                .status(batch.getStatus())
                .algorithmVersion(batch.getAlgorithmVersion())
                .sampleCount(batch.getSampleCount())
                .externalJobId(batch.getExternalJobId())
                .judgeConfigSnapshot(judge)
                .progressTotal(asInt(progress.get("total")))
                .progressCompleted(asInt(progress.get("completed")))
                .progressFailed(asInt(progress.get("failed")))
                .progressSkipped(asInt(progress.get("skipped")))
                .progressEvaluable(asInt(progress.get("evaluable")))
                .progressWorkTotal(asInt(progress.get("work_total")))
                .progressWorkCompleted(asInt(progress.get("work_completed")))
                .tokenUsage(EvalJsonSupport.toMap(batch.getTokenUsage()))
                .estimatedCost(batch.getEstimatedCost())
                .startedAt(batch.getStartedAt())
                .finishedAt(batch.getFinishedAt())
                .errorMessage(batch.getErrorMessage())
                .createTime(batch.getCreateTime())
                .build();
    }

    private static Integer asInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && StrUtil.isNotBlank(s)) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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

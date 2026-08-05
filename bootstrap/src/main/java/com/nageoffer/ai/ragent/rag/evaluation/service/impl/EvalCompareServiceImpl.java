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
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.evaluation.constant.EvalWorkbenchConstants;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalMetricReportVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRunCompareVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRunVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalScoreBatchVO;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalCompareService;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalRunService;
import com.nageoffer.ai.ragent.rag.evaluation.service.EvalScoreService;
import com.nageoffer.ai.ragent.rag.evaluation.support.EvalRunCompareSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ragent.eval", name = "workbench-enabled", havingValue = "true")
public class EvalCompareServiceImpl implements EvalCompareService {

    private final EvalRunService evalRunService;
    private final EvalScoreService evalScoreService;

    @Override
    public EvalRunCompareVO compare(String runId, String baselineRunId) {
        Assert.notBlank(runId, () -> new ClientException("runId 不能为空"));
        Assert.notBlank(baselineRunId, () -> new ClientException("baselineRunId 不能为空"));
        Assert.isFalse(runId.equals(baselineRunId), () -> new ClientException("不能与自身对比"));

        EvalRunVO current = evalRunService.getRun(runId);
        EvalRunVO baseline = evalRunService.getRun(baselineRunId);
        Assert.isTrue(Objects.equals(current.getDatasetVersionId(), baseline.getDatasetVersionId()),
                () -> new ClientException("仅支持相同数据集版本的 Run 对比"));

        EvalMetricReportVO detCurrent = evalScoreService.getReport(runId, null, EvalWorkbenchConstants.SCORE_DETERMINISTIC);
        EvalMetricReportVO detBaseline = evalScoreService.getReport(baselineRunId, null, EvalWorkbenchConstants.SCORE_DETERMINISTIC);
        List<EvalRunCompareVO.MetricDeltaVO> detMetrics =
                EvalRunCompareSupport.metricDeltas(detCurrent.getMetrics(), detBaseline.getMetrics());

        EvalMetricReportVO ragasCurrent = tryReport(runId, EvalWorkbenchConstants.SCORE_RAGAS);
        EvalMetricReportVO ragasBaseline = tryReport(baselineRunId, EvalWorkbenchConstants.SCORE_RAGAS);
        boolean ragasAvailable = ragasCurrent != null || ragasBaseline != null;
        List<EvalRunCompareVO.MetricDeltaVO> ragasMetrics = ragasAvailable
                ? EvalRunCompareSupport.metricDeltas(
                ragasCurrent == null ? List.of() : ragasCurrent.getMetrics(),
                ragasBaseline == null ? List.of() : ragasBaseline.getMetrics())
                : List.of();

        Map<String, Object> currentJudge = Map.of();
        Map<String, Object> baselineJudge = Map.of();
        if (ragasCurrent != null) {
            currentJudge = loadJudgeConfig(runId, ragasCurrent.getBatchId());
        } else {
            currentJudge = loadJudgeConfig(runId, null);
        }
        if (ragasBaseline != null) {
            baselineJudge = loadJudgeConfig(baselineRunId, ragasBaseline.getBatchId());
        } else {
            baselineJudge = loadJudgeConfig(baselineRunId, null);
        }

        return EvalRunCompareVO.builder()
                .runId(runId)
                .baselineRunId(baselineRunId)
                .datasetVersionId(current.getDatasetVersionId())
                .current(toBrief(current))
                .baseline(toBrief(baseline))
                .deterministic(EvalRunCompareVO.ScoreSideVO.builder()
                        .scoreType(EvalWorkbenchConstants.SCORE_DETERMINISTIC)
                        .currentBatchId(detCurrent.getBatchId())
                        .baselineBatchId(detBaseline.getBatchId())
                        .available(true)
                        .metrics(detMetrics)
                        .ttft(EvalRunCompareSupport.ttftDelta(detMetrics))
                        .build())
                .ragas(EvalRunCompareVO.ScoreSideVO.builder()
                        .scoreType(EvalWorkbenchConstants.SCORE_RAGAS)
                        .currentBatchId(ragasCurrent == null ? null : ragasCurrent.getBatchId())
                        .baselineBatchId(ragasBaseline == null ? null : ragasBaseline.getBatchId())
                        .available(ragasAvailable)
                        .metrics(ragasMetrics)
                        .ttft(null)
                        .build())
                .currentJudgeConfig(currentJudge.isEmpty() ? null : currentJudge)
                .baselineJudgeConfig(baselineJudge.isEmpty() ? null : baselineJudge)
                .judgeConfigDiff(EvalRunCompareSupport.configDiff(currentJudge, baselineJudge))
                .configDiff(EvalRunCompareSupport.configDiff(
                        current.getConfigSnapshot() == null ? Map.of() : current.getConfigSnapshot(),
                        baseline.getConfigSnapshot() == null ? Map.of() : baseline.getConfigSnapshot()))
                .failures(EvalRunCompareSupport.failureRegression(detCurrent.getFailures(), detBaseline.getFailures()))
                .build();
    }

    private EvalMetricReportVO tryReport(String runId, String scoreType) {
        try {
            return evalScoreService.getReport(runId, null, scoreType);
        } catch (ClientException ex) {
            return null;
        }
    }

    private Map<String, Object> loadJudgeConfig(String runId, String batchId) {
        if (StrUtil.isNotBlank(batchId)) {
            Map<String, Object> fromBatch = evalScoreService.listBatches(runId).stream()
                    .filter(b -> batchId.equals(b.getId()))
                    .map(EvalScoreBatchVO::getJudgeConfigSnapshot)
                    .findFirst()
                    .map(EvalRunCompareSupport::sanitizeJudgeConfig)
                    .orElse(Map.of());
            if (hasJudgeModel(fromBatch)) {
                return fromBatch;
            }
        }
        return evalScoreService.listBatches(runId).stream()
                .filter(b -> EvalWorkbenchConstants.SCORE_RAGAS.equals(b.getScoreType()))
                .map(EvalScoreBatchVO::getJudgeConfigSnapshot)
                .map(EvalRunCompareSupport::sanitizeJudgeConfig)
                .filter(EvalCompareServiceImpl::hasJudgeModel)
                .findFirst()
                .orElse(Map.of());
    }

    private static boolean hasJudgeModel(Map<String, Object> snap) {
        if (snap == null || snap.isEmpty()) {
            return false;
        }
        return snap.get("chatModel") != null
                || snap.get("chatModelId") != null
                || snap.get("embeddingModel") != null
                || snap.get("embeddingModelId") != null;
    }

    private EvalRunCompareVO.RunBriefVO toBrief(EvalRunVO run) {
        return EvalRunCompareVO.RunBriefVO.builder()
                .runId(run.getId())
                .name(run.getName())
                .datasetVersionId(run.getDatasetVersionId())
                .datasetVersion(run.getDatasetVersion())
                .status(run.getStatus())
                .qualityVerdict(run.getQualityVerdict())
                .configSnapshot(run.getConfigSnapshot())
                .build();
    }
}

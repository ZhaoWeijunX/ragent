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

package com.nageoffer.ai.ragent.rag.evaluation.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 同数据集版本 Run A/B 对比结果（自建 + RAGAS 同页）。
 */
@Data
@Builder
public class EvalRunCompareVO {

    private String runId;
    private String baselineRunId;
    private String datasetVersionId;

    private RunBriefVO current;
    private RunBriefVO baseline;

    /** 自建指标对比（应始终有） */
    private ScoreSideVO deterministic;
    /** RAGAS 对比；任一侧无批次时 metrics 为空，仍可能带有另一侧 Judge */
    private ScoreSideVO ragas;

    /** RAGAS Judge 模型快照（不含 api key / progress） */
    private Map<String, Object> currentJudgeConfig;
    private Map<String, Object> baselineJudgeConfig;
    private List<ConfigDiffItemVO> judgeConfigDiff;

    /** Run 创建时冻结的 configSnapshot 扁平 diff（模型 / 检索 / 知识指纹等） */
    private List<ConfigDiffItemVO> configDiff;

    /** 失败回归来自自建报告口径 */
    private FailureRegressionVO failures;

    @Data
    @Builder
    public static class ScoreSideVO {
        private String scoreType;
        private String currentBatchId;
        private String baselineBatchId;
        private Boolean available;
        private List<MetricDeltaVO> metrics;
        private TtftDeltaVO ttft;
    }

    @Data
    @Builder
    public static class RunBriefVO {
        private String runId;
        private String name;
        private String datasetVersionId;
        private String datasetVersion;
        private String status;
        private String qualityVerdict;
        private Map<String, Object> configSnapshot;
    }

    @Data
    @Builder
    public static class ConfigDiffItemVO {
        private String path;
        private Object current;
        private Object baseline;
    }

    @Data
    @Builder
    public static class MetricDeltaVO {
        private String name;
        private Boolean pct;
        private Double current;
        private Double baseline;
        private Double absoluteDelta;
        private Double relativeDelta;
        private List<SliceDeltaVO> byIntentL2;
        private List<SliceDeltaVO> byDifficulty;
    }

    @Data
    @Builder
    public static class SliceDeltaVO {
        private String key;
        private Double current;
        private Double baseline;
        private Double absoluteDelta;
        private Double relativeDelta;
    }

    @Data
    @Builder
    public static class ValueDeltaVO {
        private Double current;
        private Double baseline;
        private Double absoluteDelta;
        private Double relativeDelta;
    }

    @Data
    @Builder
    public static class TtftDeltaVO {
        private ValueDeltaVO p50;
        private ValueDeltaVO mean;
    }

    @Data
    @Builder
    public static class FailureBriefVO {
        private String recordId;
        private String queryId;
        private String intentL2;
        private List<String> failureReasons;
        private String traceId;
    }

    @Data
    @Builder
    public static class FailureRegressionVO {
        private List<FailureBriefVO> newFailures;
        private List<FailureBriefVO> fixedFailures;
        private List<FailureBriefVO> persistentFailures;
    }
}

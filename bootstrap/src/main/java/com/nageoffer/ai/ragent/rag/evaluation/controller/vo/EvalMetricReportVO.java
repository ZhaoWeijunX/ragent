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

@Data
@Builder
public class EvalMetricReportVO {
    private String runId;
    private String batchId;
    private String scoreType;
    private String algorithmVersion;
    private String status;
    private Integer sampleCount;
    private String intentTop1Note;
    private List<MetricItemVO> metrics;
    private List<SampleFailureVO> failures;

    @Data
    @Builder
    public static class MetricItemVO {
        private String name;
        private Double overall;
        private Boolean pct;
        private Integer sampleCount;
        private Map<String, Double> byIntentL1;
        private Map<String, Double> byIntentL2;
        private Map<String, Double> byDifficulty;
        private Map<String, Object> meta;
    }

    @Data
    @Builder
    public static class SampleFailureVO {
        private String recordId;
        private String queryId;
        private String question;
        private String response;
        private String groundTruth;
        private String status;
        private String intentPred;
        private String intentL2;
        private List<String> retrievedDocIds;
        private List<String> expectedDocIds;
        /** 期望有但 Top-K 未召回的文档 */
        private List<String> missedDocIds;
        /** 召回了但不在期望 must 中的文档 */
        private List<String> extraDocIds;
        private List<String> failureReasons;
        /** 失败原因：code + 中文短句 */
        private List<FailureReasonVO> failureDetails;
        private String errorCode;
        private String errorMessage;
        private String traceId;
        private Double hitAt5;
        private Double intentTop1;
    }

    @Data
    @Builder
    public static class FailureReasonVO {
        private String code;
        private String message;
    }
}

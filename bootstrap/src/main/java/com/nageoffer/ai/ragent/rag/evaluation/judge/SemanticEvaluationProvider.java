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

package com.nageoffer.ai.ragent.rag.evaluation.judge;

import java.util.List;
import java.util.Map;

/**
 * RAGAS / 语义评分适配器：对接独立 ragenteval HTTP 服务。
 */
public interface SemanticEvaluationProvider {

    boolean isAvailable();

    /**
     * 提交异步评分任务，返回外部 jobId。
     */
    String submit(String idempotencyKey,
                  List<Map<String, Object>> snakeCaseRecords,
                  int ragasN,
                  Integer ragasLimit,
                  JudgeEndpointSpec judge);

    /**
     * 查询任务；终态含 metrics。
     */
    JobSnapshot poll(String jobId);

    void cancel(String jobId);

    /**
     * Java 解析后的 Judge 端点（OpenAI-compatible base_url + key + model）。
     */
    record JudgeEndpointSpec(
            String chatModelId,
            String chatModel,
            String chatProvider,
            String chatBaseUrl,
            String chatApiKey,
            String embeddingModelId,
            String embeddingModel,
            String embeddingProvider,
            String embeddingBaseUrl,
            String embeddingApiKey
    ) {
        public boolean hasAny() {
            return (chatModel != null && !chatModel.isBlank())
                    || (embeddingModel != null && !embeddingModel.isBlank());
        }
    }

    record JobSnapshot(
            String jobId,
            String status,
            Integer total,
            Integer completed,
            Integer failed,
            Integer skipped,
            Integer evaluable,
            Integer workTotal,
            Integer workCompleted,
            List<Map<String, Object>> metrics,
            List<Map<String, Object>> sampleErrors,
            String errorMessage,
            Map<String, Object> tokenUsage,
            Double estimatedCostUsd
    ) {
        public boolean terminal() {
            return "SUCCEEDED".equals(status)
                    || "FAILED".equals(status)
                    || "CANCELLED".equals(status)
                    || "PARTIAL_SUCCESS".equals(status);
        }

        public boolean successLike() {
            return "SUCCEEDED".equals(status) || "PARTIAL_SUCCESS".equals(status);
        }
    }
}

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

package com.nageoffer.ai.ragent.rag.evaluation.controller.request;

import lombok.Data;

import java.util.Map;

@Data
public class EvalRunCreateRequest {

    private String name;

    private String datasetVersionId;

    private String baselineRunId;

    /**
     * 是否启用 RAGAS（允许详情页手动评分；与是否自动开始无关）。
     */
    private Boolean ragasEnabled;

    /**
     * 是否在录制并完成自建评分后自动开始 RAGAS。
     * 仅当 {@link #ragasEnabled} 为 true 时生效。
     */
    private Boolean ragasAutoStart;

    /**
     * 可选：RAGAS Judge 聊天模型候选 id（写入 configSnapshot；自动/手动评分均可回退使用）。
     */
    private String ragasChatModelId;

    /**
     * 可选：RAGAS 嵌入模型候选 id。
     */
    private String ragasEmbeddingModelId;

    /**
     * 可选标签：gitBranch / gitCommit / environment / appVersion / extra。
     */
    private Map<String, Object> tags;
}

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

import java.util.ArrayList;
import java.util.List;

/**
 * RAGAS 评分弹窗可选模型：Judge chat 来自 {@code ragent.eval.ragas.judge-chat}，embedding 来自 {@code ai.embedding}。
 */
@Data
@Builder
public class EvalRagasJudgeModelsVO {

    private ModelGroupVO chat;

    private ModelGroupVO embedding;

    @Data
    @Builder
    public static class ModelGroupVO {
        private String defaultModel;
        @Builder.Default
        private List<ModelCandidateVO> candidates = new ArrayList<>();
    }

    @Data
    @Builder
    public static class ModelCandidateVO {
        private String id;
        private String provider;
        private String model;
        private Boolean enabled;
    }
}

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

package com.nageoffer.ai.ragent.rag.evaluation.metric;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对齐 docs/evaluation/schemas/metric-result.schema.json 与 ragenteval MetricResult。
 */
@Data
@Builder
public class MetricResult {

    public static final String ALGORITHM_VERSION = "deterministic-1.0.0";

    private String name;
    private String algorithmVersion;
    private Double overall;
    @Builder.Default
    private Map<String, Double> byIntentL1 = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Double> byIntentL2 = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Double> byDifficulty = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Double> perSample = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Object> meta = new LinkedHashMap<>();
    private boolean pct;

    public static MetricResult empty(String name, boolean pct) {
        return MetricResult.builder()
                .name(name)
                .algorithmVersion(ALGORITHM_VERSION)
                .overall(null)
                .byIntentL1(Collections.emptyMap())
                .byIntentL2(Collections.emptyMap())
                .byDifficulty(Collections.emptyMap())
                .perSample(Collections.emptyMap())
                .meta(Map.of("sampleCount", 0))
                .pct(pct)
                .build();
    }
}

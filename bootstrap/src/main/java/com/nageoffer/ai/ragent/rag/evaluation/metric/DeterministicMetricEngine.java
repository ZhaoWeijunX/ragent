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

import com.nageoffer.ai.ragent.rag.evaluation.metric.impl.BehaviorMetrics;
import com.nageoffer.ai.ragent.rag.evaluation.metric.impl.IntentTop1Metric;
import com.nageoffer.ai.ragent.rag.evaluation.metric.impl.LatencyMetrics;
import com.nageoffer.ai.ragent.rag.evaluation.metric.impl.RetrievalMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DeterministicMetricEngine {

    private final IntentTop1Metric intentTop1Metric;
    private final RetrievalMetrics retrievalMetrics;
    private final BehaviorMetrics behaviorMetrics;
    private final LatencyMetrics latencyMetrics;

    public List<MetricResult> scoreAll(List<EvalScoreSample> samples) {
        List<MetricResult> results = new ArrayList<>();
        results.addAll(intentTop1Metric.compute(samples));
        results.addAll(retrievalMetrics.compute(samples));
        results.addAll(behaviorMetrics.compute(samples));
        results.addAll(latencyMetrics.compute(samples));
        return results;
    }
}

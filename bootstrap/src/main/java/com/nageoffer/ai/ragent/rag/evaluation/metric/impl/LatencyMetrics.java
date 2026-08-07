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

package com.nageoffer.ai.ragent.rag.evaluation.metric.impl;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.evaluation.metric.EvalMetric;
import com.nageoffer.ai.ragent.rag.evaluation.metric.EvalScoreSample;
import com.nageoffer.ai.ragent.rag.evaluation.metric.MetricResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LatencyMetrics implements EvalMetric {

    @Override
    public List<MetricResult> compute(List<EvalScoreSample> samples) {
        List<Long> ttfts = new ArrayList<>();
        List<Long> totals = new ArrayList<>();
        Map<String, Double> perTtft = new LinkedHashMap<>();
        for (EvalScoreSample s : samples) {
            Long ttft = firstTokenOrTotal(s);
            String qid = StrUtil.blankToDefault(s.getQueryId(), s.getRecordId());
            if (ttft != null) {
                ttfts.add(ttft);
                perTtft.put(qid, ttft.doubleValue());
            } else {
                perTtft.put(qid, null);
            }
            if (s.getTotalLatencyMs() != null) {
                totals.add(s.getTotalLatencyMs());
            }
        }
        Collections.sort(ttfts);
        Collections.sort(totals);

        return List.of(
                latencyResult("ttft_p50_ms", percentile(ttfts, 0.50), perTtft, ttfts.size()),
                latencyResult("ttft_mean_ms", mean(ttfts), perTtft, ttfts.size()),
                latencyResult("total_mean_ms", mean(totals), Map.of(), totals.size())
        );
    }

    private static Long firstTokenOrTotal(EvalScoreSample s) {
        if (s.getTtftMs() != null) {
            return s.getTtftMs();
        }
        return s.getTotalLatencyMs();
    }

    private static Double percentile(List<Long> sorted, double q) {
        if (sorted.isEmpty()) {
            return null;
        }
        int idx = Math.min(sorted.size() - 1, (int) (sorted.size() * q));
        return sorted.get(idx).doubleValue();
    }

    private static Double mean(List<Long> xs) {
        if (xs.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Long x : xs) {
            sum += x;
        }
        return sum / xs.size();
    }

    private static MetricResult latencyResult(String name, Double overall, Map<String, Double> perSample, int n) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sampleCount", n);
        meta.put("note", "样本量 < 500 时不宣称 P95/P99");
        return MetricResult.builder()
                .name(name)
                .algorithmVersion(MetricResult.ALGORITHM_VERSION)
                .overall(overall)
                .byIntentL1(Map.of())
                .byIntentL2(Map.of())
                .byDifficulty(Map.of())
                .perSample(perSample)
                .meta(meta)
                .pct(false)
                .build();
    }
}

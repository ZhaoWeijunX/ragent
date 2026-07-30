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

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 对齐 ragenteval slice_mean / is_retrieval_eligible。
 */
public final class MetricSupport {

    private MetricSupport() {
    }

    public static boolean isRetrievalEligible(EvalScoreSample s, boolean inclusive) {
        if (!s.isRequiresRag()) {
            return false;
        }
        if (inclusive) {
            return !s.safeReference().isEmpty() || !s.safeNice().isEmpty();
        }
        return !s.safeReference().isEmpty();
    }

    public static MetricResult sliceMean(String name,
                                         boolean pct,
                                         List<EvalScoreSample> samples,
                                         Function<EvalScoreSample, Double> valueFn,
                                         Predicate<EvalScoreSample> eligibleFn) {
        List<Double> overallVals = new ArrayList<>();
        Map<String, List<Double>> bucketL1 = new HashMap<>();
        Map<String, List<Double>> bucketL2 = new HashMap<>();
        Map<String, List<Double>> bucketDiff = new HashMap<>();
        Map<String, Double> perSample = new LinkedHashMap<>();
        int skipped = 0;

        for (EvalScoreSample s : samples) {
            String qid = StrUtil.blankToDefault(s.getQueryId(), s.getRecordId());
            if (eligibleFn != null && !eligibleFn.test(s)) {
                perSample.put(qid, null);
                skipped++;
                continue;
            }
            Double v = valueFn.apply(s);
            perSample.put(qid, v);
            if (v == null) {
                skipped++;
                continue;
            }
            overallVals.add(v);
            bucketL1.computeIfAbsent(StrUtil.blankToDefault(s.getIntentL1(), "?"), k -> new ArrayList<>()).add(v);
            bucketL2.computeIfAbsent(StrUtil.blankToDefault(s.getIntentL2(), "?"), k -> new ArrayList<>()).add(v);
            bucketDiff.computeIfAbsent(StrUtil.blankToDefault(s.getDifficulty(), "?"), k -> new ArrayList<>()).add(v);
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sampleCount", overallVals.size());
        meta.put("skippedCount", skipped);
        meta.put("totalCount", samples.size());

        return MetricResult.builder()
                .name(name)
                .algorithmVersion(MetricResult.ALGORITHM_VERSION)
                .overall(mean(overallVals))
                .byIntentL1(meanMap(bucketL1))
                .byIntentL2(meanMap(bucketL2))
                .byDifficulty(meanMap(bucketDiff))
                .perSample(perSample)
                .meta(meta)
                .pct(pct)
                .build();
    }

    private static Double mean(List<Double> xs) {
        if (xs == null || xs.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Double x : xs) {
            sum += x;
        }
        return sum / xs.size();
    }

    private static Map<String, Double> meanMap(Map<String, List<Double>> buckets) {
        Map<String, Double> out = new LinkedHashMap<>();
        buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.put(e.getKey(), mean(e.getValue())));
        return out;
    }
}

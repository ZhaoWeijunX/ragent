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

import com.nageoffer.ai.ragent.rag.evaluation.metric.EvalMetric;
import com.nageoffer.ai.ragent.rag.evaluation.metric.EvalScoreSample;
import com.nageoffer.ai.ragent.rag.evaluation.metric.MetricResult;
import com.nageoffer.ai.ragent.rag.evaluation.metric.MetricSupport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RetrievalMetrics implements EvalMetric {

    private static final int[] K_VALUES = {1, 3, 5, 10};

    @Override
    public List<MetricResult> compute(List<EvalScoreSample> samples) {
        List<MetricResult> out = new ArrayList<>();
        for (int k : K_VALUES) {
            final int kk = k;
            out.add(MetricSupport.sliceMean(
                    "hit@" + k,
                    true,
                    samples,
                    s -> hitAtK(s, kk),
                    s -> MetricSupport.isRetrievalEligible(s, false)
            ));
            out.add(MetricSupport.sliceMean(
                    "recall@" + k,
                    true,
                    samples,
                    s -> recallAtK(s, kk, false),
                    s -> MetricSupport.isRetrievalEligible(s, false)
            ));
            out.add(MetricSupport.sliceMean(
                    "recall_inclusive@" + k,
                    true,
                    samples,
                    s -> recallAtK(s, kk, true),
                    s -> MetricSupport.isRetrievalEligible(s, true)
            ));
        }
        out.add(MetricSupport.sliceMean(
                "mrr@10",
                true,
                samples,
                RetrievalMetrics::mrrAt10,
                s -> MetricSupport.isRetrievalEligible(s, false)
        ));
        return out;
    }

    public static double hitAtK(EvalScoreSample s, int k) {
        Set<String> topk = new HashSet<>(prefix(s.safeRetrieved(), k));
        Set<String> ref = new HashSet<>(s.safeReference());
        for (String doc : topk) {
            if (ref.contains(doc)) {
                return 1.0;
            }
        }
        return 0.0;
    }

    public static double recallAtK(EvalScoreSample s, int k, boolean inclusive) {
        Set<String> topk = new HashSet<>(prefix(s.safeRetrieved(), k));
        Set<String> ref = new LinkedHashSet<>(s.safeReference());
        if (inclusive) {
            ref.addAll(s.safeNice());
        }
        if (ref.isEmpty()) {
            return 0.0;
        }
        int hit = 0;
        for (String doc : ref) {
            if (topk.contains(doc)) {
                hit++;
            }
        }
        return hit * 1.0 / ref.size();
    }

    public static double mrrAt10(EvalScoreSample s) {
        Set<String> ref = new HashSet<>(s.safeReference());
        List<String> retrieved = prefix(s.safeRetrieved(), 10);
        for (int i = 0; i < retrieved.size(); i++) {
            if (ref.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private static List<String> prefix(List<String> docs, int k) {
        if (docs.isEmpty() || k <= 0) {
            return List.of();
        }
        return docs.subList(0, Math.min(k, docs.size()));
    }
}

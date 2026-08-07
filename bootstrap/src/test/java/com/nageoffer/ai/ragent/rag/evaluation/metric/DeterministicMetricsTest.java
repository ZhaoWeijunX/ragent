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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicMetricsTest {

    @Test
    void intentTop1MatchesPython() {
        IntentTop1Metric metric = new IntentTop1Metric();
        List<MetricResult> results = metric.compute(List.of(
                sample("q1", "A", "A", true, List.of("D1"), List.of("D1")),
                sample("q2", "A", "B", true, List.of("D1"), List.of("D1")),
                sample("q3", null, "B", true, List.of(), List.of())
        ));
        MetricResult r = results.get(0);
        assertEquals("intent_top1", r.getName());
        assertEquals(0.5, r.getOverall(), 1e-9);
        assertEquals(1.0, r.getPerSample().get("q1"), 1e-9);
        assertEquals(0.0, r.getPerSample().get("q2"), 1e-9);
        assertNull(r.getPerSample().get("q3"));
    }

    @Test
    void hitRecallMrr() {
        RetrievalMetrics metric = new RetrievalMetrics();
        EvalScoreSample s = sample("q1", "L2", "L2", true,
                List.of("A", "B", "C"), List.of("B", "Z"));
        assertEquals(1.0, RetrievalMetrics.hitAtK(s, 3), 1e-9);
        assertEquals(0.0, RetrievalMetrics.hitAtK(s, 1), 1e-9);
        assertEquals(0.5, RetrievalMetrics.recallAtK(s, 3, false), 1e-9);
        assertEquals(0.5, RetrievalMetrics.mrrAt10(s), 1e-9);

        List<MetricResult> results = metric.compute(List.of(
                s,
                sample("q2", "L2", "L2", false, List.of("A"), List.of("A"))
        ));
        MetricResult hit5 = results.stream().filter(m -> "hit@5".equals(m.getName())).findFirst().orElseThrow();
        assertEquals(1.0, hit5.getOverall(), 1e-9);
        assertEquals(1, hit5.getMeta().get("sampleCount"));
        assertNull(hit5.getPerSample().get("q2"));
    }

    @Test
    void emptyGoldExcludedFromRetrieval() {
        RetrievalMetrics metric = new RetrievalMetrics();
        List<MetricResult> results = metric.compute(List.of(
                sample("q1", "L2", "L2", true, List.of("A"), List.of())
        ));
        MetricResult hit1 = results.stream().filter(m -> "hit@1".equals(m.getName())).findFirst().orElseThrow();
        assertNull(hit1.getOverall());
        assertNotNull(hit1.getPerSample());
    }

    @Test
    void behaviorUsesStructuredFields() {
        BehaviorMetrics metric = new BehaviorMetrics();
        EvalScoreSample refused = sample("q1", "L2", "L2", true, List.of(), List.of("A"));
        refused.setHasKb(false);
        refused.setRetrievalSkipped(true);
        EvalScoreSample over = sample("q2", "L2", "L2", false, List.of("A"), List.of());
        over.setHasKb(true);
        List<MetricResult> results = metric.compute(List.of(refused, over));
        MetricResult refusal = results.stream()
                .filter(m -> "refusal_when_required".equals(m.getName())).findFirst().orElseThrow();
        MetricResult overRate = results.stream()
                .filter(m -> "over_retrieval_rate".equals(m.getName())).findFirst().orElseThrow();
        assertEquals(1.0, refusal.getOverall(), 1e-9);
        assertEquals(1.0, overRate.getOverall(), 1e-9);
    }

    @Test
    void latencyReportsP50AndMeanOnly() {
        LatencyMetrics metric = new LatencyMetrics();
        EvalScoreSample a = sample("q1", "L2", "L2", true, List.of("A"), List.of("A"));
        a.setTtftMs(10L);
        a.setTotalLatencyMs(100L);
        EvalScoreSample b = sample("q2", "L2", "L2", true, List.of("A"), List.of("A"));
        b.setTtftMs(30L);
        b.setTotalLatencyMs(300L);
        List<MetricResult> results = metric.compute(List.of(a, b));
        assertTrue(results.stream().anyMatch(m -> "ttft_p50_ms".equals(m.getName())));
        assertTrue(results.stream().anyMatch(m -> "ttft_mean_ms".equals(m.getName())));
        assertTrue(results.stream().anyMatch(m -> "total_mean_ms".equals(m.getName())));
        assertTrue(results.stream().noneMatch(m -> m.getName().contains("p95") || m.getName().contains("p99")));
        MetricResult p50 = results.stream().filter(m -> "ttft_p50_ms".equals(m.getName())).findFirst().orElseThrow();
        assertEquals(false, p50.isPct());
        assertEquals(30.0, p50.getOverall(), 1e-9);
    }

    private static EvalScoreSample sample(String qid, String intentL2, String intentPred,
                                          boolean requiresRag, List<String> retrieved, List<String> gold) {
        return EvalScoreSample.builder()
                .recordId(qid)
                .queryId(qid)
                .intentL1("L1")
                .intentL2(intentL2)
                .difficulty("medium")
                .requiresRag(requiresRag)
                .intentPred(intentPred)
                .retrievedDocIds(retrieved)
                .referenceDocIds(gold)
                .referenceDocIdsNice(List.of())
                .build();
    }
}

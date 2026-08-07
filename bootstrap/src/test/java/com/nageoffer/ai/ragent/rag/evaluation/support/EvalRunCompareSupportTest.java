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

package com.nageoffer.ai.ragent.rag.evaluation.support;

import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalMetricReportVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRunCompareVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalRunCompareSupportTest {

    @Test
    void absoluteAndRelativeDelta() {
        assertEquals(0.1, EvalRunCompareSupport.absoluteDelta(0.9, 0.8), 1e-9);
        assertEquals(0.125, EvalRunCompareSupport.relativeDelta(0.9, 0.8), 1e-9);
        assertNull(EvalRunCompareSupport.relativeDelta(0.5, 0.0));
        assertNull(EvalRunCompareSupport.absoluteDelta(null, 0.1));
    }

    @Test
    void configDiffDetectsChangedAndNestedPaths() {
        Map<String, Object> current = Map.of(
                "concurrency", 2,
                "model", "a",
                "nested", Map.of("x", 1)
        );
        Map<String, Object> baseline = Map.of(
                "concurrency", 1,
                "model", "a",
                "nested", Map.of("x", 2)
        );
        List<EvalRunCompareVO.ConfigDiffItemVO> diff = EvalRunCompareSupport.configDiff(current, baseline);
        assertEquals(2, diff.size());
        assertTrue(diff.stream().anyMatch(d -> "concurrency".equals(d.getPath())));
        assertTrue(diff.stream().anyMatch(d -> "nested.x".equals(d.getPath())));
    }

    @Test
    void metricDeltasIncludeSlices() {
        List<EvalMetricReportVO.MetricItemVO> current = List.of(
                EvalMetricReportVO.MetricItemVO.builder()
                        .name("hit@5")
                        .overall(0.9)
                        .pct(true)
                        .byIntentL2(Map.of("billing", 1.0))
                        .byDifficulty(Map.of("easy", 0.95))
                        .build()
        );
        List<EvalMetricReportVO.MetricItemVO> baseline = List.of(
                EvalMetricReportVO.MetricItemVO.builder()
                        .name("hit@5")
                        .overall(0.8)
                        .pct(true)
                        .byIntentL2(Map.of("billing", 0.5))
                        .byDifficulty(Map.of("easy", 0.9))
                        .build()
        );
        List<EvalRunCompareVO.MetricDeltaVO> deltas = EvalRunCompareSupport.metricDeltas(current, baseline);
        assertEquals(1, deltas.size());
        assertEquals(0.1, deltas.get(0).getAbsoluteDelta(), 1e-9);
        assertEquals(1, deltas.get(0).getByIntentL2().size());
        assertEquals(0.5, deltas.get(0).getByIntentL2().get(0).getAbsoluteDelta(), 1e-9);
    }

    @Test
    void failureRegressionTrichotomy() {
        List<EvalMetricReportVO.SampleFailureVO> current = List.of(
                failure("q1", "intent_mismatch"),
                failure("q2", "hit_at_5_miss")
        );
        List<EvalMetricReportVO.SampleFailureVO> baseline = List.of(
                failure("q2", "hit_at_5_miss"),
                failure("q3", "chat_request_failed")
        );
        EvalRunCompareVO.FailureRegressionVO reg = EvalRunCompareSupport.failureRegression(current, baseline);
        assertEquals(1, reg.getNewFailures().size());
        assertEquals("q1", reg.getNewFailures().get(0).getQueryId());
        assertEquals(1, reg.getFixedFailures().size());
        assertEquals("q3", reg.getFixedFailures().get(0).getQueryId());
        assertEquals(1, reg.getPersistentFailures().size());
        assertEquals("q2", reg.getPersistentFailures().get(0).getQueryId());
    }

    @Test
    void sanitizeJudgeConfigStripsSecretsAndProgress() {
        Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("chatModel", "gpt-5.4-mini");
        raw.put("chatProvider", "aihubmix");
        raw.put("progress", Map.of("total", 10));
        raw.put("apiKey", "secret");
        raw.put("chatApiKey", "secret2");
        Map<String, Object> sanitized = EvalRunCompareSupport.sanitizeJudgeConfig(raw);
        assertEquals("gpt-5.4-mini", sanitized.get("chatModel"));
        assertEquals("aihubmix", sanitized.get("chatProvider"));
        assertNull(sanitized.get("progress"));
        assertNull(sanitized.get("apiKey"));
        assertNull(sanitized.get("chatApiKey"));
    }

    private static EvalMetricReportVO.SampleFailureVO failure(String queryId, String reason) {
        return EvalMetricReportVO.SampleFailureVO.builder()
                .queryId(queryId)
                .recordId("r-" + queryId)
                .failureReasons(List.of(reason))
                .build();
    }
}

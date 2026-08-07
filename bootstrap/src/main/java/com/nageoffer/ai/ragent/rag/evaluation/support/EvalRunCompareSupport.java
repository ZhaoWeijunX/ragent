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

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalMetricReportVO;
import com.nageoffer.ai.ragent.rag.evaluation.controller.vo.EvalRunCompareVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Run A/B 对比纯逻辑（可单测，不依赖 Spring）。
 */
public final class EvalRunCompareSupport {

    public static final int MAX_FAILURE_ITEMS = 100;

    private EvalRunCompareSupport() {
    }

    public static Double absoluteDelta(Double current, Double baseline) {
        if (current == null || baseline == null) {
            return null;
        }
        return current - baseline;
    }

    public static Double relativeDelta(Double current, Double baseline) {
        if (current == null || baseline == null || baseline == 0.0) {
            return null;
        }
        return (current - baseline) / Math.abs(baseline);
    }

    public static EvalRunCompareVO.ValueDeltaVO valueDelta(Double current, Double baseline) {
        return EvalRunCompareVO.ValueDeltaVO.builder()
                .current(current)
                .baseline(baseline)
                .absoluteDelta(absoluteDelta(current, baseline))
                .relativeDelta(relativeDelta(current, baseline))
                .build();
    }

    /**
     * 递归展开配置快照，仅保留两侧值不同的路径。
     */
    public static List<EvalRunCompareVO.ConfigDiffItemVO> configDiff(Map<String, Object> current,
                                                                     Map<String, Object> baseline) {
        Map<String, Object> flatCurrent = flatten("", current == null ? Map.of() : current);
        Map<String, Object> flatBaseline = flatten("", baseline == null ? Map.of() : baseline);
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(flatCurrent.keySet());
        keys.addAll(flatBaseline.keySet());
        List<EvalRunCompareVO.ConfigDiffItemVO> out = new ArrayList<>();
        for (String key : keys) {
            Object c = flatCurrent.get(key);
            Object b = flatBaseline.get(key);
            if (Objects.equals(stringify(c), stringify(b))) {
                continue;
            }
            out.add(EvalRunCompareVO.ConfigDiffItemVO.builder()
                    .path(key)
                    .current(c)
                    .baseline(b)
                    .build());
        }
        return out;
    }

    public static List<EvalRunCompareVO.MetricDeltaVO> metricDeltas(List<EvalMetricReportVO.MetricItemVO> current,
                                                                    List<EvalMetricReportVO.MetricItemVO> baseline) {
        Map<String, EvalMetricReportVO.MetricItemVO> curMap = indexByName(current);
        Map<String, EvalMetricReportVO.MetricItemVO> baseMap = indexByName(baseline);
        Set<String> names = new LinkedHashSet<>();
        names.addAll(curMap.keySet());
        names.addAll(baseMap.keySet());
        List<EvalRunCompareVO.MetricDeltaVO> out = new ArrayList<>();
        for (String name : names) {
            EvalMetricReportVO.MetricItemVO c = curMap.get(name);
            EvalMetricReportVO.MetricItemVO b = baseMap.get(name);
            Double cv = c == null ? null : c.getOverall();
            Double bv = b == null ? null : b.getOverall();
            Boolean pct = c != null && c.getPct() != null ? c.getPct()
                    : b != null ? b.getPct() : Boolean.TRUE;
            out.add(EvalRunCompareVO.MetricDeltaVO.builder()
                    .name(name)
                    .pct(pct)
                    .current(cv)
                    .baseline(bv)
                    .absoluteDelta(absoluteDelta(cv, bv))
                    .relativeDelta(relativeDelta(cv, bv))
                    .byIntentL2(sliceDeltas(
                            c == null ? null : c.getByIntentL2(),
                            b == null ? null : b.getByIntentL2()))
                    .byDifficulty(sliceDeltas(
                            c == null ? null : c.getByDifficulty(),
                            b == null ? null : b.getByDifficulty()))
                    .build());
        }
        return out;
    }

    public static List<EvalRunCompareVO.SliceDeltaVO> sliceDeltas(Map<String, Double> current,
                                                                 Map<String, Double> baseline) {
        Map<String, Double> c = current == null ? Map.of() : current;
        Map<String, Double> b = baseline == null ? Map.of() : baseline;
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(c.keySet());
        keys.addAll(b.keySet());
        List<EvalRunCompareVO.SliceDeltaVO> out = new ArrayList<>();
        for (String key : keys) {
            Double cv = c.get(key);
            Double bv = b.get(key);
            out.add(EvalRunCompareVO.SliceDeltaVO.builder()
                    .key(key)
                    .current(cv)
                    .baseline(bv)
                    .absoluteDelta(absoluteDelta(cv, bv))
                    .relativeDelta(relativeDelta(cv, bv))
                    .build());
        }
        return out;
    }

    public static EvalRunCompareVO.TtftDeltaVO ttftDelta(List<EvalRunCompareVO.MetricDeltaVO> metrics) {
        Map<String, EvalRunCompareVO.MetricDeltaVO> byName = metrics.stream()
                .collect(Collectors.toMap(EvalRunCompareVO.MetricDeltaVO::getName, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
        return EvalRunCompareVO.TtftDeltaVO.builder()
                .p50(fromMetric(byName.get("ttft_p50_ms")))
                .mean(fromMetric(byName.get("ttft_mean_ms")))
                .build();
    }

    /**
     * 对比用 Judge 快照：去掉 progress / apiKey，保留模型与 endpoint 元数据。
     */
    public static Map<String, Object> sanitizeJudgeConfig(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String key = e.getKey();
            if (key == null) {
                continue;
            }
            String lower = key.toLowerCase();
            if ("progress".equals(lower) || lower.contains("apikey") || lower.contains("api_key") || lower.contains("secret")) {
                continue;
            }
            out.put(key, e.getValue());
        }
        return out;
    }

    public static EvalRunCompareVO.FailureRegressionVO failureRegression(
            List<EvalMetricReportVO.SampleFailureVO> currentFailures,
            List<EvalMetricReportVO.SampleFailureVO> baselineFailures) {
        Map<String, EvalMetricReportVO.SampleFailureVO> cur = indexFailures(currentFailures);
        Map<String, EvalMetricReportVO.SampleFailureVO> base = indexFailures(baselineFailures);
        List<EvalRunCompareVO.FailureBriefVO> neu = new ArrayList<>();
        List<EvalRunCompareVO.FailureBriefVO> fixed = new ArrayList<>();
        List<EvalRunCompareVO.FailureBriefVO> persistent = new ArrayList<>();
        for (String key : cur.keySet()) {
            if (base.containsKey(key)) {
                if (persistent.size() < MAX_FAILURE_ITEMS) {
                    persistent.add(toBrief(cur.get(key)));
                }
            } else if (neu.size() < MAX_FAILURE_ITEMS) {
                neu.add(toBrief(cur.get(key)));
            }
        }
        for (String key : base.keySet()) {
            if (!cur.containsKey(key) && fixed.size() < MAX_FAILURE_ITEMS) {
                fixed.add(toBrief(base.get(key)));
            }
        }
        return EvalRunCompareVO.FailureRegressionVO.builder()
                .newFailures(neu)
                .fixedFailures(fixed)
                .persistentFailures(persistent)
                .build();
    }

    private static EvalRunCompareVO.ValueDeltaVO fromMetric(EvalRunCompareVO.MetricDeltaVO m) {
        if (m == null) {
            return valueDelta(null, null);
        }
        return valueDelta(m.getCurrent(), m.getBaseline());
    }

    private static Map<String, EvalMetricReportVO.MetricItemVO> indexByName(
            List<EvalMetricReportVO.MetricItemVO> metrics) {
        if (metrics == null) {
            return Map.of();
        }
        return metrics.stream()
                .filter(m -> StrUtil.isNotBlank(m.getName()))
                .collect(Collectors.toMap(EvalMetricReportVO.MetricItemVO::getName, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
    }

    private static Map<String, EvalMetricReportVO.SampleFailureVO> indexFailures(
            List<EvalMetricReportVO.SampleFailureVO> failures) {
        Map<String, EvalMetricReportVO.SampleFailureVO> out = new LinkedHashMap<>();
        if (failures == null) {
            return out;
        }
        for (EvalMetricReportVO.SampleFailureVO f : failures) {
            String key = failureKey(f);
            if (StrUtil.isNotBlank(key)) {
                out.putIfAbsent(key, f);
            }
        }
        return out;
    }

    private static String failureKey(EvalMetricReportVO.SampleFailureVO f) {
        if (StrUtil.isNotBlank(f.getQueryId())) {
            return f.getQueryId();
        }
        return f.getRecordId();
    }

    private static EvalRunCompareVO.FailureBriefVO toBrief(EvalMetricReportVO.SampleFailureVO f) {
        return EvalRunCompareVO.FailureBriefVO.builder()
                .recordId(f.getRecordId())
                .queryId(f.getQueryId())
                .intentL2(f.getIntentL2())
                .failureReasons(f.getFailureReasons())
                .traceId(f.getTraceId())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> flatten(String prefix, Map<String, Object> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String path = StrUtil.isBlank(prefix) ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map<?, ?> nested) {
                out.putAll(flatten(path, (Map<String, Object>) nested));
            } else {
                out.put(path, v);
            }
        }
        return out;
    }

    private static String stringify(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}

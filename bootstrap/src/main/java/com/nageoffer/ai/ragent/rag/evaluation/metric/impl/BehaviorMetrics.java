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
import com.nageoffer.ai.ragent.rag.evaluation.metric.MetricSupport;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 对齐 ragenteval behavior.py；误拒/过召回优先看结构化字段，空召回作回退。
 */
@Component
public class BehaviorMetrics implements EvalMetric {

    public static final String FALLBACK_MARKER = "未检索到与问题相关的文档内容";

    @Override
    public List<MetricResult> compute(List<EvalScoreSample> samples) {
        MetricResult refusal = MetricSupport.sliceMean(
                "refusal_when_required",
                true,
                samples,
                s -> isRefusal(s) ? 1.0 : 0.0,
                EvalScoreSample::isRequiresRag
        );
        MetricResult fallback = MetricSupport.sliceMean(
                "fallback_when_required",
                true,
                samples,
                s -> StrUtil.contains(s.getResponse(), FALLBACK_MARKER) ? 1.0 : 0.0,
                EvalScoreSample::isRequiresRag
        );
        MetricResult over = MetricSupport.sliceMean(
                "over_retrieval_rate",
                true,
                samples,
                s -> enteredKb(s) ? 1.0 : 0.0,
                s -> !s.isRequiresRag()
        );
        return List.of(refusal, fallback, over);
    }

    public static boolean isRefusal(EvalScoreSample s) {
        if (Boolean.TRUE.equals(s.getRetrievalSkipped())) {
            return true;
        }
        if (s.getHasKb() != null) {
            return !Boolean.TRUE.equals(s.getHasKb());
        }
        return s.safeRetrieved().isEmpty();
    }

    public static boolean enteredKb(EvalScoreSample s) {
        if (s.getHasKb() != null) {
            return Boolean.TRUE.equals(s.getHasKb());
        }
        return !s.safeRetrieved().isEmpty();
    }
}

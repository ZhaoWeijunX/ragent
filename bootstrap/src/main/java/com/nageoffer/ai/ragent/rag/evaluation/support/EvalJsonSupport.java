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

import cn.hutool.json.JSONUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSONB 字段与 Java 集合互转。
 */
public final class EvalJsonSupport {

    private EvalJsonSupport() {
    }

    public static String toJsonArray(List<String> values) {
        if (values == null) {
            return "[]";
        }
        return JSONUtil.toJsonStr(values);
    }

    public static String toJsonObject(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        return JSONUtil.toJsonStr(values);
    }

    public static List<String> toStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return JSONUtil.toList(json, String.class);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        return JSONUtil.toBean(json, Map.class);
    }
}

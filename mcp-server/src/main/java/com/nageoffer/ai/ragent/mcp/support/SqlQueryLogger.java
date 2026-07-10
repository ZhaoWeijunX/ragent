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

package com.nageoffer.ai.ragent.mcp.support;

import lombok.extern.slf4j.Slf4j;

import java.time.temporal.Temporal;
import java.util.List;

@Slf4j
public final class SqlQueryLogger {

    private SqlQueryLogger() {
    }

    public static void log(String source, String sql, List<Object> params) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info("[MCP-SQL][{}] {}", source, renderSql(sql, params));
    }

    private static String renderSql(String sql, List<Object> params) {
        String rendered = sql.strip().replaceAll("\\R+", " ");
        if (params == null || params.isEmpty()) {
            return rendered;
        }
        StringBuilder sb = new StringBuilder(rendered.length() + params.size() * 16);
        int paramIndex = 0;
        for (int i = 0; i < rendered.length(); i++) {
            char ch = rendered.charAt(i);
            if (ch == '?' && paramIndex < params.size()) {
                sb.append(formatParam(params.get(paramIndex++)));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String formatParam(Object param) {
        if (param == null) {
            return "NULL";
        }
        if (param instanceof String || param instanceof Temporal) {
            return "'" + String.valueOf(param).replace("'", "''") + "'";
        }
        return String.valueOf(param);
    }
}

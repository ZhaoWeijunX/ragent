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

import java.time.LocalDate;

public final class PeriodDateRange {

    private PeriodDateRange() {
    }

    public static LocalDate[] resolve(String period) {
        LocalDate now = LocalDate.now();
        return switch (period == null || period.isBlank() ? "本月" : period) {
            case "上月" -> new LocalDate[]{
                    now.minusMonths(1).withDayOfMonth(1),
                    now.withDayOfMonth(1).minusDays(1)
            };
            case "本季度" -> {
                int quarter = (now.getMonthValue() - 1) / 3;
                yield new LocalDate[]{
                        now.withMonth(quarter * 3 + 1).withDayOfMonth(1),
                        now
                };
            }
            case "上季度" -> {
                int quarter = (now.getMonthValue() - 1) / 3;
                LocalDate end = now.withMonth(quarter * 3 + 1).withDayOfMonth(1).minusDays(1);
                LocalDate start = end.withMonth(((quarter - 1 + 4) % 4) * 3 + 1).withDayOfMonth(1);
                yield new LocalDate[]{start, end};
            }
            case "本年" -> new LocalDate[]{now.withDayOfYear(1), now};
            default -> new LocalDate[]{now.withDayOfMonth(1), now};
        };
    }
}

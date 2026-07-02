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

package com.nageoffer.ai.ragent.mcp.executor;

import com.nageoffer.ai.ragent.mcp.client.QWeatherClient;
import com.nageoffer.ai.ragent.mcp.client.QWeatherClient.CityLocation;
import com.nageoffer.ai.ragent.mcp.client.QWeatherClient.DailyWeather;
import com.nageoffer.ai.ragent.mcp.client.QWeatherClient.NowWeather;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherMcpExecutor {

    private static final String TOOL_ID = "weather_query";

    private final QWeatherClient qWeatherClient;

    @Bean
    public McpServerFeatures.SyncToolSpecification weatherToolSpecification() {
        return new McpServerFeatures.SyncToolSpecification(buildTool(),
                (exchange, request) -> handleCall(request));
    }

    private Tool buildTool() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("city", Map.of(
                "type", "string",
                "description", "城市名称，支持全国及全球主要城市，如北京、上海、广州等"
        ));

        properties.put("queryType", Map.of(
                "type", "string",
                "description", "查询类型：current(当前天气)、forecast(未来预报)",
                "enum", List.of("current", "forecast"),
                "default", "current"
        ));

        properties.put("days", Map.of(
                "type", "integer",
                "description", "预报天数，仅forecast模式有效，默认3天，最多7天",
                "default", 3
        ));

        JsonSchema inputSchema = new JsonSchema(
                "object", properties, List.of("city"), null, null, null);

        return Tool.builder()
                .name(TOOL_ID)
                .description("查询城市天气信息，支持查看当前实时天气和未来多天天气预报，包含温度、湿度、风力、天气状况等信息")
                .inputSchema(inputSchema)
                .build();
    }

    private CallToolResult handleCall(CallToolRequest request) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
            String city = stringArg(args, "city");
            String queryType = stringArg(args, "queryType");
            Integer days = intArg(args, "days");

            if (city == null || city.isBlank()) {
                return errorResult("请提供城市名称");
            }
            if (queryType == null || queryType.isBlank()) {
                queryType = "current";
            }
            if (days == null || days <= 0) {
                days = 3;
            }
            if (days > 7) {
                days = 7;
            }

            CityLocation location = qWeatherClient.lookupCity(city.trim());
            String result = switch (queryType) {
                case "forecast" -> buildForecastResult(location, days);
                default -> buildCurrentResult(location);
            };

            log.info("MCP 工具调用完成, toolId={}, city={}, queryType={}, elapsed={}ms",
                    TOOL_ID, city, queryType, System.currentTimeMillis() - startMs);
            return successResult(result);
        } catch (QWeatherClient.QWeatherException e) {
            log.warn("MCP 工具调用失败, toolId={}, reason={}, elapsed={}ms",
                    TOOL_ID, e.getMessage(), System.currentTimeMillis() - startMs);
            return errorResult(e.getMessage());
        } catch (Exception e) {
            log.error("MCP 工具调用失败, toolId={}, elapsed={}ms",
                    TOOL_ID, System.currentTimeMillis() - startMs, e);
            return errorResult("查询失败: " + e.getMessage());
        }
    }

    private String buildCurrentResult(CityLocation location) {
        NowWeather now = qWeatherClient.getNow(location.id());
        String airQuality = qWeatherClient.getAirQuality(location);
        List<DailyWeather> dailyList = qWeatherClient.getDaily(location.id(), 1);
        DailyWeather today = dailyList.isEmpty() ? null : dailyList.get(0);

        String displayName = displayCityName(location);
        LocalDate todayDate = LocalDate.now();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【%s 今日天气】\n\n", displayName));
        sb.append(String.format("日期: %s\n", todayDate.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))));
        sb.append(String.format("天气: %s\n", now.text()));
        sb.append(String.format("当前温度: %s°C\n", now.temp()));
        if (today != null) {
            sb.append(String.format("最高温度: %s°C\n", today.tempMax()));
            sb.append(String.format("最低温度: %s°C\n", today.tempMin()));
        }
        sb.append(String.format("体感温度: %s°C\n", now.feelsLike()));
        sb.append(String.format("相对湿度: %s%%\n", now.humidity()));
        sb.append(String.format("风向: %s\n", now.windDir()));
        sb.append(String.format("风力: %s级\n", now.windScale()));
        sb.append(String.format("空气质量: %s\n", airQuality));

        String weatherText = now.text();
        int highTemp = today != null ? parseIntOrZero(today.tempMax()) : parseIntOrZero(now.temp());
        int lowTemp = today != null ? parseIntOrZero(today.tempMin()) : parseIntOrZero(now.temp());
        if (weatherText.contains("雨") || weatherText.contains("雪")) {
            sb.append("\n提示: 今日有降水，出行请携带雨具。");
        } else if (highTemp >= 35) {
            sb.append("\n提示: 今日高温，注意防暑降温。");
        } else if (lowTemp <= 0) {
            sb.append("\n提示: 今日气温较低，注意防寒保暖。");
        }

        return sb.toString().trim();
    }

    private String buildForecastResult(CityLocation location, int days) {
        List<DailyWeather> dailyList = qWeatherClient.getDaily(location.id(), days);
        String displayName = displayCityName(location);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【%s 未来%d天天气预报】\n\n", displayName, days));

        for (int d = 0; d < dailyList.size(); d++) {
            DailyWeather weather = dailyList.get(d);
            LocalDate date = LocalDate.parse(weather.fxDate());
            String dayLabel = d == 0 ? "今天" : d == 1 ? "明天" : d == 2 ? "后天"
                    : date.format(DateTimeFormatter.ofPattern("MM月dd日"));

            sb.append(String.format("📅 %s（%s）\n", dayLabel, date.format(DateTimeFormatter.ofPattern("MM-dd"))));
            sb.append(String.format("   天气: %s | 温度: %s°C ~ %s°C\n",
                    weather.textDay(), weather.tempMin(), weather.tempMax()));
            sb.append(String.format("   湿度: %s%% | %s %s级\n\n",
                    weather.humidity(), weather.windDirDay(), weather.windScaleDay()));
        }

        if (dailyList.size() >= 2) {
            int todayHigh = parseIntOrZero(dailyList.get(0).tempMax());
            int lastHigh = parseIntOrZero(dailyList.get(dailyList.size() - 1).tempMax());
            int tempTrend = lastHigh - todayHigh;
            if (Math.abs(tempTrend) >= 5) {
                sb.append(String.format("趋势: 未来%d天气温%s，注意%s。",
                        days,
                        tempTrend > 0 ? "逐渐升高" : "逐渐下降",
                        tempTrend > 0 ? "防暑" : "保暖"));
            }
        }

        return sb.toString().trim();
    }

    private static String displayCityName(CityLocation location) {
        if (location.adm2() != null && !location.adm2().isBlank()
                && !location.adm2().equals(location.name())) {
            return location.adm2() + location.name();
        }
        return location.name();
    }

    private static int parseIntOrZero(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    private static Integer intArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private static CallToolResult successResult(String text) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(text)))
                .isError(false)
                .build();
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}

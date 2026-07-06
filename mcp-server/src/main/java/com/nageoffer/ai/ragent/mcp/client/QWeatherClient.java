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

package com.nageoffer.ai.ragent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.mcp.config.WeatherProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

/**
 * 和风天气 API 客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QWeatherClient {

    private static final String GEO_LOOKUP_PATH = "/geo/v2/city/lookup";

    private final WeatherProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestClient restClient;

    public record CityLocation(String id, String name, String adm1, String adm2, String lat, String lon) {
    }

    public record NowWeather(
            String temp,
            String feelsLike,
            String text,
            String windDir,
            String windScale,
            String humidity
    ) {
    }

    public record DailyWeather(
            String fxDate,
            String textDay,
            String tempMax,
            String tempMin,
            String humidity,
            String windDirDay,
            String windScaleDay
    ) {
    }

    @PostConstruct
    void initRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        restClient = RestClient.builder()
                .baseUrl(properties.normalizedHost())
                .defaultHeader("X-QW-Api-Key", properties.getApiKey())
                .defaultHeader("Accept-Encoding", "gzip")
                .requestFactory(factory)
                .build();
    }

    public CityLocation lookupCity(String cityName) {
        assertConfigured();
        JsonNode root = getJson(GEO_LOOKUP_PATH, builder -> builder
                .queryParam("location", cityName)
                .queryParam("range", "cn")
                .queryParam("lang", properties.getLang())
                .queryParam("number", 1));
        assertV7Success(root);

        JsonNode locations = root.path("location");
        if (!locations.isArray() || locations.isEmpty()) {
            throw new QWeatherException("未找到城市「" + cityName + "」，请检查城市名称");
        }

        JsonNode first = locations.get(0);
        return new CityLocation(
                first.path("id").asText(),
                first.path("name").asText(),
                first.path("adm1").asText(),
                first.path("adm2").asText(),
                first.path("lat").asText(),
                first.path("lon").asText()
        );
    }

    public NowWeather getNow(String locationId) {
        assertConfigured();
        JsonNode root = getJson("/v7/weather/now", builder -> builder
                .queryParam("location", locationId)
                .queryParam("lang", properties.getLang()));
        assertV7Success(root);

        JsonNode now = root.path("now");
        return new NowWeather(
                now.path("temp").asText(),
                now.path("feelsLike").asText(),
                now.path("text").asText(),
                now.path("windDir").asText(),
                now.path("windScale").asText(),
                now.path("humidity").asText()
        );
    }

    public List<DailyWeather> getDaily(String locationId, int days) {
        assertConfigured();
        JsonNode root = getJson("/v7/weather/7d", builder -> builder
                .queryParam("location", locationId)
                .queryParam("lang", properties.getLang()));
        assertV7Success(root);

        JsonNode daily = root.path("daily");
        if (!daily.isArray() || daily.isEmpty()) {
            throw new QWeatherException("未获取到预报数据");
        }

        int limit = Math.min(Math.max(days, 1), daily.size());
        List<DailyWeather> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            JsonNode item = daily.get(i);
            result.add(new DailyWeather(
                    item.path("fxDate").asText(),
                    item.path("textDay").asText(),
                    item.path("tempMax").asText(),
                    item.path("tempMin").asText(),
                    item.path("humidity").asText(),
                    item.path("windDirDay").asText(),
                    item.path("windScaleDay").asText()
            ));
        }
        return result;
    }

    public String getAirQuality(CityLocation location) {
        try {
            assertConfigured();
            String path = "/airquality/v1/current/"
                    + formatCoord(location.lat()) + "/" + formatCoord(location.lon());
            JsonNode root = getJson(path, builder -> builder.queryParam("lang", properties.getLang()));
            return parseAirQuality(root);
        } catch (Exception e) {
            log.warn("空气质量查询失败, locationId={}, reason={}", location.id(), e.getMessage());
            return "暂无数据";
        }
    }

    private JsonNode getJson(String path, Function<UriComponentsBuilder, UriComponentsBuilder> uriCustomizer) {
        String uri = uriCustomizer.apply(UriComponentsBuilder.fromPath(path)).build().toUriString();
        try {
            byte[] raw = restClient.get().uri(uri).retrieve().body(byte[].class);
            return objectMapper.readTree(decodeBody(raw));
        } catch (HttpStatusCodeException e) {
            throw new QWeatherException("天气服务 HTTP 异常(status=" + e.getStatusCode().value() + ")", e);
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new QWeatherException("天气服务请求失败: " + reason, e);
        }
    }

    private static String decodeBody(byte[] raw) throws Exception {
        if (raw == null || raw.length == 0) {
            return "";
        }
        if (raw.length >= 2 && raw[0] == (byte) 0x1f && raw[1] == (byte) 0x8b) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    private static String parseAirQuality(JsonNode root) {
        JsonNode indexes = root.path("indexes");
        if (!indexes.isArray() || indexes.isEmpty()) {
            return "暂无数据";
        }
        JsonNode index = null;
        for (JsonNode item : indexes) {
            String code = item.path("code").asText();
            if ("cn-mee".equals(code) || "cn-mee-1h".equals(code)) {
                index = item;
                break;
            }
        }
        if (index == null) {
            index = indexes.get(0);
        }
        String category = index.path("category").asText("");
        String aqiDisplay = index.path("aqiDisplay").asText("");
        if (!category.isBlank() && !aqiDisplay.isBlank()) {
            return category + " (AQI " + aqiDisplay + ")";
        }
        if (!category.isBlank()) {
            return category;
        }
        return aqiDisplay.isBlank() ? "暂无数据" : "AQI " + aqiDisplay;
    }

    private static String formatCoord(String value) {
        return String.format("%.2f", Double.parseDouble(value));
    }

    private void assertConfigured() {
        if (!properties.isConfigured()) {
            throw new QWeatherException("天气服务未配置 API Key 或 API Host");
        }
    }

    private void assertV7Success(JsonNode root) {
        if (!"200".equals(root.path("code").asText())) {
            throw new QWeatherException("天气服务异常(code=" + root.path("code").asText() + ")");
        }
    }

    public static class QWeatherException extends RuntimeException {
        public QWeatherException(String message) {
            super(message);
        }

        public QWeatherException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

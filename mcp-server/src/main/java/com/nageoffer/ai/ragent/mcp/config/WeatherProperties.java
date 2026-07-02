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

package com.nageoffer.ai.ragent.mcp.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 和风天气 API 配置
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "weather.qweather")
public class WeatherProperties {

    private String apiKey = "";

    /** 控制台专属 API Host：<a href="https://console.qweather.com/setting">API HOST</a> */
    private String apiHost = "";

    private String lang = "zh";

    private int timeoutMs = 5000;

    @PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("weather.qweather.api-key 未配置，天气查询工具将无法正常工作");
        }
        if (apiHost == null || apiHost.isBlank()) {
            log.error("weather.qweather.api-host 未配置，请在控制台获取专属 API Host");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && apiHost != null && !apiHost.isBlank();
    }

    public String normalizedHost() {
        String host = apiHost == null ? "" : apiHost.trim();
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        if (!host.isBlank() && !host.startsWith("http://") && !host.startsWith("https://")) {
            host = "https://" + host;
        }
        return host;
    }
}

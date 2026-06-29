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

package com.nageoffer.ai.ragent.ingestion.strategy.fetcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.ingestion.util.HttpClientHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 飞书云文档 docx API 客户端
 */
@Component
@RequiredArgsConstructor
public class FeishuDocxClient {

    private static final String RAW_CONTENT_URL =
            "https://open.feishu.cn/open-apis/docx/v1/documents/%s/raw_content";

    private final HttpClientHelper httpClientHelper;

    /**
     * 拉取 docx 文档纯文本内容
     */
    public String fetchRawContent(String documentToken, Map<String, String> headers) {
        String apiUrl = String.format(RAW_CONTENT_URL, documentToken);
        HttpClientHelper.HttpFetchResponse resp = httpClientHelper.get(apiUrl, headers);
        String content = extractDocxContent(resp.body());
        if (!StringUtils.hasText(content)) {
            content = new String(resp.body(), StandardCharsets.UTF_8);
        }
        return content;
    }

    private String extractDocxContent(byte[] bytes) {
        try {
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("data")) {
                JsonObject data = root.getAsJsonObject("data");
                if (data.has("content")) {
                    return data.get("content").getAsString();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

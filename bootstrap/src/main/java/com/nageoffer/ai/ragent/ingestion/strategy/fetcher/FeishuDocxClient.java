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
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.ingestion.util.HttpClientHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

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
    private static final String MARKDOWN_CONTENT_URL =
            "https://open.feishu.cn/open-apis/docs/v1/content";

    private final HttpClientHelper httpClientHelper;

    /**
     * 拉取 docx 文档 Markdown 内容（docs/v1/content）
     */
    public String fetchMarkdownContent(String documentToken, Map<String, String> headers) {
        String apiUrl = UriComponentsBuilder.fromHttpUrl(MARKDOWN_CONTENT_URL)
                .queryParam("doc_token", documentToken)
                .queryParam("doc_type", "docx")
                .queryParam("content_type", "markdown")
                .toUriString();
        HttpClientHelper.HttpFetchResponse resp = httpClientHelper.get(apiUrl, headers);
        return parseContentResponse(resp.body(), "飞书 Markdown 导出失败");
    }

    /**
     * 拉取 docx 文档纯文本内容（raw_content，供回退或 plain 模式使用）
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

    private String parseContentResponse(byte[] bytes, String failurePrefix) {
        JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.has("code") && root.get("code").getAsInt() != 0) {
            String msg = root.has("msg") ? root.get("msg").getAsString() : "unknown";
            throw new ClientException(failurePrefix + ": " + msg);
        }
        if (root.has("data")) {
            JsonObject data = root.getAsJsonObject("data");
            if (data.has("content") && !data.get("content").isJsonNull()) {
                return data.get("content").getAsString();
            }
        }
        throw new ClientException(failurePrefix + ": 响应缺少 content");
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

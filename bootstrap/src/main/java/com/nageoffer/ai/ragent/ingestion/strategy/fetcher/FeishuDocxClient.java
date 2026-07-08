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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 飞书云文档 docx API 客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuDocxClient {

    private static final String RAW_CONTENT_URL =
            "https://open.feishu.cn/open-apis/docx/v1/documents/%s/raw_content";
    private static final String MARKDOWN_CONTENT_URL =
            "https://open.feishu.cn/open-apis/docs/v1/content";
    private static final String EXPORT_TASKS_URL =
            "https://open.feishu.cn/open-apis/drive/v1/export_tasks";

    private static final long EXPORT_POLL_INTERVAL_MS = 2_000L;
    private static final long EXPORT_TIMEOUT_MS = 120_000L;
    /** 飞书导出任务状态：0=成功，1=初始化，2=处理中 */
    private static final int EXPORT_JOB_SUCCESS = 0;
    private static final int EXPORT_JOB_INIT = 1;
    private static final int EXPORT_JOB_PROCESSING = 2;

    private final HttpClientHelper httpClientHelper;
    private final FeishuExportPollingExecutor pollingExecutor;

    /**
     * 导出 docx 为 PDF 字节流（异步导出任务：创建 → 轮询 → 下载）
     *
     * @param maxBytes 下载大小上限，{@code <= 0} 表示不限制
     */
    public byte[] fetchPdfContent(String documentToken, Map<String, String> headers, long maxBytes) {
        String ticket = createPdfExportTask(documentToken, headers);
        String fileToken = pollExportFileToken(documentToken, ticket, headers);
        return downloadExportFile(fileToken, headers, maxBytes);
    }

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

    private String createPdfExportTask(String documentToken, Map<String, String> headers) {
        JsonObject body = new JsonObject();
        body.addProperty("file_extension", "pdf");
        body.addProperty("token", documentToken);
        body.addProperty("type", "docx");
        HttpClientHelper.HttpFetchResponse resp = httpClientHelper.postJson(EXPORT_TASKS_URL, headers, body.toString());
        JsonObject data = parseJsonRoot(resp.body(), "飞书 PDF 导出任务创建失败").getAsJsonObject("data");
        if (data != null && data.has("ticket") && !data.get("ticket").isJsonNull()) {
            return data.get("ticket").getAsString();
        }
        throw new ClientException("飞书 PDF 导出任务创建失败: 响应缺少 ticket");
    }

    private String pollExportFileToken(String documentToken, String ticket, Map<String, String> headers) {
        try {
            return pollingExecutor.submitAndAwait(
                    () -> queryExportFileTokenOnce(documentToken, ticket, headers),
                    Duration.ofMillis(EXPORT_TIMEOUT_MS),
                    EXPORT_POLL_INTERVAL_MS
            ).get(EXPORT_TIMEOUT_MS + 5_000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientException("飞书 PDF 导出轮询被中断");
        } catch (TimeoutException e) {
            throw new ClientException("飞书 PDF 导出超时，ticket=" + ticket);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ClientException("飞书 PDF 导出失败: " + cause.getMessage());
        }
    }

    private String queryExportFileTokenOnce(String documentToken, String ticket, Map<String, String> headers) {
        String apiUrl = UriComponentsBuilder.fromHttpUrl(EXPORT_TASKS_URL + "/" + ticket)
                .queryParam("token", documentToken)
                .toUriString();
        HttpClientHelper.HttpFetchResponse resp = httpClientHelper.get(apiUrl, headers);
        JsonObject data = parseJsonRoot(resp.body(), "飞书 PDF 导出查询失败").getAsJsonObject("data");
        if (data != null && data.has("result") && !data.get("result").isJsonNull()) {
            JsonObject result = data.getAsJsonObject("result");
            int jobStatus = result.has("job_status") ? result.get("job_status").getAsInt() : -1;
            if (jobStatus == EXPORT_JOB_SUCCESS) {
                if (result.has("file_token") && !result.get("file_token").isJsonNull()) {
                    String fileToken = result.get("file_token").getAsString();
                    if (StringUtils.hasText(fileToken)) {
                        return fileToken;
                    }
                }
                log.warn("飞书 PDF 导出状态为成功但缺少 file_token, ticket={}, 继续轮询", ticket);
            } else if (jobStatus == EXPORT_JOB_INIT || jobStatus == EXPORT_JOB_PROCESSING) {
                log.debug("飞书 PDF 导出进行中, ticket={}, job_status={}", ticket, jobStatus);
            } else {
                String msg = resolveExportErrorMessage(result, jobStatus);
                throw new ClientException("飞书 PDF 导出失败: " + msg);
            }
        }
        return null;
    }

    private static String resolveExportErrorMessage(JsonObject result, int jobStatus) {
        if (result.has("job_error_msg") && !result.get("job_error_msg").isJsonNull()) {
            String msg = result.get("job_error_msg").getAsString();
            if (StringUtils.hasText(msg) && !"success".equalsIgnoreCase(msg)) {
                return msg;
            }
        }
        return switch (jobStatus) {
            case 3 -> "内部错误";
            case 107 -> "导出文档过大";
            case 108 -> "处理超时";
            case 109 -> "导出内容块无权限";
            case 110 -> "无权限，请为应用添加文档阅读权限";
            case 111 -> "导出文档已删除";
            case 122 -> "创建副本中禁止导出";
            case 123 -> "导出文档不存在";
            case 6000 -> "导出文档图片过多";
            default -> "job_status=" + jobStatus;
        };
    }

    private byte[] downloadExportFile(String fileToken, Map<String, String> headers, long maxBytes) {
        String apiUrl = EXPORT_TASKS_URL + "/file/" + fileToken + "/download";
        HttpClientHelper.HttpFetchResponse resp = maxBytes > 0
                ? httpClientHelper.getWithLimit(apiUrl, headers, maxBytes)
                : httpClientHelper.get(apiUrl, headers);
        byte[] bytes = resp.body();
        if (bytes == null || bytes.length == 0) {
            throw new ClientException("飞书 PDF 下载结果为空");
        }
        return bytes;
    }

    private String parseContentResponse(byte[] bytes, String failurePrefix) {
        JsonObject root = parseJsonRoot(bytes, failurePrefix);
        if (root.has("data")) {
            JsonObject data = root.getAsJsonObject("data");
            if (data.has("content") && !data.get("content").isJsonNull()) {
                return data.get("content").getAsString();
            }
        }
        throw new ClientException(failurePrefix + ": 响应缺少 content");
    }

    private JsonObject parseJsonRoot(byte[] bytes, String failurePrefix) {
        JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.has("code") && root.get("code").getAsInt() != 0) {
            String msg = root.has("msg") ? root.get("msg").getAsString() : "unknown";
            throw new ClientException(failurePrefix + ": " + msg);
        }
        return root;
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

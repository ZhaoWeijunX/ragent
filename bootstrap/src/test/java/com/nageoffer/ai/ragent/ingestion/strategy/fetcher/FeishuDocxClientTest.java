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

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.ingestion.util.HttpClientHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuDocxClientTest {

    @Mock
    private HttpClientHelper httpClientHelper;

    @InjectMocks
    private FeishuDocxClient feishuDocxClient;

    @Test
    void shouldFetchMarkdownContent() {
        String json = """
                {
                  "code": 0,
                  "data": {
                    "content": "# Title\\n\\nHello"
                  }
                }
                """;
        when(httpClientHelper.get(anyString(), any())).thenReturn(response(json));

        String content = feishuDocxClient.fetchMarkdownContent("doccnABC", Map.of());

        assertEquals("# Title\n\nHello", content);
        verify(httpClientHelper).get(argThat((String url) ->
                url.contains("/docs/v1/content")
                        && url.contains("doc_token=doccnABC")
                        && url.contains("doc_type=docx")
                        && url.contains("content_type=markdown")), any());
    }

    @Test
    void shouldRejectMarkdownApiError() {
        String json = """
                {
                  "code": 99991663,
                  "msg": "permission denied"
                }
                """;
        when(httpClientHelper.get(anyString(), any())).thenReturn(response(json));

        ClientException ex = assertThrows(ClientException.class,
                () -> feishuDocxClient.fetchMarkdownContent("doccnABC", Map.of()));
        assertTrue(ex.getMessage().contains("飞书 Markdown 导出失败"));
        assertTrue(ex.getMessage().contains("permission denied"));
    }

    @Test
    void shouldFetchRawContentFromDataField() {
        String json = """
                {
                  "code": 0,
                  "data": {
                    "content": "plain body"
                  }
                }
                """;
        when(httpClientHelper.get(anyString(), any())).thenReturn(response(json));

        String content = feishuDocxClient.fetchRawContent("doccnABC", Map.of());

        assertEquals("plain body", content);
        verify(httpClientHelper).get(argThat((String url) -> url.contains("/raw_content")), any());
    }

    @Test
    void shouldFetchPdfContentViaExportTaskFlow() {
        String createJson = """
                {
                  "code": 0,
                  "data": {
                    "ticket": "ticket123"
                  }
                }
                """;
        String processingJson = """
                {
                  "code": 0,
                  "data": {
                    "result": {
                      "job_status": 2,
                      "job_error_msg": ""
                    }
                  }
                }
                """;
        String pollJson = """
                {
                  "code": 0,
                  "data": {
                    "result": {
                      "job_status": 0,
                      "job_error_msg": "success",
                      "file_token": "fileTokenABC"
                    }
                  }
                }
                """;
        byte[] pdf = "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8);
        when(httpClientHelper.postJson(anyString(), any(), any())).thenReturn(response(createJson));
        when(httpClientHelper.get(anyString(), any()))
                .thenReturn(response(processingJson))
                .thenReturn(response(pollJson))
                .thenReturn(new HttpClientHelper.HttpFetchResponse(pdf, "application/pdf", null, null, null, null));

        byte[] content = feishuDocxClient.fetchPdfContent("doccnABC", Map.of());

        assertArrayEquals(pdf, content);
        verify(httpClientHelper).postJson(argThat((String url) -> url.contains("/export_tasks")
                && !url.contains("/file/")), any(), argThat(body ->
                body.contains("\"file_extension\":\"pdf\"")
                        && body.contains("\"token\":\"doccnABC\"")
                        && body.contains("\"type\":\"docx\"")));
        verify(httpClientHelper).get(argThat((String url) -> url.contains("/export_tasks/ticket123")
                && url.contains("token=doccnABC")), any());
        verify(httpClientHelper).get(argThat((String url) -> url.contains("/export_tasks/file/fileTokenABC/download")), any());
    }

    @Test
    void shouldRejectExportFailureStatus() {
        String createJson = """
                {
                  "code": 0,
                  "data": {
                    "ticket": "ticket123"
                  }
                }
                """;
        String failedJson = """
                {
                  "code": 0,
                  "data": {
                    "result": {
                      "job_status": 110,
                      "job_error_msg": ""
                    }
                  }
                }
                """;
        when(httpClientHelper.postJson(anyString(), any(), any())).thenReturn(response(createJson));
        when(httpClientHelper.get(anyString(), any())).thenReturn(response(failedJson));

        ClientException ex = assertThrows(ClientException.class,
                () -> feishuDocxClient.fetchPdfContent("doccnABC", Map.of()));
        assertTrue(ex.getMessage().contains("无权限"));
    }

    private static HttpClientHelper.HttpFetchResponse response(String json) {
        return new HttpClientHelper.HttpFetchResponse(
                json.getBytes(StandardCharsets.UTF_8), null, null, null, null, null);
    }
}

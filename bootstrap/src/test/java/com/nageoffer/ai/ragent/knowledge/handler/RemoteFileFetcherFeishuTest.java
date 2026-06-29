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

package com.nageoffer.ai.ragent.knowledge.handler;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.ingestion.domain.context.DocumentSource;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.FeishuFetcher;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.FetchResult;
import com.nageoffer.ai.ragent.ingestion.util.HttpClientHelper;
import com.nageoffer.ai.ragent.knowledge.config.FeishuCredentialsProvider;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteFileFetcherFeishuTest {

    @Mock
    private HttpClientHelper httpClientHelper;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FeishuFetcher feishuFetcher;

    @Mock
    private FeishuCredentialsProvider feishuCredentialsProvider;

    private RemoteFileFetcher remoteFileFetcher;

    @BeforeEach
    void setUp() {
        remoteFileFetcher = new RemoteFileFetcher(
                httpClientHelper, fileStorageService, feishuFetcher, feishuCredentialsProvider);
        ReflectionTestUtils.setField(remoteFileFetcher, "maxFileSize", DataSize.ofMegabytes(50));
    }

    @Test
    void shouldFetchFeishuDocxAndStore() {
        String url = "https://example.feishu.cn/docx/doccnABC";
        byte[] content = "hello feishu".getBytes(StandardCharsets.UTF_8);
        when(feishuCredentialsProvider.resolve()).thenReturn(Map.of("tenantAccessToken", "token"));
        when(feishuFetcher.fetch(any(DocumentSource.class))).thenReturn(
                new FetchResult(content, "text/plain", "doc.txt"));
        when(fileStorageService.upload(eq("kb-bucket"), eq(content), eq("doc.txt"), eq("text/plain")))
                .thenReturn(StoredFileDTO.builder().originalFilename("doc.txt").detectedType("txt").build());

        StoredFileDTO stored = remoteFileFetcher.fetchAndStore("kb-bucket", url);

        assertEquals("doc.txt", stored.getOriginalFilename());
        verify(feishuCredentialsProvider).validateConfigured();
        verify(feishuFetcher).fetch(any(DocumentSource.class));
    }

    @Test
    void shouldRejectUnsupportedFeishuUrlWithoutHttpFallback() {
        String url = "https://example.feishu.cn/drive/folder/abc";

        ClientException ex = assertThrows(ClientException.class,
                () -> remoteFileFetcher.fetchAndStore("kb-bucket", url));
        assertTrue(ex.getMessage().contains("不支持的飞书链接格式"));
    }

    @Test
    void shouldFailWhenFeishuNotConfigured() {
        String url = "https://example.feishu.cn/docx/doccnABC";
        doThrow(new ClientException("飞书集成未启用"))
                .when(feishuCredentialsProvider).validateConfigured();

        assertThrows(ClientException.class, () -> remoteFileFetcher.fetchAndStore("kb", url));
    }

    @Test
    void shouldSkipFeishuRefreshWhenHashUnchanged() {
        String url = "https://example.feishu.cn/docx/doccnABC";
        byte[] content = "same".getBytes(StandardCharsets.UTF_8);
        when(feishuCredentialsProvider.resolve()).thenReturn(Map.of("tenantAccessToken", "token"));
        when(feishuFetcher.fetch(any(DocumentSource.class)))
                .thenReturn(new FetchResult(content, "text/plain", "doc.txt"));

        RemoteFileFetcher.RemoteFetchResult result = remoteFileFetcher.fetchIfChanged(
                url, null, null, sha256(content), "fallback.txt");

        assertFalse(result.changed());
        assertEquals("飞书文档内容未变化", result.message());
    }

    @Test
    void shouldRefreshFeishuWhenContentChanged() throws Exception {
        String url = "https://example.feishu.cn/wiki/wikcnXYZ";
        byte[] content = "new content".getBytes(StandardCharsets.UTF_8);
        when(feishuCredentialsProvider.resolve()).thenReturn(Map.of("tenantAccessToken", "token"));
        when(feishuFetcher.fetch(any(DocumentSource.class)))
                .thenReturn(new FetchResult(content, "text/plain", "wiki.txt"));

        RemoteFileFetcher.RemoteFetchResult result = remoteFileFetcher.fetchIfChanged(
                url, null, null, sha256("old".getBytes(StandardCharsets.UTF_8)), "fallback.txt");

        assertTrue(result.changed());
        assertEquals("wiki.txt", result.fileName());
        assertTrue(Files.exists(result.tempFile()));
        result.close();
    }

    private static String sha256(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String value = Integer.toHexString(0xff & b);
                if (value.length() == 1) {
                    hex.append('0');
                }
                hex.append(value);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

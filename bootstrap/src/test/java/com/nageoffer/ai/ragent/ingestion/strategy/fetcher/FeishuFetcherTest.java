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
import com.nageoffer.ai.ragent.ingestion.domain.context.DocumentSource;
import com.nageoffer.ai.ragent.ingestion.domain.enums.SourceType;
import com.nageoffer.ai.ragent.knowledge.config.FeishuProperties;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuFetcherTest {

    @Mock
    private FeishuDocxClient feishuDocxClient;

    @Mock
    private FeishuWikiClient feishuWikiClient;

    private FeishuFetcher feishuFetcher;
    private FeishuProperties feishuProperties;

    @BeforeEach
    void setUp() {
        feishuProperties = new FeishuProperties();
        FeishuAuthService feishuAuthService = new FeishuAuthService(new OkHttpClient());
        feishuFetcher = new FeishuFetcher(
                feishuAuthService, feishuDocxClient, feishuWikiClient, feishuProperties);
    }

    @Test
    void shouldFetchDocxUrlViaPdfClientByDefault() {
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
        when(feishuDocxClient.fetchPdfContent(eq("doccnABC"), any(), anyLong())).thenReturn(pdf);

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/docx/doccnABC")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        FetchResult result = feishuFetcher.fetch(source);

        assertEquals("application/pdf", result.mimeType());
        assertArrayEquals(pdf, result.content());
        assertEquals("doccnABC.pdf", result.fileName());
        verify(feishuDocxClient).fetchPdfContent(eq("doccnABC"), any(), eq(0L));
    }

    @Test
    void shouldFetchWikiDocxNodeViaPdfClientByDefault() {
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
        when(feishuWikiClient.getNode(eq("wikcnXYZ"), any())).thenReturn(
                new WikiNodeInfo("产品手册", "docx", "doccnFromWiki", "space123"));
        when(feishuDocxClient.fetchPdfContent(eq("doccnFromWiki"), any(), anyLong())).thenReturn(pdf);

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/wiki/wikcnXYZ")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        FetchResult result = feishuFetcher.fetch(source);

        assertArrayEquals(pdf, result.content());
        assertEquals("产品手册.pdf", result.fileName());
        assertEquals("application/pdf", result.mimeType());
        verify(feishuWikiClient).getNode(eq("wikcnXYZ"), any());
        verify(feishuDocxClient).fetchPdfContent(eq("doccnFromWiki"), any(), eq(0L));
    }

    @Test
    void shouldFetchDocxUrlViaMarkdownClientWhenConfigured() {
        feishuProperties.setContentFormat("markdown");
        when(feishuDocxClient.fetchMarkdownContent(eq("doccnABC"), any())).thenReturn("# hello docx");

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/docx/doccnABC")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        FetchResult result = feishuFetcher.fetch(source);

        assertEquals("text/markdown", result.mimeType());
        assertEquals("# hello docx", new String(result.content(), StandardCharsets.UTF_8));
        assertEquals("doccnABC.md", result.fileName());
        verify(feishuDocxClient).fetchMarkdownContent(eq("doccnABC"), any());
        verify(feishuDocxClient, never()).fetchPdfContent(any(), any(), anyLong());
    }

    @Test
    void shouldFallbackPdfToMarkdownToPlainWhenEnabled() {
        when(feishuDocxClient.fetchPdfContent(eq("doccnABC"), any(), anyLong()))
                .thenThrow(new ClientException("飞书 PDF 导出失败: 无权限"));
        when(feishuDocxClient.fetchMarkdownContent(eq("doccnABC"), any()))
                .thenThrow(new ClientException("飞书 Markdown 导出失败: permission denied"));
        when(feishuDocxClient.fetchRawContent(eq("doccnABC"), any())).thenReturn("plain fallback");

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/docx/doccnABC")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        FetchResult result = feishuFetcher.fetch(source);

        assertEquals("text/plain", result.mimeType());
        assertEquals("plain fallback", new String(result.content(), StandardCharsets.UTF_8));
        assertEquals("doccnABC.txt", result.fileName());
        verify(feishuDocxClient).fetchPdfContent(eq("doccnABC"), any(), eq(0L));
        verify(feishuDocxClient).fetchMarkdownContent(eq("doccnABC"), any());
        verify(feishuDocxClient).fetchRawContent(eq("doccnABC"), any());
    }

    @Test
    void shouldFallbackMarkdownToPlainWhenConfigured() {
        feishuProperties.setContentFormat("markdown");
        when(feishuDocxClient.fetchMarkdownContent(eq("doccnABC"), any()))
                .thenThrow(new ClientException("飞书 Markdown 导出失败: permission denied"));
        when(feishuDocxClient.fetchRawContent(eq("doccnABC"), any())).thenReturn("plain fallback");

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/docx/doccnABC")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        FetchResult result = feishuFetcher.fetch(source);

        assertEquals("text/plain", result.mimeType());
        assertEquals("plain fallback", new String(result.content(), StandardCharsets.UTF_8));
        assertEquals("doccnABC.txt", result.fileName());
        verify(feishuDocxClient, never()).fetchPdfContent(any(), any(), anyLong());
        verify(feishuDocxClient).fetchRawContent(eq("doccnABC"), any());
    }

    @Test
    void shouldThrowWhenPdfFailsAndFallbackDisabled() {
        feishuProperties.setFallbackOnError(false);
        when(feishuDocxClient.fetchPdfContent(eq("doccnABC"), any(), anyLong()))
                .thenThrow(new ClientException("飞书 PDF 导出失败: 无权限"));

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/docx/doccnABC")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        assertThrows(ClientException.class, () -> feishuFetcher.fetch(source));
        verify(feishuDocxClient, never()).fetchMarkdownContent(any(), any());
        verify(feishuDocxClient, never()).fetchRawContent(any(), any());
    }

    @Test
    void shouldUsePlainFormatWhenConfigured() {
        feishuProperties.setContentFormat("plain");
        when(feishuDocxClient.fetchRawContent(eq("doccnABC"), any())).thenReturn("plain only");

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/docx/doccnABC")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        FetchResult result = feishuFetcher.fetch(source);

        assertEquals("text/plain", result.mimeType());
        assertEquals("doccnABC.txt", result.fileName());
        verify(feishuDocxClient).fetchRawContent(eq("doccnABC"), any());
        verify(feishuDocxClient, never()).fetchPdfContent(any(), any(), anyLong());
    }

    @Test
    void shouldRejectInvalidContentFormat() {
        feishuProperties.setContentFormat("invalid");

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/docx/doccnABC")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> feishuFetcher.fetch(source));
        assertEquals("无效的 feishu.content-format: invalid，允许值: pdf, markdown, plain", ex.getMessage());
    }

    @Test
    void shouldRejectNonDocxWikiNode() {
        when(feishuWikiClient.getNode(eq("wikcnSheet"), any())).thenReturn(
                new WikiNodeInfo("数据表", "sheet", "shtcnXXX", "space123"));

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/wiki/wikcnSheet")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        ClientException ex = assertThrows(ClientException.class, () -> feishuFetcher.fetch(source));
        assertEquals("暂仅支持 docx 类型的 wiki 节点，当前类型: sheet", ex.getMessage());
    }

    @Test
    void shouldRejectUnsupportedFeishuUrl() {
        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/drive/folder/abc")
                .build();

        assertThrows(ClientException.class, () -> feishuFetcher.fetch(source));
    }

    @Test
    void shouldPassMaxBytesToPdfDownload() {
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
        when(feishuDocxClient.fetchPdfContent(eq("doccnABC"), any(), eq(1024L))).thenReturn(pdf);

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/docx/doccnABC")
                .credentials(Map.of("tenantAccessToken", "token"))
                .maxBytes(1024L)
                .build();

        feishuFetcher.fetch(source);

        verify(feishuDocxClient).fetchPdfContent(eq("doccnABC"), any(), eq(1024L));
    }
}

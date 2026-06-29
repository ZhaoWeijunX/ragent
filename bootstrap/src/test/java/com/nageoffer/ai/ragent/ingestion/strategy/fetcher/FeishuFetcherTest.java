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
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuFetcherTest {

    @Mock
    private FeishuDocxClient feishuDocxClient;

    @Mock
    private FeishuWikiClient feishuWikiClient;

    private FeishuFetcher feishuFetcher;

    @BeforeEach
    void setUp() {
        feishuFetcher = new FeishuFetcher(new OkHttpClient(), feishuDocxClient, feishuWikiClient);
    }

    @Test
    void shouldFetchDocxUrlViaDocxClient() {
        when(feishuDocxClient.fetchRawContent(eq("doccnABC"), any())).thenReturn("hello docx");

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/docx/doccnABC")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        FetchResult result = feishuFetcher.fetch(source);

        assertEquals("text/plain", result.mimeType());
        assertEquals("hello docx", new String(result.content(), StandardCharsets.UTF_8));
        assertEquals("doccnABC.txt", result.fileName());
        verify(feishuDocxClient).fetchRawContent(eq("doccnABC"), any());
    }

    @Test
    void shouldFetchWikiDocxNodeViaWikiAndDocxClient() {
        when(feishuWikiClient.getNode(eq("wikcnXYZ"), any())).thenReturn(
                new WikiNodeInfo("产品手册", "docx", "doccnFromWiki", "space123"));
        when(feishuDocxClient.fetchRawContent(eq("doccnFromWiki"), any())).thenReturn("wiki content");

        DocumentSource source = DocumentSource.builder()
                .type(SourceType.FEISHU)
                .location("https://example.feishu.cn/wiki/wikcnXYZ")
                .credentials(Map.of("tenantAccessToken", "token"))
                .build();

        FetchResult result = feishuFetcher.fetch(source);

        assertEquals("wiki content", new String(result.content(), StandardCharsets.UTF_8));
        assertEquals("产品手册.txt", result.fileName());
        verify(feishuWikiClient).getNode(eq("wikcnXYZ"), any());
        verify(feishuDocxClient).fetchRawContent(eq("doccnFromWiki"), any());
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
}

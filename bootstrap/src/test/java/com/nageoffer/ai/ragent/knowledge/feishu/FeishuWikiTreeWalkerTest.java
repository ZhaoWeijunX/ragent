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

package com.nageoffer.ai.ragent.knowledge.feishu;

import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.FeishuWikiClient;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.WikiListNodesResult;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.WikiNodeInfo;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.WikiNodeItem;
import com.nageoffer.ai.ragent.knowledge.config.FeishuWikiImportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuWikiTreeWalkerTest {

    @Mock
    private FeishuWikiClient feishuWikiClient;

    private FeishuWikiTreeWalker treeWalker;

    @BeforeEach
    void setUp() {
        FeishuWikiImportProperties properties = new FeishuWikiImportProperties();
        properties.setMaxPagesPerJob(100);
        properties.setRateLimitPerMinute(1000);
        treeWalker = new FeishuWikiTreeWalker(feishuWikiClient, new FeishuApiRateLimiter(properties), properties);
    }

    @Test
    void shouldDiscoverPageOnlyDocx() {
        when(feishuWikiClient.getNode(eq("wikcnRoot"), any())).thenReturn(
                new WikiNodeInfo("手册", "docx", "doccn1", "space1"));

        FeishuWikiDiscoveryResult result = treeWalker.discover(
                "https://example.feishu.cn/wiki/wikcnRoot",
                FeishuWikiImportScope.PAGE_ONLY,
                Map.of("Authorization", "Bearer t"));

        assertEquals(1, result.pages().size());
        assertEquals("wikcnRoot", result.pages().get(0).nodeToken());
        assertEquals("https://example.feishu.cn/wiki/wikcnRoot", result.pages().get(0).wikiUrl());
        verify(feishuWikiClient).getNode(eq("wikcnRoot"), any());
    }

    @Test
    void shouldDiscoverSubtreeWithChildDocx() {
        when(feishuWikiClient.getNode(eq("wikcnRoot"), any())).thenReturn(
                new WikiNodeInfo("目录", "folder", "fld1", "space1"));
        when(feishuWikiClient.listNodes(eq("space1"), eq("wikcnRoot"), isNull(), eq(50), any()))
                .thenReturn(new WikiListNodesResult(
                        List.of(new WikiNodeItem("wikcnChild", "子页", "docx", "doccn2", "space1",
                                "wikcnRoot", false, "origin")),
                        null,
                        false));

        FeishuWikiDiscoveryResult result = treeWalker.discover(
                "https://example.feishu.cn/wiki/wikcnRoot",
                FeishuWikiImportScope.SUBTREE,
                Map.of("Authorization", "Bearer t"));

        assertEquals(1, result.pages().size());
        assertEquals("wikcnChild", result.pages().get(0).nodeToken());
    }

    @Test
    void shouldSkipNonDocxLeafInSubtree() {
        when(feishuWikiClient.getNode(eq("wikcnRoot"), any())).thenReturn(
                new WikiNodeInfo("根", "docx", "doccn1", "space1"));
        when(feishuWikiClient.listNodes(eq("space1"), eq("wikcnRoot"), isNull(), eq(50), any()))
                .thenReturn(new WikiListNodesResult(
                        List.of(new WikiNodeItem("wikcnSheet", "表格", "sheet", "sht1", "space1",
                                "wikcnRoot", false, "origin")),
                        null,
                        false));

        FeishuWikiDiscoveryResult result = treeWalker.discover(
                "https://example.feishu.cn/wiki/wikcnRoot",
                FeishuWikiImportScope.SUBTREE,
                Map.of("Authorization", "Bearer t"));

        assertEquals(1, result.pages().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("sheet"));
    }

    @Test
    void shouldDiscoverEntireSpaceWithoutGetNode() {
        when(feishuWikiClient.listNodes(eq("space123"), eq(""), isNull(), eq(50), any()))
                .thenReturn(new WikiListNodesResult(
                        List.of(new WikiNodeItem("wikcnChild", "子页", "docx", "doccn2", "space123",
                                "", false, "origin")),
                        null,
                        false));

        FeishuWikiDiscoveryResult result = treeWalker.discover(
                "https://example.feishu.cn/wiki/space/space123",
                FeishuWikiImportScope.ENTIRE_SPACE,
                Map.of("Authorization", "Bearer t"));

        assertEquals(1, result.pages().size());
        assertEquals("space123", result.spaceId());
        assertEquals(null, result.rootNodeToken());
    }
}

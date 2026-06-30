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

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.FeishuUrlParser;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.FeishuWikiClient;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.WikiListNodesResult;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.WikiNodeInfo;
import com.nageoffer.ai.ragent.ingestion.strategy.fetcher.WikiNodeItem;
import com.nageoffer.ai.ragent.knowledge.config.FeishuWikiImportProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 飞书 Wiki 知识库树遍历，枚举可导入的 docx 页面
 */
@Component
@RequiredArgsConstructor
public class FeishuWikiTreeWalker {

    private static final int LIST_PAGE_SIZE = 50;

    private final FeishuWikiClient feishuWikiClient;
    private final FeishuApiRateLimiter rateLimiter;
    private final FeishuWikiImportProperties importProperties;

    public FeishuWikiDiscoveryResult discover(String rootUrl, FeishuWikiImportScope scope, Map<String, String> headers) {
        if (!FeishuUrlParser.isFeishuHost(rootUrl)) {
            throw new ClientException("请提供飞书 Wiki 页面链接");
        }
        FeishuUrlParser.ParseResult parsed = FeishuUrlParser.parse(rootUrl);
        if (parsed.linkType() != FeishuUrlParser.LinkType.WIKI) {
            throw new ClientException("整库导入仅支持 Wiki 页面链接");
        }
        String rootNodeToken = parsed.token();
        String spaceIdFromUrl = parsed.wikiSpaceId();
        String wikiHost = extractWikiHost(rootUrl);

        List<FeishuWikiImportablePage> pages = new ArrayList<>();
        List<FeishuWikiSkippedNode> skipped = new ArrayList<>();
        int maxPages = Math.max(importProperties.getMaxPagesPerJob(), 1);

        if (scope == FeishuWikiImportScope.ENTIRE_SPACE
                && StringUtils.hasText(spaceIdFromUrl)
                && !StringUtils.hasText(rootNodeToken)) {
            traverseChildren(spaceIdFromUrl, null, wikiHost, headers, pages, skipped, maxPages);
            return new FeishuWikiDiscoveryResult(spaceIdFromUrl, null, pages, skipped);
        }

        if (!StringUtils.hasText(rootNodeToken)) {
            throw new ClientException("请粘贴知识库内具体页面链接，或用于整库导入的空间/设置页链接");
        }

        rateLimiter.acquire();
        WikiNodeInfo rootNode = feishuWikiClient.getNode(rootNodeToken, headers);
        String spaceId = StringUtils.hasText(spaceIdFromUrl) ? spaceIdFromUrl : rootNode.spaceId();
        if (!StringUtils.hasText(spaceId)) {
            throw new ClientException("无法解析飞书知识空间 ID");
        }

        if (scope == FeishuWikiImportScope.PAGE_ONLY) {
            collectPage(rootNodeToken, rootNode.title(), rootNode.objType(), wikiHost, pages, skipped, maxPages);
            return new FeishuWikiDiscoveryResult(spaceId, rootNodeToken, pages, skipped);
        }

        if (scope == FeishuWikiImportScope.SUBTREE) {
            collectPage(rootNodeToken, rootNode.title(), rootNode.objType(), wikiHost, pages, skipped, maxPages);
            if (pages.size() < maxPages) {
                traverseChildren(spaceId, rootNodeToken, wikiHost, headers, pages, skipped, maxPages);
            }
            return new FeishuWikiDiscoveryResult(spaceId, rootNodeToken, pages, skipped);
        }

        traverseChildren(spaceId, null, wikiHost, headers, pages, skipped, maxPages);
        return new FeishuWikiDiscoveryResult(spaceId, rootNodeToken, pages, skipped);
    }

    private void traverseChildren(String spaceId, String parentNodeToken, String wikiHost,
                                  Map<String, String> headers,
                                  List<FeishuWikiImportablePage> pages,
                                  List<FeishuWikiSkippedNode> skipped,
                                  int maxPages) {
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visitedParents = new HashSet<>();
        if (StringUtils.hasText(parentNodeToken)) {
            queue.add(parentNodeToken);
        } else {
            queue.add("");
        }

        while (!queue.isEmpty() && pages.size() < maxPages) {
            String parent = queue.poll();
            String parentKey = StringUtils.hasText(parent) ? parent : "__root__";
            if (!visitedParents.add(parentKey)) {
                continue;
            }
            listAllChildren(spaceId, parent, wikiHost, headers, pages, skipped, maxPages, queue);
        }
    }

    private void listAllChildren(String spaceId, String parentNodeToken, String wikiHost,
                                 Map<String, String> headers,
                                 List<FeishuWikiImportablePage> pages,
                                 List<FeishuWikiSkippedNode> skipped,
                                 int maxPages,
                                 Deque<String> childQueue) {
        String pageToken = null;
        do {
            rateLimiter.acquire();
            WikiListNodesResult result = feishuWikiClient.listNodes(
                    spaceId, parentNodeToken, pageToken, LIST_PAGE_SIZE, headers);
            for (WikiNodeItem item : result.items()) {
                if (pages.size() >= maxPages) {
                    return;
                }
                if (!StringUtils.hasText(item.nodeToken())) {
                    continue;
                }
                collectPage(item.nodeToken(), item.title(), item.objType(), wikiHost, pages, skipped, maxPages);
                if (item.hasChild()) {
                    childQueue.add(item.nodeToken());
                }
            }
            pageToken = result.hasMore() ? result.nextPageToken() : null;
        } while (StringUtils.hasText(pageToken) && pages.size() < maxPages);
    }

    private void collectPage(String nodeToken, String title, String objType, String wikiHost,
                             List<FeishuWikiImportablePage> pages,
                             List<FeishuWikiSkippedNode> skipped,
                             int maxPages) {
        if (pages.size() >= maxPages) {
            return;
        }
        if ("docx".equalsIgnoreCase(objType)) {
            pages.add(new FeishuWikiImportablePage(
                    nodeToken,
                    title,
                    FeishuUrlParser.buildWikiUrl(wikiHost, nodeToken),
                    objType
            ));
            return;
        }
        if (StringUtils.hasText(objType) && !"folder".equalsIgnoreCase(objType)) {
            skipped.add(new FeishuWikiSkippedNode(
                    nodeToken,
                    title,
                    objType,
                    "暂仅支持 docx 类型，当前: " + objType
            ));
        }
    }

    private static String extractWikiHost(String rootUrl) {
        URI uri = URI.create(rootUrl.trim());
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new ClientException("无法解析飞书链接域名");
        }
        return host;
    }
}

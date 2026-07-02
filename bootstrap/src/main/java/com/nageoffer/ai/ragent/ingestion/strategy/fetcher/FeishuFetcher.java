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
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.ingestion.domain.context.DocumentSource;
import com.nageoffer.ai.ragent.ingestion.domain.enums.SourceType;
import com.nageoffer.ai.ragent.knowledge.config.FeishuProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 飞书文档抓取器
 * 负责从飞书平台获取文档内容，支持云文档 docx 链接与知识库 wiki 页面（docx 节点）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuFetcher implements DocumentFetcher {

    private static final String MIME_MARKDOWN = "text/markdown";
    private static final String MIME_PLAIN = "text/plain";
    private static final String EXT_MARKDOWN = ".md";
    private static final String EXT_PLAIN = ".txt";

    private final FeishuAuthService feishuAuthService;
    private final FeishuDocxClient feishuDocxClient;
    private final FeishuWikiClient feishuWikiClient;
    private final FeishuProperties feishuProperties;

    @Override
    public SourceType supportedType() {
        return SourceType.FEISHU;
    }

    @Override
    public FetchResult fetch(DocumentSource source) {
        String location = source.getLocation();
        if (!StringUtils.hasText(location)) {
            throw new ServiceException("飞书文档地址不能为空");
        }

        Map<String, String> headers = feishuAuthService.buildAuthHeaders(source.getCredentials());
        FeishuUrlParser.ParseResult parsed = FeishuUrlParser.parse(location);

        return switch (parsed.linkType()) {
            case DOCX -> fetchDocx(parsed.token(), source.getFileName(), headers, null);
            case WIKI -> fetchWiki(parsed.token(), source.getFileName(), headers);
            case UNSUPPORTED -> throw new ClientException("不支持的飞书链接格式: " + location.trim());
        };
    }

    private FetchResult fetchWiki(String wikiNodeToken, String preferredFileName, Map<String, String> headers) {
        WikiNodeInfo node = feishuWikiClient.getNode(wikiNodeToken, headers);
        if (!"docx".equalsIgnoreCase(node.objType())) {
            throw new ClientException("暂仅支持 docx 类型的 wiki 节点，当前类型: " + node.objType());
        }
        return fetchDocx(node.objToken(), preferredFileName, headers, node.title());
    }

    private FetchResult fetchDocx(String documentToken, String preferredFileName, Map<String, String> headers,
                                  String title) {
        ContentFetch contentFetch = fetchDocumentContent(documentToken, headers);
        String fileName = resolveFileName(
                preferredFileName, title, documentToken, extensionForMime(contentFetch.mimeType()));
        return new FetchResult(contentFetch.content().getBytes(StandardCharsets.UTF_8), contentFetch.mimeType(), fileName);
    }

    private ContentFetch fetchDocumentContent(String documentToken, Map<String, String> headers) {
        if (!feishuProperties.isMarkdownContentFormat()) {
            return new ContentFetch(
                    feishuDocxClient.fetchRawContent(documentToken, headers), MIME_PLAIN);
        }
        try {
            return new ContentFetch(
                    feishuDocxClient.fetchMarkdownContent(documentToken, headers), MIME_MARKDOWN);
        } catch (RuntimeException markdownError) {
            if (!feishuProperties.isFallbackToPlainOnError()) {
                throw markdownError;
            }
            log.warn("飞书 Markdown 导出失败，回退纯文本, token={}, reason={}",
                    documentToken, markdownError.getMessage());
            return new ContentFetch(
                    feishuDocxClient.fetchRawContent(documentToken, headers), MIME_PLAIN);
        }
    }

    private String resolveFileName(String preferredFileName, String title, String fallbackToken, String extension) {
        if (StringUtils.hasText(preferredFileName)) {
            return preferredFileName;
        }
        if (StringUtils.hasText(title)) {
            return title.trim() + extension;
        }
        return fallbackToken + extension;
    }

    private static String extensionForMime(String mimeType) {
        return MIME_MARKDOWN.equals(mimeType) ? EXT_MARKDOWN : EXT_PLAIN;
    }

    private record ContentFetch(String content, String mimeType) {
    }
}

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
import com.nageoffer.ai.ragent.knowledge.config.FeishuProperties.ContentFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 飞书文档抓取器
 * 负责从飞书平台获取文档内容，支持云文档 docx 链接与知识库 wiki 页面（docx 节点）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeishuFetcher implements DocumentFetcher {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_MARKDOWN = "text/markdown";
    private static final String MIME_PLAIN = "text/plain";
    private static final String EXT_PDF = ".pdf";
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
        long maxBytes = source.getMaxBytes() != null ? source.getMaxBytes() : 0L;

        return switch (parsed.linkType()) {
            case DOCX -> fetchDocx(parsed.token(), source.getFileName(), headers, null, maxBytes);
            case WIKI -> fetchWiki(parsed.token(), source.getFileName(), headers, maxBytes);
            case UNSUPPORTED -> throw new ClientException("不支持的飞书链接格式: " + location.trim());
        };
    }

    private FetchResult fetchWiki(String wikiNodeToken, String preferredFileName, Map<String, String> headers,
                                  long maxBytes) {
        WikiNodeInfo node = feishuWikiClient.getNode(wikiNodeToken, headers);
        if (!"docx".equalsIgnoreCase(node.objType())) {
            throw new ClientException("暂仅支持 docx 类型的 wiki 节点，当前类型: " + node.objType());
        }
        return fetchDocx(node.objToken(), preferredFileName, headers, node.title(), maxBytes);
    }

    private FetchResult fetchDocx(String documentToken, String preferredFileName, Map<String, String> headers,
                                  String title, long maxBytes) {
        ContentFetch contentFetch = fetchDocumentContent(documentToken, headers, maxBytes);
        String fileName = resolveFileName(
                preferredFileName, title, documentToken, extensionForMime(contentFetch.mimeType()));
        return new FetchResult(contentFetch.content(), contentFetch.mimeType(), fileName);
    }

    private ContentFetch fetchDocumentContent(String documentToken, Map<String, String> headers, long maxBytes) {
        ContentFormat startFormat = feishuProperties.getResolvedContentFormat();
        List<ContentFormat> chain = chainFrom(startFormat);
        RuntimeException lastError = null;

        for (ContentFormat format : chain) {
            try {
                return fetchByFormat(format, documentToken, headers, maxBytes);
            } catch (RuntimeException error) {
                lastError = error;
                if (!feishuProperties.isFallbackOnError() || format == ContentFormat.PLAIN) {
                    throw error;
                }
                log.warn("飞书 {} 导出失败，降级下一格式, token={}, reason={}",
                        format.name().toLowerCase(), documentToken, error.getMessage());
            }
        }
        throw lastError != null ? lastError : new ClientException("飞书文档内容获取失败");
    }

    private static List<ContentFormat> chainFrom(ContentFormat start) {
        return switch (start) {
            case PDF -> List.of(ContentFormat.PDF, ContentFormat.MARKDOWN, ContentFormat.PLAIN);
            case MARKDOWN -> List.of(ContentFormat.MARKDOWN, ContentFormat.PLAIN);
            case PLAIN -> List.of(ContentFormat.PLAIN);
        };
    }

    private ContentFetch fetchByFormat(ContentFormat format, String documentToken, Map<String, String> headers,
                                       long maxBytes) {
        return switch (format) {
            case PDF -> ContentFetch.ofBytes(
                    feishuDocxClient.fetchPdfContent(documentToken, headers, maxBytes), MIME_PDF);
            case MARKDOWN -> ContentFetch.ofText(
                    feishuDocxClient.fetchMarkdownContent(documentToken, headers), MIME_MARKDOWN);
            case PLAIN -> ContentFetch.ofText(
                    feishuDocxClient.fetchRawContent(documentToken, headers), MIME_PLAIN);
        };
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
        return switch (mimeType) {
            case MIME_PDF -> EXT_PDF;
            case MIME_MARKDOWN -> EXT_MARKDOWN;
            default -> EXT_PLAIN;
        };
    }

    private record ContentFetch(byte[] content, String mimeType) {

        private static ContentFetch ofText(String text, String mimeType) {
            return new ContentFetch(text.getBytes(StandardCharsets.UTF_8), mimeType);
        }

        private static ContentFetch ofBytes(byte[] bytes, String mimeType) {
            return new ContentFetch(bytes, mimeType);
        }
    }
}

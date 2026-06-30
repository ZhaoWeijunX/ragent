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
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * 飞书链接解析器，识别云文档 docx 与知识库 wiki 页面链接并提取 token
 */
public final class FeishuUrlParser {

    public enum LinkType {
        DOCX,
        WIKI,
        UNSUPPORTED
    }

    public record ParseResult(LinkType linkType, String token, String wikiSpaceId) {

        public ParseResult(LinkType linkType, String token) {
            this(linkType, token, null);
        }
    }

    private FeishuUrlParser() {
    }

    /**
     * 是否为飞书 / Lark 域名链接
     */
    public static boolean isFeishuHost(String location) {
        if (!StringUtils.hasText(location)) {
            return false;
        }
        try {
            URI uri = URI.create(location.trim());
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return false;
            }
            host = host.toLowerCase();
            return host.endsWith(".feishu.cn") || "feishu.cn".equals(host)
                    || host.endsWith(".larksuite.com") || "larksuite.com".equals(host)
                    || host.endsWith(".larkoffice.com") || "larkoffice.com".equals(host);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 是否为可通过 Open API 拉取正文的 docx/docs/wiki 页面（不抛异常）
     */
    public static boolean isSupportedDocumentUrl(String location) {
        if (!isFeishuHost(location)) {
            return false;
        }
        String trimmed = location.trim();
        if (StringUtils.hasText(tryExtractDocToken(trimmed))) {
            return true;
        }
        if (containsWikiPath(trimmed)) {
            return StringUtils.hasText(tryExtractWikiToken(trimmed))
                    || StringUtils.hasText(tryExtractWikiSpaceId(trimmed));
        }
        return false;
    }

    public static ParseResult parse(String location) {
        if (!StringUtils.hasText(location)) {
            throw new ServiceException("飞书文档地址不能为空");
        }
        String trimmed = location.trim();

        String docToken = tryExtractDocToken(trimmed);
        if (StringUtils.hasText(docToken)) {
            return new ParseResult(LinkType.DOCX, docToken);
        }

        if (containsWikiPath(trimmed)) {
            String wikiToken = tryExtractWikiToken(trimmed);
            String wikiSpaceId = tryExtractWikiSpaceId(trimmed);
            if (!StringUtils.hasText(wikiToken) && !StringUtils.hasText(wikiSpaceId)) {
                throw new ClientException("请提供具体 wiki 页面链接或带知识空间 ID 的链接");
            }
            return new ParseResult(LinkType.WIKI, wikiToken, wikiSpaceId);
        }

        return new ParseResult(LinkType.UNSUPPORTED, null, null);
    }

    /**
     * 根据域名与节点 token 构造 wiki 页面 URL
     */
    public static String buildWikiUrl(String host, String nodeToken) {
        if (!StringUtils.hasText(host) || !StringUtils.hasText(nodeToken)) {
            throw new ServiceException("构造 Wiki URL 参数不完整");
        }
        return "https://" + host.trim() + "/wiki/" + nodeToken.trim();
    }

    private static boolean containsWikiPath(String location) {
        return location.contains("/wiki/") || location.endsWith("/wiki");
    }

    private static String tryExtractDocToken(String location) {
        String[] parts = location.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("docx".equalsIgnoreCase(parts[i]) || "docs".equalsIgnoreCase(parts[i])) {
                if (i + 1 < parts.length) {
                    return stripQuery(parts[i + 1]);
                }
            }
        }
        return null;
    }

    private static String tryExtractWikiToken(String location) {
        String[] parts = location.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (!"wiki".equalsIgnoreCase(parts[i]) || i + 1 >= parts.length) {
                continue;
            }
            String seg1 = stripQuery(parts[i + 1]);
            if ("space".equalsIgnoreCase(seg1)) {
                for (int j = i + 2; j < parts.length; j++) {
                    if ("nodes".equalsIgnoreCase(parts[j]) && j + 1 < parts.length) {
                        String nodeToken = stripQuery(parts[j + 1]);
                        return StringUtils.hasText(nodeToken) ? nodeToken : null;
                    }
                }
                return null;
            }
            if (isReservedWikiSegment(seg1)) {
                return null;
            }
            return StringUtils.hasText(seg1) ? seg1 : null;
        }
        return null;
    }

    /**
     * 从 /wiki/space/{spaceId} 或 /wiki/settings/{spaceId} 提取知识空间 ID
     */
    public static String tryExtractWikiSpaceId(String location) {
        if (!StringUtils.hasText(location)) {
            return null;
        }
        String[] parts = location.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (!"wiki".equalsIgnoreCase(parts[i]) || i + 1 >= parts.length) {
                continue;
            }
            String seg1 = stripQuery(parts[i + 1]);
            if (("space".equalsIgnoreCase(seg1) || "settings".equalsIgnoreCase(seg1)) && i + 2 < parts.length) {
                String spaceId = stripQuery(parts[i + 2]);
                return StringUtils.hasText(spaceId) ? spaceId : null;
            }
        }
        return null;
    }

    private static boolean isReservedWikiSegment(String segment) {
        if (!StringUtils.hasText(segment)) {
            return true;
        }
        String lower = segment.toLowerCase();
        return "space".equals(lower) || "settings".equals(lower) || "nodes".equals(lower);
    }

    private static String stripQuery(String token) {
        if (!StringUtils.hasText(token)) {
            return token;
        }
        int queryIndex = token.indexOf('?');
        return queryIndex > 0 ? token.substring(0, queryIndex) : token;
    }
}

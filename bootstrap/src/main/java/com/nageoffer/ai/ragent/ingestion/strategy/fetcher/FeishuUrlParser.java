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

    public record ParseResult(LinkType linkType, String token) {
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
            return StringUtils.hasText(tryExtractWikiToken(trimmed));
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
            if (!StringUtils.hasText(wikiToken)) {
                throw new ClientException("请提供具体 wiki 页面链接，不能只填写知识库空间首页");
            }
            return new ParseResult(LinkType.WIKI, wikiToken);
        }

        return new ParseResult(LinkType.UNSUPPORTED, null);
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
            if ("wiki".equalsIgnoreCase(parts[i])) {
                if (i + 1 < parts.length) {
                    String token = stripQuery(parts[i + 1]);
                    return StringUtils.hasText(token) ? token : null;
                }
                return null;
            }
        }
        return null;
    }

    private static String stripQuery(String token) {
        if (!StringUtils.hasText(token)) {
            return token;
        }
        int queryIndex = token.indexOf('?');
        return queryIndex > 0 ? token.substring(0, queryIndex) : token;
    }
}

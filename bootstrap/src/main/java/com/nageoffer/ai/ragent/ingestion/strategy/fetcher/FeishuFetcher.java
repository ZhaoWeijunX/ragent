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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 飞书文档抓取器
 * 负责从飞书平台获取文档内容，支持云文档 docx 链接与知识库 wiki 页面（docx 节点）
 */
@Component
@RequiredArgsConstructor
public class FeishuFetcher implements DocumentFetcher {

    private final FeishuAuthService feishuAuthService;
    private final FeishuDocxClient feishuDocxClient;
    private final FeishuWikiClient feishuWikiClient;

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
            case DOCX -> fetchDocx(parsed.token(), source.getFileName(), headers);
            case WIKI -> fetchWiki(parsed.token(), source.getFileName(), headers);
            case UNSUPPORTED -> throw new ClientException("不支持的飞书链接格式: " + location.trim());
        };
    }

    private FetchResult fetchWiki(String wikiNodeToken, String preferredFileName, Map<String, String> headers) {
        WikiNodeInfo node = feishuWikiClient.getNode(wikiNodeToken, headers);
        if (!"docx".equalsIgnoreCase(node.objType())) {
            throw new ClientException("暂仅支持 docx 类型的 wiki 节点，当前类型: " + node.objType());
        }
        String fileName = resolveFileName(preferredFileName, node.title(), node.objToken());
        return fetchDocx(node.objToken(), fileName, headers);
    }

    private FetchResult fetchDocx(String documentToken, String preferredFileName, Map<String, String> headers) {
        String content = feishuDocxClient.fetchRawContent(documentToken, headers);
        String fileName = resolveFileName(preferredFileName, null, documentToken);
        return new FetchResult(content.getBytes(StandardCharsets.UTF_8), "text/plain", fileName);
    }

    private String resolveFileName(String preferredFileName, String title, String fallbackToken) {
        if (StringUtils.hasText(preferredFileName)) {
            return preferredFileName;
        }
        if (StringUtils.hasText(title)) {
            return title.trim() + ".txt";
        }
        return fallbackToken + ".txt";
    }
}

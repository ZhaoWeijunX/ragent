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

package com.nageoffer.ai.ragent.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 飞书 Open API 配置（知识库 Remote URL 自动识别飞书链接时使用）
 */
@Configuration
@ConfigurationProperties("feishu")
@Data
public class FeishuProperties {

    /**
     * 是否启用飞书 URL 拉取；关闭时遇到飞书链接将明确报错，不会 HTTP 兜底
     */
    private boolean enabled = false;

    /**
     * 飞书自建应用 App ID
     */
    private String appId;

    /**
     * 飞书自建应用 App Secret
     */
    private String appSecret;

    /**
     * 可选：直接使用 tenant_access_token，非空时优先于 app-id/secret 换 token
     */
    private String tenantAccessToken;

    /**
     * 文档导出格式：markdown（默认）或 plain（raw_content 纯文本）
     */
    private String contentFormat = "markdown";

    /**
     * Markdown 导出 API 失败时是否回退 raw_content 纯文本
     */
    private boolean fallbackToPlainOnError = true;

    public boolean isMarkdownContentFormat() {
        return !"plain".equalsIgnoreCase(contentFormat);
    }
}

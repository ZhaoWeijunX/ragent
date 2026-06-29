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

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 从配置文件解析飞书 API 凭证，供知识库 Remote URL 拉取飞书文档使用
 */
@Component
@RequiredArgsConstructor
public class FeishuCredentialsProvider {

    private final FeishuProperties feishuProperties;

    public void validateConfigured() {
        if (!feishuProperties.isEnabled()) {
            throw new ClientException("飞书集成未启用，请在配置文件中设置 feishu.enabled=true");
        }
        Map<String, String> credentials = resolve();
        boolean hasToken = StringUtils.hasText(credentials.get("tenantAccessToken"))
                || StringUtils.hasText(credentials.get("accessToken"));
        boolean hasApp = StringUtils.hasText(credentials.get("app_id"))
                && StringUtils.hasText(credentials.get("app_secret"));
        if (!hasToken && !hasApp) {
            throw new ClientException("飞书集成未配置凭证，请设置 feishu.app-id/feishu.app-secret 或 feishu.tenant-access-token");
        }
    }

    public Map<String, String> resolve() {
        Map<String, String> credentials = new HashMap<>();
        if (StringUtils.hasText(feishuProperties.getTenantAccessToken())) {
            credentials.put("tenantAccessToken", feishuProperties.getTenantAccessToken().trim());
        }
        if (StringUtils.hasText(feishuProperties.getAppId())) {
            credentials.put("app_id", feishuProperties.getAppId().trim());
        }
        if (StringUtils.hasText(feishuProperties.getAppSecret())) {
            credentials.put("app_secret", feishuProperties.getAppSecret().trim());
        }
        return credentials;
    }
}

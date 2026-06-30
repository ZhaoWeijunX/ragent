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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 飞书 Open API 鉴权：解析 access token 并组装请求头
 */
@Component
@RequiredArgsConstructor
public class FeishuAuthService {

    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal/";

    @Qualifier("syncHttpClient")
    private final OkHttpClient okHttpClient;

    public Map<String, String> buildAuthHeaders(Map<String, String> credentials) {
        return buildAuthHeaders(resolveAccessToken(credentials));
    }

    public Map<String, String> buildAuthHeaders(String accessToken) {
        Map<String, String> headers = new HashMap<>();
        if (StringUtils.hasText(accessToken)) {
            headers.put("Authorization", "Bearer " + accessToken);
        }
        return headers;
    }

    public String resolveAccessToken(Map<String, String> credentials) {
        if (credentials == null) {
            return null;
        }
        String token = credentials.get("tenantAccessToken");
        if (!StringUtils.hasText(token)) {
            token = credentials.get("accessToken");
        }
        if (StringUtils.hasText(token)) {
            return token;
        }
        String appId = credentials.get("app_id");
        String appSecret = credentials.get("app_secret");
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            return null;
        }
        return requestTenantAccessToken(appId, appSecret);
    }

    private String requestTenantAccessToken(String appId, String appSecret) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("app_id", appId);
            payload.addProperty("app_secret", appSecret);

            Request request = new Request.Builder()
                    .url(TOKEN_URL)
                    .post(RequestBody.create(payload.toString(), MediaType.parse("application/json")))
                    .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new ServiceException("飞书令牌请求失败: " + response.code());
                }
                String raw = response.body().string();
                JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                if (json.has("tenant_access_token")) {
                    return json.get("tenant_access_token").getAsString();
                }
                return null;
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("飞书令牌请求失败: " + e.getMessage());
        }
    }
}

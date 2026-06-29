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
import com.nageoffer.ai.ragent.ingestion.util.HttpClientHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 飞书知识库 wiki API 客户端
 */
@Component
@RequiredArgsConstructor
public class FeishuWikiClient {

    private static final String GET_NODE_URL =
            "https://open.feishu.cn/open-apis/wiki/v2/spaces/get_node?token=%s";

    private final HttpClientHelper httpClientHelper;

    /**
     * 根据 wiki 节点 token 获取节点信息
     */
    public WikiNodeInfo getNode(String wikiNodeToken, Map<String, String> headers) {
        String url = String.format(GET_NODE_URL, wikiNodeToken);
        HttpClientHelper.HttpFetchResponse resp = httpClientHelper.get(url, headers);
        return parseNodeResponse(resp.body());
    }

    private WikiNodeInfo parseNodeResponse(byte[] body) {
        JsonObject root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.has("code") && root.get("code").getAsInt() != 0) {
            String msg = root.has("msg") ? root.get("msg").getAsString() : "unknown";
            throw new ServiceException("飞书 Wiki API 请求失败: " + msg);
        }
        if (!root.has("data") || !root.getAsJsonObject("data").has("node")) {
            throw new ServiceException("飞书 Wiki API 响应缺少节点信息");
        }
        JsonObject node = root.getAsJsonObject("data").getAsJsonObject("node");
        String title = node.has("title") ? node.get("title").getAsString() : null;
        String objType = node.has("obj_type") ? node.get("obj_type").getAsString() : null;
        String objToken = node.has("obj_token") ? node.get("obj_token").getAsString() : null;
        String spaceId = node.has("space_id") ? node.get("space_id").getAsString() : null;
        if (!StringUtils.hasText(objType) || !StringUtils.hasText(objToken)) {
            throw new ServiceException("飞书 Wiki 节点信息不完整");
        }
        return new WikiNodeInfo(title, objType, objToken, spaceId);
    }
}

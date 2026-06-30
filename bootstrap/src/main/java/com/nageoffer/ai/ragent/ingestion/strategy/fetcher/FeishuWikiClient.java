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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.ingestion.util.HttpClientHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 飞书知识库 wiki API 客户端
 */
@Component
@RequiredArgsConstructor
public class FeishuWikiClient {

    private static final String GET_NODE_URL = "https://open.feishu.cn/open-apis/wiki/v2/spaces/get_node";
    private static final String LIST_NODES_URL = "https://open.feishu.cn/open-apis/wiki/v2/spaces/%s/nodes";

    private final HttpClientHelper httpClientHelper;

    /**
     * 根据 wiki 节点 token 获取节点信息
     */
    public WikiNodeInfo getNode(String wikiNodeToken, Map<String, String> headers) {
        return getNode(wikiNodeToken, null, headers);
    }

    /**
     * 根据节点或云文档 token 获取 wiki 节点信息
     *
     * @param objType 云文档 token 查询时传入 docx/doc 等；wiki 节点可传 null
     */
    public WikiNodeInfo getNode(String token, String objType, Map<String, String> headers) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(GET_NODE_URL)
                .queryParam("token", token);
        if (StringUtils.hasText(objType)) {
            builder.queryParam("obj_type", objType);
        }
        HttpClientHelper.HttpFetchResponse resp = wikiGet(builder.toUriString(), headers);
        return parseNodeResponse(resp.body());
    }

    /**
     * 分页获取知识空间子节点列表
     */
    public WikiListNodesResult listNodes(String spaceId, String parentNodeToken, String pageToken,
                                         int pageSize, Map<String, String> headers) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(String.format(LIST_NODES_URL, spaceId))
                .queryParam("page_size", Math.min(Math.max(pageSize, 1), 50));
        if (StringUtils.hasText(parentNodeToken)) {
            builder.queryParam("parent_node_token", parentNodeToken);
        }
        if (StringUtils.hasText(pageToken)) {
            builder.queryParam("page_token", pageToken);
        }
        HttpClientHelper.HttpFetchResponse resp = wikiGet(builder.toUriString(), headers);
        return parseListNodesResponse(resp.body());
    }

    private HttpClientHelper.HttpFetchResponse wikiGet(String url, Map<String, String> headers) {
        try {
            return httpClientHelper.get(url, headers);
        } catch (ServiceException e) {
            throw translateWikiHttpError(e);
        }
    }

    private RuntimeException translateWikiHttpError(ServiceException e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return e;
        }
        if (message.contains("\"code\":131006") || message.contains("131006")) {
            return new ClientException(buildPermissionDeniedMessage(message));
        }
        if (message.contains("\"code\":131005") || message.contains("131005")) {
            return new ClientException(
                    "飞书 Wiki 节点不存在或应用无法访问：请确认链接为知识库内具体页面（非空间首页），"
                            + "且应用已安装到目标租户并拥有该知识库访问权限");
        }
        if (message.contains("\"code\":131002") || message.contains("131002")) {
            return new ClientException("飞书 Wiki 请求参数错误，请检查粘贴的链接是否完整");
        }
        return e;
    }

    private WikiNodeInfo parseNodeResponse(byte[] body) {
        JsonObject root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.has("code") && root.get("code").getAsInt() != 0) {
            String msg = root.has("msg") ? root.get("msg").getAsString() : "unknown";
            int code = root.get("code").getAsInt();
            if (code == 131005) {
                throw new ClientException(
                        "飞书 Wiki 节点不存在或应用无法访问：请确认链接为知识库内具体页面，且应用拥有该知识库访问权限");
            }
            if (code == 131006) {
                throw new ClientException(buildPermissionDeniedMessage(msg));
            }
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
        if (!StringUtils.hasText(objType)) {
            throw new ServiceException("飞书 Wiki 节点信息不完整");
        }
        boolean folderLike = "folder".equalsIgnoreCase(objType);
        if (!folderLike && !StringUtils.hasText(objToken)) {
            throw new ServiceException("飞书 Wiki 节点信息不完整");
        }
        return new WikiNodeInfo(title, objType, objToken, spaceId);
    }

    private WikiListNodesResult parseListNodesResponse(byte[] body) {
        JsonObject root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.has("code") && root.get("code").getAsInt() != 0) {
            String msg = root.has("msg") ? root.get("msg").getAsString() : "unknown";
            int code = root.get("code").getAsInt();
            if (code == 131005) {
                throw new ClientException("飞书知识空间不存在或应用无法访问，请检查 space_id 与知识库权限");
            }
            if (code == 131006) {
                throw new ClientException(buildPermissionDeniedMessage(msg));
            }
            throw new ServiceException("飞书 Wiki 子节点列表请求失败: " + msg);
        }
        if (!root.has("data")) {
            throw new ServiceException("飞书 Wiki 子节点列表响应缺少 data");
        }
        JsonObject data = root.getAsJsonObject("data");
        List<WikiNodeItem> items = new ArrayList<>();
        if (data.has("items") && data.get("items").isJsonArray()) {
            JsonArray array = data.getAsJsonArray("items");
            for (int i = 0; i < array.size(); i++) {
                items.add(parseListNodeItem(array.get(i).getAsJsonObject()));
            }
        }
        String nextPageToken = data.has("page_token") && !data.get("page_token").isJsonNull()
                ? data.get("page_token").getAsString() : null;
        boolean hasMore = data.has("has_more") && data.get("has_more").getAsBoolean();
        return new WikiListNodesResult(items, nextPageToken, hasMore);
    }

    private WikiNodeItem parseListNodeItem(JsonObject node) {
        String nodeToken = textOrNull(node, "node_token");
        String title = textOrNull(node, "title");
        String objType = textOrNull(node, "obj_type");
        String objToken = textOrNull(node, "obj_token");
        String spaceId = textOrNull(node, "space_id");
        String parentNodeToken = textOrNull(node, "parent_node_token");
        boolean hasChild = node.has("has_child") && node.get("has_child").getAsBoolean();
        String nodeType = textOrNull(node, "node_type");
        return new WikiNodeItem(nodeToken, title, objType, objToken, spaceId, parentNodeToken, hasChild, nodeType);
    }

    private static String buildPermissionDeniedMessage(String detail) {
        String lower = detail != null ? detail.toLowerCase() : "";
        if (lower.contains("wiki space permission") || lower.contains("space permission denied")) {
            return "飞书知识空间权限不足：请在飞书开放平台为应用开通「查看知识空间节点列表」(wiki:node:retrieve) "
                    + "或「查看知识库」(wiki:wiki:readonly)，并在目标知识库设置中将该应用/机器人添加为知识空间成员";
        }
        return "飞书 Wiki 权限不足：请为应用开通 wiki:node:retrieve 或 wiki:wiki:readonly，"
                + "并将应用添加为目标知识库成员；单页导入可改用「仅当前页」范围";
    }

    private static String textOrNull(JsonObject node, String field) {
        if (!node.has(field) || node.get(field).isJsonNull()) {
            return null;
        }
        return node.get(field).getAsString();
    }
}

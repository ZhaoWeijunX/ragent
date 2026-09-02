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

package com.nageoffer.ai.ragent.agent.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.agent.tool.AgentToolCatalog.McpToolBinding;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolExecutor;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将 MCP 执行器适配为 AgentScope 工具，继承 ToolBase 以接入权限检查
 */
@Slf4j
public class McpToolBridge extends ToolBase {

    private final McpToolExecutor executor;

    /**
     * 意图树配置的执行前确认开关
     */
    private final boolean requireConfirm;

    /**
     * MCP 服务端声明的 readOnlyHint，null 表示未声明
     */
    private final Boolean readOnlyHint;

    public McpToolBridge(McpToolBinding binding) {
        super(ToolBase.builder()
                .name(binding.toolId())
                .description(resolveDescription(binding))
                .inputSchema(buildInputSchema(binding.executor()))
                .readOnly(Boolean.TRUE.equals(resolveReadOnlyHint(binding.executor()))));
        this.executor = binding.executor();
        this.requireConfirm = binding.requireConfirm();
        this.readOnlyHint = resolveReadOnlyHint(binding.executor());
    }

    /**
     * 需要确认时返回 ask，否则返回 allow
     */
    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> toolInput, PermissionContextState context) {
        if (!needsConfirm()) {
            return Mono.just(PermissionDecision.allow("该工具未配置执行前确认"));
        }
        return Mono.just(PermissionDecision.ask("该操作会产生实际业务影响，执行前需要你确认"));
    }

    /**
     * 意图树勾选或 readOnlyHint 显式为 false 时需要确认
     */
    private boolean needsConfirm() {
        return requireConfirm || Boolean.FALSE.equals(readOnlyHint);
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static String resolveDescription(McpToolBinding binding) {
        if (StrUtil.isNotBlank(binding.description())) {
            return binding.description();
        }
        return StrUtil.emptyIfNull(binding.executor().getToolDefinition().description());
    }

    private static Map<String, Object> buildInputSchema(McpToolExecutor executor) {
        JsonSchema schema = executor.getToolDefinition().inputSchema();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", schema == null || StrUtil.isBlank(schema.type()) ? "object" : schema.type());
        parameters.put("properties", schema == null || schema.properties() == null ? Map.of() : schema.properties());
        if (schema != null && CollUtil.isNotEmpty(schema.required())) {
            parameters.put("required", schema.required());
        }
        return parameters;
    }

    /**
     * 取 MCP annotations 的 readOnlyHint，未声明返回 null
     */
    private static Boolean resolveReadOnlyHint(McpToolExecutor executor) {
        Tool definition = executor.getToolDefinition();
        ToolAnnotations annotations = definition.annotations();
        return annotations == null ? null : annotations.readOnlyHint();
    }

    private ToolResultBlock execute(ToolCallParam param) {
        String toolCallId = param.getToolUseBlock() == null ? null : param.getToolUseBlock().getId();
        try {
            CallToolResult result = executor.execute(new HashMap<>(param.getInput()));
            boolean isError = result != null && Boolean.TRUE.equals(result.isError());
            return buildResult(toolCallId, extractText(result), isError);
        } catch (Exception e) {
            log.error("MCP 工具调用异常, toolId: {}", getName(), e);
            return buildResult(toolCallId, "工具调用异常: " + e.getMessage(), true);
        }
    }

    private ToolResultBlock buildResult(String toolCallId, String text, boolean isError) {
        return ToolResultBlock.builder()
                .id(toolCallId)
                .name(getName())
                .output(TextBlock.builder().text(text).build())
                .state(isError ? ToolResultState.ERROR : ToolResultState.SUCCESS)
                .build();
    }

    private String extractText(CallToolResult result) {
        if (result == null || CollUtil.isEmpty(result.content())) {
            return "（工具无返回内容）";
        }
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(content -> ((TextContent) content).text())
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n"));
    }
}

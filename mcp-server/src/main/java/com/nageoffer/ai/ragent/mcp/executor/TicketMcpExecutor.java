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

package com.nageoffer.ai.ragent.mcp.executor;

import com.nageoffer.ai.ragent.mcp.dao.SupportTicketDao;
import com.nageoffer.ai.ragent.mcp.model.SupportTicket;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketMcpExecutor {

    private static final String TOOL_ID = "ticket_query";
    private static final int MAX_LIMIT = 50;

    private static final String STATUS_PENDING = "待处理";
    private static final String STATUS_IN_PROGRESS = "处理中";
    private static final String STATUS_RESOLVED = "已解决";
    private static final String STATUS_CLOSED = "已关闭";
    private static final List<String> STATUSES = List.of(STATUS_PENDING, STATUS_IN_PROGRESS, STATUS_RESOLVED, STATUS_CLOSED);
    private static final List<String> PRIORITIES = List.of("紧急", "高", "中", "低");

    private final SupportTicketDao supportTicketDao;

    @Bean
    public McpServerFeatures.SyncToolSpecification ticketToolSpecification() {
        return new McpServerFeatures.SyncToolSpecification(buildTool(),
                (exchange, request) -> handleCall(request));
    }

    private Tool buildTool() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("region", Map.of(
                "type", "string",
                "description", "地区筛选：华东、华南、华北、西南、西北，不填则查询全国",
                "enum", List.of("华东", "华南", "华北", "西南", "西北")
        ));

        properties.put("status", Map.of(
                "type", "string",
                "description", "工单状态筛选：待处理、处理中、已解决、已关闭，不填则查询全部状态",
                "enum", STATUSES
        ));

        properties.put("priority", Map.of(
                "type", "string",
                "description", "优先级筛选：紧急、高、中、低，不填则查询全部优先级",
                "enum", List.of("紧急", "高", "中", "低")
        ));

        properties.put("product", Map.of(
                "type", "string",
                "description", "产品筛选：企业版、专业版、基础版，不填则查询全部产品",
                "enum", List.of("企业版", "专业版", "基础版")
        ));

        properties.put("customerName", Map.of(
                "type", "string",
                "description", "客户名称关键字，支持模糊匹配。用户问题中出现公司/客户名（如腾讯科技、阿里巴巴）时必须填写"
        ));

        properties.put("queryType", Map.of(
                "type", "string",
                "description", "查询类型：summary(汇总概览)、list(工单列表)、stats(统计分析)",
                "enum", List.of("summary", "list", "stats"),
                "default", "summary"
        ));

        properties.put("limit", Map.of(
                "type", "integer",
                "description", "返回记录数限制，默认10",
                "default", 10
        ));

        JsonSchema inputSchema = new JsonSchema(
                "object", properties, List.of(), null, null, null);

        return Tool.builder()
                .name(TOOL_ID)
                .description("查询客户技术支持工单数据，支持按地区、状态、优先级、产品、客户等维度筛选，支持汇总概览、工单列表、统计分析等多种查询")
                .inputSchema(inputSchema)
                .build();
    }

    private CallToolResult handleCall(CallToolRequest request) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
            String region = stringArg(args, "region");
            String status = stringArg(args, "status");
            String priority = stringArg(args, "priority");
            String product = stringArg(args, "product");
            String customerName = stringArg(args, "customerName");
            String queryType = stringArg(args, "queryType");
            Integer limit = intArg(args, "limit");

            if (queryType == null || queryType.isBlank()) queryType = "summary";
            if (limit == null || limit <= 0) limit = 10;
            limit = Math.min(limit, MAX_LIMIT);

            List<SupportTicket> data = supportTicketDao.query(region, status, priority, product, customerName);

            String result = switch (queryType) {
                case "list" -> buildListResult(data, limit, region, status, priority, product, customerName);
                case "stats" -> buildStatsResult(data, region, status, priority, product, customerName);
                default -> buildSummaryResult(data, region, status, priority, product, customerName);
            };

            log.info("MCP 工具调用完成, toolId={}, queryType={}, region={}, status={}, priority={}, customerName={}, elapsed={}ms",
                    TOOL_ID, queryType, region, status, priority, customerName, System.currentTimeMillis() - startMs);
            return successResult(result);
        } catch (Exception e) {
            log.error("MCP 工具调用失败, toolId={}, elapsed={}ms",
                    TOOL_ID, System.currentTimeMillis() - startMs, e);
            return errorResult("查询失败: " + e.getMessage());
        }
    }

    private String buildSummaryResult(List<SupportTicket> data, String region, String status,
                                      String priority, String product, String customerName) {
        int total = data.size();
        long pending = data.stream().filter(t -> STATUS_PENDING.equals(t.getStatus())).count();
        long inProgress = data.stream().filter(t -> STATUS_IN_PROGRESS.equals(t.getStatus())).count();
        long resolved = data.stream().filter(t -> STATUS_RESOLVED.equals(t.getStatus())).count();
        long closed = data.stream().filter(t -> STATUS_CLOSED.equals(t.getStatus())).count();
        long urgent = data.stream().filter(t -> "紧急".equals(t.getPriority())).count();
        long high = data.stream().filter(t -> "高".equals(t.getPriority())).count();

        StringBuilder sb = new StringBuilder();
        sb.append("【客户工单汇总概览】\n\n");
        appendFilters(sb, region, status, priority, product, customerName);

        sb.append(String.format("工单总数: %d 个\n\n", total));
        if (total == 0) {
            sb.append(buildEmptyMessage(customerName));
            return sb.toString().trim();
        }

        sb.append("【状态分布】\n");
        sb.append(String.format("  待处理: %d 个\n", pending));
        sb.append(String.format("  处理中: %d 个\n", inProgress));
        sb.append(String.format("  已解决: %d 个\n", resolved));
        sb.append(String.format("  已关闭: %d 个\n\n", closed));

        double resolveRate = (resolved + closed) * 100.0 / total;
        sb.append(String.format("解决率: %.1f%%\n", resolveRate));

        if (urgent + high > 0) {
            sb.append(String.format("\n⚠ 紧急/高优先级工单: %d 个（紧急 %d，高 %d）\n", urgent + high, urgent, high));
        }

        Map<String, Long> byProduct = data.stream()
                .collect(Collectors.groupingBy(SupportTicket::getProduct, Collectors.counting()));
        if (product == null && !byProduct.isEmpty()) {
            sb.append("\n【按产品分布】\n");
            byProduct.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .forEach(e -> sb.append(String.format("  %s: %d 个\n", e.getKey(), e.getValue())));
        }

        Map<String, Long> byRegion = data.stream()
                .collect(Collectors.groupingBy(SupportTicket::getRegion, Collectors.counting()));
        if (region == null && !byRegion.isEmpty()) {
            sb.append("\n【按地区分布】\n");
            byRegion.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .forEach(e -> sb.append(String.format("  %s: %d 个\n", e.getKey(), e.getValue())));
        }

        return sb.toString().trim();
    }

    private String buildListResult(List<SupportTicket> data, int limit,
                                   String region, String status, String priority,
                                   String product, String customerName) {
        StringBuilder sb = new StringBuilder();
        sb.append("【工单列表】\n\n");
        appendFilters(sb, region, status, priority, product, customerName);

        if (data.isEmpty()) {
            sb.append(buildEmptyMessage(customerName));
            return sb.toString().trim();
        }

        List<SupportTicket> sorted = data.stream()
                .sorted((a, b) -> {
                    int pa = PRIORITIES.indexOf(a.getPriority());
                    int pb = PRIORITIES.indexOf(b.getPriority());
                    if (pa != pb) return Integer.compare(pa, pb);
                    return b.getCreatedDate().compareTo(a.getCreatedDate());
                })
                .limit(limit)
                .toList();

        sb.append(String.format("共 %d 条，显示 %d 条（按优先级排序）\n\n", data.size(), sorted.size()));

        for (int i = 0; i < sorted.size(); i++) {
            SupportTicket t = sorted.get(i);
            sb.append(String.format("%d. [%s] %s\n", i + 1, t.getTicketNo(), t.getTitle()));
            sb.append(String.format("   客户: %s | 产品: %s | 地区: %s\n", t.getCustomer(), t.getProduct(), t.getRegion()));
            sb.append(String.format("   优先级: %s | 状态: %s | 分类: %s\n", t.getPriority(), t.getStatus(), t.getCategory()));
            sb.append(String.format("   处理人: %s | 创建时间: %s\n\n", t.getEngineer(), t.getCreatedDate()));
        }

        return sb.toString().trim();
    }

    private String buildStatsResult(List<SupportTicket> data, String region, String status,
                                    String priority, String product, String customerName) {
        StringBuilder sb = new StringBuilder();
        sb.append("【工单统计分析】\n\n");
        appendFilters(sb, region, status, priority, product, customerName);

        if (data.isEmpty()) {
            sb.append(buildEmptyMessage(customerName));
            return sb.toString().trim();
        }

        Map<String, Long> byCategory = data.stream()
                .collect(Collectors.groupingBy(SupportTicket::getCategory, Collectors.counting()));
        sb.append("【问题分类统计】\n");
        byCategory.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> sb.append(String.format("  %s: %d 个 (%.1f%%)\n",
                        e.getKey(), e.getValue(), e.getValue() * 100.0 / data.size())));

        sb.append("\n【各产品解决率】\n");
        Map<String, List<SupportTicket>> byProduct = data.stream()
                .collect(Collectors.groupingBy(SupportTicket::getProduct));
        byProduct.forEach((productName, tickets) -> {
            long resolvedCount = tickets.stream()
                    .filter(t -> STATUS_RESOLVED.equals(t.getStatus()) || STATUS_CLOSED.equals(t.getStatus())).count();
            sb.append(String.format("  %s: %.1f%% (%d/%d)\n",
                    productName, resolvedCount * 100.0 / tickets.size(), resolvedCount, tickets.size()));
        });

        sb.append("\n【处理人工单量排名】\n");
        Map<String, Long> byEngineer = data.stream()
                .filter(t -> STATUS_PENDING.equals(t.getStatus()) || STATUS_IN_PROGRESS.equals(t.getStatus()))
                .collect(Collectors.groupingBy(SupportTicket::getEngineer, Collectors.counting()));
        byEngineer.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(5)
                .forEach(e -> sb.append(String.format("  %s: %d 个待处理\n", e.getKey(), e.getValue())));

        return sb.toString().trim();
    }

    private static void appendFilters(StringBuilder sb, String region, String status,
                                      String priority, String product, String customerName) {
        List<String> filters = new ArrayList<>();
        if (region != null && !region.isBlank()) filters.add("地区: " + region);
        if (status != null && !status.isBlank()) filters.add("状态: " + status);
        if (priority != null && !priority.isBlank()) filters.add("优先级: " + priority);
        if (product != null && !product.isBlank()) filters.add("产品: " + product);
        if (customerName != null && !customerName.isBlank()) filters.add("客户: " + customerName);
        if (!filters.isEmpty()) {
            sb.append("筛选条件: ").append(String.join("，", filters)).append("\n\n");
        }
    }

    private static String buildEmptyMessage(String customerName) {
        if (customerName != null && !customerName.isBlank()) {
            return "未查询到客户「" + customerName + "」符合当前筛选条件的工单";
        }
        return "暂无工单数据";
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    private static Integer intArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        return null;
    }

    private static CallToolResult successResult(String text) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(text)))
                .isError(false)
                .build();
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}

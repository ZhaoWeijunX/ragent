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

import com.nageoffer.ai.ragent.mcp.dao.SalesOrderDao;
import com.nageoffer.ai.ragent.mcp.model.SalesOrder;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesMcpExecutor {

    private static final String TOOL_ID = "sales_query";
    private static final int MAX_LIMIT = 50;

    private final SalesOrderDao salesOrderDao;

    @Bean
    public McpServerFeatures.SyncToolSpecification salesToolSpecification() {
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

        properties.put("period", Map.of(
                "type", "string",
                "description", "时间段：本月、上月、本季度、上季度、本年，默认本月",
                "enum", List.of("本月", "上月", "本季度", "上季度", "本年"),
                "default", "本月"
        ));

        properties.put("product", Map.of(
                "type", "string",
                "description", "产品筛选：企业版、专业版、基础版，不填则查询全部产品",
                "enum", List.of("企业版", "专业版", "基础版")
        ));

        properties.put("salesPerson", Map.of(
                "type", "string",
                "description", "销售人员姓名，不填则查询全部销售"
        ));

        properties.put("queryType", Map.of(
                "type", "string",
                "description", "查询类型：summary(汇总)、ranking(排名)、detail(明细)、trend(趋势)",
                "enum", List.of("summary", "ranking", "detail", "trend"),
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
                .description("查询软件销售数据，支持按地区、时间、产品、销售人员等维度筛选，支持汇总统计、排名、明细列表等多种查询")
                .inputSchema(inputSchema)
                .build();
    }

    private CallToolResult handleCall(CallToolRequest request) {
        long startMs = System.currentTimeMillis();
        try {
            Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
            String region = stringArg(args, "region");
            String period = stringArg(args, "period");
            String product = stringArg(args, "product");
            String salesPerson = stringArg(args, "salesPerson");
            String queryType = stringArg(args, "queryType");
            Integer limit = intArg(args, "limit");

            if (period == null || period.isBlank()) period = "本月";
            if (queryType == null || queryType.isBlank()) queryType = "summary";
            if (limit == null || limit <= 0) limit = 10;
            limit = Math.min(limit, MAX_LIMIT);

            List<SalesOrder> data = salesOrderDao.query(region, period, product, salesPerson);

            String result = switch (queryType) {
                case "ranking" -> buildRankingResult(data, region, period, limit);
                case "detail" -> buildDetailResult(data, region, period, limit);
                case "trend" -> buildTrendResult(data, region, period);
                default -> buildSummaryResult(data, region, period, product, salesPerson);
            };

            log.info("MCP 工具调用完成, toolId={}, queryType={}, region={}, period={}, elapsed={}ms",
                    TOOL_ID, queryType, region, period, System.currentTimeMillis() - startMs);
            return successResult(result);
        } catch (Exception e) {
            log.error("MCP 工具调用失败, toolId={}, elapsed={}ms",
                    TOOL_ID, System.currentTimeMillis() - startMs, e);
            return errorResult("查询失败: " + e.getMessage());
        }
    }

    private String buildSummaryResult(List<SalesOrder> data, String region, String period,
                                      String product, String salesPerson) {
        double totalAmount = data.stream().mapToDouble(this::amountValue).sum();
        int orderCount = data.size();
        double avgAmount = orderCount > 0 ? totalAmount / orderCount : 0;
        Map<String, Double> byProduct = data.stream()
                .collect(Collectors.groupingBy(SalesOrder::getProduct, Collectors.summingDouble(this::amountValue)));
        Map<String, Double> byRegion = data.stream()
                .collect(Collectors.groupingBy(SalesOrder::getRegion, Collectors.summingDouble(this::amountValue)));

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(period).append(" 销售数据汇总】\n\n");
        List<String> filters = new ArrayList<>();
        if (region != null) filters.add("地区: " + region);
        if (product != null) filters.add("产品: " + product);
        if (salesPerson != null) filters.add("销售: " + salesPerson);
        if (!filters.isEmpty()) sb.append("筛选条件: ").append(String.join("，", filters)).append("\n\n");
        if (orderCount == 0) {
            sb.append("暂无销售数据");
            return sb.toString().trim();
        }
        sb.append(String.format("总销售额: ¥%.2f 万\n", totalAmount));
        sb.append(String.format("成交订单: %d 笔\n", orderCount));
        sb.append(String.format("平均单价: ¥%.2f 万\n", avgAmount));
        if (product == null && !byProduct.isEmpty()) {
            sb.append("\n【按产品分布】\n");
            byProduct.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .forEach(e -> sb.append(String.format("  %s: ¥%.2f 万 (%.1f%%)\n",
                            e.getKey(), e.getValue(), e.getValue() / totalAmount * 100)));
        }
        if (region == null && !byRegion.isEmpty()) {
            sb.append("\n【按地区分布】\n");
            byRegion.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .forEach(e -> sb.append(String.format("  %s: ¥%.2f 万 (%.1f%%)\n",
                            e.getKey(), e.getValue(), e.getValue() / totalAmount * 100)));
        }
        return sb.toString().trim();
    }

    private String buildRankingResult(List<SalesOrder> data, String region, String period, int limit) {
        Map<String, Double> bySales = data.stream()
                .collect(Collectors.groupingBy(SalesOrder::getSalesPerson, Collectors.summingDouble(this::amountValue)));
        List<Map.Entry<String, Double>> ranking = bySales.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue())).limit(limit).toList();
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(period);
        if (region != null) sb.append(" ").append(region);
        sb.append(" 销售排名】\n\n");
        if (ranking.isEmpty()) {
            sb.append("暂无销售数据");
        } else {
            for (int i = 0; i < ranking.size(); i++) {
                Map.Entry<String, Double> entry = ranking.get(i);
                sb.append(String.format("第%d名: %s - ¥%.2f 万\n", i + 1, entry.getKey(), entry.getValue()));
            }
        }
        return sb.toString().trim();
    }

    private String buildDetailResult(List<SalesOrder> data, String region, String period, int limit) {
        List<SalesOrder> topRecords = data.stream()
                .sorted((a, b) -> Double.compare(amountValue(b), amountValue(a)))
                .limit(limit)
                .toList();
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(period);
        if (region != null) sb.append(" ").append(region);
        sb.append(" 销售明细】\n\n");
        sb.append(String.format("共 %d 条记录，显示金额最高的 %d 条：\n\n", data.size(), topRecords.size()));
        for (int i = 0; i < topRecords.size(); i++) {
            SalesOrder r = topRecords.get(i);
            sb.append(String.format("%d. %s\n", i + 1, r.getCustomer()));
            sb.append(String.format("   产品: %s | 金额: ¥%.2f 万\n", r.getProduct(), amountValue(r)));
            sb.append(String.format("   销售: %s | 地区: %s | 日期: %s\n\n",
                    r.getSalesPerson(), r.getRegion(), r.getOrderDate()));
        }
        return sb.toString().trim();
    }

    private String buildTrendResult(List<SalesOrder> data, String region, String period) {
        Map<String, Double> byWeek = data.stream().collect(Collectors.groupingBy(
                r -> "第" + ((r.getOrderDate().getDayOfMonth() - 1) / 7 + 1) + "周",
                Collectors.summingDouble(this::amountValue)));
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(period);
        if (region != null) sb.append(" ").append(region);
        sb.append(" 销售趋势】\n\n");
        if (byWeek.isEmpty()) {
            sb.append("暂无数据");
        } else {
            byWeek.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e ->
                    sb.append(String.format("%s: ¥%.2f 万\n", e.getKey(), e.getValue())));
        }
        return sb.toString().trim();
    }

    private double amountValue(SalesOrder order) {
        BigDecimal amount = order.getAmount();
        return amount == null ? 0D : amount.doubleValue();
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

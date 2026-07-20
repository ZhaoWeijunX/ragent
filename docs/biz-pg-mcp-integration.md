# 业务 PG MCP 集成说明

将 `sales_query`、`ticket_query` 从内存 Mock 切换为查询 ragent 本库业务表。

## 改造范围

| 项目 | 改造前 | 改造后 |
|------|--------|--------|
| 数据来源 | Executor 内 Random 生成 | PostgreSQL `t_biz_sales_order` / `t_biz_support_ticket` |
| 工具 ID | `sales_query` / `ticket_query` | 不变 |
| bootstrap | MCP Client + 意图树 | **无改动** |

## 初始化数据库

```bash
psql -h localhost -U postgres -d ragent -f resources/database/biz_mcp_schema.sql
psql -h localhost -U postgres -d ragent -f resources/database/biz_mcp_seed.sql
psql -h localhost -U postgres -d ragent -f docs/examples/intent-node-import/mcp-intent-nodes-import.sql
psql -h localhost -U postgres -d ragent -f docs/examples/intent-node-import/mcp-intent-nodes-biz-prompt-update.sql
```

意图节点提示词源文件见 `docs/examples/prompt/sales-mcp-*.st`、`ticket-mcp-*.st`。

## 配置

`mcp-server` 与 bootstrap 共用 `ragent` 库，默认连接：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${ragent.infra.host:localhost}:5432/ragent?client_encoding=UTF8
    username: postgres
    password: postgres
```

数据源默认连本机 `ragent`（可用 `RAGENT_INFRA_HOST` 覆盖主机）。密钥（天气 / You.com）使用仓库根目录 `.env`。

## 验证

1. 启动 mcp-server（9099），确认日志注册 `sales_query`、`ticket_query`
2. 启动 bootstrap，确认 `rag.mcp.servers` 指向 mcp-server
3. MCP 工具查库时，mcp-server 控制台会输出 `[MCP-SQL]` 日志，可直接复制到 psql 执行
4. 示例问题：
   - 「华东本月销售额是多少」
   - 「腾讯科技有哪些紧急工单」
   - 「本月工单解决率」

可选：开启 `app.eval.enabled=true` 后访问 `/rag/eval?question=...` 仅测检索链路。

## 新增文件

- `resources/database/biz_mcp_schema.sql`
- `resources/database/biz_mcp_seed.sql`
- `mcp-server/.../dao/SalesOrderDao.java`
- `mcp-server/.../dao/SupportTicketDao.java`
- `mcp-server/.../model/SalesOrder.java`
- `mcp-server/.../model/SupportTicket.java`
- `mcp-server/.../support/PeriodDateRange.java`

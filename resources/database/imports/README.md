# 数据导入说明

本目录保存可选的演示数据、意图树和摄取流水线脚本。执行前先确认目标库为 `ragent`，并按脚本中的占位符、前置条件和固定 ID 说明操作；同一类节点不要重复导入。

## 目录结构

```text
imports/
├── intent-nodes/              # 系统与知识库意图节点
├── mcp-business/              # sales / ticket 的示例库与 MCP 意图
├── mcp-weather/               # weather MCP 意图与提示词
├── mcp-youcom/                # You.com MCP 意图与提示词
├── pipelines/                 # 摄取流水线
└── sample-questions/          # 欢迎页示例问题
```

## 1. 系统与知识库意图节点

先执行系统交互节点；再按实际使用的知识库选择导入。知识库脚本中的 `__KB_ID_*__`、`__COLLECTION_*__` 必须替换为目标知识库的真实值。

| 脚本 | 内容 |
|---|---|
| [`intent-nodes/system-intent-nodes-import.sql`](intent-nodes/system-intent-nodes-import.sql) | 全局 `sys`、欢迎、助手介绍和反馈节点 |
| [`intent-nodes/12306-intent-nodes-import.sql`](intent-nodes/12306-intent-nodes-import.sql) | 12306 知识库意图节点 |
| [`intent-nodes/biz-security-intent-nodes-import.sql`](intent-nodes/biz-security-intent-nodes-import.sql) | biz-security 知识库意图节点 |
| [`intent-nodes/onboarding-intent-nodes-import.sql`](intent-nodes/onboarding-intent-nodes-import.sql) | onboarding 知识库意图节点 |
| [`intent-nodes/ragent-test-intent-nodes-import.sql`](intent-nodes/ragent-test-intent-nodes-import.sql) | ragent-test 知识库意图节点 |

对应素材位于 [`resources/knowledge-samples/`](../../knowledge-samples/README.md)。各知识库目录中的 `intent-tree-design.md` 说明了节点与样本内容的对应关系。

## 2. MCP 意图节点

每个 MCP 脚本均已合并节点插入与该 MCP 专属提示词，可一次执行；不再需要按“节点脚本 + Prompt 更新脚本”的两步方式执行。执行后重启 bootstrap 或刷新意图树缓存。

| 目录 / 脚本 | 前置条件 | 内容 |
|---|---|---|
| [`mcp-business/schema.sql`](mcp-business/schema.sql)、[`seed.sql`](mcp-business/seed.sql) | 仅业务 PG MCP 示例需要 | 销售与工单示例表、示例数据 |
| [`mcp-business/intent-nodes.sql`](mcp-business/intent-nodes.sql) | 已注册 `sales_query`、`ticket_query` | sales / ticket 节点及参数提取、回答提示词 |
| [`mcp-weather/intent-nodes.sql`](mcp-weather/intent-nodes.sql) | 已注册 `weather_query` | weather 节点及天气参数提取提示词 |
| [`mcp-youcom/intent-nodes.sql`](mcp-youcom/intent-nodes.sql) | 已配置 You.com Key 且已注册 `youcom_search` | You.com 节点及联网搜索参数提取提示词 |

业务 PG MCP 的 schema 与 seed 应在 `mcp-business/intent-nodes.sql` 之前执行。天气和 You.com 与业务示例数据无依赖，仅在对应 MCP 工具可用时导入。

## 3. 摄取流水线

| 脚本 | 用途 |
|---|---|
| [`pipelines/feishu-pdf-ingestion-pipeline.sql`](pipelines/feishu-pdf-ingestion-pipeline.sql) | 飞书 PDF：fetcher → parser → enhancer → chunker → indexer |
| [`pipelines/feishu-markdown-ingestion-pipeline.sql`](pipelines/feishu-markdown-ingestion-pipeline.sql) | 飞书 Markdown：fetcher → parser → chunker → indexer |

飞书配置、数据库升级与单页 / 批量导入见 [`docs/integrations/feishu/wiki.md`](../../../docs/integrations/feishu/wiki.md)。已有库开启批量 Wiki 导入前，需要先执行 [`260630_feishu_wiki_import.sql`](../upgrades/v1.1.0/260630_feishu_wiki_import.sql)。

## 4. 欢迎页示例问题

[`sample-questions/sample-questions-import.sql`](sample-questions/sample-questions-import.sql) 写入首页“试试这些开场”。应在相应知识库、意图树或 MCP 节点已准备完成后执行。

## 常用执行顺序

```bash
# 1. 全局系统节点（推荐）
psql -h localhost -U postgres -d ragent \
  -f resources/database/imports/intent-nodes/system-intent-nodes-import.sql

# 2. 按需选择一个或多个知识库意图脚本
psql -h localhost -U postgres -d ragent \
  -f resources/database/imports/intent-nodes/12306-intent-nodes-import.sql

# 3. 按需导入 MCP；业务 PG MCP 先建示例表和数据
psql -h localhost -U postgres -d ragent \
  -f resources/database/imports/mcp-business/schema.sql
psql -h localhost -U postgres -d ragent \
  -f resources/database/imports/mcp-business/seed.sql
psql -h localhost -U postgres -d ragent \
  -f resources/database/imports/mcp-business/intent-nodes.sql

# 4. 可选：摄取流水线和欢迎页示例
psql -h localhost -U postgres -d ragent \
  -f resources/database/imports/pipelines/feishu-pdf-ingestion-pipeline.sql
psql -h localhost -U postgres -d ragent \
  -f resources/database/imports/sample-questions/sample-questions-import.sql
```

脚本写入后，请根据实际部署刷新服务缓存，并用目标知识库或 MCP 请求验证节点可被命中。


# 数据导入指南

> 表结构初始化见同目录 [`README.md`](README.md)（`schema_pg.sql` / `init_data_pg.sql` / 增量升级）。  
> **本文件不替代 README**，只描述演示数据、意图树、示例问题与摄取流水线的导入。

本地一键环境总览也可参考 [`docs/local-env-setup.md`](../../docs/local-env-setup.md)。

---

## 目录结构

```text
resources/database/
├── README.md                 # 全量初始化 / 增量升级（勿改职责）
├── DATA_IMPORT.md            # 本文件
├── schema_pg.sql
├── init_data_pg.sql
├── biz_mcp_schema.sql
├── biz_mcp_seed.sql
└── imports/
    ├── intent-nodes/         # 意图树与 MCP Prompt 回填
    ├── sample-questions/     # 欢迎页示例问题
    └── pipelines/            # 摄取流水线种子数据
```

API 请求体示例仍保留在 `docs/examples/`（如 `pdf-pipeline-request.json`），与 SQL 种子内容对齐。

---

## 推荐导入顺序

```text
【全新最小可跑】
  schema_pg.sql
  → init_data_pg.sql

【MCP 业务演示】
  → biz_mcp_schema.sql
  → biz_mcp_seed.sql
  → imports/intent-nodes/mcp-intent-nodes-import.sql
  → imports/intent-nodes/mcp-intent-nodes-weather-prompt-update.sql
  → imports/intent-nodes/mcp-intent-nodes-biz-prompt-update.sql
  →（可选）mcp-intent-nodes-youcom-import.sql
      + mcp-intent-nodes-youcom-prompt-update.sql

【摄取流水线】
  → imports/pipelines/feishu-pdf-ingestion-pipeline.sql
  → imports/pipelines/feishu-markdown-ingestion-pipeline.sql

【示例知识库意图 / 欢迎页】
  → 管理台创建知识库并上传文档、完成分块
  → 替换占位符后执行对应 *-intent-nodes-import.sql
  → imports/sample-questions/sample-questions-import.sql
```

意图节点导入后若管理台仍显示旧树：

```bash
docker exec -it redis redis-cli -a 123456 DEL ragent:intent:tree
```

---

## 1. MCP 业务表与模拟数据

| 脚本 | 作用 |
|------|------|
| `biz_mcp_schema.sql` | `t_biz_sales_order` / `t_biz_support_ticket` |
| `biz_mcp_seed.sql` | 可重复执行的模拟订单与工单 |

```bash
psql "postgresql://postgres:postgres@localhost:5432/ragent" -f resources/database/biz_mcp_schema.sql
psql "postgresql://postgres:postgres@localhost:5432/ragent" -f resources/database/biz_mcp_seed.sql
```

---

## 2. 意图树（`imports/intent-nodes/`）

| 脚本 | 说明 |
|------|------|
| `mcp-intent-nodes-import.sql` | MCP 意图（销售 / 工单 / 天气）+ 系统交互节点 `sys*` |
| `mcp-intent-nodes-weather-prompt-update.sql` | 天气提参 Prompt |
| `mcp-intent-nodes-biz-prompt-update.sql` | 销售 / 工单提参 Prompt |
| `mcp-intent-nodes-youcom-import.sql` | You.com 联网搜索意图（可选） |
| `mcp-intent-nodes-youcom-prompt-update.sql` | You.com 提参 Prompt（可选） |
| `onboarding-intent-nodes-import.sql` | 入职知识库意图树（7 篇文档） |
| `biz-security-intent-nodes-import.sql` | 信息安全知识库意图树（7 篇文档） |
| `ragent-test-intent-nodes-import.sql` | 技术专栏意图树（53 篇文档） |
| `12306-intent-nodes-import.sql` | 12306 实战专栏意图树（按正文聚类；87 篇有效 PDF） |

知识库意图脚本使用占位符，执行前全文替换：

| 脚本 | 占位符 |
|------|--------|
| onboarding | `__KB_ID_ONBOARDING__` / `__COLLECTION_ONBOARDING__` |
| biz-security | `__KB_ID_BIZ_SECURITY__` / `__COLLECTION_BIZ_SECURITY__` |
| ragent-test | `__KB_ID_RAGENT_TEST__` / `__COLLECTION_RAGENT_TEST__` |
| 12306 | `__KB_ID_12306__` / `__COLLECTION_12306__` |

```sql
SELECT id, name, collection_name FROM t_knowledge_base;
```

文档目录与设计说明：

| 知识库 | 文档目录 | 设计文档 |
|--------|----------|----------|
| onboarding | `resources/docs/knowledge/group/onboarding/` | 同目录 `intent-tree-design.md` |
| biz-security | `resources/docs/knowledge/biz/biz-security/` | 同目录 `intent-tree-design.md` |
| ragent-test | `resources/docs/ragent-test/` | 同目录 `intent-tree-design.md` |
| 12306 | `resources/docs/12306-pdf-doc/` | 同目录 `intent-tree-design.md` |

### 与文档内容对齐说明（校验结论）

- **onboarding / biz-security**：叶子意图与目录下 7 个内容文件一一对应（此前注释误写「8 篇」，已更正）。
- **ragent-test**：已覆盖 53 篇 Markdown；技术文档系列含 `tech-docs-threadpool`（线程池）与 `tech-docs-patterns`（设计模式全景）。
- **12306**：按 PDF **正文内容**聚类为 17 个 TOPIC（非原目录结构）；89 篇中有 **2 篇近似空文档**不纳入叶子映射，详见 `resources/docs/12306-pdf-doc/intent-tree-design.md` §0。
- **sample-questions**：Demo 区 OA / 保险问法已对齐现有「数据安全规范」文档（非功能介绍 / 整体架构文档）；含 12306 开场示例。

---

## 3. 欢迎页示例问题（`imports/sample-questions/`）

| 脚本 | 作用 |
|------|------|
| `sample-questions-import.sql` | 写入 `t_sample_question`，首页「试试这些开场」 |

建议在相关意图树与知识库文档就绪后执行。

---

## 4. 摄取流水线（`imports/pipelines/`）

| 脚本 | 对应 JSON 示例 | 节点链 |
|------|----------------|--------|
| `feishu-pdf-ingestion-pipeline.sql` | `docs/examples/pdf-pipeline-request.json` | fetcher → parser(PDF) → enhancer → chunker → indexer |
| `feishu-markdown-ingestion-pipeline.sql` | `docs/examples/feishu-pipeline-request.json` | fetcher → parser(MARKDOWN/TEXT) → chunker → indexer |

脚本可重复执行（先删固定 id 再插）。也可用管理台 / API 创建，参见：

- [`docs/examples/pdf-ingestion-example.md`](../../docs/examples/pdf-ingestion-example.md)
- [`docs/examples/feishu-wiki-ingestion-example.md`](../../docs/examples/feishu-wiki-ingestion-example.md)

```bash
psql "postgresql://postgres:postgres@localhost:5432/ragent" \
  -f resources/database/imports/pipelines/feishu-pdf-ingestion-pipeline.sql
psql "postgresql://postgres:postgres@localhost:5432/ragent" \
  -f resources/database/imports/pipelines/feishu-markdown-ingestion-pipeline.sql
```

---

## 5. 非 SQL 的导入入口

| 能力 | 说明 |
|------|------|
| 知识库文档上传 | 管理台上传 `resources/docs/**` 素材后分块 |
| 飞书 Wiki 批量导入 | 管理台 / API，见 `docs/examples/feishu-wiki-batch-import-example.md` |
| 运维清理 / 迁移 | `scripts/`，见 [`scripts/README.md`](../../scripts/README.md) |

---

## 执行示例（Docker Postgres）

```bash
docker exec -i postgres psql -U postgres -d ragent < resources/database/biz_mcp_schema.sql
docker exec -i postgres psql -U postgres -d ragent < resources/database/biz_mcp_seed.sql
docker exec -i postgres psql -U postgres -d ragent < resources/database/imports/intent-nodes/mcp-intent-nodes-import.sql
docker exec -i postgres psql -U postgres -d ragent < resources/database/imports/intent-nodes/mcp-intent-nodes-weather-prompt-update.sql
docker exec -i postgres psql -U postgres -d ragent < resources/database/imports/intent-nodes/mcp-intent-nodes-biz-prompt-update.sql
docker exec -i postgres psql -U postgres -d ragent < resources/database/imports/pipelines/feishu-pdf-ingestion-pipeline.sql
docker exec -i postgres psql -U postgres -d ragent < resources/database/imports/pipelines/feishu-markdown-ingestion-pipeline.sql
```

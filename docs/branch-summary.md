# 分支总结

> 生成时间：2026-07-20  
> 基准分支：`main` / `origin/main`（`6e9a584`）  
> 集成预览分支：`preview` / `origin/preview`（`8330aac`，与本地同步）  
> 上游参考：`upstream/main`（`bb90353`）

本文档汇总 **origin 远程仓库** 各分支相对 `main` 的增量工作，便于了解进展、合并顺序与分支归档。

---

## 最近更新（2026-07-09 ～ 07-20）

按时间从新到旧，仅列 `preview` 相对 `main` 的主要非 merge 增量：

| 提交 | 说明 |
|------|------|
| `8330aac` | 本地密钥统一到仓库根目录 `.env`，移除 `application-local.yaml` |
| `0a9250f` | 存储 URL 迁移脚本（`scripts/migrate_storage_s3_urls.py`） |
| `91683ea` / `8befba2` | You.com MCP 搜索节点 + 调试日志；Web Search Key 配置说明 |
| `9d23ba3` / `dda1152` / `478eb90` | 前端：消息重新生成、KaTeX 公式渲染、行内代码样式 |
| `e56aa5b` | 设计模式文档、本地环境搭建、`ragent-infra` Compose |
| `9c90cdd` | 模型健康主动探测（`ModelProbeService` / `/admin/models/probe`） |
| `3e32959` | 业务 PG MCP：`sales_query` / `ticket_query` 由 Mock 切真实库表 |
| `0a1655f` / `59cafe0` / `3323687` / `e85bd4e` | 示例知识库与意图树（ragent-test 问答篇、biz-security、onboarding、示例问题 SQL） |
| `9368734` / `f042bb1` | 飞书默认 PDF 导出 + 轮询与 pdf→md→plain 降级链 |
| （同期经 merge 吸收 upstream） | 图谱检索通道、向量通道合并、检索配置校验、存储抽象重构、You.com 联网检索通道等 |

同期 `main` 多次 merge `upstream/main`，并保留对个人功能的 revert（见下文）。`preview` 通过直接 merge `upstream/main` 跟上上游能力，**未**合入 `main` 上那组 revert。

---

## 分支策略

| 分支 | 角色 |
|------|------|
| `main` | **干净基线**：对齐上游 fork；个人实验功能通过 revert 从工作区移除，代码在 `preview` 可回溯 |
| `preview` | **个人功能集成预览**：汇集飞书 / MCP / 示例库 / 前端增强等，并定期 merge `upstream/main` |

## 分支关系概览

```
upstream/main（上游主线）
 └── origin/main（干净基线；含个人 revert，领先 upstream 约 20 个提交）
      └──（开发不在此做功能）

origin/preview（个人集成预览；领先 main 48 / 落后 main 10）
 ├── [已合入] 飞书：single_wiki → batch_wiki → markdown → PDF 默认
 ├── [已合入] DeepSeek、天气 MCP、业务 PG MCP、模型健康探测
 ├── [已合入] You.com MCP 节点、示例知识库与意图树、前端 KaTeX / 重生成
 ├── [已合入] 根目录 .env 配置、存储 URL 迁移脚本
 └── [定期] merge upstream/main（图谱检索、检索通道重构等）

中间 / 备份线（功能多已在 preview，可归档）：
 origin/feat/feishu_source/{single_wiki,batch_wiki,markdown_support,pdf_support}
 origin/feat/mcp/{weather,pg_query}
```

## 远程分支一览（origin）

相对 `origin/main` 的 ahead/behind（`main 独有 / 分支独有`）：

| 远程分支 | 落后 main | 领先 main | 最新提交 | 状态 |
|----------|-----------|-----------|----------|------|
| `origin/main` | — | — | `6e9a584` | 稳定基线 |
| `origin/preview` | 10 | 48 | `8330aac` | **个人功能集成预览** |
| `origin/feat/feishu_source/single_wiki` | 43 | 10 | `4989755` | 中间分支，可归档 |
| `origin/feat/feishu_source/batch_wiki` | 43 | 15 | `84b7925` | 中间分支，可归档 |
| `origin/feat/feishu_source/markdown_support` | 36 | 21 | `26084e2` | 已被 preview 包含 |
| `origin/feat/feishu_source/pdf_support` | 36 | 27 | `9368734` | 已被 preview 包含（祖先） |
| `origin/feat/mcp/weather` | 43 | 2 | `72d7b78` | 天气备份；tip 非 preview 祖先，可归档 |
| `origin/feat/mcp/pg_query` | 33 | 41 | `9d23ba3` | 业务 PG 线已合入 preview；tip 已漂到后续前端提交 |

> **上游**：`upstream/main`（`bb90353`）。`origin/main` 领先 upstream 约 20 个提交（merge 与 revert 历史），落后 0。`upstream/1.0.x` 为旧版维护线。

> **命名**：原 `my_preview` 已统一为 `preview` / `origin/preview`。

---

## origin/main

**定位**：干净基线，与上游 fork 基本对齐。

**工作区能力**（含上游已合入部分，不限于）：

- MinerU 并发、Chunk 打包、共享向量/关键词索引、知识库异步清理
- 向量检索 / ES 关键词检索、跨 Collection 召回
- 图谱检索（LightRAG 等，随 upstream 进入）
- 检索通道精简与配置校验、You.com 联网检索通道（upstream #49）等

**已通过 revert 从工作区移除**（历史仍在，完整代码在 `preview`）：

| Revert 提交 | 原功能 |
|-------------|--------|
| `90f9b95` | 天气 MCP（QWeather） |
| `c8ced87` | 意图树文档 |
| `bc302ba` | `infra-ai` 注释整理 |
| `0ec5c90` | `branch-summary.md` |

**相对 preview 多出的 10 个提交**：上述 revert、若干 `merge upstream/main`，以及 `1d73fa5`（web-search Key 占位说明，preview 侧有对应 `8befba2`）。merge `main` → `preview` 时需注意 revert 冲突，**保留 preview 侧功能代码**。

---

## origin/preview

**定位**：个人最新集成预览分支。跟踪分支：本地 `preview` ↔ `origin/preview`（当前一致）。

**相对 `main` 额外包含**（个人功能为主；上游能力两边经不同路径都可能已有）：

| 模块 | 内容 |
|------|------|
| 飞书功能线 | 单页 / 批量 Wiki 导入、Remote URL、处理模式 chunk/pipeline、ZIP 批量下载、**PDF 默认导出** + 轮询与降级链 |
| DeepSeek | `DeepSeekChatClient`、`ModelProvider`、配置项 |
| 天气 MCP | `QWeatherClient`、`WeatherMcpExecutor`（`main` 已 revert） |
| 业务 PG MCP | `sales_query` / `ticket_query` 查 `ragent` 业务表 |
| You.com | MCP 搜索节点；与 bootstrap 联网检索通道配合 |
| 模型健康 | 主动探测 API（手动触发，无定时任务） |
| 示例知识库 | biz-security、onboarding、ragent-test（含 AI 知识问答篇）+ 意图树 / 示例问题 SQL |
| 前端 | KaTeX、消息重新生成、行内代码样式 |
| 配置 | 根目录 `.env` / `RAGENT_INFRA_HOST` 等；已移除 `application-local.yaml` |
| 运维脚本 | 存储 URL 迁移（`scripts/migrate_storage_s3_urls.py`） |
| 文档 | `feishu-wiki-integration.md`、`local-env-setup.md`、`weather-mcp-integration.md`、本文件等 |

**飞书功能线演进**：

| 阶段 | 能力 |
|------|------|
| `single_wiki` | Wiki 单页 Ingestion、Remote URL |
| `batch_wiki` | 空间 / 子树 / 单页批量、MQ、前后端 UI |
| `markdown_support` | Markdown 导出、ZIP 下载 |
| **PDF 默认（当前）** | `content-format: pdf`、`fallback-on-error`、`FeishuExportPollingExecutor` |

详见 [`docs/feishu-wiki-integration.md`](feishu-wiki-integration.md)。

**变更规模（相对 `main`，`origin/main...HEAD`）**：约 216 个文件，+46000 / -900 行量级（含大量示例文档）。

**同步状态**：领先 `main` 48 个提交，落后 10 个（主要是 `main` 上的 revert 与 merge 记录）。

---

## origin/feat/feishu_source/single_wiki

**定位**：飞书 Wiki **单文档**导入的最小可用实现。

| 模块 | 内容 |
|------|------|
| Fetcher / Pipeline | 飞书 Wiki 单页 URL 解析与拉取 |
| 知识库 Remote URL | 远程 URL 导入飞书文档 |
| 配置 | 飞书凭证、基础设施 host 外置（现已演进为 `.env`） |
| 基础设施 | RocketMQ Compose 兼容修复 |

**状态**：已被 `preview` 包含，可归档。

---

## origin/feat/feishu_source/batch_wiki

**定位**：在 `single_wiki` 上扩展**批量导入**与前后端闭环。

| 模块 | 内容 |
|------|------|
| 批量导入 | 空间 / 子树 / 单页发现与导入 |
| 任务调度 | Import Job / Item、MQ、导入服务 |
| API / 前端 | Controller + `FeishuWikiImportDialog`、chunk/pipeline 模式 |
| 数据库 | `upgrade_v1.2_to_v1.2.1_feishu.sql` |

**状态**：已被 `preview` 包含，可归档。

---

## origin/feat/feishu_source/markdown_support

**定位**：Markdown 导出与知识库批量 ZIP 下载；后续在 preview 演进为 PDF 默认。

**状态**：已合入 `preview`；PDF 改造见 `pdf_support` / preview。

---

## origin/feat/feishu_source/pdf_support

**定位**：飞书导出改为默认 PDF + MinerU，含轮询与级联降级。

| 提交 | 说明 |
|------|------|
| `f042bb1` | 默认 PDF、可配置 fallback |
| `9368734` | 导出轮询、级联降级 |

**状态**：已是 `preview` 祖先，可归档。

---

## origin/feat/mcp/weather

**定位**：天气 MCP 独立备份线。

| 模块 | 内容 |
|------|------|
| API | 和风天气 `QWeatherClient` |
| MCP | `WeatherMcpExecutor` |
| 文档 | `docs/weather-mcp-integration.md` |

**状态**：`main` 已 revert；完整代码在 `preview`。当前 tip 不是 preview 祖先，确认无独有提交后可删。

---

## origin/feat/mcp/pg_query

**定位**：业务 PG 查询 MCP（销售 / 工单）及同期合入的文档 / 前端增量线。

| 模块 | 内容 |
|------|------|
| MCP | `sales_query` / `ticket_query` 切 PostgreSQL |
| 文档 / 其它 | 示例知识库、模型健康探测、本地环境文档等（后续提交叠在同一 tip 上） |

**状态**：功能已在 `preview`；分支 tip（`9d23ba3`）与 preview 历史重叠，可归档。

---

## 已归档分支

| 分支 | 归档时间 | 功能去向 | 说明 |
|------|----------|----------|------|
| `origin/feat/intent` | 2026-07-07 | `preview` | 意图树设计文档与导入 SQL；`main` 已 revert |
| `origin/feat/model/add_model` | 2026-07-07 | `preview` | DeepSeek 对话模型接入 |
| `origin/my_preview` | 2026-07-07 | 更名为 `origin/preview` | 集成预览分支重命名 |

---

## 合并建议

1. **日常开发**：在 `preview` 上进行；`main` 只同步上游并保持干净基线。
2. **同步上游**：优先在 `preview` 上 `merge upstream/main`（当前做法）；若先更新 `main` 再合入 `preview`，注意 revert 冲突，保留 preview 功能。
3. **密钥与中间件**：使用仓库根目录 `.env`（见 `.env.example`），启动前用 IDEA EnvFile 或 `scripts/export-dotenv.ps1` 注入；`RAGENT_INFRA_HOST` 覆盖中间件主机。
4. **飞书 PDF**：`content-format: pdf` 需 `docs:document:export` 与 `MINERU_API_KEY`；详见 [`feishu-wiki-integration.md`](feishu-wiki-integration.md) §7.6。
5. **示例知识库**：按各 `intent-tree-design.md` 上传 → 分块 → 执行 `docs/examples/intent-node-import/*-intent-nodes-import.sql` → `redis-cli DEL ragent:intent:tree`。
6. **可归档**：`single_wiki`、`batch_wiki`、`markdown_support`、`pdf_support`、`mcp/weather`、`mcp/pg_query` 在确认 `preview` 稳定后可删远程分支。
7. **已归档**：`intent`、`add_model`、`my_preview`（已更名）无需再操作。

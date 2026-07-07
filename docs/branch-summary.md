# 分支总结

> 生成时间：2026-07-07（晚间更新）  
> 基准分支：`main` / `origin/main`（`0ec5c90`）  
> 集成预览分支：`preview` / `origin/preview`（`6670919`）

本文档汇总 **origin 远程仓库** 各分支相对 `main` 的增量工作，便于了解进展、合并顺序与分支归档。

## 分支策略

| 分支 | 角色 |
|------|------|
| `main` | **干净基线**，对齐上游 fork；个人实验功能通过 revert 从工作区移除，仅保留上游核心能力 |
| `preview` | **个人功能集成预览**，汇集所有新开发功能，用于本地验证与后期回溯代码 |

## 分支关系概览

```
origin/main（干净基线，对齐 upstream/main）
 └── origin/preview（个人功能集成预览）
      ├── [已合入] 飞书功能线（single_wiki → batch_wiki → markdown_support → PDF 默认）
      ├── [已合入] DeepSeek 模型接入（原 add_model）
      ├── 天气 MCP、意图树文档、infra-ai 整理等（main 已 revert，preview 保留）
      └── [已删除] origin/feat/intent、origin/feat/model/add_model

origin/feat/feishu_source/single_wiki → batch_wiki → markdown_support  （中间分支，可归档）
origin/feat/mcp/weather                                                 （天气备份线，可归档）
```

## 远程分支一览（origin）

| 远程分支 | 领先 `main` | 落后 `main` | 最新提交 | 状态 |
|----------|-------------|-------------|----------|------|
| `origin/main` | — | — | `0ec5c90` | 稳定基线 |
| `origin/preview` | 23 | 5 | `6670919` | **个人功能集成预览** |
| `origin/feat/feishu_source/single_wiki` | 10 | 12 | `4989755` | 中间分支，可归档 |
| `origin/feat/feishu_source/batch_wiki` | 15 | 12 | `84b7925` | 中间分支，可归档 |
| `origin/feat/feishu_source/markdown_support` | 21 | 5 | `26084e2` | 已被 `preview` 包含 |
| `origin/feat/mcp/weather` | 2 | 12 | `72d7b78` | 天气备份，可归档 |

> **上游参考**：`upstream/main`（`50afe91`）；本地 `main` 领先 `upstream/main` 15 个提交、落后 0（含 merge 与个人配置历史，工作区已 revert 个人功能）。`upstream/1.0.x` 为旧版维护线。

> **命名变更**：原 `my_preview` / `origin/my_preview` 已统一更名为 `preview` / `origin/preview`。

---

## origin/main

**定位**：干净基线，与上游 fork 基本对齐。

**工作区包含**（上游核心能力）：

- MinerU 解析并发：分布式信号量控制
- Chunk 合并：文本流与图片处理优化；块级贪心打包
- 知识库索引：Milvus 向量与关键词索引统一为共享物理索引
- 知识库删除时底层资源异步清理
- 向量检索重构，支持全局跨 Collection 召回
- Elasticsearch 关键词检索与索引

**已通过 revert 从工作区移除**（历史记录仍保留，代码在 `preview` 可回溯）：

| Revert 提交 | 原功能 |
|-------------|--------|
| `90f9b95` | 天气 MCP（QWeather） |
| `c8ced87` | 意图树文档 |
| `bc302ba` | `infra-ai` 注释整理 |
| `0ec5c90` | `branch-summary.md` |

---

## origin/preview

**定位**：个人**最新集成预览分支**（原 `my_preview`），汇集所有新开发功能。

**相对 `main` 额外包含**：

| 模块 | 内容 |
|------|------|
| 飞书功能线 | 单文档 / 批量导入、**PDF 默认导出**（MinerU 解析）、Markdown/plain 兼容、**pdf→md→plain 降级链**、`FeishuExportPollingExecutor` 共享轮询、知识库 Remote URL + Wiki 批量导入前后端闭环 |
| DeepSeek | `DeepSeekChatClient`、`ModelProvider` 扩展、配置项 |
| 天气 MCP | `QWeatherClient`、`WeatherMcpExecutor` 等（`main` 已 revert） |
| 意图树 | 设计文档与导入 SQL（`main` 已 revert） |
| 基础设施 | `infra-ai` 注释与结构整理（`main` 已 revert） |
| 文档 | `feishu-wiki-integration.md`、`docs/branch-summary.md`、`pdf-ingestion-example.md` 等 |

**飞书功能线演进（preview 工作区，含本地未推送增量）**：

| 阶段 | 能力 |
|------|------|
| `single_wiki` | Wiki 单页 Ingestion、Remote URL 自动识别 |
| `batch_wiki` | 整库/子树批量导入、MQ 异步、前后端 UI |
| `markdown_support` | Markdown 导出、ZIP 批量下载 |
| **PDF 默认（当前）** | `export_tasks` 异步 PDF、`content-format: pdf`、`fallback-on-error`、PDF 下载大小限制、`FeishuExportPollingExecutor` |

详细设计见 [`docs/feishu-wiki-integration.md`](feishu-wiki-integration.md)。

**变更规模**：约 70+ 个文件，+5800 行量级（相对 `main`，含 PDF 改造增量）。

**同步状态**：领先 `main` 23 个提交，落后 5 个（`main` 上的 revert 提交及 `a593718` 文档版本，merge 时可能产生冲突）。

**本地分支**：`preview`，跟踪 `origin/preview`。

---

## origin/feat/feishu_source/single_wiki

**定位**：飞书 Wiki **单文档**导入的最小可用实现。

| 模块 | 内容 |
|------|------|
| 数据通道（Fetcher） | `FeishuFetcher` 支持飞书 Wiki 单页 URL 解析与拉取 |
| 知识库 Remote URL | 通过远程 URL 导入飞书文档 |
| 流水线任务 | 流水线 Fetcher 节点支持飞书 Wiki 单文档 |
| 配置 | 飞书凭证、`application-local` 本地配置、基础设施 host 外置 |
| 基础设施 | 修复 RocketMQ Docker Compose 版本兼容问题 |

**变更规模**：约 20 个文件，+1560 / -65 行（相对 `main`）。

**状态**：已被 `preview` 包含，可归档。

---

## origin/feat/feishu_source/batch_wiki

**定位**：在 `single_wiki` 之上扩展**批量导入**与完整前后端闭环。

| 模块 | 内容 |
|------|------|
| 批量导入 | 支持按 Wiki 空间、子树、单页三种范围批量发现并导入 |
| 任务调度 | `FeishuWikiImportJob` / `Item` 实体、MQ 消费者、导入服务 |
| API | `FeishuWikiImportController`（发现、提交、查询任务） |
| 前端 | `FeishuWikiImportDialog`、文档处理模式选择（chunk / pipeline） |
| 数据库 | `upgrade_v1.2_to_v1.2.1_feishu.sql` 升级脚本 |

**变更规模**：约 63 个文件，+5111 / -177 行（相对 `main`）。

**状态**：已被 `preview` 包含，可归档。

---

## origin/feat/feishu_source/markdown_support

**定位**：飞书功能线中间分支，增加 Markdown 导出与知识库批量下载；后续在 preview 上演进为 **PDF 默认导出**。

| 模块 | 内容 |
|------|------|
| 文档导出 | `FeishuDocxClient` Markdown 导出（现仍可通过 `content-format=markdown` 启用） |
| 批量下载 | 知识库文档批量 ZIP 下载 |
| 测试 | `FeishuDocxClientTest` 等 |

**后续演进（preview / 本地）**：默认改为 PDF + MinerU；`fallback-on-error` 统一降级链；`FeishuExportPollingExecutor`。

**变更规模**：约 68 个文件，+5770 / -174 行（相对 `main`）。

**状态**：已全部合入 `preview` 基线；PDF 改造在 preview 之上继续迭代。

---

## origin/feat/mcp/weather

**定位**：天气 MCP 独立功能线与备份。

| 模块 | 内容 |
|------|------|
| 天气 API | 和风天气（QWeather）`QWeatherClient` |
| MCP 执行器 | `WeatherMcpExecutor` 预报查询 |
| 文档 | `docs/weather-mcp-integration.md` |

**状态**：`main` 已 revert；完整代码保留在 `preview`，可归档删除。

---

## 已归档分支

| 分支 | 归档时间 | 功能去向 | 说明 |
|------|----------|----------|------|
| `origin/feat/intent` | 2026-07-07 | `preview`（`c91656d`） | 意图树设计文档与导入 SQL；`main` 已 revert |
| `origin/feat/model/add_model` | 2026-07-07 | `preview`（`08577db`） | DeepSeek 对话模型接入 |
| `origin/my_preview` | 2026-07-07 | 更名为 `origin/preview` | 集成预览分支重命名 |

---

## 合并建议

1. **日常开发**：在 `preview` 上进行；`main` 仅用于同步上游与保持干净基线。
2. **同步上游**：在 `main` 上 merge `upstream/main`，再将 `main` merge 到 `preview`（注意 revert 冲突，保留 `preview` 侧功能代码）。
3. **飞书 PDF 升级**：切换 `content-format: pdf` 后须配置 `docs:document:export` 与 `mineru.api-key`，Pipeline 改为 `PARSER(PDF)`；详见 [`feishu-wiki-integration.md`](feishu-wiki-integration.md) §7.6。
4. **可归档分支**：`single_wiki`、`batch_wiki`、`markdown_support`、`mcp/weather` 在确认 `preview` 稳定后可删除。
5. **已归档**：`intent`、`add_model`、`my_preview`（已更名）无需进一步操作。

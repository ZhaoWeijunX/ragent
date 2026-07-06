# 分支总结

> 生成时间：2026-07-06  
> 基准分支：`main` / `origin/main`（`c91656d`）  
> 当前工作分支：`feat/feishu_source/markdown_support`（`143f53c`，已 merge 最新 `main`）

本文档汇总 **origin 远程仓库** 各功能分支相对 `main` 的增量工作，便于了解进展、合并顺序与分支归档。

## 分支关系概览

```
origin/main
 ├── origin/feat/feishu_source/single_wiki      （飞书单文档导入，基础能力）
 │    └── origin/feat/feishu_source/batch_wiki  （批量导入 + 前后端闭环）
 │         └── origin/feat/feishu_source/markdown_support  （Markdown 导出 + 批量下载，已同步 main）
 ├── origin/feat/mcp/weather                     （天气 MCP，已合入 main，可归档）
 ├── origin/feat/intent                          （意图树文档，已合入 main，与 main 同指针）
 └── origin/feat/model/add_model                 （DeepSeek 模型接入，独立功能线）
```

## 远程分支一览（origin）

| 远程分支 | 领先 `main` | 落后 `main` | 最新提交 | 状态 |
|----------|-------------|-------------|----------|------|
| `origin/main` | — | — | `c91656d` | 稳定基线 |
| `origin/feat/feishu_source/single_wiki` | 10 | 7 | `4989755` | 开发中，需同步 main |
| `origin/feat/feishu_source/batch_wiki` | 15 | 7 | `84b7925` | 开发中，需同步 main |
| `origin/feat/feishu_source/markdown_support` | 20 | 0 | `143f53c` | **推荐合入 main** |
| `origin/feat/intent` | 0 | 0 | `c91656d` | 已合入 main，可归档 |
| `origin/feat/mcp/weather` | 2 | 7 | `72d7b78` | 已合入 main，可归档 |
| `origin/feat/model/add_model` | 1 | 3 | `08577db` | 待 review，合入前需同步 main |

> **上游参考**：`upstream/main`（`50afe91`）落后本地 `main` 10 个提交；`upstream/1.0.x`（`5500546`）为旧版维护线，落后 28 个提交。

---

## origin/main

当前稳定基线，与本地 `main` 同步。近期主要变更：

- MinerU 解析并发：重构为分布式信号量控制
- Chunk 合并：优化文本流与图片处理能力；块级贪心打包
- 知识库索引：统一 Milvus 向量与关键词索引为共享物理索引
- 基础设施：`infra-ai` 模块补充注释与代码结构整理
- **天气 MCP**：和风天气（QWeather）预报查询（`e16e0bc`）
- **意图树**：ragent-test 知识库意图树设计与导入 SQL（`c91656d`）
- 知识库删除时底层资源异步清理
- 向量检索重构，支持全局跨 Collection 召回
- 集成 Elasticsearch 关键词检索与索引

---

## origin/feat/feishu_source/single_wiki

**定位**：飞书 Wiki **单文档**导入的最小可用实现。

**核心能力**：

| 模块 | 内容 |
|------|------|
| 数据通道（Fetcher） | `FeishuFetcher` 支持飞书 Wiki 单页 URL 解析与拉取 |
| 知识库 Remote URL | 通过远程 URL 导入飞书文档 |
| 流水线任务 | 流水线 Fetcher 节点支持飞书 Wiki 单文档 |
| 配置 | 飞书凭证、`application-local` 本地配置、基础设施 host 外置 |
| 基础设施 | 修复 RocketMQ Docker Compose 版本兼容问题 |

**主要新增文件**：`FeishuDocxClient`、`FeishuWikiClient`、`FeishuUrlParser`、`FeishuCredentialsProvider`、`RemoteFileFetcher` 飞书分支逻辑、集成文档 `docs/feishu-wiki-integration.md`。

**变更规模**：约 20 个文件，+1560 / -65 行（相对 `main`）。

**注意**：落后 `main` 7 个提交，且已被 `batch_wiki` / `markdown_support` 包含，无需单独合入。

---

## origin/feat/feishu_source/batch_wiki

**定位**：在 `single_wiki` 全部能力之上，扩展为**批量导入**与完整前后端闭环。

**在 single_wiki 基础上新增**：

| 模块 | 内容 |
|------|------|
| 批量导入 | 支持按 Wiki 空间、子树、单页三种范围批量发现并导入 |
| 任务调度 | `FeishuWikiImportJob` / `Item` 实体、MQ 消费者、导入服务 |
| API | `FeishuWikiImportController`（发现、提交、查询任务） |
| 前端 | `FeishuWikiImportDialog`、文档处理模式选择（chunk / pipeline） |
| 知识库 | 集合名允许连字符；修复 RestFS → RustFS 拼写 |
| 数据库 | `schema_pg.sql` 增量 + `upgrade_v1.2_to_v1.2.1_feishu.sql` 升级脚本 |

**变更规模**：约 63 个文件，+5111 / -177 行（相对 `main`）。

**注意**：落后 `main` 7 个提交，已被 `markdown_support` 包含，无需单独合入。

---

## origin/feat/feishu_source/markdown_support

**定位**：飞书功能线**最新分支**，在 `batch_wiki` 之上增加 Markdown 导出与知识库批量下载；**已 merge 最新 `main`，可提 PR**。

**在 batch_wiki 基础上新增**：

| 模块 | 内容 |
|------|------|
| 文档导出 | `FeishuDocxClient` 支持 Markdown 导出，失败时回退为纯文本 |
| 流水线 | `IngestionPipelineServiceImpl` 与节点 Mapper 适配 Markdown 内容 |
| 批量下载 | 知识库文档批量打包为 ZIP 下载（`KnowledgeDocumentController` / `Service`） |
| 测试 | `FeishuDocxClientTest` 覆盖 Markdown / 纯文本回退 |
| 配置 | `FeishuProperties` 增加 Markdown 相关开关 |
| 文档 | `feishu-wiki-integration.md`、示例与 `docs/branch-summary.md` |

**变更规模**：约 68 个文件，+5710 / -174 行（相对 `main`）。

**同步状态**：与 `origin/feat/feishu_source/markdown_support` 一致（`143f53c`），领先 `main` 20 个提交，落后 0。

---

## origin/feat/intent

**定位**：ragent-test 知识库**意图树**设计与导入脚本。

**核心能力**：

| 模块 | 内容 |
|------|------|
| 设计文档 | `resources/docs/ragent-test/intent-tree-design.md` |
| 导入 SQL | `docs/examples/ragent-test-intent-nodes-import.sql` |

**变更规模**：3 个文件，+427 行（已通过 `c91656d` 合入 `main`）。

**状态**：远程分支指针与 `origin/main` 相同，功能已交付，分支可归档。

---

## origin/feat/mcp/weather

**定位**：独立的天气查询 MCP 工具集成，与飞书功能线无依赖。

**核心能力**：

| 模块 | 内容 |
|------|------|
| 天气 API | 接入和风天气（QWeather）`QWeatherClient` |
| MCP 执行器 | 重构 `WeatherMcpExecutor`，支持预报查询 |
| 意图节点 | SQL 示例：天气意图节点导入与 prompt 更新 |
| Prompt | `weather-mcp-parameter-extract.st` 参数抽取模板 |
| 配置 | `WeatherProperties`、`application.yaml` / `application-local` 示例 |
| 文档 | `docs/weather-mcp-integration.md` |

**变更规模**：约 12 个文件，+955 / -153 行（相对分支基点）。

**状态**：同等功能已通过 `e16e0bc` 合入 `main`；远程分支落后 `main` 7 个提交，可删除或仅作历史参考。

---

## origin/feat/model/add_model

**定位**：扩展 AI 模型提供方，接入 **DeepSeek** 对话能力。

**核心能力**：

| 模块 | 内容 |
|------|------|
| Chat 客户端 | `DeepSeekChatClient` 实现 DeepSeek API 调用 |
| 模型枚举 | `ModelProvider` 新增 DeepSeek 类型 |
| 配置 | `application.yaml` 与 `application-local.yaml.example` 增加 DeepSeek 配置项 |

**变更规模**：4 个文件，+67 行（相对 `main`）。

**注意**：落后 `main` 3 个提交（`infra-ai` 整理、天气 MCP、意图树文档），提 PR 前需 merge/rebase `main`。

---

## 合并建议

1. **飞书功能**：仅 `origin/feat/feishu_source/markdown_support` 需要合入 `main`；`single_wiki` 与 `batch_wiki` 为其历史中间分支，无需单独提 PR。
2. **优先合入**：`markdown_support` 已同步 `main`，可直接创建 PR。
3. **待同步后合入**：`origin/feat/model/add_model` merge `main` 后提 PR。
4. **可归档分支**：`origin/feat/intent`、`origin/feat/mcp/weather`（功能已在 `main`）；`single_wiki`、`batch_wiki` 在 `markdown_support` 合入后可删除。
5. **并行性**：`model/add_model` 与飞书分支模块重叠小（`infra-ai` / 配置），可与飞书 PR 并行 review。

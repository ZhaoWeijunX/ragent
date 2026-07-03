# 本地分支总结

> 生成时间：2026-07-03  
> 基准分支：`main`（`545aea7`）

本文档简要说明各本地功能分支相对 `main` 的增量工作，便于了解进展与合并顺序。

## 分支关系概览

```
main
 ├── feat/feishu_source/single_wiki      （飞书单文档导入，基础能力）
 │    └── feat/feishu_source/batch_wiki  （在 single_wiki 上增加批量导入等）
 │         └── feat/feishu_source/markdown_support  （在 batch_wiki 上增加 Markdown 导出）
 └── feat/mcp/weather                     （天气 MCP，独立功能线）
```

各功能分支均已通过 merge 纳入最新 `main`，**不落后**于 `main`，仅在 `main` 之上有各自的功能提交：

| 分支 | 领先 `main` | 落后 `main` |
|------|-------------|-------------|
| `feat/feishu_source/single_wiki` | 10 | 0 |
| `feat/feishu_source/batch_wiki` | 15 | 0 |
| `feat/feishu_source/markdown_support` | 16 | 0 |
| `feat/mcp/weather` | 2 | 0 |

---

## main

当前稳定基线，已同步上游 `upstream/main`。近期主要变更：

- 知识库删除时底层资源异步清理
- 向量检索重构，支持全局跨 Collection 召回
- 集成 Elasticsearch 关键词检索与索引

---

## feat/feishu_source/single_wiki

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

**变更规模**：约 20 个文件，+1560 / -65 行。

---

## feat/feishu_source/batch_wiki

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

**变更规模**：约 63 个文件，+5111 / -177 行。

---

## feat/feishu_source/markdown_support

**定位**：在 `batch_wiki` 全部能力之上，改进飞书 **docx 内容导出格式**。

**在 batch_wiki 基础上新增**：

| 模块 | 内容 |
|------|------|
| 文档导出 | `FeishuDocxClient` 支持 Markdown 导出，失败时回退为纯文本 |
| 流水线 | `IngestionPipelineServiceImpl` 与节点 Mapper 适配 Markdown 内容 |
| 测试 | `FeishuDocxClientTest` 覆盖 Markdown / 纯文本回退 |
| 配置 | `FeishuProperties` 增加 Markdown 相关开关 |
| 文档 | 扩展 `feishu-wiki-integration.md` 说明导出行为 |

**变更规模**：约 66 个文件，+5475 / -172 行（相对 `main`）。

---

## feat/mcp/weather

**定位**：独立的天气查询 MCP 工具集成，与飞书功能线无依赖。

**核心能力**：

| 模块 | 内容 |
|------|------|
| 天气 API | 接入和风天气（QWeather）`QWeatherClient` |
| MCP 执行器 | 重构 `WeatherMcpExecutor`，支持预报查询 |
| 意图节点 | 提供 SQL 示例：天气意图节点导入与 prompt 更新 |
| Prompt | `weather-mcp-parameter-extract.st` 参数抽取模板 |
| 配置 | `WeatherProperties`、`application.yaml` / `application-local` 示例 |
| 文档 | `docs/weather-mcp-integration.md` |

**变更规模**：约 12 个文件，+955 / -153 行。

---

## 合并建议

1. **飞书系列**：建议按 `single_wiki` → `batch_wiki` → `markdown_support` 顺序合并，后者包含前者全部提交。
2. **天气 MCP**：可与飞书分支并行合并，冲突面较小（主要触及 `mcp-server` 模块）。
3. 各分支已包含 `main` 最新基线（含 ES 关键词检索、向量检索重构等），可直接向 `main` 提 PR；若希望保持线性历史，可在合并前 rebase，但无需为追赶 `main` 而 rebase。

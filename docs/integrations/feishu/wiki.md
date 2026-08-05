# 飞书 Wiki 接入开发说明

本文是飞书文档 / Wiki 接入的唯一开发说明，覆盖运行配置、数据库变更、单页与批量导入、代码入口及排错方式。

## 能力边界

- 支持飞书 Wiki、Docx 链接作为知识库的远程来源。
- 支持单页文档导入，以及按单页、子树或整个空间批量导入。
- 默认导出 PDF，并交给 MinerU 解析为结构化 Block；可切换为 Markdown 或纯文本。
- 导出失败时可按 `PDF → Markdown → Plain` 降级，是否降级由配置决定。

## 运行配置

在根目录 `.env` 配置飞书应用凭证；`tenant-access-token` 与 `app-id` / `app-secret` 二选一，前者非空时优先。

```yaml
feishu:
  enabled: true
  app-id: ${FEISHU_APP_ID:}
  app-secret: ${FEISHU_APP_SECRET:}
  tenant-access-token: ${FEISHU_TENANT_TOKEN:}
  content-format: pdf             # pdf（默认）/ markdown / plain
  fallback-on-error: true         # pdf 失败时依次降级
  wiki-import:
    max-pages-per-job: 500
    rate-limit-per-minute: 90

mineru:
  api-key: ${MINERU_API_KEY:}     # content-format=pdf 时必填
```

飞书自建应用需要具备读取 Wiki、读取文档内容与导出文档的权限；具体权限项以当前飞书开放平台应用配置为准。启动后应确认凭证有效、可读取目标空间，且 MinerU 在 PDF 模式下可访问。

## 数据库与依赖

已有数据库需要先执行飞书 Wiki 导入升级脚本：

```bash
psql -h localhost -U postgres -d ragent \
  -f resources/database/upgrades/v1.1.0/260630_feishu_wiki_import.sql
```

批量导入通过 RocketMQ 逐页处理，需保证 RocketMQ 已启动。若采用 `pipeline` 处理模式，还需预先创建相应摄取流水线：

- PDF：[`feishu-pdf-ingestion-pipeline.sql`](../../../resources/database/imports/pipelines/feishu-pdf-ingestion-pipeline.sql)
- Markdown：[`feishu-markdown-ingestion-pipeline.sql`](../../../resources/database/imports/pipelines/feishu-markdown-ingestion-pipeline.sql)

## 导入链路

### 单页远程文档

知识库文档的 `sourceType=REMOTE_URL` 且 URL 识别为飞书链接时，会进入 `FeishuFetcher`：

```text
飞书 URL
  → FeishuUrlParser
  → FeishuAuthService（tenant access token）
  → FeishuDocxClient / FeishuWikiClient
  → PDF、Markdown 或 plain 内容
  → 知识库文档落盘与分块 / Pipeline
```

`content-format=pdf` 是推荐配置：PDF 由 `MinerUDocumentParser` 解析；当 `fallback-on-error=true` 时，失败会尝试 Markdown，再回退到纯文本。若仅需要文本兼容性，可显式使用 `markdown` 或 `plain`。

### 批量 Wiki 导入

批量导入先发现可导入页面，确认范围后创建 Job；消费者通过 RocketMQ 逐页导入。支持范围：

| `scope` | 含义 |
|---|---|
| `PAGE_ONLY` | 仅导入根 URL 对应页面 |
| `SUBTREE` | 导入根节点及其后代 |
| `ENTIRE_SPACE` | 导入所在 Wiki 空间 |

接口由 [`FeishuWikiImportController`](../../../bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/controller/FeishuWikiImportController.java) 提供：

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/knowledge-base/{kb-id}/feishu-wiki/discover` | 预览可导入页面 |
| POST | `/knowledge-base/{kb-id}/feishu-wiki/import` | 创建异步导入任务 |
| GET | `/knowledge-base/feishu-wiki/import/{jobId}` | 查询任务进度 |
| GET | `/knowledge-base/feishu-wiki/import/{jobId}/items` | 分页查询页面结果 |

请求体字段：

```json
{
  "rootUrl": "https://example.feishu.cn/wiki/xxx",
  "scope": "SUBTREE",
  "autoChunk": true,
  "processMode": "chunk",
  "ingestionSpec": "{...}",
  "pipelineId": null,
  "scheduleEnabled": false,
  "scheduleCron": null
}
```

`processMode=chunk` 时使用 `ingestionSpec`；`processMode=pipeline` 时指定 `pipelineId`。定时刷新仅适用于已配置 cron 的文档任务，应先确认飞书权限、限流与目标知识库容量。

## 代码入口

| 职责 | 入口 |
|---|---|
| 配置与格式决策 | [`FeishuProperties`](../../../bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/config/FeishuProperties.java) |
| 批量导入限额与限流 | [`FeishuWikiImportProperties`](../../../bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/config/FeishuWikiImportProperties.java) |
| URL 解析 | [`FeishuUrlParser`](../../../bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/strategy/fetcher/FeishuUrlParser.java) |
| 授权与 API 调用 | `FeishuAuthService`、`FeishuDocxClient`、`FeishuWikiClient` |
| 远程内容获取及格式降级 | [`FeishuFetcher`](../../../bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/strategy/fetcher/FeishuFetcher.java) |
| 批量任务编排 | `FeishuWikiImportServiceImpl`、`FeishuWikiImportConsumer` |
| PDF 解析 | [`MinerUDocumentParser`](../../../bootstrap/src/main/java/com/nageoffer/ai/ragent/core/parser/mineru/MinerUDocumentParser.java) |

## 排错顺序

1. 检查 `feishu.enabled`、凭证及目标链接是否属于应用可访问的空间。
2. 单页失败时确认 `content-format` 与日志中的 PDF / Markdown / Plain 降级过程；PDF 路径再检查 `MINERU_API_KEY` 与网络连通性。
3. 批量任务停滞时检查 RocketMQ、`feishu-wiki-import_topic` 消费情况、任务 Item 的失败原因与 API 限流。
4. `pipeline` 模式失败时确认 `pipelineId` 存在，且 Parser 支持导出内容的 MIME 类型。
5. 修改意图、流水线或配置后，按需重启服务或刷新对应缓存，再重复导入验证。

## 验证清单

- 可发现一个 `PAGE_ONLY` 页面，并能创建 Job。
- Job 完成后文档记录、原文件和分块状态一致。
- `content-format=pdf` 时可看到 MinerU 解析结果；关闭或破坏 PDF 路径时，降级行为符合 `fallback-on-error`。
- `SUBTREE` / `ENTIRE_SPACE` 任务不超过 `max-pages-per-job`，并可通过 Job Items 定位失败页面。

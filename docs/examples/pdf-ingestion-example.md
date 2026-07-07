# 飞书 PDF 摄取完整示例

本文档演示如何创建**飞书 PDF 摄取流水线**（默认 `feishu.content-format: pdf`），并通过 Ingestion 或知识库 Pipeline 模式处理飞书导出的 PDF。

> Markdown 兼容模式见 [`feishu-pipeline-request.json`](feishu-pipeline-request.json) 与 [`feishu-wiki-ingestion-example.md`](feishu-wiki-ingestion-example.md)。

## 流程说明

```
飞书导出 PDF → fetcher-1 → parser-1(MinerU) → enhancer-1 → chunker-1 → indexer-1
               (获取)       (PDF 解析)        (排版修复)  (结构分块)   (向量化)
```

**设计要点**：

- Parser 允许 `PDF`，MIME 路由自动命中 **MinerU**（表格、图片、公式保留为结构化 Block）
- **Enhancer** 修复 MinerU/PDF 导出噪声：合并错误换行、整理表格排版，并**剥离代码块中被注入的行号**（见下文）
- 使用 `structure_aware` 分块，配合 MinerU 的 block-aware 切分（含表格按行拆分）
- Indexer 设置 `includeEnhancedContent: true`，向量化使用 Enhancer 整理后的文本

---

## 完整操作步骤

### Step 1: 创建流水线

**请求**:

```bash
curl -X POST "http://localhost:8080/api/ragent/ingestion/pipelines" \
  -H "Content-Type: application/json" \
  -d @pdf-pipeline-request.json
```

**请求体** (`pdf-pipeline-request.json`):

- `nextNodeId`: 指向下一个要执行的节点
- 最后一个节点 (indexer-1) 不需要 `nextNodeId` 字段
- 引擎自动找到起始节点（没有被引用的节点）

**响应**:

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "feishu-pdf-ingestion-pipeline",
    "description": "飞书云文档 / 知识库 Wiki 摄取流水线（PDF 格式，默认）- 异步导出 PDF、MinerU 解析、AI 排版修复、结构感知分块、向量化",
    "nodeCount": 5
  }
}
```

### Step 2: 创建摄取任务（飞书来源）

**前置**：`application.yaml` 配置 `feishu.*` 与 `mineru.api-key`；飞书应用需开通 `docs:document:export` 权限。

**请求**:

```bash
curl -X POST "http://localhost:8080/api/ragent/ingestion/tasks" \
  -H "Content-Type: application/json" \
  -H "Authorization: <token>" \
  -d '{
    "pipelineId": "1",
    "source": {
      "type": "FEISHU",
      "location": "https://xxx.feishu.cn/wiki/wikcnXXXXXXXX",
      "credentials": {
        "app_id": "cli_xxx",
        "app_secret": "xxx"
      }
    },
    "vectorSpaceId": {
      "logicalName": "你的知识库collectionName"
    }
  }'
```

**说明**:

- 来源须选 **Feishu**，不要用 URL 来源粘贴飞书链接
- `pipelineId`: 第一步返回的流水线 ID
- FeishuFetcher 按 `content-format: pdf` 导出 PDF 后进入 Parser → MinerU

**响应**:

```json
{
  "success": true,
  "data": {
    "taskId": 123,
    "status": "RUNNING",
    "pipelineId": 1
  }
}
```

### Step 3: 查看任务状态

**请求**:

```bash
curl "http://localhost:8080/api/ragent/ingestion/tasks/123"
```

**响应（完成）**:

```json
{
  "success": true,
  "data": {
    "taskId": 123,
    "pipelineId": 1,
    "status": "COMPLETED",
    "startTime": "2026-01-22T14:30:00",
    "completeTime": "2026-01-22T14:35:20",
    "chunks": 28
  }
}
```

> MinerU 解析含上传、轮询、下载 zip，单文档耗时通常长于 Markdown 路径。

---

## 节点配置说明

### Parser（PDF → MinerU）

```json
{
  "rules": [{ "mimeType": "PDF" }]
}
```

`application/pdf` 由 `DocumentParserSelector` 路由到 `MinerUDocumentParser`（`@Order(HIGHEST_PRECEDENCE)`）。

### Enhancer（排版修复 + 代码块行号清理）

MinerU 解析飞书导出的 PDF 时，代码块内可能被注入 PDF 行号或页码，例如：

````
```1
2 public String chat(ChatRequest request) {
3   return executor.executeWithFallback(
````

Enhancer 的 `context_enhance` 任务会：

- 将 `` ```1 `` 恢复为 `` ``` ``（保留语言标签如 `` ```java ``）
- 剥离代码行首的 `1 `、`2 ` 等序号，**不改动代码本体**
- 不误删正文中的有序列表（如 `1. 第一步`）

配置见 [`pdf-pipeline-request.json`](pdf-pipeline-request.json) 中 `enhancer-1` 节点的 `systemPrompt` / `userPromptTemplate`。

### Chunker（结构感知）

```json
{
  "strategy": "structure_aware",
  "chunkSize": 1400,
  "overlapSize": 0,
  "rowsPerChunk": 40
}
```

MinerU 输出非空 `blocks` 时走 block-aware 路径，表格按 `rowsPerChunk` 拆分。

### Indexer

```json
{
  "embeddingModel": "qwen-emb-8b",
  "includeEnhancedContent": true,
  "metadataFields": ["source_type", "source_location"]
}
```

---

## 知识库 Pipeline 模式

知识库 **Remote URL** 或 **飞书 Wiki 批量导入** 选择 `processMode=pipeline` 时：

1. 文档在上传阶段已由 `FeishuFetcher` 落盘为 `.pdf`
2. Pipeline 推荐 `PARSER(PDF) → ENHANCER → CHUNKER → INDEXER`（Fetcher 自动跳过）
3. 确保 `feishu.content-format` 为 `pdf`（默认）

---

## 常见错误

| 现象 | 原因 | 处理 |
|------|------|------|
| `文件类型不符合要求…MARKDOWN…` | Pipeline 仍配置为 MARKDOWN，但文档已是 PDF | 改用本示例 Pipeline，允许 `PDF` |
| `未找到 MIME [application/pdf] 对应的解析器` | 未配置 `MINERU_API_KEY` 或 MinerU 组件未加载 | 检查 `mineru.api-key` |
| MinerU 下载 zip SSL 失败 | 本地代理 TUN 拦截 Java 流量 | 对 `aliyuncs.com` / `mineru.net` 直连，或关闭 TUN |
| `可分块文本为空` | 用了 URL 来源而非 Feishu 来源 | 改用 Feishu 来源或知识库 Remote URL |

---

## 快速测试脚本

```bash
#!/bin/bash

API_BASE="http://localhost:8080/api/ragent"

echo "Creating Feishu PDF pipeline..."
PIPELINE_RESPONSE=$(curl -s -X POST "${API_BASE}/ingestion/pipelines" \
  -H "Content-Type: application/json" \
  -d @pdf-pipeline-request.json)

PIPELINE_ID=$(echo $PIPELINE_RESPONSE | jq -r '.data.id')
echo "Pipeline created: ID=${PIPELINE_ID}"

echo "Creating Feishu ingestion task..."
TASK_RESPONSE=$(curl -s -X POST "${API_BASE}/ingestion/tasks" \
  -H "Content-Type: application/json" \
  -d "{
    \"pipelineId\": \"${PIPELINE_ID}\",
    \"source\": {
      \"type\": \"FEISHU\",
      \"location\": \"https://xxx.feishu.cn/docx/doccnXXXX\",
      \"credentials\": {
        \"app_id\": \"${FEISHU_APP_ID}\",
        \"app_secret\": \"${FEISHU_APP_SECRET}\"
      }
    },
    \"vectorSpaceId\": { \"logicalName\": \"test-collection\" }
  }")

TASK_ID=$(echo $TASK_RESPONSE | jq -r '.data.taskId')
echo "Task created: ID=${TASK_ID}"
```

---

## 相关文档

- [飞书 Wiki 集成开发文档](../feishu-wiki-integration.md)
- [飞书摄取示例](feishu-wiki-ingestion-example.md)
- [Markdown 兼容 Pipeline](feishu-pipeline-request.json)

---

**创建时间**: 2026-01-22  
**更新时间**: 2026-07-07  
**维护者**: RAGent Team

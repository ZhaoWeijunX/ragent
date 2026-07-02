# 飞书 Wiki / 云文档摄取示例

> 完整开发说明（背景、架构、实现细节、测试）见 [`docs/feishu-wiki-integration.md`](../feishu-wiki-integration.md)。

本文档说明如何将飞书云文档或知识库 wiki 页面导入 RAG 系统。有两种入口，按是否需要**知识库文档管理**选择。

## 入口选择

| 需求 | 推荐入口 | 凭证配置 |
|------|---------|---------|
| 文档列表、分块管理、定时同步 | **知识库 → Remote URL** | `application.yaml` 的 `feishu.*` |
| 仅写入向量、无需文档记录 | **Ingestion → Feishu 来源** | 任务内 `credentials` JSON |

## 支持的链接格式

| 类型 | 链接示例 | 说明 |
|------|---------|------|
| 云文档 docx | `https://xxx.feishu.cn/docx/doccnXXXX` | 直接拉取文档 Markdown |
| 旧版 docs | `https://xxx.feishu.cn/docs/doccnXXXX` | 同上 |
| 知识库 wiki 页面 | `https://xxx.feishu.cn/wiki/wikcnXXXX` | 先解析 wiki 节点，再拉取底层 docx Markdown |

**不支持：**

- 知识库空间首页：`https://xxx.feishu.cn/wiki/`（无具体页面 token）
- wiki 中非 docx 类型节点（如表格 sheet），会返回明确错误

## 飞书应用准备

1. 在 [飞书开放平台](https://open.feishu.cn/) 创建企业自建应用
2. 开通权限（名称以平台文档为准）：
   - 查看云文档内容 `docs:document.content:read`（Markdown 导出）
   - 云文档只读 `docx:document:readonly`（Markdown 失败回退）
   - 知识库节点读取（wiki `get_node`）
3. 发布应用并安装到目标租户
4. 确保目标文档 / wiki 页面对应用可见

---

## 方式一：知识库 Remote URL（推荐）

### 1. 服务端配置

在 `application.yaml` 中启用飞书：

```yaml
feishu:
  enabled: true
  app-id: ${FEISHU_APP_ID:}
  app-secret: ${FEISHU_APP_SECRET:}
  # 可选：tenant-access-token: ${FEISHU_TENANT_TOKEN:}
```

### 2. 上传文档

管理后台：**知识库 → 上传文档**

- 来源类型：**Remote URL**
- 来源地址：粘贴飞书 docx 或 wiki 单页链接
- 处理模式：
  - **chunk**：直接分块（Markdown 自动走 block-aware 结构分块）
  - **pipeline**：选择 Parser 允许 `MARKDOWN` 的 Pipeline（无需含 Fetcher 节点，上传时已落盘）

### 3. 定时同步（可选）

Remote URL 来源可开启 cron 定时刷新；飞书文档按内容 hash 检测变更后自动重新分块。

---

## 方式二：Ingestion 数据通道

### 创建 Pipeline

wiki / docx 拉取结果均为 **Markdown**（`text/markdown`），Parser 需允许 MARKDOWN：

```
FETCHER → PARSER → CHUNKER → INDEXER
```

Parser settings 示例：

```json
{
  "rules": [{ "mimeType": "MARKDOWN" }]
}
```

Indexer 的 `collectionName` 或任务中的 `vectorSpaceId.logicalName` 应与目标知识库的 `collectionName` 一致。

### 创建摄取任务

**请求：**

```bash
curl -X POST "http://localhost:9090/api/ragent/ingestion/tasks" \
  -H "Content-Type: application/json" \
  -H "Authorization: <token>" \
  -d '{
    "pipelineId": "你的流水线ID",
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

管理后台 **Ingestion** 页：来源须选 **Feishu**（不要选 URL 粘贴飞书链接）。

> Ingestion 任务不会创建 `t_knowledge_document` 记录，向量 `doc_id` 为任务 ID。

---

## 常见错误

| 现象 | 原因 | 处理 |
|------|------|------|
| `请提供具体 wiki 页面链接` | 只填了 `/wiki/` 首页 | 打开具体页面复制完整 URL |
| `暂仅支持 docx 类型的 wiki 节点` | wiki 页是表格等类型 | 导出文件上传，或等待后续扩展 |
| `飞书集成未启用` / `未配置凭证` | 知识库路径未配 `feishu.*` | 检查 `feishu.enabled` 与 app-id/secret |
| `飞书 Wiki API 请求失败` | 权限不足或 token 无效 | 检查应用权限、安装范围与凭证 |
| `不支持的飞书链接格式` | 非 docx/docs/wiki 链接 | 使用支持的链接格式 |
| `文件类型不符合要求…UNKNOWN…TEXT` | Pipeline 传入扩展名而非 MIME | 已修复：需 `MimeTypeDetector`；升级后重试分块 |
| `可分块文本为空` + Parser=Tika | Ingestion 用了 **URL** 来源拉飞书 | 改用 **Feishu** 来源，或走知识库 Remote URL |

---

## 相关文档与代码

- 开发文档：[`docs/feishu-wiki-integration.md`](../feishu-wiki-integration.md)
- Pipeline 示例：[`feishu-pipeline-request.json`](feishu-pipeline-request.json)
- [`FeishuFetcher.java`](../../bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/strategy/fetcher/FeishuFetcher.java)
- [`RemoteFileFetcher.java`](../../bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/handler/RemoteFileFetcher.java)
- [`FeishuProperties.java`](../../bootstrap/src/main/java/com/nageoffer/ai/ragent/knowledge/config/FeishuProperties.java)

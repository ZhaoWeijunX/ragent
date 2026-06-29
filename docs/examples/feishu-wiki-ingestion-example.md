# 飞书 Wiki / 云文档摄取示例

> 完整开发说明（背景、架构、实现细节、测试）见 [`docs/feishu-wiki-integration.md`](../feishu-wiki-integration.md)。

本文档说明如何通过 **数据通道（Ingestion）** 导入飞书云文档或知识库 wiki 页面。

## 支持的链接格式

| 类型 | 链接示例 | 说明 |
|------|---------|------|
| 云文档 docx | `https://xxx.feishu.cn/docx/doccnXXXX` | 直接拉取文档纯文本 |
| 旧版 docs | `https://xxx.feishu.cn/docs/doccnXXXX` | 同上 |
| 知识库 wiki 页面 | `https://xxx.feishu.cn/wiki/wikcnXXXX` | 先解析 wiki 节点，再拉取底层 docx 正文 |

**不支持：**

- 知识库空间首页：`https://xxx.feishu.cn/wiki/`（无具体页面 token）
- wiki 中非 docx 类型节点（如表格 sheet），会返回明确错误

## 飞书应用准备

1. 在 [飞书开放平台](https://open.feishu.cn/) 创建企业自建应用
2. 开通权限（名称以平台文档为准）：
   - 云文档只读（docx `raw_content`）
   - 知识库节点读取（wiki `get_node`）
3. 发布应用并安装到目标租户
4. 确保目标文档 / wiki 页面对应用可见

## 创建流水线

wiki / docx 拉取结果均为 **纯文本**（`text/plain`），Parser 需允许 TEXT 类型或不配置 rules：

```
FETCHER → PARSER → CHUNKER → INDEXER
```

Parser settings 示例：

```json
{
  "rules": [{ "mimeType": "TEXT" }]
}
```

Indexer 的 `collectionName` 或任务中的 `vectorSpaceId.logicalName` 应与目标知识库的 `collectionName` 一致。

## 创建摄取任务

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

也可在管理后台 **Ingestion** 页选择来源 **Feishu**，粘贴链接并填写凭证 JSON。

## 常见错误

| 现象 | 原因 | 处理 |
|------|------|------|
| `请提供具体 wiki 页面链接` | 只填了 `/wiki/` 首页 | 打开具体页面复制完整 URL |
| `暂仅支持 docx 类型的 wiki 节点` | wiki 页是表格等类型 | 导出文件上传，或后续扩展支持 |
| `飞书 Wiki API 请求失败` | 权限不足或 token 无效 | 检查应用权限、安装范围与凭证 |
| `不支持的飞书链接格式` | 非 docx/docs/wiki 链接 | 使用支持的链接格式 |

## 相关文档与代码

- 开发文档：[`docs/feishu-wiki-integration.md`](../feishu-wiki-integration.md)
- Pipeline 示例：[`feishu-pipeline-request.json`](feishu-pipeline-request.json)
- [`FeishuFetcher.java`](../../bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/strategy/fetcher/FeishuFetcher.java)
- [`FeishuWikiClient.java`](../../bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/strategy/fetcher/FeishuWikiClient.java)
- [`FeishuUrlParser.java`](../../bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/strategy/fetcher/FeishuUrlParser.java)

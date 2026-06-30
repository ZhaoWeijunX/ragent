# 飞书 Wiki 整库批量导入示例

> 完整开发说明见 [`docs/feishu-wiki-integration.md`](../feishu-wiki-integration.md)。

## 前置条件

1. `application.yaml` 配置 `feishu.enabled=true` 及 `app-id` / `app-secret`
2. 飞书应用开通 **查看知识库**、**云文档只读** 权限
3. 数据库执行 [`upgrade_v1.2_to_v1.3.sql`](../../resources/database/upgrade_v1.2_to_v1.3.sql)

## 管理后台

**知识库 → 文档管理 → 飞书 Wiki 导入**

1. 粘贴 Wiki 页面链接（如 `https://xxx.feishu.cn/wiki/wikcnXXXX`）
2. 选择范围：仅当前页 / 子树 / 整个知识空间
3. 点击 **预览页面** 查看可导入列表
4. 勾选「导入后自动分块」（可选）
5. 确认导入，等待进度完成

## API 示例

### 预览

```bash
curl -X POST "http://localhost:9090/api/ragent/knowledge-base/{kbId}/feishu-wiki/discover" \
  -H "Content-Type: application/json" \
  -H "Authorization: <token>" \
  -d '{
    "rootUrl": "https://xxx.feishu.cn/wiki/wikcnXXXX",
    "scope": "SUBTREE"
  }'
```

### 启动导入

```bash
curl -X POST "http://localhost:9090/api/ragent/knowledge-base/{kbId}/feishu-wiki/import" \
  -H "Content-Type: application/json" \
  -H "Authorization: <token>" \
  -d '{
    "rootUrl": "https://xxx.feishu.cn/wiki/wikcnXXXX",
    "scope": "ENTIRE_SPACE",
    "autoChunk": true,
    "processMode": "chunk",
    "chunkStrategy": "fixed_size",
    "chunkConfig": "{\"chunkSize\":512,\"overlapSize\":128}"
  }'
```

### 查询进度

```bash
curl "http://localhost:9090/api/ragent/knowledge-base/feishu-wiki/import/{jobId}" \
  -H "Authorization: <token>"
```

## 去重说明

同一知识库下相同 `feishu_node_token` 的页面再次导入时会 **更新** 已有文档（覆盖文件并置为 pending），不会重复创建。

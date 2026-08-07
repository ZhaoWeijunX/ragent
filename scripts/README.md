# 脚本说明

本目录只保存开发、数据导入、维护、迁移与评测辅助脚本。脚本不参与应用启动；执行前先阅读 `--help` 和脚本顶部说明，并确认连接的服务、数据库和存储环境。

## 开发环境

| 脚本 | 作用 | 使用要点 |
|---|---|---|
| [`development/export-dotenv.ps1`](development/export-dotenv.ps1) | 读取仓库根目录 `.env` 并导出到当前 PowerShell 会话 | 使用点号加载：`. .\scripts\development\export-dotenv.ps1`；只影响当前会话 |

## 知识导入

| 脚本 | 作用 | 使用要点 |
|---|---|---|
| [`ingestion/batch_import_12306_pdfs.py`](ingestion/batch_import_12306_pdfs.py) | 发现并批量上传 `resources/knowledge-samples/12306-pdf-doc/` 中的 PDF，创建或复用知识库并等待分块 | 先以 `--dry-run` 检查；默认连接本机服务，可用 `--base-url`、`--kb-id`、`--limit` 等参数覆盖 |

```powershell
# 建议先确认运行参数
python .\scripts\ingestion\batch_import_12306_pdfs.py --help

# 仅发现文件与打印计划，不写入服务
python .\scripts\ingestion\batch_import_12306_pdfs.py --dry-run
```

## 数据维护

维护脚本面向已有环境，可能删除对象、清理关系或更新数据库。先阅读脚本参数并以预览模式确认目标；只有明确传入 `--apply` 才执行写操作。

| 脚本 | 作用 |
|---|---|
| [`maintenance/cleanup_orphan_knowledge.py`](maintenance/cleanup_orphan_knowledge.py) | 清理已失去关联的知识库数据 |
| [`maintenance/cleanup_lightrag.py`](maintenance/cleanup_lightrag.py) | 清理 LightRAG 图谱相关残留数据 |
| [`maintenance/purge_soft_deleted.py`](maintenance/purge_soft_deleted.py) | 物理清理超过保留期的软删除数据 |

## 存储迁移

| 脚本 / 文档 | 作用 |
|---|---|
| [`migration/migrate_storage_s3_urls.py`](migration/migrate_storage_s3_urls.py) | 将知识文档 `file_url` 从 `s3://bucket/key` 迁移为裸对象 key，并按需迁移对象 |
| [`migration/README-migrate-storage.md`](migration/README-migrate-storage.md) | 迁移前置条件、参数与回滚注意事项 |

默认以预览模式运行；确认输出后再显式添加 `--apply`。

## 评测辅助

[`evaluation/`](evaluation/README.md) 包含评测工作台的离线契约校验与在线 Spike。离线校验不依赖启动服务；在线脚本需要可访问的 ragent、管理员凭证和目标评测数据。

## 压测

[`sse_queue_test.sh`](sse_queue_test.sh) 用于 SSE 对话排队与并发压测。它会向服务发起实际请求，只应在明确的测试环境中运行。

## 相关资料

- 演示数据、意图树与摄取流水线：[`resources/database/imports/README.md`](../resources/database/imports/README.md)
- 知识样本分类：[`resources/knowledge-samples/README.md`](../resources/knowledge-samples/README.md)

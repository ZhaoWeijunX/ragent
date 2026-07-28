# scripts/

运维与本地辅助脚本。默认安全：涉及写库 / 删数据的 Python 脚本均为 **dry-run**，加 `--apply` 才真正执行。

| 脚本 | 作用 |
|------|------|
| [`export-dotenv.ps1`](export-dotenv.ps1) | 将仓库根目录 `.env` 导出到当前 PowerShell 会话（Spring Boot 不会自动加载 `.env`） |
| [`migrate_storage_s3_urls.py`](migrate_storage_s3_urls.py) | 文档 `file_url` 从 `s3://bucket/key` 迁移为裸 key，并 CopyObject 到全局桶 `ragent-sources` |
| [`cleanup_orphan_knowledge.py`](cleanup_orphan_knowledge.py) | 清理孤儿向量 / 分块 / 定时任务（文档已删或软删，附属数据仍残留） |
| [`cleanup_lightrag.py`](cleanup_lightrag.py) | 通过 LightRAG HTTP API 清空图谱侧文档（不影响业务 `t_*` 表） |
| [`purge_soft_deleted.py`](purge_soft_deleted.py) | 物理清除 PostgreSQL 中 `deleted=1` 的软删行及附属孤儿记录（分批、短事务） |
| [`batch_import_12306_pdfs.py`](batch_import_12306_pdfs.py) | 将 `resources/docs/12306-pdf-doc/` 下 PDF 批量导入知识库，走 `feishu-pdf-ingestion-pipeline`（MinerU → Enhancer → 分块 → 向量化） |
| [`sse_queue_test.sh`](sse_queue_test.sh) | SSE 对话排队 / 并发压测（`BASE_URL` / `TOKEN` / `CONCURRENCY` 等可环境变量覆盖） |

## 常用命令

```powershell
# 加载本地密钥后启动
. .\scripts\export-dotenv.ps1
mvn -pl bootstrap spring-boot:run
```

```bash
# 存储 URL 迁移（先预览）
python scripts/migrate_storage_s3_urls.py
python scripts/migrate_storage_s3_urls.py --apply

# 孤儿知识数据
python scripts/cleanup_orphan_knowledge.py
python scripts/cleanup_orphan_knowledge.py --apply

# LightRAG 图谱
python scripts/cleanup_lightrag.py
python scripts/cleanup_lightrag.py --apply

# 软删物理清理（建议先 dry-run，并设置 --min-age-hours）
python scripts/purge_soft_deleted.py
python scripts/purge_soft_deleted.py --apply --min-age-hours 24

# 12306 PDF 批量导入知识库（需后端已启动、已导入 feishu-pdf-ingestion-pipeline、已配 mineru.api-key）
pip install requests
python scripts/batch_import_12306_pdfs.py --dry-run
python scripts/batch_import_12306_pdfs.py
python scripts/batch_import_12306_pdfs.py --kb-id <KB_ID> --skip-existing
python scripts/batch_import_12306_pdfs.py --kb-id <KB_ID> --retry-failed
```

存储迁移细节见 [`README-migrate-storage.md`](README-migrate-storage.md)。

演示数据 / 意图树 / 流水线 SQL 导入见 [`resources/database/DATA_IMPORT.md`](../resources/database/DATA_IMPORT.md)。
12306 意图树设计见 [`resources/docs/12306-pdf-doc/intent-tree-design.md`](../resources/docs/12306-pdf-doc/intent-tree-design.md)。

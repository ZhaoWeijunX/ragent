# 存储引用迁移（`s3://` → 裸 key）

对应提交 `7cbb02c`：知识库文档从「每库一桶 + `s3://bucket/key`」改为「全局桶 `ragent-sources` + 裸 key `{collection}/{uuid}.ext`」。

## 前置

```bash
pip install boto3 psycopg2-binary
```

确保 PostgreSQL 与 RustFS（默认 `localhost:9000`）可访问，且**旧桶中的源对象仍在**。

## 用法

```bash
# 1. 只预览，不改数据
python scripts/migrate_storage_s3_urls.py

# 2. 试跑一条
python scripts/migrate_storage_s3_urls.py --limit 1 --doc-id 2074327680513388544

# 3. 确认后执行
python scripts/migrate_storage_s3_urls.py --apply
```

可选参数：

| 参数 | 说明 |
|------|------|
| `--pg-dsn` | 默认 `postgresql://postgres:postgres@localhost:5432/ragent` |
| `--s3-endpoint` | 默认 `http://localhost:9000` |
| `--kb-bucket` | 默认 `ragent-sources` |
| `--include-deleted` | 包含已软删文档 |
| `--fail-on-missing-source` | 源对象缺失时中止（默认跳过并继续） |

## 行为说明

对每条 `file_url LIKE 's3://%'` 的文档：

1. 解析 `s3://{bucket}/{key}`
2. `CopyObject` → `ragent-sources/{bucket}/{key}`（目标已存在则跳过拷贝）
3. 将 `file_url` 更新为 `{bucket}/{key}`

源对象不存在时默认跳过该条（需重新上传或从备份恢复后再跑）。

向量 / 分块记录无需因此迁移；迁移后预览与重新分块应能正常读到原文。

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
迁移历史知识库文档存储引用（7cbb02c 存储重构兼容）

旧格式（每知识库一桶）:
  file_url = s3://{bucket}/{key}          例: s3://kb-product/96d5f5a....md
  对象位置: bucket={bucket}, key={key}

新格式（全局知识库桶 + 目录隔离）:
  file_url = {bucket}/{key}               例: kb-product/96d5f5a....md
  对象位置: bucket=ragent-sources, key={bucket}/{key}

默认只做 dry-run；加 --apply 才会拷贝对象并更新数据库。

依赖:
  pip install boto3 psycopg2-binary

用法示例:
  # 预览
  python scripts/migrate_storage_s3_urls.py

  # 执行迁移
  python scripts/migrate_storage_s3_urls.py --apply

  # 自定义连接
  python scripts/migrate_storage_s3_urls.py --apply \\
    --pg-dsn "postgresql://postgres:postgres@localhost:5432/ragent" \\
    --s3-endpoint http://localhost:9000 \\
    --kb-bucket ragent-sources
"""

from __future__ import annotations

import argparse
import sys
import traceback
from dataclasses import dataclass
from typing import Iterable, List, Optional, Tuple
from urllib.parse import urlparse

try:
    import boto3
    from botocore.client import Config
    from botocore.exceptions import ClientError
except ImportError:
    print("缺少依赖 boto3，请先执行: pip install boto3", file=sys.stderr)
    sys.exit(1)

try:
    import psycopg2
    import psycopg2.extras
except ImportError:
    print("缺少依赖 psycopg2，请先执行: pip install psycopg2-binary", file=sys.stderr)
    sys.exit(1)


@dataclass
class DocRow:
    id: str
    file_url: str
    doc_name: str
    deleted: int


@dataclass
class ParsedS3Url:
    bucket: str
    key: str

    @property
    def new_key(self) -> str:
        return f"{self.bucket}/{self.key}"

    @property
    def new_file_url(self) -> str:
        return self.new_key


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="将 t_knowledge_document.file_url 从 s3://bucket/key 迁移为裸 key，并拷贝对象到全局 kb-bucket"
    )
    p.add_argument(
        "--apply",
        action="store_true",
        help="真正执行拷贝与 UPDATE；默认仅 dry-run",
    )
    p.add_argument(
        "--pg-dsn",
        default="postgresql://postgres:postgres@localhost:5432/ragent",
        help="PostgreSQL DSN（默认与 application.yaml 本地开发一致）",
    )
    p.add_argument(
        "--s3-endpoint",
        default="http://localhost:9000",
        help="S3 兼容 endpoint（RustFS / MinIO）",
    )
    p.add_argument("--s3-access-key", default="rustfsadmin")
    p.add_argument("--s3-secret-key", default="rustfsadmin")
    p.add_argument("--s3-region", default="us-east-1")
    p.add_argument(
        "--kb-bucket",
        default="ragent-sources",
        help="新全局知识库桶（rag.storage.kb-bucket）",
    )
    p.add_argument(
        "--include-deleted",
        action="store_true",
        help="同时处理 deleted=1 的文档（默认跳过）",
    )
    p.add_argument(
        "--limit",
        type=int,
        default=0,
        help="最多处理 N 条（0=不限制，便于试跑）",
    )
    p.add_argument(
        "--doc-id",
        action="append",
        default=[],
        help="只处理指定文档 id，可重复传入",
    )
    p.add_argument(
        "--skip-missing-source",
        action="store_true",
        default=True,
        help="源对象不存在时跳过该条并继续（默认开启）",
    )
    p.add_argument(
        "--fail-on-missing-source",
        action="store_true",
        help="源对象不存在时立即失败",
    )
    return p.parse_args()


def parse_s3_url(file_url: str) -> ParsedS3Url:
    """解析 s3://bucket/key；key 可含路径分隔符。"""
    uri = urlparse(file_url)
    if uri.scheme.lower() != "s3":
        raise ValueError(f"不是 s3:// URL: {file_url}")
    bucket = uri.hostname or uri.netloc
    if not bucket:
        raise ValueError(f"缺少 bucket: {file_url}")
    key = (uri.path or "").lstrip("/")
    if not key:
        raise ValueError(f"缺少 key: {file_url}")
    return ParsedS3Url(bucket=bucket, key=key)


def make_s3_client(args: argparse.Namespace):
    return boto3.client(
        "s3",
        endpoint_url=args.s3_endpoint,
        aws_access_key_id=args.s3_access_key,
        aws_secret_access_key=args.s3_secret_key,
        region_name=args.s3_region,
        config=Config(s3={"addressing_style": "path"}),
    )


def ensure_kb_bucket(s3, kb_bucket: str, apply: bool) -> None:
    try:
        s3.head_bucket(Bucket=kb_bucket)
        print(f"[ok] 目标桶已存在: {kb_bucket}")
        return
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "")
        status = e.response.get("ResponseMetadata", {}).get("HTTPStatusCode")
        if code not in ("404", "NoSuchBucket", "NotFound") and status != 404:
            raise

    if not apply:
        print(f"[dry-run] 将创建目标桶: {kb_bucket}")
        return

    s3.create_bucket(Bucket=kb_bucket)
    print(f"[ok] 已创建目标桶: {kb_bucket}")


def object_exists(s3, bucket: str, key: str) -> bool:
    try:
        s3.head_object(Bucket=bucket, Key=key)
        return True
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code", "")
        status = e.response.get("ResponseMetadata", {}).get("HTTPStatusCode")
        if code in ("404", "NoSuchKey", "NotFound") or status == 404:
            return False
        raise


def fetch_docs(conn, include_deleted: bool, doc_ids: List[str], limit: int) -> List[DocRow]:
    # LIKE 模式用绑定参数传入，避免字面量 's3://%' 中的 % 被 psycopg2 当成占位符
    clauses = ["file_url LIKE %s"]
    params: list = ["s3://%"]
    if not include_deleted:
        clauses.append("deleted = 0")
    if doc_ids:
        clauses.append("id = ANY(%s)")
        params.append(doc_ids)

    sql = f"""
        SELECT id, file_url, doc_name, deleted
        FROM t_knowledge_document
        WHERE {' AND '.join(clauses)}
        ORDER BY id
    """
    if limit and limit > 0:
        sql += " LIMIT %s"
        params.append(limit)

    with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
        cur.execute(sql, params)
        rows = cur.fetchall()
    return [
        DocRow(
            id=str(r["id"]),
            file_url=r["file_url"],
            doc_name=r["doc_name"] or "",
            deleted=int(r["deleted"] or 0),
        )
        for r in rows
    ]


def update_file_url(conn, doc_id: str, new_url: str, apply: bool) -> None:
    if not apply:
        return
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE t_knowledge_document
            SET file_url = %s, update_time = CURRENT_TIMESTAMP
            WHERE id = %s
            """,
            (new_url, doc_id),
        )


def copy_object(
    s3,
    src_bucket: str,
    src_key: str,
    dst_bucket: str,
    dst_key: str,
    apply: bool,
) -> str:
    """返回动作: copied | already-exists | dry-run-copy"""
    if object_exists(s3, dst_bucket, dst_key):
        return "already-exists"
    if not apply:
        return "dry-run-copy"
    s3.copy_object(
        Bucket=dst_bucket,
        Key=dst_key,
        CopySource={"Bucket": src_bucket, "Key": src_key},
    )
    return "copied"


def migrate_one(
    s3,
    conn,
    doc: DocRow,
    kb_bucket: str,
    apply: bool,
    skip_missing: bool,
) -> Tuple[str, Optional[str]]:
    """
    返回 (status, detail)
    status: migrated | skipped-missing | skipped-invalid | error
    """
    try:
        parsed = parse_s3_url(doc.file_url)
    except ValueError as e:
        return "skipped-invalid", str(e)

    if parsed.bucket == kb_bucket:
        # 极端情况：旧数据已把全局桶写进 s3://ragent-sources/xxx
        # 新 key 仍用 bucket/key，避免与「裸 uuid」旧对象混淆
        pass

    if not object_exists(s3, parsed.bucket, parsed.key):
        msg = f"源对象不存在 s3://{parsed.bucket}/{parsed.key}"
        if skip_missing:
            return "skipped-missing", msg
        raise FileNotFoundError(msg)

    action = copy_object(
        s3,
        parsed.bucket,
        parsed.key,
        kb_bucket,
        parsed.new_key,
        apply,
    )
    update_file_url(conn, doc.id, parsed.new_file_url, apply)
    return "migrated", f"{action} -> {kb_bucket}/{parsed.new_key} ; file_url={parsed.new_file_url}"


def summarize(counts: dict) -> None:
    print("\n===== 汇总 =====")
    for k in sorted(counts.keys()):
        print(f"  {k}: {counts[k]}")


def main(argv: Optional[Iterable[str]] = None) -> int:
    args = parse_args()
    if args.fail_on_missing_source:
        args.skip_missing_source = False

    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"模式: {mode}")
    print(f"PG:   {args.pg_dsn}")
    print(f"S3:   {args.s3_endpoint}  kb-bucket={args.kb_bucket}")

    s3 = make_s3_client(args)
    ensure_kb_bucket(s3, args.kb_bucket, args.apply)

    conn = psycopg2.connect(args.pg_dsn)
    conn.autocommit = False
    counts = {
        "migrated": 0,
        "skipped-missing": 0,
        "skipped-invalid": 0,
        "error": 0,
        "total": 0,
    }

    try:
        docs = fetch_docs(conn, args.include_deleted, args.doc_id, args.limit)
        counts["total"] = len(docs)
        print(f"待处理文档数: {len(docs)}")
        if not docs:
            print("没有 file_url LIKE 's3://%' 的文档，无需迁移。")
            return 0

        for i, doc in enumerate(docs, 1):
            prefix = f"[{i}/{len(docs)}] id={doc.id} name={doc.doc_name!r}"
            try:
                status, detail = migrate_one(
                    s3,
                    conn,
                    doc,
                    args.kb_bucket,
                    args.apply,
                    args.skip_missing_source,
                )
                counts[status] = counts.get(status, 0) + 1
                print(f"{prefix} {status}: {doc.file_url} | {detail}")
                if args.apply and status == "migrated":
                    conn.commit()
            except Exception as e:
                conn.rollback()
                counts["error"] += 1
                print(f"{prefix} error: {e}")
                traceback.print_exc()
                if args.fail_on_missing_source:
                    raise

        if not args.apply:
            conn.rollback()
            print("\n[dry-run] 未写入数据库、未拷贝对象。确认无误后加 --apply 执行。")
        else:
            print("\n[apply] 已按条提交成功的迁移。")

        summarize(counts)
        return 1 if counts["error"] else 0
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())

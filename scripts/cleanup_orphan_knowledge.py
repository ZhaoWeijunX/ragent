#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
清理知识库无效/孤儿数据（文档已不存在或已软删，但向量等仍残留）

典型场景：文档表无记录（或 deleted=1），t_knowledge_vector 仍按 metadata.doc_id 可检索，
导致问答召回「不存在的文档」，预览报「文档不存在」。

清理范围（默认）:
  1. 孤儿向量：metadata.doc_id 为空，或对应文档不存在 / deleted=1
  2. 孤儿分块：deleted=0 且所属文档不存在 / deleted=1 → 软删
  3. 孤儿定时任务及其执行记录：所属文档不存在 / deleted=1 → 硬删
  4. 无归属 collection 的向量：collection_name 不在未删除知识库中

默认只做 dry-run；加 --apply 才会执行 DELETE/UPDATE。

依赖:
  pip install psycopg2-binary

用法示例:
  # 预览
  python scripts/cleanup_orphan_knowledge.py

  # 只看某个 doc
  python scripts/cleanup_orphan_knowledge.py --doc-id 2073960493273477120

  # 确认后执行
  python scripts/cleanup_orphan_knowledge.py --apply

  # 自定义连接
  python scripts/cleanup_orphan_knowledge.py --apply \\
    --pg-dsn "postgresql://postgres:postgres@localhost:5432/ragent"

说明:
  - 不清理 LightRAG 图谱（需另调 HTTP API）；清完 PG 后若仍召回，检查图谱残留
  - 默认不处理「文档仍在但 enabled=0」的向量；需要时加 --include-disabled
"""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Sequence, Tuple

try:
    import psycopg2
    import psycopg2.extras
except ImportError:
    print("缺少依赖 psycopg2，请先执行: pip install psycopg2-binary", file=sys.stderr)
    sys.exit(1)


@dataclass
class Counts:
    orphan_vectors: int = 0
    orphan_chunks: int = 0
    orphan_schedules: int = 0
    orphan_schedule_execs: int = 0
    stray_collection_vectors: int = 0


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="清理知识库孤儿向量 / 分块 / 定时任务")
    p.add_argument(
        "--apply",
        action="store_true",
        help="真正执行清理；默认仅 dry-run",
    )
    p.add_argument(
        "--pg-dsn",
        default="postgresql://postgres:postgres@localhost:5432/ragent",
        help="PostgreSQL DSN（默认与本地开发一致）",
    )
    p.add_argument(
        "--doc-id",
        action="append",
        default=[],
        help="只处理指定 doc_id（可重复传入）",
    )
    p.add_argument(
        "--include-disabled",
        action="store_true",
        help="同时清理所属文档 enabled=0 的向量与活跃分块（默认只清理缺失/已软删文档）",
    )
    p.add_argument(
        "--skip-stray-collections",
        action="store_true",
        help="跳过「collection 不属于任何未删除知识库」的向量清理",
    )
    p.add_argument(
        "--limit",
        type=int,
        default=20,
        help="预览时每种问题最多打印 N 条样例（默认 20）",
    )
    return p.parse_args()


def connect(dsn: str):
    conn = psycopg2.connect(dsn)
    conn.autocommit = False
    return conn


def doc_invalid_sql(include_disabled: bool) -> str:
    """文档视为无效的条件（相对 t_knowledge_document d）。"""
    if include_disabled:
        return "(d.id IS NULL OR d.deleted = 1 OR d.enabled = 0)"
    return "(d.id IS NULL OR d.deleted = 1)"


def fetch_all(cur, sql: str, params: Optional[Sequence[Any]] = None) -> List[Dict[str, Any]]:
    cur.execute(sql, params or ())
    rows = cur.fetchall()
    if not rows:
        return []
    cols = [d[0] for d in cur.description]
    return [dict(zip(cols, row)) for row in rows]


def fetch_one_int(cur, sql: str, params: Optional[Sequence[Any]] = None) -> int:
    cur.execute(sql, params or ())
    row = cur.fetchone()
    return int(row[0]) if row and row[0] is not None else 0


def build_doc_filter(alias_expr: str, doc_ids: List[str]) -> Tuple[str, List[Any]]:
    if not doc_ids:
        return "", []
    placeholders = ", ".join(["%s"] * len(doc_ids))
    return f" AND {alias_expr} IN ({placeholders})", list(doc_ids)


def preview_orphan_vectors(cur, include_disabled: bool, doc_ids: List[str], limit: int) -> None:
    invalid = doc_invalid_sql(include_disabled)
    extra, params = build_doc_filter("v.metadata->>'doc_id'", doc_ids)
    rows = fetch_all(
        cur,
        f"""
        SELECT
            v.id AS chunk_id,
            v.collection_name,
            v.metadata->>'doc_id' AS doc_id,
            left(coalesce(v.content, ''), 80) AS content_preview,
            d.deleted AS doc_deleted,
            d.enabled AS doc_enabled
        FROM t_knowledge_vector v
        LEFT JOIN t_knowledge_document d ON d.id = v.metadata->>'doc_id'
        WHERE (
            v.metadata->>'doc_id' IS NULL
            OR btrim(v.metadata->>'doc_id') = ''
            OR {invalid}
        )
        {extra}
        ORDER BY v.collection_name, v.metadata->>'doc_id', v.id
        LIMIT %s
        """,
        params + [limit],
    )
    print(f"\n[孤儿向量样例] 最多 {limit} 条:")
    if not rows:
        print("  (无)")
        return
    for r in rows:
        print(
            f"  chunk={r['chunk_id']} collection={r['collection_name']} "
            f"doc_id={r['doc_id']!r} deleted={r['doc_deleted']} enabled={r['doc_enabled']} "
            f"preview={r['content_preview']!r}"
        )


def count_orphan_vectors(cur, include_disabled: bool, doc_ids: List[str]) -> int:
    invalid = doc_invalid_sql(include_disabled)
    extra, params = build_doc_filter("v.metadata->>'doc_id'", doc_ids)
    return fetch_one_int(
        cur,
        f"""
        SELECT count(*)
        FROM t_knowledge_vector v
        LEFT JOIN t_knowledge_document d ON d.id = v.metadata->>'doc_id'
        WHERE (
            v.metadata->>'doc_id' IS NULL
            OR btrim(v.metadata->>'doc_id') = ''
            OR {invalid}
        )
        {extra}
        """,
        params,
    )


def delete_orphan_vectors(cur, include_disabled: bool, doc_ids: List[str]) -> int:
    invalid = doc_invalid_sql(include_disabled)
    extra, params = build_doc_filter("v2.metadata->>'doc_id'", doc_ids)
    cur.execute(
        f"""
        DELETE FROM t_knowledge_vector v
        WHERE v.id IN (
            SELECT v2.id
            FROM t_knowledge_vector v2
            LEFT JOIN t_knowledge_document d ON d.id = v2.metadata->>'doc_id'
            WHERE (
                v2.metadata->>'doc_id' IS NULL
                OR btrim(v2.metadata->>'doc_id') = ''
                OR {invalid}
            )
            {extra}
        )
        """,
        params,
    )
    return cur.rowcount


def preview_orphan_chunks(cur, include_disabled: bool, doc_ids: List[str], limit: int) -> None:
    invalid = doc_invalid_sql(include_disabled)
    extra, params = build_doc_filter("c.doc_id", doc_ids)
    rows = fetch_all(
        cur,
        f"""
        SELECT c.id, c.doc_id, c.kb_id, c.chunk_index, d.deleted AS doc_deleted, d.enabled AS doc_enabled
        FROM t_knowledge_chunk c
        LEFT JOIN t_knowledge_document d ON d.id = c.doc_id
        WHERE c.deleted = 0 AND {invalid}
        {extra}
        ORDER BY c.doc_id, c.chunk_index
        LIMIT %s
        """,
        params + [limit],
    )
    print(f"\n[孤儿分块样例] 最多 {limit} 条:")
    if not rows:
        print("  (无)")
        return
    for r in rows:
        print(
            f"  chunk={r['id']} doc_id={r['doc_id']} kb_id={r['kb_id']} "
            f"idx={r['chunk_index']} deleted={r['doc_deleted']} enabled={r['doc_enabled']}"
        )


def count_orphan_chunks(cur, include_disabled: bool, doc_ids: List[str]) -> int:
    invalid = doc_invalid_sql(include_disabled)
    extra, params = build_doc_filter("c.doc_id", doc_ids)
    return fetch_one_int(
        cur,
        f"""
        SELECT count(*)
        FROM t_knowledge_chunk c
        LEFT JOIN t_knowledge_document d ON d.id = c.doc_id
        WHERE c.deleted = 0 AND {invalid}
        {extra}
        """,
        params,
    )


def soft_delete_orphan_chunks(cur, include_disabled: bool, doc_ids: List[str]) -> int:
    invalid = doc_invalid_sql(include_disabled)
    extra, params = build_doc_filter("c2.doc_id", doc_ids)
    cur.execute(
        f"""
        UPDATE t_knowledge_chunk c
        SET deleted = 1, update_time = CURRENT_TIMESTAMP
        WHERE c.deleted = 0
          AND c.id IN (
            SELECT c2.id
            FROM t_knowledge_chunk c2
            LEFT JOIN t_knowledge_document d ON d.id = c2.doc_id
            WHERE c2.deleted = 0 AND {invalid}
            {extra}
          )
        """,
        params,
    )
    return cur.rowcount


def count_orphan_schedules(cur, include_disabled: bool, doc_ids: List[str]) -> Tuple[int, int]:
    invalid = doc_invalid_sql(include_disabled)
    extra_s, params_s = build_doc_filter("s.doc_id", doc_ids)
    schedules = fetch_one_int(
        cur,
        f"""
        SELECT count(*)
        FROM t_knowledge_document_schedule s
        LEFT JOIN t_knowledge_document d ON d.id = s.doc_id
        WHERE {invalid}
        {extra_s}
        """,
        params_s,
    )
    extra_e, params_e = build_doc_filter("e.doc_id", doc_ids)
    execs = fetch_one_int(
        cur,
        f"""
        SELECT count(*)
        FROM t_knowledge_document_schedule_exec e
        LEFT JOIN t_knowledge_document d ON d.id = e.doc_id
        WHERE {invalid}
        {extra_e}
        """,
        params_e,
    )
    return schedules, execs


def delete_orphan_schedules(cur, include_disabled: bool, doc_ids: List[str]) -> Tuple[int, int]:
    invalid = doc_invalid_sql(include_disabled)
    extra_e, params_e = build_doc_filter("e2.doc_id", doc_ids)
    cur.execute(
        f"""
        DELETE FROM t_knowledge_document_schedule_exec e
        WHERE e.id IN (
            SELECT e2.id
            FROM t_knowledge_document_schedule_exec e2
            LEFT JOIN t_knowledge_document d ON d.id = e2.doc_id
            WHERE {invalid}
            {extra_e}
        )
        """,
        params_e,
    )
    exec_deleted = cur.rowcount

    extra_s, params_s = build_doc_filter("s2.doc_id", doc_ids)
    cur.execute(
        f"""
        DELETE FROM t_knowledge_document_schedule s
        WHERE s.id IN (
            SELECT s2.id
            FROM t_knowledge_document_schedule s2
            LEFT JOIN t_knowledge_document d ON d.id = s2.doc_id
            WHERE {invalid}
            {extra_s}
        )
        """,
        params_s,
    )
    return cur.rowcount, exec_deleted


def preview_stray_collection_vectors(cur, limit: int) -> None:
    rows = fetch_all(
        cur,
        """
        SELECT v.collection_name, count(*) AS cnt
        FROM t_knowledge_vector v
        LEFT JOIN t_knowledge_base kb
          ON kb.collection_name = v.collection_name AND kb.deleted = 0
        WHERE kb.id IS NULL
        GROUP BY v.collection_name
        ORDER BY cnt DESC
        LIMIT %s
        """,
        [limit],
    )
    print(f"\n[无归属 collection 的向量] 按 collection 汇总，最多 {limit} 组:")
    if not rows:
        print("  (无)")
        return
    for r in rows:
        print(f"  collection={r['collection_name']} vectors={r['cnt']}")


def count_stray_collection_vectors(cur) -> int:
    return fetch_one_int(
        cur,
        """
        SELECT count(*)
        FROM t_knowledge_vector v
        LEFT JOIN t_knowledge_base kb
          ON kb.collection_name = v.collection_name AND kb.deleted = 0
        WHERE kb.id IS NULL
        """,
    )


def delete_stray_collection_vectors(cur) -> int:
    cur.execute(
        """
        DELETE FROM t_knowledge_vector v
        WHERE v.id IN (
            SELECT v2.id
            FROM t_knowledge_vector v2
            LEFT JOIN t_knowledge_base kb
              ON kb.collection_name = v2.collection_name AND kb.deleted = 0
            WHERE kb.id IS NULL
        )
        """
    )
    return cur.rowcount


def summarize_by_doc(cur, include_disabled: bool, doc_ids: List[str], limit: int) -> None:
    invalid = doc_invalid_sql(include_disabled)
    extra, params = build_doc_filter("v.metadata->>'doc_id'", doc_ids)
    rows = fetch_all(
        cur,
        f"""
        SELECT
            coalesce(nullif(btrim(v.metadata->>'doc_id'), ''), '<empty>') AS doc_id,
            v.collection_name,
            count(*) AS vector_count
        FROM t_knowledge_vector v
        LEFT JOIN t_knowledge_document d ON d.id = v.metadata->>'doc_id'
        WHERE (
            v.metadata->>'doc_id' IS NULL
            OR btrim(v.metadata->>'doc_id') = ''
            OR {invalid}
        )
        {extra}
        GROUP BY 1, 2
        ORDER BY vector_count DESC
        LIMIT %s
        """,
        params + [limit],
    )
    print(f"\n[孤儿向量按 doc_id 汇总] 最多 {limit} 组:")
    if not rows:
        print("  (无)")
        return
    for r in rows:
        print(
            f"  doc_id={r['doc_id']} collection={r['collection_name']} vectors={r['vector_count']}"
        )


def main() -> int:
    args = parse_args()
    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"=== 知识库孤儿数据清理 [{mode}] ===")
    print(f"DSN: {args.pg_dsn}")
    print(f"include_disabled={args.include_disabled} skip_stray_collections={args.skip_stray_collections}")
    if args.doc_id:
        print(f"doc_id filter: {args.doc_id}")

    conn = connect(args.pg_dsn)
    try:
        with conn.cursor() as cur:
            counts = Counts()
            counts.orphan_vectors = count_orphan_vectors(cur, args.include_disabled, args.doc_id)
            counts.orphan_chunks = count_orphan_chunks(cur, args.include_disabled, args.doc_id)
            counts.orphan_schedules, counts.orphan_schedule_execs = count_orphan_schedules(
                cur, args.include_disabled, args.doc_id
            )
            if not args.skip_stray_collections and not args.doc_id:
                counts.stray_collection_vectors = count_stray_collection_vectors(cur)

            print("\n[统计]")
            print(f"  孤儿向量:              {counts.orphan_vectors}")
            print(f"  孤儿分块(deleted=0):   {counts.orphan_chunks}")
            print(f"  孤儿定时任务:          {counts.orphan_schedules}")
            print(f"  孤儿定时执行记录:      {counts.orphan_schedule_execs}")
            if not args.skip_stray_collections and not args.doc_id:
                print(f"  无归属 collection 向量: {counts.stray_collection_vectors}")

            summarize_by_doc(cur, args.include_disabled, args.doc_id, args.limit)
            preview_orphan_vectors(cur, args.include_disabled, args.doc_id, args.limit)
            preview_orphan_chunks(cur, args.include_disabled, args.doc_id, args.limit)
            if not args.skip_stray_collections and not args.doc_id:
                preview_stray_collection_vectors(cur, args.limit)

            total = (
                counts.orphan_vectors
                + counts.orphan_chunks
                + counts.orphan_schedules
                + counts.orphan_schedule_execs
                + counts.stray_collection_vectors
            )
            if total == 0:
                print("\n无需清理。")
                conn.rollback()
                return 0

            if not args.apply:
                print("\n当前为 dry-run，未修改数据。确认后请加 --apply 执行。")
                print("注意: 本脚本不清理 LightRAG 图谱；若清完向量后仍召回，请检查图谱残留。")
                conn.rollback()
                return 0

            deleted_vectors = delete_orphan_vectors(cur, args.include_disabled, args.doc_id)
            soft_chunks = soft_delete_orphan_chunks(cur, args.include_disabled, args.doc_id)
            deleted_schedules, deleted_execs = delete_orphan_schedules(
                cur, args.include_disabled, args.doc_id
            )
            deleted_stray = 0
            if not args.skip_stray_collections and not args.doc_id:
                deleted_stray = delete_stray_collection_vectors(cur)

            conn.commit()
            print("\n[已执行]")
            print(f"  删除孤儿向量:            {deleted_vectors}")
            print(f"  软删孤儿分块:            {soft_chunks}")
            print(f"  删除孤儿定时任务:        {deleted_schedules}")
            print(f"  删除孤儿定时执行记录:    {deleted_execs}")
            if not args.skip_stray_collections and not args.doc_id:
                print(f"  删除无归属 collection 向量: {deleted_stray}")
            print("完成。建议重启或观察下一轮检索是否仍命中已删文档。")
            return 0
    except Exception as e:
        conn.rollback()
        print(f"\n失败: {e}", file=sys.stderr)
        return 1
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())

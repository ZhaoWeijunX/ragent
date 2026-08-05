#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
物理清除 PostgreSQL 中已软删数据（deleted=1），并顺带清理其附属孤儿记录。

设计目标（不影响在线业务）:
  - 默认 dry-run，仅统计；加 --apply 才删除
  - 只删 deleted=1，以及「父记录已软删/已不存在」的附属表行
  - 绝不修改 deleted=0 的业务行
  - 分批 DELETE + 短事务，配合 lock_timeout / statement_timeout，避免长锁
  - 默认仅清理 update_time（或 create_time）早于 --min-age-hours 的软删行，
    降低与「刚软删、异步清理仍在进行」的竞态

附属清理（无 deleted 列的表）:
  - t_knowledge_vector：doc_id 对应文档已软删或不存在
  - t_knowledge_document_chunk_log：文档已软删或不存在
  - t_knowledge_document_schedule / _exec：文档已软删或不存在
  - t_feishu_wiki_import_job / _item：所属知识库已软删或不存在

用法:
  # 预览
  python scripts/maintenance/purge_soft_deleted.py

  # 执行（建议先 dry-run）
  python scripts/maintenance/purge_soft_deleted.py --apply

  # 更保守：只清 24h 前的软删，批次更小
  python scripts/maintenance/purge_soft_deleted.py --apply --min-age-hours 24 --batch-size 1000

  # 自定义连接
  python scripts/maintenance/purge_soft_deleted.py --pg-dsn "postgresql://postgres:postgres@localhost:5432/ragent"
"""

from __future__ import annotations

import argparse
import sys
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Sequence, Tuple

try:
    import psycopg2
    import psycopg2.extras
except ImportError:
    print("缺少依赖 psycopg2，请先执行: pip install psycopg2-binary", file=sys.stderr)
    sys.exit(1)


# 软删表清理顺序：先子后父，避免业务逻辑上的悬挂引用（库内无 FK）
SOFT_DELETE_TABLES: List[str] = [
    "t_message_feedback",
    "t_rag_trace_node",
    "t_ingestion_task_node",
    "t_knowledge_chunk",
    "t_conversation_summary",
    "t_message",
    "t_conversation",
    "t_ingestion_task",
    "t_ingestion_pipeline_node",
    "t_ingestion_pipeline",
    "t_knowledge_document",
    "t_knowledge_base",
    "t_intent_node",
    "t_query_term_mapping",
    "t_sample_question",
    "t_rag_trace_run",
    "t_biz_sales_order",
    "t_biz_support_ticket",
    "t_user",
]


@dataclass
class StepResult:
    name: str
    counted: int = 0
    deleted: int = 0


@dataclass
class RunStats:
    steps: List[StepResult] = field(default_factory=list)

    def add(self, step: StepResult) -> None:
        self.steps.append(step)

    @property
    def total_counted(self) -> int:
        return sum(s.counted for s in self.steps)

    @property
    def total_deleted(self) -> int:
        return sum(s.deleted for s in self.steps)


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="分批硬删软删数据（默认 dry-run）")
    p.add_argument("--apply", action="store_true", help="真正执行 DELETE；默认仅统计")
    p.add_argument(
        "--pg-dsn",
        default="postgresql://postgres:postgres@localhost:5432/ragent",
        help="PostgreSQL DSN",
    )
    p.add_argument(
        "--min-age-hours",
        type=float,
        default=1.0,
        help="仅清理更新时间早于 N 小时的软删行（默认 1；设 0 表示不限制）",
    )
    p.add_argument(
        "--batch-size",
        type=int,
        default=2000,
        help="每批 DELETE 行数（默认 2000）",
    )
    p.add_argument(
        "--lock-timeout-ms",
        type=int,
        default=2000,
        help="单批 lock_timeout（毫秒，默认 2000）",
    )
    p.add_argument(
        "--statement-timeout-ms",
        type=int,
        default=30000,
        help="单批 statement_timeout（毫秒，默认 30000）",
    )
    p.add_argument(
        "--sleep-ms",
        type=int,
        default=50,
        help="批次之间休眠毫秒，降低对在线负载冲击（默认 50）",
    )
    p.add_argument(
        "--max-retries",
        type=int,
        default=5,
        help="遇锁超时/语句超时的最大重试次数（默认 5）",
    )
    p.add_argument(
        "--tables",
        default="",
        help="仅清理指定软删表（逗号分隔）；默认全部",
    )
    p.add_argument(
        "--skip-satellites",
        action="store_true",
        help="跳过无 deleted 列的附属孤儿清理",
    )
    return p.parse_args()


def connect(dsn: str):
    conn = psycopg2.connect(dsn)
    conn.autocommit = False
    return conn


def fetch_one_int(cur, sql: str, params: Optional[Sequence[Any]] = None) -> int:
    cur.execute(sql, params or ())
    row = cur.fetchone()
    if not row:
        return 0
    # 兼容 tuple cursor 与 RealDictCursor
    value = row[0] if not isinstance(row, dict) else next(iter(row.values()))
    return int(value) if value is not None else 0


def table_exists(cur, table: str) -> bool:
    return (
        fetch_one_int(
            cur,
            """
            SELECT count(*)
            FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = %s
            """,
            [table],
        )
        > 0
    )


def column_exists(cur, table: str, column: str) -> bool:
    return (
        fetch_one_int(
            cur,
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = %s AND column_name = %s
            """,
            [table, column],
        )
        > 0
    )


def age_predicate(cur, table: str, alias: str, min_age_hours: float) -> Tuple[str, List[Any]]:
    """按 update_time / create_time 限制软删行年龄。"""
    if min_age_hours is None or min_age_hours <= 0:
        return "", []
    if column_exists(cur, table, "update_time"):
        col = f"{alias}.update_time"
    elif column_exists(cur, table, "create_time"):
        col = f"{alias}.create_time"
    else:
        return "", []
    return (
        f" AND COALESCE({col}, TIMESTAMP '1970-01-01') < (NOW() - (%s || ' hours')::interval)",
        [str(min_age_hours)],
    )


def set_batch_timeouts(cur, lock_timeout_ms: int, statement_timeout_ms: int) -> None:
    cur.execute("SET LOCAL lock_timeout = %s", [f"{lock_timeout_ms}ms"])
    cur.execute("SET LOCAL statement_timeout = %s", [f"{statement_timeout_ms}ms"])


def is_retryable(exc: BaseException) -> bool:
    msg = str(exc).lower()
    return any(
        x in msg
        for x in (
            "lock timeout",
            "canceling statement due to statement timeout",
            "could not obtain lock",
            "deadlock detected",
        )
    )


def batched_delete(
    conn,
    *,
    name: str,
    count_sql: str,
    count_params: Sequence[Any],
    delete_sql: str,
    delete_params: Sequence[Any],
    apply: bool,
    batch_size: int,
    lock_timeout_ms: int,
    statement_timeout_ms: int,
    sleep_ms: int,
    max_retries: int,
) -> StepResult:
    result = StepResult(name=name)
    with conn.cursor() as cur:
        result.counted = fetch_one_int(cur, count_sql, count_params)
    print(f"  [{name}] candidates={result.counted}")
    if result.counted == 0 or not apply:
        conn.rollback()
        return result

    remaining_hint = result.counted
    while True:
        deleted_this = 0
        attempt = 0
        while True:
            attempt += 1
            try:
                with conn.cursor() as cur:
                    set_batch_timeouts(cur, lock_timeout_ms, statement_timeout_ms)
                    cur.execute(delete_sql, list(delete_params) + [batch_size])
                    deleted_this = cur.rowcount
                conn.commit()
                break
            except Exception as exc:
                conn.rollback()
                if attempt >= max_retries or not is_retryable(exc):
                    raise
                wait = min(2.0, 0.2 * attempt)
                print(f"    retry {attempt}/{max_retries} after: {exc} (sleep {wait:.1f}s)")
                time.sleep(wait)

        if deleted_this <= 0:
            break
        result.deleted += deleted_this
        remaining_hint = max(0, remaining_hint - deleted_this)
        print(f"    deleted +{deleted_this} (batch total={result.deleted}, remain~{remaining_hint})")
        if sleep_ms > 0:
            time.sleep(sleep_ms / 1000.0)
    return result


def purge_soft_table(
    conn,
    table: str,
    *,
    apply: bool,
    min_age_hours: float,
    batch_size: int,
    lock_timeout_ms: int,
    statement_timeout_ms: int,
    sleep_ms: int,
    max_retries: int,
) -> Optional[StepResult]:
    with conn.cursor() as cur:
        if not table_exists(cur, table):
            print(f"  [{table}] skip (table missing)")
            return None
        if not column_exists(cur, table, "deleted"):
            print(f"  [{table}] skip (no deleted column)")
            return None
        age_sql, age_params = age_predicate(cur, table, "t", min_age_hours)

    # 用 ctid 分批，避免大表一次性删光
    count_sql = f"""
        SELECT count(*)
        FROM {table} t
        WHERE t.deleted = 1
        {age_sql}
    """
    delete_sql = f"""
        DELETE FROM {table} t
        WHERE t.ctid IN (
            SELECT t2.ctid
            FROM {table} t2
            WHERE t2.deleted = 1
            {age_sql.replace('t.', 't2.')}
            LIMIT %s
        )
    """
    return batched_delete(
        conn,
        name=f"soft:{table}",
        count_sql=count_sql,
        count_params=age_params,
        delete_sql=delete_sql,
        delete_params=age_params,
        apply=apply,
        batch_size=batch_size,
        lock_timeout_ms=lock_timeout_ms,
        statement_timeout_ms=statement_timeout_ms,
        sleep_ms=sleep_ms,
        max_retries=max_retries,
    )


def purge_satellites(
    conn,
    *,
    apply: bool,
    min_age_hours: float,
    batch_size: int,
    lock_timeout_ms: int,
    statement_timeout_ms: int,
    sleep_ms: int,
    max_retries: int,
) -> List[StepResult]:
    """清理附属孤儿；年龄门槛作用在父文档/知识库上。"""
    results: List[StepResult] = []
    age_doc = ""
    age_kb = ""
    age_params_doc: List[Any] = []
    age_params_kb: List[Any] = []
    if min_age_hours and min_age_hours > 0:
        age_doc = " AND COALESCE(d.update_time, TIMESTAMP '1970-01-01') < (NOW() - (%s || ' hours')::interval)"
        age_kb = " AND COALESCE(kb.update_time, TIMESTAMP '1970-01-01') < (NOW() - (%s || ' hours')::interval)"
        age_params_doc = [str(min_age_hours)]
        age_params_kb = [str(min_age_hours)]

    table_map = {
        "sat:vectors_of_invalid_docs": "t_knowledge_vector",
        "sat:chunk_logs_invalid_docs": "t_knowledge_document_chunk_log",
        "sat:schedule_exec_invalid_docs": "t_knowledge_document_schedule_exec",
        "sat:schedule_invalid_docs": "t_knowledge_document_schedule",
        "sat:feishu_items_invalid_kb": "t_feishu_wiki_import_item",
        "sat:feishu_jobs_invalid_kb": "t_feishu_wiki_import_job",
    }
    specs: List[Tuple[str, str, str, List[Any]]] = [
        (
            "sat:vectors_of_invalid_docs",
            f"""
            SELECT count(*)
            FROM t_knowledge_vector v
            LEFT JOIN t_knowledge_document d ON d.id = v.metadata->>'doc_id'
            WHERE v.metadata->>'doc_id' IS NULL
               OR btrim(v.metadata->>'doc_id') = ''
               OR d.id IS NULL
               OR (d.deleted = 1 {age_doc})
            """,
            f"""
            DELETE FROM t_knowledge_vector v
            WHERE v.ctid IN (
                SELECT v2.ctid
                FROM t_knowledge_vector v2
                LEFT JOIN t_knowledge_document d ON d.id = v2.metadata->>'doc_id'
                WHERE v2.metadata->>'doc_id' IS NULL
                   OR btrim(v2.metadata->>'doc_id') = ''
                   OR d.id IS NULL
                   OR (d.deleted = 1 {age_doc})
                LIMIT %s
            )
            """,
            age_params_doc,
        ),
        (
            "sat:chunk_logs_invalid_docs",
            f"""
            SELECT count(*)
            FROM t_knowledge_document_chunk_log l
            LEFT JOIN t_knowledge_document d ON d.id = l.doc_id
            WHERE d.id IS NULL OR (d.deleted = 1 {age_doc})
            """,
            f"""
            DELETE FROM t_knowledge_document_chunk_log l
            WHERE l.ctid IN (
                SELECT l2.ctid
                FROM t_knowledge_document_chunk_log l2
                LEFT JOIN t_knowledge_document d ON d.id = l2.doc_id
                WHERE d.id IS NULL OR (d.deleted = 1 {age_doc})
                LIMIT %s
            )
            """,
            age_params_doc,
        ),
        (
            "sat:schedule_exec_invalid_docs",
            f"""
            SELECT count(*)
            FROM t_knowledge_document_schedule_exec e
            LEFT JOIN t_knowledge_document d ON d.id = e.doc_id
            WHERE d.id IS NULL OR (d.deleted = 1 {age_doc})
            """,
            f"""
            DELETE FROM t_knowledge_document_schedule_exec e
            WHERE e.ctid IN (
                SELECT e2.ctid
                FROM t_knowledge_document_schedule_exec e2
                LEFT JOIN t_knowledge_document d ON d.id = e2.doc_id
                WHERE d.id IS NULL OR (d.deleted = 1 {age_doc})
                LIMIT %s
            )
            """,
            age_params_doc,
        ),
        (
            "sat:schedule_invalid_docs",
            f"""
            SELECT count(*)
            FROM t_knowledge_document_schedule s
            LEFT JOIN t_knowledge_document d ON d.id = s.doc_id
            WHERE d.id IS NULL OR (d.deleted = 1 {age_doc})
            """,
            f"""
            DELETE FROM t_knowledge_document_schedule s
            WHERE s.ctid IN (
                SELECT s2.ctid
                FROM t_knowledge_document_schedule s2
                LEFT JOIN t_knowledge_document d ON d.id = s2.doc_id
                WHERE d.id IS NULL OR (d.deleted = 1 {age_doc})
                LIMIT %s
            )
            """,
            age_params_doc,
        ),
        (
            "sat:feishu_items_invalid_kb",
            f"""
            SELECT count(*)
            FROM t_feishu_wiki_import_item i
            JOIN t_feishu_wiki_import_job j ON j.id = i.job_id
            LEFT JOIN t_knowledge_base kb ON kb.id = j.kb_id
            WHERE kb.id IS NULL OR (kb.deleted = 1 {age_kb})
            """,
            f"""
            DELETE FROM t_feishu_wiki_import_item i
            WHERE i.ctid IN (
                SELECT i2.ctid
                FROM t_feishu_wiki_import_item i2
                JOIN t_feishu_wiki_import_job j ON j.id = i2.job_id
                LEFT JOIN t_knowledge_base kb ON kb.id = j.kb_id
                WHERE kb.id IS NULL OR (kb.deleted = 1 {age_kb})
                LIMIT %s
            )
            """,
            age_params_kb,
        ),
        (
            "sat:feishu_jobs_invalid_kb",
            f"""
            SELECT count(*)
            FROM t_feishu_wiki_import_job j
            LEFT JOIN t_knowledge_base kb ON kb.id = j.kb_id
            WHERE kb.id IS NULL OR (kb.deleted = 1 {age_kb})
            """,
            f"""
            DELETE FROM t_feishu_wiki_import_job j
            WHERE j.ctid IN (
                SELECT j2.ctid
                FROM t_feishu_wiki_import_job j2
                LEFT JOIN t_knowledge_base kb ON kb.id = j2.kb_id
                WHERE kb.id IS NULL OR (kb.deleted = 1 {age_kb})
                LIMIT %s
            )
            """,
            age_params_kb,
        ),
    ]

    with conn.cursor() as cur:
        for name, count_sql, delete_sql, params in specs:
            real_table = table_map[name]
            if not table_exists(cur, real_table):
                print(f"  [{name}] skip (table missing)")
                continue
            results.append(
                batched_delete(
                    conn,
                    name=name,
                    count_sql=count_sql,
                    count_params=params,
                    delete_sql=delete_sql,
                    delete_params=params,
                    apply=apply,
                    batch_size=batch_size,
                    lock_timeout_ms=lock_timeout_ms,
                    statement_timeout_ms=statement_timeout_ms,
                    sleep_ms=sleep_ms,
                    max_retries=max_retries,
                )
            )
    return results


def print_overview(conn, min_age_hours: float) -> None:
    print("\n=== 软删总览 ===")
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            """
            SELECT c.relname AS table_name
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            JOIN pg_attribute a ON a.attrelid = c.oid
            WHERE n.nspname = 'public'
              AND c.relkind = 'r'
              AND a.attname = 'deleted'
              AND NOT a.attisdropped
            ORDER BY 1
            """
        )
        tables = [r["table_name"] for r in cur.fetchall()]
        total = 0
        aged_total = 0
        for t in tables:
            soft = fetch_one_int(cur, f'SELECT count(*) FROM "{t}" WHERE deleted = 1')
            age_sql, age_params = age_predicate(cur, t, "t", min_age_hours)
            aged = soft
            if age_sql:
                aged = fetch_one_int(
                    cur,
                    f'SELECT count(*) FROM "{t}" t WHERE t.deleted = 1 {age_sql}',
                    age_params,
                )
            total += soft
            aged_total += aged
            if soft:
                print(f"  {t:40s} soft={soft:6d}  purgeable(age)={aged:6d}")
        print(f"  TOTAL soft={total}  purgeable(age)={aged_total}")


def main() -> int:
    args = parse_args()
    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"=== 软删数据物理清理 [{mode}] ===")
    print(f"DSN: {args.pg_dsn}")
    print(
        f"min_age_hours={args.min_age_hours} batch_size={args.batch_size} "
        f"lock_timeout_ms={args.lock_timeout_ms} statement_timeout_ms={args.statement_timeout_ms}"
    )

    tables = SOFT_DELETE_TABLES
    if args.tables.strip():
        requested = [x.strip() for x in args.tables.split(",") if x.strip()]
        unknown = [t for t in requested if t not in SOFT_DELETE_TABLES]
        if unknown:
            print(f"未知表: {unknown}", file=sys.stderr)
            return 2
        tables = requested

    conn = connect(args.pg_dsn)
    stats = RunStats()
    try:
        print_overview(conn, args.min_age_hours)

        if not args.skip_satellites:
            print("\n=== 附属孤儿清理 ===")
            for step in purge_satellites(
                conn,
                apply=args.apply,
                min_age_hours=args.min_age_hours,
                batch_size=args.batch_size,
                lock_timeout_ms=args.lock_timeout_ms,
                statement_timeout_ms=args.statement_timeout_ms,
                sleep_ms=args.sleep_ms,
                max_retries=args.max_retries,
            ):
                stats.add(step)

        print("\n=== 软删行硬删 ===")
        for table in tables:
            step = purge_soft_table(
                conn,
                table,
                apply=args.apply,
                min_age_hours=args.min_age_hours,
                batch_size=args.batch_size,
                lock_timeout_ms=args.lock_timeout_ms,
                statement_timeout_ms=args.statement_timeout_ms,
                sleep_ms=args.sleep_ms,
                max_retries=args.max_retries,
            )
            if step:
                stats.add(step)

        print("\n=== 汇总 ===")
        print(f"  candidates: {stats.total_counted}")
        if args.apply:
            print(f"  deleted:    {stats.total_deleted}")
            print("完成。在线查询仅读 deleted=0，硬删软删行不影响业务读路径。")
        else:
            print("当前为 dry-run，未修改数据。确认后请加 --apply。")
            print("建议：高峰期用默认 --min-age-hours 1；刚删的文档可先等异步清理完成。")
        return 0
    except Exception as exc:
        conn.rollback()
        print(f"\n失败: {exc}", file=sys.stderr)
        return 1
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())

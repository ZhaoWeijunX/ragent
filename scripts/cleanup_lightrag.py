#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通过 LightRAG HTTP API 清除知识图谱文档数据。

清理范围:
  1. DELETE /documents —— 官方「清空全部文档」接口
     （文档状态、chunks、实体/关系、图谱侧向量一并清）
  2. 若清空接口不可用，回退：GET /documents 列举全部 id → DELETE /documents/delete_document

默认只做 dry-run；加 --apply 才会真正删除。

说明:
  - 仅走 LightRAG HTTP API，不 DROP PostgreSQL 表、不操作 Docker 卷
  - lightrag_llm_cache 等 PG 表由 LightRAG 自行管理，本脚本不删表
  - 不影响业务向量库 / ES / t_* 业务表
  - 清空后请把 application.yaml 中 rag.graph.type 置 none，并重启后端，避免再次写入

用法示例:
  # 预览
  python scripts/cleanup_lightrag.py

  # 通过 LightRAG API 清空
  python scripts/cleanup_lightrag.py --apply

  # 指定地址 / API Key
  python scripts/cleanup_lightrag.py --apply \\
    --base-url http://127.0.0.1:9621 --api-key ""
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="通过 LightRAG HTTP API 清除知识图谱文档数据")
    p.add_argument(
        "--apply",
        action="store_true",
        help="真正执行清理；默认仅 dry-run",
    )
    p.add_argument(
        "--base-url",
        default="http://127.0.0.1:9621",
        help="LightRAG 基址（默认与 application.yaml 一致）",
    )
    p.add_argument(
        "--api-key",
        default="",
        help="X-API-Key（本地默认留空）",
    )
    p.add_argument(
        "--timeout",
        type=int,
        default=120,
        help="HTTP 超时秒数（清空可能较慢）",
    )
    return p.parse_args()


def http_json(
    method: str,
    url: str,
    *,
    api_key: str = "",
    body: Optional[Dict[str, Any]] = None,
    timeout: int = 120,
) -> tuple[int, Any]:
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if api_key:
        headers["X-API-Key"] = api_key
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            payload: Any = json.loads(raw) if raw.strip() else None
            return resp.status, payload
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(raw) if raw.strip() else {"message": raw}
        except json.JSONDecodeError:
            payload = {"message": raw}
        return e.code, payload
    except urllib.error.URLError as e:
        raise SystemExit(f"无法连接 LightRAG: {url}\n  原因: {e.reason}") from e


def check_health(base_url: str, api_key: str, timeout: int) -> None:
    code, payload = http_json("GET", f"{base_url.rstrip('/')}/health", api_key=api_key, timeout=timeout)
    if code != 200:
        raise SystemExit(f"LightRAG /health 失败: HTTP {code} {payload}")
    status = payload.get("status") if isinstance(payload, dict) else payload
    print(f"[ok] LightRAG 存活: status={status}")


def collect_doc_ids(statuses: Any) -> List[str]:
    ids: List[str] = []
    if not isinstance(statuses, dict):
        return ids
    for group in statuses.values():
        if not isinstance(group, list):
            continue
        for doc in group:
            if not isinstance(doc, dict):
                continue
            doc_id = doc.get("id") or doc.get("doc_id")
            if doc_id:
                ids.append(str(doc_id))
    seen = set()
    out: List[str] = []
    for i in ids:
        if i not in seen:
            seen.add(i)
            out.append(i)
    return out


def list_documents(base_url: str, api_key: str, timeout: int) -> List[str]:
    code, payload = http_json("GET", f"{base_url.rstrip('/')}/documents", api_key=api_key, timeout=timeout)
    if code != 200 or not isinstance(payload, dict):
        print(f"[warn] GET /documents 失败: HTTP {code} {payload}")
        return []
    statuses = payload.get("statuses", payload)
    return collect_doc_ids(statuses)


def clear_via_delete_all(base_url: str, api_key: str, timeout: int) -> bool:
    """优先走官方清空全部接口 DELETE /documents。"""
    code, payload = http_json(
        "DELETE",
        f"{base_url.rstrip('/')}/documents",
        api_key=api_key,
        timeout=timeout,
    )
    if 200 <= code < 300:
        print(f"[ok] DELETE /documents 成功: {payload}")
        return True
    print(f"[warn] DELETE /documents 不可用/失败: HTTP {code} {payload}")
    return False


def clear_via_delete_ids(base_url: str, api_key: str, timeout: int, doc_ids: List[str]) -> None:
    if not doc_ids:
        print("[ok] 无需按 id 删除（文档列表为空）")
        return
    batch = 50
    for i in range(0, len(doc_ids), batch):
        chunk = doc_ids[i : i + batch]
        code, payload = http_json(
            "DELETE",
            f"{base_url.rstrip('/')}/documents/delete_document",
            api_key=api_key,
            body={"doc_ids": chunk, "delete_file": True, "delete_llm_cache": True},
            timeout=timeout,
        )
        if 200 <= code < 300:
            print(f"[ok] 已提交删除 {len(chunk)} 个文档 ({i + 1}-{i + len(chunk)}/{len(doc_ids)}): {payload}")
        else:
            raise SystemExit(f"DELETE /documents/delete_document 失败: HTTP {code} {payload}")


def main() -> int:
    args = parse_args()
    base_url = args.base_url.rstrip("/")
    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"=== LightRAG 图谱清理 ({mode}) ===")
    print(f"base-url = {base_url}")

    check_health(base_url, args.api_key, args.timeout)
    remaining = list_documents(base_url, args.api_key, args.timeout)
    print(f"[info] 当前文档数: {len(remaining)}")
    if remaining:
        preview = remaining[:10]
        more = "" if len(remaining) <= 10 else f" ...(+{len(remaining) - 10})"
        print(f"[info] 文档 id 预览: {preview}{more}")

    if not args.apply:
        print("\n[dry-run] 未做任何删除。确认后请加 --apply。")
        print(
            "\n提醒: 清理后请将 application.yaml 置为:\n"
            "  rag.graph.type: none\n"
            "  rag.graph.ingestion.enabled: false\n"
            "  rag.search.channels.graph.enabled: false\n"
            "并重启后端。"
        )
        return 0

    cleared = clear_via_delete_all(base_url, args.api_key, args.timeout)
    if not cleared:
        clear_via_delete_ids(base_url, args.api_key, args.timeout, remaining)

    after = list_documents(base_url, args.api_key, args.timeout)
    if after:
        print(f"[warn] 清空后仍残留 {len(after)} 个文档，尝试按 id 再删")
        clear_via_delete_ids(base_url, args.api_key, args.timeout, after)
        after = list_documents(base_url, args.api_key, args.timeout)
    print(f"[ok] HTTP 清理后文档数: {len(after)}")
    if after:
        print("[warn] 仍有残留，请检查 LightRAG 服务端日志。")

    print(
        "\n完成。请确认 application.yaml 已关闭图谱，并重启后端，"
        "避免摄取路径再次向 LightRAG 写入。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

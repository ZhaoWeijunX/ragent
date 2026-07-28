#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将 resources/docs/12306-pdf-doc/ 下的 PDF 批量导入知识库，
并使用 feishu-pdf-ingestion-pipeline（MinerU 解析 → Enhancer → 结构分块 → 向量化）处理。

流程:
  1. 登录管理台
  2. 解析 / 校验流水线 feishu-pdf-ingestion-pipeline
  3. 创建或复用知识库（默认：拿个offer-12306 / collection=train12306）
  4. 递归扫描 PDF → 上传（processMode=pipeline）→ 触发分块
  5. 轮询文档状态直到 success / failed

前置条件:
  - 后端已启动（默认 http://localhost:9090）
  - 已导入流水线种子:
      psql -f resources/database/imports/pipelines/feishu-pdf-ingestion-pipeline.sql
  - 已配置 mineru.api-key（PDF 解析依赖 MinerU）

依赖:
  pip install requests

用法示例:
  # 预览将导入的文件
  python scripts/batch_import_12306_pdfs.py --dry-run

  # 全量导入（默认并发 2，MinerU 较慢）
  python scripts/batch_import_12306_pdfs.py

  # 指定已有知识库，跳过同名已存在文档
  python scripts/batch_import_12306_pdfs.py --kb-id <KB_ID> --skip-existing

  # 仅上传不触发分块
  python scripts/batch_import_12306_pdfs.py --upload-only

  # 对已存在但失败的文档重新触发分块
  python scripts/batch_import_12306_pdfs.py --kb-id <KB_ID> --retry-failed

导入完成后，将 kb id / collection_name 填入:
  resources/database/imports/intent-nodes/12306-intent-nodes-import.sql
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

# Windows 控制台默认 GBK，强制 UTF-8 避免中文文件名乱码
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

try:
    import requests
except ImportError:
    print("缺少依赖 requests，请先执行: pip install requests", file=sys.stderr)
    sys.exit(1)

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DOCS_DIR = REPO_ROOT / "resources" / "docs" / "12306-pdf-doc"
DEFAULT_PIPELINE_NAME = "feishu-pdf-ingestion-pipeline"
DEFAULT_PIPELINE_ID = "2080100000000000001"
DEFAULT_KB_NAME = "拿个offer-12306"
DEFAULT_COLLECTION = "train12306"
DEFAULT_EMBEDDING = "qwen-emb-8b"
TERMINAL_STATUSES = {"success", "failed"}


@dataclass
class ImportResult:
    path: str
    doc_id: Optional[str] = None
    status: str = "pending"
    error: Optional[str] = None
    chunk_count: int = 0


@dataclass
class Summary:
    uploaded: int = 0
    skipped: int = 0
    chunk_triggered: int = 0
    success: int = 0
    failed: int = 0
    errors: List[str] = field(default_factory=list)


class RagentClient:
    def __init__(self, base_url: str, timeout: int = 120, token: Optional[str] = None) -> None:
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.timeout = timeout
        if token:
            self.session.headers["Authorization"] = token

    def clone(self) -> "RagentClient":
        """为并发任务创建带相同 token 的独立 Session。"""
        token = self.session.headers.get("Authorization")
        return RagentClient(self.base_url, timeout=self.timeout, token=token)

    def login(self, username: str, password: str) -> str:
        data = self._request(
            "POST",
            "/auth/login",
            json_body={"username": username, "password": password},
        )
        token = data.get("token")
        if not token:
            raise RuntimeError(f"登录响应缺少 token: {data}")
        self.session.headers["Authorization"] = token
        return token

    def find_pipeline(
        self,
        name: str = DEFAULT_PIPELINE_NAME,
        preferred_id: str = DEFAULT_PIPELINE_ID,
    ) -> Dict[str, Any]:
        # 优先按固定种子 ID 取
        try:
            pipe = self._request("GET", f"/ingestion/pipelines/{preferred_id}")
            if pipe and pipe.get("id"):
                return pipe
        except RuntimeError:
            pass

        page = self._request(
            "GET",
            "/ingestion/pipelines",
            params={"pageNo": 1, "pageSize": 100, "keyword": name},
        )
        records = page.get("records") or []
        for item in records:
            if item.get("name") == name:
                return item
        for item in records:
            if name in (item.get("name") or ""):
                return item
        raise RuntimeError(
            f"未找到流水线 `{name}`（也未找到种子 ID {preferred_id}）。"
            f"请先执行: psql -f resources/database/imports/pipelines/feishu-pdf-ingestion-pipeline.sql"
        )

    def find_knowledge_base(self, name: str) -> Optional[Dict[str, Any]]:
        page = self._request(
            "GET",
            "/knowledge-base",
            params={"pageNo": 1, "pageSize": 50, "keyword": name},
        )
        for item in page.get("records") or []:
            if item.get("name") == name:
                return item
        return None

    def get_knowledge_base(self, kb_id: str) -> Dict[str, Any]:
        return self._request("GET", f"/knowledge-base/{kb_id}")

    def create_knowledge_base(
        self,
        name: str,
        collection_name: str,
        embedding_model: str,
    ) -> str:
        kb_id = self._request(
            "POST",
            "/knowledge-base",
            json_body={
                "name": name,
                "collectionName": collection_name,
                "embeddingModel": embedding_model,
            },
        )
        if not kb_id:
            raise RuntimeError("创建知识库失败：响应为空")
        return str(kb_id)

    def list_all_documents(self, kb_id: str) -> List[Dict[str, Any]]:
        docs: List[Dict[str, Any]] = []
        page_no = 1
        page_size = 100
        while True:
            page = self._request(
                "GET",
                f"/knowledge-base/{kb_id}/docs",
                params={"current": page_no, "size": page_size},
            )
            records = page.get("records") or []
            docs.extend(records)
            total = int(page.get("total") or 0)
            if not records or len(docs) >= total:
                break
            page_no += 1
        return docs

    def upload_pdf(
        self,
        kb_id: str,
        file_path: Path,
        pipeline_id: str,
    ) -> Dict[str, Any]:
        mime = mimetypes.guess_type(file_path.name)[0] or "application/pdf"
        with file_path.open("rb") as fh:
            files = {"file": (file_path.name, fh, mime)}
            form = {
                "sourceType": "file",
                "processMode": "pipeline",
                "pipelineId": pipeline_id,
            }
            return self._request(
                "POST",
                f"/knowledge-base/{kb_id}/docs/upload",
                data=form,
                files=files,
                timeout=max(self.timeout, 300),
            )

    def start_chunk(self, doc_id: str) -> None:
        self._request("POST", f"/knowledge-base/docs/{doc_id}/chunk")

    def get_document(self, doc_id: str) -> Dict[str, Any]:
        return self._request("GET", f"/knowledge-base/docs/{doc_id}")

    def wait_document(
        self,
        doc_id: str,
        poll_interval: float,
        timeout_sec: float,
    ) -> Dict[str, Any]:
        deadline = time.time() + timeout_sec
        last: Dict[str, Any] = {}
        while time.time() < deadline:
            last = self.get_document(doc_id)
            status = (last.get("status") or "").lower()
            if status in TERMINAL_STATUSES:
                return last
            time.sleep(poll_interval)
        raise TimeoutError(
            f"等待文档处理超时（{timeout_sec:.0f}s）: docId={doc_id}, "
            f"lastStatus={last.get('status')}"
        )

    def _request(
        self,
        method: str,
        path: str,
        *,
        params: Optional[Dict[str, Any]] = None,
        json_body: Optional[Dict[str, Any]] = None,
        data: Optional[Dict[str, Any]] = None,
        files: Any = None,
        timeout: Optional[int] = None,
    ) -> Any:
        url = f"{self.base_url}{path}"
        try:
            resp = self.session.request(
                method,
                url,
                params=params,
                json=json_body,
                data=data,
                files=files,
                timeout=timeout or self.timeout,
            )
        except requests.RequestException as exc:
            raise RuntimeError(f"请求失败 {method} {path}: {exc}") from exc

        try:
            payload = resp.json()
        except ValueError as exc:
            raise RuntimeError(
                f"非 JSON 响应 {method} {path}: HTTP {resp.status_code} {resp.text[:300]}"
            ) from exc

        if resp.status_code >= 400:
            msg = payload.get("message") if isinstance(payload, dict) else resp.text
            raise RuntimeError(f"HTTP {resp.status_code} {method} {path}: {msg}")

        if isinstance(payload, dict) and "code" in payload:
            if str(payload.get("code")) != "0":
                raise RuntimeError(
                    f"业务失败 {method} {path}: {payload.get('message') or payload}"
                )
            return payload.get("data")
        return payload


def discover_pdfs(docs_dir: Path) -> List[Path]:
    files = sorted(p for p in docs_dir.rglob("*.pdf") if p.is_file())
    return files


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="批量将 12306-pdf-doc PDF 导入知识库（feishu-pdf-ingestion-pipeline）"
    )
    p.add_argument(
        "--base-url",
        default="http://localhost:9090/api/ragent",
        help="API 根路径（默认 http://localhost:9090/api/ragent）",
    )
    p.add_argument("--username", default="admin", help="登录用户名")
    p.add_argument("--password", default="admin", help="登录密码")
    p.add_argument(
        "--docs-dir",
        type=Path,
        default=DEFAULT_DOCS_DIR,
        help=f"PDF 根目录（默认 {DEFAULT_DOCS_DIR}）",
    )
    p.add_argument("--kb-id", default=None, help="已有知识库 ID；不传则按名称创建/复用")
    p.add_argument("--kb-name", default=DEFAULT_KB_NAME, help="知识库名称")
    p.add_argument(
        "--collection-name",
        default=DEFAULT_COLLECTION,
        help="向量 collection 名称（仅新建知识库时生效）",
    )
    p.add_argument(
        "--embedding-model",
        default=DEFAULT_EMBEDDING,
        help="嵌入模型（仅新建知识库时生效）",
    )
    p.add_argument(
        "--pipeline-id",
        default=None,
        help=f"流水线 ID（默认自动解析 {DEFAULT_PIPELINE_NAME}）",
    )
    p.add_argument(
        "--pipeline-name",
        default=DEFAULT_PIPELINE_NAME,
        help="流水线名称",
    )
    p.add_argument(
        "--concurrency",
        type=int,
        default=2,
        help="并发处理数（上传+分块+轮询）；MinerU 建议 1~3",
    )
    p.add_argument(
        "--poll-interval",
        type=float,
        default=5.0,
        help="文档状态轮询间隔（秒）",
    )
    p.add_argument(
        "--wait-timeout",
        type=float,
        default=1800.0,
        help="单文档等待完成超时（秒，默认 30 分钟）",
    )
    p.add_argument(
        "--limit",
        type=int,
        default=0,
        help="只处理前 N 个 PDF（0=全部）",
    )
    p.add_argument(
        "--skip-existing",
        action="store_true",
        help="跳过知识库中同名文档",
    )
    p.add_argument(
        "--upload-only",
        action="store_true",
        help="只上传不触发分块",
    )
    p.add_argument(
        "--retry-failed",
        action="store_true",
        help="对知识库内 status=failed 的同名文档重新触发分块（不重新上传）",
    )
    p.add_argument(
        "--dry-run",
        action="store_true",
        help="仅列出将处理的 PDF，不调用 API",
    )
    p.add_argument(
        "--report",
        type=Path,
        default=None,
        help="将导入结果写入 JSON 文件",
    )
    return p.parse_args()


def resolve_knowledge_base(client: RagentClient, args: argparse.Namespace) -> Dict[str, Any]:
    if args.kb_id:
        kb = client.get_knowledge_base(args.kb_id)
        print(f"[kb] 使用已有知识库: id={kb.get('id')} name={kb.get('name')} "
              f"collection={kb.get('collectionName')}")
        return kb

    existing = client.find_knowledge_base(args.kb_name)
    if existing:
        print(f"[kb] 复用同名知识库: id={existing.get('id')} name={existing.get('name')} "
              f"collection={existing.get('collectionName')}")
        return existing

    kb_id = client.create_knowledge_base(
        name=args.kb_name,
        collection_name=args.collection_name,
        embedding_model=args.embedding_model,
    )
    kb = client.get_knowledge_base(kb_id)
    print(f"[kb] 已创建知识库: id={kb.get('id')} name={kb.get('name')} "
          f"collection={kb.get('collectionName')}")
    return kb


def process_one(
    client: RagentClient,
    kb_id: str,
    pipeline_id: str,
    pdf: Path,
    *,
    existing_by_name: Dict[str, Dict[str, Any]],
    skip_existing: bool,
    upload_only: bool,
    retry_failed: bool,
    poll_interval: float,
    wait_timeout: float,
) -> ImportResult:
    name = pdf.name
    result = ImportResult(path=str(pdf))

    existing = existing_by_name.get(name)
    if existing:
        doc_id = str(existing.get("id"))
        status = (existing.get("status") or "").lower()
        result.doc_id = doc_id

        if retry_failed and status == "failed":
            print(f"[retry] {name} (docId={doc_id})")
            try:
                client.start_chunk(doc_id)
                doc = client.wait_document(doc_id, poll_interval, wait_timeout)
                result.status = (doc.get("status") or "").lower()
                result.chunk_count = int(doc.get("chunkCount") or 0)
                if result.status != "success":
                    result.error = f"重试后状态={result.status}"
            except Exception as exc:  # noqa: BLE001
                result.status = "failed"
                result.error = str(exc)
            return result

        if skip_existing:
            result.status = f"skipped:{status or 'unknown'}"
            result.chunk_count = int(existing.get("chunkCount") or 0)
            return result

        result.status = "failed"
        result.error = f"同名文档已存在（docId={doc_id}, status={status}），请加 --skip-existing 或 --retry-failed"
        return result

    try:
        print(f"[upload] {name}")
        doc = client.upload_pdf(kb_id, pdf, pipeline_id)
        doc_id = str(doc.get("id"))
        result.doc_id = doc_id
        existing_by_name[name] = doc

        if upload_only:
            result.status = "uploaded"
            return result

        print(f"[chunk]  {name} (docId={doc_id})")
        client.start_chunk(doc_id)
        final = client.wait_document(doc_id, poll_interval, wait_timeout)
        result.status = (final.get("status") or "").lower()
        result.chunk_count = int(final.get("chunkCount") or 0)
        if result.status != "success":
            result.error = f"处理结束状态={result.status}"
        else:
            print(f"[ok]     {name} chunks={result.chunk_count}")
    except Exception as exc:  # noqa: BLE001
        result.status = "failed"
        result.error = str(exc)
        print(f"[fail]   {name}: {exc}", file=sys.stderr)

    return result


def main() -> int:
    args = parse_args()
    docs_dir: Path = args.docs_dir
    if not docs_dir.is_dir():
        print(f"文档目录不存在: {docs_dir}", file=sys.stderr)
        return 1

    pdfs = discover_pdfs(docs_dir)
    if args.limit and args.limit > 0:
        pdfs = pdfs[: args.limit]

    print(f"[scan] 目录={docs_dir}")
    print(f"[scan] 发现 PDF {len(pdfs)} 篇")
    if args.dry_run:
        for i, pdf in enumerate(pdfs, 1):
            rel = pdf.relative_to(docs_dir)
            print(f"  {i:3d}. {rel}")
        print("[dry-run] 未调用 API")
        return 0

    if not pdfs:
        print("没有可导入的 PDF", file=sys.stderr)
        return 1

    client = RagentClient(args.base_url)
    print(f"[auth] 登录 {args.username} @ {args.base_url}")
    client.login(args.username, args.password)

    if args.pipeline_id:
        pipeline_id = args.pipeline_id
        print(f"[pipeline] 使用指定 ID={pipeline_id}")
    else:
        pipe = client.find_pipeline(args.pipeline_name, DEFAULT_PIPELINE_ID)
        pipeline_id = str(pipe.get("id"))
        print(f"[pipeline] name={pipe.get('name')} id={pipeline_id}")

    kb = resolve_knowledge_base(client, args)
    kb_id = str(kb.get("id"))
    collection = kb.get("collectionName")

    existing_docs = client.list_all_documents(kb_id)
    existing_by_name: Dict[str, Dict[str, Any]] = {}
    for doc in existing_docs:
        name = doc.get("docName") or ""
        if name and name not in existing_by_name:
            existing_by_name[name] = doc
    print(f"[kb] 已有文档 {len(existing_by_name)} 篇")

    concurrency = max(1, args.concurrency)
    summary = Summary()
    results: List[ImportResult] = []

    def _worker(pdf: Path) -> ImportResult:
        # 每任务独立 Session，避免并发下共享 Session 的竞态
        worker_client = client.clone()
        return process_one(
            worker_client,
            kb_id,
            pipeline_id,
            pdf,
            existing_by_name=existing_by_name,
            skip_existing=args.skip_existing,
            upload_only=args.upload_only,
            retry_failed=args.retry_failed,
            poll_interval=args.poll_interval,
            wait_timeout=args.wait_timeout,
        )

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(_worker, pdf) for pdf in pdfs]
        for fut in as_completed(futures):
            item = fut.result()
            results.append(item)
            status = item.status
            if status.startswith("skipped"):
                summary.skipped += 1
            elif status == "uploaded":
                summary.uploaded += 1
            elif status == "success":
                summary.uploaded += 1
                summary.chunk_triggered += 1
                summary.success += 1
            elif status == "failed":
                summary.failed += 1
                if item.error:
                    summary.errors.append(f"{Path(item.path).name}: {item.error}")
            else:
                summary.uploaded += 1
                if status not in {"pending"}:
                    summary.chunk_triggered += 1

    results.sort(key=lambda r: r.path)
    print("\n========== 导入摘要 ==========")
    print(f"知识库 ID:        {kb_id}")
    print(f"知识库名称:      {kb.get('name')}")
    print(f"collection:       {collection}")
    print(f"pipelineId:       {pipeline_id}")
    print(f"扫描 PDF:         {len(pdfs)}")
    print(f"跳过:             {summary.skipped}")
    print(f"成功:             {summary.success}")
    print(f"失败:             {summary.failed}")
    if args.upload_only:
        print(f"仅上传:           {summary.uploaded}")
    if summary.errors:
        print("--- 失败明细 ---")
        for err in summary.errors[:30]:
            print(f"  - {err}")
        if len(summary.errors) > 30:
            print(f"  ... 另有 {len(summary.errors) - 30} 条")

    print("\n下一步：将下列占位符写入 12306-intent-nodes-import.sql 后执行 SQL：")
    print(f"  __KB_ID_12306__      -> {kb_id}")
    print(f"  __COLLECTION_12306__ -> {collection}")
    print("然后: redis-cli DEL ragent:intent:tree")

    if args.report:
        report = {
            "kbId": kb_id,
            "kbName": kb.get("name"),
            "collectionName": collection,
            "pipelineId": pipeline_id,
            "summary": {
                "scanned": len(pdfs),
                "skipped": summary.skipped,
                "success": summary.success,
                "failed": summary.failed,
                "uploaded": summary.uploaded,
            },
            "results": [
                {
                    "path": r.path,
                    "docId": r.doc_id,
                    "status": r.status,
                    "chunkCount": r.chunk_count,
                    "error": r.error,
                }
                for r in results
            ],
        }
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\n[report] 已写入 {args.report}")

    return 1 if summary.failed else 0


if __name__ == "__main__":
    sys.exit(main())

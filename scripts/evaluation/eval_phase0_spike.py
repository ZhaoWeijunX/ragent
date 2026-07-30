#!/usr/bin/env python3
"""阶段 0 Spike：验证双路径录制可行性（只读，不改 ragent 业务代码）。

验证项：
1. /rag/v3/chat SSE：解析 meta / message(think|response) / finish|reject|cancel / done|error
2. TTFT：首个 type=response 且 delta 非空的耗时
3. /rag/eval：取回检索证据
4. 用 taskId 轮询 /rag/traces/runs?taskId= 取得 traceId

用法（仓库根目录，需 ragent 已启动且账号可登录）:

  set RAGENT_BASE_URL=http://localhost:9090/api/ragent
  set RAGENT_USERNAME=admin
  set RAGENT_PASSWORD=***
  python scripts/evaluation/eval_phase0_spike.py
  python scripts/evaluation/eval_phase0_spike.py --limit 3
  python scripts/evaluation/eval_phase0_spike.py --question "你是谁"

可选环境变量：
  RAGENT_TOKEN          若已有 token，跳过登录
  EVAL_SET_PATH         默认尝试 ragenteval 的 20 条集；不存在则用内置单条
  SPIKE_OUT_DIR         默认 docs/evaluation/fixtures/spike-out
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Iterator

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OUT = ROOT / "docs" / "evaluation" / "fixtures" / "spike-out"
DEFAULT_EVAL_SET_CANDIDATES = [
    Path(r"D:\code\ragenteval\eval\rag\dataset\eval_set_v1.jsonl"),
    ROOT / "docs" / "evaluation" / "examples" / "eval-sample.example.json",
]


def env(name: str, default: str | None = None) -> str | None:
    v = os.environ.get(name)
    if v is None or v == "":
        return default
    return v


def http_json(method: str, url: str, headers: dict[str, str], body: dict[str, Any] | None = None, timeout: float = 60) -> Any:
    data = None
    req_headers = dict(headers)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        req_headers.setdefault("Content-Type", "application/json")
    req = urllib.request.Request(url, data=data, headers=req_headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read().decode("utf-8")
        return json.loads(raw) if raw else None


def login(base_url: str, username: str, password: str) -> str:
    payload = http_json(
        "POST",
        f"{base_url}/auth/login",
        {"Accept": "application/json"},
        {"username": username, "password": password},
        timeout=30,
    )
    # Result<LoginVO> 常见形态：{code,data:{token}} 或直接 token 字段
    if isinstance(payload, dict):
        data = payload.get("data") if "data" in payload else payload
        if isinstance(data, dict):
            for key in ("token", "accessToken", "saToken"):
                if data.get(key):
                    return str(data[key])
        if payload.get("token"):
            return str(payload["token"])
    raise RuntimeError(f"登录响应无法解析 token: {payload!r}")


def parse_sse(byte_iter: Iterator[bytes]) -> Iterator[tuple[str, str]]:
    """自研 SSE 解析，避免按行缓冲导致跨 chunk 丢事件。"""
    buffer = ""
    event_name = "message"
    data_lines: list[str] = []

    def flush() -> tuple[str, str] | None:
        nonlocal event_name, data_lines
        if not data_lines:
            event_name = "message"
            return None
        data = "\n".join(data_lines)
        name = event_name
        event_name = "message"
        data_lines = []
        return name, data

    for chunk in byte_iter:
        buffer += chunk.decode("utf-8", errors="replace")
        while "\n" in buffer:
            line, buffer = buffer.split("\n", 1)
            line = line.rstrip("\r")
            if line == "":
                item = flush()
                if item:
                    yield item
                continue
            if line.startswith(":"):
                continue
            if line.startswith("event:"):
                event_name = line[6:].lstrip()
            elif line.startswith("data:"):
                data_lines.append(line[5:].lstrip() if line.startswith("data: ") else line[5:])
    item = flush()
    if item:
        yield item


def stream_chat(base_url: str, token: str, question: str, timeout: float = 180) -> dict[str, Any]:
    params = urllib.parse.urlencode({"question": question})
    url = f"{base_url}/rag/v3/chat?{params}"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": token,
            "Accept": "text/event-stream",
        },
        method="GET",
    )
    state: dict[str, Any] = {
        "response": "",
        "thinking": "",
        "events": [],
        "meta": None,
        "final_status": "unknown",
        "error": None,
        "first_token_ms": None,
        "conversation_id": None,
        "task_id": None,
    }
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            def chunks() -> Iterator[bytes]:
                while True:
                    block = resp.read(512)
                    if not block:
                        break
                    yield block

            for event_name, data_str in parse_sse(chunks()):
                state["events"].append(event_name)
                payload: Any = data_str
                if data_str and data_str != "[DONE]":
                    try:
                        payload = json.loads(data_str)
                    except json.JSONDecodeError:
                        pass

                if event_name == "meta" and isinstance(payload, dict):
                    state["meta"] = payload
                    state["conversation_id"] = payload.get("conversationId")
                    state["task_id"] = payload.get("taskId")
                elif event_name == "message" and isinstance(payload, dict):
                    delta_type = payload.get("type")
                    content = payload.get("delta") or ""
                    if delta_type == "response":
                        if state["first_token_ms"] is None and content:
                            state["first_token_ms"] = int((time.time() - start) * 1000)
                        state["response"] += content
                    elif delta_type == "think":
                        state["thinking"] += content
                elif event_name == "finish":
                    state["final_status"] = "success"
                elif event_name == "reject":
                    state["final_status"] = "refused"
                    state["error"] = payload if isinstance(payload, str) else json.dumps(payload, ensure_ascii=False)
                elif event_name == "cancel":
                    state["final_status"] = "cancelled"
                elif event_name == "error":
                    state["final_status"] = "error"
                    state["error"] = payload if isinstance(payload, str) else json.dumps(payload, ensure_ascii=False)
                elif event_name == "done":
                    break
    except Exception as exc:  # noqa: BLE001
        state["error"] = str(exc)
        if state["final_status"] == "unknown":
            state["final_status"] = "error"
    state["latency_ms"] = int((time.time() - start) * 1000)
    state["unique_events"] = sorted(set(state["events"]))
    # ADR：thinking 不落库；spike 仅统计长度
    state["thinking_chars"] = len(state["thinking"])
    state["thinking"] = None
    return state


def fetch_eval(base_url: str, token: str, question: str, timeout: float = 120) -> dict[str, Any]:
    params = urllib.parse.urlencode({"question": question})
    url = f"{base_url}/rag/eval?{params}"
    start = time.time()
    try:
        payload = http_json("GET", url, {"Authorization": token, "Accept": "application/json"}, timeout=timeout)
        data = payload.get("data") if isinstance(payload, dict) and "data" in payload else payload
        if not isinstance(data, dict):
            return {"ok": False, "error": f"unexpected payload: {payload!r}", "eval_latency_ms": int((time.time() - start) * 1000)}
        data = dict(data)
        data["ok"] = True
        data["eval_latency_ms"] = int((time.time() - start) * 1000)
        return data
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "error": str(exc), "eval_latency_ms": int((time.time() - start) * 1000)}


def resolve_trace_id(
    base_url: str,
    token: str,
    task_id: str,
    retries: int = 10,
    interval_ms: int = 300,
) -> dict[str, Any]:
    """验证 taskId → traceId 时序窗口。"""
    attempts = []
    for i in range(retries):
        params = urllib.parse.urlencode({"taskId": task_id, "current": 1, "size": 5})
        url = f"{base_url}/rag/traces/runs?{params}"
        t0 = time.time()
        try:
            payload = http_json("GET", url, {"Authorization": token, "Accept": "application/json"}, timeout=30)
            data = payload.get("data") if isinstance(payload, dict) else None
            records = []
            if isinstance(data, dict):
                records = data.get("records") or data.get("list") or []
            trace_id = None
            if records:
                trace_id = records[0].get("traceId")
            elapsed = int((time.time() - t0) * 1000)
            attempts.append({"attempt": i + 1, "elapsed_ms": elapsed, "hit": bool(trace_id), "trace_id": trace_id})
            if trace_id:
                return {
                    "ok": True,
                    "trace_id": trace_id,
                    "attempts": attempts,
                    "resolved_after_ms": sum(a["elapsed_ms"] for a in attempts) + i * interval_ms,
                    "recommended_retry": {
                        "retries": retries,
                        "interval_ms": interval_ms,
                        "observed_success_attempt": i + 1,
                    },
                }
        except Exception as exc:  # noqa: BLE001
            attempts.append({"attempt": i + 1, "error": str(exc)})
        time.sleep(interval_ms / 1000.0)
    return {"ok": False, "trace_id": None, "attempts": attempts}


def load_questions(limit: int, explicit: str | None, eval_set: str | None) -> list[dict[str, str]]:
    if explicit:
        return [{"query_id": "SPIKE-1", "query": explicit}]
    path: Path | None = Path(eval_set) if eval_set else None
    if path is None:
        for cand in DEFAULT_EVAL_SET_CANDIDATES:
            if cand.exists():
                path = cand
                break
    if path is None:
        return [{"query_id": "SPIKE-1", "query": "你是谁？"}]
    if path.suffix == ".jsonl":
        rows = []
        with path.open(encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                obj = json.loads(line)
                rows.append({"query_id": obj.get("query_id") or obj.get("queryId"), "query": obj.get("query")})
                if len(rows) >= limit:
                    break
        return rows
    obj = json.loads(path.read_text(encoding="utf-8"))
    return [{"query_id": obj.get("queryId") or "SPIKE-1", "query": obj.get("query") or "你是谁？"}]


def build_record(sample: dict[str, str], chat: dict[str, Any], ev: dict[str, Any], trace: dict[str, Any]) -> dict[str, Any]:
    intent_leaf_ids = ev.get("intentLeafIds") or []
    return {
        "schemaVersion": "1.0.0",
        "queryId": sample["query_id"],
        "userInput": sample["query"],
        "reference": "",
        "referenceDocIds": [],
        "referenceDocIdsNice": [],
        "intentL1": "",
        "intentL2": "",
        "difficulty": "medium",
        "requiresRag": True,
        "response": chat.get("response") or "",
        "thinking": None,
        "latencyMs": chat.get("latency_ms") or 0,
        "firstTokenMs": chat.get("first_token_ms"),
        "finalStatus": chat.get("final_status") or "unknown",
        "error": chat.get("error"),
        "conversationId": chat.get("conversation_id"),
        "taskId": chat.get("task_id"),
        "traceId": trace.get("trace_id"),
        "retrievedDocIds": ev.get("retrievedDocIds") or [],
        "retrievedDocIdsRaw": ev.get("retrievedDocIds") or [],
        "retrievedChunkIds": ev.get("retrievedChunkIds") or [],
        "retrievedContexts": ev.get("retrievedContexts") or [],
        "retrievedContextDocIds": ev.get("retrievedContextDocIds") or [],
        "intentPred": intent_leaf_ids[0] if intent_leaf_ids else None,
        "intentPredAll": [x for x in intent_leaf_ids if x],
        "hasKb": ev.get("hasKb"),
        "hasMcp": ev.get("hasMcp"),
        "retrievalSkipped": bool(ev.get("retrievalSkipped")),
        "skipReason": ev.get("skipReason"),
        "evalLatencyMs": ev.get("eval_latency_ms"),
        "evidenceSource": "DUAL_PATH_CHAT_AND_EVAL",
        "spike": {
            "chat_events": chat.get("unique_events"),
            "eval_ok": ev.get("ok"),
            "trace_ok": trace.get("ok"),
            "trace_attempts": trace.get("attempts"),
            "thinking_chars_observed": chat.get("thinking_chars"),
            "note": "双路径证据可能与 Chat 实际上下文不完全一致",
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="RAG 评测工作台阶段 0 spike")
    parser.add_argument("--limit", type=int, default=1, help="样本数，默认 1；可调到 20")
    parser.add_argument("--question", type=str, default=None)
    parser.add_argument("--eval-set", type=str, default=env("EVAL_SET_PATH"))
    parser.add_argument("--out-dir", type=str, default=env("SPIKE_OUT_DIR", str(DEFAULT_OUT)))
    parser.add_argument("--trace-retries", type=int, default=10)
    parser.add_argument("--trace-interval-ms", type=int, default=300)
    args = parser.parse_args()

    base_url = env("RAGENT_BASE_URL", "http://localhost:9090/api/ragent")
    assert base_url
    token = env("RAGENT_TOKEN")
    username = env("RAGENT_USERNAME")
    password = env("RAGENT_PASSWORD")

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    try:
        if not token:
            if not username or not password:
                print("ERROR: 请设置 RAGENT_TOKEN，或 RAGENT_USERNAME + RAGENT_PASSWORD", file=sys.stderr)
                print("（无运行中服务时，可先跑: python scripts/evaluation/eval_phase0_validate_schemas.py）", file=sys.stderr)
                return 2
            token = login(base_url, username, password)
            print(f"login ok, token length={len(token)}")
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: 登录失败（服务未启动？）: {exc}", file=sys.stderr)
        return 2

    samples = load_questions(args.limit, args.question, args.eval_set)
    results = []
    ok_count = 0

    for idx, sample in enumerate(samples, 1):
        print(f"[{idx}/{len(samples)}] {sample['query_id']}: {sample['query'][:40]}...")
        chat = stream_chat(base_url, token, sample["query"])
        ev = fetch_eval(base_url, token, sample["query"])
        trace: dict[str, Any] = {"ok": False, "trace_id": None}
        if chat.get("task_id"):
            trace = resolve_trace_id(
                base_url,
                token,
                str(chat["task_id"]),
                retries=args.trace_retries,
                interval_ms=args.trace_interval_ms,
            )
        else:
            trace = {"ok": False, "trace_id": None, "error": "meta 未返回 taskId"}

        record = build_record(sample, chat, ev, trace)
        results.append(record)

        chat_ok = chat.get("final_status") in ("success", "refused") and chat.get("task_id")
        eval_ok = bool(ev.get("ok"))
        trace_ok = bool(trace.get("ok"))
        ttft_ok = chat.get("first_token_ms") is not None or chat.get("final_status") == "refused"
        passed = bool(chat_ok and eval_ok and trace_ok and ttft_ok)
        if passed:
            ok_count += 1
        print(
            f"  chat={chat.get('final_status')} ttft={chat.get('first_token_ms')} "
            f"eval={eval_ok} docs={len(record['retrievedDocIds'])} "
            f"trace={record.get('traceId')} pass={passed}"
        )

    stamp = time.strftime("%Y%m%d_%H%M%S")
    out_path = out_dir / f"spike_records_{stamp}.jsonl"
    with out_path.open("w", encoding="utf-8") as f:
        for row in results:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    summary = {
        "base_url": base_url,
        "n": len(results),
        "passed": ok_count,
        "failed": len(results) - ok_count,
        "out_path": str(out_path),
        "ttft_definition": "first non-empty message delta with type=response",
        "trace_strategy": "poll GET /rag/traces/runs?taskId= after chat meta",
        "evidence_source": "DUAL_PATH_CHAT_AND_EVAL",
        "code_change": "none (read-only spike)",
        "recommended_runner_defaults": {
            "trace_retries": args.trace_retries,
            "trace_interval_ms": args.trace_interval_ms,
        },
    }
    summary_path = out_dir / f"spike_summary_{stamp}.json"
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if ok_count == 0:
        print("FAIL: 无样本通过 spike", file=sys.stderr)
        return 1
    if ok_count < len(results):
        print("PARTIAL: 部分样本通过（可接受为 PARTIAL_SUCCESS 口径验证）", file=sys.stderr)
        return 0
    print("OK: spike 通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())

'''
PS D:\code\ragent> python scripts/eval_phase0_spike.py --limit 1
login ok, token length=32
[1/1] F1-01: 我的扫地机充不进电了...
  chat=success ttft=7840 eval=True docs=4 trace=2082669712286130176 pass=True
{
  "base_url": "http://localhost:9090/api/ragent",
  "n": 1,
  "passed": 1,
  "failed": 0,
  "out_path": "D:\\code\\ragent\\docs\\evaluation\\fixtures\\spike-out\\spike_records_20260730_112920.jsonl",
  "ttft_definition": "first non-empty message delta with type=response",
  "trace_strategy": "poll GET /rag/traces/runs?taskId= after chat meta",
  "evidence_source": "DUAL_PATH_CHAT_AND_EVAL",
  "code_change": "none (read-only spike)",
  "recommended_runner_defaults": {
    "trace_retries": 10,
    "trace_interval_ms": 300
  }
}
OK: spike 通过
'''

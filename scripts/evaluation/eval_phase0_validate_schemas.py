#!/usr/bin/env python3
"""阶段 0：离线校验 EvalSample / EvalRecord / MetricResult 契约。

不依赖 ragent 进程、不改业务代码。验证：
1. 官方 camelCase 样例符合 JSON Schema（若安装 jsonschema）或内置必填检查
2. camelCase ↔ snake_case 双向转换后字段完整
3. 非法样例被拒绝

用法（仓库根目录）:
  python scripts/evaluation/eval_phase0_validate_schemas.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "docs" / "evaluation"
SCHEMA_DIR = DOC / "schemas"
EXAMPLE_DIR = DOC / "examples"
FIXTURE_DIR = DOC / "fixtures"

SAMPLE_TO_PY = {
    "schemaVersion": "schema_version",
    "queryId": "query_id",
    "query": "query",
    "intentL1": "intent_l1",
    "intentL2": "intent_l2",
    "difficulty": "difficulty",
    "requiresRag": "requires_rag",
    "expectedDocIds": "expected_doc_ids",
    "niceToHaveDocIds": "expected_doc_ids_nice",
    "groundTruth": "ground_truth",
    "expectedAnswerType": "expected_answer_type",
    "trapType": "trap_type",
    "enabledMetrics": "eval_metrics",
    "tags": "tags",
    "metadata": "metadata",
}
SAMPLE_FROM_PY = {v: k for k, v in SAMPLE_TO_PY.items()}

RECORD_TO_PY = {
    "schemaVersion": "schema_version",
    "queryId": "query_id",
    "userInput": "user_input",
    "reference": "reference",
    "referenceDocIds": "reference_doc_ids",
    "referenceDocIdsNice": "reference_doc_ids_nice",
    "intentL1": "intent_l1",
    "intentL2": "intent_l2",
    "difficulty": "difficulty",
    "requiresRag": "requires_rag",
    "response": "response",
    "thinking": "thinking",
    "latencyMs": "latency_ms",
    "firstTokenMs": "first_token_ms",
    "finalStatus": "final_status",
    "error": "error",
    "conversationId": "conversation_id",
    "taskId": "task_id",
    "traceId": "trace_id",
    "retrievedDocIds": "retrieved_doc_ids",
    "retrievedDocIdsRaw": "retrieved_doc_ids_raw",
    "retrievedChunkIds": "retrieved_chunk_ids",
    "retrievedContexts": "retrieved_contexts",
    "retrievedContextDocIds": "retrieved_context_doc_ids",
    "intentPred": "intent_pred",
    "intentPredAll": "intent_pred_all",
    "hasKb": "has_kb",
    "hasMcp": "has_mcp",
    "retrievalSkipped": "retrieval_skipped",
    "skipReason": "skip_reason",
    "evalLatencyMs": "eval_latency_ms",
    "evidenceSource": "evidence_source",
    "chatStartedAt": "chat_started_at",
    "chatFinishedAt": "chat_finished_at",
    "evalStartedAt": "eval_started_at",
    "evalFinishedAt": "eval_finished_at",
}
RECORD_FROM_PY = {v: k for k, v in RECORD_TO_PY.items()}

METRIC_TO_PY = {
    "schemaVersion": "schema_version",
    "name": "name",
    "algorithmVersion": "algorithm_version",
    "overall": "overall",
    "byIntentL1": "by_intent_l1",
    "byIntentL2": "by_intent_l2",
    "byDifficulty": "by_difficulty",
    "perSample": "per_sample",
    "meta": "meta",
    "isPct": "is_pct",
}
METRIC_FROM_PY = {v: k for k, v in METRIC_TO_PY.items()}


def load_json(path: Path) -> Any:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def convert(obj: dict[str, Any], mapping: dict[str, str]) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for k, v in obj.items():
        if k not in mapping:
            raise KeyError(f"未映射字段: {k}")
        out[mapping[k]] = v
    return out


def roundtrip(obj: dict[str, Any], to_py: dict[str, str], from_py: dict[str, str]) -> dict[str, Any]:
    return convert(convert(obj, to_py), from_py)


def basic_required_check(obj: dict[str, Any], required: list[str], path: str) -> list[str]:
    errors = []
    for key in required:
        if key not in obj:
            errors.append(f"{path}: 缺少必填字段 {key}")
        elif obj[key] is None:
            errors.append(f"{path}: 必填字段 {key} 为 null")
        elif key in ("query", "queryId", "userInput") and isinstance(obj[key], str) and not obj[key].strip():
            errors.append(f"{path}: 字段 {key} 不能为空字符串")
    if "schemaVersion" in required and obj.get("schemaVersion") != "1.0.0":
        errors.append(f"{path}: schemaVersion 必须为 1.0.0")
    return errors


def try_jsonschema(schema: dict[str, Any], instance: dict[str, Any], path: str) -> list[str]:
    try:
        import jsonschema  # type: ignore
    except ImportError:
        return []
    validator = jsonschema.Draft202012Validator(schema)
    return [f"{path}: {e.message}" for e in sorted(validator.iter_errors(instance), key=lambda e: list(e.path))]


def assert_core_equal(left: dict[str, Any], right: dict[str, Any], keys: list[str], label: str) -> list[str]:
    errors = []
    for key in keys:
        if left.get(key) != right.get(key):
            errors.append(f"{label}: {key} 不一致: {left.get(key)!r} vs {right.get(key)!r}")
    return errors


def main() -> int:
    errors: list[str] = []

    sample_schema = load_json(SCHEMA_DIR / "eval-sample.schema.json")
    record_schema = load_json(SCHEMA_DIR / "eval-record.schema.json")
    metric_schema = load_json(SCHEMA_DIR / "metric-result.schema.json")

    sample = load_json(EXAMPLE_DIR / "eval-sample.example.json")
    record = load_json(EXAMPLE_DIR / "eval-record.example.json")
    metric = load_json(EXAMPLE_DIR / "metric-result.example.json")

    sample_py = load_json(EXAMPLE_DIR / "eval-sample.python.example.json")
    record_py = load_json(EXAMPLE_DIR / "eval-record.python.example.json")
    metric_py = load_json(EXAMPLE_DIR / "metric-result.python.example.json")

    invalid = load_json(FIXTURE_DIR / "eval-sample.invalid.json")

    errors += basic_required_check(sample, ["schemaVersion", "queryId", "query", "requiresRag"], "sample")
    errors += basic_required_check(
        record,
        [
            "schemaVersion",
            "queryId",
            "userInput",
            "requiresRag",
            "response",
            "finalStatus",
            "latencyMs",
            "retrievedDocIds",
            "retrievedContexts",
            "evidenceSource",
        ],
        "record",
    )
    errors += basic_required_check(metric, ["schemaVersion", "name", "algorithmVersion", "overall", "isPct"], "metric")

    errors += try_jsonschema(sample_schema, sample, "sample/jsonschema")
    errors += try_jsonschema(record_schema, record, "record/jsonschema")
    errors += try_jsonschema(metric_schema, metric, "metric/jsonschema")

    invalid_errs = basic_required_check(invalid, ["schemaVersion", "queryId", "query", "requiresRag"], "invalid")
    invalid_errs += try_jsonschema(sample_schema, invalid, "invalid/jsonschema")
    if not invalid_errs:
        errors.append("invalid fixture 未被拒绝（期望失败）")

    try:
        back_sample = roundtrip(sample, SAMPLE_TO_PY, SAMPLE_FROM_PY)
        for k, v in sample.items():
            if back_sample.get(k) != v:
                errors.append(f"sample roundtrip 不一致: {k}")
        py_from_api = convert(sample, SAMPLE_TO_PY)
        errors += assert_core_equal(
            py_from_api,
            sample_py,
            ["query_id", "query", "requires_rag", "expected_doc_ids", "expected_doc_ids_nice", "eval_metrics"],
            "sample API→PY vs python.example",
        )
    except Exception as exc:  # noqa: BLE001
        errors.append(f"sample roundtrip 失败: {exc}")

    try:
        back_record = roundtrip(record, RECORD_TO_PY, RECORD_FROM_PY)
        for k, v in record.items():
            if back_record.get(k) != v:
                errors.append(f"record roundtrip 不一致: {k}")
        py_rec = convert(record, RECORD_TO_PY)
        errors += assert_core_equal(
            py_rec,
            record_py,
            ["query_id", "user_input", "first_token_ms", "evidence_source", "eval_latency_ms", "final_status"],
            "record API→PY vs python.example",
        )
    except Exception as exc:  # noqa: BLE001
        errors.append(f"record roundtrip 失败: {exc}")

    try:
        back_metric = roundtrip(metric, METRIC_TO_PY, METRIC_FROM_PY)
        for k, v in metric.items():
            if back_metric.get(k) != v:
                errors.append(f"metric roundtrip 不一致: {k}")
        py_m = convert(metric, METRIC_TO_PY)
        errors += assert_core_equal(
            py_m,
            metric_py,
            ["name", "algorithm_version", "overall", "is_pct"],
            "metric API→PY vs python.example",
        )
    except Exception as exc:  # noqa: BLE001
        errors.append(f"metric roundtrip 失败: {exc}")

    if errors:
        print("FAIL: 发现以下问题：")
        for e in errors:
            print(f"  - {e}")
        return 1

    print("OK: Schema examples validated; camelCase <-> snake_case roundtrip passed")
    print(f"  schemas: {SCHEMA_DIR}")
    print(f"  examples: {EXAMPLE_DIR}")
    try:
        import jsonschema  # noqa: F401

        print("  jsonschema: enabled")
    except ImportError:
        print("  jsonschema: not installed (builtin required-field checks used; optional: pip install jsonschema)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

'''
PS D:\code\ragent> python scripts/eval_phase0_validate_schemas.py
OK: Schema examples validated; camelCase <-> snake_case roundtrip passed
  schemas: D:\code\ragent\docs\evaluation\schemas
  examples: D:\code\ragent\docs\evaluation\examples
  jsonschema: not installed (builtin required-field checks used; optional: pip install jsonschema)
'''

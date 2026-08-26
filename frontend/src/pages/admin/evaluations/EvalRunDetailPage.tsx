import { useEffect, useRef, useState, Fragment, type ReactNode } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Ban, ChevronDown, ChevronRight, Download, ExternalLink, GitCompareArrows, Loader2, RefreshCw, RotateCcw, Calculator } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { RelativeTime } from "@/components/RelativeTime";
import { EvalStatusBadge } from "@/pages/admin/evaluations/EvalStatusBadge";
import type {
  EvalMetricReport,
  EvalRagasJudgeModelCandidate,
  EvalRecord,
  EvalRun,
  EvalSampleFailure,
  EvalScoreBatch,
  PageResult
} from "@/services/evaluationService";
import {
  cancelRagasBatch,
  cancelRun,
  exportRunReport,
  getRecord,
  getRun,
  getRunMetrics,
  listRagasJudgeModels,
  listScoreBatches,
  pageRecords,
  pageRuns,
  ragasRescoreRun,
  resumeRun,
  rerunRecord
} from "@/services/evaluationService";
import { getEvalWorkbenchErrorMessage as getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 20;
const ACTIVE = new Set(["PENDING", "RECORDING", "DETERMINISTIC_SCORING", "RAGAS_SCORING", "REPORTING"]);
const RESUMABLE = new Set(["FAILED", "PARTIAL_SUCCESS", "CANCELLED"]);
const TERMINAL = new Set(["COMPLETED", "PARTIAL_SUCCESS", "FAILED", "CANCELLED"]);
const RAGAS_BATCH_ACTIVE = new Set(["PENDING", "RUNNING"]);
const RAGAS_BATCH_TERMINAL = new Set(["COMPLETED", "PARTIAL_SUCCESS", "FAILED"]);

function enabledCandidates(list: EvalRagasJudgeModelCandidate[] | undefined | null): EvalRagasJudgeModelCandidate[] {
  return (list || []).filter((c) => c?.id && c.enabled !== false);
}

function pickDefaultId(
  defaultModel: string | null | undefined,
  candidates: EvalRagasJudgeModelCandidate[]
): string {
  if (candidates.length === 0) return "";
  if (defaultModel && candidates.some((c) => c.id === defaultModel)) {
    return defaultModel;
  }
  return candidates[0].id;
}

function modelOptionLabel(m: EvalRagasJudgeModelCandidate): string {
  if (m.provider && m.model) return `${m.provider} · ${m.model}`;
  return m.model || m.id;
}

function isRagasServiceInterrupted(message?: string | null) {
  const m = (message || "").toLowerCase();
  return (
    m.includes("中断") ||
    m.includes("不可达") ||
    m.includes("丢失") ||
    m.includes("connection") ||
    m.includes("refused") ||
    m.includes("connect timed out") ||
    m.includes("reset") ||
    m.includes("404") ||
    m.includes("not found") ||
    m.includes("restart") ||
    m.includes("超时") ||
    m.includes("timeout")
  );
}

function formatRagasCostHint(batch: EvalScoreBatch | null | undefined): string | null {
  if (!batch) return null;
  // token_usage 目前由评分服务占位回传（常为 0），暂不展示；仅在有真实估算费用时显示
  if (batch.estimatedCost == null || batch.estimatedCost === "") return null;
  const n = typeof batch.estimatedCost === "number" ? batch.estimatedCost : Number(batch.estimatedCost);
  if (Number.isNaN(n)) return null;
  return `约 $${n.toFixed(4)}`;
}

function formatRagasJudgeHint(batch: EvalScoreBatch | null | undefined): string | null {
  const snap = batch?.judgeConfigSnapshot;
  if (!snap) return null;
  const chatProvider = typeof snap.chatProvider === "string" ? snap.chatProvider : "";
  const chatModel = typeof snap.chatModel === "string" ? snap.chatModel : typeof snap.chatModelId === "string" ? snap.chatModelId : "";
  const embProvider = typeof snap.embeddingProvider === "string" ? snap.embeddingProvider : "";
  const embModel =
    typeof snap.embeddingModel === "string"
      ? snap.embeddingModel
      : typeof snap.embeddingModelId === "string"
        ? snap.embeddingModelId
        : "";
  const chat = [chatProvider, chatModel].filter(Boolean).join(" · ");
  const emb = [embProvider, embModel].filter(Boolean).join(" · ");
  if (!chat && !emb) return null;
  const parts: string[] = [];
  if (chat) parts.push(`Judge ${chat}`);
  if (emb) parts.push(`Embedding ${emb}`);
  return parts.join(" · ");
}

function describeRagasFailure(message?: string | null) {
  if (isRagasServiceInterrupted(message)) {
    return message
      ? `RAGAS 评分服务运行中中断：${message}`
      : "RAGAS 评分服务运行中中断或不可达";
  }
  return message
    ? `RAGAS 评分失败（已降级）：${message}`
    : "RAGAS 评分失败（已降级）";
}

/** 对齐 ragenteval report.md「自建指标」顺序与中文名 */
const DETERMINISTIC_METRIC_ROWS: { key: string; label: string; pct: boolean }[] = [
  { key: "intent_top1", label: "意图 Top-1 准确率", pct: true },
  { key: "hit@1", label: "Hit@1", pct: true },
  { key: "hit@3", label: "Hit@3", pct: true },
  { key: "hit@5", label: "Hit@5", pct: true },
  { key: "hit@10", label: "Hit@10", pct: true },
  { key: "recall@5", label: "Recall@5", pct: true },
  { key: "recall_inclusive@5", label: "Recall@5 (含 nice)", pct: true },
  { key: "recall@10", label: "Recall@10", pct: true },
  { key: "mrr@10", label: "MRR@10", pct: true },
  { key: "refusal_when_required", label: "误拒率（requires_rag 却 0 召回）", pct: true },
  { key: "fallback_when_required", label: "答案兜底率", pct: true },
  { key: "over_retrieval_rate", label: "过召回率（!requires_rag 却走 RAG）", pct: true },
  { key: "ttft_p50_ms", label: "首字延迟 P50 (ms)", pct: false },
  { key: "ttft_mean_ms", label: "首字延迟均值 (ms)", pct: false },
  { key: "total_mean_ms", label: "整流均值 (ms, 仅供参考)", pct: false }
];

/** 对齐 ragenteval report.md「RAGAS LLM-as-judge」 */
const RAGAS_METRIC_ROWS: { key: string; label: string; pct: boolean }[] = [
  { key: "faithfulness", label: "Faithfulness", pct: true },
  { key: "answer_relevancy", label: "Answer Relevancy", pct: true },
  { key: "answer_correctness", label: "Answer Correctness", pct: true },
  { key: "context_precision", label: "Context Precision", pct: true },
  { key: "context_recall", label: "Context Recall", pct: true }
];

/** Intent L2 / 难度切片固定列（无数据时展示 "-"） */
const SLICE_METRIC_COLUMNS: { key: string; label: string; pct: boolean }[] = [
  { key: "hit@5", label: "Hit@5", pct: true },
  { key: "recall@5", label: "Recall@5", pct: true },
  { key: "mrr@10", label: "MRR@10", pct: true },
  { key: "intent_top1", label: "Intent Top-1", pct: true },
  { key: "faithfulness", label: "Faithfulness", pct: true },
  { key: "answer_correctness", label: "Answer Correctness", pct: true }
];

const DIFFICULTY_ORDER = ["easy", "medium", "hard"];

function sortDifficultyKeys(keys: string[]) {
  return [...keys].sort((a, b) => {
    const ia = DIFFICULTY_ORDER.indexOf(a);
    const ib = DIFFICULTY_ORDER.indexOf(b);
    if (ia >= 0 && ib >= 0) return ia - ib;
    if (ia >= 0) return -1;
    if (ib >= 0) return 1;
    return a.localeCompare(b);
  });
}

function collectSliceKeys(
  metricByName: Map<string, NonNullable<EvalMetricReport["metrics"]>[number]>,
  dim: "byIntentL2" | "byDifficulty"
) {
  return Array.from(
    new Set(
      SLICE_METRIC_COLUMNS.flatMap((col) => Object.keys(metricByName.get(col.key)?.[dim] || {}))
    )
  );
}

function buildMetricByName(report: EvalMetricReport | null, ragas: EvalMetricReport | null) {
  const metricByName = new Map<string, NonNullable<EvalMetricReport["metrics"]>[number]>();
  for (const m of report?.metrics || []) metricByName.set(m.name, m);
  for (const m of ragas?.metrics || []) metricByName.set(m.name, m);
  return metricByName;
}

function docDiff(expected: string[] | undefined, retrieved: string[] | undefined) {
  const exp = new Set(expected || []);
  const ret = new Set(retrieved || []);
  const missed = [...exp].filter((id) => !ret.has(id));
  const extra = [...ret].filter((id) => !exp.has(id));
  return { missed, extra };
}

export function EvalRunDetailPage() {
  const { runId = "" } = useParams();
  const navigate = useNavigate();
  const [run, setRun] = useState<EvalRun | null>(null);
  const [records, setRecords] = useState<PageResult<EvalRecord> | null>(null);
  const [report, setReport] = useState<EvalMetricReport | null>(null);
  const [ragasReport, setRagasReport] = useState<EvalMetricReport | null>(null);
  const [scoreBatches, setScoreBatches] = useState<EvalScoreBatch[]>([]);
  const [pageNo, setPageNo] = useState(1);
  const [statusFilter, setStatusFilter] = useState("all");
  const [keyword, setKeyword] = useState("");
  const [searchKeyword, setSearchKeyword] = useState("");
  const [loading, setLoading] = useState(true);
  const [ragasSubmitting, setRagasSubmitting] = useState(false);
  const [ragasDialogOpen, setRagasDialogOpen] = useState(false);
  const [ragasModelsLoading, setRagasModelsLoading] = useState(false);
  const [ragasCancelling, setRagasCancelling] = useState(false);
  const [chatModels, setChatModels] = useState<EvalRagasJudgeModelCandidate[]>([]);
  const [embeddingModels, setEmbeddingModels] = useState<EvalRagasJudgeModelCandidate[]>([]);
  const [selectedChatModelId, setSelectedChatModelId] = useState("");
  const [selectedEmbeddingModelId, setSelectedEmbeddingModelId] = useState("");
  const [expandedFailures, setExpandedFailures] = useState<Set<string>>(new Set());
  const [compareDialogOpen, setCompareDialogOpen] = useState(false);
  const [compareCandidates, setCompareCandidates] = useState<EvalRun[]>([]);
  const [compareLoading, setCompareLoading] = useState(false);
  const [selectedBaselineId, setSelectedBaselineId] = useState("");
  const [recordSheetOpen, setRecordSheetOpen] = useState(false);
  const [recordDetail, setRecordDetail] = useState<EvalRecord | null>(null);
  const [recordDetailLoading, setRecordDetailLoading] = useState(false);
  const [recordRerunning, setRecordRerunning] = useState(false);
  const ragasStatusRef = useRef<string | null>(null);
  const initialRecordsRunIdRef = useRef<string | undefined>(undefined);

  const activeRagasBatch =
    scoreBatches.find((b) => b.scoreType === "RAGAS" && RAGAS_BATCH_ACTIVE.has(b.status || "")) || null;
  const latestRagasBatch = scoreBatches.find((b) => b.scoreType === "RAGAS") || null;
  const failedRagasBatch =
    latestRagasBatch && latestRagasBatch.status === "FAILED" ? latestRagasBatch : null;
  const ragasBusy = ragasSubmitting || !!activeRagasBatch;

  const loadRun = async () => {
    if (!runId) return;
    try {
      setRun(await getRun(runId));
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 Run 失败"));
    }
  };

  const loadRecords = async (current = pageNo, status = statusFilter, kw = keyword) => {
    if (!runId) return;
    try {
      setRecords(
        await pageRecords(runId, current, PAGE_SIZE, {
          status: status === "all" ? undefined : status,
          keyword: kw || undefined
        })
      );
    } catch (error) {
      toast.error(getErrorMessage(error, "加载样本记录失败"));
    }
  };

  const loadMetrics = async () => {
    if (!runId) return;
    try {
      setReport(await getRunMetrics(runId, undefined, "DETERMINISTIC"));
    } catch {
      setReport(null);
    }
    try {
      setRagasReport(await getRunMetrics(runId, undefined, "RAGAS"));
    } catch {
      setRagasReport(null);
    }
  };

  const loadBatches = async () => {
    if (!runId) return;
    try {
      setScoreBatches(await listScoreBatches(runId));
    } catch {
      /* ignore — 批次列表非关键路径 */
    }
  };

  const refresh = async () => {
    setLoading(true);
    try {
      await Promise.all([loadRun(), loadRecords(), loadMetrics(), loadBatches()]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, [runId]);

  useEffect(() => {
    if (initialRecordsRunIdRef.current !== runId) {
      initialRecordsRunIdRef.current = runId;
      return;
    }
    void loadRecords();
  }, [pageNo, statusFilter, keyword]);

  useEffect(() => {
    if (!run || !ACTIVE.has(run.status)) return;
    const timer = setInterval(() => {
      loadRun();
      loadRecords();
      loadBatches();
    }, 3000);
    return () => clearInterval(timer);
  }, [run?.status, runId, pageNo, statusFilter, keyword]);

  useEffect(() => {
    if (!activeRagasBatch) return;
    const timer = setInterval(() => {
      loadBatches();
    }, 10000);
    return () => clearInterval(timer);
  }, [activeRagasBatch?.id, runId]);

  useEffect(() => {
    if (run && TERMINAL.has(run.status) && !activeRagasBatch) {
      loadMetrics();
    }
  }, [run?.status, activeRagasBatch?.id]);

  useEffect(() => {
    const batch = latestRagasBatch;
    if (!batch) return;
    const prev = ragasStatusRef.current;
    const next = batch.status || null;
    if (prev && RAGAS_BATCH_ACTIVE.has(prev) && next && RAGAS_BATCH_TERMINAL.has(next)) {
      if (next === "FAILED") {
        const text = describeRagasFailure(batch.errorMessage);
        if (isRagasServiceInterrupted(batch.errorMessage)) {
          toast.error(text);
        } else {
          toast.warning(text);
        }
      } else if (next === "PARTIAL_SUCCESS") {
        toast.warning("RAGAS 评分部分成功，请查看下方指标");
      } else {
        toast.success("RAGAS 评分完成");
      }
      loadMetrics();
    }
    ragasStatusRef.current = next;
  }, [latestRagasBatch?.id, latestRagasBatch?.status, latestRagasBatch?.errorMessage]);

  const handleCancel = async () => {
    try {
      await cancelRun(runId);
      toast.success("已请求取消（协作式，已录制数据保留）");
      await loadRun();
    } catch (error) {
      toast.error(getErrorMessage(error, "取消失败"));
    }
  };

  const handleResume = async () => {
    try {
      await resumeRun(runId);
      toast.success("已提交失败样本重试");
      await refresh();
    } catch (error) {
      toast.error(getErrorMessage(error, "重试失败"));
    }
  };

  const openRecordDetail = async (recordId: string, fallback?: EvalRecord | null) => {
    if (!recordId) return;
    const fromList = fallback || records?.records?.find((r) => r.id === recordId) || null;
    setRecordSheetOpen(true);
    setRecordDetail(fromList);
    setRecordDetailLoading(true);
    try {
      setRecordDetail(await getRecord(recordId));
    } catch (error) {
      if (!fromList) {
        toast.error(getErrorMessage(error, "加载样本详情失败"));
        setRecordSheetOpen(false);
      } else {
        toast.warning(getErrorMessage(error, "刷新样本详情失败，已展示列表缓存"));
      }
    } finally {
      setRecordDetailLoading(false);
    }
  };

  const handleRerunRecord = async () => {
    if (!runId || !recordDetail?.id || !run || recordRerunning) return;
    if (!TERMINAL.has(run.status)) {
      toast.error("仅终态 Run 可单样本重跑");
      return;
    }
    const recordId = recordDetail.id;
    setRecordRerunning(true);
    try {
      await rerunRecord(runId, recordId);
      toast.success("已提交单样本重跑，完成后将自动更新自建指标");
      const deadline = Date.now() + 15 * 60 * 1000;
      let sawActive = false;
      while (Date.now() < deadline) {
        await new Promise((r) => setTimeout(r, 1500));
        const latest = await getRun(runId);
        setRun(latest);
        if (ACTIVE.has(latest.status || "")) {
          sawActive = true;
        }
        if (sawActive && TERMINAL.has(latest.status || "")) {
          await refresh();
          try {
            const detail = await getRecord(recordId);
            setRecordDetail((prev) => (prev?.id === recordId ? detail : prev));
          } catch {
            /* 列表刷新已覆盖主要状态 */
          }
          toast.warning(
            "单样本重跑完成，自建指标已更新。录制结果已变化，请自行点击「RAGAS 评分」重新计算",
            { duration: 12000 }
          );
          return;
        }
      }
      toast.warning("单样本重跑仍在进行，请稍后刷新页面查看结果");
    } catch (error) {
      toast.error(getErrorMessage(error, "单样本重跑失败"));
    } finally {
      setRecordRerunning(false);
    }
  };

  const openRagasDialog = async () => {
    if (ragasBusy) return;
    setRagasDialogOpen(true);
    setRagasModelsLoading(true);
    try {
      const models = await listRagasJudgeModels();
      const chats = enabledCandidates(models.chat?.candidates);
      const embs = enabledCandidates(models.embedding?.candidates);
      setChatModels(chats);
      setEmbeddingModels(embs);
      const ragasPref =
        run?.configSnapshot?.ragas && typeof run.configSnapshot.ragas === "object"
          ? (run.configSnapshot.ragas as Record<string, unknown>)
          : null;
      const prefChat =
        typeof ragasPref?.chatModelId === "string" ? ragasPref.chatModelId : null;
      const prefEmb =
        typeof ragasPref?.embeddingModelId === "string" ? ragasPref.embeddingModelId : null;
      setSelectedChatModelId((prev) => {
        if (prev && chats.some((c) => c.id === prev)) return prev;
        if (prefChat && chats.some((c) => c.id === prefChat)) return prefChat;
        return pickDefaultId(models.chat?.defaultModel, chats);
      });
      setSelectedEmbeddingModelId((prev) => {
        if (prev && embs.some((c) => c.id === prev)) return prev;
        if (prefEmb && embs.some((c) => c.id === prefEmb)) return prefEmb;
        return pickDefaultId(models.embedding?.defaultModel, embs);
      });
    } catch (error) {
      toast.error(getErrorMessage(error, "加载模型列表失败"));
      setRagasDialogOpen(false);
    } finally {
      setRagasModelsLoading(false);
    }
  };

  const openCompareDialog = async () => {
    if (!run?.datasetVersionId) {
      toast.error("当前 Run 缺少数据集版本，无法对比");
      return;
    }
    setCompareDialogOpen(true);
    setCompareLoading(true);
    try {
      const page = await pageRuns(1, 50, { datasetVersionId: run.datasetVersionId });
      const candidates = (page.records || []).filter((r) => r.id !== runId);
      setCompareCandidates(candidates);
      const preferred =
        (run.baselineRunId && candidates.find((c) => c.id === run.baselineRunId)?.id) ||
        candidates[0]?.id ||
        "";
      setSelectedBaselineId(preferred);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载同版本 Run 失败"));
      setCompareDialogOpen(false);
    } finally {
      setCompareLoading(false);
    }
  };

  const handleRagasRescore = async () => {
    if (ragasBusy) return;
    if (!selectedChatModelId || !selectedEmbeddingModelId) {
      toast.error("请选择语言模型与嵌入模型");
      return;
    }
    setRagasDialogOpen(false);
    setRagasSubmitting(true);
    try {
      const batchId = await ragasRescoreRun(runId, {
        chatModelId: selectedChatModelId,
        embeddingModelId: selectedEmbeddingModelId
      });
      const batches = await listScoreBatches(runId);
      setScoreBatches(batches);
      const batch = batches.find((b) => b.id === batchId);
      const status = batch?.status || "";
      if (status === "FAILED") {
        const text = describeRagasFailure(batch?.errorMessage);
        if (isRagasServiceInterrupted(batch?.errorMessage)) {
          toast.error(text);
        } else {
          toast.warning(text);
        }
        ragasStatusRef.current = status;
        return;
      }
      if (RAGAS_BATCH_TERMINAL.has(status)) {
        toast.success(status === "PARTIAL_SUCCESS" ? "RAGAS 评分部分成功" : "RAGAS 评分完成");
        ragasStatusRef.current = status;
        await loadMetrics();
        return;
      }
      toast.success("已提交 RAGAS 异步评分，下方可查看进度");
      ragasStatusRef.current = status || "RUNNING";
    } catch (error) {
      toast.error(getErrorMessage(error, "RAGAS 评分失败"), { duration: 8000 });
    } finally {
      setRagasSubmitting(false);
    }
  };

  const handleCancelRagas = async () => {
    if (!activeRagasBatch || ragasCancelling) return;
    setRagasCancelling(true);
    try {
      await cancelRagasBatch(runId, activeRagasBatch.id);
      toast.success("已请求取消 RAGAS 评分");
      const batches = await listScoreBatches(runId);
      setScoreBatches(batches);
      ragasStatusRef.current = "FAILED";
    } catch (error) {
      toast.error(getErrorMessage(error, "取消 RAGAS 失败"));
    } finally {
      setRagasCancelling(false);
    }
  };

  const handleExport = async (format: "json" | "csv") => {
    try {
      const blob = await exportRunReport(runId, format);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `eval-run-${runId}.${format}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      toast.error(getErrorMessage(error, "导出失败"));
    }
  };

  const formatScore = (value?: number | null, pct?: boolean | null) => {
    if (value == null || Number.isNaN(value)) return "-";
    if (pct) return `${(value * 100).toFixed(1)}%`;
    return String(Math.round(value));
  };

  const failureSummary = (f: EvalSampleFailure) => {
    const details = f.failureDetails || [];
    if (details.length > 0) return details.map((d) => d.message).join("；");
    return (f.failureReasons || []).join(", ") || "-";
  };

  const toggleFailure = (recordId: string) => {
    setExpandedFailures((prev) => {
      const next = new Set(prev);
      if (next.has(recordId)) next.delete(recordId);
      else next.add(recordId);
      return next;
    });
  };

  if (!run && loading) {
    return <div className="text-muted-foreground">加载中…</div>;
  }
  if (!run) {
    return <div className="text-muted-foreground">Run 不存在</div>;
  }

  return (
    <div className="admin-page space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Button variant="ghost" size="sm" onClick={() => navigate("/admin/evaluations/runs")}>
          <ArrowLeft className="mr-1 h-4 w-4" />
          返回列表
        </Button>
        <h1 className="admin-page-title text-xl">{run.name}</h1>
        <EvalStatusBadge kind="run" status={run.status} />
        <div className="ml-auto flex flex-wrap gap-2">
          <Button variant="outline" size="sm" onClick={refresh}>
            <RefreshCw className="mr-1 h-4 w-4" />
            刷新
          </Button>
          <Button variant="outline" size="sm" onClick={() => void openCompareDialog()}>
            <GitCompareArrows className="mr-1 h-4 w-4" />
            对比
          </Button>
          {TERMINAL.has(run.status) && (
            <>
              <Button
                variant="outline"
                size="sm"
                onClick={openRagasDialog}
                disabled={ragasBusy || !run.ragasEnabled}
                title={
                  run.ragasEnabled
                    ? undefined
                    : "创建 Run 时未启用 RAGAS，无法在此评分"
                }
              >
                {ragasBusy ? (
                  <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                ) : (
                  <Calculator className="mr-1 h-4 w-4" />
                )}
                {ragasBusy ? "RAGAS 评分中…" : "RAGAS 评分"}
              </Button>
              <Button variant="outline" size="sm" onClick={() => handleExport("json")}>
                <Download className="mr-1 h-4 w-4" />
                导出 JSON
              </Button>
              <Button variant="outline" size="sm" onClick={() => handleExport("csv")}>
                <Download className="mr-1 h-4 w-4" />
                导出 CSV
              </Button>
            </>
          )}
          {ACTIVE.has(run.status) && (
            <Button variant="destructive" size="sm" onClick={handleCancel}>
              <Ban className="mr-1 h-4 w-4" />
              取消
            </Button>
          )}
          {RESUMABLE.has(run.status) && (
            <Button size="sm" onClick={handleResume}>
              <RotateCcw className="mr-1 h-4 w-4" />
              重试失败样本
            </Button>
          )}
        </div>
      </div>

      <div className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
        {run.dualPathDisclaimer ||
          "双路径证据提示：旁路 (/rag/eval) 检索证据可能与真实 Chat 回答所用上下文不完全一致。"}
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">录制进度</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="mb-2 text-2xl font-semibold">{run.progress ?? 0}%</div>
            <div className="h-2 w-full overflow-hidden rounded bg-slate-100">
              <div
                className="h-full rounded bg-sky-500 transition-all"
                style={{ width: `${Math.min(100, Math.max(0, run.progress ?? 0))}%` }}
              />
            </div>
            <p className="mt-2 text-xs text-muted-foreground">
              样例 {(run.successCount ?? 0) + (run.failedCount ?? 0)}/{run.totalCount ?? 0}
              {run.currentPhase ? ` · 阶段 ${run.currentPhase}` : ""}
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">成功 / 失败</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-semibold">
              {run.successCount} / {run.failedCount}
            </div>
            {run.cancelRequested ? <p className="mt-2 text-xs text-amber-700">已请求取消</p> : null}
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">数据集版本</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="font-medium">{run.datasetName || "-"}</div>
            <div className="text-sm text-muted-foreground">{run.datasetVersion || run.datasetVersionId}</div>
            {run.datasetVersionId ? (
              <Link
                className="mt-2 inline-flex items-center text-xs text-sky-700 hover:underline"
                to={`/admin/evaluations/dataset-versions/${run.datasetVersionId}`}
              >
                查看版本 <ExternalLink className="ml-1 h-3 w-3" />
              </Link>
            ) : null}
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">时间</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1 text-sm">
            <div>
              开始：<RelativeTime value={run.startedAt} />
            </div>
            <div>
              结束：<RelativeTime value={run.finishedAt} />
            </div>
            {run.errorMessage ? <div className="text-rose-600">{run.errorMessage}</div> : null}
          </CardContent>
        </Card>
      </div>

      {run.configSnapshot && Object.keys(run.configSnapshot).length > 0 ? (
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">配置快照</CardTitle>
            <CardDescription>
              创建时冻结 · schema {String(run.configSnapshot.schemaVersion || "-")} ·{" "}
              {String(run.configSnapshot.frozenAt || "-")}
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-2 text-sm sm:grid-cols-2 lg:grid-cols-4">
            <div>
              <div className="text-xs text-muted-foreground">Chat 档位</div>
              <div className="font-mono text-xs">
                {String(
                  (run.configSnapshot.model as Record<string, unknown> | undefined)?.resolvedTier ?? "-"
                )}
              </div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground">Embedding</div>
              <div className="font-mono text-xs">
                {String(
                  (run.configSnapshot.embedding as Record<string, unknown> | undefined)?.defaultModelId ??
                    "-"
                )}
              </div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground">检索 TopK / RRF-k</div>
              <div className="font-mono text-xs">
                {String(
                  (run.configSnapshot.retrieval as Record<string, unknown> | undefined)?.defaultTopK ?? "-"
                )}{" "}
                /{" "}
                {String(
                  (
                    (run.configSnapshot.retrieval as Record<string, unknown> | undefined)?.fusion as
                      | Record<string, unknown>
                      | undefined
                  )?.rrfK ?? "-"
                )}
              </div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground">知识库存指纹</div>
              <div
                className="truncate font-mono text-xs"
                title={String(
                  (run.configSnapshot.knowledgeSnapshot as Record<string, unknown> | undefined)
                    ?.fingerprint ?? "-"
                )}
              >
                {(() => {
                  const fp = String(
                    (run.configSnapshot.knowledgeSnapshot as Record<string, unknown> | undefined)
                      ?.fingerprint ?? "-"
                  );
                  return fp === "-" ? "-" : `${fp.slice(0, 16)}…`;
                })()}
              </div>
            </div>
          </CardContent>
        </Card>
      ) : null}

      {report ? (
        <Card>
          <CardHeader>
            <CardTitle>自建指标</CardTitle>
            <CardDescription>
              批次 {report.batchId} · {report.algorithmVersion}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-3">
              <h3 className="text-base font-semibold text-slate-900">指标明细</h3>
              <div className="overflow-hidden rounded-md border border-slate-200">
                <Table className="w-full table-fixed">
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      <TableHead className="text-center">指标</TableHead>
                      <TableHead className="text-center">数值</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {DETERMINISTIC_METRIC_ROWS.map((row) => {
                      const metric = (report.metrics || []).find((m) => m.name === row.key);
                      const value = metric?.overall;
                      return (
                        <TableRow key={row.key}>
                          <TableCell className="text-center text-sm">{row.label}</TableCell>
                          <TableCell className="text-center font-medium tabular-nums">
                            {value == null ? "-" : formatScore(value, row.pct)}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
            </div>
            {(() => {
              const metricByName = buildMetricByName(report, ragasReport);
              const l2Keys = collectSliceKeys(metricByName, "byIntentL2").sort();
              if (l2Keys.length === 0) return null;
              return (
                <div className="space-y-3 border-t border-slate-100 pt-4">
                  <h3 className="text-base font-semibold text-slate-900">Intent L2 切片</h3>
                  <Table className="min-w-[720px]">
                    <TableHeader>
                      <TableRow>
                        <TableHead>Intent L2</TableHead>
                        {SLICE_METRIC_COLUMNS.map((col) => (
                          <TableHead key={col.key}>{col.label}</TableHead>
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {l2Keys.map((key) => (
                        <TableRow key={key}>
                          <TableCell className="font-mono text-xs">{key}</TableCell>
                          {SLICE_METRIC_COLUMNS.map((col) => {
                            const metric = metricByName.get(col.key);
                            const value = metric?.byIntentL2?.[key];
                            return (
                              <TableCell key={col.key}>
                                {value == null ? "-" : formatScore(value, col.pct)}
                              </TableCell>
                            );
                          })}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              );
            })()}
            {(() => {
              const metricByName = buildMetricByName(report, ragasReport);
              const diffKeys = sortDifficultyKeys(collectSliceKeys(metricByName, "byDifficulty"));
              if (diffKeys.length === 0) return null;
              return (
                <div className="space-y-3 border-t border-slate-100 pt-4">
                  <h3 className="text-base font-semibold text-slate-900">难度切片</h3>
                  <Table className="min-w-[720px]">
                    <TableHeader>
                      <TableRow>
                        <TableHead>Difficulty</TableHead>
                        {SLICE_METRIC_COLUMNS.map((col) => (
                          <TableHead key={col.key}>{col.label}</TableHead>
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {diffKeys.map((key) => (
                        <TableRow key={key}>
                          <TableCell className="font-mono text-xs">{key}</TableCell>
                          {SLICE_METRIC_COLUMNS.map((col) => {
                            const metric = metricByName.get(col.key);
                            const value = metric?.byDifficulty?.[key];
                            return (
                              <TableCell key={col.key}>
                                {value == null ? "-" : formatScore(value, col.pct)}
                              </TableCell>
                            );
                          })}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              );
            })()}
            <div className="space-y-3 border-t border-slate-100 pt-4">
              <div>
                <h3 className="text-base font-semibold text-slate-900">RAGAS LLM-as-judge</h3>
                <p className="mt-1 text-xs text-muted-foreground">
                  {/*ID-based Recall@K 与 RAGAS Context Recall 口径不同，不可直接替换。*/}
                  {activeRagasBatch
                    ? ` 批次 ${activeRagasBatch.id} · ${activeRagasBatch.status}${
                        activeRagasBatch.externalJobId ? ` · job ${activeRagasBatch.externalJobId}` : ""
                      }`
                    : ragasReport
                      ? ` 批次 ${ragasReport.batchId} · ${ragasReport.status} · ${ragasReport.algorithmVersion || "-"}`
                      : " 暂无 RAGAS 批次（需创建时勾选「启用 RAGAS」，且 ragent.eval.ragas.enabled=true）"}
                  {!activeRagasBatch && (() => {
                    const hint = formatRagasCostHint(latestRagasBatch);
                    return hint ? ` · ${hint}` : "";
                  })()}
                </p>
                {(() => {
                  const judgeHint = formatRagasJudgeHint(activeRagasBatch || latestRagasBatch);
                  return judgeHint ? (
                    <p className="mt-1 text-xs text-slate-600">{judgeHint}</p>
                  ) : null;
                })()}
              </div>
              {activeRagasBatch ? (
                <div className="rounded-md border border-violet-200 bg-violet-50/60 px-3 py-2 text-sm text-violet-900">
                  {(() => {
                    const total = activeRagasBatch.progressTotal ?? activeRagasBatch.sampleCount ?? 0;
                    const skipped = activeRagasBatch.progressSkipped ?? 0;
                    const evaluable =
                      activeRagasBatch.progressEvaluable ??
                      (total > 0 ? Math.max(0, total - skipped) : 0);
                    const workTotal = activeRagasBatch.progressWorkTotal ?? 0;
                    const workCompleted = workTotal > 0
                      ? Math.min(workTotal, activeRagasBatch.progressWorkCompleted ?? 0)
                      : (activeRagasBatch.progressWorkCompleted ?? 0);
                    const useWork = workTotal > 0;
                    const pct = useWork
                      ? Math.min(100, Math.round((workCompleted * 100) / workTotal))
                      : 0;
                    const waiting = !useWork;
                    const costHint = formatRagasCostHint(activeRagasBatch);
                    return (
                      <>
                        <div className="flex items-center justify-between gap-3">
                          <span className="font-medium">评分进行中...</span>
                          <span className="shrink-0 tabular-nums text-xs text-violet-700/80">
                            {useWork
                              ? `评分项 ${workCompleted} / ${workTotal}`
                              : "等待外部服务…"}
                          </span>
                        </div>
                        <div
                          className={
                            waiting
                              ? "mt-2 h-1.5 overflow-hidden rounded-full bg-violet-100 animate-pulse"
                              : "mt-2 h-1.5 overflow-hidden rounded-full bg-violet-100"
                          }
                        >
                          <div
                            className="h-full rounded-full bg-violet-500 transition-all duration-500"
                            style={{ width: `${waiting ? 0 : pct}%` }}
                          />
                        </div>
                        <div className="mt-1.5 flex items-center justify-between gap-3">
                          <p className="min-w-0 text-xs text-violet-700/80">
                            送评 {total} · 可评 {evaluable} · 跳过 {skipped}
                            {activeRagasBatch.progressFailed
                              ? ` · 失败 ${activeRagasBatch.progressFailed}`
                              : ""}
                            {costHint ? ` · ${costHint}` : ""}
                          </p>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="h-6 shrink-0 rounded-md px-2 text-xs font-normal text-violet-800/60 hover:bg-rose-50 hover:text-rose-700"
                            disabled={ragasCancelling}
                            onClick={handleCancelRagas}
                          >
                            {ragasCancelling ? (
                              <Loader2 className="h-3 w-3 animate-spin" />
                            ) : (
                              <Ban className="h-3 w-3" />
                            )}
                            {ragasCancelling ? "取消中" : "取消评分"}
                          </Button>
                        </div>
                      </>
                    );
                  })()}
                </div>
              ) : null}
              {failedRagasBatch ? (
                <div className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                  <div className="font-medium">
                    {(failedRagasBatch.errorMessage || "").includes("用户取消")
                      ? "RAGAS 评分已取消"
                      : isRagasServiceInterrupted(failedRagasBatch.errorMessage)
                        ? "RAGAS 评分服务运行中中断"
                        : "RAGAS 评分失败"}
                  </div>
                  <p className="mt-1 text-xs text-rose-700/90">
                    {failedRagasBatch.errorMessage || "未知错误"}
                    。修复服务后可再次点击「RAGAS 评分」。
                  </p>
                </div>
              ) : null}
              <div className="overflow-hidden rounded-md border border-slate-200">
                <Table className="w-full table-fixed">
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      <TableHead className="text-center">指标</TableHead>
                      <TableHead className="text-center">数值</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {RAGAS_METRIC_ROWS.map((row) => {
                      const metric = (ragasReport?.metrics || []).find((m) => m.name === row.key);
                      const value = metric?.overall;
                      return (
                        <TableRow key={row.key}>
                          <TableCell className="text-center text-sm">{row.label}</TableCell>
                          <TableCell className="text-center font-medium tabular-nums">
                            {value == null ? "-" : formatScore(value, row.pct)}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
              {ragasReport?.status === "FAILED" && !failedRagasBatch ? (
                <p className="text-sm text-rose-600">
                  RAGAS 批次失败不影响自建指标
                  {ragasReport.status ? `（status=${ragasReport.status}）` : ""}
                </p>
              ) : null}
            </div>
            {(report.failures || []).length > 0 ? (
              <div className="space-y-3 border-t border-slate-100 pt-4">
                <h3 className="text-base font-semibold text-slate-900">
                  失败样本
                  <span className="ml-2 text-sm font-normal text-muted-foreground">
                    {report.failures.length} 条
                  </span>
                </h3>
                <Table className="min-w-[720px]">
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-8" />
                      <TableHead>queryId</TableHead>
                      <TableHead>失败原因</TableHead>
                      <TableHead>Trace</TableHead>
                      <TableHead className="w-[72px]" />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {report.failures.slice(0, 50).map((f) => {
                      const open = expandedFailures.has(f.recordId);
                      return (
                        <Fragment key={f.recordId}>
                          <TableRow
                            className="cursor-pointer"
                            onClick={() => toggleFailure(f.recordId)}
                          >
                            <TableCell className="w-8 px-2">
                              {open ? (
                                <ChevronDown className="h-4 w-4 text-slate-500" />
                              ) : (
                                <ChevronRight className="h-4 w-4 text-slate-500" />
                              )}
                            </TableCell>
                            <TableCell className="font-mono text-xs">{f.queryId || "-"}</TableCell>
                            <TableCell className="max-w-lg text-xs text-slate-800">{failureSummary(f)}</TableCell>
                            <TableCell onClick={(e) => e.stopPropagation()}>
                              {f.traceId ? (
                                <Link
                                  className="text-sky-700 hover:underline"
                                  to={`/admin/traces/${encodeURIComponent(f.traceId)}`}
                                >
                                  打开
                                </Link>
                              ) : (
                                "-"
                              )}
                            </TableCell>
                            <TableCell onClick={(e) => e.stopPropagation()}>
                              <Button
                                variant="ghost"
                                size="sm"
                                className="h-7 px-2 text-xs"
                                onClick={() => void openRecordDetail(f.recordId)}
                              >
                                详情
                              </Button>
                            </TableCell>
                          </TableRow>
                          {open ? (
                            <TableRow className="bg-slate-50/80 hover:bg-slate-50/80">
                              <TableCell colSpan={5} className="px-4 py-3">
                                <dl className="grid gap-3 md:grid-cols-2">
                                  <div className="rounded-md border border-slate-200/80 bg-white px-3 py-2.5">
                                    <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                                      问题
                                    </dt>
                                    <dd className="mt-1.5 whitespace-pre-wrap text-sm leading-relaxed text-slate-900">
                                      {f.question || "-"}
                                    </dd>
                                  </div>
                                  <div className="rounded-md border border-slate-200/80 bg-white px-3 py-2.5">
                                    <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                                      原因明细
                                    </dt>
                                    <dd className="mt-1.5">
                                      <ul className="space-y-1.5">
                                        {(f.failureDetails || []).map((d) => (
                                          <li key={d.code} className="text-sm text-slate-900">
                                            <span>{d.message}</span>
                                            <span className="ml-2 font-mono text-[11px] text-slate-400">
                                              {d.code}
                                            </span>
                                          </li>
                                        ))}
                                        {(f.failureDetails || []).length === 0
                                          ? (f.failureReasons || []).map((code) => (
                                              <li key={code} className="font-mono text-sm text-slate-900">
                                                {code}
                                              </li>
                                            ))
                                          : null}
                                      </ul>
                                    </dd>
                                  </div>
                                  <div className="rounded-md border border-slate-200/80 bg-white px-3 py-2.5">
                                    <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                                      期望意图 / 预测意图
                                    </dt>
                                    <dd className="mt-1.5 font-mono text-sm text-slate-900">
                                      {f.intentL2 || "-"} / {f.intentPred || "-"}
                                    </dd>
                                  </div>
                                  <div className="rounded-md border border-slate-200/80 bg-white px-3 py-2.5">
                                    <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                                      状态
                                    </dt>
                                    <dd className="mt-1.5 text-sm text-slate-900">
                                      <span className="font-medium">{f.status || "-"}</span>
                                      {f.errorMessage ? (
                                        <span className="mt-1 block text-rose-600">{f.errorMessage}</span>
                                      ) : null}
                                    </dd>
                                  </div>
                                  <div className="rounded-md border border-slate-200/80 bg-white px-3 py-2.5">
                                    <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                                      期望文档
                                    </dt>
                                    <dd className="mt-1.5 break-all font-mono text-xs leading-relaxed text-slate-900">
                                      {(f.expectedDocIds || []).join(", ") || "-"}
                                    </dd>
                                  </div>
                                  <div className="rounded-md border border-slate-200/80 bg-white px-3 py-2.5">
                                    <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                                      召回文档
                                    </dt>
                                    <dd className="mt-1.5 break-all font-mono text-xs leading-relaxed text-slate-900">
                                      {(f.retrievedDocIds || []).join(", ") || "-"}
                                    </dd>
                                  </div>
                                  <div className="rounded-md border border-rose-100 bg-rose-50/40 px-3 py-2.5">
                                    <dt className="text-[11px] font-semibold uppercase tracking-wide text-rose-400">
                                      未召回（miss）
                                    </dt>
                                    <dd className="mt-1.5 break-all font-mono text-xs leading-relaxed text-rose-800">
                                      {(f.missedDocIds || []).join(", ") || "-"}
                                    </dd>
                                  </div>
                                  <div className="rounded-md border border-amber-100 bg-amber-50/40 px-3 py-2.5">
                                    <dt className="text-[11px] font-semibold uppercase tracking-wide text-amber-500">
                                      多召回（extra）
                                    </dt>
                                    <dd className="mt-1.5 break-all font-mono text-xs leading-relaxed text-amber-900">
                                      {(f.extraDocIds || []).join(", ") || "-"}
                                    </dd>
                                  </div>
                                  <div className="rounded-md border border-slate-200/80 bg-white px-3 py-2.5 md:col-span-2">
                                    <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                                      系统回答
                                    </dt>
                                    <dd className="mt-1.5 whitespace-pre-wrap text-sm leading-relaxed text-slate-900">
                                      {f.response || "-"}
                                    </dd>
                                  </div>
                                  {f.groundTruth ? (
                                    <div className="rounded-md border border-slate-200/80 bg-white px-3 py-2.5 md:col-span-2">
                                      <dt className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                                        标准答案
                                      </dt>
                                      <dd className="mt-1.5 whitespace-pre-wrap text-sm leading-relaxed text-slate-900">
                                        {f.groundTruth}
                                      </dd>
                                    </div>
                                  ) : null}
                                </dl>
                              </TableCell>
                            </TableRow>
                          ) : null}
                        </Fragment>
                      );
                    })}
                  </TableBody>
                </Table>
              </div>
            ) : null}
          </CardContent>
        </Card>
      ) : TERMINAL.has(run.status) ? (
        <Card>
          <CardHeader>
            <CardTitle>自建指标</CardTitle>
            <CardDescription>
              录制结束后会自动计算。若此处仍为空，多为自动评分失败；可通过 API{" "}
              <code className="text-xs">POST .../runs/{"{runId}"}/rescore</code> 补算。
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">暂无报告</p>
          </CardContent>
        </Card>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <Input
          className="max-w-xs"
          placeholder="搜索问题"
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              setPageNo(1);
              setKeyword(searchKeyword.trim());
            }
          }}
        />
        <Select
          value={statusFilter}
          onValueChange={(v) => {
            setPageNo(1);
            setStatusFilter(v);
          }}
        >
          <SelectTrigger className="w-40">
            <SelectValue placeholder="样本状态" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部</SelectItem>
            <SelectItem value="success">success</SelectItem>
            <SelectItem value="refused">refused</SelectItem>
            <SelectItem value="error">error</SelectItem>
            <SelectItem value="cancelled">cancelled</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <Card>
        <CardContent className="pt-6">
          {(records?.records || []).length === 0 ? (
            <div className="py-8 text-center text-muted-foreground">暂无录制记录</div>
          ) : (
            <Table className="min-w-[960px]">
              <TableHeader>
                <TableRow>
                  <TableHead>queryId</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>难度</TableHead>
                  <TableHead>问题</TableHead>
                  <TableHead>TTFT</TableHead>
                  <TableHead>耗时</TableHead>
                  <TableHead>期望/预测意图</TableHead>
                  <TableHead>Trace</TableHead>
                  <TableHead className="w-[72px]" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {(records?.records || []).map((item) => (
                  <TableRow
                    key={item.id}
                    className="cursor-pointer"
                    onClick={() => void openRecordDetail(item.id, item)}
                  >
                    <TableCell className="font-mono text-xs">{item.queryId || "-"}</TableCell>
                    <TableCell>
                      <EvalStatusBadge kind="record" status={item.status} />
                    </TableCell>
                    <TableCell className="font-mono text-xs">{item.difficulty || "-"}</TableCell>
                    <TableCell className="max-w-[250px] truncate" title={item.question}>
                      {item.question}
                    </TableCell>
                    <TableCell>{item.ttftMs ?? "-"} ms</TableCell>
                    <TableCell>{item.totalLatencyMs ?? "-"} ms</TableCell>
                    <TableCell className="font-mono text-xs">
                      {item.intentL2 || "-"} / {item.intentPred || "-"}
                    </TableCell>
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      {item.traceId ? (
                        <Link
                          className="inline-flex items-center text-sky-700 hover:underline"
                          to={`/admin/traces/${encodeURIComponent(item.traceId)}`}
                        >
                          {item.traceId.slice(0, 12)}…
                          <ExternalLink className="ml-1 h-3 w-3" />
                        </Link>
                      ) : (
                        "-"
                      )}
                    </TableCell>
                    <TableCell onClick={(e) => e.stopPropagation()}>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-7 px-2 text-xs"
                        onClick={() => void openRecordDetail(item.id, item)}
                      >
                        详情
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <div className="flex items-center justify-end gap-2">
        <Button variant="outline" disabled={pageNo <= 1} onClick={() => setPageNo((p) => p - 1)}>
          上一页
        </Button>
        <span className="text-sm text-muted-foreground">
          {pageNo} / {records?.pages || 1}
        </span>
        <Button
          variant="outline"
          disabled={!records || pageNo >= (records.pages || 1)}
          onClick={() => setPageNo((p) => p + 1)}
        >
          下一页
        </Button>
      </div>

      <Dialog open={ragasDialogOpen} onOpenChange={(open) => !ragasSubmitting && setRagasDialogOpen(open)}>
        <DialogContent className="sm:max-w-[440px]">
          <DialogHeader>
            <DialogTitle>RAGAS 评分</DialogTitle>
            <DialogDescription>
              选择 Judge 语言模型与嵌入模型。调用将产生 LLM / Embedding 费用，样本越多成本越高。
            </DialogDescription>
          </DialogHeader>
          {ragasModelsLoading ? (
            <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              加载模型列表…
            </div>
          ) : (
            <div className="space-y-4 py-1">
              <div className="space-y-2">
                <Label>语言模型</Label>
                <Select value={selectedChatModelId} onValueChange={setSelectedChatModelId}>
                  <SelectTrigger>
                    <SelectValue placeholder="选择语言模型" />
                  </SelectTrigger>
                  <SelectContent position="item-aligned">
                    {chatModels.map((m) => (
                      <SelectItem key={m.id} value={m.id}>
                        {modelOptionLabel(m)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>嵌入模型</Label>
                <Select value={selectedEmbeddingModelId} onValueChange={setSelectedEmbeddingModelId}>
                  <SelectTrigger>
                    <SelectValue placeholder="选择嵌入模型" />
                  </SelectTrigger>
                  <SelectContent position="item-aligned">
                    {embeddingModels.map((m) => (
                      <SelectItem key={m.id} value={m.id}>
                        {modelOptionLabel(m)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <p className="text-xs text-amber-700/90">
                提示：将按所选模型对应的 Java provider endpoint / API Key 调用评分服务。
              </p>
            </div>
          )}
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setRagasDialogOpen(false)}
              disabled={ragasSubmitting}
            >
              取消
            </Button>
            <Button
              onClick={handleRagasRescore}
              disabled={
                ragasSubmitting ||
                ragasModelsLoading ||
                !selectedChatModelId ||
                !selectedEmbeddingModelId
              }
            >
              {ragasSubmitting ? (
                <>
                  <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                  提交中…
                </>
              ) : (
                "开始评分"
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={compareDialogOpen} onOpenChange={setCompareDialogOpen}>
        <DialogContent className="sm:max-w-[480px]">
          <DialogHeader>
            <DialogTitle>同版本 Run 对比</DialogTitle>
            <DialogDescription>
              仅可选择相同数据集版本的其它 Run 作为基线。跨版本对比暂不支持。
            </DialogDescription>
          </DialogHeader>
          {compareLoading ? (
            <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" />
              加载同版本 Run…
            </div>
          ) : compareCandidates.length === 0 ? (
            <p className="py-4 text-sm text-muted-foreground">当前版本下没有其它可对比的 Run</p>
          ) : (
            <div className="space-y-2 py-1">
              <Label>基线 Run</Label>
              <Select value={selectedBaselineId} onValueChange={setSelectedBaselineId}>
                <SelectTrigger>
                  <SelectValue placeholder="选择基线 Run" />
                </SelectTrigger>
                <SelectContent position="item-aligned">
                  {compareCandidates.map((r) => (
                    <SelectItem key={r.id} value={r.id}>
                      {r.name} · {r.status}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setCompareDialogOpen(false)}>
              取消
            </Button>
            <Button
              disabled={!selectedBaselineId || compareLoading}
              onClick={() => {
                setCompareDialogOpen(false);
                navigate(`/admin/evaluations/runs/${runId}/compare/${selectedBaselineId}`);
              }}
            >
              开始对比
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={recordSheetOpen} onOpenChange={setRecordSheetOpen}>
        <DialogContent className="flex max-h-[85vh] w-full max-w-3xl flex-col gap-0 overflow-hidden p-0 sm:rounded-2xl">
          <DialogHeader className="shrink-0 border-b px-6 py-4 pr-12">
            <DialogTitle>样本详情</DialogTitle>
            <DialogDescription>
              {recordDetail?.queryId
                ? `queryId ${recordDetail.queryId}`
                : recordDetailLoading
                  ? "加载中…"
                  : "录制结果与 Case 标注对照"}
            </DialogDescription>
          </DialogHeader>
          <div className="min-h-0 flex-1 overflow-y-auto px-6 py-4">
            {recordDetailLoading && !recordDetail ? (
              <div className="flex items-center gap-2 py-10 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" />
                加载样本…
              </div>
            ) : recordDetail ? (
              <RecordDetailBody record={recordDetail} />
            ) : (
              <p className="py-8 text-sm text-muted-foreground">暂无数据</p>
            )}
          </div>
          {recordDetail?.id && run && (TERMINAL.has(run.status) || recordRerunning) ? (
            <DialogFooter className="shrink-0 border-t px-6 py-3 sm:justify-between">
              <p className="max-w-[28rem] text-left text-xs text-muted-foreground">
                {recordRerunning
                  ? "正在重跑本条样本，可关闭本窗；完成后页面会刷新自建指标。"
                  : "重跑将重新录制本条样本并自动重算自建指标；RAGAS 需完成后手动触发。"}
              </p>
              <Button
                size="sm"
                onClick={() => void handleRerunRecord()}
                disabled={recordRerunning || recordDetailLoading || ragasBusy || !TERMINAL.has(run.status)}
              >
                {recordRerunning ? (
                  <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                ) : (
                  <RotateCcw className="mr-1 h-4 w-4" />
                )}
                {recordRerunning ? "重跑中…" : "重跑本条"}
              </Button>
            </DialogFooter>
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function RecordDetailBody({ record }: { record: EvalRecord }) {
  const { missed, extra } = docDiff(record.expectedDocIds, record.retrievedDocIds);
  return (
    <div className="mt-4 space-y-4 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <EvalStatusBadge kind="record" status={record.status} />
        {record.difficulty ? (
          <span className="rounded border px-1.5 py-0.5 font-mono text-xs text-muted-foreground">
            {record.difficulty}
          </span>
        ) : null}
        {record.requiresRag != null ? (
          <span className="rounded border px-1.5 py-0.5 font-mono text-xs text-muted-foreground">
            requiresRag={String(record.requiresRag)}
          </span>
        ) : null}
        {record.traceId ? (
          <Link
            className="inline-flex items-center text-xs text-sky-700 hover:underline"
            to={`/admin/traces/${encodeURIComponent(record.traceId)}`}
          >
            Trace {record.traceId.slice(0, 12)}…
            <ExternalLink className="ml-1 h-3 w-3" />
          </Link>
        ) : null}
      </div>

      <DetailBlock title="问题">{record.question || "-"}</DetailBlock>
      <div className="grid gap-3 sm:grid-cols-2">
        <DetailBlock title="期望意图">
          <span className="font-mono text-xs">
            {record.intentL1 || "-"} / {record.intentL2 || "-"}
          </span>
        </DetailBlock>
        <DetailBlock title="预测意图">
          <span className="font-mono text-xs">
            {record.intentPred || "-"}
            {(record.predictedIntents || []).length > 1
              ? ` · [${(record.predictedIntents || []).join(", ")}]`
              : ""}
          </span>
        </DetailBlock>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <DetailBlock title="期望文档">
          <span className="break-all font-mono text-xs">
            {(record.expectedDocIds || []).join(", ") || "-"}
          </span>
        </DetailBlock>
        <DetailBlock title="召回文档">
          <span className="break-all font-mono text-xs">
            {(record.retrievedDocIds || []).join(", ") || "-"}
          </span>
        </DetailBlock>
        <DetailBlock title="未召回 (miss)">
          <span className="break-all font-mono text-xs text-rose-800">{missed.join(", ") || "-"}</span>
        </DetailBlock>
        <DetailBlock title="多召回 (extra)">
          <span className="break-all font-mono text-xs text-amber-900">{extra.join(", ") || "-"}</span>
        </DetailBlock>
      </div>
      {(record.niceToHaveDocIds || []).length > 0 ? (
        <DetailBlock title="nice 文档">
          <span className="break-all font-mono text-xs">
            {(record.niceToHaveDocIds || []).join(", ")}
          </span>
        </DetailBlock>
      ) : null}
      <DetailBlock title="系统回答" pre>
        {record.response || "-"}
      </DetailBlock>
      {record.groundTruth ? (
        <DetailBlock title="标准答案" pre>
          {record.groundTruth}
        </DetailBlock>
      ) : null}
      {(record.retrievedContexts || []).length > 0 ? (
        <DetailBlock title={`召回上下文 (${record.retrievedContexts!.length})`}>
          <div className="max-h-64 space-y-2 overflow-y-auto">
            {record.retrievedContexts!.map((ctx, i) => (
              <pre
                key={`${record.id}-ctx-${i}`}
                className="whitespace-pre-wrap rounded border bg-slate-50 px-2 py-1.5 font-mono text-[11px] leading-relaxed text-slate-800"
              >
                [{i + 1}] {record.retrievedContextDocIds?.[i] || "?"}
                {"\n"}
                {ctx}
              </pre>
            ))}
          </div>
        </DetailBlock>
      ) : null}
      <div className="grid gap-3 sm:grid-cols-2">
        <DetailBlock title="性能">
          TTFT {record.ttftMs ?? "-"} ms · 总耗时 {record.totalLatencyMs ?? "-"} ms · 旁路{" "}
          {record.evalLatencyMs ?? "-"} ms
        </DetailBlock>
        <DetailBlock title="分流">
          hasKb={String(record.hasKb ?? "-")} · hasMcp={String(record.hasMcp ?? "-")} · skipped=
          {String(record.retrievalSkipped ?? "-")}
          {record.skipReason ? ` · ${record.skipReason}` : ""}
        </DetailBlock>
      </div>
      {(record.errorCode || record.errorMessage) && (
        <DetailBlock title="错误">
          <span className="text-rose-700">
            {[record.errorCode, record.errorMessage].filter(Boolean).join(" · ")}
          </span>
        </DetailBlock>
      )}
      <div className="grid gap-2 font-mono text-[11px] text-muted-foreground">
        <div>conversationId: {record.conversationId || "-"}</div>
        <div>taskId: {record.taskId || "-"}</div>
        <div>evidence: {record.evidenceSource || "-"}</div>
      </div>
    </div>
  );
}

function DetailBlock({
  title,
  children,
  pre
}: {
  title: string;
  children: ReactNode;
  pre?: boolean;
}) {
  return (
    <div className="rounded-md border px-3 py-2.5">
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">{title}</div>
      <div className={`mt-1.5 text-slate-900 ${pre ? "whitespace-pre-wrap leading-relaxed" : ""}`}>
        {children}
      </div>
    </div>
  );
}

import { useEffect, useState, Fragment } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Ban, ChevronDown, ChevronRight, Download, ExternalLink, RefreshCw, RotateCcw, Calculator } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { RelativeTime } from "@/components/RelativeTime";
import { EvalStatusBadge } from "@/pages/admin/evaluations/EvalStatusBadge";
import type {
  EvalMetricReport,
  EvalRecord,
  EvalRun,
  EvalSampleFailure,
  PageResult
} from "@/services/evaluationService";
import {
  cancelRun,
  exportRunReport,
  getRun,
  getRunMetrics,
  pageRecords,
  rescoreRun,
  resumeRun
} from "@/services/evaluationService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 20;
const ACTIVE = new Set(["PENDING", "RECORDING", "DETERMINISTIC_SCORING", "RAGAS_SCORING", "REPORTING"]);
const RESUMABLE = new Set(["FAILED", "PARTIAL_SUCCESS", "CANCELLED"]);
const TERMINAL = new Set(["COMPLETED", "PARTIAL_SUCCESS", "FAILED", "CANCELLED"]);

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

/** Intent L2 切片固定列（无数据时展示 "-"） */
const INTENT_L2_SLICE_COLUMNS: { key: string; label: string; pct: boolean }[] = [
  { key: "hit@5", label: "Hit@5", pct: true },
  { key: "recall@5", label: "Recall@5", pct: true },
  { key: "mrr@10", label: "MRR@10", pct: true },
  { key: "faithfulness", label: "Faithfulness", pct: true },
  { key: "answer_correctness", label: "Answer Correctness", pct: true }
];

export function EvalRunDetailPage() {
  const { runId = "" } = useParams();
  const navigate = useNavigate();
  const [run, setRun] = useState<EvalRun | null>(null);
  const [records, setRecords] = useState<PageResult<EvalRecord> | null>(null);
  const [report, setReport] = useState<EvalMetricReport | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [statusFilter, setStatusFilter] = useState("all");
  const [keyword, setKeyword] = useState("");
  const [searchKeyword, setSearchKeyword] = useState("");
  const [loading, setLoading] = useState(true);
  const [expandedFailures, setExpandedFailures] = useState<Set<string>>(new Set());

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
      setReport(await getRunMetrics(runId));
    } catch {
      setReport(null);
    }
  };

  const refresh = async () => {
    setLoading(true);
    try {
      await Promise.all([loadRun(), loadRecords(), loadMetrics()]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh();
  }, [runId]);

  useEffect(() => {
    loadRecords();
  }, [pageNo, statusFilter, keyword]);

  useEffect(() => {
    if (!run || !ACTIVE.has(run.status)) return;
    const timer = setInterval(() => {
      loadRun();
      loadRecords();
    }, 3000);
    return () => clearInterval(timer);
  }, [run?.status, runId, pageNo, statusFilter, keyword]);

  useEffect(() => {
    if (run && TERMINAL.has(run.status)) {
      loadMetrics();
    }
  }, [run?.status]);

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

  const handleRescore = async () => {
    try {
      await rescoreRun(runId);
      toast.success("已创建新的确定性评分批次");
      await loadMetrics();
    } catch (error) {
      toast.error(getErrorMessage(error, "重新评分失败"));
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
          {TERMINAL.has(run.status) && (
            <>
              <Button variant="outline" size="sm" onClick={handleRescore}>
                <Calculator className="mr-1 h-4 w-4" />
                重新评分
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
          "双路径证据提示：旁路 /rag/eval 检索证据可能与真实 Chat 回答所用上下文不完全一致。"}
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">进度</CardTitle>
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
              {run.successCount + run.failedCount}/{run.totalCount} · 阶段 {run.currentPhase}
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

      {report ? (
        <Card>
          <CardHeader>
            <CardTitle>确定性指标</CardTitle>
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
              const metricByName = new Map((report.metrics || []).map((m) => [m.name, m]));
              const l2Keys = Array.from(
                new Set(
                  INTENT_L2_SLICE_COLUMNS.flatMap((col) =>
                    Object.keys(metricByName.get(col.key)?.byIntentL2 || {})
                  )
                )
              ).sort();
              if (l2Keys.length === 0) return null;
              return (
                <div className="space-y-3 border-t border-slate-100 pt-4">
                  <h3 className="text-base font-semibold text-slate-900">Intent L2 切片(核心指标)</h3>
                  <Table className="min-w-[720px]">
                    <TableHeader>
                      <TableRow>
                        <TableHead>Intent L2</TableHead>
                        {INTENT_L2_SLICE_COLUMNS.map((col) => (
                          <TableHead key={col.key}>{col.label}</TableHead>
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {l2Keys.map((key) => (
                        <TableRow key={key}>
                          <TableCell className="font-mono text-xs">{key}</TableCell>
                          {INTENT_L2_SLICE_COLUMNS.map((col) => {
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
                          </TableRow>
                          {open ? (
                            <TableRow className="bg-slate-50/80 hover:bg-slate-50/80">
                              <TableCell colSpan={4} className="px-4 py-3">
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
            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
              <div className="space-y-1.5">
                <CardTitle>确定性指标</CardTitle>
                <CardDescription>
                  录制已结束，尚未生成评分批次。重新评分不重跑 Chat，只写入新的 score_batch。
                </CardDescription>
              </div>
              <Button variant="outline" size="sm" className="shrink-0" onClick={handleRescore}>
                <Calculator className="mr-1 h-4 w-4" />
                重新评分
              </Button>
            </div>
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
                  <TableHead>问题</TableHead>
                  <TableHead>TTFT</TableHead>
                  <TableHead>耗时</TableHead>
                  <TableHead>意图</TableHead>
                  <TableHead>Trace</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(records?.records || []).map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-mono text-xs">{item.queryId || "-"}</TableCell>
                    <TableCell>
                      <EvalStatusBadge kind="record" status={item.status} />
                    </TableCell>
                    <TableCell className="max-w-[250px] truncate" title={item.question}>
                      {item.question}
                    </TableCell>
                    <TableCell>{item.ttftMs ?? "-"} ms</TableCell>
                    <TableCell>{item.totalLatencyMs ?? "-"} ms</TableCell>
                    <TableCell className="font-mono text-xs">{item.intentPred || "-"}</TableCell>
                    <TableCell>
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
    </div>
  );
}

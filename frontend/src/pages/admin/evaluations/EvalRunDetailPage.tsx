import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Ban, ExternalLink, RefreshCw, RotateCcw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { RelativeTime } from "@/components/RelativeTime";
import { EvalStatusBadge } from "@/pages/admin/evaluations/EvalStatusBadge";
import type { EvalRecord, EvalRun, PageResult } from "@/services/evaluationService";
import { cancelRun, getRun, pageRecords, resumeRun } from "@/services/evaluationService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 20;
const ACTIVE = new Set(["PENDING", "RECORDING", "DETERMINISTIC_SCORING", "RAGAS_SCORING", "REPORTING"]);
const RESUMABLE = new Set(["FAILED", "PARTIAL_SUCCESS", "CANCELLED"]);

export function EvalRunDetailPage() {
  const { runId = "" } = useParams();
  const navigate = useNavigate();
  const [run, setRun] = useState<EvalRun | null>(null);
  const [records, setRecords] = useState<PageResult<EvalRecord> | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [statusFilter, setStatusFilter] = useState("all");
  const [keyword, setKeyword] = useState("");
  const [searchKeyword, setSearchKeyword] = useState("");
  const [loading, setLoading] = useState(true);

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

  const refresh = async () => {
    setLoading(true);
    try {
      await Promise.all([loadRun(), loadRecords()]);
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
        <div className="ml-auto flex gap-2">
          <Button variant="outline" size="sm" onClick={refresh}>
            <RefreshCw className="mr-1 h-4 w-4" />
            刷新
          </Button>
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

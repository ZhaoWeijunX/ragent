import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, GitCompareArrows, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { EvalStatusBadge } from "@/pages/admin/evaluations/EvalStatusBadge";
import type {
  EvalCompareMetricDelta,
  EvalCompareScoreSide,
  EvalCompareValueDelta,
  EvalRunCompare
} from "@/services/evaluationService";
import { compareRuns } from "@/services/evaluationService";
import { getEvalWorkbenchErrorMessage as getErrorMessage } from "@/utils/error";

const DETERMINISTIC_LABELS: Record<string, string> = {
  intent_top1: "意图 Top-1 准确率",
  "hit@1": "Hit@1",
  "hit@3": "Hit@3",
  "hit@5": "Hit@5",
  "hit@10": "Hit@10",
  "recall@5": "Recall@5",
  "recall_inclusive@5": "Recall@5 (含 nice)",
  "recall@10": "Recall@10",
  "mrr@10": "MRR@10",
  refusal_when_required: "误拒率",
  fallback_when_required: "答案兜底率",
  over_retrieval_rate: "过召回率",
  ttft_p50_ms: "首字延迟 P50 (ms)",
  ttft_mean_ms: "首字延迟均值 (ms)",
  total_mean_ms: "整流均值 (ms)"
};

const RAGAS_LABELS: Record<string, string> = {
  faithfulness: "Faithfulness",
  answer_relevancy: "Answer Relevancy",
  answer_correctness: "Answer Correctness",
  context_precision: "Context Precision",
  context_recall: "Context Recall"
};

function formatNum(v: number | null | undefined, pct?: boolean | null) {
  if (v == null || Number.isNaN(v)) return "-";
  if (pct) return `${(v * 100).toFixed(1)}%`;
  return Number.isInteger(v) ? String(v) : v.toFixed(2);
}

function formatDelta(v: number | null | undefined, pct?: boolean | null) {
  if (v == null || Number.isNaN(v)) return "-";
  const sign = v > 0 ? "+" : "";
  if (pct) return `${sign}${(v * 100).toFixed(1)}pp`;
  return `${sign}${Number.isInteger(v) ? v : v.toFixed(2)}`;
}

function formatRel(v: number | null | undefined) {
  if (v == null || Number.isNaN(v)) return "-";
  const sign = v > 0 ? "+" : "";
  return `${sign}${(v * 100).toFixed(1)}%`;
}

function deltaClass(v: number | null | undefined, higherIsBetter = true) {
  if (v == null || v === 0) return "text-muted-foreground";
  const good = higherIsBetter ? v > 0 : v < 0;
  return good ? "text-emerald-700" : "text-rose-700";
}

function isLatencyMetric(name: string) {
  return name.endsWith("_ms") || name.includes("latency") || name.includes("ttft") || name.includes("total_mean");
}

function higherIsBetterFor(name: string) {
  return !isLatencyMetric(name) && !name.includes("refusal") && !name.includes("over_retrieval") && !name.includes("fallback");
}

function orderMetrics(metrics: EvalCompareMetricDelta[] | undefined, labels: Record<string, string>) {
  if (!metrics?.length) return [];
  const preferred = Object.keys(labels);
  const rank = new Map(preferred.map((k, i) => [k, i]));
  return [...metrics].sort((a, b) => {
    const ra = rank.get(a.name) ?? 1000;
    const rb = rank.get(b.name) ?? 1000;
    if (ra !== rb) return ra - rb;
    return a.name.localeCompare(b.name);
  });
}

function ValueDeltaCell({ delta, pct, higherIsBetter }: { delta?: EvalCompareValueDelta | null; pct?: boolean; higherIsBetter?: boolean }) {
  if (!delta) return <span className="text-muted-foreground">-</span>;
  return (
    <span className={deltaClass(delta.absoluteDelta, higherIsBetter)}>
      {formatDelta(delta.absoluteDelta, pct)} ({formatRel(delta.relativeDelta)})
    </span>
  );
}

export function EvalRunComparePage() {
  const { runId = "", baselineRunId = "" } = useParams();
  const navigate = useNavigate();

  const [data, setData] = useState<EvalRunCompare | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    if (!runId || !baselineRunId) return;
    try {
      setLoading(true);
      setData(await compareRuns(runId, baselineRunId));
    } catch (error) {
      setData(null);
      toast.error(getErrorMessage(error, "对比失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [runId, baselineRunId]);

  const detMetrics = useMemo(
    () => orderMetrics(data?.deterministic?.metrics, DETERMINISTIC_LABELS),
    [data]
  );
  const ragasMetrics = useMemo(() => orderMetrics(data?.ragas?.metrics, RAGAS_LABELS), [data]);

  const intentL2Rows = useMemo(() => {
    const keys = new Set<string>();
    for (const m of detMetrics) {
      for (const s of m.byIntentL2 || []) keys.add(s.key);
    }
    const focus = ["hit@5", "recall@5", "mrr@10", "intent_top1"].filter((n) =>
      detMetrics.some((m) => m.name === n)
    );
    return { keys: [...keys].sort(), focus };
  }, [detMetrics]);

  const hasJudge =
    !!(data?.currentJudgeConfig && Object.keys(data.currentJudgeConfig).length) ||
    !!(data?.baselineJudgeConfig && Object.keys(data.baselineJudgeConfig).length);

  if (loading && !data) {
    return <div className="text-muted-foreground">加载对比结果…</div>;
  }

  if (!data) {
    return (
      <div className="admin-page space-y-4">
        <Button variant="ghost" size="sm" onClick={() => navigate(`/admin/evaluations/runs/${runId}`)}>
          <ArrowLeft className="mr-1 h-4 w-4" />
          返回 Run
        </Button>
        <p className="text-muted-foreground">暂无对比数据（需同版本且两侧均有自建评分批次）</p>
      </div>
    );
  }

  return (
    <div className="admin-page space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Button variant="ghost" size="sm" onClick={() => navigate(`/admin/evaluations/runs/${runId}`)}>
          <ArrowLeft className="mr-1 h-4 w-4" />
          返回 Run
        </Button>
        <h1 className="admin-page-title text-xl">Run 对比</h1>
        <GitCompareArrows className="h-5 w-5 text-muted-foreground" />
        <div className="ml-auto">
          <Button variant="outline" size="sm" onClick={() => void load()}>
            <RefreshCw className="mr-1 h-4 w-4" />
            刷新
          </Button>
        </div>
      </div>

      <div className="rounded-md border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-950">
        仅支持相同数据集版本对比 · 版本 {data.current.datasetVersion || data.datasetVersionId}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">当前 Run</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1 text-sm">
            <div className="font-medium">
              <Link className="text-sky-700 hover:underline" to={`/admin/evaluations/runs/${data.current.runId}`}>
                {data.current.name || data.current.runId}
              </Link>
            </div>
            <div className="flex items-center gap-2">
              <EvalStatusBadge kind="run" status={data.current.status || ""} />
            </div>
            <div className="text-xs text-muted-foreground">
              自建 batch {data.deterministic?.currentBatchId || "-"}
              {data.ragas?.currentBatchId ? ` · RAGAS ${data.ragas.currentBatchId}` : ""}
            </div>
            <JudgeModelLines config={data.currentJudgeConfig} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">基线 Run</CardTitle>
          </CardHeader>
          <CardContent className="space-y-1 text-sm">
            <div className="font-medium">
              <Link className="text-sky-700 hover:underline" to={`/admin/evaluations/runs/${data.baseline.runId}`}>
                {data.baseline.name || data.baseline.runId}
              </Link>
            </div>
            <div className="flex items-center gap-2">
              <EvalStatusBadge kind="run" status={data.baseline.status || ""} />
            </div>
            <div className="text-xs text-muted-foreground">
              自建 batch {data.deterministic?.baselineBatchId || "-"}
              {data.ragas?.baselineBatchId ? ` · RAGAS ${data.ragas.baselineBatchId}` : ""}
            </div>
            <JudgeModelLines config={data.baselineJudgeConfig} />
          </CardContent>
        </Card>
      </div>

      {(data.configDiff || []).length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>配置快照差异</CardTitle>
            <CardDescription>
              创建 Run 时冻结的模型 / Embedding / 检索 / 知识指纹等；共 {(data.configDiff || []).length} 项不同
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>路径</TableHead>
                  <TableHead>当前</TableHead>
                  <TableHead>基线</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(data.configDiff || []).slice(0, 80).map((d) => (
                  <TableRow key={d.path}>
                    <TableCell className="font-mono text-xs">{d.path}</TableCell>
                    <TableCell className="max-w-[280px] truncate text-xs" title={stringify(d.current)}>
                      {stringify(d.current)}
                    </TableCell>
                    <TableCell className="max-w-[280px] truncate text-xs" title={stringify(d.baseline)}>
                      {stringify(d.baseline)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {(data.configDiff || []).length > 80 ? (
              <p className="mt-2 text-xs text-muted-foreground">仅展示前 80 项差异</p>
            ) : null}
          </CardContent>
        </Card>
      )}

      {hasJudge && (
        <Card>
          <CardHeader>
            <CardTitle>RAGAS Judge 模型</CardTitle>
            <CardDescription>
              {(data.judgeConfigDiff || []).length === 0
                ? "两侧 Judge / Embedding 配置一致"
                : `${(data.judgeConfigDiff || []).length} 项不同`}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2 text-sm">
              <div>
                <div className="mb-1 text-muted-foreground">当前</div>
                <JudgeModelBlock config={data.currentJudgeConfig} />
              </div>
              <div>
                <div className="mb-1 text-muted-foreground">基线</div>
                <JudgeModelBlock config={data.baselineJudgeConfig} />
              </div>
            </div>
            {(data.judgeConfigDiff || []).length > 0 && (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>路径</TableHead>
                    <TableHead>当前</TableHead>
                    <TableHead>基线</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(data.judgeConfigDiff || []).map((d) => (
                    <TableRow key={d.path}>
                      <TableCell className="font-mono text-xs">{d.path}</TableCell>
                      <TableCell className="max-w-[240px] truncate text-xs">{stringify(d.current)}</TableCell>
                      <TableCell className="max-w-[240px] truncate text-xs">{stringify(d.baseline)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      )}

      <MetricTableCard
        title="自建指标"
        description="当前相对基线变化绝对值"
        metrics={detMetrics}
        labels={DETERMINISTIC_LABELS}
        side={data.deterministic}
      />

      <Card>
        <CardHeader>
          <CardTitle>TTFT</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2 text-sm">
          <div>
            <div className="text-muted-foreground">P50</div>
            <div>
              当前 {formatNum(data.deterministic?.ttft?.p50?.current, false)} / 基线{" "}
              {formatNum(data.deterministic?.ttft?.p50?.baseline, false)} ·{" "}
              <ValueDeltaCell delta={data.deterministic?.ttft?.p50} higherIsBetter={false} />
            </div>
          </div>
          <div>
            <div className="text-muted-foreground">均值</div>
            <div>
              当前 {formatNum(data.deterministic?.ttft?.mean?.current, false)} / 基线{" "}
              {formatNum(data.deterministic?.ttft?.mean?.baseline, false)} ·{" "}
              <ValueDeltaCell delta={data.deterministic?.ttft?.mean} higherIsBetter={false} />
            </div>
          </div>
        </CardContent>
      </Card>

      {intentL2Rows.keys.length > 0 && intentL2Rows.focus.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Intent L2 自建指标变化</CardTitle>
            {/*<CardDescription>展示有切片数据的关键指标绝对差</CardDescription>*/}
          </CardHeader>
          <CardContent className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Intent L2</TableHead>
                  {intentL2Rows.focus.map((n) => (
                    <TableHead key={n}>{DETERMINISTIC_LABELS[n] || n} Δ</TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {intentL2Rows.keys.map((key) => (
                  <TableRow key={key}>
                    <TableCell>{key}</TableCell>
                    {intentL2Rows.focus.map((n) => {
                      const metric = detMetrics.find((m) => m.name === n);
                      const slice = metric?.byIntentL2?.find((s) => s.key === key);
                      return (
                        <TableCell key={n} className={deltaClass(slice?.absoluteDelta)}>
                          {formatDelta(slice?.absoluteDelta, metric?.pct)}
                        </TableCell>
                      );
                    })}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <MetricTableCard
        title="RAGAS 指标"
        description={data.ragas?.available ? "当前 − 基线；相对变化相对基线绝对值" : "两侧均无 RAGAS 评分批次"}
        metrics={ragasMetrics}
        labels={RAGAS_LABELS}
        side={data.ragas}
        emptyHint="暂无 RAGAS 对比数据"
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <FailureCard title="新增失败" items={data.failures?.newFailures || []} empty="无" />
        <FailureCard title="已修复" items={data.failures?.fixedFailures || []} empty="无" />
        <FailureCard title="持续失败" items={data.failures?.persistentFailures || []} empty="无" />
      </div>
    </div>
  );
}

function MetricTableCard({
  title,
  description,
  metrics,
  labels,
  side,
  emptyHint
}: {
  title: string;
  description: string;
  metrics: EvalCompareMetricDelta[];
  labels: Record<string, string>;
  side?: EvalCompareScoreSide | null;
  emptyHint?: string;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>
          {description}
          {side?.currentBatchId || side?.baselineBatchId
            ? ` · batch ${side.currentBatchId || "-"} / ${side.baselineBatchId || "-"}`
            : ""}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {metrics.length === 0 ? (
          <p className="text-sm text-muted-foreground">{emptyHint || "暂无指标"}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>指标</TableHead>
                <TableHead>当前</TableHead>
                <TableHead>基线</TableHead>
                <TableHead>绝对差</TableHead>
                <TableHead>相对变化</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {metrics.map((m) => (
                <TableRow key={m.name}>
                  <TableCell>{labels[m.name] || m.name}</TableCell>
                  <TableCell>{formatNum(m.current, m.pct)}</TableCell>
                  <TableCell>{formatNum(m.baseline, m.pct)}</TableCell>
                  <TableCell className={deltaClass(m.absoluteDelta, higherIsBetterFor(m.name))}>
                    {formatDelta(m.absoluteDelta, m.pct)}
                  </TableCell>
                  <TableCell className={deltaClass(m.relativeDelta, higherIsBetterFor(m.name))}>
                    {formatRel(m.relativeDelta)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function FailureCard({
  title,
  items,
  empty
}: {
  title: string;
  items: EvalRunCompare["failures"]["newFailures"];
  empty: string;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">
          {title} ({items.length})
        </CardTitle>
      </CardHeader>
      <CardContent>
        {items.length === 0 ? (
          <p className="text-sm text-muted-foreground">{empty}</p>
        ) : (
          <ul className="space-y-2 text-sm">
            {items.map((f) => (
              <li key={f.recordId} className="rounded border px-2 py-1">
                <div className="font-mono text-xs">{f.queryId || f.recordId}</div>
                <div className="text-muted-foreground text-xs">{(f.failureReasons || []).join(", ")}</div>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function judgeField(config: Record<string, unknown> | null | undefined, ...keys: string[]) {
  if (!config) return null;
  for (const k of keys) {
    const v = config[k];
    if (v != null && String(v).trim() !== "") return String(v);
  }
  return null;
}

function formatJudgeModel(config: Record<string, unknown> | null | undefined, kind: "chat" | "embedding") {
  if (kind === "chat") {
    const provider = judgeField(config, "chatProvider");
    const model = judgeField(config, "chatModel", "chatModelId");
    if (!provider && !model) return null;
    return provider && model ? `${provider} · ${model}` : model || provider;
  }
  const provider = judgeField(config, "embeddingProvider");
  const model = judgeField(config, "embeddingModel", "embeddingModelId");
  if (!provider && !model) return null;
  return provider && model ? `${provider} · ${model}` : model || provider;
}

function JudgeModelLines({ config }: { config?: Record<string, unknown> | null }) {
  const chat = formatJudgeModel(config, "chat");
  const emb = formatJudgeModel(config, "embedding");
  if (!chat && !emb) {
    return <p className="text-xs text-muted-foreground">未记录 Judge 模型</p>;
  }
  return (
    <div className="space-y-0.5 pt-1 text-xs text-muted-foreground">
      {chat && <div>Judge：{chat}</div>}
      {emb && <div>Embedding：{emb}</div>}
    </div>
  );
}

function JudgeModelBlock({ config }: { config?: Record<string, unknown> | null }) {
  const chat = formatJudgeModel(config, "chat");
  const emb = formatJudgeModel(config, "embedding");
  const chatUrl = judgeField(config, "chatBaseUrl");
  const embUrl = judgeField(config, "embeddingBaseUrl");
  if (!chat && !emb) {
    return <p className="text-muted-foreground">未记录</p>;
  }
  return (
    <ul className="space-y-1">
      <li>
        <span className="text-muted-foreground">Judge：</span>
        {chat || "-"}
        {chatUrl ? <span className="ml-1 font-mono text-xs text-muted-foreground">({chatUrl})</span> : null}
      </li>
      <li>
        <span className="text-muted-foreground">Embedding：</span>
        {emb || "-"}
        {embUrl ? <span className="ml-1 font-mono text-xs text-muted-foreground">({embUrl})</span> : null}
      </li>
    </ul>
  );
}

function stringify(v: unknown) {
  if (v == null) return "-";
  if (typeof v === "string") return v;
  try {
    return JSON.stringify(v);
  } catch {
    return String(v);
  }
}

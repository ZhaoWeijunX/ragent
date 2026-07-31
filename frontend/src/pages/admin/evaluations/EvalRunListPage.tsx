import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Plus, RefreshCw, Play } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { RelativeTime } from "@/components/RelativeTime";
import { EvalStatusBadge } from "@/pages/admin/evaluations/EvalStatusBadge";
import type { EvalDataset, EvalDatasetVersion, EvalRun, PageResult } from "@/services/evaluationService";
import { createRun, getDataset, listVersions, pageDatasets, pageRuns } from "@/services/evaluationService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;
const DUAL_PATH_HINT =
  "双路径证据提示：旁路 (/rag/eval) 检索证据可能与真实 Chat 回答所用上下文不完全一致，请勿将两者视为严格等价。";

export function EvalRunListPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const presetVersionId = searchParams.get("datasetVersionId") || "";

  const [pageData, setPageData] = useState<PageResult<EvalRun> | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [datasets, setDatasets] = useState<EvalDataset[]>([]);
  const [versions, setVersions] = useState<EvalDatasetVersion[]>([]);
  const [form, setForm] = useState({
    name: "",
    datasetId: "",
    datasetVersionId: presetVersionId,
    environment: "eval-local",
    ragasEnabled: false
  });

  const load = async (current = pageNo, kw = keyword, status = statusFilter) => {
    try {
      setLoading(true);
      setPageData(
        await pageRuns(current, PAGE_SIZE, {
          keyword: kw || undefined,
          status: status === "all" ? undefined : status,
          datasetVersionId: presetVersionId || undefined
        })
      );
    } catch (error) {
      toast.error(getErrorMessage(error, "加载 Run 列表失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [pageNo, keyword, statusFilter, presetVersionId]);

  useEffect(() => {
    if (!dialogOpen) return;
    (async () => {
      try {
        const ds = await pageDatasets(1, 100);
        setDatasets(ds.records || []);
        if (presetVersionId) {
          for (const d of ds.records || []) {
            const vs = await listVersions(d.id);
            const hit = vs.find((v) => v.id === presetVersionId);
            if (hit) {
              setVersions(vs.filter((v) => v.status === "PUBLISHED"));
              setForm((prev) => ({
                ...prev,
                datasetId: d.id,
                datasetVersionId: presetVersionId,
                name: prev.name || `${d.name}-${hit.version}`
              }));
              return;
            }
          }
        }
      } catch (error) {
        toast.error(getErrorMessage(error, "加载评估集失败"));
      }
    })();
  }, [dialogOpen, presetVersionId]);

  const publishedVersions = useMemo(() => versions.filter((v) => v.status === "PUBLISHED"), [versions]);

  const handleSelectDataset = async (datasetId: string) => {
    setForm((prev) => ({ ...prev, datasetId, datasetVersionId: "" }));
    try {
      const vs = await listVersions(datasetId);
      setVersions(vs.filter((v) => v.status === "PUBLISHED"));
      const ds = await getDataset(datasetId);
      setForm((prev) => ({
        ...prev,
        datasetId,
        name: prev.name || `${ds.name}-run`
      }));
    } catch (error) {
      toast.error(getErrorMessage(error, "加载版本失败"));
    }
  };

  const handleCreate = async () => {
    if (!form.name.trim() || !form.datasetVersionId) {
      toast.error("请填写名称并选择已发布版本");
      return;
    }
    try {
      const id = await createRun({
        name: form.name.trim(),
        datasetVersionId: form.datasetVersionId,
        ragasEnabled: form.ragasEnabled,
        tags: { environment: form.environment || "unknown" }
      });
      toast.success("Run 已创建并开始录制");
      setDialogOpen(false);
      navigate(`/admin/evaluations/runs/${id}`);
    } catch (error) {
      toast.error(getErrorMessage(error, "创建 Run 失败"));
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">评测运行</h1>
          <p className="admin-page-subtitle">异步双路径录制；仅可引用已发布评估集版本</p>
        </div>
        <div className="admin-page-actions">
          <Select
            value={statusFilter}
            onValueChange={(v) => {
              setPageNo(1);
              setStatusFilter(v);
            }}
          >
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="状态" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部状态</SelectItem>
              <SelectItem value="PENDING">PENDING</SelectItem>
              <SelectItem value="RECORDING">RECORDING</SelectItem>
              <SelectItem value="COMPLETED">COMPLETED</SelectItem>
              <SelectItem value="PARTIAL_SUCCESS">PARTIAL_SUCCESS</SelectItem>
              <SelectItem value="FAILED">FAILED</SelectItem>
              <SelectItem value="CANCELLED">CANCELLED</SelectItem>
            </SelectContent>
          </Select>
          <Input
            className="w-[220px]"
            placeholder="搜索 Run 名称"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                setPageNo(1);
                setKeyword(searchKeyword.trim());
              }
            }}
          />
          <Button
            variant="outline"
            onClick={() => {
              setPageNo(1);
              setKeyword(searchKeyword.trim());
            }}
          >
            搜索
          </Button>
          <Button variant="outline" onClick={() => load()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            刷新
          </Button>
          <Button className="admin-primary-gradient" onClick={() => setDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            创建 Run
          </Button>
        </div>
      </div>

      <div className="mb-4 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
        {DUAL_PATH_HINT}
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : !pageData?.records?.length ? (
            <div className="py-8 text-center text-muted-foreground">暂无 Run，点击上方按钮创建</div>
          ) : (
            <Table className="min-w-[1080px]">
              <TableHeader>
                <TableRow>
                  <TableHead>名称</TableHead>
                  <TableHead>评估集版本</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>阶段</TableHead>
                  <TableHead>进度</TableHead>
                  <TableHead>成功/失败</TableHead>
                  <TableHead>更新时间</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {pageData.records.map((item) => {
                  const success = item.successCount ?? 0;
                  const failed = item.failedCount ?? 0;
                  const total = item.totalCount ?? 0;
                  return (
                    <TableRow
                      key={item.id}
                      className="cursor-pointer"
                      onClick={() => navigate(`/admin/evaluations/runs/${item.id}`)}
                    >
                      <TableCell className="font-medium">
                        <span className="admin-link max-w-[180px] truncate block">{item.name}</span>
                      </TableCell>
                      <TableCell>
                        {item.datasetName || "-"} / {item.datasetVersion || item.datasetVersionId}
                      </TableCell>
                      <TableCell>
                        <EvalStatusBadge kind="run" status={item.status} />
                      </TableCell>
                      <TableCell className="font-mono text-xs">{item.currentPhase || "-"}</TableCell>
                      <TableCell>
                        {item.progress ?? 0}% ({success + failed}/{total})
                      </TableCell>
                      <TableCell>
                        {success}/{failed}
                      </TableCell>
                      <TableCell>
                        {item.updateTime ? <RelativeTime value={item.updateTime} /> : "-"}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {pageData ? (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
          <span>共 {pageData.total} 条</span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={pageNo <= 1}
              onClick={() => setPageNo((p) => Math.max(1, p - 1))}
            >
              上一页
            </Button>
            <span>
              {pageData.current} / {pageData.pages}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={pageNo >= (pageData.pages || 1)}
              onClick={() => setPageNo((p) => p + 1)}
            >
              下一页
            </Button>
          </div>
        </div>
      ) : null}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建评测 Run</DialogTitle>
            <DialogDescription>仅可引用 PUBLISHED 版本。创建后立即异步录制。</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1.5">
              <Label>名称</Label>
              <Input value={form.name} onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))} />
            </div>
            <div className="space-y-1.5">
              <Label>评估集</Label>
              <Select value={form.datasetId} onValueChange={handleSelectDataset}>
                <SelectTrigger>
                  <SelectValue placeholder="选择评估集" />
                </SelectTrigger>
                <SelectContent>
                  {datasets.map((d) => (
                    <SelectItem key={d.id} value={d.id}>
                      {d.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>已发布版本</Label>
              <Select
                value={form.datasetVersionId}
                onValueChange={(v) => setForm((p) => ({ ...p, datasetVersionId: v }))}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择版本" />
                </SelectTrigger>
                <SelectContent>
                  {publishedVersions.map((v) => (
                    <SelectItem key={v.id} value={v.id}>
                      {v.version}（{v.sampleCount} 条）
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label>环境标签</Label>
              <Input
                value={form.environment}
                onChange={(e) => setForm((p) => ({ ...p, environment: e.target.value }))}
              />
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={form.ragasEnabled}
                onChange={(e) => setForm((p) => ({ ...p, ragasEnabled: e.target.checked }))}
              />
              启用 RAGAS（需 app.eval.ragas.enabled 与评分服务可用；失败不影响自建指标）
            </label>
            <p className="text-xs text-amber-800">{DUAL_PATH_HINT}</p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={handleCreate}>
              <Play className="mr-2 h-4 w-4" />
              开始
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

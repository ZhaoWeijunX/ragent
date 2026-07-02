import { useEffect, useRef, useState } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { DocumentProcessModeSection } from "@/pages/admin/knowledge/components/DocumentProcessModeSection";
import {
  buildProcessModePayload,
  DEFAULT_CONFIG_VALUES,
  validateProcessModeValues,
  type ProcessMode
} from "@/pages/admin/knowledge/utils/documentProcessMode";
import {
  discoverFeishuWiki,
  getFeishuWikiImportJob,
  startFeishuWikiImport,
  type ChunkStrategyOption,
  type FeishuWikiDiscoverResult,
  type FeishuWikiImportJob,
  type FeishuWikiImportPayload,
  type FeishuWikiImportScope
} from "@/services/knowledgeService";
import { getErrorMessage } from "@/utils/error";

type Step = "config" | "preview" | "importing";

const SCOPE_OPTIONS: { value: FeishuWikiImportScope; label: string }[] = [
  { value: "PAGE_ONLY", label: "仅当前页面" },
  { value: "SUBTREE", label: "当前页面及子树" },
  { value: "ENTIRE_SPACE", label: "整个知识空间" }
];

interface FeishuWikiImportDialogProps {
  open: boolean;
  kbId: string;
  onOpenChange: (open: boolean) => void;
  onCompleted?: () => void;
}

export function FeishuWikiImportDialog({ open, kbId, onOpenChange, onCompleted }: FeishuWikiImportDialogProps) {
  const [step, setStep] = useState<Step>("config");
  const [rootUrl, setRootUrl] = useState("");
  const [scope, setScope] = useState<FeishuWikiImportScope>("SUBTREE");
  const [autoChunk, setAutoChunk] = useState(false);
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [scheduleCron, setScheduleCron] = useState("0 0 * * *");
  const [processMode, setProcessMode] = useState<ProcessMode>("chunk");
  const [chunkStrategy, setChunkStrategy] = useState("fixed_size");
  const [configValues, setConfigValues] = useState<Record<string, string>>({ ...DEFAULT_CONFIG_VALUES });
  const [pipelineId, setPipelineId] = useState("");
  const [noChunk, setNoChunk] = useState(false);
  const [chunkStrategies, setChunkStrategies] = useState<ChunkStrategyOption[]>([]);
  const [discovering, setDiscovering] = useState(false);
  const [importing, setImporting] = useState(false);
  const [preview, setPreview] = useState<FeishuWikiDiscoverResult | null>(null);
  const [job, setJob] = useState<FeishuWikiImportJob | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const reset = () => {
    setStep("config");
    setRootUrl("");
    setScope("SUBTREE");
    setAutoChunk(false);
    setScheduleEnabled(false);
    setScheduleCron("0 0 * * *");
    setProcessMode("chunk");
    setChunkStrategy("fixed_size");
    setConfigValues({ ...DEFAULT_CONFIG_VALUES });
    setPipelineId("");
    setNoChunk(false);
    setPreview(null);
    setJob(null);
    setDiscovering(false);
    setImporting(false);
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  useEffect(() => {
    if (!open) {
      reset();
    }
  }, [open]);

  useEffect(() => {
    return () => {
      if (pollRef.current) {
        clearInterval(pollRef.current);
      }
    };
  }, []);

  const buildPayload = (): FeishuWikiImportPayload => {
    const processModeInput = { processMode, chunkStrategy, configValues, pipelineId };
    const processModePayload = buildProcessModePayload(processModeInput, chunkStrategies);

    return {
      rootUrl: rootUrl.trim(),
      scope,
      autoChunk,
      ...processModePayload,
      scheduleEnabled,
      scheduleCron: scheduleEnabled ? scheduleCron : undefined
    };
  };

  const validateConfig = (): boolean => {
    if (!rootUrl.trim()) {
      toast.error("请输入飞书 Wiki 页面链接");
      return false;
    }
    const processModeError = validateProcessModeValues({ processMode, chunkStrategy, configValues, pipelineId });
    if (processModeError) {
      toast.error(processModeError);
      return false;
    }
    if (processMode === "chunk" && chunkStrategies.length === 0) {
      toast.error("分块策略加载中，请稍后再试");
      return false;
    }
    return true;
  };

  const handleDiscover = async () => {
    if (!validateConfig()) {
      return;
    }
    setDiscovering(true);
    try {
      const result = await discoverFeishuWiki(kbId, buildPayload());
      setPreview(result);
      setStep("preview");
      if (!result.pages?.length) {
        toast.warning("未发现可导入的 docx 页面");
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "预览失败"));
    } finally {
      setDiscovering(false);
    }
  };

  const startPolling = (jobId: string) => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
    }
    pollRef.current = setInterval(async () => {
      try {
        const latest = await getFeishuWikiImportJob(jobId);
        setJob(latest);
        if (["completed", "partial", "failed"].includes(latest.status)) {
          if (pollRef.current) {
            clearInterval(pollRef.current);
            pollRef.current = null;
          }
          setImporting(false);
          onCompleted?.();
          if (latest.status === "completed") {
            toast.success(`导入完成，共 ${latest.successCount ?? 0} 个文档`);
          } else if (latest.status === "partial") {
            toast.warning(`部分成功：${latest.successCount ?? 0} 成功，${latest.failedCount ?? 0} 失败`);
          }
        }
      } catch {
        // 轮询失败时静默重试
      }
    }, 2000);
  };

  const handleImport = async () => {
    const processModeError = validateProcessModeValues({ processMode, chunkStrategy, configValues, pipelineId });
    if (processModeError) {
      toast.error(processModeError);
      return;
    }
    setImporting(true);
    try {
      const created = await startFeishuWikiImport(kbId, buildPayload());
      setJob(created);
      setStep("importing");
      startPolling(created.id);
      toast.success("已启动批量导入任务");
    } catch (error) {
      toast.error(getErrorMessage(error, "启动导入失败"));
      setImporting(false);
    }
  };

  const handleClose = (next: boolean) => {
    if (importing) {
      toast.message("导入任务在后台继续执行");
    }
    onOpenChange(next);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>飞书 Wiki 批量导入</DialogTitle>
          <DialogDescription>
            粘贴飞书知识库页面链接，系统将遍历并导入 docx 类型页面。需在服务端配置飞书 OpenAPI 凭证。
          </DialogDescription>
        </DialogHeader>

        {step === "config" && (
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="feishu-root-url">Wiki 页面链接</Label>
              <Input
                id="feishu-root-url"
                placeholder="https://xxx.feishu.cn/wiki/xxx"
                value={rootUrl}
                onChange={(e) => setRootUrl(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>导入范围</Label>
              <Select value={scope} onValueChange={(v) => setScope(v as FeishuWikiImportScope)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SCOPE_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-3 rounded-lg border p-3">
              <DocumentProcessModeSection
                active={open}
                processMode={processMode}
                onProcessModeChange={setProcessMode}
                chunkStrategy={chunkStrategy}
                onChunkStrategyChange={setChunkStrategy}
                configValues={configValues}
                onConfigValuesChange={setConfigValues}
                pipelineId={pipelineId}
                onPipelineIdChange={setPipelineId}
                noChunk={noChunk}
                onNoChunkChange={setNoChunk}
                onChunkStrategiesReady={setChunkStrategies}
              />
            </div>

            <div className="flex items-center gap-2">
              <Checkbox id="auto-chunk" checked={autoChunk} onCheckedChange={(v) => setAutoChunk(v === true)} />
              <Label htmlFor="auto-chunk" className="font-normal">
                导入后自动分块
              </Label>
            </div>
            <div className="flex items-center gap-2">
              <Checkbox
                id="schedule-enabled"
                checked={scheduleEnabled}
                onCheckedChange={(v) => setScheduleEnabled(v === true)}
              />
              <Label htmlFor="schedule-enabled" className="font-normal">
                开启定时刷新
              </Label>
            </div>
            {scheduleEnabled && (
              <div className="space-y-2">
                <Label htmlFor="schedule-cron">Cron 表达式</Label>
                <Input
                  id="schedule-cron"
                  value={scheduleCron}
                  onChange={(e) => setScheduleCron(e.target.value)}
                  placeholder="0 0 * * *"
                />
              </div>
            )}
          </div>
        )}

        {step === "preview" && preview && (
          <div className="space-y-3 py-2">
            <p className="text-sm text-slate-600">
              发现 <strong>{preview.pages?.length ?? 0}</strong> 个可导入页面
              {(preview.skipped?.length ?? 0) > 0 && (
                <span>，跳过 {preview.skipped.length} 个非 docx 节点</span>
              )}
            </p>
            <div className="max-h-64 overflow-auto rounded border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>标题</TableHead>
                    <TableHead className="w-28">类型</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(preview.pages ?? []).map((page) => (
                    <TableRow key={page.nodeToken}>
                      <TableCell className="max-w-xs truncate">{page.title || page.nodeToken}</TableCell>
                      <TableCell>{page.objType || "docx"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
            {(preview.skipped?.length ?? 0) > 0 && (
              <p className="text-xs text-amber-600">
                已跳过：{preview.skipped.map((s) => s.title || s.nodeToken).join("、")}
              </p>
            )}
          </div>
        )}

        {step === "importing" && job && (
          <div className="space-y-3 py-4">
            <div className="flex items-center gap-2 text-sm">
              {importing && <Loader2 className="h-4 w-4 animate-spin" />}
              <span>
                状态：{job.status} · 进度 {job.successCount ?? 0}/{job.totalCount ?? 0}
                {(job.failedCount ?? 0) > 0 && ` · 失败 ${job.failedCount}`}
              </span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-slate-100">
              <div
                className="h-full bg-primary transition-all"
                style={{
                  width: `${job.totalCount ? Math.min(100, ((job.successCount ?? 0) / job.totalCount) * 100) : 0}%`
                }}
              />
            </div>
          </div>
        )}

        <DialogFooter>
          {step === "config" && (
            <>
              <Button variant="outline" onClick={() => handleClose(false)}>
                取消
              </Button>
              <Button onClick={handleDiscover} disabled={discovering}>
                {discovering && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                预览页面
              </Button>
            </>
          )}
          {step === "preview" && (
            <>
              <Button variant="outline" onClick={() => setStep("config")}>
                返回
              </Button>
              <Button onClick={handleImport} disabled={importing || !(preview?.pages?.length)}>
                {importing && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                确认导入
              </Button>
            </>
          )}
          {step === "importing" && (
            <Button variant="outline" onClick={() => handleClose(false)}>
              关闭
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

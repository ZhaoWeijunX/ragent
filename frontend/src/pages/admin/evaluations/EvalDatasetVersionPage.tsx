import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Download, FileUp, Pencil, Plus, RefreshCw, ShieldCheck, Trash2, Upload } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog";
import type {
  EvalCase,
  EvalDatasetVersion,
  EvalImportIssue,
  EvalValidateResult,
  PageResult
} from "@/services/evaluationService";
import {
  createCase,
  deleteCase,
  exportVersion,
  getVersion,
  importCases,
  pageCases,
  publishVersion,
  updateCase,
  validateVersion
} from "@/services/evaluationService";
import { getErrorMessage } from "@/utils/error";
import { cn } from "@/lib/utils";
import { EvalStatusBadge } from "@/pages/admin/evaluations/EvalStatusBadge";

const PAGE_SIZE = 20;

type CaseFormState = {
  queryId: string;
  query: string;
  intentL1: string;
  intentL2: string;
  difficulty: string;
  requiresRag: boolean;
  expectedAnswerType: string;
  expectedDocIds: string;
  niceToHaveDocIds: string;
  groundTruth: string;
  trapType: string;
  tags: string;
  enabledMetrics: string;
  metadata: string;
};

const EMPTY_CASE_FORM: CaseFormState = {
  queryId: "",
  query: "",
  intentL1: "",
  intentL2: "",
  difficulty: "medium",
  requiresRag: true,
  expectedAnswerType: "",
  expectedDocIds: "",
  niceToHaveDocIds: "",
  groundTruth: "",
  trapType: "",
  tags: "",
  enabledMetrics: "",
  metadata: ""
};

function splitCsv(value: string): string[] {
  return value
    .split(/[,，\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function formatMetadata(value?: Record<string, unknown> | null): string {
  if (!value || Object.keys(value).length === 0) return "";
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return "";
  }
}

function parseMetadata(raw: string): Record<string, unknown> | undefined {
  const text = raw.trim();
  if (!text) return {};
  const parsed = JSON.parse(text) as unknown;
  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("metadata 必须是 JSON 对象，例如 {\"source\":\"manual\"}");
  }
  return parsed as Record<string, unknown>;
}

function toCaseForm(item?: EvalCase | null): CaseFormState {
  if (!item) return { ...EMPTY_CASE_FORM };
  return {
    queryId: item.queryId || "",
    query: item.query || "",
    intentL1: item.intentL1 || "",
    intentL2: item.intentL2 || "",
    difficulty: item.difficulty || "medium",
    requiresRag: Boolean(item.requiresRag),
    expectedAnswerType: item.expectedAnswerType || "",
    expectedDocIds: (item.expectedDocIds || []).join(", "),
    niceToHaveDocIds: (item.niceToHaveDocIds || []).join(", "),
    groundTruth: item.groundTruth || "",
    trapType: item.trapType || "",
    tags: (item.tags || []).join(", "),
    enabledMetrics: (item.enabledMetrics || []).join(", "),
    metadata: formatMetadata(item.metadata)
  };
}

function toCasePayload(form: CaseFormState, includeQueryId: boolean) {
  const payload: Record<string, unknown> = {
    query: form.query.trim(),
    intentL1: form.intentL1.trim() || undefined,
    intentL2: form.intentL2.trim() || undefined,
    difficulty: form.difficulty,
    requiresRag: form.requiresRag,
    expectedAnswerType: form.expectedAnswerType.trim() || undefined,
    expectedDocIds: splitCsv(form.expectedDocIds),
    niceToHaveDocIds: splitCsv(form.niceToHaveDocIds),
    groundTruth: form.groundTruth,
    trapType: form.trapType.trim() || undefined,
    tags: splitCsv(form.tags),
    enabledMetrics: splitCsv(form.enabledMetrics),
    metadata: parseMetadata(form.metadata)
  };
  if (includeQueryId) {
    payload.queryId = form.queryId.trim();
  }
  return payload;
}

export function EvalDatasetVersionPage() {
  const { versionId = "" } = useParams();
  const navigate = useNavigate();
  const fileRef = useRef<HTMLInputElement>(null);
  const [version, setVersion] = useState<EvalDatasetVersion | null>(null);
  const [pageData, setPageData] = useState<PageResult<EvalCase> | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [searchKeyword, setSearchKeyword] = useState("");
  const [issues, setIssues] = useState<EvalImportIssue[]>([]);
  const [loading, setLoading] = useState(true);
  const [publishOpen, setPublishOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [publishChecking, setPublishChecking] = useState(false);
  const [publishPreview, setPublishPreview] = useState<EvalValidateResult | null>(null);
  const [importConfirmOpen, setImportConfirmOpen] = useState(false);
  const [pendingImportFile, setPendingImportFile] = useState<File | null>(null);
  const [importing, setImporting] = useState(false);
  const [caseDialogMode, setCaseDialogMode] = useState<"create" | "edit" | null>(null);
  const [editingCase, setEditingCase] = useState<EvalCase | null>(null);
  const [caseForm, setCaseForm] = useState<CaseFormState>(EMPTY_CASE_FORM);
  const [savingCase, setSavingCase] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<EvalCase | null>(null);
  const [deletingCase, setDeletingCase] = useState(false);
  const [validateResult, setValidateResult] = useState<EvalValidateResult | null>(null);
  const [issueSummary, setIssueSummary] = useState<{
    errorCount: number;
    warningCount: number;
    publishable?: boolean;
  } | null>(null);
  const issuesRef = useRef<HTMLDivElement>(null);
  const isDraft = version?.status === "DRAFT";

  const showIssuesPanel = (nextIssues: EvalImportIssue[], summary?: {
    errorCount: number;
    warningCount: number;
    publishable?: boolean;
  }) => {
    setIssues(nextIssues || []);
    setIssueSummary(summary ?? null);
    if ((nextIssues || []).length > 0) {
      requestAnimationFrame(() => {
        issuesRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    }
  };

  const load = async (current = pageNo, kw = keyword) => {
    try {
      setLoading(true);
      const [v, cases] = await Promise.all([
        getVersion(versionId),
        pageCases(versionId, current, PAGE_SIZE, { keyword: kw || undefined })
      ]);
      setVersion(v);
      setPageData(cases);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载版本失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (versionId) load();
  }, [versionId, pageNo, keyword]);

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(searchKeyword.trim());
  };

  const openCreateCase = () => {
    setEditingCase(null);
    setCaseForm({ ...EMPTY_CASE_FORM });
    setCaseDialogMode("create");
  };

  const openEditCase = (item: EvalCase) => {
    setEditingCase(item);
    setCaseForm(toCaseForm(item));
    setCaseDialogMode("edit");
  };

  const closeCaseDialog = () => {
    if (savingCase) return;
    setCaseDialogMode(null);
    setEditingCase(null);
  };

  const handleSaveCase = async () => {
    if (caseDialogMode === "create" && !caseForm.queryId.trim()) {
      toast.error("请填写 queryId");
      return;
    }
    if (!caseForm.query.trim()) {
      toast.error("请填写问题 query");
      return;
    }
    try {
      setSavingCase(true);
      const payload = toCasePayload(caseForm, caseDialogMode === "create");
      if (caseDialogMode === "create") {
        await createCase(versionId, payload);
        toast.success("样本已新增");
        setCaseDialogMode(null);
        setPageNo(1);
        await load(1, keyword);
      } else if (editingCase) {
        await updateCase(editingCase.id, payload);
        toast.success("样本已更新");
        setCaseDialogMode(null);
        setEditingCase(null);
        await load();
      }
    } catch (error) {
      toast.error(getErrorMessage(error, "保存样本失败"));
    } finally {
      setSavingCase(false);
    }
  };

  const handleDeleteCase = async () => {
    if (!deleteTarget) return;
    try {
      setDeletingCase(true);
      await deleteCase(deleteTarget.id);
      toast.success("样本已删除");
      setDeleteTarget(null);
      await load();
    } catch (error) {
      toast.error(getErrorMessage(error, "删除样本失败"));
    } finally {
      setDeletingCase(false);
    }
  };

  const handleImport = async (file?: File | null) => {
    if (!file) return;
    try {
      setImporting(true);
      const result = await importCases(versionId, file);
      showIssuesPanel(result.issues || [], {
        errorCount: result.failedCount,
        warningCount: result.warningCount
      });
      const summary = `导入完成：成功 ${result.successCount}，错误 ${result.failedCount}，警告 ${result.warningCount}`;
      if (result.failedCount > 0) {
        toast.error(summary);
      } else if (result.warningCount > 0) {
        toast.warning(summary);
      } else {
        toast.success(summary);
      }
      setImportConfirmOpen(false);
      setPendingImportFile(null);
      setPageNo(1);
      await load(1, keyword);
    } catch (error) {
      toast.error(getErrorMessage(error, "导入失败"));
    } finally {
      setImporting(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  const requestImport = (file?: File | null) => {
    if (!file) return;
    setPendingImportFile(file);
    setImportConfirmOpen(true);
  };

  const cancelImport = () => {
    if (importing) return;
    setImportConfirmOpen(false);
    setPendingImportFile(null);
    if (fileRef.current) fileRef.current.value = "";
  };

  const runValidate = async (options?: { silentSuccess?: boolean }) => {
    const result = await validateVersion(versionId);
    showIssuesPanel(result.issues || [], {
      errorCount: result.errorCount,
      warningCount: result.warningCount,
      publishable: result.publishable
    });
    if (result.publishable) {
      setValidateResult(null);
      if (!options?.silentSuccess) {
        toast.success(`校验通过：${result.sampleCount} 条样本，警告 ${result.warningCount}`);
      }
    } else {
      setValidateResult(result);
      const firstError = (result.issues || []).find((item) => item.level === "ERROR");
      toast.error(
        firstError
          ? `校验未通过：${result.errorCount} 个错误。例如 ${firstError.code}：${firstError.message}`
          : `校验未通过：错误 ${result.errorCount}，样本 ${result.sampleCount}（无法发布）`
      );
    }
    return result;
  };

  const handleValidate = async () => {
    try {
      await runValidate();
    } catch (error) {
      toast.error(getErrorMessage(error, "校验失败"));
    }
  };

  const handlePublishClick = async () => {
    try {
      setPublishChecking(true);
      const result = await runValidate({ silentSuccess: true });
      if (!result.publishable) {
        setPublishPreview(null);
        toast.error("校验未通过，已阻止发布。请先修复错误样本后再试。");
        return;
      }
      setPublishPreview(result);
      if (result.warningCount > 0) {
        toast.warning(`校验可发布，但有 ${result.warningCount} 条警告，请确认后再发布`);
      } else {
        toast.success(`校验通过（${result.sampleCount} 条），可确认发布`);
      }
      setPublishOpen(true);
    } catch (error) {
      toast.error(getErrorMessage(error, "发布前校验失败"));
    } finally {
      setPublishChecking(false);
    }
  };

  const handlePublish = async () => {
    try {
      setPublishing(true);
      // 再次校验，防止确认期间样本被改动
      const result = await runValidate({ silentSuccess: true });
      if (!result.publishable) {
        setPublishOpen(false);
        setPublishPreview(null);
        toast.error("校验未通过，已取消发布");
        return;
      }
      await publishVersion(versionId);
      toast.success("发布成功（版本已不可变）");
      setPublishOpen(false);
      setPublishPreview(null);
      load();
    } catch (error) {
      toast.error(getErrorMessage(error, "发布失败"));
    } finally {
      setPublishing(false);
    }
  };

  const handleExport = async () => {
    try {
      const blob = await exportVersion(versionId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `eval-${version?.version || versionId}.jsonl`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      toast.error(getErrorMessage(error, "导出失败"));
    }
  };

  const backToDataset = () => {
    if (version?.datasetId) {
      navigate(`/admin/evaluations/datasets/${version.datasetId}`);
    } else {
      navigate("/admin/evaluations/datasets");
    }
  };

  const hasFileLineNumbers = issues.some((item) => item.line != null);

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title flex flex-wrap items-center gap-2">
            {version?.version || "版本详情"}
            <EvalStatusBadge kind="version" status={version?.status} className="align-middle text-sm" />
          </h1>
          <p className="admin-page-subtitle">
            {version?.datasetName ? `${version.datasetName} · ` : ""}
            样本 {version?.sampleCount ?? 0}
            {!isDraft
              ? " · 已发布版本不可原地修改，请复制为新草稿"
              : " · 可单条编辑、导入 JSONL/JSON 并发布"}
          </p>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={backToDataset}>
            返回评估集
          </Button>
          <Button variant="outline" onClick={() => load()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            刷新
          </Button>
          <Button variant="outline" onClick={handleExport}>
            <Download className="mr-2 h-4 w-4" />
            导出
          </Button>
          {isDraft && (
            <>
              <input
                ref={fileRef}
                type="file"
                accept=".json,.jsonl,application/json"
                className="hidden"
                onChange={(e) => requestImport(e.target.files?.[0])}
              />
              <Button variant="outline" onClick={() => fileRef.current?.click()}>
                <Upload className="mr-2 h-4 w-4" />
                导入
              </Button>
              <Button variant="outline" onClick={handleValidate}>
                <ShieldCheck className="mr-2 h-4 w-4" />
                校验
              </Button>
              <Button
                className="admin-primary-gradient"
                onClick={handlePublishClick}
                disabled={publishChecking || publishing}
              >
                <FileUp className="mr-2 h-4 w-4" />
                {publishChecking ? "校验中..." : "发布"}
              </Button>
            </>
          )}
        </div>
      </div>

      <Card>
        <CardHeader>
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle>样本列表</CardTitle>
              <CardDescription>
                {isDraft ? "草稿可新增 / 编辑 / 删除单条样本，也支持整包导入" : "已发布版本只读，可导出"}
              </CardDescription>
            </div>
            <div className="flex flex-1 flex-wrap items-center justify-end gap-2">
              <Input
                placeholder="搜索 queryId / query"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleSearch();
                }}
                className="max-w-xs"
              />
              <Button variant="outline" onClick={handleSearch}>
                搜索
              </Button>
              {isDraft && (
                <Button className="admin-primary-gradient" onClick={openCreateCase}>
                  <Plus className="mr-2 h-4 w-4" />
                  新增样本
                </Button>
              )}
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {loading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : !pageData?.records?.length ? (
            <div className="py-8 text-center text-muted-foreground">
              {isDraft ? "暂无样本，可点「新增样本」或导入 JSONL" : "暂无样本"}
            </div>
          ) : (
            <Table className="min-w-[980px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[120px]">queryId</TableHead>
                  <TableHead>问题</TableHead>
                  <TableHead>意图</TableHead>
                  <TableHead>难度</TableHead>
                  <TableHead>requiresRag</TableHead>
                  <TableHead>gold docs</TableHead>
                  {isDraft && <TableHead className="w-[160px] text-left">操作</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {pageData.records.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-mono text-xs">{item.queryId}</TableCell>
                    <TableCell className="max-w-[250px] truncate" title={item.query}>
                      {item.query}
                    </TableCell>
                    <TableCell className="text-xs">
                      {item.intentL1 || "-"} / {item.intentL2 || "-"}
                    </TableCell>
                    <TableCell>{item.difficulty || "-"}</TableCell>
                    <TableCell>{item.requiresRag ? "true" : "false"}</TableCell>
                    <TableCell className="max-w-[180px] truncate text-xs">
                      {(item.expectedDocIds || []).join(", ") || "-"}
                    </TableCell>
                    {isDraft && (
                      <TableCell>
                        <div className="flex gap-2">
                          <Button variant="outline" size="sm" onClick={() => openEditCase(item)}>
                            <Pencil className="mr-0.5 h-4 w-4" />
                            编辑
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:text-destructive"
                            onClick={() => setDeleteTarget(item)}
                          >
                            <Trash2 className="mr-0.5 h-4 w-4" />
                            删除
                          </Button>
                        </div>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          {pageData ? (
            <div className="flex items-center justify-between text-sm text-muted-foreground">
              <span>共 {pageData.total ?? 0} 条</span>
              <div className="flex items-center gap-2">
                <Button size="sm" variant="outline" disabled={pageNo <= 1} onClick={() => setPageNo((p) => p - 1)}>
                  上一页
                </Button>
                <span>
                  {pageData.current} / {pageData.pages}
                </span>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={!pageData || pageNo >= (pageData.pages || 1)}
                  onClick={() => setPageNo((p) => p + 1)}
                >
                  下一页
                </Button>
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>

      {!!issues.length && (
        <Card ref={issuesRef}>
          <CardHeader>
            <CardTitle className="text-base">
              校验 / 导入问题（{issues.length}）
              {issueSummary ? (
                <span className="ml-2 text-sm font-normal text-muted-foreground">
                  错误 {issueSummary.errorCount} · 警告 {issueSummary.warningCount}
                  {typeof issueSummary.publishable === "boolean"
                    ? issueSummary.publishable
                      ? " · 可发布"
                      : " · 不可发布"
                    : ""}
                </span>
              ) : null}
            </CardTitle>
            <CardDescription>
              {hasFileLineNumbers
                ? "「行」为导入文件中的行号；已入库样本校验请以 queryId 定位。"
                : "当前为已入库样本校验结果，无文件行号；请以 queryId 定位问题样本。"}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[90px]">级别</TableHead>
                  {hasFileLineNumbers && <TableHead className="w-[70px]">行</TableHead>}
                  <TableHead className="w-[120px]">queryId</TableHead>
                  <TableHead className="w-[160px]">代码</TableHead>
                  <TableHead>说明</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {issues.slice(0, 100).map((issue, idx) => (
                  <TableRow
                    key={`${issue.code}-${idx}`}
                    className={cn(issue.level === "ERROR" && "bg-destructive/5")}
                  >
                    <TableCell className={cn(issue.level === "ERROR" && "font-medium text-destructive")}>
                      {issue.level}
                    </TableCell>
                    {hasFileLineNumbers && <TableCell>{issue.line ?? "-"}</TableCell>}
                    <TableCell className="font-mono text-xs">{issue.queryId || "-"}</TableCell>
                    <TableCell className="font-mono text-xs">{issue.code}</TableCell>
                    <TableCell>{issue.message}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <Dialog open={!!caseDialogMode} onOpenChange={(open) => !open && closeCaseDialog()}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-[640px]">
          <DialogHeader>
            <DialogTitle>{caseDialogMode === "create" ? "新增样本" : "编辑样本"}</DialogTitle>
            <DialogDescription>
              {caseDialogMode === "create"
                ? "填写样本字段后保存到当前草稿版本"
                : `queryId「${editingCase?.queryId}」不可修改；如需更换请删除后重建`}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-2 sm:col-span-2">
              <Label>queryId {caseDialogMode === "create" ? "*" : ""}</Label>
              <Input
                value={caseForm.queryId}
                disabled={caseDialogMode === "edit"}
                onChange={(e) => setCaseForm({ ...caseForm, queryId: e.target.value })}
                placeholder="例如 F1-01"
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label>问题 query *</Label>
              <Textarea
                value={caseForm.query}
                onChange={(e) => setCaseForm({ ...caseForm, query: e.target.value })}
                placeholder="用户问题"
                rows={3}
              />
            </div>
            <div className="space-y-2">
              <Label>intentL1</Label>
              <Input
                value={caseForm.intentL1}
                onChange={(e) => setCaseForm({ ...caseForm, intentL1: e.target.value })}
                placeholder="可选"
              />
            </div>
            <div className="space-y-2">
              <Label>intentL2</Label>
              <Input
                value={caseForm.intentL2}
                onChange={(e) => setCaseForm({ ...caseForm, intentL2: e.target.value })}
                placeholder="可选"
              />
            </div>
            <div className="space-y-2">
              <Label>难度</Label>
              <Select
                value={caseForm.difficulty}
                onValueChange={(value) => setCaseForm({ ...caseForm, difficulty: value })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="easy">easy</SelectItem>
                  <SelectItem value="medium">medium</SelectItem>
                  <SelectItem value="hard">hard</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>答案类型</Label>
              <Input
                value={caseForm.expectedAnswerType}
                onChange={(e) => setCaseForm({ ...caseForm, expectedAnswerType: e.target.value })}
                placeholder="可选"
              />
            </div>
            <div className="flex items-center gap-2 sm:col-span-2">
              <Checkbox
                id="requiresRag"
                checked={caseForm.requiresRag}
                onCheckedChange={(checked) => setCaseForm({ ...caseForm, requiresRag: checked === true })}
              />
              <Label htmlFor="requiresRag">requiresRag（应走 RAG）</Label>
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label>expectedDocIds</Label>
              <Input
                value={caseForm.expectedDocIds}
                onChange={(e) => setCaseForm({ ...caseForm, expectedDocIds: e.target.value })}
                placeholder="多个用逗号分隔，如 FAQ_VAC_001, CODE_VAC_001"
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label>niceToHaveDocIds</Label>
              <Input
                value={caseForm.niceToHaveDocIds}
                onChange={(e) => setCaseForm({ ...caseForm, niceToHaveDocIds: e.target.value })}
                placeholder="可选，逗号分隔"
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label>groundTruth</Label>
              <Textarea
                value={caseForm.groundTruth}
                onChange={(e) => setCaseForm({ ...caseForm, groundTruth: e.target.value })}
                placeholder="标准答案（可选）"
                rows={4}
              />
            </div>
            <div className="space-y-2">
              <Label>trapType</Label>
              <Input
                value={caseForm.trapType}
                onChange={(e) => setCaseForm({ ...caseForm, trapType: e.target.value })}
                placeholder="如 budget_scene / gift_scene"
              />
            </div>
            <div className="space-y-2">
              <Label>tags</Label>
              <Input
                value={caseForm.tags}
                onChange={(e) => setCaseForm({ ...caseForm, tags: e.target.value })}
                placeholder="可选，逗号分隔"
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label>enabledMetrics</Label>
              <Input
                value={caseForm.enabledMetrics}
                onChange={(e) => setCaseForm({ ...caseForm, enabledMetrics: e.target.value })}
                placeholder="可选，逗号分隔，如 recall@3, recall@5, hit@5；空则用 Run 默认指标"
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label>metadata（JSON 对象）</Label>
              <Textarea
                value={caseForm.metadata}
                onChange={(e) => setCaseForm({ ...caseForm, metadata: e.target.value })}
                placeholder='可选，例如 {"source":"manual","note":"..."}'
                rows={4}
                className="font-mono text-xs"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={closeCaseDialog} disabled={savingCase}>
              取消
            </Button>
            <Button onClick={handleSaveCase} disabled={savingCase}>
              {savingCase ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={!!deleteTarget}
        onOpenChange={(open) => {
          if (!deletingCase && !open) setDeleteTarget(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除样本？</AlertDialogTitle>
            <AlertDialogDescription>
              将删除样本 <span className="font-mono">{deleteTarget?.queryId}</span>
              {deleteTarget?.query ? `（${deleteTarget.query}）` : ""}。此操作不可恢复，可稍后重新新增或导入。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deletingCase}>取消</AlertDialogCancel>
            <AlertDialogAction
              disabled={deletingCase}
              className="bg-destructive text-destructive-foreground"
              onClick={(e) => {
                e.preventDefault();
                handleDeleteCase();
              }}
            >
              {deletingCase ? "删除中..." : "确认删除"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={!!validateResult} onOpenChange={(open) => !open && setValidateResult(null)}>
        <AlertDialogContent className="max-w-lg">
          <AlertDialogHeader>
            <AlertDialogTitle>
              {validateResult?.publishable ? "校验通过" : "校验未通过，无法发布"}
            </AlertDialogTitle>
            <AlertDialogDescription asChild>
              <div className="space-y-3 text-sm text-muted-foreground">
                <p>
                  样本 {validateResult?.sampleCount ?? 0} 条 · 错误 {validateResult?.errorCount ?? 0} · 警告{" "}
                  {validateResult?.warningCount ?? 0}
                </p>
                {!validateResult?.publishable && (validateResult?.sampleCount ?? 0) === 0 ? (
                  <p>当前版本没有样本，请先新增或导入后再校验。</p>
                ) : null}
                {(validateResult?.issues || [])
                  .filter((item) => item.level === "ERROR")
                  .slice(0, 5)
                  .map((item, idx) => (
                    <p key={`${item.code}-${idx}`} className="text-destructive">
                      [{item.code}] {item.queryId ? `${item.queryId}：` : ""}
                      {item.message}
                    </p>
                  ))}
                {(validateResult?.errorCount ?? 0) > 5 ? (
                  <p>另有 {(validateResult?.errorCount ?? 0) - 5} 条错误，详见下方问题列表。</p>
                ) : null}
                {(validateResult?.issues || []).length > 0 ? (
                  <p>完整问题列表已展示在页面下方，可滚动查看。</p>
                ) : null}
              </div>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogAction onClick={() => setValidateResult(null)}>知道了</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={importConfirmOpen}
        onOpenChange={(open) => {
          if (!open) cancelImport();
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认整包导入？</AlertDialogTitle>
            <AlertDialogDescription>
              将用文件「{pendingImportFile?.name}」替换当前草稿中的全部样本（现有{" "}
              {version?.sampleCount ?? 0} 条会被清空后重写）。仅成功解析的行会入库，失败行见导入问题列表。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={importing} onClick={cancelImport}>
              取消
            </AlertDialogCancel>
            <AlertDialogAction
              disabled={importing || !pendingImportFile}
              onClick={(e) => {
                e.preventDefault();
                handleImport(pendingImportFile);
              }}
            >
              {importing ? "导入中..." : "确认导入"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={publishOpen}
        onOpenChange={(open) => {
          if (!publishing) {
            setPublishOpen(open);
            if (!open) setPublishPreview(null);
          }
        }}
      >
        <AlertDialogContent className="max-w-lg">
          <AlertDialogHeader>
            <AlertDialogTitle>发布版本？</AlertDialogTitle>
            <AlertDialogDescription asChild>
              <div className="space-y-3 text-sm text-muted-foreground">
                <p>
                  校验已通过：样本 {publishPreview?.sampleCount ?? 0} 条 · 错误{" "}
                  {publishPreview?.errorCount ?? 0} · 警告 {publishPreview?.warningCount ?? 0}。
                </p>
                {(publishPreview?.warningCount ?? 0) > 0 ? (
                  <>
                    <p className="font-medium text-amber-700">
                      存在 {publishPreview?.warningCount} 条警告，发布后仍可使用，但可能影响评测质量，请确认已知晓。
                    </p>
                    {(publishPreview?.issues || [])
                      .filter((item) => item.level === "WARNING")
                      .slice(0, 5)
                      .map((item, idx) => (
                        <p key={`${item.code}-${idx}`} className="text-amber-700">
                          [{item.code}] {item.queryId ? `${item.queryId}：` : ""}
                          {item.message}
                        </p>
                      ))}
                    {(publishPreview?.warningCount ?? 0) > 5 ? (
                      <p>另有 {(publishPreview?.warningCount ?? 0) - 5} 条警告，详见页面下方问题列表。</p>
                    ) : null}
                  </>
                ) : (
                  <p>无警告。发布后样本不可修改，Run 只能引用 PUBLISHED 版本。</p>
                )}
                {(publishPreview?.warningCount ?? 0) > 0 ? (
                  <p>发布后样本不可修改，Run 只能引用 PUBLISHED 版本。</p>
                ) : null}
              </div>
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={publishing}>取消</AlertDialogCancel>
            <AlertDialogAction
              disabled={publishing}
              onClick={(e) => {
                e.preventDefault();
                handlePublish();
              }}
            >
              {publishing ? "发布中..." : "确认发布"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

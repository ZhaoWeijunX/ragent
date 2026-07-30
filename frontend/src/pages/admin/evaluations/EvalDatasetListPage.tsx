import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Archive, ArchiveRestore, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
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
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { RelativeTime } from "@/components/RelativeTime";
import { EvalStatusBadge } from "@/pages/admin/evaluations/EvalStatusBadge";
import type { EvalDataset, PageResult } from "@/services/evaluationService";
import { createDataset, deleteDataset, pageDatasets, updateDataset } from "@/services/evaluationService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;
const EMPTY_FORM = { name: "", description: "", domain: "" };

export function EvalDatasetListPage() {
  const navigate = useNavigate();
  const [pageData, setPageData] = useState<PageResult<EvalDataset> | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<"active" | "ARCHIVED">("active");
  const [deleteTarget, setDeleteTarget] = useState<EvalDataset | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<EvalDataset | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editForm, setEditForm] = useState(EMPTY_FORM);

  const load = async (current = pageNo, kw = keyword, status = statusFilter) => {
    try {
      setLoading(true);
      // 默认列表排除已归档；选「已归档」时显式按 ARCHIVED 查询
      const statusParam = status === "ARCHIVED" ? "ARCHIVED" : undefined;
      setPageData(await pageDatasets(current, PAGE_SIZE, kw || undefined, statusParam));
    } catch (error) {
      toast.error(getErrorMessage(error, "加载评估集失败（请确认 app.eval.workbench-enabled=true）"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [pageNo, keyword, statusFilter]);

  const handleSearch = () => {
    setPageNo(1);
    setKeyword(searchKeyword.trim());
  };

  const openEdit = (item: EvalDataset) => {
    setEditTarget(item);
    setEditForm({
      name: item.name || "",
      domain: item.domain || "",
      description: item.description || ""
    });
  };

  const handleCreate = async () => {
    if (!form.name.trim()) {
      toast.error("请输入评估集名称");
      return;
    }
    try {
      await createDataset({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        domain: form.domain.trim() || undefined
      });
      toast.success("创建成功");
      setDialogOpen(false);
      setForm(EMPTY_FORM);
      setPageNo(1);
      await load(1, keyword, statusFilter);
    } catch (error) {
      toast.error(getErrorMessage(error, "创建失败"));
    }
  };

  const handleEdit = async () => {
    if (!editTarget) return;
    if (!editForm.name.trim()) {
      toast.error("请输入评估集名称");
      return;
    }
    try {
      await updateDataset(editTarget.id, {
        name: editForm.name.trim(),
        domain: editForm.domain.trim(),
        description: editForm.description.trim()
      });
      toast.success("已保存");
      setEditTarget(null);
      load();
    } catch (error) {
      toast.error(getErrorMessage(error, "保存失败"));
    }
  };

  const handleArchive = async (item: EvalDataset) => {
    try {
      await updateDataset(item.id, { status: "ARCHIVED" });
      toast.success("已归档，可在「已归档」筛选中恢复");
      load();
    } catch (error) {
      toast.error(getErrorMessage(error, "归档失败"));
    }
  };

  const handleRestore = async (item: EvalDataset) => {
    try {
      await updateDataset(item.id, { status: "ACTIVE" });
      toast.success("已恢复，将重新出现在默认列表");
      load();
    } catch (error) {
      toast.error(getErrorMessage(error, "恢复失败"));
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteDataset(deleteTarget.id);
      toast.success("已删除");
      setDeleteTarget(null);
      load();
    } catch (error) {
      toast.error(getErrorMessage(error, "删除失败"));
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">评估集</h1>
          <p className="admin-page-subtitle">导入 / 校验 / 发布评估集版本，供后续评测 Run 引用</p>
        </div>
        <div className="admin-page-actions">
          <Select
            value={statusFilter}
            onValueChange={(value: "active" | "ARCHIVED") => {
              setPageNo(1);
              setStatusFilter(value);
            }}
          >
            <SelectTrigger className="w-[140px]">
              <SelectValue placeholder="状态" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="active">默认（活跃）</SelectItem>
              <SelectItem value="ARCHIVED">已归档</SelectItem>
            </SelectContent>
          </Select>
          <Input
            placeholder="按名称搜索"
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSearch();
            }}
            className="w-[220px]"
          />
          <Button variant="outline" onClick={handleSearch}>
            搜索
          </Button>
          <Button variant="outline" onClick={() => load()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            刷新
          </Button>
          <Button className="admin-primary-gradient" onClick={() => setDialogOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            新建评估集
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="pt-6">
          {loading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : !pageData?.records?.length ? (
            <div className="py-8 text-center text-muted-foreground">
              {statusFilter === "ARCHIVED" ? "暂无已归档评估集" : "暂无评估集，点击上方按钮创建"}
            </div>
          ) : (
            <Table className="min-w-[1080px]">
              <TableHeader>
                <TableRow>
                  <TableHead>名称</TableHead>
                  <TableHead>域</TableHead>
                  <TableHead>最新版本</TableHead>
                  <TableHead>样本数</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead>创建人</TableHead>
                  <TableHead>更新时间</TableHead>
                  <TableHead className="w-[240px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {pageData.records.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="font-medium">
                      <button
                        type="button"
                        className="admin-link max-w-[200px] truncate"
                        onClick={() => navigate(`/admin/evaluations/datasets/${item.id}`)}
                      >
                        {item.name}
                      </button>
                    </TableCell>
                    <TableCell>{item.domain || "-"}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap items-center gap-2">
                        <span>{item.latestVersion || "-"}</span>
                        {item.latestVersionStatus ? (
                          <EvalStatusBadge kind="version" status={item.latestVersionStatus} />
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell>{item.latestSampleCount ?? 0}</TableCell>
                    <TableCell>
                      <EvalStatusBadge kind="dataset" status={item.status} />
                    </TableCell>
                    <TableCell>{item.createdBy || "-"}</TableCell>
                    <TableCell>{item.updateTime ? <RelativeTime value={item.updateTime} /> : "-"}</TableCell>
                    <TableCell>
                      <div className="flex gap-2">
                        <Button variant="outline" size="sm" onClick={() => openEdit(item)}>
                          <Pencil className="mr-0.5 h-4 w-4" />
                          编辑
                        </Button>
                        {item.status === "ARCHIVED" ? (
                          <TooltipProvider delayDuration={200}>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button variant="outline" size="sm" onClick={() => handleRestore(item)}>
                                  <ArchiveRestore className="mr-0.5 h-4 w-4" />
                                  恢复
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent className="max-w-[240px]">
                                <p>恢复为 ACTIVE 后重新出现在默认列表，可用于后续评测。</p>
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        ) : (
                          <TooltipProvider delayDuration={200}>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button variant="outline" size="sm" onClick={() => handleArchive(item)}>
                                  <Archive className="mr-0.5 h-4 w-4" />
                                  归档
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent className="max-w-[240px]">
                                <p>归档后从默认列表隐藏。可在顶部筛选「已归档」后点「恢复」重新启用。</p>
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        )}
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
                  </TableRow>
                ))}
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
            <DialogTitle>新建评估集</DialogTitle>
            <DialogDescription>创建后自动生成草稿版本 v1</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <Input placeholder="名称" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <Input placeholder="业务域（可选）" value={form.domain} onChange={(e) => setForm({ ...form, domain: e.target.value })} />
            <Textarea
              placeholder="描述（可选）"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={handleCreate}>创建</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={!!editTarget}
        onOpenChange={(open) => {
          if (!open) setEditTarget(null);
        }}
      >
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader>
            <DialogTitle>编辑评估集</DialogTitle>
            <DialogDescription>修改名称、业务域与描述</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-2">
              <Label>名称</Label>
              <Input value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label>业务域</Label>
              <Input
                placeholder="可选"
                value={editForm.domain}
                onChange={(e) => setEditForm({ ...editForm, domain: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label>描述</Label>
              <Textarea
                placeholder="可选"
                value={editForm.description}
                onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditTarget(null)}>
              取消
            </Button>
            <Button onClick={handleEdit}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除评估集？</AlertDialogTitle>
            <AlertDialogDescription>若版本已被 Run 引用将无法删除。</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} className="bg-destructive text-destructive-foreground">
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

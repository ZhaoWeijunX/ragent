import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Archive, ArchiveRestore, Copy, Plus, RefreshCw, Trash2 } from "lucide-react";
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
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { RelativeTime } from "@/components/RelativeTime";
import { EvalStatusBadge } from "@/pages/admin/evaluations/EvalStatusBadge";
import type { EvalDataset, EvalDatasetVersion } from "@/services/evaluationService";
import {
  archiveVersion,
  copyVersion,
  createDraftVersion,
  deleteVersion,
  getDataset,
  listVersions,
  unarchiveVersion
} from "@/services/evaluationService";
import { getEvalWorkbenchErrorMessage as getErrorMessage } from "@/utils/error";

export function EvalDatasetDetailPage() {
  const { datasetId = "" } = useParams();
  const navigate = useNavigate();
  const [dataset, setDataset] = useState<EvalDataset | null>(null);
  const [versions, setVersions] = useState<EvalDatasetVersion[]>([]);
  const [loading, setLoading] = useState(true);
  const [deleteTarget, setDeleteTarget] = useState<EvalDatasetVersion | null>(null);
  const [copyTarget, setCopyTarget] = useState<EvalDatasetVersion | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<EvalDatasetVersion | null>(null);
  const [unarchiveTarget, setUnarchiveTarget] = useState<EvalDatasetVersion | null>(null);
  const [copying, setCopying] = useState(false);
  const [archiving, setArchiving] = useState(false);
  const [unarchiving, setUnarchiving] = useState(false);
  const [createDraftOpen, setCreateDraftOpen] = useState(false);
  const [creatingDraft, setCreatingDraft] = useState(false);

  const load = async () => {
    try {
      setLoading(true);
      const [ds, vs] = await Promise.all([getDataset(datasetId), listVersions(datasetId)]);
      setDataset(ds);
      setVersions(vs);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载评估集详情失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (datasetId) load();
  }, [datasetId]);

  const handleCreateDraft = async () => {
    try {
      setCreatingDraft(true);
      const versionId = await createDraftVersion(datasetId);
      toast.success("已创建草稿版本");
      setCreateDraftOpen(false);
      navigate(`/admin/evaluations/dataset-versions/${versionId}`);
    } catch (error) {
      toast.error(getErrorMessage(error, "创建草稿失败"));
    } finally {
      setCreatingDraft(false);
    }
  };

  const handleCopy = async () => {
    if (!copyTarget) return;
    try {
      setCopying(true);
      const newId = await copyVersion(copyTarget.id);
      toast.success("已复制为新草稿");
      setCopyTarget(null);
      navigate(`/admin/evaluations/dataset-versions/${newId}`);
    } catch (error) {
      toast.error(getErrorMessage(error, "复制失败"));
    } finally {
      setCopying(false);
    }
  };

  const handleArchive = async () => {
    if (!archiveTarget) return;
    try {
      setArchiving(true);
      await archiveVersion(archiveTarget.id);
      toast.success(`版本 ${archiveTarget.version} 已归档，不可再被新 Run 引用`);
      setArchiveTarget(null);
      await load();
    } catch (error) {
      toast.error(getErrorMessage(error, "归档失败"));
    } finally {
      setArchiving(false);
    }
  };

  const handleUnarchive = async () => {
    if (!unarchiveTarget) return;
    try {
      setUnarchiving(true);
      await unarchiveVersion(unarchiveTarget.id);
      toast.success(`版本 ${unarchiveTarget.version} 已恢复为已发布，可再次被 Run 引用`);
      setUnarchiveTarget(null);
      await load();
    } catch (error) {
      toast.error(getErrorMessage(error, "恢复失败"));
    } finally {
      setUnarchiving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteVersion(deleteTarget.id);
      toast.success("已删除版本");
      setDeleteTarget(null);
      await load();
    } catch (error) {
      toast.error(getErrorMessage(error, "删除失败"));
    }
  };

  if (loading && !dataset) {
    return <div className="text-sm text-muted-foreground">加载中...</div>;
  }

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">{dataset?.name}</h1>
          <p className="admin-page-subtitle">{dataset?.description || "无描述"}</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={() => navigate("/admin/evaluations/datasets")}>
            返回评估集
          </Button>
          <Button variant="outline" onClick={load}>
            <RefreshCw className="mr-2 h-4 w-4" />
            刷新
          </Button>
          <Button className="admin-primary-gradient" onClick={() => setCreateDraftOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            新建草稿版本
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">版本列表</CardTitle>
        </CardHeader>
        <CardContent>
          {!versions.length ? (
            <div className="py-8 text-center text-muted-foreground">暂无版本</div>
          ) : (
            <Table className="min-w-[860px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[100px]">版本</TableHead>
                  <TableHead className="w-[110px]">状态</TableHead>
                  <TableHead className="w-[90px]">样本数</TableHead>
                  <TableHead className="w-[160px]">发布时间</TableHead>
                  <TableHead className="w-[280px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {versions.map((v) => (
                  <TableRow key={v.id}>
                    <TableCell className="font-medium">
                      <button
                        type="button"
                        className="admin-link"
                        onClick={() => navigate(`/admin/evaluations/dataset-versions/${v.id}`)}
                      >
                        {v.version}
                      </button>
                    </TableCell>
                    <TableCell>
                      <EvalStatusBadge kind="version" status={v.status} />
                    </TableCell>
                    <TableCell>{v.sampleCount ?? 0}</TableCell>
                    <TableCell>{v.publishedAt ? <RelativeTime value={v.publishedAt} /> : "-"}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-2">
                        <Button variant="outline" size="sm" onClick={() => setCopyTarget(v)}>
                          <Copy className="mr-0.5 h-4 w-4" />
                          复制
                        </Button>
                        {v.status === "PUBLISHED" && (
                          <TooltipProvider delayDuration={200}>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button variant="outline" size="sm" onClick={() => setArchiveTarget(v)}>
                                  <Archive className="mr-0.5 h-4 w-4" />
                                  归档
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent className="max-w-[240px]">
                                <p>归档后不可再被新 Run 引用；可随时恢复。草稿请直接删除，无需归档。</p>
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        )}
                        {v.status === "ARCHIVED" && (
                          <TooltipProvider delayDuration={200}>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button variant="outline" size="sm" onClick={() => setUnarchiveTarget(v)}>
                                  <ArchiveRestore className="mr-0.5 h-4 w-4" />
                                  恢复
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent className="max-w-[240px]">
                                <p>恢复为 PUBLISHED 后可再次被新 Run 引用；样本仍不可原地修改。</p>
                              </TooltipContent>
                            </Tooltip>
                          </TooltipProvider>
                        )}
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive hover:text-destructive"
                          onClick={() => setDeleteTarget(v)}
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

      <AlertDialog
        open={createDraftOpen}
        onOpenChange={(open) => {
          if (!creatingDraft) setCreateDraftOpen(open);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>新建草稿版本？</AlertDialogTitle>
            <AlertDialogDescription>
              将创建一个空的草稿版本，可导入样本后校验并发布。已有版本不会被覆盖；若需基于现有样本修改，请使用「复制」。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={creatingDraft}>取消</AlertDialogCancel>
            <AlertDialogAction
              disabled={creatingDraft}
              onClick={(e) => {
                e.preventDefault();
                handleCreateDraft();
              }}
            >
              {creatingDraft ? "创建中..." : "确认创建"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={!!copyTarget}
        onOpenChange={(open) => {
          if (!copying && !open) setCopyTarget(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>复制为新草稿？</AlertDialogTitle>
            <AlertDialogDescription>
              将基于版本 {copyTarget?.version}（{copyTarget?.sampleCount ?? 0} 条样本）创建新的草稿版本，可在新草稿中继续修改后发布。源版本不会被改动。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={copying}>取消</AlertDialogCancel>
            <AlertDialogAction
              disabled={copying}
              onClick={(e) => {
                e.preventDefault();
                handleCopy();
              }}
            >
              {copying ? "复制中..." : "确认复制"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={!!archiveTarget}
        onOpenChange={(open) => {
          if (!archiving && !open) setArchiveTarget(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>归档版本？</AlertDialogTitle>
            <AlertDialogDescription>
              将归档已发布版本 {archiveTarget?.version}。归档后不可再被新 Run 引用，历史 Run 仍保留。之后可通过「恢复」重新发布可用。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={archiving}>取消</AlertDialogCancel>
            <AlertDialogAction
              disabled={archiving}
              onClick={(e) => {
                e.preventDefault();
                handleArchive();
              }}
            >
              {archiving ? "归档中..." : "确认归档"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={!!unarchiveTarget}
        onOpenChange={(open) => {
          if (!unarchiving && !open) setUnarchiveTarget(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>恢复为已发布？</AlertDialogTitle>
            <AlertDialogDescription>
              将版本 {unarchiveTarget?.version} 从 ARCHIVED 恢复为 PUBLISHED，之后可再次被新 Run 引用。样本内容仍保持不可变。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={unarchiving}>取消</AlertDialogCancel>
            <AlertDialogAction
              disabled={unarchiving}
              onClick={(e) => {
                e.preventDefault();
                handleUnarchive();
              }}
            >
              {unarchiving ? "恢复中..." : "确认恢复"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除版本？</AlertDialogTitle>
            <AlertDialogDescription>
              将删除版本 {deleteTarget?.version} 及其全部样本。若已被 Run 引用或为最后一个版本则无法删除。
            </AlertDialogDescription>
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

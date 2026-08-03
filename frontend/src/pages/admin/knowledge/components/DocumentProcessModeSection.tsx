import { useEffect, useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";

import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { IngestionSpecSchema } from "@/services/knowledgeService";
import { getIngestionPipelines, type IngestionPipeline } from "@/services/ingestionService";
import {
  budgetHintOf,
  PROCESS_MODE_OPTIONS,
  type ProcessMode
} from "@/pages/admin/knowledge/utils/documentProcessMode";

interface DocumentProcessModeSectionProps {
  active?: boolean;
  processMode: ProcessMode;
  onProcessModeChange: (mode: ProcessMode) => void;
  schema: IngestionSpecSchema | null;
  parseProfile: string;
  onParseProfileChange: (profile: string) => void;
  configValues: Record<string, string>;
  onConfigValuesChange: (values: Record<string, string>) => void;
  pipelineId: string;
  onPipelineIdChange: (id: string) => void;
  noChunk: boolean;
  onNoChunkChange: (value: boolean) => void;
}

export function DocumentProcessModeSection({
  active = true,
  processMode,
  onProcessModeChange,
  schema,
  parseProfile,
  onParseProfileChange,
  configValues,
  onConfigValuesChange,
  pipelineId,
  onPipelineIdChange,
  noChunk,
  onNoChunkChange
}: DocumentProcessModeSectionProps) {
  const [pipelines, setPipelines] = useState<IngestionPipeline[]>([]);
  const [loadingPipelines, setLoadingPipelines] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);

  const isChunkMode = processMode === "chunk";
  const isPipelineMode = processMode === "pipeline";
  const budgetFields = (schema?.budgetFields ?? []).filter((field) => field.key !== "rowsPerChunk");

  useEffect(() => {
    if (!active) {
      return;
    }
    setLoadingPipelines(true);
    getIngestionPipelines(1, 100)
      .then((result) => setPipelines(result.records || []))
      .catch(() => {})
      .finally(() => setLoadingPipelines(false));
  }, [active]);

  const updateConfigValue = (key: string, value: string) => {
    onConfigValuesChange({ ...configValues, [key]: value });
  };

  return (
    <div className="space-y-3">
      <div className="space-y-2">
        <Label>处理模式</Label>
        <Select value={processMode} onValueChange={(value) => onProcessModeChange(value as ProcessMode)}>
          <SelectTrigger>
            <SelectValue placeholder="选择处理模式" />
          </SelectTrigger>
          <SelectContent>
            {PROCESS_MODE_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isPipelineMode ? (
        <div className="space-y-2">
          <Label className="text-xs font-normal text-muted-foreground">选择通道</Label>
          <Select value={pipelineId} onValueChange={onPipelineIdChange} disabled={loadingPipelines}>
            <SelectTrigger>
              <SelectValue placeholder={loadingPipelines ? "加载中..." : "请选择"} />
            </SelectTrigger>
            <SelectContent>
              {pipelines.length > 0 ? (
                pipelines.map((pipeline) => (
                  <SelectItem key={pipeline.id} value={pipeline.id}>
                    {pipeline.name}
                  </SelectItem>
                ))
              ) : (
                <div className="py-6 text-center text-sm text-muted-foreground">暂无数据通道</div>
              )}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">通过 ETL 处理提升文件数据质量，增强向量搜索效果</p>
        </div>
      ) : null}

      {isChunkMode ? (
        <div className="space-y-3">
          <p className="text-xs leading-relaxed text-muted-foreground">
            切法由文档结构决定：标题、表格、代码、列表各按自身边界切分，每块自动带上所属章节。
            块大小等预算已有默认值，通常不必调整
          </p>
          <div className="space-y-2">
            <Label className="text-xs font-normal text-muted-foreground">
              {schema?.parseProfileLabel ?? "解析档位"}
            </Label>
            <Select value={parseProfile} onValueChange={onParseProfileChange} disabled={!schema}>
              <SelectTrigger>
                <SelectValue placeholder={schema ? "选择解析档位" : "加载中..."} />
              </SelectTrigger>
              <SelectContent>
                {(schema?.parseProfiles ?? []).map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">
              {schema?.parseProfiles.find((option) => option.value === parseProfile)?.hint ?? ""}
            </p>
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              role="switch"
              aria-checked={noChunk}
              onClick={() => onNoChunkChange(!noChunk)}
              className={cn(
                "relative inline-flex h-5 w-9 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 focus:ring-offset-background",
                noChunk ? "bg-blue-600" : "bg-slate-200"
              )}
            >
              <span
                className={cn(
                  "inline-block h-4 w-4 transform rounded-full bg-background shadow transition-transform",
                  noChunk ? "translate-x-4" : "translate-x-1"
                )}
              />
            </button>
            <div>
              <div className="text-xs font-medium">整篇不分块</div>
              <div className="text-xs text-muted-foreground">开启后整个文档作为一个块入库</div>
            </div>
          </div>

          <div>
            <button
              type="button"
              onClick={() => setShowAdvanced((value) => !value)}
              className="flex items-center gap-1 text-xs text-muted-foreground transition-colors hover:text-foreground"
            >
              {showAdvanced ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
              高级设置
              <span className="ml-1">默认值适用于绝大多数文档</span>
            </button>
            {showAdvanced ? (
              <div className="mt-3 grid gap-4 md:grid-cols-3">
                {budgetFields.map((field) => (
                  <div key={field.key} className="space-y-2">
                    <Label className="text-xs font-normal text-muted-foreground">{field.label}</Label>
                  <Input
                    type="number"
                    min={field.min}
                    max={field.max}
                    value={configValues[field.key] ?? String(field.defaultValue)}
                    disabled={noChunk}
                    onChange={(event) => updateConfigValue(field.key, event.target.value)}
                  />
                    <p className="text-xs text-muted-foreground">{budgetHintOf(field)}</p>
                  </div>
                ))}
              </div>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  );
}

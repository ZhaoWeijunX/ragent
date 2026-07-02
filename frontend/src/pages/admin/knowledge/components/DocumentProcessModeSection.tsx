import { useEffect, useRef, useState } from "react";

import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { getChunkStrategies, type ChunkStrategyOption } from "@/services/knowledgeService";
import { getIngestionPipelines, type IngestionPipeline } from "@/services/ingestionService";
import {
  applyStrategyDefaults,
  NO_CHUNK_VALUE,
  PROCESS_MODE_OPTIONS,
  type ProcessMode
} from "@/pages/admin/knowledge/utils/documentProcessMode";

interface DocumentProcessModeSectionProps {
  active?: boolean;
  processMode: ProcessMode;
  onProcessModeChange: (mode: ProcessMode) => void;
  chunkStrategy: string;
  onChunkStrategyChange: (strategy: string) => void;
  configValues: Record<string, string>;
  onConfigValuesChange: (values: Record<string, string>) => void;
  pipelineId: string;
  onPipelineIdChange: (id: string) => void;
  noChunk: boolean;
  onNoChunkChange: (value: boolean) => void;
  onChunkStrategiesReady?: (strategies: ChunkStrategyOption[]) => void;
}

export function DocumentProcessModeSection({
  active = true,
  processMode,
  onProcessModeChange,
  chunkStrategy,
  onChunkStrategyChange,
  configValues,
  onConfigValuesChange,
  pipelineId,
  onPipelineIdChange,
  noChunk,
  onNoChunkChange,
  onChunkStrategiesReady
}: DocumentProcessModeSectionProps) {
  const [chunkStrategies, setChunkStrategies] = useState<ChunkStrategyOption[]>([]);
  const [pipelines, setPipelines] = useState<IngestionPipeline[]>([]);
  const [loadingPipelines, setLoadingPipelines] = useState(false);
  const originalChunkSizeRef = useRef(configValues.chunkSize || "512");

  const isChunkMode = processMode === "chunk";
  const isPipelineMode = processMode === "pipeline";
  const isFixedSize = chunkStrategy === "fixed_size";

  useEffect(() => {
    if (!active) {
      return;
    }
    getChunkStrategies()
      .then((strategies) => {
        setChunkStrategies(strategies);
        onChunkStrategiesReady?.(strategies);
      })
      .catch(() => {});
    setLoadingPipelines(true);
    getIngestionPipelines(1, 100)
      .then((result) => setPipelines(result.records || []))
      .catch(() => {})
      .finally(() => setLoadingPipelines(false));
  }, [active]);

  useEffect(() => {
    const strategy = chunkStrategies.find((item) => item.value === chunkStrategy);
    if (!strategy) {
      return;
    }
    onConfigValuesChange(applyStrategyDefaults(strategy, configValues));
    if (strategy.defaultConfig.chunkSize !== undefined) {
      originalChunkSizeRef.current = String(strategy.defaultConfig.chunkSize);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only react to strategy list / selection changes
  }, [chunkStrategy, chunkStrategies]);

  useEffect(() => {
    if (noChunk && configValues.chunkSize !== String(NO_CHUNK_VALUE)) {
      onNoChunkChange(false);
    }
  }, [configValues.chunkSize, noChunk, onNoChunkChange]);

  const updateConfigValue = (key: string, value: string) => {
    onConfigValuesChange({ ...configValues, [key]: value });
  };

  const handleNoChunkToggle = () => {
    if (noChunk) {
      updateConfigValue("chunkSize", originalChunkSizeRef.current);
      onNoChunkChange(false);
      return;
    }
    originalChunkSizeRef.current = configValues.chunkSize || "512";
    updateConfigValue("chunkSize", String(NO_CHUNK_VALUE));
    onNoChunkChange(true);
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
          <div className="space-y-2">
            <Label className="text-xs font-normal text-muted-foreground">切分方式</Label>
            <Select value={chunkStrategy} onValueChange={onChunkStrategyChange}>
              <SelectTrigger>
                <SelectValue placeholder="选择切分方式" />
              </SelectTrigger>
              <SelectContent>
                {chunkStrategies.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {isFixedSize ? (
            <div className="grid gap-4 md:grid-cols-3">
              <div className="space-y-2">
                <Label className="text-xs font-normal text-muted-foreground">块大小</Label>
                <Input
                  type="number"
                  value={configValues.chunkSize ?? ""}
                  onChange={(event) => updateConfigValue("chunkSize", event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label className="text-xs font-normal text-muted-foreground">重叠大小</Label>
                <Input
                  type="number"
                  value={configValues.overlapSize ?? ""}
                  onChange={(event) => updateConfigValue("overlapSize", event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label className="text-xs font-normal text-muted-foreground">不分块</Label>
                <div className="flex h-9 items-center">
                  <button
                    type="button"
                    role="switch"
                    aria-checked={noChunk}
                    onClick={handleNoChunkToggle}
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
                </div>
                <p className="text-xs text-muted-foreground">开启后块大小为 -1</p>
              </div>
            </div>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {(
                [
                  ["targetChars", "理想块大小"],
                  ["maxChars", "块上限"],
                  ["minChars", "块下限"],
                  ["overlapChars", "重叠大小"]
                ] as const
              ).map(([key, label]) => (
                <div key={key} className="space-y-2">
                  <Label className="text-xs font-normal text-muted-foreground">{label}</Label>
                  <Input
                    type="number"
                    value={configValues[key] ?? ""}
                    onChange={(event) => updateConfigValue(key, event.target.value)}
                  />
                </div>
              ))}
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}

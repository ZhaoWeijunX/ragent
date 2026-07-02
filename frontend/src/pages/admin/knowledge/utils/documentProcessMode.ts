import type { ChunkStrategyOption } from "@/services/knowledgeService";

export type ProcessMode = "chunk" | "pipeline";

export const PROCESS_MODE_OPTIONS: { value: ProcessMode; label: string }[] = [
  { value: "chunk", label: "直接分块" },
  { value: "pipeline", label: "数据通道" }
];

export const NO_CHUNK_VALUE = -1;

export const DEFAULT_CONFIG_VALUES: Record<string, string> = {
  chunkSize: "512",
  overlapSize: "128",
  targetChars: "1400",
  maxChars: "1800",
  minChars: "600",
  overlapChars: "0",
  rowsPerChunk: "50",
  excelParser: "poi"
};

export interface ProcessModeInput {
  processMode: ProcessMode;
  chunkStrategy: string;
  configValues: Record<string, string>;
  pipelineId: string;
}

export function parseNumber(value?: string): number | null {
  if (!value || !value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

export function applyStrategyDefaults(
  strategy: ChunkStrategyOption,
  current: Record<string, string>
): Record<string, string> {
  const next = { ...current };
  for (const key of Object.keys(strategy.defaultConfig)) {
    if (strategy.defaultConfig[key] !== undefined) {
      next[key] = String(strategy.defaultConfig[key]);
    }
  }
  return next;
}

export function buildChunkConfig(
  input: ProcessModeInput,
  chunkStrategies: ChunkStrategyOption[],
  options?: { isTableType?: boolean; isCsv?: boolean }
): string | undefined {
  if (input.processMode !== "chunk") {
    return undefined;
  }

  if (options?.isTableType) {
    const config: Record<string, number | string> = {
      chunkSize: parseNumber(input.configValues.chunkSize) ?? 512,
      overlapSize: 0,
      rowsPerChunk: parseNumber(input.configValues.rowsPerChunk) ?? 50
    };
    if (!options.isCsv) {
      config.excelParser = input.configValues.excelParser || "poi";
    }
    return JSON.stringify(config);
  }

  const strategy = chunkStrategies.find((s) => s.value === input.chunkStrategy);
  if (!strategy) {
    return undefined;
  }

  const config: Record<string, number> = {};
  for (const key of Object.keys(strategy.defaultConfig)) {
    const val = parseNumber(input.configValues[key]);
    if (val !== null) {
      config[key] = val;
    }
  }
  return JSON.stringify(config);
}

export function validateProcessModeValues(input: ProcessModeInput): string | null {
  const isBlank = (value?: string) => !value || value.trim() === "";
  const requireNumber = (value: string | undefined, label: string): string | null => {
    if (isBlank(value)) {
      return `请输入${label}`;
    }
    if (Number.isNaN(Number(value))) {
      return `${label}必须是数字`;
    }
    return null;
  };

  if (input.processMode === "chunk") {
    if (!input.chunkStrategy) {
      return "请选择分块策略";
    }
    if (input.chunkStrategy === "fixed_size") {
      const chunkSizeError = requireNumber(input.configValues.chunkSize, "块大小");
      if (chunkSizeError) {
        return chunkSizeError;
      }
      const overlapError = requireNumber(input.configValues.overlapSize, "重叠大小");
      if (overlapError) {
        return overlapError;
      }
    } else {
      for (const [key, label] of [
        ["targetChars", "理想块大小"],
        ["maxChars", "块上限"],
        ["minChars", "块下限"],
        ["overlapChars", "重叠大小"]
      ] as const) {
        const err = requireNumber(input.configValues[key], label);
        if (err) {
          return err;
        }
      }
    }
  } else if (input.processMode === "pipeline") {
    if (isBlank(input.pipelineId)) {
      return "请选择数据通道";
    }
  }

  return null;
}

export function buildProcessModePayload(
  input: ProcessModeInput,
  chunkStrategies: ChunkStrategyOption[],
  options?: { isTableType?: boolean; isCsv?: boolean }
): {
  processMode: ProcessMode;
  chunkStrategy?: string;
  chunkConfig?: string | null;
  pipelineId?: string | null;
} {
  return {
    processMode: input.processMode,
    chunkStrategy: input.processMode === "chunk" ? input.chunkStrategy : undefined,
    chunkConfig:
      input.processMode === "chunk"
        ? (buildChunkConfig(input, chunkStrategies, options) ?? null)
        : null,
    pipelineId: input.processMode === "pipeline" ? input.pipelineId : null
  };
}

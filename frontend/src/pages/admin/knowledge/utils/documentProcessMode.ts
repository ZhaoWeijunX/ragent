import type { BudgetFieldSchema, IngestionSpecSchema } from "@/services/knowledgeService";

export type ProcessMode = "chunk" | "pipeline";

export const PROCESS_MODE_OPTIONS: { value: ProcessMode; label: string }[] = [
  { value: "chunk", label: "直接分块" },
  { value: "pipeline", label: "数据通道" }
];

export const NO_CHUNK_VALUE = -1;

export const DEFAULT_CONFIG_VALUES: Record<string, string> = {
  maxChars: "1024",
  overlapChars: "128",
  rowsPerChunk: "50",
  toleranceFactor: "3"
};

export interface ProcessModeInput {
  processMode: ProcessMode;
  parseProfile: string;
  configValues: Record<string, string>;
  pipelineId: string;
  noChunk?: boolean;
}

export function noChunkValueOf(schema: IngestionSpecSchema | null): number {
  return schema?.wholeDocumentSentinel ?? NO_CHUNK_VALUE;
}

export function budgetHintOf(field: BudgetFieldSchema): string {
  return `${field.hint ?? ""}（${field.min} ~ ${field.max}）`;
}

export function budgetDefaultsOf(schema: IngestionSpecSchema | null): Record<string, string> {
  const defaults = { ...DEFAULT_CONFIG_VALUES };
  for (const field of schema?.budgetFields ?? []) {
    defaults[field.key] = String(field.defaultValue);
  }
  return defaults;
}

export function buildIngestionSpec(
  parseProfile: string,
  values: Record<string, string>,
  schema: IngestionSpecSchema | null,
  wholeDocument = false
): string {
  const spec: Record<string, number | string> = { parseProfile };
  for (const field of schema?.budgetFields ?? []) {
    const raw = values[field.key];
    const parsed = raw === undefined || raw.trim() === "" ? NaN : Number(raw);
    spec[field.key] = Number.isFinite(parsed) ? parsed : field.defaultValue;
  }
  if (wholeDocument) {
    spec.maxChars = noChunkValueOf(schema);
  }
  return JSON.stringify(spec);
}

export function validateProcessModeValues(
  input: ProcessModeInput,
  schema: IngestionSpecSchema | null
): string | null {
  const isBlank = (value?: string) => !value || value.trim() === "";

  if (input.processMode === "chunk") {
    if (!schema) {
      return "摄取配置加载中，请稍后再试";
    }
    for (const field of schema.budgetFields) {
      if (input.noChunk && field.key === "maxChars") {
        continue;
      }
      const raw = input.configValues[field.key];
      if (isBlank(raw)) {
        return `请输入${field.label}`;
      }
      const value = Number(raw);
      if (!Number.isFinite(value)) {
        return `${field.label}必须是数字`;
      }
      if (value < field.min || value > field.max) {
        return `${field.label}必须在 ${field.min} 到 ${field.max} 之间`;
      }
    }
    const maxChars = Number(input.configValues.maxChars);
    const overlapChars = Number(input.configValues.overlapChars);
    if (!input.noChunk && Number.isFinite(maxChars) && Number.isFinite(overlapChars)
        && maxChars > 0 && overlapChars >= maxChars) {
      return "块重叠必须小于块大小";
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
  schema: IngestionSpecSchema | null
): {
  processMode: ProcessMode;
  ingestionSpec?: string | null;
  pipelineId?: string | null;
} {
  return {
    processMode: input.processMode,
    ingestionSpec:
      input.processMode === "chunk"
        ? buildIngestionSpec(input.parseProfile || "fast", input.configValues, schema, input.noChunk)
        : null,
    pipelineId: input.processMode === "pipeline" ? input.pipelineId : null
  };
}

import { api } from "@/services/api";

export type ModelCapabilityPath = "chat" | "embedding" | "rerank";

export type ModelProbeItem = {
  capability: string;
  modelId: string;
  provider: string;
  model: string;
  healthy: boolean;
  latencyMs?: number | null;
  errorMessage?: string | null;
};

export type ModelProbeReport = {
  probedAt: number;
  results: ModelProbeItem[];
};

export function probeKey(capability: ModelCapabilityPath, modelId: string) {
  return `${capability}:${modelId}`;
}

export async function probeAllModels(): Promise<ModelProbeReport> {
  return api.post<ModelProbeReport, ModelProbeReport>("/admin/models/probe");
}

export async function probeModelsByCapability(capability: ModelCapabilityPath): Promise<ModelProbeReport> {
  return api.post<ModelProbeReport, ModelProbeReport>(`/admin/models/probe/${capability}`);
}

export async function probeModel(capability: ModelCapabilityPath, modelId: string): Promise<ModelProbeReport> {
  return api.post<ModelProbeReport, ModelProbeReport>(`/admin/models/probe/${capability}/${modelId}`);
}

export function mergeProbeResults(
  current: Record<string, ModelProbeItem>,
  report: ModelProbeReport
): Record<string, ModelProbeItem> {
  const next = { ...current };
  for (const item of report.results) {
    const capability = (item.capability || "").toLowerCase() as ModelCapabilityPath;
    if (!capability || !item.modelId) {
      continue;
    }
    next[probeKey(capability, item.modelId)] = item;
  }
  return next;
}

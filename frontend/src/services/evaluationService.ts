import { api } from "@/services/api";

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface EvalDataset {
  id: string;
  name: string;
  description?: string | null;
  domain?: string | null;
  status: string;
  createdBy?: string | null;
  latestVersion?: string | null;
  latestVersionStatus?: string | null;
  latestSampleCount?: number | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface EvalDatasetVersion {
  id: string;
  datasetId: string;
  datasetName?: string | null;
  version: string;
  status: string;
  sampleCount: number;
  contentHash?: string | null;
  publishedBy?: string | null;
  publishedAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface EvalCase {
  id: string;
  datasetVersionId: string;
  queryId: string;
  query: string;
  intentL1?: string | null;
  intentL2?: string | null;
  difficulty?: string | null;
  requiresRag: boolean;
  expectedAnswerType?: string | null;
  expectedDocIds?: string[];
  niceToHaveDocIds?: string[];
  groundTruth?: string | null;
  trapType?: string | null;
  enabledMetrics?: string[];
  tags?: string[];
  metadata?: Record<string, unknown>;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface EvalImportIssue {
  line?: number | null;
  queryId?: string | null;
  level: string;
  code: string;
  message: string;
}

export interface EvalImportResult {
  versionId: string;
  successCount: number;
  failedCount: number;
  warningCount: number;
  issues: EvalImportIssue[];
}

export interface EvalValidateResult {
  versionId: string;
  publishable: boolean;
  sampleCount: number;
  errorCount: number;
  warningCount: number;
  issues: EvalImportIssue[];
}

const BASE = "/admin/evaluations";

export async function pageDatasets(current = 1, size = 10, keyword?: string, status?: string) {
  return api.get<PageResult<EvalDataset>, PageResult<EvalDataset>>(`${BASE}/datasets`, {
    params: { current, size, keyword: keyword || undefined, status: status || undefined }
  });
}

export async function createDataset(payload: { name: string; description?: string; domain?: string }) {
  return api.post<string, string>(`${BASE}/datasets`, payload);
}

export async function getDataset(id: string) {
  return api.get<EvalDataset, EvalDataset>(`${BASE}/datasets/${id}`);
}

export async function updateDataset(
  id: string,
  payload: { name?: string; description?: string; domain?: string; status?: string }
) {
  await api.put(`${BASE}/datasets/${id}`, payload);
}

export async function deleteDataset(id: string) {
  await api.delete(`${BASE}/datasets/${id}`);
}

export async function listVersions(datasetId: string) {
  return api.get<EvalDatasetVersion[], EvalDatasetVersion[]>(`${BASE}/datasets/${datasetId}/versions`);
}

export async function createDraftVersion(datasetId: string, version?: string) {
  return api.post<string, string>(`${BASE}/datasets/${datasetId}/versions`, version ? { version } : {});
}

export async function getVersion(versionId: string) {
  return api.get<EvalDatasetVersion, EvalDatasetVersion>(`${BASE}/dataset-versions/${versionId}`);
}

export async function copyVersion(versionId: string) {
  return api.post<string, string>(`${BASE}/dataset-versions/${versionId}/copy`);
}

export async function archiveVersion(versionId: string) {
  await api.post(`${BASE}/dataset-versions/${versionId}/archive`);
}

export async function unarchiveVersion(versionId: string) {
  await api.post(`${BASE}/dataset-versions/${versionId}/unarchive`);
}

export async function deleteVersion(versionId: string) {
  await api.delete(`${BASE}/dataset-versions/${versionId}`);
}

export async function importCases(versionId: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return api.post<EvalImportResult, EvalImportResult>(`${BASE}/dataset-versions/${versionId}/import`, formData, {
    headers: { "Content-Type": "multipart/form-data" }
  });
}

export async function validateVersion(versionId: string) {
  return api.post<EvalValidateResult, EvalValidateResult>(`${BASE}/dataset-versions/${versionId}/validate`);
}

export async function publishVersion(versionId: string) {
  await api.post(`${BASE}/dataset-versions/${versionId}/publish`);
}

export async function exportVersion(versionId: string) {
  const blob = await api.get<Blob, Blob>(`${BASE}/dataset-versions/${versionId}/export`, {
    responseType: "blob"
  });
  return blob;
}

export async function pageCases(
  versionId: string,
  current = 1,
  size = 10,
  params?: { keyword?: string; intentL2?: string; difficulty?: string; requiresRag?: boolean }
) {
  return api.get<PageResult<EvalCase>, PageResult<EvalCase>>(`${BASE}/dataset-versions/${versionId}/cases`, {
    params: { current, size, ...params }
  });
}

export async function createCase(versionId: string, payload: Partial<EvalCase>) {
  return api.post<string, string>(`${BASE}/dataset-versions/${versionId}/cases`, payload);
}

export async function updateCase(caseId: string, payload: Partial<EvalCase>) {
  await api.put(`${BASE}/cases/${caseId}`, payload);
}

export async function deleteCase(caseId: string) {
  await api.delete(`${BASE}/cases/${caseId}`);
}

export interface EvalRun {
  id: string;
  name: string;
  datasetVersionId: string;
  datasetId?: string | null;
  datasetName?: string | null;
  datasetVersion?: string | null;
  baselineRunId?: string | null;
  status: string;
  currentPhase?: string | null;
  qualityVerdict?: string | null;
  cancelRequested?: boolean | null;
  configSnapshot?: Record<string, unknown>;
  thresholdSnapshot?: Record<string, unknown>;
  tags?: Record<string, unknown>;
  totalCount: number;
  successCount: number;
  failedCount: number;
  progress: number;
  ragasEnabled?: boolean | null;
  createdBy?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  errorMessage?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
  dualPathDisclaimer?: string | null;
}

export interface EvalRecord {
  id: string;
  runId: string;
  caseId: string;
  queryId?: string | null;
  status: string;
  question: string;
  response?: string | null;
  retrievedDocIds?: string[];
  retrievedChunkIds?: string[];
  retrievedContexts?: string[];
  retrievedContextDocIds?: string[];
  predictedIntents?: string[];
  intentPred?: string | null;
  hasKb?: boolean | null;
  hasMcp?: boolean | null;
  retrievalSkipped?: boolean | null;
  skipReason?: string | null;
  ttftMs?: number | null;
  totalLatencyMs?: number | null;
  evalLatencyMs?: number | null;
  conversationId?: string | null;
  taskId?: string | null;
  traceId?: string | null;
  evidenceSource?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export async function pageRuns(
  current = 1,
  size = 10,
  params?: { keyword?: string; status?: string; datasetVersionId?: string }
) {
  return api.get<PageResult<EvalRun>, PageResult<EvalRun>>(`${BASE}/runs`, {
    params: { current, size, ...params }
  });
}

export async function createRun(payload: {
  name: string;
  datasetVersionId: string;
  baselineRunId?: string;
  ragasEnabled?: boolean;
  tags?: Record<string, unknown>;
}) {
  return api.post<string, string>(`${BASE}/runs`, payload);
}

export async function getRun(runId: string) {
  return api.get<EvalRun, EvalRun>(`${BASE}/runs/${runId}`);
}

export async function cancelRun(runId: string) {
  await api.post(`${BASE}/runs/${runId}/cancel`);
}

export async function resumeRun(runId: string) {
  await api.post(`${BASE}/runs/${runId}/resume`);
}

export async function pageRecords(
  runId: string,
  current = 1,
  size = 10,
  params?: { status?: string; keyword?: string }
) {
  return api.get<PageResult<EvalRecord>, PageResult<EvalRecord>>(`${BASE}/runs/${runId}/records`, {
    params: { current, size, ...params }
  });
}

export async function getRecord(recordId: string) {
  return api.get<EvalRecord, EvalRecord>(`${BASE}/records/${recordId}`);
}

export interface EvalMetricItem {
  name: string;
  overall?: number | null;
  pct?: boolean | null;
  sampleCount?: number | null;
  byIntentL1?: Record<string, number | null>;
  byIntentL2?: Record<string, number | null>;
  byDifficulty?: Record<string, number | null>;
  meta?: Record<string, unknown>;
}

export interface EvalFailureReason {
  code: string;
  message: string;
}

export interface EvalSampleFailure {
  recordId: string;
  queryId?: string | null;
  question?: string | null;
  response?: string | null;
  groundTruth?: string | null;
  status?: string | null;
  intentPred?: string | null;
  intentL2?: string | null;
  retrievedDocIds?: string[];
  expectedDocIds?: string[];
  missedDocIds?: string[];
  extraDocIds?: string[];
  failureReasons?: string[];
  failureDetails?: EvalFailureReason[];
  errorCode?: string | null;
  errorMessage?: string | null;
  traceId?: string | null;
  hitAt5?: number | null;
  intentTop1?: number | null;
}

export interface EvalMetricReport {
  runId: string;
  batchId: string;
  scoreType?: string | null;
  algorithmVersion?: string | null;
  status?: string | null;
  sampleCount?: number | null;
  intentTop1Note?: string | null;
  metrics: EvalMetricItem[];
  failures: EvalSampleFailure[];
}

export async function getRunMetrics(runId: string, batchId?: string) {
  return api.get<EvalMetricReport, EvalMetricReport>(`${BASE}/runs/${runId}/metrics`, {
    params: { batchId: batchId || undefined }
  });
}

export async function rescoreRun(runId: string) {
  return api.post<string, string>(`${BASE}/runs/${runId}/rescore`);
}

export async function exportRunReport(runId: string, format: "json" | "jsonl" | "csv" = "json", batchId?: string) {
  return api.get<Blob, Blob>(`${BASE}/runs/${runId}/export`, {
    params: { format, batchId: batchId || undefined },
    responseType: "blob"
  });
}

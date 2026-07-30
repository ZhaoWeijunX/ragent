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

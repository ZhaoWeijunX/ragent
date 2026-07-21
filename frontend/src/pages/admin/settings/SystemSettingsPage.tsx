import type { ReactNode } from "react";
import { useCallback, useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";

import { ModelCandidateProbeTable, ModelGroupProbeButton } from "@/pages/admin/settings/ModelCandidateProbeTable";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table";
import {
  mergeProbeResults,
  probeAllModels,
  probeModel,
  probeModelsByCapability,
  probeKey,
  type ModelCapabilityPath,
  type ModelProbeItem
} from "@/services/modelHealthService";
import type { SystemSettings } from "@/services/settingsService";
import { getSystemSettings } from "@/services/settingsService";
import { getErrorMessage } from "@/utils/error";

const BoolBadge = ({ value }: { value: boolean }) => (
  <Badge variant={value ? "default" : "outline"}>{value ? "启用" : "禁用"}</Badge>
);

function InfoItem({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex flex-col gap-1 rounded-lg border border-slate-200/70 bg-white px-4 py-3">
      <span className="text-xs text-slate-500">{label}</span>
      <div className="text-sm font-medium text-slate-800">{value}</div>
    </div>
  );
}

export function SystemSettingsPage() {
  const [settings, setSettings] = useState<SystemSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [probeResults, setProbeResults] = useState<Record<string, ModelProbeItem>>({});
  const [probingKeys, setProbingKeys] = useState<Set<string>>(new Set());
  const [probingAll, setProbingAll] = useState(false);
  const [probingCapability, setProbingCapability] = useState<ModelCapabilityPath | null>(null);
  const [lastProbedAt, setLastProbedAt] = useState<number | null>(null);

  const loadSettings = async () => {
    try {
      setLoading(true);
      const data = await getSystemSettings();
      setSettings(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载系统配置失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSettings();
  }, []);

  const applyReport = useCallback((report: { probedAt: number; results: ModelProbeItem[] }) => {
    setProbeResults((current) => mergeProbeResults(current, report));
    setLastProbedAt(report.probedAt);
  }, []);

  const withProbeKey = useCallback(async (key: string, task: () => Promise<void>) => {
    setProbingKeys((current) => new Set(current).add(key));
    try {
      await task();
    } finally {
      setProbingKeys((current) => {
        const next = new Set(current);
        next.delete(key);
        return next;
      });
    }
  }, []);

  const handleProbeAll = async () => {
    try {
      setProbingAll(true);
      const report = await probeAllModels();
      applyReport(report);
      const healthyCount = report.results.filter((item) => item.healthy).length;
      toast.success(`探测完成：${healthyCount}/${report.results.length} 可用`);
    } catch (error) {
      toast.error(getErrorMessage(error, "模型探测失败"));
      console.error(error);
    } finally {
      setProbingAll(false);
    }
  };

  const handleProbeCapability = async (capability: ModelCapabilityPath) => {
    try {
      setProbingCapability(capability);
      const report = await probeModelsByCapability(capability);
      applyReport(report);
      const healthyCount = report.results.filter((item) => item.healthy).length;
      toast.success(`${capability} 探测完成：${healthyCount}/${report.results.length} 可用`);
    } catch (error) {
      toast.error(getErrorMessage(error, "模型探测失败"));
      console.error(error);
    } finally {
      setProbingCapability(null);
    }
  };

  const handleProbeOne = async (capability: ModelCapabilityPath, modelId: string) => {
    const key = probeKey(capability, modelId);
    await withProbeKey(key, async () => {
      try {
        const report = await probeModel(capability, modelId);
        applyReport(report);
        const result = report.results[0];
        if (result?.healthy) {
          toast.success(`${modelId} 可用（${result.latencyMs ?? "-"} ms）`);
        } else {
          toast.error(result?.errorMessage || `${modelId} 不可用`);
        }
      } catch (error) {
        toast.error(getErrorMessage(error, "模型探测失败"));
        console.error(error);
        throw error;
      }
    });
  };

  if (loading) {
    return (
      <div className="admin-page">
        <div className="text-sm text-muted-foreground">加载中...</div>
      </div>
    );
  }

  if (!settings) {
    return (
      <div className="admin-page">
        <div className="text-sm text-muted-foreground">暂无可展示的配置</div>
      </div>
    );
  }

  const { rag, ai } = settings;
  const providers = Object.entries(ai.providers || {});

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">系统配置</h1>
          <p className="admin-page-subtitle">
            只读展示当前 application 配置，支持主动探测模型连通性
          </p>
          {lastProbedAt ? (
            <p className="mt-1 text-xs text-muted-foreground">
              最近探测时间：{new Date(lastProbedAt).toLocaleString()}
            </p>
          ) : null}
        </div>
        <div className="admin-page-actions">
          <Button type="button" disabled={probingAll} onClick={handleProbeAll}>
            {probingAll ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                探测中...
              </>
            ) : (
              "探测全部模型"
            )}
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>RAG 默认配置</CardTitle>
          <CardDescription>向量空间与检索基础参数</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-3">
          <InfoItem label="Collection" value={rag.default.collectionName} />
          <InfoItem label="Dimension" value={rag.default.dimension} />
          <InfoItem label="Metric Type" value={rag.default.metricType} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>查询改写</CardTitle>
          <CardDescription>历史上下文压缩与改写策略</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-3">
          <InfoItem label="Enabled" value={<BoolBadge value={rag.queryRewrite.enabled} />} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>全局限流</CardTitle>
          <CardDescription>并发与租约控制</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-3">
          <InfoItem label="Enabled" value={<BoolBadge value={rag.rateLimit.global.enabled} />} />
          <InfoItem label="Max Concurrent" value={rag.rateLimit.global.maxConcurrent} />
          <InfoItem label="Max Wait Seconds" value={rag.rateLimit.global.maxWaitSeconds} />
          <InfoItem label="Lease Seconds" value={rag.rateLimit.global.leaseSeconds} />
          <InfoItem label="Poll Interval (ms)" value={rag.rateLimit.global.pollIntervalMs} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>记忆管理</CardTitle>
          <CardDescription>摘要与上下文保留策略</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-3">
          <InfoItem label="History Keep Turns" value={rag.memory.historyKeepTurns} />
          <InfoItem label="Summary Start Turns" value={rag.memory.summaryStartTurns} />
          <InfoItem
            label="Summary Enabled"
            value={<BoolBadge value={rag.memory.summaryEnabled} />}
          />
          <InfoItem label="Summary Max Chars" value={rag.memory.summaryMaxChars} />
          <InfoItem label="Title Max Length" value={rag.memory.titleMaxLength} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>模型服务提供方</CardTitle>
          <CardDescription>接入地址与端点配置</CardDescription>
        </CardHeader>
        <CardContent>
          <Table className="min-w-[760px]">
            <TableHeader>
              <TableRow>
                <TableHead className="w-[140px]">Provider</TableHead>
                <TableHead className="w-[240px]">URL</TableHead>
                <TableHead className="w-[200px]">API Key</TableHead>
                <TableHead>Endpoints</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {providers.map(([name, provider]) => (
                <TableRow key={name}>
                  <TableCell className="font-medium">{name}</TableCell>
                  <TableCell>{provider.url}</TableCell>
                  <TableCell>{provider.apiKey ? provider.apiKey : "-"}</TableCell>
                  <TableCell>
                    <div className="space-y-1 text-xs text-muted-foreground">
                      {Object.entries(provider.endpoints).map(([key, value]) => (
                        <div key={key}>
                          {key}: {value}
                        </div>
                      ))}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>模型选择策略</CardTitle>
          <CardDescription>熔断与选择阈值</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          <InfoItem label="Failure Threshold" value={ai.selection.failureThreshold} />
          <InfoItem label="Open Duration (ms)" value={ai.selection.openDurationMs} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>流式响应</CardTitle>
          <CardDescription>输出分片大小</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-2">
          <InfoItem label="Message Chunk Size" value={ai.stream.messageChunkSize} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex w-full items-center justify-between gap-3">
            <CardTitle>Chat 模型配置</CardTitle>
            <ModelGroupProbeButton
              probing={probingCapability === "chat"}
              disabled={ai.chat.candidates.length === 0}
              onClick={() => handleProbeCapability("chat")}
            />
          </div>
          <CardDescription>档位路由与候选注册表，可探测上游 Chat API 是否可用</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <InfoItem label="Default Tier" value={ai.chat.defaultTier ?? "-"} />
            <InfoItem label="Deep Thinking Tier" value={ai.chat.deepThinkingTier ?? "-"} />
          </div>
          <div className="space-y-2">
            <div className="text-xs font-medium text-slate-500">档位（Tiers）</div>
            <Table className="min-w-[560px]">
              <TableHeader>
                <TableRow>
                  <TableHead>Tier</TableHead>
                  <TableHead>候选（有序）</TableHead>
                  <TableHead>Timeout (ms/候选)</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {Object.keys(ai.chat.tiers ?? {}).map((name) => {
                  const cfg = ai.chat.tiers?.[name];
                  if (!cfg) return null;
                  return (
                    <TableRow key={name}>
                      <TableCell className="font-medium">{name}</TableCell>
                      <TableCell>{cfg.candidates?.join(" → ")}</TableCell>
                      <TableCell>{cfg.timeoutMs ?? "-"}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
          <div className="space-y-2">
            <div className="text-xs font-medium text-slate-500">候选注册表（Candidates）</div>
            <ModelCandidateProbeTable
              capability="chat"
              candidates={ai.chat.candidates}
              extraColumns={[
                {
                  header: "Thinking",
                  className: "w-[90px]",
                  render: (item) => (item.supportsThinking ? "支持" : "-")
                }
              ]}
              probeResults={probeResults}
              probingKeys={probingKeys}
              onProbeOne={handleProbeOne}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex w-full items-center justify-between gap-3">
            <CardTitle>Embedding 模型配置</CardTitle>
            <ModelGroupProbeButton
              probing={probingCapability === "embedding"}
              disabled={ai.embedding.candidates.length === 0}
              onClick={() => handleProbeCapability("embedding")}
            />
          </div>
          <CardDescription>向量化模型列表，可探测上游 Embedding API 是否可用</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <InfoItem label="Default Model" value={ai.embedding.defaultModel} />
          </div>
          <ModelCandidateProbeTable
            capability="embedding"
            candidates={ai.embedding.candidates}
            extraColumns={[
              {
                header: "Dimension",
                className: "w-[100px]",
                render: (item) => item.dimension ?? "-"
              }
            ]}
            probeResults={probeResults}
            probingKeys={probingKeys}
            onProbeOne={handleProbeOne}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex w-full items-center justify-between gap-3">
            <CardTitle>Rerank 模型配置</CardTitle>
            <ModelGroupProbeButton
              probing={probingCapability === "rerank"}
              disabled={ai.rerank.candidates.length === 0}
              onClick={() => handleProbeCapability("rerank")}
            />
          </div>
          <CardDescription>重排模型列表，可探测上游 Rerank API 是否可用</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <InfoItem label="Default Model" value={ai.rerank.defaultModel} />
          </div>
          <ModelCandidateProbeTable
            capability="rerank"
            candidates={ai.rerank.candidates}
            probeResults={probeResults}
            probingKeys={probingKeys}
            onProbeOne={handleProbeOne}
          />
        </CardContent>
      </Card>
    </div>
  );
}

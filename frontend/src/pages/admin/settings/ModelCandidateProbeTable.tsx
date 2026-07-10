import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import type { ModelCapabilityPath, ModelProbeItem } from "@/services/modelHealthService";
import { probeKey } from "@/services/modelHealthService";
import type { ModelCandidate } from "@/services/settingsService";

type ExtraColumn = {
  header: string;
  className?: string;
  render: (item: ModelCandidate) => ReactNode;
};

type ModelCandidateProbeTableProps = {
  capability: ModelCapabilityPath;
  candidates: ModelCandidate[];
  extraColumns?: ExtraColumn[];
  probeResults: Record<string, ModelProbeItem>;
  probingKeys: Set<string>;
  onProbeOne: (capability: ModelCapabilityPath, modelId: string) => void;
};

function ProbeStatusCell({ result }: { result?: ModelProbeItem }) {
  if (!result) {
    return <span className="text-xs text-muted-foreground">未探测</span>;
  }

  const badge = (
    <Badge variant={result.healthy ? "default" : "destructive"}>
      {result.healthy ? "可用" : "不可用"}
    </Badge>
  );

  if (!result.healthy && result.errorMessage) {
    return (
      <TooltipProvider delayDuration={200}>
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="inline-flex cursor-help">{badge}</span>
          </TooltipTrigger>
          <TooltipContent side="top" className="max-w-sm break-all">
            <p>{result.errorMessage}</p>
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    );
  }

  return badge;
}

export function ModelGroupProbeButton({
  probing,
  disabled,
  onClick
}: {
  probing: boolean;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <Button
      type="button"
      size="sm"
      className="admin-primary-gradient shrink-0"
      disabled={disabled || probing}
      onClick={onClick}
    >
      {probing ? (
        <>
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          探测中...
        </>
      ) : (
        "探测本组"
      )}
    </Button>
  );
}

export function ModelCandidateProbeTable({
  capability,
  candidates,
  extraColumns = [],
  probeResults,
  probingKeys,
  onProbeOne
}: ModelCandidateProbeTableProps) {
  return (
    <Table className="min-w-[860px]">
        <TableHeader>
          <TableRow>
            <TableHead className="w-[150px]">ID</TableHead>
            <TableHead className="w-[110px]">Provider</TableHead>
            <TableHead className="w-[200px]">Model</TableHead>
            {extraColumns.map((column) => (
              <TableHead key={column.header} className={column.className}>
                {column.header}
              </TableHead>
            ))}
            <TableHead className="w-[90px]">Priority</TableHead>
            <TableHead className="w-[120px]">健康状态</TableHead>
            <TableHead className="w-[90px]">耗时</TableHead>
            <TableHead className="w-[90px]">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {candidates.map((item) => {
            const key = probeKey(capability, item.id);
            const result = probeResults[key];
            const probing = probingKeys.has(key);
            const disabled = item.enabled === false;

            return (
              <TableRow key={item.id}>
                <TableCell className="font-medium">{item.id}</TableCell>
                <TableCell>{item.provider}</TableCell>
                <TableCell>{item.model}</TableCell>
                {extraColumns.map((column) => (
                  <TableCell key={column.header}>{column.render(item)}</TableCell>
                ))}
                <TableCell>{item.priority}</TableCell>
                <TableCell>
                  {disabled ? (
                    <Badge variant="outline">已禁用</Badge>
                  ) : (
                    <ProbeStatusCell result={result} />
                  )}
                </TableCell>
                <TableCell className="text-xs text-muted-foreground">
                  {result?.latencyMs != null ? `${result.latencyMs} ms` : "-"}
                </TableCell>
                <TableCell>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-8 min-w-[52px] border-sky-200 bg-sky-50 px-2 text-sky-700 hover:border-sky-300 hover:bg-sky-100 hover:text-sky-800"
                    disabled={disabled || probing}
                    onClick={() => onProbeOne(capability, item.id)}
                  >
                    {probing ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : "探测"}
                  </Button>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
  );
}

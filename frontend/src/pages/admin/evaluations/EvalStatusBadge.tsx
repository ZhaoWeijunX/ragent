import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

const DATASET_STATUS_STYLE: Record<string, string> = {
  ACTIVE: "border-emerald-200 bg-emerald-50 text-emerald-700",
  ARCHIVED: "border-slate-200 bg-slate-100 text-slate-600"
};

const VERSION_STATUS_STYLE: Record<string, string> = {
  DRAFT: "border-amber-200 bg-amber-50 text-amber-700",
  PUBLISHED: "border-emerald-200 bg-emerald-50 text-emerald-700",
  ARCHIVED: "border-slate-200 bg-slate-100 text-slate-600"
};

const RUN_STATUS_STYLE: Record<string, string> = {
  PENDING: "border-slate-200 bg-slate-50 text-slate-600",
  RECORDING: "border-sky-200 bg-sky-50 text-sky-700",
  DETERMINISTIC_SCORING: "border-indigo-200 bg-indigo-50 text-indigo-700",
  RAGAS_SCORING: "border-violet-200 bg-violet-50 text-violet-700",
  REPORTING: "border-cyan-200 bg-cyan-50 text-cyan-700",
  COMPLETED: "border-emerald-200 bg-emerald-50 text-emerald-700",
  PARTIAL_SUCCESS: "border-amber-200 bg-amber-50 text-amber-700",
  FAILED: "border-rose-200 bg-rose-50 text-rose-700",
  CANCELLED: "border-slate-200 bg-slate-100 text-slate-500"
};

const RECORD_STATUS_STYLE: Record<string, string> = {
  success: "border-emerald-200 bg-emerald-50 text-emerald-700",
  refused: "border-amber-200 bg-amber-50 text-amber-700",
  error: "border-rose-200 bg-rose-50 text-rose-700",
  cancelled: "border-slate-200 bg-slate-100 text-slate-500",
  unknown: "border-slate-200 bg-slate-50 text-slate-500",
  PENDING: "border-slate-200 bg-slate-50 text-slate-600"
};

type EvalStatusBadgeProps = {
  status?: string | null;
  kind: "dataset" | "version" | "run" | "record";
  className?: string;
};

export function EvalStatusBadge({ status, kind, className }: EvalStatusBadgeProps) {
  const map =
    kind === "dataset"
      ? DATASET_STATUS_STYLE
      : kind === "version"
        ? VERSION_STATUS_STYLE
        : kind === "run"
          ? RUN_STATUS_STYLE
          : RECORD_STATUS_STYLE;
  const key = kind === "record" ? status || "" : (status || "").toUpperCase();
  const colorClass = map[key] || (kind === "record" ? map[(status || "").toLowerCase()] : undefined);
  return (
    <Badge
      variant="outline"
      className={cn(
        "px-2.5 py-0.5 font-mono text-xs font-semibold",
        colorClass || "border-slate-200 bg-slate-50 text-slate-500",
        className
      )}
    >
      {status || "-"}
    </Badge>
  );
}

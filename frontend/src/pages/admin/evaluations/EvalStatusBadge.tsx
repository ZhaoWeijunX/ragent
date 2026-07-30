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

type EvalStatusBadgeProps = {
  status?: string | null;
  kind: "dataset" | "version";
  className?: string;
};

export function EvalStatusBadge({ status, kind, className }: EvalStatusBadgeProps) {
  const map = kind === "dataset" ? DATASET_STATUS_STYLE : VERSION_STATUS_STYLE;
  const key = (status || "").toUpperCase();
  const colorClass = map[key];
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

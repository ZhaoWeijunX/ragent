import * as React from "react";
import { Copy, RotateCcw, ThumbsDown, ThumbsUp } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";
import type { FeedbackValue } from "@/types";

interface FeedbackButtonsProps {
  messageId: string;
  feedback: FeedbackValue;
  content: string;
  className?: string;
  alwaysVisible?: boolean;
}

const HOLD_AFTER_INTERACTION_MS = 3000;

const tooltipContentClassName =
  "rounded-lg border-0 bg-[#0d0d0d] px-2.5 py-1 text-xs font-medium text-white shadow-none";

function ActionTooltip({
  label,
  children
}: {
  label: string;
  children: React.ReactElement;
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipContent side="bottom" sideOffset={6} className={tooltipContentClassName}>
        {label}
      </TooltipContent>
    </Tooltip>
  );
}

export function FeedbackButtons({
  messageId,
  feedback,
  content,
  className,
  alwaysVisible
}: FeedbackButtonsProps) {
  const submitFeedback = useChatStore((state) => state.submitFeedback);
  const regenerateMessage = useChatStore((state) => state.regenerateMessage);
  const isStreaming = useChatStore((state) => state.isStreaming);
  const [held, setHeld] = React.useState(false);
  const hideTimerRef = React.useRef<number | null>(null);

  const cancelHideTimer = React.useCallback(() => {
    if (hideTimerRef.current) {
      window.clearTimeout(hideTimerRef.current);
      hideTimerRef.current = null;
    }
  }, []);

  React.useEffect(() => () => cancelHideTimer(), [cancelHideTimer]);

  const markInteracted = React.useCallback(() => {
    cancelHideTimer();
    setHeld(true);
  }, [cancelHideTimer]);

  const handleMouseEnter = React.useCallback(() => {
    if (held) cancelHideTimer();
  }, [held, cancelHideTimer]);

  const handleMouseLeave = React.useCallback(() => {
    if (!held) return;
    cancelHideTimer();
    hideTimerRef.current = window.setTimeout(() => {
      hideTimerRef.current = null;
      setHeld(false);
    }, HOLD_AFTER_INTERACTION_MS);
  }, [held, cancelHideTimer]);

  const handleFeedback = (value: FeedbackValue) => {
    markInteracted();
    const next = feedback === value ? null : value;
    submitFeedback(messageId, next).catch(() => null);
  };

  const handleCopy = async () => {
    markInteracted();
    try {
      await navigator.clipboard.writeText(content);
      toast.success("复制成功");
    } catch {
      toast.error("复制失败");
    }
  };

  const handleRegenerate = () => {
    if (isStreaming) return;
    markInteracted();
    regenerateMessage(messageId).catch(() => null);
  };

  return (
    <TooltipProvider delayDuration={200}>
      <div
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        className={cn(
          "flex items-center gap-1",
          alwaysVisible || held
            ? "opacity-100"
            : "pointer-events-none opacity-0 group-hover:pointer-events-auto group-hover:opacity-100",
          className
        )}
      >
        <ActionTooltip label="复制回复">
          <Button
            variant="ghost"
            size="icon"
            onClick={handleCopy}
            aria-label="复制回复"
            className="h-8 w-8 text-[#999999] hover:bg-[#F5F5F5] hover:text-[#666666]"
          >
            <Copy className="h-4 w-4" />
          </Button>
        </ActionTooltip>
        <ActionTooltip label="喜欢">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => handleFeedback("like")}
            aria-label="喜欢"
            className={cn(
              "h-8 w-8 text-[#999999] hover:bg-[#F5F5F5] hover:text-[#10B981]",
              feedback === "like" && "text-[#10B981]"
            )}
          >
            <ThumbsUp className="h-4 w-4" />
          </Button>
        </ActionTooltip>
        <ActionTooltip label="不喜欢">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => handleFeedback("dislike")}
            aria-label="不喜欢"
            className={cn(
              "h-8 w-8 text-[#999999] hover:bg-[#F5F5F5] hover:text-[#EF4444]",
              feedback === "dislike" && "text-[#EF4444]"
            )}
          >
            <ThumbsDown className="h-4 w-4" />
          </Button>
        </ActionTooltip>
        <ActionTooltip label="重试...">
          <Button
            variant="ghost"
            size="icon"
            onClick={handleRegenerate}
            disabled={isStreaming}
            aria-label="重试..."
            className="h-8 w-8 text-[#999999] hover:bg-[#F5F5F5] hover:text-[#666666] disabled:opacity-40"
          >
            <RotateCcw className="h-4 w-4" />
          </Button>
        </ActionTooltip>
      </div>
    </TooltipProvider>
  );
}

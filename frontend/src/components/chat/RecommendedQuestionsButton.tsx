import { ChevronDown, Loader2, Sparkles } from "lucide-react";

import { cn } from "@/lib/utils";
import { useChatStore } from "@/stores/chatStore";
import type { Message } from "@/types";

interface RecommendedQuestionsButtonProps {
  message: Message;
}

export function RecommendedQuestionsButton({ message }: RecommendedQuestionsButtonProps) {
  const toggleRecommended = useChatStore((state) => state.toggleRecommended);

  const open = Boolean(message.recommendedOpen);
  // 仅用户可见的加载（已展开）才转圈；回答完成后的后台预取保持静默、按钮如常
  const spinning = message.recommendedState === "loading" && open;

  return (
    <button
      type="button"
      onClick={() => toggleRecommended(message.id)}
      disabled={spinning}
      aria-expanded={open}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full py-1 pl-2.5 pr-2 text-xs transition-colors",
        open
          ? "bg-[#EAF1FF] text-[#2563EB]"
          : "text-[#666666] hover:bg-[#F0F0F1] hover:text-[#1A1A1A]",
        spinning && "cursor-wait opacity-80"
      )}
    >
      {spinning ? (
        <Loader2 className="h-3.5 w-3.5 animate-spin" />
      ) : (
        <Sparkles className={cn("h-3.5 w-3.5", open && "text-[#3B82F6]")} />
      )}
      推荐问题
      <ChevronDown
        className={cn("h-3 w-3 transition-transform", open && "rotate-180")}
        aria-hidden="true"
      />
    </button>
  );
}

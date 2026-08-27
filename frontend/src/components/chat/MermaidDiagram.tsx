import * as React from "react";

import type { ThemeMode } from "@/stores/themeStore";

type Mermaid = (typeof import("mermaid"))["default"];

let mermaidPromise: Promise<Mermaid> | null = null;
let renderQueue: Promise<void> = Promise.resolve();

function loadMermaid(): Promise<Mermaid> {
  if (!mermaidPromise) {
    mermaidPromise = import("mermaid").then((module) => module.default);
  }
  return mermaidPromise;
}

/**
 * Mermaid 使用全局配置；串行化“配置 → 渲染”可避免多个图表同时切换主题时互相覆盖。
 */
function renderMermaid(source: string, id: string, theme: ThemeMode) {
  const render = renderQueue.then(async () => {
    const mermaid = await loadMermaid();
    mermaid.initialize({
      startOnLoad: false,
      securityLevel: "strict",
      htmlLabels: false,
      suppressErrorRendering: true,
      maxTextSize: 50_000,
      maxEdges: 500,
      theme: theme === "dark" ? "dark" : "default"
    });
    return mermaid.render(id, source);
  });

  renderQueue = render.then(
    () => undefined,
    () => undefined
  );
  return render;
}

interface MermaidDiagramProps {
  source: string;
  theme: ThemeMode;
  enabled: boolean;
}

function MermaidSource({ source, message }: { source: string; message?: string }) {
  return (
    <div className="my-3 overflow-hidden rounded-md border border-[#d0d7de] bg-[#f6f8fa] dark:border-[#30363d] dark:bg-[#161b22]">
      <div className="border-b border-[#d0d7de] bg-[#f6f8fa] px-3 py-1.5 font-mono text-[11px] font-semibold uppercase tracking-wider text-[#57606a] dark:border-[#30363d] dark:bg-[#161b22] dark:text-[#8b949e]">
        mermaid
      </div>
      {message ? (
        <p className="border-b border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-900/70 dark:bg-amber-950/30 dark:text-amber-200">
          {message}
        </p>
      ) : null}
      <pre className="overflow-x-auto p-3 text-[13px] leading-5 text-[#24292f] dark:text-[#c9d1d9]">
        <code>{source}</code>
      </pre>
    </div>
  );
}

export function MermaidDiagram({ source, theme, enabled }: MermaidDiagramProps) {
  const instanceId = React.useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const [svg, setSvg] = React.useState("");
  const [error, setError] = React.useState(false);
  const [loading, setLoading] = React.useState(enabled);

  React.useEffect(() => {
    if (!enabled) {
      setSvg("");
      setError(false);
      setLoading(false);
      return;
    }

    let cancelled = false;
    setSvg("");
    setError(false);
    setLoading(true);

    renderMermaid(source, `mermaid-${instanceId}`, theme)
      .then((result) => {
        if (!cancelled) {
          setSvg(result.svg);
          setLoading(false);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setError(true);
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [enabled, instanceId, source, theme]);

  if (!enabled) {
    return <MermaidSource source={source} />;
  }
  if (loading) {
    return (
      <div className="my-3 flex min-h-20 items-center justify-center rounded-md border border-[#d0d7de] bg-[#f6f8fa] text-sm text-[#57606a] dark:border-[#30363d] dark:bg-[#161b22] dark:text-[#8b949e]">
        正在渲染 Mermaid 图表…
      </div>
    );
  }
  if (error || !svg) {
    return <MermaidSource source={source} message="Mermaid 图表语法无效，已显示源代码。" />;
  }

  return (
    <div
      className="my-3 overflow-x-auto rounded-md border border-[#d0d7de] bg-white p-3 dark:border-[#30363d] dark:bg-[#161b22] [&_svg]:h-auto [&_svg]:max-w-full"
      // Mermaid 在 strict 安全级别下将图表源码生成 SVG；Markdown 原始 HTML 仍走现有 sanitize 链。
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}

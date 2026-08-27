// @ts-nocheck
/* eslint-disable */

import * as React from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import remarkCjkFriendly from "remark-cjk-friendly";
import rehypeKatex from "rehype-katex";
import rehypeRaw from "rehype-raw";
import rehypeSanitize, { defaultSchema } from "rehype-sanitize";
import { Check, Copy, ImageIcon } from "lucide-react";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark, oneLight } from "react-syntax-highlighter/dist/esm/styles/prism";
import "katex/dist/katex.min.css";

import { Button } from "@/components/ui/button";
import { MermaidDiagram } from "@/components/chat/MermaidDiagram";
import { SourceCitation } from "@/components/chat/SourceCitation";
import { cn } from "@/lib/utils";
import { useThemeStore } from "@/stores/themeStore";
import type { SourceRef } from "@/types";

/** 放行 KaTeX 输出的标签与 class/style，避免 sanitize 洗掉公式 HTML */
const markdownSanitizeSchema = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    div: [...(defaultSchema.attributes?.div || []), "className", "style"],
    span: [
      ...(defaultSchema.attributes?.span || []),
      "className",
      "style",
      "aria-hidden"
    ],
    math: ["xmlns", "display"],
    annotation: ["encoding"],
    semantics: [],
    mrow: [],
    mi: [],
    mo: [],
    mn: [],
    msup: [],
    msub: [],
    msubsup: [],
    mfrac: [],
    msqrt: [],
    mroot: [],
    mtable: [],
    mtr: [],
    mtd: [],
    munder: [],
    mover: [],
    munderover: [],
    mtext: [],
    mspace: ["width"]
  },
  tagNames: [
    ...(defaultSchema.tagNames || []),
    "math",
    "annotation",
    "semantics",
    "mrow",
    "mi",
    "mo",
    "mn",
    "msup",
    "msub",
    "msubsup",
    "mfrac",
    "msqrt",
    "mroot",
    "mtable",
    "mtr",
    "mtd",
    "munder",
    "mover",
    "munderover",
    "mtext",
    "mspace"
  ]
};

interface MarkdownRendererProps {
  content: string;
  messageId?: string;
  sources?: SourceRef[];
  renderMermaid?: boolean;
}

// 标题字号更大 中线更高 角标要比正文多抬一点 左侧也留稍多的气口
const headingCitationStyles =
  "[&_[data-source-citation]]:-top-[3px] [&_[data-source-citation]]:ml-1 [&_[data-source-citation]]:mr-0";

interface MarkdownNode {
  type?: string;
  value?: string;
  url?: string;
  children?: MarkdownNode[];
}

/** 单个角标 token：规范链接 / 全角【N】/ 裸写 [N] */
const CITATION_TOKEN =
  /\[([1-9]\d*)\]\(#cite-[1-9]\d*\)|【([1-9]\d*)】|\[([1-9]\d*)\]/g;

/**
 * 进 Markdown 前清洗：去掉“只含角标”的行内代码反引号，并解开被转义的角标
 *
 * 模型常写出 ``寿命 `[1](#cite-1)`。`` —— 反引号会让整段变成 code，原文直接露出。
 * 这里先剥掉这类反引号，再交给常规 link / SourceCitation 渲染。
 */
function normalizeCitationMarkup(content: string): string {
  if (!content) return content;
  let next = content;
  // \[1\](#cite-1) → [1](#cite-1)
  next = next.replace(/\\\[([1-9]\d*)\\\]\(#cite-\1\)/g, "[$1](#cite-$1)");
  // `[1](#cite-1)` / `[1](#cite-1)[2](#cite-2)` / `【1】` / `[1]` → 去掉外层反引号
  next = next.replace(
    /`((?:\s*(?:\[[1-9]\d*\]\(#cite-[1-9]\d*\)|【[1-9]\d*】|\[[1-9]\d*\]))+)\s*`/g,
    "$1"
  );
  return next;
}

/**
 * 若整段文本（可含空白）只由角标组成，拆成 link 节点；否则返回 null
 */
function linksFromCitationOnlyText(value: string): MarkdownNode[] | null {
  const trimmed = value.trim();
  if (!trimmed) return null;

  const links: MarkdownNode[] = [];
  let cursor = 0;
  CITATION_TOKEN.lastIndex = 0;
  for (const match of trimmed.matchAll(CITATION_TOKEN)) {
    if (match.index == null) continue;
    const gap = trimmed.slice(cursor, match.index);
    if (gap.trim() !== "") return null;
    const index = Number(match[1] ?? match[2] ?? match[3]);
    if (!Number.isFinite(index)) return null;
    links.push({
      type: "link",
      url: `#cite-${index}`,
      children: [{ type: "text", value: String(index) }]
    });
    cursor = match.index + match[0].length;
  }
  if (links.length === 0) return null;
  if (trimmed.slice(cursor).trim() !== "") return null;
  return links;
}

/**
 * AST 兜底：若仍有 inlineCode 整段都是角标，改写成 link
 */
function remarkUnwrapCitationCode() {
  return (tree: MarkdownNode) => {
    const visit = (node: MarkdownNode) => {
      if (!Array.isArray(node.children)) return;
      node.children = node.children.flatMap((child) => {
        if (child.type === "inlineCode" && typeof child.value === "string") {
          const links = linksFromCitationOnlyText(child.value);
          if (links) return links;
          return [child];
        }
        visit(child);
        return [child];
      });
    };
    visit(tree);
  };
}

function remarkPlainSourceCitations(options?: { indexes?: number[] }) {
  const indexes = new Set(options?.indexes ?? []);
  const markerPattern = /(?:\[([1-9]\d*)\]|【([1-9]\d*)】)/g;

  return (tree: MarkdownNode) => {
    const visit = (node: MarkdownNode) => {
      if (!Array.isArray(node.children)) return;
      node.children = node.children.flatMap((child) => {
        if (child.type !== "text" || typeof child.value !== "string") {
          visit(child);
          return [child];
        }

        const parts: MarkdownNode[] = [];
        let cursor = 0;
        markerPattern.lastIndex = 0;
        for (const match of child.value.matchAll(markerPattern)) {
          const index = Number(match[1] ?? match[2]);
          if (!indexes.has(index) || match.index == null) continue;
          if (match.index > cursor) {
            parts.push({ type: "text", value: child.value.slice(cursor, match.index) });
          }
          parts.push({
            type: "link",
            url: `#cite-${index}`,
            children: [{ type: "text", value: String(index) }]
          });
          cursor = match.index + match[0].length;
        }
        if (parts.length === 0) return [child];
        if (cursor < child.value.length) {
          parts.push({ type: "text", value: child.value.slice(cursor) });
        }
        return parts;
      });
    };
    visit(tree);
  };
}

function isCitationLink(node: MarkdownNode | undefined) {
  return node?.type === "link" && /^#cite-[1-9]\d*$/.test(node.url ?? "");
}

function isBlankText(node: MarkdownNode) {
  return node.type === "text" && typeof node.value === "string" && node.value.trim() === "";
}

/**
 * 找到能承接角标的段落：段落取自身，列表 / 列表项 / 引用块递归取视觉上的最后一段
 */
function citationAnchor(node: MarkdownNode | undefined): MarkdownNode | null {
  if (!node) return null;
  if (node.type === "paragraph") return node;
  if (node.type !== "list" && node.type !== "listItem" && node.type !== "blockquote") return null;
  const children = node.children ?? [];
  for (let i = children.length - 1; i >= 0; i -= 1) {
    const anchor = citationAnchor(children[i]);
    if (anchor) return anchor;
  }
  return null;
}

/**
 * 归位角标：把脱落成独立段落的角标收回行内，并抹掉角标左侧的空白
 *
 * 其一，模型偶尔会把角标当脚注、空一行单独成段（`...结论。\n\n[1](#cite-1)`），markdown 语义上
 * 这就是 list / paragraph 的兄弟块，渲染必然独占一行。这里在 AST 上把它挪回所支撑内容的
 * 末尾，还原成行内角标；找不到合适落点（前面是标题、表格或本身就是首个块）时保持原样
 *
 * 其二，模型写 `总经理 [2]` 或软换行时，那个空格 / 换行会被渲染成约一个字宽的空隙，
 * 角标看着像飘在句子外面。角标是句子的一部分，统一贴紧前文，间距只由 margin 决定
 */
function remarkNormalizeCitations() {
  return (tree: MarkdownNode) => {
    const visit = (node: MarkdownNode) => {
      const children = node.children;
      if (!Array.isArray(children)) return;
      children.forEach(visit);

      for (let i = children.length - 1; i >= 0; i -= 1) {
        const child = children[i];
        if (child.type !== "paragraph") continue;
        const inner = child.children ?? [];
        if (!inner.some(isCitationLink)) continue;
        if (!inner.every((item) => isCitationLink(item) || isBlankText(item))) continue;

        const anchor = citationAnchor(children[i - 1]);
        if (!anchor?.children) continue;

        const tail = anchor.children[anchor.children.length - 1];
        if (tail?.type === "text" && typeof tail.value === "string") {
          tail.value = tail.value.replace(/\s+$/, "");
        }
        anchor.children.push(...inner.filter(isCitationLink));
        children.splice(i, 1);
      }

      for (let i = children.length - 1; i > 0; i -= 1) {
        if (!isCitationLink(children[i])) continue;
        const prev = children[i - 1];
        if (prev.type !== "text" || typeof prev.value !== "string") continue;
        prev.value = prev.value.replace(/\s+$/, "");
        // 前面整段只有空白（如两个角标之间）时连节点一起去掉 免得留个空文本
        if (prev.value === "") children.splice(i - 1, 1);
      }
    };
    visit(tree);
  };
}

function parseCitationIndex(href: string | undefined) {
  if (!href) return null;
  const match = /^#cite-([1-9]\d*)$/.exec(href);
  return match ? Number(match[1]) : null;
}

/**
 * 收集本条回答里可渲染为角标的编号
 *
 * 进入引用模式的条件（满足其一即可）：
 * 1. 正文出现过规范角标 `[N](#cite-N)`（含曾被反引号误包的形式）
 * 2. 本条消息带有 sources —— 此时把裸写 `[N]` / `【N】` 且 N∈sources 的一并升级
 */
function resolveCitationIndexes(content: string, sources?: SourceRef[]) {
  const result = new Set<number>();
  for (const match of content.matchAll(/`?\[([1-9]\d*)\]\(#cite-\1\)`?/g)) {
    result.add(Number(match[1]));
  }
  const sourceIndexes =
    sources
      ?.map((source) => source.index)
      .filter((index): index is number => typeof index === "number") ?? [];
  if (result.size === 0 && sourceIndexes.length === 0) {
    return [];
  }
  sourceIndexes.forEach((index) => result.add(index));
  return [...result];
}

export function MarkdownRenderer({ content, messageId, sources, renderMermaid = true }: MarkdownRendererProps) {
  const theme = useThemeStore((state) => state.theme);
  const normalizedContent = React.useMemo(() => normalizeCitationMarkup(content), [content]);
  const citationIndexes = React.useMemo(
    () => resolveCitationIndexes(normalizedContent, sources),
    [normalizedContent, sources]
  );

  return (
    <ReactMarkdown
        remarkPlugins={[
            [remarkGfm, { singleTilde: false }],
            remarkMath,
            remarkCjkFriendly,
            remarkUnwrapCitationCode,
            [remarkPlainSourceCitations, { indexes: citationIndexes }],
            remarkNormalizeCitations
        ]}
        rehypePlugins={[
            [rehypeKatex, { throwOnError: false, strict: "ignore" }],
            rehypeRaw,
            [rehypeSanitize, markdownSanitizeSchema]
        ]}
      components={{
        code({ inline, className, children, node, ...props }) {
          const match = /language-(\w+)/.exec(className || "");
          const language = match?.[1] || "text";
          const value = String(children).replace(/\n$/, "");

          // Mermaid 必须在“单行代码等同于 inline”的兜底前处理，例如 `flowchart LR` 只有一行时。
          if (language.toLowerCase() === "mermaid") {
            return <MermaidDiagram source={value} theme={theme} enabled={renderMermaid} />;
          }

          // 判断是否为内联代码：inline 为 true 或者没有换行符
          if (inline || !value.includes('\n')) {
            // 组件层最后兜底：若仍是纯角标行内代码，直接渲染角标（防止插件链漏网）
            const citationLinks = linksFromCitationOnlyText(value);
            if (citationLinks && citationLinks.length > 0) {
              return (
                <>
                  {citationLinks.map((link, i) => {
                    const citationIndex = parseCitationIndex(link.url);
                    if (citationIndex == null) return null;
                    const source = sources?.find((item) => item.index === citationIndex);
                    return (
                      <SourceCitation
                        key={`cite-code-${citationIndex}-${i}`}
                        index={citationIndex}
                        messageId={messageId}
                        source={source}
                      />
                    );
                  })}
                </>
              );
            }
            return (
              <code
                className={cn(
                  "mx-0.5 rounded px-1.5 py-0.5 text-[13px] font-mono bg-[#eaeef2] text-[#24292f]",
                  "dark:bg-[#30363d] dark:text-[#c9d1d9]",
                  className
                )}
                {...props}
              >
                {children}
              </code>
            );
          }

          return (
            <div className="my-3 overflow-hidden rounded-md border border-[#d0d7de] bg-[#f6f8fa] dark:border-[#30363d] dark:bg-[#161b22]">
              <div className="flex items-center justify-between border-b border-[#d0d7de] bg-[#f6f8fa] px-3 py-1.5 dark:border-[#30363d] dark:bg-[#161b22]">
                <span className="font-mono text-[11px] font-semibold uppercase tracking-wider text-[#57606a] dark:text-[#8b949e]">
                  {language}
                </span>
                <CopyButton value={value} />
              </div>
              <div className="overflow-x-auto">
                <SyntaxHighlighter
                  language={language}
                  style={theme === "dark" ? oneDark : oneLight}
                  PreTag="div"
                  customStyle={{
                    margin: 0,
                    padding: "0.75rem 1rem",
                    background: "transparent",
                    fontSize: "13px",
                    lineHeight: "1.5"
                  }}
                  showLineNumbers={false}
                  wrapLines={true}
                >
                  {value}
                </SyntaxHighlighter>
              </div>
            </div>
          );
        },
        img({ src, alt, ...props }) {
          const [hasError, setHasError] = React.useState(false);

          if (hasError) {
            return (
              <div className="my-3 flex items-center gap-2 text-sm text-[#999999]">
                <ImageIcon className="h-4 w-4" />
                <span>图片加载失败</span>
              </div>
            );
          }

          return (
            <img
              src={src}
              alt=""
              className="my-3 max-w-full rounded-lg"
              onError={() => setHasError(true)}
              loading="lazy"
              {...props}
            />
          );
        },
        a({ children, href, ...props }) {
          // #cite-N 一律走角标组件，避免退回普通锚点
          const citationIndex = parseCitationIndex(href);
          if (citationIndex != null) {
            const source = sources?.find((item) => item.index === citationIndex);
            return (
              <SourceCitation
                index={citationIndex}
                messageId={messageId}
                source={source}
              />
            );
          }
          return (
            <a
              className="text-[#0969da] underline-offset-4 hover:underline dark:text-[#58a6ff]"
              target="_blank"
              rel="noreferrer"
              href={href}
              {...props}
            >
              {children}
            </a>
          );
        },
        h1({ children, ...props }) {
          return (
            <h1
              className={cn(
                "mt-6 mb-4 border-b border-[#d0d7de] pb-2 text-3xl font-bold leading-tight first:mt-0 dark:border-[#30363d]",
                headingCitationStyles
              )}
              {...props}
            >
              {children}
            </h1>
          );
        },
        h2({ children, ...props }) {
          return (
            <h2
              className={cn(
                "mt-6 mb-4 border-b border-[#d0d7de] pb-1.5 text-2xl font-bold leading-tight first:mt-0 dark:border-[#30363d]",
                headingCitationStyles
              )}
              {...props}
            >
              {children}
            </h2>
          );
        },
        h3({ children, ...props }) {
          return (
            <h3
              className={cn(
                "mt-5 mb-3 text-xl font-bold leading-snug first:mt-0",
                headingCitationStyles
              )}
              {...props}
            >
              {children}
            </h3>
          );
        },
        h4({ children, ...props }) {
          return (
            <h4
              className={cn(
                "mt-4 mb-2 text-base font-bold leading-snug first:mt-0",
                headingCitationStyles
              )}
              {...props}
            >
              {children}
            </h4>
          );
        },
        table({ children, ...props }) {
          return (
            <div className="my-6 w-full min-w-0 overflow-x-auto">
              <table
                className="w-full border-separate border-spacing-0 overflow-hidden rounded-lg border border-[#d0d7de] text-sm dark:border-[#30363d] [&_tr:last-child>td]:border-b-0"
                {...props}
              >
                {children}
              </table>
            </div>
          );
        },
        thead({ children, ...props }) {
          return (
            <thead className="bg-[#eaeef2] dark:bg-[#21262d]" {...props}>
              {children}
            </thead>
          );
        },
        tr({ children, ...props }) {
          return (
            <tr
              className="transition-colors hover:bg-[#f6f8fa]/60 dark:hover:bg-[#161b22]/60"
              {...props}
            >
              {children}
            </tr>
          );
        },
        th({ children, ...props }) {
          return (
            <th
              className="border-b border-r border-[#d0d7de] px-2 py-2 text-left text-sm font-semibold text-[#24292f] align-middle break-words last:border-r-0 dark:border-[#30363d] dark:text-[#c9d1d9]"
              {...props}
            >
              {children}
            </th>
          );
        },
        td({ children, ...props }) {
          return (
            <td
              className="border-b border-r border-[#d0d7de] px-2 py-2 text-sm text-[#24292f] align-middle break-words last:border-r-0 dark:border-[#30363d] dark:text-[#c9d1d9]"
              {...props}
            >
              {children}
            </td>
          );
        },
        blockquote({ children, ...props }) {
          return (
            <blockquote
              className="my-5 rounded-r-md border-l-4 border-[#0969da] bg-[#f6f8fa] px-6 py-4 italic text-[#24292f] dark:border-[#58a6ff] dark:bg-[#161b22] dark:text-[#c9d1d9] [&_p:first-of-type]:before:content-none [&_p:last-of-type]:after:content-none"
              {...props}
            >
              {children}
            </blockquote>
          );
        },
        ul({ children, ...props }) {
          return (
            <ul
              className="my-4 list-disc space-y-2 pl-6 marker:text-[#6e7781] dark:marker:text-[#8b949e] [&_ul]:my-2 [&_ol]:my-2"
              {...props}
            >
              {children}
            </ul>
          );
        },
        ol({ children, ...props }) {
          return (
            <ol
              className="my-4 list-decimal space-y-2 pl-6 marker:text-[#6e7781] dark:marker:text-[#8b949e] [&_ul]:my-2 [&_ol]:my-2"
              {...props}
            >
              {children}
            </ol>
          );
        },
        hr({ ...props }) {
          return <hr className="my-6 border-0 border-t border-[#d0d7de] dark:border-[#30363d]" {...props} />;
        }
      }}
      className="prose prose-gray max-w-none break-words leading-[1.6] dark:prose-invert prose-headings:text-[#1A1A1A] dark:prose-headings:text-[#EEEEEE] prose-p:text-[#333333] dark:prose-p:text-[#CCCCCC] prose-p:leading-relaxed prose-li:text-[#333333] dark:prose-li:text-[#CCCCCC] prose-strong:text-[#1A1A1A] dark:prose-strong:text-[#EEEEEE]"
    >
      {normalizedContent}
    </ReactMarkdown>
  );
}

function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = React.useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
    }
  };

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={handleCopy}
      aria-label="复制代码"
      className="h-7 w-7 hover:bg-[#eaeef2] dark:hover:bg-[#30363d] transition-colors"
    >
      {copied ? (
        <Check className="h-3.5 w-3.5 text-green-600 dark:text-green-400" />
      ) : (
        <Copy className="h-3.5 w-3.5 text-[#57606a] dark:text-[#8b949e]" />
      )}
    </Button>
  );
}

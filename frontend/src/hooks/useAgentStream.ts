import type {
  AgentCompletionPayload,
  AgentConfirmPayload,
  AgentHintPayload,
  AgentMessageDelta,
  AgentMetaPayload,
  AgentToolProgress
} from "@/types/agent";

export interface AgentStreamHandlers {
  onMeta?: (payload: AgentMetaPayload) => void;
  onMessage?: (payload: AgentMessageDelta) => void;
  onThinking?: (payload: AgentMessageDelta) => void;
  onTool?: (payload: AgentToolProgress) => void;
  onHint?: (payload: AgentHintPayload) => void;
  onConfirm?: (payload: AgentConfirmPayload) => void;
  onFinish?: (payload: AgentCompletionPayload) => void;
  onDone?: () => void;
  onCancel?: (payload: AgentCompletionPayload) => void;
  onError?: (error: Error) => void;
  onEvent?: (event: string, payload: unknown) => void;
}

export interface AgentStreamOptions {
  url: string;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  retryCount?: number;
  retryDelayMs?: number;
  // 带 body 即走 POST：确认裁决是有副作用的动作 不该塞进 query 让浏览器随手重发
  body?: unknown;
}

// 幂等提交被拒等业务错误：重试只会再次被拒或触发重复生成 故直接抛出
class AgentStreamRejectionError extends Error {}

function parseData(raw: string): unknown {
  if (!raw) return "";
  try {
    return JSON.parse(raw);
  } catch {
    return raw;
  }
}

async function readSseStream(
  response: Response,
  handlers: AgentStreamHandlers,
  signal?: AbortSignal
) {
  if (!response.body) {
    throw new Error("流式响应为空");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let eventName = "message";
  let dataLines: string[] = [];

  const dispatchEvent = () => {
    if (dataLines.length === 0) {
      eventName = "message";
      return;
    }
    const raw = dataLines.join("\n");
    const payload = parseData(raw);
    handlers.onEvent?.(eventName, payload);

    switch (eventName) {
      case "meta":
        handlers.onMeta?.(payload as AgentMetaPayload);
        break;
      case "message":
        {
          const messagePayload = payload as AgentMessageDelta;
          if (messagePayload?.type === "think") {
            handlers.onThinking?.(messagePayload);
          }
          handlers.onMessage?.(messagePayload);
        }
        break;
      case "tool":
        handlers.onTool?.(payload as AgentToolProgress);
        break;
      case "hint":
        handlers.onHint?.(payload as AgentHintPayload);
        break;
      case "confirm":
        handlers.onConfirm?.(payload as AgentConfirmPayload);
        break;
      case "finish":
        handlers.onFinish?.(payload as AgentCompletionPayload);
        break;
      case "done":
        handlers.onDone?.();
        break;
      case "cancel":
        handlers.onCancel?.(payload as AgentCompletionPayload);
        break;
      case "error":
        handlers.onError?.(new Error(String((payload as { error?: string })?.error || payload)));
        break;
      default:
        break;
    }

    eventName = "message";
    dataLines = [];
  };

  for (;;) {
    if (signal?.aborted) {
      reader.cancel();
      break;
    }
    const { value, done } = await reader.read();
    if (done) {
      dispatchEvent();
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? "";
    for (const line of lines) {
      if (!line) {
        dispatchEvent();
        continue;
      }
      if (line.startsWith(":")) {
        continue;
      }
      if (line.startsWith("event:")) {
        eventName = line.slice(6).trim();
        continue;
      }
      if (line.startsWith("data:")) {
        dataLines.push(line.slice(5).trim());
      }
    }
  }
}

async function streamWithRetry(
  options: AgentStreamOptions,
  handlers: AgentStreamHandlers
): Promise<void> {
  const { url, headers, signal, body } = options;
  const retryCount = options.retryCount ?? 2;
  const retryDelayMs = options.retryDelayMs ?? 600;
  const post = body !== undefined;

  let attempt = 0;
  while (attempt <= retryCount) {
    try {
      const response = await fetch(url, {
        method: post ? "POST" : "GET",
        headers: {
          Accept: "text/event-stream",
          ...(post ? { "Content-Type": "application/json" } : {}),
          ...headers
        },
        body: post ? JSON.stringify(body) : undefined,
        signal
      });

      if (!response.ok) {
        throw new Error(`SSE 请求失败（${response.status}）`);
      }

      // @IdempotentSubmit 拦截时返回 200 + JSON 体而非事件流
      const contentType = response.headers.get("content-type") || "";
      if (!contentType.includes("text/event-stream")) {
        const errBody = (await response.json().catch(() => null)) as { message?: string } | null;
        throw new AgentStreamRejectionError(errBody?.message || "请求失败");
      }

      await readSseStream(response, handlers, signal);
      return;
    } catch (error) {
      const err = error as Error;
      if (signal?.aborted) {
        throw err;
      }
      if (err instanceof AgentStreamRejectionError) {
        throw err;
      }
      if (attempt >= retryCount) {
        throw err;
      }
      await new Promise((resolve) => setTimeout(resolve, retryDelayMs * Math.pow(2, attempt)));
      attempt += 1;
    }
  }
}

export function createAgentStreamResponse(
  options: AgentStreamOptions,
  handlers: AgentStreamHandlers
) {
  const controller = new AbortController();
  const mergedOptions = {
    ...options,
    signal: options.signal ?? controller.signal
  };

  return {
    start: () => streamWithRetry(mergedOptions, handlers),
    cancel: () => controller.abort()
  };
}

export function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error === "string" && error.trim()) {
    return error;
  }
  if (error && typeof error === "object") {
    const maybeMessage = (error as { message?: unknown }).message;
    if (typeof maybeMessage === "string" && maybeMessage.trim()) {
      return maybeMessage;
    }
  }
  return fallback;
}

const GENERIC_SYSTEM_ERROR_MESSAGES = new Set(["系统执行出错", "系统执行错误"]);

export function getEvalWorkbenchErrorMessage(error: unknown, fallback: string) {
  const message = getErrorMessage(error, fallback);
  if (GENERIC_SYSTEM_ERROR_MESSAGES.has(message.trim())) {
    return "当前环境暂未启用评测工作台，生产环境暂不开放此功能。";
    // return "评测台未启用";
  }
  return message;
}

function register(actions) {
  actions.registerAction({
    id: "js_broadcast",
    category: "javascript",
    description: "从JavaScript注册的CoreLib操作中广播服务器消息",
    executionMode: "SYNC",
    timeoutMillis: 1000,
    parameters: [
      { name: "text", type: "STRING", required: true, description: "要广播的消息" }
    ],
    execute: "executeBroadcast"
  });
}

function executeBroadcast(ctx, args) {
  const text = read(args, "text", "");
  if (!text) {
    return { success: false, message: "text不能为空" };
  }
  emaki.server.broadcast(String(text));
  return {
    success: true,
    message: "由js_broadcast发送广播",
    output: {
      text: String(text)
    }
  };
}

function read(object, key, fallback) {
  if (object == null) {
    return fallback;
  }
  if (typeof object.get === "function") {
    const value = object.get(key);
    return value == null ? fallback : value;
  }
  const value = object[key];
  return value == null ? fallback : value;
}

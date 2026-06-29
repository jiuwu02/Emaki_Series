function main(ctx) {
  const corelib = emaki.module("corelib");
  emaki.logger.info("Hello from Emaki JavaScript. CoreLib ready=" + corelib.ready());
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] Hello, " + emaki.player.name() + "! CoreLib API=" + corelib.apiVersion());
  }
  emaki.state.set("hello_script_executed", true);
  return {
    success: true,
    message: "hello.js executed",
    output: {
      phase: emaki.context.phase(),
      plugin: emaki.context.plugin(),
      corelib_ready: corelib.ready(),
      corelib_api: corelib.apiVersion()
    }
  };
}

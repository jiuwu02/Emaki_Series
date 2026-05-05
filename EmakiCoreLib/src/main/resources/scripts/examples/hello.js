function main(ctx) {
  emaki.logger.info("Hello from Emaki JavaScript.");
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] Hello, " + emaki.player.name() + "!");
  }
  emaki.state.set("hello_script_executed", true);
  return {
    success: true,
    message: "hello.js executed",
    output: {
      phase: emaki.context.phase(),
      plugin: emaki.context.plugin()
    }
  };
}

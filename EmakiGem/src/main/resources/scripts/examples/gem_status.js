function main(ctx) {
  const gem = emaki.module("gem");
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] 宝石模块状态: ready=" + gem.ready() + " api=" + gem.apiVersion());
  }
  return {
    success: true,
    output: {
      gem_available: gem.available(),
      gem_ready: gem.ready(),
      gem_api: gem.apiVersion()
    }
  };
}

function register() {
  const forge = emaki.module("forge");

  forge.registerForgeRule({
    id: "example_moon_bonus",
    priority: 10,
    function: "modifyForge"
  });

  forge.onResult({
    id: "example_result_notice",
    function: "onForgeResult"
  });
}

function modifyForge(ctx) {
  return {
    successBonus: 3,
    message: "示例：锻造成功率额外增加 3%"
  };
}

function onForgeResult(event) {
  emaki.logger.info("Forge result hook: recipe=" + event.recipeId + ", success=" + event.success + ", quality=" + event.quality);
}

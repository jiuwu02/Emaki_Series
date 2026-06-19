function main(ctx) {
  const item = emaki.module("item");
  const itemId = emaki.context.placeholder("item_id");
  const trigger = emaki.context.placeholder("item_trigger");
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] 物品触发脚本: " + itemId + " trigger=" + trigger + " item=" + item.available());
  }
  return {
    success: true,
    output: {
      item_id: String(itemId || ""),
      trigger: String(trigger || ""),
      item_available: item.available()
    }
  };
}

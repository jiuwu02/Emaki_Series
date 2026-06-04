function main(ctx) {
  const itemId = emaki.context.placeholder("item_id");
  const trigger = emaki.context.placeholder("item_trigger");
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] 物品触发脚本: " + itemId + " trigger=" + trigger);
  }
  return true;
}

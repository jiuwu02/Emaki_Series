function main(ctx) {
  if (!emaki.player.exists()) {
    return { success: false, message: "No player context" };
  }
  return emaki.action.run("attribute_add", {
    effect_id: "js_example_buff",
    attribute: "attack",
    value: "5",
    duration_ticks: "10s"
  });
}

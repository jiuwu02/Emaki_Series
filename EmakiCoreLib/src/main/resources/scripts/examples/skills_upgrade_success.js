function main(ctx) {
  const skillId = emaki.context.placeholder("skills_skill_id") || emaki.context.attribute("skill_id");
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] 技能升级成功脚本: " + skillId);
  }
  return true;
}

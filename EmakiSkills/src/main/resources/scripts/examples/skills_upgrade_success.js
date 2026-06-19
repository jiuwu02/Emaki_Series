function main(ctx) {
  const skills = emaki.module("skills");
  const skillId = emaki.context.placeholder("skills_skill_id") || emaki.context.attribute("skill_id");
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] 技能升级成功脚本: " + skillId + " skills=" + skills.available());
  }
  return {
    success: true,
    output: {
      skill_id: String(skillId || ""),
      skills_available: skills.available(),
      registered_script_actions: skills.registeredScriptActions().size()
    }
  };
}

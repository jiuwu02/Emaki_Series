function register(skills) {
  skills.registerAction({
    id: "js_lightning_strike",
    category: "javascript",
    description: "攻击当前技能目标，并可选择通过EmakiAttribute传递伤害",
    executionMode: "SYNC",
    timeoutMillis: 1000,
    parameters: [
      { name: "damage", type: "DOUBLE", required: false, defaultValue: "10", description: "应用基础伤害" },
      { name: "damage_type", type: "STRING", required: false, defaultValue: "lightning", description: "Emaki属性伤害类型ID" }
    ],
    validate: "validateLightning",
    execute: "executeLightning"
  });
}

function validateLightning(args) {
  const damage = Number(read(args, "damage", "10"));
  if (!Number.isFinite(damage) || damage <= 0) {
    return { success: false, message: "伤害必须是一个正数" };
  }
  return true;
}

function executeLightning(ctx, args) {
  const caster = ctx.caster();
  const target = ctx.target();
  if (!target.exists()) {
    return { skipped: true, message: "未选择目标" };
  }

  const damage = Number(read(args, "damage", "10"));
  const damageType = String(read(args, "damage_type", "lightning"));
  const location = target.location();
  const world = target.world();
  if (world.exists()) {
    world.strikeLightningEffect(
      Number(read(location, "x", 0)),
      Number(read(location, "y", 0)),
      Number(read(location, "z", 0))
    );
  }

  let applied = false;
  if (emaki.attribute.available()) {
    applied = emaki.attribute.applyDamage(caster, target, damageType, damage, {
      source: "js_lightning_strike",
      skill_id: ctx.skillId(),
      trigger_id: ctx.triggerId()
    });
  }
  if (!applied) {
    target.damage(damage);
  }

  return {
    success: true,
    message: "JavaScript闪电技能执行成功",
    output: {
      target: target.uuid(),
      damage: damage,
      damage_type: damageType,
      attribute_pipeline: applied
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

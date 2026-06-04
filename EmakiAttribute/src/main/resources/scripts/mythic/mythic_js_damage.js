function mythicDamage(meta, args) {
  const caster = meta.caster();
  const target = meta.firstTarget();
  if (!target.exists()) {
    return { skipped: true, message: "No MythicMobs target." };
  }

  const damage = Number(read(args, "damage", read(args, "base", meta.power())));
  const damageType = String(read(args, "damage_type", read(args, "type", "default")));
  if (!Number.isFinite(damage) || damage <= 0) {
    return { success: false, message: "damage must be a positive number" };
  }

  const attribute = emaki.module("attribute");
  const applied = attribute.applyDamage(caster, target, damageType, damage, {
    source: "mythic_js",
    mythic_mechanic: meta.mechanic(),
    mythic_power: meta.power(),
    mythic_cause: meta.cause()
  });

  return {
    success: applied,
    message: applied ? "Mythic JS damage applied." : "Attribute damage pipeline unavailable.",
    output: {
      target: target.uuid(),
      damage: damage,
      damage_type: damageType
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

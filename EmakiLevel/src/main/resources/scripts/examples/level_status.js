function main(ctx) {
  const level = emaki.module("level");
  const typeIds = level.typeIds();
  const typeId = first(typeIds, "main");
  let currentLevel = 0;
  let currentExp = 0;
  if (emaki.player.exists() && typeId) {
    currentLevel = level.level(emaki.player.uuid(), typeId);
    currentExp = level.exp(emaki.player.uuid(), typeId);
    emaki.player.sendMessage("[EmakiJS] 等级模块状态: type=" + typeId + " level=" + currentLevel + " exp=" + currentExp);
  }
  return {
    success: true,
    output: {
      level_available: level.available(),
      type_count: size(typeIds),
      sample_type: String(typeId || ""),
      current_level: currentLevel,
      current_exp: currentExp
    }
  };
}

function size(list) {
  if (list == null) {
    return 0;
  }
  if (typeof list.size === "function") {
    return list.size();
  }
  if (typeof list.length === "number") {
    return list.length;
  }
  return 0;
}

function first(list, fallback) {
  if (size(list) <= 0) {
    return fallback;
  }
  if (typeof list.get === "function") {
    return list.get(0);
  }
  return list[0] == null ? fallback : list[0];
}

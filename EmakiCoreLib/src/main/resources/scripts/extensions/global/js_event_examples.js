const ENABLE_EXAMPLE_EVENTS = false;

function register(api) {
  if (!ENABLE_EXAMPLE_EVENTS) {
    return;
  }

  api.onEvent({
    id: "js_join_logger",
    event: "player_join",
    priority: "NORMAL",
    ignoreCancelled: true,
    function: "onJoin",
    timeoutMillis: 500
  });

  api.onEvent({
    id: "js_right_click_logger",
    event: "player_interact",
    priority: "NORMAL",
    ignoreCancelled: true,
    function: "onInteract",
    timeoutMillis: 500
  });

  api.onEvent({
    id: "js_damage_logger",
    event: "entity_damage_by_entity",
    priority: "MONITOR",
    ignoreCancelled: true,
    function: "onDamage",
    timeoutMillis: 500
  });
}

function onJoin(event, args) {
  const player = event.player();
  if (player.exists()) {
    emaki.logger.info("[js-event] " + player.name() + " joined " + event.location().world);
  }
  return true;
}

function onInteract(event, args) {
  if (!event.rightClick()) {
    return { skipped: true, message: "not right click" };
  }
  const player = event.player();
  emaki.logger.info("[js-event] " + player.name() + " right clicked with " + event.itemType());
  return true;
}

function onDamage(event, args) {
  const damager = event.damager();
  const victim = event.victim();
  emaki.logger.info("[js-event] " + damager.name() + " -> " + victim.name() + " damage=" + event.damage());
  return true;
}

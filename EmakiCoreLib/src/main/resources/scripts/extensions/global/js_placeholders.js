function register(api) {
  api.registerPlaceholder({
    id: "js_online_count",
    function: "onlineCount",
    timeoutMillis: 500
  });

  api.registerPlaceholder({
    id: "js_player_world",
    function: "playerWorld",
    timeoutMillis: 500
  });
}

function onlineCount(ctx, args) {
  return emaki.server.onlinePlayers().size();
}

function playerWorld(ctx, args) {
  if (emaki.player == null || !emaki.player.exists()) {
    return "";
  }
  return emaki.player.world();
}

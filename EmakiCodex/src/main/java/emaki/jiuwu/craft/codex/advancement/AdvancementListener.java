package emaki.jiuwu.craft.codex.advancement;

import java.util.List;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.api.event.AdvancementCompletedEvent;
import emaki.jiuwu.craft.corelib.action.ActionContext;






public final class AdvancementListener implements Listener {

    private final EmakiCodexPlugin plugin;
    private final AdvancementRegistrar registrar;

    public AdvancementListener(EmakiCodexPlugin plugin, AdvancementRegistrar registrar) {
        this.plugin = plugin;
        this.registrar = registrar;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        NamespacedKey key = event.getAdvancement().getKey();
        AdvancementDefinition definition = registrar.definitionByKey(key);
        if (definition == null) {
            return;
        }
        org.bukkit.Bukkit.getPluginManager().callEvent(
                new AdvancementCompletedEvent(event.getPlayer(), definition.id(), key.toString()));
        List<String> lines = definition.completeActions();
        if (lines.isEmpty()) {
            return;
        }
        String pageId = registrar.pageByKey(key);
        ActionContext context = ActionContext.create(plugin, event.getPlayer(), "advancement.complete", false)
                .withPlaceholders(Map.of(
                        "advancement_id", key.toString(),
                        "advancement_node", definition.id(),
                        "advancement_title", definition.title(),
                        "advancement_page", pageId == null ? "" : pageId
                ));
        plugin.coreLib().actionExecutor().executeAll(context, lines, true);
    }
}

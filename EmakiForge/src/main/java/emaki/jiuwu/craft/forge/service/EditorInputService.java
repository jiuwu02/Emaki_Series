package emaki.jiuwu.craft.forge.service;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class EditorInputService implements Listener {

    private final EmakiForgePlugin plugin;
    private final EditorGuiService editorGuiService;
    private final EditorStateManager stateManager;

    EditorInputService(EmakiForgePlugin plugin,
            EditorGuiService editorGuiService,
            EditorStateManager stateManager) {
        this.plugin = plugin;
        this.editorGuiService = editorGuiService;
        this.stateManager = stateManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        EditorSession session = stateManager.get(player);
        if (session == null || session.pendingInput() == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, () -> handleInput(player, message));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        editorGuiService.abandonSession(event.getPlayer());
    }

    private void handleInput(Player player, String message) {
        EditorSession session = stateManager.get(player);
        if (session == null) {
            return;
        }
        EditorSession.PendingInput pendingInput = session.pendingInput();
        if (pendingInput == null) {
            return;
        }
        session.clearPendingInput();
        if (pendingInput.expired()) {
            plugin.messageService().sendRaw(player, "<yellow>聊天输入已超时，已返回编辑器。</yellow>");
            editorGuiService.resume(player);
            return;
        }
        String content = message == null ? "" : message.trim();
        if ("cancel".equalsIgnoreCase(content)) {
            plugin.messageService().sendRaw(player, "<yellow>已取消本次输入。</yellow>");
            editorGuiService.resume(player);
            return;
        }
        try {
            pendingInput.submitHandler().accept(content);
        } catch (Exception exception) {
            plugin.messageService().sendRaw(player, "<red>输入处理失败: " + exception.getMessage() + "</red>");
            editorGuiService.resume(player);
        }
    }
}

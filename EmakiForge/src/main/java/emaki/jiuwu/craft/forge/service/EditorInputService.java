package emaki.jiuwu.craft.forge.service;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

    public boolean submitPendingInput(Player player, String message) {
        if (player == null) {
            return false;
        }
        EditorSession session = stateManager.get(player);
        if (session == null || session.pendingInput() == null) {
            plugin.messageService().sendRaw(player, "<yellow>当前没有待提交的编辑器输入。</yellow>");
            return false;
        }
        EditorSession.PendingInput pendingInput = session.pendingInput();
        session.clearPendingInput();
        if (pendingInput.expired()) {
            plugin.messageService().sendRaw(player, "<yellow>输入已超时，已返回编辑器。</yellow>");
            editorGuiService.resume(player);
            return true;
        }
        String content = message == null ? "" : message.trim();
        if ("cancel".equalsIgnoreCase(content)) {
            plugin.messageService().sendRaw(player, "<yellow>已取消本次输入。</yellow>");
            editorGuiService.resume(player);
            return true;
        }
        try {
            pendingInput.submitHandler().accept(content);
        } catch (Exception exception) {
            plugin.messageService().sendRaw(player, "<red>输入处理失败: " + exception.getMessage() + "</red>");
            editorGuiService.resume(player);
        }
        return true;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        editorGuiService.abandonSession(event.getPlayer());
    }
}

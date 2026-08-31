package emaki.jiuwu.craft.codex.codex.provider;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.extension.ContributionProviderRegistration;
import emaki.jiuwu.craft.codex.codex.loader.CodexCategoryLoader;
import emaki.jiuwu.craft.codex.codex.service.PlayerCodexStore;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;

public final class CodexProviderRegistrar {

    private final Plugin plugin;
    private final CodexCategoryLoader categoryLoader;
    private final PlayerCodexStore codexStore;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;

    private ContributionProviderRegistration attributeRegistration;

    public CodexProviderRegistrar(Plugin plugin,
            CodexCategoryLoader categoryLoader,
            PlayerCodexStore codexStore,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.categoryLoader = categoryLoader;
        this.codexStore = codexStore;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
    }

    public boolean attributeRegistered() {
        return attributeRegistration != null;
    }

    public void register() {
        if (attributeRegistration != null || !EmakiAttributeApi.status().usable()) {
            return;
        }
        attributeRegistration = EmakiAttributeApi.extensions().registerContributionProvider(plugin,
                new CodexAttributeProvider(categoryLoader, codexStore, plugin.getLogger()));
    }

    public void unregister() {
        if (attributeRegistration != null) {
            attributeRegistration.close();
            attributeRegistration = null;
        }
    }

    public void resyncPlayer(Player player) {
        if (attributeRegistration == null
                || player == null
                || !player.isOnline()
                || !EmakiAttributeApi.status().usable()) {
            return;
        }
        Runnable task = () -> EmakiAttributeApi.operations().resyncPlayer(player);
        if (threadOwnership.isEntityOwned(player)) {
            task.run();
            return;
        }
        executionDispatcher.runEntity(plugin, player, task, null);
    }

    public void resyncAll() {
        if (attributeRegistration == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            resyncPlayer(player);
        }
    }
}

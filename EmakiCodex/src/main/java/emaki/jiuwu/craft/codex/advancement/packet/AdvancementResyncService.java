package emaki.jiuwu.craft.codex.advancement.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.advancements.Advancement;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementDisplay;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementHolder;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAdvancements;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementFrame;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementPage;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

import net.kyori.adventure.text.Component;
















public final class AdvancementResyncService {

    private final JavaPlugin plugin;
    private final AdvancementRegistrar registrar;
    private final ItemSourceService itemSourceService;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;






    public AdvancementResyncService(JavaPlugin plugin,
            AdvancementRegistrar registrar,
            ItemSourceService itemSourceService,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.registrar = registrar;
        this.itemSourceService = itemSourceService;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
    }






    public CompletableFuture<Integer> resyncAllAsync() {
        List<AdvancementHolder> holders = buildHolders();
        if (holders.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(players.size());
        for (Player player : players) {
            futures.add(resyncAsync(player, holders));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(_ -> {
                    int sent = 0;
                    for (CompletableFuture<Boolean> future : futures) {
                        if (Boolean.TRUE.equals(future.getNow(false))) {
                            sent++;
                        }
                    }
                    return sent;
                });
    }







    public boolean resync(Player player) {
        List<AdvancementHolder> holders = buildHolders();
        return !holders.isEmpty() && sendTo(player, holders);
    }

    private CompletableFuture<Boolean> resyncAsync(Player player, List<AdvancementHolder> holders) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (player == null || !player.isOnline()) {
            future.complete(false);
            return future;
        }
        Runnable operation = () -> future.complete(sendTo(player, holders));
        try {
            if (threadOwnership != null && threadOwnership.isEntityOwned(player)) {
                operation.run();
                return future;
            }
            var scheduled = executionDispatcher.runEntity(plugin, player, operation,
                    () -> future.complete(false));
            if (scheduled == null) {
                future.complete(false);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "[Codex] Advancement resync scheduling failed for " + player.getName() + ": " + throwable.getMessage());
            future.complete(false);
        }
        return future;
    }

    private boolean sendTo(Player player, List<AdvancementHolder> holders) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        try {
            WrapperPlayServerUpdateAdvancements packet = new WrapperPlayServerUpdateAdvancements(
                    false, holders, Collections.emptySet(), Collections.emptyMap(), true);
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "[Codex] Advancement resync failed for " + player.getName() + ": " + throwable.getMessage());
            return false;
        }
    }






    private List<AdvancementHolder> buildHolders() {
        List<AdvancementHolder> holders = new ArrayList<>();
        for (AdvancementRegistrar.RegisteredNode node : registrar.registeredNodes()) {
            try {
                holders.add(buildHolder(node));
            } catch (Throwable throwable) {
                plugin.getLogger().log(Level.FINE,
                        "[Codex] Skipped advancement holder " + node.key() + ": " + throwable.getMessage());
            }
        }
        return holders;
    }

    private AdvancementHolder buildHolder(AdvancementRegistrar.RegisteredNode node) {
        AdvancementDefinition definition = node.definition();
        AdvancementPage page = node.page();

        ResourceLocation background = definition.isRoot() && page != null && Texts.isNotBlank(page.background())
                ? new ResourceLocation(page.background()) : null;

        AdvancementDisplay display = new AdvancementDisplay(
                textComponent(definition.title()),
                textComponent(definition.description()),
                resolveIcon(definition.icon()),
                frameType(definition.frame()),
                background,
                definition.showToast(),
                definition.hidden(),
                (float) definition.x(),
                (float) definition.y());

        ResourceLocation parent = Texts.isBlank(node.parentKey()) ? null : new ResourceLocation(node.parentKey());
        List<List<String>> requirements = List.of(List.of(AdvancementDefinition.CRITERION));
        Advancement advancement = new Advancement(parent, display, requirements, false);
        return new AdvancementHolder(new ResourceLocation(node.key().toString()), advancement);
    }

    private com.github.retrooper.packetevents.protocol.item.ItemStack resolveIcon(String iconShorthand) {
        ItemStack bukkitIcon = null;
        if (Texts.isNotBlank(iconShorthand) && itemSourceService != null) {
            ItemSource source = ItemSourceUtil.parse(iconShorthand);
            if (source != null) {
                ItemStack created = itemSourceService.createItem(source, 1);
                if (created != null && !created.getType().isAir()) {
                    bukkitIcon = created;
                }
            }
        }
        if (bukkitIcon == null) {
            bukkitIcon = new ItemStack(org.bukkit.Material.BOOK);
        }
        return SpigotConversionUtil.fromBukkitItemStack(bukkitIcon);
    }

    private AdvancementType frameType(AdvancementFrame frame) {
        if (frame == null) {
            return AdvancementType.TASK;
        }

        return switch (frame) {
            case GOAL -> AdvancementType.GOAL;
            case CHALLENGE -> AdvancementType.CHALLENGE;
            default -> AdvancementType.TASK;
        };
    }

    private Component textComponent(String miniMessage) {
        return MiniMessages.parse(miniMessage == null ? "" : miniMessage);
    }
}

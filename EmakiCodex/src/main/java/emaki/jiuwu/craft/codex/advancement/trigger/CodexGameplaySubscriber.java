package emaki.jiuwu.craft.codex.advancement.trigger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.event.EmakiEventBus;
import emaki.jiuwu.craft.corelib.event.gameplay.BlockBreakGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.BrewGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.CraftGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.EntityKillEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.FishGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.FurnaceExtractGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.GameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.MythicKillEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.TameGameplayEvent;

/**
 * Subscribes EmakiCodex to CoreLib's shared gameplay events and forwards each one to
 * {@link CodexTriggerService} as a trigger key with domain variables. This replaces the old
 * self-registered {@code CodexTriggerListener}: the Bukkit event handling, MythicMobs reflection
 * and brew-stand attribution now live once inside CoreLib's
 * {@link emaki.jiuwu.craft.corelib.event.gameplay.GameplayEventPublisher}.
 *
 * <p>Codex has no experience, anti-abuse or attribution-window policy of its own, so every event
 * is forwarded unconditionally; {@link CodexTriggerService} decides which advancements match and
 * whether their conditions pass. The {@code crop_harvest} trigger is derived here from a block
 * break flagged {@code mature}, preserving the previous listener's behavior.
 */
public final class CodexGameplaySubscriber {

    /** Grace window for attributing a finished brew, preserved from the old CodexTriggerListener. */
    private static final long BREW_ATTRIBUTION_EXPIRE_TICKS = 600L;

    private final EmakiCodexPlugin plugin;
    private final CodexTriggerService triggerService;
    private final List<EmakiEventBus.Subscription> subscriptions = new ArrayList<>();

    public CodexGameplaySubscriber(EmakiCodexPlugin plugin, CodexTriggerService triggerService) {
        this.plugin = plugin;
        this.triggerService = triggerService;
    }

    /**
     * Registers all gameplay subscriptions on the shared bus, owned by this plugin so they are
     * released together on {@link #unsubscribe()} or plugin disable.
     */
    public void subscribe(EmakiEventBus eventBus) {
        subscribe(eventBus, EntityKillEvent.class, this::forward);
        subscribe(eventBus, MythicKillEvent.class, this::forward);
        subscribe(eventBus, CraftGameplayEvent.class, this::forward);
        subscribe(eventBus, FurnaceExtractGameplayEvent.class, this::forward);
        subscribe(eventBus, FishGameplayEvent.class, this::forward);
        subscribe(eventBus, TameGameplayEvent.class, this::forward);
        subscribe(eventBus, BrewGameplayEvent.class, this::onBrew);
        subscribe(eventBus, BlockBreakGameplayEvent.class, this::onBlockBreak);
    }

    /** Releases every subscription created by {@link #subscribe(EmakiEventBus)}. */
    public void unsubscribe() {
        for (EmakiEventBus.Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
        subscriptions.clear();
    }

    private <T extends GameplayEvent> void subscribe(EmakiEventBus eventBus,
            Class<T> type, java.util.function.Consumer<T> handler) {
        subscriptions.add(eventBus.subscribe(plugin, type, handler));
    }

    private void onBlockBreak(BlockBreakGameplayEvent event) {
        forward(event);
        if (event.mature()) {
            fire(event.player(), "crop_harvest", event.variables());
        }
    }

    private void onBrew(BrewGameplayEvent event) {
        // CoreLib keeps a looser attribution window; enforce Codex's original, tighter grace here.
        if (event.ageTicks() <= BREW_ATTRIBUTION_EXPIRE_TICKS) {
            forward(event);
        }
    }

    private void forward(GameplayEvent event) {
        fire(event.player(), event.triggerKey(), event.variables());
    }

    private void fire(Player player, String trigger, Map<String, Object> variables) {
        if (player == null) {
            return;
        }
        Map<String, Object> merged = new LinkedHashMap<>(variables);
        merged.putIfAbsent("player", player.getName());
        merged.putIfAbsent("world", player.getWorld() == null ? "" : player.getWorld().getName());
        triggerService.fire(player, trigger, merged);
    }
}

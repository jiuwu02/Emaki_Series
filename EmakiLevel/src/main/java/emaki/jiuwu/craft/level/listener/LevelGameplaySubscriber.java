package emaki.jiuwu.craft.level.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.event.EmakiEventBus;
import emaki.jiuwu.craft.corelib.event.gameplay.BlockBreakGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.BlockPlaceGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.BrewGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.CraftGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.EntityKillEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.FishGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.FurnaceExtractGameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.GameplayEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.MythicKillEvent;
import emaki.jiuwu.craft.corelib.event.gameplay.TameGameplayEvent;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.SourceRuleConfig;
import emaki.jiuwu.craft.level.service.SourceExperienceService;

/**
 * Subscribes EmakiLevel to CoreLib's shared gameplay events and awards source experience.
 *
 * <p>This replaces the seven per-event {@code *SourceListener} classes. CoreLib's
 * {@link emaki.jiuwu.craft.corelib.event.gameplay.GameplayEventPublisher} now owns the raw
 * Bukkit event handling, MythicMobs reflection, last-damager attribution and brew-stand user
 * tracking; everything that remains here is EmakiLevel's own <em>business policy</em> and is kept
 * intact:
 * <ul>
 *   <li>last-damager toggle — when {@code last_damager_tracking} is disabled, indirect kills
 *       (no {@code directKill}) are ignored, preserving the old behavior;</li>
 *   <li>anti-abuse — near-spawner scanning, player-placed-block tracking and the
 *       {@code ignore_player_placed_blocks} / {@code placed_block_exp} rules;</li>
 *   <li>per-source attribution windows for brewing;</li>
 *   <li>{@code include_players} for entity kills.</li>
 * </ul>
 *
 * <p>Subscriptions are owned by the plugin so they are released on {@link #unsubscribe()} or
 * plugin disable.
 */
public final class LevelGameplaySubscriber {

    private final EmakiLevelPlugin plugin;
    private final SourceExperienceService sourceService;
    private final List<EmakiEventBus.Subscription> subscriptions = new ArrayList<>();

    public LevelGameplaySubscriber(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
        this.sourceService = new SourceExperienceService(plugin);
    }

    /** Registers every gameplay subscription on the shared bus. */
    public void subscribe(EmakiEventBus eventBus) {
        subscribe(eventBus, EntityKillEvent.class, this::onEntityKill);
        subscribe(eventBus, MythicKillEvent.class, this::onMythicKill);
        subscribe(eventBus, BlockPlaceGameplayEvent.class, this::onBlockPlace);
        subscribe(eventBus, BlockBreakGameplayEvent.class, this::onBlockBreak);
        subscribe(eventBus, CraftGameplayEvent.class, this::onCraft);
        subscribe(eventBus, FurnaceExtractGameplayEvent.class, this::onFurnaceExtract);
        subscribe(eventBus, FishGameplayEvent.class, this::onFish);
        subscribe(eventBus, TameGameplayEvent.class, this::onTame);
        subscribe(eventBus, BrewGameplayEvent.class, this::onBrew);
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

    // ------------------------------------------------------------------
    // Combat
    // ------------------------------------------------------------------

    private void onEntityKill(EntityKillEvent event) {
        // Preserve the old last-damager toggle: with tracking off, only vanilla-attributed
        // (direct) kills award experience.
        if (!event.directKill() && !plugin.appConfig().lastDamagerTracking()) {
            return;
        }
        LivingEntity entity = event.victim();
        Player killer = event.player();
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("entity_kill")) {
            if (!source.includePlayers() && entity instanceof Player) {
                continue;
            }
            if (source.ignoreNearSpawner()
                    && plugin.antiAbuseService().nearSpawner(entity.getLocation(), source.spawnerScanRadius())) {
                continue;
            }
            SourceRuleConfig.Rule rule = sourceService.matchEntity(source, entity.getType());
            if (rule == null) {
                continue;
            }
            sourceService.award(killer, source, rule, Map.of("entity_type", entity.getType().name()), "entity_kill");
        }
    }

    private void onMythicKill(MythicKillEvent event) {
        if (!plugin.appConfig().mythicEnabled() || !plugin.appConfig().mythicKillSources()) {
            return;
        }
        LivingEntity entity = event.victim();
        Player killer = event.player();
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("mythic_mob_kill")) {
            if (source.ignoreNearSpawner()
                    && plugin.antiAbuseService().nearSpawner(entity.getLocation(), source.spawnerScanRadius())) {
                continue;
            }
            SourceRuleConfig.Rule rule = sourceService.matchMobId(source, event.mobId());
            if (rule != null) {
                sourceService.award(killer, source, rule,
                        Map.of("mythic_id", event.mobId(), "mythic_level", event.level()), "mythic_mob_kill");
            }
        }
    }

    // ------------------------------------------------------------------
    // Blocks
    // ------------------------------------------------------------------

    private void onBlockPlace(BlockPlaceGameplayEvent event) {
        plugin.antiAbuseService().recordPlacedBlock(event.block().getLocation());
    }

    private void onBlockBreak(BlockBreakGameplayEvent event) {
        boolean playerPlaced = plugin.antiAbuseService().removePlacedBlock(event.block().getLocation());
        String blockType = event.block().getType().name();
        awardBlock("block_break", event, playerPlaced, blockType);
        if (event.mature()) {
            awardBlock("crop_harvest", event, playerPlaced, blockType);
        }
    }

    private void awardBlock(String trigger, BlockBreakGameplayEvent event, boolean playerPlaced, String blockType) {
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger(trigger)) {
            if ((source.ignorePlayerPlacedBlocks() || !plugin.appConfig().placedBlockExp()) && playerPlaced) {
                continue;
            }
            SourceRuleConfig.Rule rule = sourceService.matchBlock(source, event.block().getType());
            if (rule != null) {
                sourceService.award(event.player(), source, rule, Map.of("block_type", blockType), trigger);
            }
        }
    }

    // ------------------------------------------------------------------
    // Crafting / smelting
    // ------------------------------------------------------------------

    private void onCraft(CraftGameplayEvent event) {
        int amount = Math.max(1, event.result().getAmount());
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("craft_item")) {
            SourceRuleConfig.Rule rule = sourceService.matchItem(source, event.result());
            if (rule != null) {
                sourceService.award(event.player(), source, rule,
                        Map.of("result_amount", amount, "result_type", event.result().getType().name()), "craft_item");
            }
        }
    }

    private void onFurnaceExtract(FurnaceExtractGameplayEvent event) {
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("furnace_extract")) {
            SourceRuleConfig.Rule rule = sourceService.matchItem(source, event.result());
            if (rule != null) {
                sourceService.award(event.player(), source, rule,
                        Map.of("result_amount", event.amount(), "result_type", event.result().getType().name()), "furnace_extract");
            }
        }
    }

    // ------------------------------------------------------------------
    // Fishing / taming
    // ------------------------------------------------------------------

    private void onFish(FishGameplayEvent event) {
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("player_fish")) {
            SourceRuleConfig.Rule rule = sourceService.matchState(source, event.state());
            if (rule != null) {
                sourceService.award(event.player(), source, rule, Map.of("fish_state", event.state()), "player_fish");
            }
        }
    }

    private void onTame(TameGameplayEvent event) {
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("entity_tame")) {
            SourceRuleConfig.Rule rule = sourceService.matchEntity(source, event.entityType());
            if (rule != null) {
                sourceService.award(event.player(), source, rule,
                        Map.of("entity_type", event.entityType().name()), "entity_tame");
            }
        }
    }

    // ------------------------------------------------------------------
    // Brewing
    // ------------------------------------------------------------------

    private void onBrew(BrewGameplayEvent event) {
        String potionType = event.potionType();
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("brew_complete")) {
            if (event.ageTicks() > Math.max(1, source.attributionExpireTicks())) {
                continue;
            }
            SourceRuleConfig.Rule rule = sourceService.matchPotion(source, potionType);
            if (rule != null) {
                sourceService.award(event.player(), source, rule, Map.of("potion_type", potionType), "brew_complete");
            }
        }
    }
}

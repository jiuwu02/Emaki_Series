package emaki.jiuwu.craft.level.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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





















public final class LevelGameplaySubscriber {

    private final EmakiLevelPlugin plugin;
    private final SourceExperienceService sourceService;
    private final List<EmakiEventBus.Subscription> subscriptions = new ArrayList<>();

    public LevelGameplaySubscriber(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
        this.sourceService = new SourceExperienceService(plugin);
    }


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


    public void unsubscribe() {
        for (EmakiEventBus.Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
        subscriptions.clear();
    }

    private <T extends GameplayEvent> void subscribe(EmakiEventBus eventBus,
            Class<T> type, Consumer<T> handler) {
        subscriptions.add(eventBus.subscribe(plugin, type, handler));
    }





    private void onEntityKill(EntityKillEvent event) {
        LivingEntity entity = event.victim();
        Player killer = event.player();
        sourceService.awardExtensions(killer, "entity_kill", Map.of(
                "entity_type", entity.getType().name(),
                "victim", entity,
                "direct_kill", event.directKill()));
        if (!event.directKill() && !plugin.appConfig().lastDamagerTracking()) {
            return;
        }
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
        LivingEntity entity = event.victim();
        Player killer = event.player();
        sourceService.awardExtensions(killer, "mythic_mob_kill", Map.of(
                "mythic_id", event.mobId(),
                "mythic_level", event.level(),
                "victim", entity));
        if (!plugin.appConfig().mythicEnabled() || !plugin.appConfig().mythicKillSources()) {
            return;
        }
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





    private void onBlockPlace(BlockPlaceGameplayEvent event) {
        sourceService.awardExtensions(event.player(), "block_place", Map.of(
                "block", event.block(),
                "block_type", event.block().getType().name()));
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
        sourceService.awardExtensions(event.player(), trigger, Map.of(
                "block", event.block(),
                "block_type", blockType,
                "player_placed", playerPlaced,
                "mature", event.mature()));
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





    private void onCraft(CraftGameplayEvent event) {
        int amount = Math.max(1, event.result().getAmount());
        sourceService.awardExtensions(event.player(), "craft_item", Map.of(
                "result", event.result(),
                "result_amount", amount,
                "result_type", event.result().getType().name()));
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("craft_item")) {
            SourceRuleConfig.Rule rule = sourceService.matchItem(source, event.result());
            if (rule != null) {
                sourceService.award(event.player(), source, rule,
                        Map.of("result_amount", amount, "result_type", event.result().getType().name()), "craft_item");
            }
        }
    }

    private void onFurnaceExtract(FurnaceExtractGameplayEvent event) {
        sourceService.awardExtensions(event.player(), "furnace_extract", Map.of(
                "result", event.result(),
                "result_amount", event.amount(),
                "result_type", event.result().getType().name()));
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("furnace_extract")) {
            SourceRuleConfig.Rule rule = sourceService.matchItem(source, event.result());
            if (rule != null) {
                sourceService.award(event.player(), source, rule,
                        Map.of("result_amount", event.amount(), "result_type", event.result().getType().name()), "furnace_extract");
            }
        }
    }





    private void onFish(FishGameplayEvent event) {
        sourceService.awardExtensions(
                event.player(), "player_fish", Map.of("fish_state", event.state()));
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("player_fish")) {
            SourceRuleConfig.Rule rule = sourceService.matchState(source, event.state());
            if (rule != null) {
                sourceService.award(event.player(), source, rule, Map.of("fish_state", event.state()), "player_fish");
            }
        }
    }

    private void onTame(TameGameplayEvent event) {
        sourceService.awardExtensions(
                event.player(), "entity_tame", Map.of("entity_type", event.entityType().name()));
        for (SourceRuleConfig source : plugin.sourceRuleLoader().byTrigger("entity_tame")) {
            SourceRuleConfig.Rule rule = sourceService.matchEntity(source, event.entityType());
            if (rule != null) {
                sourceService.award(event.player(), source, rule,
                        Map.of("entity_type", event.entityType().name()), "entity_tame");
            }
        }
    }





    private void onBrew(BrewGameplayEvent event) {
        String potionType = event.potionType();
        sourceService.awardExtensions(event.player(), "brew_complete", Map.of(
                "potion_type", potionType,
                "age_ticks", event.ageTicks()));
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

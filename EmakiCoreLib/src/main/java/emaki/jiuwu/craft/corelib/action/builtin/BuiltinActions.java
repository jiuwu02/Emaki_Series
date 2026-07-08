package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.List;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.loop.CancelLoopAction;
import emaki.jiuwu.craft.corelib.action.loop.LoopActionService;
import emaki.jiuwu.craft.corelib.action.loop.LoopAsyncAction;
import emaki.jiuwu.craft.corelib.action.loop.LoopSyncAction;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

public final class BuiltinActions {

    private BuiltinActions() {
    }

    public static void registerAll(ActionRegistry registry,
            EconomyManager economyManager,
            ItemSourceService itemSourceService) {
        registerAll(registry, economyManager, itemSourceService, null, null, null, null, null);
    }

    public static void registerAll(ActionRegistry registry,
            EconomyManager economyManager,
            ItemSourceService itemSourceService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge) {
        registerAll(registry, economyManager, itemSourceService, craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, null, null);
    }

    public static void registerAll(ActionRegistry registry,
            EconomyManager economyManager,
            ItemSourceService itemSourceService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge,
            CustomBlockBridge oraxenBlockBridge) {
        registerAll(registry, economyManager, itemSourceService, craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, oraxenBlockBridge, null);
    }

    public static void registerAll(ActionRegistry registry,
            EconomyManager economyManager,
            ItemSourceService itemSourceService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge,
            CustomBlockBridge oraxenBlockBridge,
            LoopActionService loopActionService) {
        register(registry, new SendMessageAction(), "send_message", "message", "msg");
        register(registry, new SendTitleAction(), "send_title", "title");
        register(registry, new SendActionBarAction(), "send_actionbar", "send_action_bar", "actionbar", "action_bar");
        register(registry, new BroadcastMessageAction(), "broadcast", "broadcast_message");
        register(registry, new PlaySoundAction(), "play_sound", "sound");
        register(registry, new SpawnParticleAction(), "spawn_particle", "particle");
        register(registry, new BossBarShowAction(), "bossbar_show", "showbossbar", "show_bossbar", "show_boss_bar");
        register(registry, new BossBarHideAction(), "bossbar_hide", "hidebossbar", "hide_bossbar", "hide_boss_bar");
        register(registry, new GiveMoneyAction(economyManager), "give_money", "money_give");
        register(registry, new TakeMoneyAction(economyManager), "take_money", "remove_money", "money_take");
        register(registry, new SetMoneyAction(economyManager), "set_money", "money_set");
        register(registry, new CreateItemAction(itemSourceService), "create_item", "makeitem", "make_item");
        register(registry, new SendItemAction(), "send_item");
        register(registry, new GiveItemAction(itemSourceService), "give_item");
        register(registry, new SetItemAction(itemSourceService), "set_item");
        register(registry, new ClearItemAction(itemSourceService), "clear_item", "removeitem", "remove_item");
        register(registry, new TakeItemAction(itemSourceService), "take_item");
        register(registry, new DropItemAction(itemSourceService), "drop_item");
        register(registry, new PlaceBlockAction(itemSourceService, craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, oraxenBlockBridge), "place_block");
        register(registry, new SetBlockAction(), "set_block");
        register(registry, new BreakBlockAction(), "break_block");
        register(registry, new TeleportAction(), "teleport_player", "tp");
        register(registry, new SpawnEntityAction(), "spawn_entity");
        register(registry, new KillEntityAction(), "kill_entity", "kill");
        register(registry, new ExplosionAction(), "explode", "create_explosion");
        register(registry, new HealAction(), "heal_player");
        register(registry, new DamageAction(), "damage_player");
        register(registry, new FeedAction(), "feed_player");
        register(registry, new IgniteAction(), "ignite_player", "set_fire");
        register(registry, new ExtinguishAction(), "extinguish_player");
        register(registry, new SetHealthAction(), "set_health");
        register(registry, new RepairItemAction(), "repair_item");
        register(registry, new DamageItemAction(), "damage_item");
        register(registry, new GiveExpAction(), "give_exp", "addexp", "add_exp");
        register(registry, new TakeExpAction(), "take_exp", "removeexp", "remove_exp");
        register(registry, new SetExpAction(), "set_exp");
        register(registry, new GivePotionEffectAction(), "give_potion_effect", "addpotioneffect", "add_potion_effect");
        register(registry, new RemovePotionEffectAction(), "remove_potion_effect");
        register(registry, new ClearPotionEffectsAction(), "clear_potion_effects");
        register(registry, new RunCommandAsPlayerAction(), "run_command_as_player", "playercommand", "player_command");
        register(registry, new RunCommandAsOpAction(), "run_command_as_op", "opcommand", "op_command");
        register(registry, new RunCommandAsConsoleAction(), "run_command_as_console", "consolecommand", "console_command");
        register(registry, new UseTemplateAction(), "use_template", "actiontemplate", "action_template");
        if (loopActionService != null) {
            registry.register(new LoopSyncAction(loopActionService));
            registry.register(new LoopAsyncAction(loopActionService));
            registry.register(new CancelLoopAction(loopActionService));
        }
        register(registry, new CastMythicSkillAction(), "cast_mythic_skill", "mythicskill", "mythic_skill");
    }

    private static void register(ActionRegistry registry, Action action, String... aliases) {
        registry.register(action);
        if (aliases == null || aliases.length == 0) {
            return;
        }
        for (String alias : List.of(aliases)) {
            if (alias != null && !alias.isBlank() && !alias.equalsIgnoreCase(action.id())) {
                registry.register(new AliasAction(alias, action));
            }
        }
    }
}

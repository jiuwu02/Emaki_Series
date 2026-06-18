package emaki.jiuwu.craft.corelib.action.builtin;

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
        registerAll(registry, economyManager, itemSourceService, null, null, null);
    }

    public static void registerAll(ActionRegistry registry,
            EconomyManager economyManager,
            ItemSourceService itemSourceService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge) {
        registerAll(registry, economyManager, itemSourceService, craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, null);
    }

    public static void registerAll(ActionRegistry registry,
            EconomyManager economyManager,
            ItemSourceService itemSourceService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge,
            LoopActionService loopActionService) {
        registry.register(new SendMessageAction());
        registry.register(new SendTitleAction());
        registry.register(new SendActionBarAction());
        registry.register(new BroadcastMessageAction());
        registry.register(new PlaySoundAction());
        registry.register(new SpawnParticleAction());
        registry.register(new GiveMoneyAction(economyManager));
        registry.register(new TakeMoneyAction(economyManager));
        registry.register(new SetMoneyAction(economyManager));
        registry.register(new CreateItemAction(itemSourceService));
        registry.register(new SendItemAction());
        registry.register(new ClearItemAction(itemSourceService));
        registry.register(new DropItemAction(itemSourceService));
        registry.register(new PlaceBlockAction(itemSourceService, craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge));
        registry.register(new TeleportAction());
        registry.register(new HealAction());
        registry.register(new DamageAction());
        registry.register(new SetHealthAction());
        registry.register(new GiveExpAction());
        registry.register(new TakeExpAction());
        registry.register(new SetExpAction());
        registry.register(new GivePotionEffectAction());
        registry.register(new RemovePotionEffectAction());
        registry.register(new ClearPotionEffectsAction());
        registry.register(new RunCommandAsPlayerAction());
        registry.register(new RunCommandAsOpAction());
        registry.register(new RunCommandAsConsoleAction());
        registry.register(new UseTemplateAction());
        if (loopActionService != null) {
            registry.register(new LoopSyncAction(loopActionService));
            registry.register(new LoopAsyncAction(loopActionService));
            registry.register(new CancelLoopAction(loopActionService));
        }
        registry.register(new CastMythicSkillAction());
    }
}

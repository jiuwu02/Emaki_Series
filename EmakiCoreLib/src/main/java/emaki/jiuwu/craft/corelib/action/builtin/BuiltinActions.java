package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

public final class BuiltinActions {

    private BuiltinActions() {
    }

    public static void registerAll(ActionRegistry registry,
            EconomyManager economyManager,
            ItemSourceService itemSourceService) {
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
        registry.register(new CastMythicSkillAction());
    }
}

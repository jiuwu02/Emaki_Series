package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.operation.OperationRegistry;

public final class BuiltinOperations {

    private BuiltinOperations() {
    }

    public static void registerAll(OperationRegistry registry, EconomyManager economyManager) {
        registry.register(new SendMessageOperation());
        registry.register(new SendTitleOperation());
        registry.register(new SendActionBarOperation());
        registry.register(new BroadcastMessageOperation());
        registry.register(new PlaySoundOperation());
        registry.register(new SpawnParticleOperation());
        registry.register(new GiveMoneyOperation(economyManager));
        registry.register(new TakeMoneyOperation(economyManager));
        registry.register(new SetMoneyOperation(economyManager));
        registry.register(new TeleportOperation());
        registry.register(new HealOperation());
        registry.register(new DamageOperation());
        registry.register(new SetHealthOperation());
        registry.register(new GiveExpOperation());
        registry.register(new TakeExpOperation());
        registry.register(new SetExpOperation());
        registry.register(new GivePotionEffectOperation());
        registry.register(new RemovePotionEffectOperation());
        registry.register(new ClearPotionEffectsOperation());
        registry.register(new RunCommandAsPlayerOperation());
        registry.register(new RunCommandAsOpOperation());
        registry.register(new RunCommandAsConsoleOperation());
        registry.register(new UseTemplateOperation());
    }
}

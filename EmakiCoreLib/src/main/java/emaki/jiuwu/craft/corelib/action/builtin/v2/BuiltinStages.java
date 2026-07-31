package emaki.jiuwu.craft.corelib.action.builtin.v2;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.AfterGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.ChanceGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.CreateItemGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.EveryGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.KeepGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.LimitGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.SetGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.SortByGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.StopGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.gate.WhereGate;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.AtSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.InheritedSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.LookingAtSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.NearbyPlayersSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.NearbySource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.OffsetSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.OriginSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.PlayerByNameSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.SelfSource;
import emaki.jiuwu.craft.corelib.action.builtin.v2.source.TriggerSource;
import emaki.jiuwu.craft.corelib.action.v2.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

/**
 * Registers every builtin pipeline stage into one {@link StageRegistry}.
 *
 * <p>The v2 counterpart of {@code BuiltinActions}. Registration failures are collected rather than ignored: a
 * duplicate id or an undeclared thread domain is a coding error, and reporting it lets CoreLib refuse to enable
 * instead of running with a stage table that silently lost an entry.</p>
 */
public final class BuiltinStages {

    /** Number of source stages this class registers. */
    public static final int SOURCE_COUNT = 10;

    /** Number of gate stages this class registers, including the two timing stages. */
    public static final int GATE_COUNT = 10;

    /** Number of action stages this class registers. */
    public static final int ACTION_COUNT = 43;

    private BuiltinStages() {
    }

    /**
     * Registers every builtin stage.
     *
     * @param registry the target registry
     * @param owner the owning plugin, recorded so a reload can revoke exactly these stages
     * @param executionDispatcher scheduler bridge for the stages that drive their own tasks
     * @param economyManager economy access for the money stages
     * @param itemSourceService item construction for the item and block stages
     * @param craftEngineBlockBridge CraftEngine block bridge, may be {@code null}
     * @param itemsAdderBlockBridge ItemsAdder block bridge, may be {@code null}
     * @param nexoBlockBridge Nexo block bridge, may be {@code null}
     * @param oraxenBlockBridge Oraxen block bridge, may be {@code null}
     * @return the registration report
     */
    public static @NotNull Report registerAll(@NotNull StageRegistry registry,
            @Nullable Plugin owner,
            @Nullable ExecutionDispatcher executionDispatcher,
            @Nullable EconomyManager economyManager,
            @Nullable ItemSourceService itemSourceService,
            @Nullable CraftEngineBlockBridge craftEngineBlockBridge,
            @Nullable CustomBlockBridge itemsAdderBlockBridge,
            @Nullable CustomBlockBridge nexoBlockBridge,
            @Nullable CustomBlockBridge oraxenBlockBridge) {
        java.util.Objects.requireNonNull(registry, "registry");
        List<String> failures = new ArrayList<>();
        registerSources(registry, owner, failures);
        registerGates(registry, owner, itemSourceService, failures);
        registerActions(registry, owner, executionDispatcher, economyManager, itemSourceService,
                craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, oraxenBlockBridge, failures);
        return new Report(List.copyOf(failures));
    }

    private static void registerSources(StageRegistry registry, Plugin owner, List<String> failures) {
        for (CoreActionSource source : List.of(
                new SelfSource(),
                new InheritedSource(),
                new TriggerSource(),
                new OriginSource(),
                new LookingAtSource(),
                new NearbySource(),
                new NearbyPlayersSource(),
                new OffsetSource(),
                new AtSource(),
                new PlayerByNameSource())) {
            record(failures, source.id(), registry.registerSource(owner, source));
        }
    }

    private static void registerGates(StageRegistry registry,
            Plugin owner,
            ItemSourceService itemSourceService,
            List<String> failures) {
        for (CoreActionGate gate : List.of(
                new WhereGate(),
                new ChanceGate(),
                new LimitGate(),
                new SortByGate(),
                new SetGate(),
                new KeepGate(),
                new StopGate(),
                new CreateItemGate(itemSourceService),
                new AfterGate(),
                new EveryGate())) {
            record(failures, gate.id(), registry.registerGate(owner, gate));
        }
    }

    private static void registerActions(StageRegistry registry,
            Plugin owner,
            ExecutionDispatcher executionDispatcher,
            EconomyManager economyManager,
            ItemSourceService itemSourceService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge,
            CustomBlockBridge oraxenBlockBridge,
            List<String> failures) {
        for (CoreActionStage stage : actions(owner, executionDispatcher, economyManager, itemSourceService,
                craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, oraxenBlockBridge)) {
            record(failures, stage.id(), registry.registerAction(owner, stage));
        }
    }

    private static List<CoreActionStage> actions(Plugin owner,
            ExecutionDispatcher executionDispatcher,
            EconomyManager economyManager,
            ItemSourceService itemSourceService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge,
            CustomBlockBridge oraxenBlockBridge) {
        List<CoreActionStage> stages = new ArrayList<>(ACTION_COUNT);
        // Messaging and feedback.
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SendMessageStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SendActionBarStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SendTitleStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.BroadcastMessageStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.PlaySoundStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SpawnParticleStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.BossBarShowStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.BossBarHideStage());
        // Health and status.
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.HealStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.DamageStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SetHealthStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.FeedStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.IgniteStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.ExtinguishStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.KillEntityStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.ProjectileStage(
                executionDispatcher, owner));
        // Potion effects.
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.GivePotionEffectStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.RemovePotionEffectStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.ClearPotionEffectsStage());
        // Items.
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SendItemStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.GiveItemStage(itemSourceService));
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SetItemStage(itemSourceService));
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.ClearItemStage(itemSourceService));
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.TakeItemStage(itemSourceService));
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.DropItemStage(itemSourceService));
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.RepairItemStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.DamageItemStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.PlaceBlockStage(itemSourceService,
                craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, oraxenBlockBridge));
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SetBlockStage());
        // Blocks and world.
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.BreakBlockStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.ExplosionStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SpawnEntityStage());
        // Teleport, economy and experience.
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.TeleportStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.GiveMoneyStage(economyManager));
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.TakeMoneyStage(economyManager));
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SetMoneyStage(economyManager));
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.GiveExpStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.TakeExpStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.SetExpStage());
        // Commands and integration.
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.RunCommandAsPlayerStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.RunCommandAsOpStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.RunCommandAsConsoleStage());
        stages.add(new emaki.jiuwu.craft.corelib.action.builtin.v2.stage.CastMythicSkillStage());
        return List.copyOf(stages);
    }

    /**
     * Releases the state builtin stages hold outside the registry.
     *
     * <p>Only the boss bar stages keep anything: a bar attached to a player's connection is not removed by
     * revoking the stage that created it, so it has to be detached explicitly or it would outlive the plugin.</p>
     */
    public static void shutdown() {
        emaki.jiuwu.craft.corelib.action.builtin.v2.stage.BossBarStages.clearAll();
    }

    private static void record(List<String> failures, String id, CoreStageRegistration registration) {
        if (registration == null || !registration.successful()) {
            failures.add(id + ": " + (registration == null ? "no_registration" : registration.reasonKey()));
        }
    }

    /**
     * Outcome of one {@link #registerAll} call.
     *
     * @param failures one entry per stage that could not be registered, as {@code id: reasonKey}
     */
    public record Report(@NotNull List<String> failures) {

        public Report {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        /** {@return whether every builtin stage registered} */
        public boolean successful() {
            return failures.isEmpty();
        }
    }
}

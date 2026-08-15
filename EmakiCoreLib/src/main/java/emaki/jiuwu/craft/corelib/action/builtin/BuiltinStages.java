package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.builtin.gate.AfterGate;
import emaki.jiuwu.craft.corelib.action.builtin.gate.ChanceGate;
import emaki.jiuwu.craft.corelib.action.builtin.gate.CreateItemGate;
import emaki.jiuwu.craft.corelib.action.builtin.gate.EveryGate;
import emaki.jiuwu.craft.corelib.action.builtin.gate.KeepGate;
import emaki.jiuwu.craft.corelib.action.builtin.gate.LimitGate;
import emaki.jiuwu.craft.corelib.action.builtin.gate.SetGate;
import emaki.jiuwu.craft.corelib.action.builtin.gate.SortByGate;
import emaki.jiuwu.craft.corelib.action.builtin.gate.StopGate;
import emaki.jiuwu.craft.corelib.action.builtin.gate.WhereGate;
import emaki.jiuwu.craft.corelib.action.builtin.source.AtSource;
import emaki.jiuwu.craft.corelib.action.builtin.source.InheritedSource;
import emaki.jiuwu.craft.corelib.action.builtin.source.LookingAtSource;
import emaki.jiuwu.craft.corelib.action.builtin.source.NearbyPlayersSource;
import emaki.jiuwu.craft.corelib.action.builtin.source.NearbySource;
import emaki.jiuwu.craft.corelib.action.builtin.source.OffsetSource;
import emaki.jiuwu.craft.corelib.action.builtin.source.OriginSource;
import emaki.jiuwu.craft.corelib.action.builtin.source.PlayerByNameSource;
import emaki.jiuwu.craft.corelib.action.builtin.source.SelfSource;
import emaki.jiuwu.craft.corelib.action.builtin.source.TriggerSource;
import emaki.jiuwu.craft.corelib.action.builtin.stage.BossBarHideStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.BossBarShowStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.BossBarStages;
import emaki.jiuwu.craft.corelib.action.builtin.stage.BreakBlockStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.BroadcastMessageStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.ClearItemStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.ClearPotionEffectsStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.DamageItemStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.DamageStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.DropItemStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.ExplosionStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.ExtinguishStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.FeedStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.GiveExpStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.GiveItemStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.GiveMoneyStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.GivePotionEffectStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.HealStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.IgniteStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.JsComputeStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.JsEntityStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.JsLocationStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.KillEntityStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.PlaceBlockStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.PlaySoundStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.ProjectileStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.RemovePotionEffectStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.RepairItemStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.RunCommandAsConsoleStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.RunCommandAsOpStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.RunCommandAsPlayerStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SendActionBarStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SendItemStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SendMessageStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SendTitleStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SetBlockStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SetExpStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SetHealthStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SetItemStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SetMoneyStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SpawnEntityStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.SpawnParticleStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.StartTaskStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.StopTaskStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.TakeExpStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.TakeItemStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.TakeMoneyStage;
import emaki.jiuwu.craft.corelib.action.builtin.stage.TeleportStage;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineTaskService;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;

public final class BuiltinStages {

    public static final int SOURCE_COUNT = 10;

    public static final int GATE_COUNT = 10;

    public static final int ACTION_COUNT = 47;

    private BuiltinStages() {
    }

    public static @NotNull Report registerAll(@NotNull StageRegistry registry,
            @Nullable Plugin owner,
            @Nullable ExecutionDispatcher executionDispatcher,
            @Nullable EconomyManager economyManager,
            @Nullable ItemSourceService itemSourceService,
            @Nullable CraftEngineBlockBridge craftEngineBlockBridge,
            @Nullable CustomBlockBridge itemsAdderBlockBridge,
            @Nullable CustomBlockBridge nexoBlockBridge,
            @Nullable CustomBlockBridge oraxenBlockBridge,
            @Nullable PipelineTaskService taskService,
            @Nullable StartTaskStage.SequenceSource sequences) {
        Objects.requireNonNull(registry, "registry");
        List<String> failures = new ArrayList<>();
        registerSources(registry, owner, failures);
        registerGates(registry, owner, itemSourceService, failures);
        registerActions(registry, owner, executionDispatcher, economyManager, itemSourceService,
                craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, oraxenBlockBridge,
                taskService, sequences, failures);
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
            PipelineTaskService taskService,
            StartTaskStage.SequenceSource sequences,
            List<String> failures) {
        for (CoreActionStage stage : actions(owner, executionDispatcher, economyManager, itemSourceService,
                craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, oraxenBlockBridge,
                taskService, sequences)) {
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
            CustomBlockBridge oraxenBlockBridge,
            PipelineTaskService taskService,
            StartTaskStage.SequenceSource sequences) {
        List<CoreActionStage> stages = new ArrayList<>(ACTION_COUNT);

        stages.add(new SendMessageStage());
        stages.add(new SendActionBarStage());
        stages.add(new SendTitleStage());
        stages.add(new BroadcastMessageStage());
        stages.add(new PlaySoundStage());
        stages.add(new SpawnParticleStage());
        stages.add(new BossBarShowStage());
        stages.add(new BossBarHideStage());

        stages.add(new HealStage());
        stages.add(new DamageStage());
        stages.add(new SetHealthStage());
        stages.add(new FeedStage());
        stages.add(new IgniteStage());
        stages.add(new ExtinguishStage());
        stages.add(new KillEntityStage());
        stages.add(new ProjectileStage(
                executionDispatcher, owner));

        stages.add(new GivePotionEffectStage());
        stages.add(new RemovePotionEffectStage());
        stages.add(new ClearPotionEffectsStage());

        stages.add(new SendItemStage());
        stages.add(new GiveItemStage(itemSourceService));
        stages.add(new SetItemStage(itemSourceService));
        stages.add(new ClearItemStage(itemSourceService));
        stages.add(new TakeItemStage(itemSourceService));
        stages.add(new DropItemStage(itemSourceService));
        stages.add(new RepairItemStage());
        stages.add(new DamageItemStage());
        stages.add(new PlaceBlockStage(itemSourceService,
                craftEngineBlockBridge, itemsAdderBlockBridge, nexoBlockBridge, oraxenBlockBridge));
        stages.add(new SetBlockStage());

        stages.add(new BreakBlockStage());
        stages.add(new ExplosionStage());
        stages.add(new SpawnEntityStage());

        stages.add(new TeleportStage());
        stages.add(new GiveMoneyStage(economyManager));
        stages.add(new TakeMoneyStage(economyManager));
        stages.add(new SetMoneyStage(economyManager));
        stages.add(new GiveExpStage());
        stages.add(new TakeExpStage());
        stages.add(new SetExpStage());

        stages.add(new RunCommandAsPlayerStage());
        stages.add(new RunCommandAsOpStage());
        stages.add(new RunCommandAsConsoleStage());

        stages.add(new StartTaskStage(
                taskService, sequences));
        stages.add(new StopTaskStage(taskService));

        stages.add(new JsComputeStage());
        stages.add(new JsEntityStage());
        stages.add(new JsLocationStage());
        return List.copyOf(stages);
    }

    public static void shutdown() {
        BossBarStages.clearAll();
    }

    private static void record(List<String> failures, String id, CoreStageRegistration registration) {
        if (registration == null || !registration.successful()) {
            failures.add(id + ": " + (registration == null ? "no_registration" : registration.reasonKey()));
        }
    }

    public record Report(@NotNull List<String> failures) {

        public Report {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public boolean successful() {
            return failures.isEmpty();
        }
    }
}

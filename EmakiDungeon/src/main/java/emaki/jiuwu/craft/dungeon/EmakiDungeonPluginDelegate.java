package emaki.jiuwu.craft.dungeon;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.dungeon.action.ActionBridge;
import emaki.jiuwu.craft.dungeon.api.EmakiDungeonApi;
import emaki.jiuwu.craft.dungeon.apiimpl.ServiceBackedDungeonBridge;
import emaki.jiuwu.craft.dungeon.audit.AuditService;
import emaki.jiuwu.craft.dungeon.bridge.DungeonBetterHudBridge;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeGuiService;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeService;
import emaki.jiuwu.craft.dungeon.command.DungeonCommandAdapter;
import emaki.jiuwu.craft.dungeon.command.DungeonCommandRouter;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.drop.DropRouter;
import emaki.jiuwu.craft.dungeon.drop.RewardChestService;
import emaki.jiuwu.craft.dungeon.editor.EditorGui;
import emaki.jiuwu.craft.dungeon.editor.EditorService;
import emaki.jiuwu.craft.dungeon.editor.EditorStateStore;
import emaki.jiuwu.craft.dungeon.flow.FlowDefinition;
import emaki.jiuwu.craft.dungeon.flow.FlowLoader;
import emaki.jiuwu.craft.dungeon.flow.FlowRuntime;
import emaki.jiuwu.craft.dungeon.flow.RegionTracker;
import emaki.jiuwu.craft.dungeon.guard.BoundaryGuard;
import emaki.jiuwu.craft.dungeon.guard.CommandGuard;
import emaki.jiuwu.craft.dungeon.guard.TeleportGuard;
import emaki.jiuwu.craft.dungeon.hud.InstanceHudService;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.isolation.MessageIsolation;
import emaki.jiuwu.craft.dungeon.leaderboard.LeaderboardStore;
import emaki.jiuwu.craft.dungeon.limit.LimitService;
import emaki.jiuwu.craft.dungeon.match.MatchmakingService;
import emaki.jiuwu.craft.dungeon.listener.DungeonChatListener;
import emaki.jiuwu.craft.dungeon.listener.DungeonLifecycleListener;
import emaki.jiuwu.craft.dungeon.listener.FlowEventListeners;
import emaki.jiuwu.craft.dungeon.listener.PlayerStateListener;
import emaki.jiuwu.craft.dungeon.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.dungeon.orchestration.DestroyArbiter;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceCoordinator;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceSlotAllocator;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.party.PartiesStore;
import emaki.jiuwu.craft.dungeon.placeholder.DungeonPlaceholderExpansion;
import emaki.jiuwu.craft.dungeon.placeholder.DungeonRecordStore;
import emaki.jiuwu.craft.dungeon.recovery.RecoveryService;
import emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxGui;
import emaki.jiuwu.craft.dungeon.rewardbox.RewardBoxService;
import emaki.jiuwu.craft.dungeon.score.ScoreTracker;
import emaki.jiuwu.craft.dungeon.script.ScriptRuntimeManager;
import emaki.jiuwu.craft.dungeon.settlement.SettlementService;
import emaki.jiuwu.craft.dungeon.snapshot.PlayersDataStore;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotManager;
import emaki.jiuwu.craft.dungeon.snapshot.SnapshotsStore;
import emaki.jiuwu.craft.dungeon.state.PlayerStateService;
import emaki.jiuwu.craft.dungeon.storage.InstancesStore;
import emaki.jiuwu.craft.dungeon.tempitem.TempItemManager;
import emaki.jiuwu.craft.dungeon.ticket.TicketService;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;
import emaki.jiuwu.craft.dungeon.type.DungeonType;
import emaki.jiuwu.craft.dungeon.type.DungeonTypeRegistry;
import emaki.jiuwu.craft.dungeon.world.TemplateRepository;
import emaki.jiuwu.craft.dungeon.world.WorldCloner;
import emaki.jiuwu.craft.dungeon.world.WorldDisposer;
import emaki.jiuwu.craft.dungeon.world.WorldFolders;
import emaki.jiuwu.craft.dungeon.world.WorldNames;
import emaki.jiuwu.craft.dungeon.world.WorldPool;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

final class EmakiDungeonPluginDelegate {

    private final EmakiDungeonPlugin ownerRef;

    EmakiDungeonPluginDelegate(EmakiDungeonPlugin ownerRef) {
        this.ownerRef = ownerRef;
    }

    String describeShutdownFailure(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        return cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }

    YamlConfigLoader<AppConfig> appConfigLoader() {
        return ownerRef.appConfigLoader;
    }

    MessageService messageService() {
        return ownerRef.messageService;
    }

    LanguageLoader languageLoader() {
        return ownerRef.languageLoader;
    }

    DebugCommand debugCommand() {
        return ownerRef.debugCommand;
    }

    InstanceCoordinator instanceCoordinator() {
        return ownerRef.coordinator;
    }

    ChallengeService challengeService() {
        return ownerRef.challenge;
    }

    PartyService partyService() {
        return ownerRef.parties;
    }

    RecoveryService recoveryService() {
        return ownerRef.recovery;
    }

    boolean runtimeInitialized() {
        return ownerRef.runtimeInitialized;
    }

    void dispatchTempItemGrant(DungeonInstance instance, String itemId, int amount, TempItemManager tempItemManager) {
        EmakiCoreLibApi.requireService(ExecutionDispatcher.class).runGlobal(ownerRef, () -> {
            var world = Bukkit.getWorld(instance.instanceId());
            if (world == null) {
                return;
            }
            for (Player member : world.getPlayers()) {
                tempItemManager.grant(instance.instanceId(), member, itemId, amount);
            }
        });
    }

    File worldContainerOf() {
        return Bukkit.getWorldContainer();
    }

    static boolean foliaDetected() {
        try {
            Class.forName(EmakiDungeonPlugin.FOLIA_MARKER, false, EmakiDungeonPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

}

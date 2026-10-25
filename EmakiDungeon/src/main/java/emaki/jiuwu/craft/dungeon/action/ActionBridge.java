package emaki.jiuwu.craft.dungeon.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.builtin.BaseSource;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreActionTrigger;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerDispatch;
import emaki.jiuwu.craft.corelib.api.action.CoreTriggerRegistration;
import emaki.jiuwu.craft.corelib.api.action.execution.CoreActionExecutionContext;
import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.TriggerContract;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;

public final class ActionBridge {

    private static final String NAMESPACE = "emakidungeon:";
    private static final String WORLD_SOURCE_ID = "dungeon_world_players";
    private static final List<String> SEGMENTS = List.of(
            "dungeon_enter",
            "dungeon_exit",
            "dungeon_room_clear",
            "dungeon_gate_open",
            "dungeon_wave_start",
            "dungeon_score",
            "dungeon_complete",
            "dungeon_fail",
            "dungeon_reward",
            "dungeon_ending",
            "dungeon_destroy"
    );

    private final Plugin plugin;
    private final Supplier<AppConfig> appConfig;
    private final InstanceRegistry registry;
    private final MessageService messages;

    private final List<CoreStageRegistration> registrations = new ArrayList<>();
    private final List<CoreTriggerRegistration> triggerRegistrations =
            new ArrayList<>();

    public ActionBridge(Plugin plugin,
            Supplier<AppConfig> appConfig,
            InstanceRegistry registry,
            MessageService messages) {
        this.plugin = plugin;
        this.appConfig = appConfig;
        this.registry = registry;
        this.messages = messages;
    }

    public void registerAll() {
        closeAll();
        if (!EmakiCoreLibApi.status().usable()) {
            messages.warning("console.action_corelib_unavailable", Map.of());
            return;
        }
        for (String segment : SEGMENTS) {
            triggerRegistrations.add(EmakiCoreLibApi.registerActionTrigger(plugin, trigger(segment)));
        }
        registrations.add(EmakiCoreLibApi.registerActionSource(plugin, worldPlayersSource()));
        EmakiCoreLibApi.onStageRegistryRebuilt(plugin, this::registerAll);
    }

    public void closeAll() {
        for (CoreStageRegistration registration : registrations) {
            try {
                registration.close();
            } catch (RuntimeException ignored) {
            }
        }
        registrations.clear();
        for (var triggerRegistration : triggerRegistrations) {
            try {
                triggerRegistration.close();
            } catch (RuntimeException ignored) {
            }
        }
        triggerRegistrations.clear();
    }

    public void dispatch(String segment, UUID casterId, Map<String, String> variables) {
        List<String> lines = appConfig.get().actionPipelines().get(segment);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        if (!EmakiCoreLibApi.status().usable()) {
            return;
        }
        CoreTriggerDispatch dispatch = new CoreTriggerDispatch(
                "default", casterId, "", false, variables == null ? Map.of() : variables, lines);
        EmakiCoreLibApi.dispatchTriggerAsync(plugin, NAMESPACE + segment, dispatch)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        messages.warning("console.action_dispatch_failed", Map.of(
                                "segment", segment,
                                "error", String.valueOf(error.getMessage())
                        ));
                    }
                });
    }

    public void sendToWorld(String instanceId, String message) {
        if (instanceId == null || message == null || message.isBlank()) {
            return;
        }
        if (!EmakiCoreLibApi.status().usable()) {
            return;
        }
        String line = WORLD_SOURCE_ID + " | send_message text=\"%var.message%\"";
        CoreActionExecutionContext context = CoreActionExecutionContext.builder()
                .variables(Map.of("message", message, "instance", instanceId))
                .build();
        EmakiCoreLibApi.executeActionLineAsync(plugin, line, context)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        messages.warning("console.action_dispatch_failed", Map.of(
                                "segment", WORLD_SOURCE_ID,
                                "error", String.valueOf(error.getMessage())
                        ));
                    }
                });
    }

    private CoreActionTrigger trigger(String segment) {
        String triggerId = NAMESPACE + segment;
        return new CoreActionTrigger() {
            @Override
            public String id() {
                return triggerId;
            }

            @Override
            public TriggerContract contract() {
                return TriggerContract.permissive(triggerId);
            }

            @Override
            public String description() {
                return "EmakiDungeon lifecycle segment: " + segment;
            }
        };
    }

    private CoreActionSourceImpl worldPlayersSource() {
        return new CoreActionSourceImpl();
    }

    private final class CoreActionSourceImpl extends BaseSource {

        private CoreActionSourceImpl() {
            super(WORLD_SOURCE_ID,
                    "当前副本世界的全部在线成员",
                    CoreActionExecutionDomain.SERVER_GLOBAL);
        }

        @Override
        public CoreSourceResult select(CoreStageContext context, CoreResolvedArguments arguments) {
            String instanceId = context.variable("instance").orElse("");
            if (instanceId.isEmpty()) {
                var casterEntity = context.caster().entityOrNull();
                if (casterEntity instanceof Player player
                        && registry.get(player.getWorld().getName()) != null) {
                    instanceId = player.getWorld().getName();
                }
            }
            if (instanceId.isEmpty()) {
                return CoreSourceResult.empty("action.source.dungeon_world_players.empty");
            }
            World world = Bukkit.getWorld(instanceId);
            if (world == null) {
                return CoreSourceResult.empty("action.source.dungeon_world_players.empty");
            }
            List<CoreActionSubject> subjects = new ArrayList<>();
            for (Player player : world.getPlayers()) {
                subjects.add(new CoreActionSubject.OfEntity(player));
            }
            if (subjects.isEmpty()) {
                return CoreSourceResult.empty("action.source.dungeon_world_players.empty");
            }
            return CoreSourceResult.selected(subjects);
        }
    }
}

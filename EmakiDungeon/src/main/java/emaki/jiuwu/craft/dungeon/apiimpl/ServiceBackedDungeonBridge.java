package emaki.jiuwu.craft.dungeon.apiimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.contract.ApiStatus;
import emaki.jiuwu.craft.dungeon.api.ChallengeId;
import emaki.jiuwu.craft.dungeon.api.DungeonApi;
import emaki.jiuwu.craft.dungeon.api.EditorApi;
import emaki.jiuwu.craft.dungeon.api.EditorSession;
import emaki.jiuwu.craft.dungeon.api.ErrorCodes;
import emaki.jiuwu.craft.dungeon.api.EmakiDungeonApi;
import emaki.jiuwu.craft.dungeon.api.InstanceApi;
import emaki.jiuwu.craft.dungeon.api.InstanceId;
import emaki.jiuwu.craft.dungeon.api.InstanceView;
import emaki.jiuwu.craft.dungeon.api.InviteToken;
import emaki.jiuwu.craft.dungeon.api.PartyApi;
import emaki.jiuwu.craft.dungeon.api.PartyId;
import emaki.jiuwu.craft.dungeon.api.Result;
import emaki.jiuwu.craft.dungeon.api.ScriptApi;
import emaki.jiuwu.craft.dungeon.api.ScriptId;
import emaki.jiuwu.craft.dungeon.api.ScriptTriggerHandler;
import emaki.jiuwu.craft.dungeon.challenge.ChallengeService;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.InstanceState;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceCoordinator;
import emaki.jiuwu.craft.dungeon.orchestration.InstanceRegistry;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.script.ScriptRuntimeManager;
import java.util.function.Function;

public final class ServiceBackedDungeonBridge implements EmakiDungeonApi.Bridge {

    private final ApiStatus status;
    private final ChallengeService challenge;
    private final PartyService parties;
    private final InstanceRegistry registry;
    private final InstanceCoordinator coordinator;
    private final Function<String, DungeonConfig> configOf;
    private final ScriptRuntimeManager scriptManager;

    public ServiceBackedDungeonBridge(ApiStatus status,
            ChallengeService challenge,
            PartyService parties,
            InstanceRegistry registry,
            InstanceCoordinator coordinator,
            Function<String, DungeonConfig> configOf,
            ScriptRuntimeManager scriptManager) {
        this.status = status;
        this.challenge = challenge;
        this.parties = parties;
        this.registry = registry;
        this.coordinator = coordinator;
        this.configOf = configOf;
        this.scriptManager = scriptManager;
    }

    @Override
    public @NotNull ApiStatus status() {
        return status;
    }

    @Override
    public @NotNull DungeonApi dungeonApi() {
        return new DungeonApi() {
            @Override
            public @NotNull Result<Object> enter(@NotNull Player player, @NotNull String dungeonId) {
                return challenge.apiEnter(player, dungeonId);
            }

            @Override
            public @NotNull List<InstanceView> listInstances() {
                List<InstanceView> views = new ArrayList<>();
                for (DungeonInstance instance : registry.all()) {
                    views.add(viewOf(instance));
                }
                return views;
            }

            @Override
            public @NotNull Optional<InstanceView> getInstance(@NotNull UUID instanceId) {
                DungeonInstance instance = registry.get(instanceId.toString());
                return instance == null ? Optional.empty() : Optional.of(viewOf(instance));
            }
        };
    }

    @Override
    public @NotNull PartyApi partyApi() {
        return new PartyApi() {
            @Override
            public @NotNull Result<PartyId> create(@NotNull UUID player) {
                Player online = Bukkit.getPlayer(player);
                if (online == null) {
                    return Result.err(ErrorCodes.TARGET_OFFLINE, "api.target_offline");
                }
                PartyService.InviteResult result = parties.create(online);
                if (result != PartyService.InviteResult.OK) {
                    return Result.err(mapInvite(result), "api.party_failed",
                            Map.of("code", result.name()));
                }
                Party party = parties.partyOf(player);
                return Result.ok(new PartyId(party == null ? "" : String.valueOf(party.id())));
            }

            @Override
            public @NotNull Result<InviteToken> invite(@NotNull UUID player, @NotNull UUID target) {
                Player actor = Bukkit.getPlayer(player);
                if (actor == null) {
                    return Result.err(ErrorCodes.TARGET_OFFLINE, "api.target_offline");
                }
                if (Bukkit.getPlayer(target) == null) {
                    return Result.err(ErrorCodes.TARGET_OFFLINE, "api.target_offline");
                }
                PartyService.InviteResult result = parties.invite(actor, target);
                if (result != PartyService.InviteResult.OK) {
                    return Result.err(mapInvite(result), "api.party_failed",
                            Map.of("code", result.name()));
                }
                return Result.ok(new InviteToken(target.toString()));
            }

            @Override
            public @NotNull Result<Void> kick(@NotNull UUID member) {
                Party party = parties.partyOf(member);
                if (party == null) {
                    return Result.err(ErrorCodes.MEMBER_NOT_FOUND, "api.member_not_found");
                }
                Player leader = Bukkit.getPlayer(party.leaderId());
                if (leader == null) {
                    return Result.err(ErrorCodes.NOT_LEADER, "api.not_leader");
                }
                PartyService.InviteResult result = parties.kick(leader, member);
                if (result != PartyService.InviteResult.OK) {
                    return Result.err(mapInvite(result), "api.party_failed",
                            Map.of("code", result.name()));
                }
                return Result.ok(null);
            }

            @Override
            public @NotNull Result<Void> confirmReady(@NotNull UUID player) {
                Player online = Bukkit.getPlayer(player);
                if (online == null) {
                    return Result.err(ErrorCodes.TARGET_OFFLINE, "api.target_offline");
                }
                Party party = parties.partyOf(player);
                if (party == null || !challenge.hasActiveSession(party.id())) {
                    return Result.err(ErrorCodes.CHALLENGE_NOT_ACTIVE, "api.challenge_not_active");
                }
                challenge.readyBy(online);
                return Result.ok(null);
            }
        };
    }

    @Override
    public @NotNull InstanceApi instanceApi() {
        return instanceId -> {
            DungeonInstance instance = registry.get(instanceId.toString());
            if (instance == null) {
                return Result.err(ErrorCodes.INSTANCE_NOT_FOUND, "api.instance_not_found");
            }
            InstanceState state = instance.state();
            if (state == InstanceState.DISPOSING || state == InstanceState.DISPOSED) {
                return Result.err(ErrorCodes.ALREADY_DISPOSING, "api.already_disposing");
            }
            coordinator.dispose(instance.instanceId(), "api_force_stop");
            return Result.ok(null);
        };
    }

    @Override
    public @NotNull ScriptApi scriptApi() {
        return new ScriptApi() {
            @Override
            public @NotNull Result<ScriptId> loadFlowScript(@NotNull String dungeonId,
                    @NotNull String source) {
                if (scriptManager == null) {
                    return Result.err(ErrorCodes.ENGINE_UNAVAILABLE, "api.engine_unavailable");
                }
                if (configOf.apply(dungeonId) == null) {
                    return Result.err(ErrorCodes.DUNGEON_NOT_FOUND, "api.dungeon_not_found",
                            Map.of("dungeon", dungeonId));
                }
                return scriptManager.loadFlowScript(dungeonId, source);
            }

            @Override
            public @NotNull Result<Void> registerTrigger(@NotNull String triggerId,
                    @NotNull String event, @NotNull ScriptTriggerHandler handler) {
                if (scriptManager == null) {
                    return Result.err(ErrorCodes.ENGINE_UNAVAILABLE, "api.engine_unavailable");
                }
                return scriptManager.registerTrigger(triggerId, event, handler);
            }
        };
    }

    @Override
    public @NotNull EditorApi editorApi() {
        return (player, dungeonId) -> {
            if (configOf.apply(dungeonId) == null) {
                return Result.err(ErrorCodes.DUNGEON_NOT_FOUND, "api.dungeon_not_found",
                        Map.of("dungeon", dungeonId));
            }
            return Result.err(ErrorCodes.ENGINE_UNAVAILABLE, "api.engine_unavailable");
        };
    }

    private InstanceView viewOf(DungeonInstance instance) {
        int members = 0;
        if (instance.partyId() != null) {
            Party party = parties.partyById(instance.partyId());
            members = party == null ? 0 : party.size();
        }
        return new InstanceView(instance.instanceId(), instance.dungeonId(),
                instance.state().name(), members, instance.instanceId());
    }

    private static String mapInvite(PartyService.InviteResult result) {
        return switch (result) {
            case ALREADY_IN_PARTY -> ErrorCodes.PARTY_ALREADY_EXISTS;
            case PARTY_FULL -> ErrorCodes.PARTY_FULL;
            case NOT_LEADER -> ErrorCodes.NOT_LEADER;
            case INVALID_TARGET -> ErrorCodes.TARGET_OFFLINE;
            default -> ErrorCodes.DENY_PARTY_INVALID;
        };
    }
}

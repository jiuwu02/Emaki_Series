package emaki.jiuwu.craft.dungeon.party;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.script.ScriptRuntimeManager;
import emaki.jiuwu.craft.dungeon.trigger.TriggerType;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public final class PartyService {

    public enum InviteMode {
        PUBLIC,
        APPLY,
        INVITE_ONLY;

        public static InviteMode parse(String raw) {
            if (raw == null) {
                return INVITE_ONLY;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if ("public".equals(normalized)) {
                return PUBLIC;
            }
            if ("apply".equals(normalized)) {
                return APPLY;
            }
            return INVITE_ONLY;
        }
    }

    public enum InviteResult {
        OK,
        ALREADY_IN_PARTY,
        TARGET_IN_PARTY,
        TARGET_BUSY,
        PARTY_FULL,
        NOT_LEADER,
        NOT_IN_PARTY,
        MODE_DENIED,
        INVALID_TARGET,
        BUSY_PARTY
    }

    private static final long PENDING_TTL_MILLIS = 60_000L;
    private static final long OFFLINE_DISSOLVE_MILLIS = 10 * 60_000L;
    private static final long CLEANUP_PERIOD_TICKS = 100L;

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final MessageService messages;
    private final DebugLogger debug;
    private final PartiesStore store;
    private final int maxSize;
    private volatile InviteMode inviteMode;
    private final Predicate<UUID> partyBusyProbe;

    private final Map<UUID, Party> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Party> byId = new ConcurrentHashMap<>();
    private final Map<UUID, PendingEntry> pendingByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastQuitAt = new ConcurrentHashMap<>();
    private final Set<UUID> chatChannel = ConcurrentHashMap.newKeySet();

    private TaskToken cleanupTask = TaskToken.UNAVAILABLE;
    private volatile ScriptRuntimeManager scriptManager;

    public void bindScriptManager(ScriptRuntimeManager scriptManager) {
        this.scriptManager = scriptManager;
    }

    private void fireTrigger(TriggerType type,
            Map<String, String> context) {
        if (scriptManager != null) {
            scriptManager.fireTrigger(type, null, null, context);
        }
    }

    public PartyService(Plugin plugin,
            ExecutionDispatcher dispatcher,
            MessageService messages,
            DebugLogger debug,
            PartiesStore store,
            int maxSize,
            String inviteMode,
            Predicate<UUID> partyBusyProbe) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.debug = debug;
        this.store = store;
        this.maxSize = Math.max(1, maxSize);
        this.inviteMode = InviteMode.parse(inviteMode);
        this.partyBusyProbe = partyBusyProbe;
    }

    public void start() {
        try {
            store.load();
        } catch (RuntimeException exception) {
            messages.warning("console.parties_load_failed", Map.of(
                    "error", String.valueOf(exception.getMessage())
            ));
        }
        for (Party party : store.parties().values()) {
            byId.put(party.id(), party);
            for (UUID memberId : party.members()) {
                byPlayer.put(memberId, party);
            }
        }
        cleanupTask = dispatcher.runGlobalTimer(plugin, this::cleanupOnce, CLEANUP_PERIOD_TICKS, CLEANUP_PERIOD_TICKS);
    }

    public boolean shutdown() {
        cleanupTask.cancel();
        return store.saveSync();
    }

    public Party partyOf(UUID playerId) {
        return playerId == null ? null : byPlayer.get(playerId);
    }

    public Party partyById(UUID partyId) {
        return partyId == null ? null : byId.get(partyId);
    }

    public InviteResult create(Player player) {
        UUID playerId = player.getUniqueId();
        if (byPlayer.containsKey(playerId)) {
            return InviteResult.ALREADY_IN_PARTY;
        }
        if (partyBusyProbe.test(playerId)) {
            return InviteResult.BUSY_PARTY;
        }
        Party party = new Party(UUID.randomUUID(), playerId);
        byId.put(party.id(), party);
        byPlayer.put(playerId, party);
        persist(party);
        debug.log("dungeon", player, "party_created", Map.of("party", String.valueOf(party.id())));
        return InviteResult.OK;
    }

    public InviteResult invite(Player actor, UUID targetId) {
        UUID actorId = actor.getUniqueId();
        if (actorId.equals(targetId)) {
            return InviteResult.INVALID_TARGET;
        }
        Player targetPlayer = Bukkit.getPlayer(targetId);
        if (targetPlayer == null) {
            return InviteResult.INVALID_TARGET;
        }
        Party targetParty = byPlayer.get(targetId);
        Party actorParty = byPlayer.get(actorId);
        if (targetParty != null) {
            if (actorParty != null) {
                return InviteResult.TARGET_IN_PARTY;
            }
            return inviteAsApplicant(actor, targetId, targetParty);
        }
        if (actorParty == null) {
            return InviteResult.NOT_IN_PARTY;
        }
        if (partyBusyProbe.test(actorParty.id())) {
            return InviteResult.BUSY_PARTY;
        }
        if (actorParty.size() >= maxSize) {
            return InviteResult.PARTY_FULL;
        }
        pendingByTarget.put(targetId, new PendingEntry(
                PendingEntry.Kind.INVITE, actorId, actorParty.id(),
                System.currentTimeMillis() + PENDING_TTL_MILLIS));
        debug.log("dungeon", actor, "party_invite_sent", Map.of(
                "party", String.valueOf(actorParty.id()),
                "target", targetId.toString()
        ));
        fireTrigger(TriggerType.onPartyInvite, Map.of(
                "party", String.valueOf(actorParty.id()),
                "target", targetId.toString(),
                "player", actorId.toString()
        ));
        return InviteResult.OK;
    }

    private InviteResult inviteAsApplicant(Player actor, UUID targetId, Party targetParty) {
        switch (inviteMode) {
            case PUBLIC -> {
                return joinDirectly(actor, targetParty);
            }
            case APPLY -> {
                if (targetParty.size() >= maxSize) {
                    return InviteResult.PARTY_FULL;
                }
                if (partyBusyProbe.test(targetParty.id())) {
                    return InviteResult.BUSY_PARTY;
                }
                pendingByTarget.put(targetParty.leaderId(), new PendingEntry(
                        PendingEntry.Kind.APPLY, actor.getUniqueId(), targetParty.id(),
                        System.currentTimeMillis() + PENDING_TTL_MILLIS));
                Player leader = Bukkit.getPlayer(targetParty.leaderId());
                if (leader != null) {
                    messages.send(leader, "party.apply_received", Map.of("player", actor.getName()));
                }
                debug.log("dungeon", actor, "party_apply_sent", Map.of(
                        "party", String.valueOf(targetParty.id()),
                        "target", targetId.toString()
                ));
                return InviteResult.OK;
            }
            default -> {
                return InviteResult.MODE_DENIED;
            }
        }
    }

    private InviteResult joinDirectly(Player actor, Party party) {
        if (party.size() >= maxSize) {
            return InviteResult.PARTY_FULL;
        }
        if (partyBusyProbe.test(party.id())) {
            return InviteResult.BUSY_PARTY;
        }
        if (!party.add(actor.getUniqueId())) {
            return InviteResult.ALREADY_IN_PARTY;
        }
        byPlayer.put(actor.getUniqueId(), party);
        persist(party);
        notifyJoin(actor, party);
        return InviteResult.OK;
    }

    public InviteResult accept(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        PendingEntry entry = pendingByTarget.get(playerId);
        if (entry != null && entry.expired(now)) {
            pendingByTarget.remove(playerId);
            entry = null;
        }
        if (entry != null && entry.kind() == PendingEntry.Kind.INVITE) {
            if (byPlayer.containsKey(playerId)) {
                pendingByTarget.remove(playerId);
                return InviteResult.ALREADY_IN_PARTY;
            }
            Party party = byId.get(entry.partyId());
            pendingByTarget.remove(playerId);
            if (party == null) {
                return InviteResult.INVALID_TARGET;
            }
            if (party.size() >= maxSize) {
                return InviteResult.PARTY_FULL;
            }
            if (partyBusyProbe.test(party.id())) {
                return InviteResult.BUSY_PARTY;
            }
            party.add(playerId);
            byPlayer.put(playerId, party);
            persist(party);
            notifyJoin(player, party);
            return InviteResult.OK;
        }
        PendingEntry apply = pendingByTarget.get(playerId);
        if (apply != null && apply.kind() == PendingEntry.Kind.APPLY) {
            Party party = byId.get(apply.partyId());
            if (party == null || !party.isLeader(playerId)) {
                return InviteResult.NOT_LEADER;
            }
            pendingByTarget.remove(playerId);
            if (party.size() >= maxSize) {
                return InviteResult.PARTY_FULL;
            }
            if (partyBusyProbe.test(party.id())) {
                return InviteResult.BUSY_PARTY;
            }
            Player applicant = Bukkit.getPlayer(apply.fromId());
            if (applicant == null) {
                return InviteResult.INVALID_TARGET;
            }
            party.add(apply.fromId());
            byPlayer.put(apply.fromId(), party);
            persist(party);
            notifyJoin(applicant, party);
            return InviteResult.OK;
        }
        return InviteResult.NOT_IN_PARTY;
    }

    public InviteResult deny(Player player) {
        UUID playerId = player.getUniqueId();
        PendingEntry entry = pendingByTarget.get(playerId);
        if (entry == null) {
            return InviteResult.NOT_IN_PARTY;
        }
        Party party = byId.get(entry.partyId());
        if (entry.kind() == PendingEntry.Kind.APPLY) {
            if (party == null || !party.isLeader(playerId)) {
                return InviteResult.NOT_LEADER;
            }
            Player applicant = Bukkit.getPlayer(entry.fromId());
            pendingByTarget.remove(playerId);
            if (applicant != null) {
                messages.send(applicant, "party.apply_denied", Map.of("player", player.getName()));
            }
            return InviteResult.OK;
        }
        pendingByTarget.remove(playerId);
        return InviteResult.OK;
    }

    public InviteResult kick(Player leader, UUID targetId) {
        Party party = byPlayer.get(leader.getUniqueId());
        if (party == null) {
            return InviteResult.NOT_IN_PARTY;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            return InviteResult.NOT_LEADER;
        }
        if (leader.getUniqueId().equals(targetId)) {
            return InviteResult.INVALID_TARGET;
        }
        if (!party.remove(targetId)) {
            return InviteResult.INVALID_TARGET;
        }
        byPlayer.remove(targetId);
        chatChannel.remove(targetId);
        persist(party);
        Player kicked = Bukkit.getPlayer(targetId);
        if (kicked != null) {
            messages.send(kicked, "party.kicked", Map.of("player", leader.getName()));
        }
        debug.log("dungeon", leader, "party_leave", Map.of(
                "party", String.valueOf(party.id()),
                "player", targetId.toString()
        ));
        fireTrigger(TriggerType.onPartyLeave, Map.of(
                "party", String.valueOf(party.id()),
                "player", targetId.toString()
        ));
        dissolveIfEmpty(party);
        return InviteResult.OK;
    }

    public InviteResult leave(Player player) {
        UUID playerId = player.getUniqueId();
        Party party = byPlayer.get(playerId);
        if (party == null) {
            return InviteResult.NOT_IN_PARTY;
        }
        boolean wasLeader = party.isLeader(playerId);
        party.remove(playerId);
        byPlayer.remove(playerId);
        chatChannel.remove(playerId);
        pendingByTarget.remove(playerId);
        persist(party);
        debug.log("dungeon", player, "party_leave", Map.of(
                "party", String.valueOf(party.id()),
                "player", playerId.toString()
        ));
        fireTrigger(TriggerType.onPartyLeave, Map.of(
                "party", String.valueOf(party.id()),
                "player", playerId.toString()
        ));
        if (party.isEmpty()) {
            dissolve(party);
            return InviteResult.OK;
        }
        if (wasLeader) {
            UUID newLeader = party.transferLeadership(playerId);
            if (newLeader != null) {
                Player newLeaderPlayer = Bukkit.getPlayer(newLeader);
                if (newLeaderPlayer != null) {
                    messages.send(newLeaderPlayer, "party.leader_transferred", Map.of("player", player.getName()));
                }
            }
        }
        return InviteResult.OK;
    }

    public InviteResult disband(Player leader) {
        Party party = byPlayer.get(leader.getUniqueId());
        if (party == null) {
            return InviteResult.NOT_IN_PARTY;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            return InviteResult.NOT_LEADER;
        }
        dissolve(party);
        return InviteResult.OK;
    }

    public boolean toggleChatChannel(Player player) {
        UUID playerId = player.getUniqueId();
        if (byPlayer.get(playerId) == null) {
            return false;
        }
        if (chatChannel.remove(playerId)) {
            return false;
        }
        chatChannel.add(playerId);
        return true;
    }

    public boolean inChatChannel(UUID playerId) {
        return playerId != null && chatChannel.contains(playerId);
    }

    public void handleQuit(Player player) {
        lastQuitAt.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void handleJoin(Player player) {
        lastQuitAt.remove(player.getUniqueId());
    }

    private void cleanupOnce() {
        long now = System.currentTimeMillis();
        pendingByTarget.entrySet().removeIf(entry -> entry.getValue().expired(now));
        for (Party party : List.copyOf(byId.values())) {
            if (partyBusyProbe.test(party.id())) {
                continue;
            }
            boolean anyOnline = false;
            boolean recentlyQuit = false;
            for (UUID memberId : party.members()) {
                if (Bukkit.getPlayer(memberId) != null) {
                    anyOnline = true;
                    break;
                }
                Long quitAt = lastQuitAt.get(memberId);
                if (quitAt == null) {
                    lastQuitAt.put(memberId, now);
                } else if (now - quitAt < OFFLINE_DISSOLVE_MILLIS) {
                    recentlyQuit = true;
                }
                if (recentlyQuit) {
                    break;
                }
            }
            if (!anyOnline && !recentlyQuit) {
                dissolve(party);
            }
        }
    }

    private void dissolveIfEmpty(Party party) {
        if (party.isEmpty()) {
            dissolve(party);
        }
    }

    private void dissolve(Party party) {
        for (UUID memberId : party.members()) {
            byPlayer.remove(memberId);
            chatChannel.remove(memberId);
            pendingByTarget.values().removeIf(entry -> entry.partyId().equals(party.id()));
        }
        byId.remove(party.id());
        store.parties().remove(party.id());
        store.saveAsync();
        debug.logRaw("dungeon", (UUID) null, "party dissolved | party=" + party.id());
    }

    private void notifyJoin(Player player, Party party) {
        debug.log("dungeon", player, "party_join", Map.of(
                "party", String.valueOf(party.id()),
                "player", player.getUniqueId().toString()
        ));
        fireTrigger(TriggerType.onPartyJoin, Map.of(
                "party", String.valueOf(party.id()),
                "player", player.getUniqueId().toString()
        ));
        for (UUID memberId : party.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                messages.send(member, "party.member_joined", Map.of("player", player.getName()));
            }
        }
    }

    private void persist(Party party) {
        store.parties().put(party.id(), party);
        store.saveAsync();
    }
}

package emaki.jiuwu.craft.dungeon.match;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.config.AppConfig;
import emaki.jiuwu.craft.dungeon.party.Party;
import emaki.jiuwu.craft.dungeon.party.PartyService;
import emaki.jiuwu.craft.dungeon.world.TemplateRepository;

public final class MatchmakingService {

    private static final long TICK_PERIOD = 100L;
    private static final String DEFAULT_DIFFICULTY = "normal";

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final MessageService messages;
    private final PartyService parties;
    private final TemplateRepository templates;
    private final Supplier<AppConfig> config;
    private final Map<Integer, RecruitEntry> recruits = new ConcurrentHashMap<>();
    private final Map<String, QueueBucket> queues = new ConcurrentHashMap<>();
    private final Map<UUID, String> queueKeys = new ConcurrentHashMap<>();
    private int nextRecruitId = 1;
    private TaskToken tickTask = TaskToken.UNAVAILABLE;

    public MatchmakingService(Plugin plugin, ExecutionDispatcher dispatcher,
            MessageService messages, PartyService parties,
            TemplateRepository templates, Supplier<AppConfig> config) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.messages = messages;
        this.parties = parties;
        this.templates = templates;
        this.config = config;
    }

    public void start() {
        tickTask = dispatcher.runGlobalTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        recruits.clear();
        queues.clear();
        queueKeys.clear();
    }

    public void openRecruit(Player leader, String dungeonId, String difficulty) {
        if (!enabled()) {
            messages.send(leader, "match.disabled", Map.of());
            return;
        }
        if (!templates.discoverDungeons().contains(dungeonId)) {
            messages.send(leader, "match.dungeon_missing", Map.of("dungeon", dungeonId));
            return;
        }
        Party party = parties.partyOf(leader.getUniqueId());
        if (party != null && !party.isLeader(leader.getUniqueId())) {
            messages.send(leader, "match.not_leader", Map.of());
            return;
        }
        if (party == null) {
            PartyService.InviteResult created = parties.create(leader);
            if (created != PartyService.InviteResult.OK) {
                messages.send(leader, "match.party_error", Map.of("code", created.name()));
                return;
            }
            party = parties.partyOf(leader.getUniqueId());
        }
        if (party == null) {
            messages.send(leader, "match.party_error", Map.of("code", "PARTY_MISSING"));
            return;
        }
        removeRecruitOf(leader.getUniqueId());
        int id = nextRecruitId++;
        long ttl = ttlMillis();
        long expiresAt = ttl <= 0L ? Long.MAX_VALUE : System.currentTimeMillis() + ttl;
        String resolved = difficulty == null || difficulty.isBlank()
                ? DEFAULT_DIFFICULTY : difficulty.trim();
        recruits.put(id, new RecruitEntry(id, leader.getUniqueId(), party.id(),
                dungeonId, resolved, expiresAt));
        messages.send(leader, "match.recruit_opened", Map.of(
                "id", String.valueOf(id), "dungeon", dungeonId,
                "difficulty", resolved, "size", String.valueOf(party.size()),
                "target", String.valueOf(targetSize())));
    }

    public void closeRecruit(Player leader) {
        if (removeRecruitOf(leader.getUniqueId())) {
            messages.send(leader, "match.recruit_closed", Map.of());
        } else {
            messages.send(leader, "match.recruit_none", Map.of());
        }
    }

    public void listRecruits(CommandSender sender) {
        List<RecruitEntry> entries = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (RecruitEntry entry : recruits.values()) {
            if (entry.expiresAt() > now) {
                entries.add(entry);
            }
        }
        entries.sort((a, b) -> Integer.compare(a.id(), b.id()));
        if (entries.isEmpty()) {
            messages.send(sender, "match.recruit_empty", Map.of());
            return;
        }
        messages.send(sender, "match.recruit_header", Map.of("count", String.valueOf(entries.size())));
        for (RecruitEntry entry : entries) {
            Player leader = Bukkit.getPlayer(entry.leaderId());
            String name = leader == null ? "-" : leader.getName();
            Party party = parties.partyById(entry.partyId());
            int size = party == null ? 0 : party.size();
            messages.send(sender, "match.recruit_entry", Map.of(
                    "id", String.valueOf(entry.id()),
                    "leader", name,
                    "dungeon", entry.dungeonId(),
                    "difficulty", entry.difficulty(),
                    "size", String.valueOf(size),
                    "target", String.valueOf(targetSize())));
        }
    }

    public void joinRecruit(Player player, int id) {
        if (!enabled()) {
            messages.send(player, "match.disabled", Map.of());
            return;
        }
        RecruitEntry entry = recruits.get(id);
        if (entry == null || entry.expiresAt() <= System.currentTimeMillis()) {
            recruits.remove(id);
            messages.send(player, "match.recruit_not_found", Map.of("id", String.valueOf(id)));
            return;
        }
        PartyService.InviteResult result = parties.invite(player, entry.leaderId());
        if (result != PartyService.InviteResult.OK) {
            messages.send(player, "match.party_error", Map.of("code", result.name()));
            return;
        }
        if (parties.partyOf(player.getUniqueId()) != null) {
            messages.send(player, "match.recruit_joined", Map.of(
                    "id", String.valueOf(id), "dungeon", entry.dungeonId()));
        } else {
            messages.send(player, "match.recruit_applied", Map.of(
                    "id", String.valueOf(id), "dungeon", entry.dungeonId()));
        }
    }

    public void queue(Player player, String dungeonId, String difficulty) {
        if (!enabled()) {
            messages.send(player, "match.disabled", Map.of());
            return;
        }
        if (!templates.discoverDungeons().contains(dungeonId)) {
            messages.send(player, "match.dungeon_missing", Map.of("dungeon", dungeonId));
            return;
        }
        if (parties.partyOf(player.getUniqueId()) != null) {
            messages.send(player, "match.already_in_party", Map.of());
            return;
        }
        if (queueKeys.containsKey(player.getUniqueId())) {
            messages.send(player, "match.queue_already", Map.of());
            return;
        }
        String resolved = difficulty == null || difficulty.isBlank()
                ? DEFAULT_DIFFICULTY : difficulty.trim();
        String key = keyOf(dungeonId, resolved);
        queueKeys.put(player.getUniqueId(), key);
        queues.computeIfAbsent(key, ignored -> new QueueBucket(dungeonId, resolved, new LinkedHashSet<>()))
                .members().add(player.getUniqueId());
        messages.send(player, "match.queued", Map.of(
                "dungeon", dungeonId, "difficulty", resolved,
                "target", String.valueOf(targetSize())));
        QueueBucket bucket = queues.get(key);
        if (bucket != null) {
            tryMatch(key, bucket);
        }
    }

    public void leaveQueue(Player player) {
        String key = queueKeys.remove(player.getUniqueId());
        if (key == null) {
            messages.send(player, "match.queue_none", Map.of());
            return;
        }
        QueueBucket bucket = queues.get(key);
        if (bucket != null) {
            bucket.members().remove(player.getUniqueId());
            if (bucket.members().isEmpty()) {
                queues.remove(key);
            }
        }
        messages.send(player, "match.queue_left", Map.of());
    }

    private void tryMatch(String key, QueueBucket bucket) {
        LinkedHashSet<UUID> members = bucket.members();
        String dungeonId = bucket.dungeonId();
        String difficulty = bucket.difficulty();
        int target = targetSize();
        List<UUID> ready = new ArrayList<>();
        for (UUID memberId : List.copyOf(members)) {
            Player online = Bukkit.getPlayer(memberId);
            if (online == null || parties.partyOf(memberId) != null
                    || !queueKeys.containsKey(memberId)) {
                members.remove(memberId);
                queueKeys.remove(memberId);
                continue;
            }
            ready.add(memberId);
            if (ready.size() >= target) {
                break;
            }
        }
        if (ready.size() < target) {
            if (members.isEmpty()) {
                queues.remove(key);
            }
            return;
        }
        Party formed = parties.formParty(ready);
        if (formed == null) {
            return;
        }
        for (UUID memberId : ready) {
            members.remove(memberId);
            queueKeys.remove(memberId);
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                messages.send(member, "match.matched", Map.of(
                        "dungeon", dungeonId, "difficulty", difficulty,
                        "size", String.valueOf(ready.size())));
            }
        }
        if (members.isEmpty()) {
            queues.remove(key);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Integer, RecruitEntry> entry : recruits.entrySet()) {
            if (entry.getValue().expiresAt() <= now) {
                recruits.remove(entry.getKey());
            }
        }
        List<UUID> stale = new ArrayList<>();
        for (UUID memberId : queueKeys.keySet()) {
            if (Bukkit.getPlayer(memberId) == null) {
                stale.add(memberId);
            }
        }
        for (UUID memberId : stale) {
            String key = queueKeys.remove(memberId);
            if (key == null) {
                continue;
            }
            QueueBucket bucket = queues.get(key);
            if (bucket != null) {
                bucket.members().remove(memberId);
                if (bucket.members().isEmpty()) {
                    queues.remove(key);
                }
            }
        }
        for (Map.Entry<String, QueueBucket> entry : queues.entrySet()) {
            tryMatch(entry.getKey(), entry.getValue());
        }
    }

    private boolean removeRecruitOf(UUID leaderId) {
        for (Map.Entry<Integer, RecruitEntry> entry : recruits.entrySet()) {
            if (entry.getValue().leaderId().equals(leaderId)) {
                recruits.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    private static String keyOf(String dungeonId, String difficulty) {
        return dungeonId + "@" + difficulty;
    }

    private boolean enabled() {
        AppConfig current = config.get();
        return current != null && current.matchmakingEnabled();
    }

    private int targetSize() {
        AppConfig current = config.get();
        int max = current == null ? 4 : current.partyMaxSize();
        int configured = current == null ? 4 : current.matchmakingTargetSize();
        if (configured < 2) {
            configured = max;
        }
        return Math.max(2, Math.min(configured, Math.max(2, max)));
    }

    private long ttlMillis() {
        AppConfig current = config.get();
        int seconds = current == null ? 300 : current.matchmakingRecruitTtlSeconds();
        return seconds <= 0 ? 0L : seconds * 1000L;
    }

    public record RecruitEntry(int id, UUID leaderId, UUID partyId,
            String dungeonId, String difficulty, long expiresAt) {
    }

    private record QueueBucket(String dungeonId, String difficulty, LinkedHashSet<UUID> members) {
    }
}

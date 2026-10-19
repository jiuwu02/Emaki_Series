package emaki.jiuwu.craft.dungeon.party;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;

public final class PartiesStore {

    private static final String CURRENT_VERSION = "1.0.0";

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File file;
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();

    public PartiesStore(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.file = new File(dataFolder, "parties.yml");
    }

    public Map<UUID, Party> parties() {
        return parties;
    }

    public synchronized void load() {
        if (!file.isFile()) {
            return;
        }
        YamlSection section = YamlFiles.load(file);
        if (section == null || section.isEmpty()) {
            return;
        }
        String version = section.getString("version", "");
        if (!CURRENT_VERSION.equals(version)) {
            throw new IllegalStateException("parties.yml version mismatch: " + version);
        }
        YamlSection partiesSection = section.getSection("parties");
        if (partiesSection == null) {
            return;
        }
        for (String key : partiesSection.getKeys(false)) {
            YamlSection entry = partiesSection.getSection(key);
            if (entry == null) {
                continue;
            }
            UUID partyId;
            UUID leaderId;
            try {
                partyId = UUID.fromString(key);
                leaderId = UUID.fromString(entry.getString("leader", ""));
            } catch (IllegalArgumentException exception) {
                continue;
            }
            List<UUID> members = new ArrayList<>();
            for (String rawMember : entry.getStringList("members")) {
                try {
                    members.add(UUID.fromString(rawMember));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (members.isEmpty() || !members.contains(leaderId)) {
                continue;
            }
            Party party = new Party(partyId, leaderId);
            for (UUID memberId : members) {
                if (!memberId.equals(leaderId)) {
                    party.add(memberId);
                }
            }
            parties.put(partyId, party);
        }
    }

    public void saveAsync() {
        dispatcher.runAsync(plugin, this::saveSync);
    }

    public synchronized void saveSync() {
        Map<String, Object> entries = new LinkedHashMap<>();
        for (Party party : parties.values()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("leader", String.valueOf(party.leaderId()));
            List<String> members = new ArrayList<>(party.size());
            for (UUID memberId : party.members()) {
                members.add(String.valueOf(memberId));
            }
            fields.put("members", members);
            entries.put(String.valueOf(party.id()), fields);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("parties", entries);
        try {
            atomicWrite(values);
        } catch (IOException ignored) {
        }
    }

    private void atomicWrite(Map<String, Object> values) throws IOException {
        Path target = file.toPath();
        Path temp = target.resolveSibling(file.getName() + ".tmp");
        YamlFiles.save(temp.toFile(), values);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

package emaki.jiuwu.craft.dungeon.ticket;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.dungeon.config.DungeonConfig;

public final class TicketService {

    private static final String CURRENT_VERSION = "1.0.0";

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File root;

    public TicketService(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "pending_tickets");
    }

    public TicketTemplate resolve(DungeonConfig.TicketRule rule) {
        if (rule == null || !rule.enabled() || rule.item() == null || rule.item().isBlank()) {
            return null;
        }
        String itemId = rule.item().trim();
        try {
            Material material = Material.valueOf(itemId.toUpperCase(Locale.ROOT));
            if (material.isItem()) {
                return new TicketTemplate(new ItemStack(material, 1), true);
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            var result = EmakiCoreLibApi.createConfiguredItem(
                    new ConfiguredItemDefinition(itemId, 1, Map.of()));
            if (result != null && result.success() && result.itemStack() != null) {
                return new TicketTemplate(result.itemStack().clone(), false);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public boolean canAfford(Player player, TicketTemplate template, int amount) {
        int found = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (!matches(template, stack)) {
                continue;
            }
            found += stack.getAmount();
            if (found >= amount) {
                return true;
            }
        }
        return false;
    }

    public List<byte[]> deduct(Player player, TicketTemplate template, int amount) {
        List<byte[]> taken = new ArrayList<>();
        int remaining = amount;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!matches(template, stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            ItemStack original = stack.clone();
            original.setAmount(take);
            taken.add(original.serializeAsBytes());
            if (take == stack.getAmount()) {
                inventory.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
                inventory.setItem(i, stack);
            }
            remaining -= take;
        }
        return taken;
    }

    public void refund(UUID playerId, List<byte[]> items) {
        if (playerId == null || items == null || items.isEmpty()) {
            return;
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            give(online, items);
            return;
        }
        dispatcher.runAsync(plugin, () -> appendPending(playerId, items));
    }

    public void drainPending(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        dispatcher.runAsync(plugin, () -> {
            List<byte[]> items = takePending(playerId);
            if (!items.isEmpty()) {
                dispatcher.runGlobal(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null) {
                        give(online, items);
                    } else {
                        appendPending(playerId, items);
                    }
                });
            }
        });
    }

    private void give(Player player, List<byte[]> items) {
        for (byte[] raw : items) {
            ItemStack stack = deserialize(raw);
            if (stack == null) {
                continue;
            }
            var leftover = player.getInventory().addItem(stack);
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    private static boolean matches(TicketTemplate template, ItemStack stack) {
        if (template == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        return template.materialOnly()
                ? stack.getType() == template.sample().getType()
                : template.sample().isSimilar(stack);
    }

    private static ItemStack deserialize(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(raw);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void appendPending(UUID playerId, List<byte[]> items) {
        File file = fileOf(playerId);
        List<String> encoded = new ArrayList<>();
        for (byte[] item : items) {
            if (item != null && item.length > 0) {
                encoded.add(Base64.getEncoder().encodeToString(item));
            }
        }
        for (byte[] item : readPending(file)) {
            encoded.add(Base64.getEncoder().encodeToString(item));
        }
        writePending(file, encoded);
    }

    private List<byte[]> takePending(UUID playerId) {
        File file = fileOf(playerId);
        List<byte[]> items = readPending(file);
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException ignored) {
        }
        return items;
    }

    private List<byte[]> readPending(File file) {
        List<byte[]> items = new ArrayList<>();
        YamlSection section = YamlFiles.load(file);
        List<?> rawItems = section.getList("items");
        if (rawItems == null) {
            return items;
        }
        for (Object raw : rawItems) {
            try {
                items.add(Base64.getDecoder().decode(String.valueOf(raw)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return items;
    }

    private void writePending(File file, List<String> encoded) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("items", List.copyOf(encoded));
        try {
            Files.createDirectories(root.toPath());
            File temp = new File(root, file.getName() + "." + UUID.randomUUID() + ".tmp");
            YamlFiles.save(temp, values);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
        }
    }

    private File fileOf(UUID playerId) {
        return new File(root, playerId + ".yml");
    }

    public record TicketTemplate(ItemStack sample, boolean materialOnly) {
    }
}

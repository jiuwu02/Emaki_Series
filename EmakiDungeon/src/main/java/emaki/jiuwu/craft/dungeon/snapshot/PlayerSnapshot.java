package emaki.jiuwu.craft.dungeon.snapshot;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class PlayerSnapshot {

    public static final int SLOT_COUNT = 41;

    private final String instanceId;
    private final String dungeonId;
    private final String originWorld;
    private final double originX;
    private final double originY;
    private final double originZ;
    private final float originYaw;
    private final float originPitch;
    private String stateWorld;
    private double stateX;
    private double stateY;
    private double stateZ;
    private float stateYaw;
    private float statePitch;
    private List<byte[]> items;
    private int level;
    private float exp;
    private double health;
    private int food;
    private String gameMode;
    private String partyId;
    private long capturedAt;

    public PlayerSnapshot(String instanceId,
            String dungeonId,
            String originWorld,
            double originX,
            double originY,
            double originZ,
            float originYaw,
            float originPitch) {
        this.instanceId = instanceId;
        this.dungeonId = dungeonId;
        this.originWorld = originWorld;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.originYaw = originYaw;
        this.originPitch = originPitch;
        this.stateWorld = originWorld;
        this.stateX = originX;
        this.stateY = originY;
        this.stateZ = originZ;
        this.stateYaw = originYaw;
        this.statePitch = originPitch;
        this.items = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            items.add(null);
        }
        this.capturedAt = System.currentTimeMillis();
    }

    public static PlayerSnapshot capture(Player player, String instanceId, String dungeonId, String partyId) {
        var location = player.getLocation();
        PlayerSnapshot snapshot = new PlayerSnapshot(
                instanceId, dungeonId,
                location.getWorld() == null ? "" : location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()
        );
        snapshot.captureState(player);
        snapshot.partyId = partyId == null ? "" : partyId;
        return snapshot;
    }

    public void captureState(Player player) {
        var location = player.getLocation();
        this.stateWorld = location.getWorld() == null ? "" : location.getWorld().getName();
        this.stateX = location.getX();
        this.stateY = location.getY();
        this.stateZ = location.getZ();
        this.stateYaw = location.getYaw();
        this.statePitch = location.getPitch();
        this.items = encodeInventory(player.getInventory());
        this.level = player.getLevel();
        this.exp = player.getExp();
        this.health = player.getHealth();
        this.food = player.getFoodLevel();
        this.gameMode = player.getGameMode().name();
        this.capturedAt = System.currentTimeMillis();
    }

    public void applyStateTo(Player player) {
        applyInventoryTo(player);
        applyVitalsTo(player);
    }

    public void applyInventoryTo(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length && i < items.size(); i++) {
            contents[i] = decode(items.get(i));
        }
        inventory.setContents(contents);
    }

    public void applyVitalsTo(Player player) {
        player.setLevel(level);
        player.setExp(exp);
        var maxHealthAttribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? 20.0D : maxHealthAttribute.getValue();
        player.setHealth(Math.max(0.5D, Math.min(maxHealth, health)));
        player.setFoodLevel(food);
        try {
            player.setGameMode(org.bukkit.GameMode.valueOf(gameMode));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void applyStateLocation(Player player) {
        teleportInternal(player, stateWorld, stateX, stateY, stateZ, stateYaw, statePitch);
    }

    public void applyOriginLocation(Player player) {
        teleportInternal(player, originWorld, originX, originY, originZ, originYaw, originPitch);
    }

    private static void teleportInternal(Player player, String worldName,
            double x, double y, double z, float yaw, float pitch) {
        var world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        player.teleport(new org.bukkit.Location(world, x, y, z, yaw, pitch));
    }

    public String instanceId() {
        return instanceId;
    }

    public String dungeonId() {
        return dungeonId;
    }

    public String originWorld() {
        return originWorld;
    }

    public String stateWorld() {
        return stateWorld;
    }

    public String partyId() {
        return partyId;
    }

    public long capturedAt() {
        return capturedAt;
    }

    public YamlSection serialize() {
        YamlSection section = new emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection();
        section.set("instance", instanceId);
        section.set("dungeon", dungeonId);
        section.set("party", partyId == null ? "" : partyId);
        section.set("captured-at", capturedAt);
        section.set("origin.world", originWorld);
        section.set("origin.x", originX);
        section.set("origin.y", originY);
        section.set("origin.z", originZ);
        section.set("origin.yaw", (double) originYaw);
        section.set("origin.pitch", (double) originPitch);
        section.set("state.world", stateWorld);
        section.set("state.x", stateX);
        section.set("state.y", stateY);
        section.set("state.z", stateZ);
        section.set("state.yaw", (double) stateYaw);
        section.set("state.pitch", (double) statePitch);
        section.set("level", level);
        section.set("exp", (double) exp);
        section.set("health", health);
        section.set("food", food);
        section.set("game-mode", gameMode);
        List<String> encoded = new ArrayList<>(items.size());
        for (byte[] item : items) {
            encoded.add(item == null ? "" : Base64.getEncoder().encodeToString(item));
        }
        section.set("items", encoded);
        return section;
    }

    public static PlayerSnapshot deserialize(YamlSection section) {
        PlayerSnapshot snapshot = new PlayerSnapshot(
                section.getString("instance", ""),
                section.getString("dungeon", ""),
                section.getString("origin.world", ""),
                section.getDouble("origin.x", 0.0D),
                section.getDouble("origin.y", 0.0D),
                section.getDouble("origin.z", 0.0D),
                section.getDouble("origin.yaw", 0.0D).floatValue(),
                section.getDouble("origin.pitch", 0.0D).floatValue()
        );
        snapshot.stateWorld = section.getString("state.world", snapshot.originWorld);
        snapshot.stateX = section.getDouble("state.x", snapshot.originX);
        snapshot.stateY = section.getDouble("state.y", snapshot.originY);
        snapshot.stateZ = section.getDouble("state.z", snapshot.originZ);
        snapshot.stateYaw = section.getDouble("state.yaw", 0.0D).floatValue();
        snapshot.statePitch = section.getDouble("state.pitch", 0.0D).floatValue();
        snapshot.level = section.getInt("level", 0);
        snapshot.exp = section.getDouble("exp", 0.0D).floatValue();
        snapshot.health = section.getDouble("health", 20.0D);
        snapshot.food = section.getInt("food", 20);
        snapshot.gameMode = section.getString("game-mode", "SURVIVAL");
        snapshot.partyId = section.getString("party", "");
        snapshot.capturedAt = section.getDouble("captured-at", 0.0D).longValue();
        snapshot.items = new ArrayList<>(SLOT_COUNT);
        List<?> rawItems = section.getList("items");
        for (int i = 0; i < SLOT_COUNT; i++) {
            Object raw = rawItems != null && i < rawItems.size() ? rawItems.get(i) : null;
            snapshot.items.add(decodeBase64(raw == null ? null : String.valueOf(raw)));
        }
        return snapshot;
    }

    public static List<byte[]> encodeInventory(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getContents();
        List<byte[]> encoded = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack item = i < contents.length ? contents[i] : null;
            if (item == null || item.getType().isAir()) {
                encoded.add(null);
            } else {
                encoded.add(item.serializeAsBytes());
            }
        }
        return encoded;
    }

    private static ItemStack decode(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(raw);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static byte[] decodeBase64(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

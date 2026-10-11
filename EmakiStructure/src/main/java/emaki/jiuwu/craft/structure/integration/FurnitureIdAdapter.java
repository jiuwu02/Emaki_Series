package emaki.jiuwu.craft.structure.integration;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

public final class FurnitureIdAdapter {

    public static final List<String> KNOWN_SOURCES =
            List.of("ItemsAdder", "Oraxen", "Nexo", "CraftEngine");

    private final ItemsAdderFurnitureBridge itemsAdder;
    private final OraxenFurnitureBridge oraxen;
    private final NexoFurnitureBridge nexo;
    private final CraftEngineFurnitureBridge craftEngine;
    private final List<String> absentSources;

    public FurnitureIdAdapter(Plugin plugin) {
        this.itemsAdder = createIfPresent(plugin, "ItemsAdder", ItemsAdderFurnitureBridge::new);
        this.oraxen = createIfPresent(plugin, "Oraxen", OraxenFurnitureBridge::new);
        this.nexo = createIfPresent(plugin, "Nexo", NexoFurnitureBridge::new);
        this.craftEngine = createIfPresent(plugin, "CraftEngine", CraftEngineFurnitureBridge::new);
        List<String> absent = new ArrayList<>();
        if (itemsAdder == null) {
            absent.add("ItemsAdder");
        }
        if (oraxen == null) {
            absent.add("Oraxen");
        }
        if (nexo == null) {
            absent.add("Nexo");
        }
        if (craftEngine == null) {
            absent.add("CraftEngine");
        }
        this.absentSources = List.copyOf(absent);
    }

    private static <T> T createIfPresent(Plugin plugin, String pluginName, Supplier<T> factory) {
        if (plugin.getServer().getPluginManager().getPlugin(pluginName) == null) {
            return null;
        }
        try {
            return factory.get();
        } catch (LinkageError error) {
            return null;
        }
    }

    public List<String> absentSources() {
        return absentSources;
    }

    public boolean anySourcePresent() {
        return itemsAdder != null || oraxen != null || nexo != null || craftEngine != null;
    }

    public boolean sourcePresent(String sourceName) {
        return switch (sourceName) {
            case "ItemsAdder" -> itemsAdder != null;
            case "Oraxen" -> oraxen != null;
            case "Nexo" -> nexo != null;
            case "CraftEngine" -> craftEngine != null;
            default -> false;
        };
    }

    public Optional<String> identify(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        String resolved = firstNonNull(
                itemsAdder == null ? null : safeIdentify(() -> itemsAdder.identify(entity)),
                oraxen == null ? null : safeIdentify(() -> oraxen.identify(entity)),
                nexo == null ? null : safeIdentify(() -> nexo.identify(entity)),
                craftEngine == null ? null : safeIdentify(() -> craftEngine.identify(entity)));
        return Optional.ofNullable(resolved);
    }

    public Optional<String> identifyAt(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        String resolved = firstNonNull(
                itemsAdder == null ? null : safeIdentify(() -> itemsAdder.identifyAt(block)),
                oraxen == null ? null : safeIdentify(() -> oraxen.identifyAt(block)),
                nexo == null ? null : safeIdentify(() -> nexo.identifyAt(block)),
                craftEngine == null ? null : safeIdentify(() -> craftEngine.identifyAt(block)));
        return Optional.ofNullable(resolved);
    }

    public boolean matchesAt(Block anchor, String expectedId) {
        if (anchor == null || expectedId == null || expectedId.isBlank()) {
            return false;
        }
        String wanted = expectedId.toLowerCase(Locale.ROOT);
        if (wanted.equals(identifyAtValue(anchor))) {
            return true;
        }
        BoundingBox box = new BoundingBox(
                anchor.getX() - 1.0d, anchor.getY(), anchor.getZ() - 1.0d,
                anchor.getX() + 2.0d, anchor.getY() + 3.0d, anchor.getZ() + 2.0d);
        Collection<Entity> nearby;
        try {
            nearby = anchor.getWorld().getNearbyEntities(box, candidate -> true);
        } catch (RuntimeException exception) {
            return false;
        }
        for (Entity entity : nearby) {
            if (wanted.equals(identifyValue(entity))) {
                return true;
            }
        }
        return false;
    }

    private String identifyAtValue(Block block) {
        Optional<String> identified = identifyAt(block);
        return identified.orElse(null);
    }

    private String identifyValue(Entity entity) {
        Optional<String> identified = identify(entity);
        return identified.orElse(null);
    }

    private static String safeIdentify(Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (LinkageError error) {
            return null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static String namespaceOf(String furnitureId) {
        if (furnitureId == null) {
            return "";
        }
        int colon = furnitureId.indexOf(':');
        return colon < 0 ? "" : furnitureId.substring(0, colon);
    }

    public static boolean knownNamespace(String namespace) {
        return switch (namespace == null ? "" : namespace.toLowerCase(Locale.ROOT)) {
            case "itemsadder", "oraxen", "nexo", "craftengine" -> true;
            default -> false;
        };
    }

    public static String sourceOfNamespace(String namespace) {
        return switch (namespace == null ? "" : namespace.toLowerCase(Locale.ROOT)) {
            case "itemsadder" -> "ItemsAdder";
            case "oraxen" -> "Oraxen";
            case "nexo" -> "Nexo";
            case "craftengine" -> "CraftEngine";
            default -> null;
        };
    }
}

package emaki.jiuwu.craft.structure.integration;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.furniture.FurnitureBridge;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FurnitureIdAdapter {

    public static final List<String> KNOWN_SOURCES =
            List.of("ItemsAdder", "Oraxen", "Nexo", "CraftEngine");

    private final FurnitureBridge itemsAdder;
    private final FurnitureBridge oraxen;
    private final FurnitureBridge nexo;
    private final FurnitureBridge craftEngine;
    private final List<String> presentSources;

    public FurnitureIdAdapter() {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.lookup();
        this.itemsAdder = coreLib.itemsAdderFurnitureBridge();
        this.oraxen = coreLib.oraxenFurnitureBridge();
        this.nexo = coreLib.nexoFurnitureBridge();
        this.craftEngine = coreLib.craftEngineFurnitureBridge();
        List<String> present = new ArrayList<>();
        for (String source : KNOWN_SOURCES) {
            if (sourcePresent(source)) {
                present.add(source);
            }
        }
        this.presentSources = List.copyOf(present);
    }

    public List<String> presentSources() {
        return presentSources;
    }

    public boolean anySourcePresent() {
        return itemsAdder.available() || oraxen.available()
                || nexo.available() || craftEngine.available();
    }

    public boolean sourcePresent(String sourceName) {
        return switch (sourceName) {
            case "ItemsAdder" -> itemsAdder.available();
            case "Oraxen" -> oraxen.available();
            case "Nexo" -> nexo.available();
            case "CraftEngine" -> craftEngine.available();
            default -> false;
        };
    }

    public Optional<String> identify(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(firstNonNull(
                itemsAdder.identify(entity),
                oraxen.identify(entity),
                nexo.identify(entity),
                craftEngine.identify(entity)));
    }

    public Optional<String> identifyAt(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(firstNonNull(
                itemsAdder.identifyAt(block),
                oraxen.identifyAt(block),
                nexo.identifyAt(block),
                craftEngine.identifyAt(block)));
    }

    public boolean matchesAt(Block anchor, String expectedId) {
        if (anchor == null || expectedId == null || expectedId.isBlank()) {
            return false;
        }
        String wanted = expectedId.toLowerCase(Locale.ROOT);
        if (wanted.equals(identifyAt(anchor).orElse(null))) {
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
            if (wanted.equals(identify(entity).orElse(null))) {
                return true;
            }
        }
        return false;
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

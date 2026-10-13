package emaki.jiuwu.craft.structure.integration;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import org.bukkit.block.Block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CustomBlockIdAdapter {

    public static final List<String> KNOWN_NAMESPACES =
            List.of("itemsadder", "oraxen", "nexo", "craftengine");

    public record BlockHandle(String namespace, String identifier, String rawId, boolean resolvable) {

        public static BlockHandle unresolvable(String namespace, String identifier, String rawId) {
            return new BlockHandle(namespace, identifier, rawId, false);
        }
    }

    private final Map<String, CustomBlockBridge> bridges = new LinkedHashMap<>();

    public CustomBlockIdAdapter() {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.lookup();
        bridges.put("itemsadder", coreLib.itemsAdderBlockBridge());
        bridges.put("oraxen", coreLib.oraxenBlockBridge());
        bridges.put("nexo", coreLib.nexoBlockBridge());
        bridges.put("craftengine", coreLib.craftEngineBlockBridge());
    }

    public static String normalize(String rawId) {
        if (rawId == null) {
            return "";
        }
        String trimmed = rawId.trim().toLowerCase(Locale.ROOT);
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return trimmed;
        }
        return trimmed;
    }

    public static String namespaceOf(String normalizedId) {
        int colon = normalizedId.indexOf(':');
        return colon < 0 ? "" : normalizedId.substring(0, colon);
    }

    public boolean namespaceKnown(String namespace) {
        return KNOWN_NAMESPACES.contains(namespace);
    }

    public BlockHandle bind(String rawId) {
        String normalized = normalize(rawId);
        for (String source : KNOWN_NAMESPACES) {
            if (!normalized.startsWith(source + "-")) {
                continue;
            }
            String identifier = normalized.substring(source.length() + 1);
            CustomBlockBridge bridge = bridges.get(source);
            if (identifier.isEmpty() || bridge == null || !bridge.available()) {
                return BlockHandle.unresolvable(source, identifier, normalized);
            }
            return new BlockHandle(source, identifier, normalized, true);
        }
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            String head = normalized.substring(0, colon);
            if (namespaceKnown(head)) {
                CustomBlockBridge bridge = bridges.get(head);
                String stripped = normalized.substring(colon + 1);
                String identifier = stripped.isEmpty() ? normalized : stripped;
                if (bridge == null || !bridge.available()) {
                    return BlockHandle.unresolvable(head, identifier, normalized);
                }
                return new BlockHandle(head, identifier, normalized, true);
            }
            return new BlockHandle("", normalized, normalized, anyBridgeAvailable());
        }
        return new BlockHandle("", normalized, normalized, anyBridgeAvailable());
    }

    public boolean matches(Block block, BlockHandle handle) {
        if (block == null || handle == null || !handle.resolvable()) {
            return false;
        }
        if (handle.namespace().isEmpty()) {
            for (CustomBlockBridge bridge : bridges.values()) {
                if (bridge != null && bridge.available()
                        && bridge.matches(block, handle.identifier())) {
                    return true;
                }
            }
            return false;
        }
        CustomBlockBridge bridge = bridges.get(handle.namespace());
        if (bridge == null || !bridge.available()) {
            return false;
        }
        try {
            if (bridge.matches(block, handle.identifier())) {
                return true;
            }
            return !handle.identifier().equals(handle.rawId())
                    && bridge.matches(block, handle.rawId());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean place(Block block, BlockHandle handle) {
        if (block == null || handle == null || !handle.resolvable()) {
            return false;
        }
        if (handle.namespace().isEmpty()) {
            for (CustomBlockBridge bridge : bridges.values()) {
                if (bridge != null && bridge.available()
                        && bridge.placeBlock(block, handle.identifier())) {
                    return true;
                }
            }
            return false;
        }
        CustomBlockBridge bridge = bridges.get(handle.namespace());
        if (bridge == null || !bridge.available()) {
            return false;
        }
        try {
            if (bridge.placeBlock(block, handle.identifier())) {
                return true;
            }
            return !handle.identifier().equals(handle.rawId())
                    && bridge.placeBlock(block, handle.rawId());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public String identify(Block block) {
        if (block == null) {
            return "";
        }
        for (Map.Entry<String, CustomBlockBridge> entry : bridges.entrySet()) {
            CustomBlockBridge bridge = entry.getValue();
            if (bridge == null || !bridge.available()) {
                continue;
            }
            try {
                if (bridge.isCustomBlock(block)) {
                    String identified = bridge.identifyBlock(block);
                    if (identified == null || identified.isBlank()) {
                        continue;
                    }
                    return entry.getKey() + "-" + identified.toLowerCase(Locale.ROOT);
                }
            } catch (RuntimeException exception) {
                continue;
            }
        }
        return "";
    }

    public boolean anyBridgeAvailable() {
        for (CustomBlockBridge bridge : bridges.values()) {
            if (bridge != null && bridge.available()) {
                return true;
            }
        }
        return false;
    }

    public List<String> availableKnownNamespaces() {
        return KNOWN_NAMESPACES.stream()
                .filter(namespace -> {
                    CustomBlockBridge bridge = bridges.get(namespace);
                    return bridge != null && bridge.available();
                })
                .toList();
    }
}

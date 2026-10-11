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

    public record BlockHandle(String namespace, String normalizedId, boolean resolvable) {

        public static BlockHandle unresolvable(String namespace, String normalizedId) {
            return new BlockHandle(namespace, normalizedId, false);
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
        String normalizedId = normalize(rawId);
        String namespace = namespaceOf(normalizedId);
        if (namespace.isEmpty()) {
            return new BlockHandle("", normalizedId, anyBridgeAvailable());
        }
        if (!namespaceKnown(namespace)) {
            return BlockHandle.unresolvable(namespace, normalizedId);
        }
        CustomBlockBridge bridge = bridges.get(namespace);
        if (bridge == null || !bridge.available()) {
            return BlockHandle.unresolvable(namespace, normalizedId);
        }
        return new BlockHandle(namespace, normalizedId, true);
    }

    public boolean matches(Block block, BlockHandle handle) {
        if (block == null || handle == null || !handle.resolvable()) {
            return false;
        }
        if (handle.namespace().isEmpty()) {
            for (CustomBlockBridge bridge : bridges.values()) {
                if (bridge != null && bridge.available() && bridge.matches(block, handle.normalizedId())) {
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
            return bridge.matches(block, handle.normalizedId());
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
                    String normalized = identified.toLowerCase(Locale.ROOT);
                    if (normalized.indexOf(':') < 0) {
                        return entry.getKey() + ":" + normalized;
                    }
                    return normalized;
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

    public List<String> unavailableKnownNamespaces() {
        return KNOWN_NAMESPACES.stream()
                .filter(namespace -> {
                    CustomBlockBridge bridge = bridges.get(namespace);
                    return bridge == null || !bridge.available();
                })
                .toList();
    }
}

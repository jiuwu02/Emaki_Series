package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class AffixSelectionService {

    private final EmakiStrengthenPlugin plugin;
    private final AffixLayerCodec layerCodec;
    private final Map<UUID, String> selections = new ConcurrentHashMap<>();

    public AffixSelectionService(EmakiStrengthenPlugin plugin, AffixLayerCodec layerCodec) {
        this.plugin = plugin;
        this.layerCodec = layerCodec;
    }

    public @NotNull List<String> enhanceableAffixes(@Nullable ItemStack itemStack, int maxLevel) {
        List<String> affixes = affixes(itemStack);
        if (affixes.isEmpty()) {
            return List.of();
        }
        AffixLayer layer = layerCodec.readOrEmpty(itemStack, 0);
        return affixes.stream()
                .filter(key -> maxLevel <= 0 || layer.affix(key).level() < maxLevel)
                .toList();
    }

    public @NotNull List<String> affixes(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || plugin == null
                || plugin.pdcAttributeGateway() == null) {
            return List.of();
        }
        Map<String, Map<String, Double>> bySource = plugin.pdcAttributeGateway().readAllAttributes(itemStack);
        if (bySource.isEmpty()) {
            return List.of();
        }
        return bySource.entrySet().stream()
                .filter(entry -> !AffixTargetProvider.ATTRIBUTE_SOURCE_ID.equals(Texts.lower(entry.getKey())))
                .flatMap(entry -> entry.getValue().keySet().stream())
                .map(Texts::lower)
                .filter(Texts::isNotBlank)
                .distinct()
                .sorted()
                .toList();
    }

    public @NotNull String selected(@Nullable UUID playerId, @NotNull List<String> candidates) {
        if (candidates.isEmpty()) {
            return "";
        }
        String current = playerId == null ? null : selections.get(playerId);
        if (current != null && candidates.contains(current)) {
            return current;
        }
        String fallback = candidates.getFirst();
        if (playerId != null) {
            selections.put(playerId, fallback);
        }
        return fallback;
    }

    public @NotNull String selectNext(@Nullable UUID playerId, @NotNull List<String> candidates) {
        return cycle(playerId, candidates, 1);
    }

    public @NotNull String selectPrevious(@Nullable UUID playerId, @NotNull List<String> candidates) {
        return cycle(playerId, candidates, -1);
    }

    public void clear(@Nullable UUID playerId) {
        if (playerId != null) {
            selections.remove(playerId);
        }
    }

    public void clearAll() {
        selections.clear();
    }

    private String cycle(UUID playerId, List<String> candidates, int delta) {
        if (candidates.isEmpty()) {
            return "";
        }
        String current = selected(playerId, candidates);
        int index = candidates.indexOf(current);
        if (index < 0) {
            index = 0;
        }
        int size = candidates.size();
        int next = ((index + delta) % size + size) % size;
        String selection = candidates.get(next);
        if (playerId != null) {
            selections.put(playerId, selection);
        }
        return selection;
    }
}

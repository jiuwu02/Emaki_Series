package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

final class ItemRenderService {

    private final StructuredPresentationRenderer structuredRenderer;

    ItemRenderService() {
        this(null);
    }

    ItemRenderService(EmakiNamespaceRegistry namespaceRegistry) {
        this.structuredRenderer = new StructuredPresentationRenderer(namespaceRegistry);
    }

    void renderItem(ItemStack itemStack, Collection<EmakiItemLayerSnapshot> snapshots) {
        renderItem(itemStack, snapshots, null);
    }

    void renderItem(ItemStack itemStack, Collection<EmakiItemLayerSnapshot> snapshots, Component baseNameOverride) {
        if (itemStack == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        Map<String, Double> aggregatedStats = aggregateStats(snapshots);
        Component currentName = baseNameOverride == null ? resolveInitialName(itemStack, itemMeta) : baseNameOverride;
        List<Component> currentLore = new ArrayList<>(
                itemMeta.hasLore() && ItemTextBridge.lore(itemMeta) != null
                        ? ItemTextBridge.lore(itemMeta)
                        : List.<Component>of()
        );
        StructuredPresentationRenderer.RenderResult renderResult = structuredRenderer.render(
                itemStack,
                currentName,
                currentLore,
                aggregatedStats,
                snapshots
        );
        ItemTextBridge.lore(itemMeta, renderResult.lore().isEmpty() ? null : renderResult.lore());
        itemStack.setItemMeta(itemMeta);
        if (renderResult.customizedName() || baseNameOverride != null) {
            if (!SpigotItemComponentNameWriter.writeCustomName(itemStack, renderResult.name())) {
                itemMeta = itemStack.getItemMeta();
                if (itemMeta != null) {
                    ItemTextBridge.customName(itemMeta, renderResult.name());
                    itemStack.setItemMeta(itemMeta);
                }
            }
        }
    }

    private Map<String, Double> aggregateStats(Collection<EmakiItemLayerSnapshot> snapshots) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (snapshots == null) {
            return result;
        }
        for (EmakiItemLayerSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.stats() == null) {
                continue;
            }
            List<EmakiStatContribution> orderedStats = new ArrayList<>(snapshot.stats());
            orderedStats.sort(Comparator.comparingInt(EmakiStatContribution::sequence)
                    .thenComparing(EmakiStatContribution::statId));
            for (EmakiStatContribution contribution : orderedStats) {
                if (contribution == null || contribution.statId() == null || contribution.statId().isBlank()) {
                    continue;
                }
                result.merge(Texts.normalizeId(contribution.statId()), contribution.amount(), Double::sum);
            }
        }
        return result;
    }

    private Component resolveInitialName(ItemStack itemStack, ItemMeta itemMeta) {
        if (itemMeta != null && ItemTextBridge.hasCustomName(itemMeta)) {
            return ItemTextBridge.customName(itemMeta);
        }
        if (itemStack != null) {
            try {
                return ItemTextBridge.effectiveName(itemStack);
            } catch (Exception _) {
                return Component.empty();
            }
        }
        return Component.empty();
    }
}

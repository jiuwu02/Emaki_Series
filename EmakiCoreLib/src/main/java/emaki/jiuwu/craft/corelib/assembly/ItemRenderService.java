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
import emaki.jiuwu.craft.corelib.text.MiniMessages;
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
        if (itemStack == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        Map<String, Double> aggregatedStats = aggregateStats(snapshots);
        String currentName = resolveInitialName(itemStack, itemMeta);
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
        if (renderResult.customizedName()) {
            ItemTextBridge.customName(itemMeta, MiniMessages.parse(renderResult.name()));
        }
        ItemTextBridge.lore(itemMeta, renderResult.lore().isEmpty() ? null : renderResult.lore());
        itemStack.setItemMeta(itemMeta);
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

    private String resolveInitialName(ItemStack itemStack, ItemMeta itemMeta) {
        if (itemMeta != null && ItemTextBridge.hasCustomName(itemMeta)) {
            return MiniMessages.serialize(ItemTextBridge.customName(itemMeta));
        }
        if (itemStack != null) {
            try {
                return MiniMessages.serialize(ItemTextBridge.effectiveName(itemStack));
            } catch (Exception ignored) {
                return "";
            }
        }
        return "";
    }
}

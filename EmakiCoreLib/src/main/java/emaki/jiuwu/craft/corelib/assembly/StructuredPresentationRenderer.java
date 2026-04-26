package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

final class StructuredPresentationRenderer {

    private final EmakiNamespaceRegistry namespaceRegistry;

    StructuredPresentationRenderer(EmakiNamespaceRegistry namespaceRegistry) {
        this.namespaceRegistry = namespaceRegistry;
    }

    RenderResult render(ItemStack itemStack,
            Component fallbackBaseName,
            List<Component> currentLore,
            Map<String, Double> aggregatedStats,
            Collection<EmakiItemLayerSnapshot> snapshots) {
        List<StructuredLayerPresentation> presentations = collectPresentations(snapshots);
        if (presentations.isEmpty()) {
            return new RenderResult(normalizeComponent(fallbackBaseName), copyLore(currentLore), false);
        }

        // 预构建格式化 Map，整个 render 过程只构建一次，避免每次 renderTemplate 都重建
        Map<String, Object> formattedStats = buildFormattedStats(aggregatedStats);

        Component baseName = resolveBaseName(itemStack, fallbackBaseName, presentations, formattedStats);
        List<EmakiNameContribution> nameContributions = collectNameContributions(presentations);
        List<Component> lore = copyLore(currentLore);
        appendLoreSections(lore, presentations, formattedStats);

        boolean customizedName = Texts.isNotBlank(resolveExplicitBaseNameTemplate(presentations)) || !nameContributions.isEmpty();
        if (!customizedName) {
            return new RenderResult(baseName, lore, false);
        }

        Component prefix = joinContributions(nameContributions, NamePosition.PREFIX, formattedStats);
        Component postfix = joinContributions(nameContributions, NamePosition.POSTFIX, formattedStats);
        return new RenderResult(Component.empty().append(prefix).append(baseName).append(postfix), lore, true);
    }

    private static Map<String, Object> buildFormattedStats(Map<String, Double> aggregatedStats) {
        if (aggregatedStats == null || aggregatedStats.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> formatted = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : aggregatedStats.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            formatted.put(entry.getKey(), Numbers.formatNumber(entry.getValue(), "0.##"));
        }
        return formatted;
    }

    private List<StructuredLayerPresentation> collectPresentations(Collection<EmakiItemLayerSnapshot> snapshots) {
        List<StructuredLayerPresentation> result = new ArrayList<>();
        if (snapshots == null) {
            return result;
        }
        for (EmakiItemLayerSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.structuredPresentation() == null || snapshot.structuredPresentation().isEmpty()) {
                continue;
            }
            result.add(new StructuredLayerPresentation(snapshot.namespaceId(), snapshot.structuredPresentation()));
        }
        result.sort(Comparator.comparingInt((StructuredLayerPresentation value) -> namespaceOrder(value.namespaceId()))
                .thenComparing(StructuredLayerPresentation::namespaceId));
        return result;
    }

    private List<EmakiNameContribution> collectNameContributions(List<StructuredLayerPresentation> presentations) {
        List<EmakiNameContribution> contributions = new ArrayList<>();
        for (StructuredLayerPresentation presentation : presentations) {
            contributions.addAll(presentation.presentation().nameContributions());
        }
        contributions.sort(Comparator
                .comparing((EmakiNameContribution contribution) -> contribution.position() == NamePosition.PREFIX ? 0 : 1)
                .thenComparingInt(EmakiNameContribution::order)
                .thenComparingInt(contribution -> namespaceOrder(contribution.sourceNamespace()))
                .thenComparing(EmakiNameContribution::slotId));
        return contributions;
    }

    private void appendLoreSections(List<Component> lore,
            List<StructuredLayerPresentation> presentations,
            Map<String, Object> formattedStats) {
        List<EmakiLoreSectionContribution> sections = new ArrayList<>();
        for (StructuredLayerPresentation presentation : presentations) {
            sections.addAll(presentation.presentation().loreSections());
        }
        sections.sort(Comparator.comparingInt(EmakiLoreSectionContribution::order)
                .thenComparingInt(section -> namespaceOrder(section.sourceNamespace()))
                .thenComparing(EmakiLoreSectionContribution::sectionId));
        for (EmakiLoreSectionContribution section : sections) {
            for (String line : section.lines()) {
                String rendered = renderTemplate(line, formattedStats);
                lore.add(Texts.isBlank(rendered) ? Component.empty() : MiniMessages.parse(rendered));
            }
        }
    }

    private Component joinContributions(List<EmakiNameContribution> contributions,
            NamePosition position,
            Map<String, Object> formattedStats) {
        Component result = Component.empty();
        for (EmakiNameContribution contribution : contributions) {
            if (contribution == null || contribution.position() != position) {
                continue;
            }
            result = result.append(MiniMessages.parse(renderTemplate(contribution.contentTemplate(), formattedStats)));
        }
        return result;
    }

    private Component resolveBaseName(ItemStack itemStack,
            Component fallbackBaseName,
            List<StructuredLayerPresentation> presentations,
            Map<String, Object> formattedStats) {
        String explicitTemplate = resolveExplicitBaseNameTemplate(presentations);
        if (Texts.isNotBlank(explicitTemplate)) {
            return MiniMessages.parse(renderTemplate(explicitTemplate, formattedStats));
        }
        if (resolveBaseNamePolicy(presentations) == BaseNamePolicy.SOURCE_TRANSLATABLE) {
            return sourceTranslatableName(itemStack);
        }
        if (!isEmptyComponent(fallbackBaseName)) {
            return fallbackBaseName;
        }
        return ItemTextBridge.effectiveName(itemStack);
    }

    private String resolveExplicitBaseNameTemplate(List<StructuredLayerPresentation> presentations) {
        for (StructuredLayerPresentation presentation : presentations) {
            if (presentation.presentation().baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                    && Texts.isNotBlank(presentation.presentation().baseNameTemplate())) {
                return presentation.presentation().baseNameTemplate();
            }
        }
        return "";
    }

    private BaseNamePolicy resolveBaseNamePolicy(List<StructuredLayerPresentation> presentations) {
        for (StructuredLayerPresentation presentation : presentations) {
            if (presentation.presentation().baseNamePolicy() == BaseNamePolicy.SOURCE_TRANSLATABLE) {
                return BaseNamePolicy.SOURCE_TRANSLATABLE;
            }
        }
        return BaseNamePolicy.SOURCE_EFFECTIVE_NAME;
    }

    private Component sourceTranslatableName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Component.empty();
        }
        String translationKey = translationKey(itemStack.getType());
        if (Texts.isNotBlank(translationKey)) {
            return Component.translatable(translationKey);
        }
        return Component.text(humanizeMaterial(itemStack.getType()));
    }

    private String translationKey(Material material) {
        if (material == null) {
            return "";
        }
        try {
            if (material.isItem()) {
                return material.getItemTranslationKey();
            }
            if (material.isBlock()) {
                return material.getBlockTranslationKey();
            }
            return material.getTranslationKey();
        } catch (RuntimeException _) {
            return "";
        }
    }

    private String humanizeMaterial(Material material) {
        if (material == null) {
            return "";
        }
        String[] parts = material.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String renderTemplate(String template, Map<String, Object> formattedStats) {
        if (Texts.isBlank(template) || formattedStats == null || formattedStats.isEmpty()) {
            return Texts.toStringSafe(template);
        }
        return Texts.formatTemplate(template, formattedStats);
    }

    private int namespaceOrder(String namespaceId) {
        if (namespaceRegistry == null || Texts.isBlank(namespaceId)) {
            return Integer.MAX_VALUE;
        }
        EmakiNamespaceDefinition definition = namespaceRegistry.get(namespaceId);
        return definition == null ? Integer.MAX_VALUE : definition.order();
    }

    private List<Component> copyLore(List<Component> currentLore) {
        return currentLore == null || currentLore.isEmpty() ? new ArrayList<>() : new ArrayList<>(currentLore);
    }

    private Component normalizeComponent(Component component) {
        return component == null ? Component.empty() : component;
    }

    private boolean isEmptyComponent(Component component) {
        return component == null || Component.empty().equals(component);
    }

    record RenderResult(Component name, List<Component> lore, boolean customizedName) {

        RenderResult {
            name = name == null ? Component.empty() : name;
            lore = lore == null ? List.of() : List.copyOf(lore);
        }
    }

    private record StructuredLayerPresentation(String namespaceId, EmakiStructuredPresentation presentation) {

    }
}

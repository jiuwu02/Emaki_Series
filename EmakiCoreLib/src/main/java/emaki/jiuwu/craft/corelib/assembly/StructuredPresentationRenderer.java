package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            String fallbackBaseName,
            List<Component> currentLore,
            Map<String, Double> aggregatedStats,
            Collection<EmakiItemLayerSnapshot> snapshots) {
        List<StructuredLayerPresentation> presentations = collectPresentations(snapshots);
        if (presentations.isEmpty()) {
            return new RenderResult(Texts.toStringSafe(fallbackBaseName), copyLore(currentLore), false);
        }

        // 预构建格式化 Map，整个 render 过程只构建一次，避免每次 renderTemplate 都重建
        Map<String, Object> formattedStats = buildFormattedStats(aggregatedStats);

        String baseName = resolveBaseName(itemStack, fallbackBaseName, presentations, formattedStats);
        List<EmakiNameContribution> nameContributions = collectNameContributions(presentations);
        List<Component> lore = copyLore(currentLore);
        appendLoreSections(lore, presentations, formattedStats);

        boolean customizedName = Texts.isNotBlank(resolveExplicitBaseNameTemplate(presentations)) || !nameContributions.isEmpty();
        if (!customizedName) {
            return new RenderResult(baseName, lore, false);
        }

        String prefix = joinContributions(nameContributions, NamePosition.PREFIX, formattedStats);
        String postfix = joinContributions(nameContributions, NamePosition.POSTFIX, formattedStats);
        return new RenderResult(prefix + baseName + postfix, lore, true);
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

    private String joinContributions(List<EmakiNameContribution> contributions,
            NamePosition position,
            Map<String, Object> formattedStats) {
        StringBuilder builder = new StringBuilder();
        for (EmakiNameContribution contribution : contributions) {
            if (contribution == null || contribution.position() != position) {
                continue;
            }
            builder.append(renderTemplate(contribution.contentTemplate(), formattedStats));
        }
        return builder.toString();
    }

    private String resolveBaseName(ItemStack itemStack,
            String fallbackBaseName,
            List<StructuredLayerPresentation> presentations,
            Map<String, Object> formattedStats) {
        String explicitTemplate = resolveExplicitBaseNameTemplate(presentations);
        if (Texts.isNotBlank(explicitTemplate)) {
            return renderTemplate(explicitTemplate, formattedStats);
        }
        if (Texts.isNotBlank(fallbackBaseName)) {
            return fallbackBaseName;
        }
        return MiniMessages.serialize(ItemTextBridge.effectiveName(itemStack));
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

    record RenderResult(String name, List<Component> lore, boolean customizedName) {

        RenderResult {
            name = Texts.toStringSafe(name);
            lore = lore == null ? List.of() : List.copyOf(lore);
        }
    }

    private record StructuredLayerPresentation(String namespaceId, EmakiStructuredPresentation presentation) {

    }
}

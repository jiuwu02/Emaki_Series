package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.assembly.lore.LoreSearchInsertProcessor;
import emaki.jiuwu.craft.corelib.assembly.lore.LoreSearchInsertResult;
import emaki.jiuwu.craft.corelib.assembly.lore.LoreSearchInsertStatus;
import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertConfig;
import emaki.jiuwu.craft.corelib.assembly.lore.SearchInsertValidationException;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

final class ItemRenderService {

    private static final double ZERO_EPSILON = 1.0E-9D;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]+)}");
    private static final Pattern EXPRESSION_BLOCK_PATTERN = Pattern.compile("\\[([^\\[\\]]+)]");

    private final Map<String, Pattern> regexCache = new ConcurrentHashMap<>();
    private final LoreSearchInsertProcessor loreSearchInsertProcessor = new LoreSearchInsertProcessor();

    void renderItem(ItemStack itemStack, Collection<EmakiItemLayerSnapshot> snapshots) {
        renderItem(itemStack, snapshots, null, AssemblyFeedbackHandler.noop());
    }

    void renderItem(ItemStack itemStack,
            Collection<EmakiItemLayerSnapshot> snapshots,
            UUID feedbackPlayerId,
            AssemblyFeedbackHandler feedbackHandler) {
        if (itemStack == null) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        Map<String, Double> aggregatedStats = aggregateStats(snapshots);
        boolean writeCustomName = itemMeta.hasCustomName();
        String currentName = resolveInitialName(itemStack, itemMeta);
        List<Component> lore = new ArrayList<>(itemMeta.hasLore() && itemMeta.lore() != null ? itemMeta.lore() : List.<Component>of());
        AssemblyFeedbackHandler effectiveFeedbackHandler = feedbackHandler == null ? AssemblyFeedbackHandler.noop() : feedbackHandler;
        Map<String, StatLineDefinition> statLineDefinitions = new LinkedHashMap<>();
        int globalSequence = 0;
        boolean lastNameChanged = false;
        for (EmakiPresentationEntry entry : flattenPresentation(snapshots)) {
            if (entry == null) {
                continue;
            }
            switch (Texts.lower(entry.entryType())) {
                case "name_prepend" -> {
                    String updatedName = formatPlaceholders(entry.contentTemplate(), aggregatedStats) + currentName;
                    lastNameChanged = !updatedName.equals(currentName);
                    currentName = updatedName;
                    writeCustomName = true;
                }
                case "name_append" -> {
                    String updatedName = currentName + formatPlaceholders(entry.contentTemplate(), aggregatedStats);
                    lastNameChanged = !updatedName.equals(currentName);
                    currentName = updatedName;
                    writeCustomName = true;
                }
                case "name_append_if_unchanged" -> {
                    if (!lastNameChanged) {
                        String updatedName = currentName + formatPlaceholders(entry.contentTemplate(), aggregatedStats);
                        lastNameChanged = !updatedName.equals(currentName);
                        currentName = updatedName;
                    } else {
                        lastNameChanged = false;
                    }
                    writeCustomName = true;
                }
                case "name_append_if_changed" -> {
                    if (lastNameChanged) {
                        String updatedName = currentName + formatPlaceholders(entry.contentTemplate(), aggregatedStats);
                        lastNameChanged = !updatedName.equals(currentName);
                        currentName = updatedName;
                    } else {
                        lastNameChanged = false;
                    }
                    writeCustomName = true;
                }
                case "name_replace" -> {
                    String updatedName = formatPlaceholders(entry.contentTemplate(), aggregatedStats);
                    lastNameChanged = !updatedName.equals(currentName);
                    currentName = updatedName;
                    writeCustomName = true;
                }
                case "name_regex_replace" -> {
                    String updatedName = replaceRegex(currentName, entry.searchPattern(), entry.contentTemplate(), aggregatedStats);
                    lastNameChanged = !updatedName.equals(currentName);
                    currentName = updatedName;
                    writeCustomName = true;
                }
                case "stat_line", "lore_stat_line" -> {
                    String statId = Texts.isBlank(entry.sourceNamespace())
                            ? firstPlaceholder(entry.contentTemplate())
                            : normalizeId(entry.sourceNamespace());
                    if (Texts.isNotBlank(statId)) {
                        statLineDefinitions.put(statId, new StatLineDefinition(statId, entry, globalSequence));
                    }
                }
                default ->
                    applyLoreEntry(lore, entry, aggregatedStats, feedbackPlayerId, effectiveFeedbackHandler);
            }
            globalSequence++;
        }
        insertStatLines(lore, aggregatedStats, statLineDefinitions);
        if (writeCustomName) {
            itemMeta.customName(MiniMessages.parse(currentName));
        }
        itemMeta.lore(lore.isEmpty() ? null : lore);
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
                if (contribution == null || Texts.isBlank(contribution.statId())) {
                    continue;
                }
                result.merge(normalizeId(contribution.statId()), contribution.amount(), Double::sum);
            }
        }
        return result;
    }

    private List<EmakiPresentationEntry> flattenPresentation(Collection<EmakiItemLayerSnapshot> snapshots) {
        List<EmakiPresentationEntry> entries = new ArrayList<>();
        if (snapshots == null) {
            return entries;
        }
        for (EmakiItemLayerSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.presentation() == null) {
                continue;
            }
            List<EmakiPresentationEntry> orderedEntries = new ArrayList<>(snapshot.presentation());
            orderedEntries.sort(Comparator.comparingInt(EmakiPresentationEntry::sequenceOrder)
                    .thenComparing(EmakiPresentationEntry::entryType)
                    .thenComparing(EmakiPresentationEntry::contentTemplate));
            entries.addAll(orderedEntries);
        }
        return entries;
    }

    private void insertStatLines(List<Component> lore,
            Map<String, Double> aggregatedStats,
            Map<String, StatLineDefinition> statLineDefinitions) {
        if (lore == null || aggregatedStats == null || aggregatedStats.isEmpty() || statLineDefinitions.isEmpty()) {
            return;
        }
        Map<String, List<StatLineDefinition>> grouped = new LinkedHashMap<>();
        List<StatLineDefinition> definitions = new ArrayList<>(statLineDefinitions.values());
        definitions.sort(Comparator.comparingInt(StatLineDefinition::globalSequence));
        for (StatLineDefinition definition : definitions) {
            Double value = aggregatedStats.get(normalizeId(definition.statId()));
            if (value == null || Math.abs(value) <= ZERO_EPSILON) {
                continue;
            }
            grouped.computeIfAbsent(definition.entry().searchPattern(), key -> new ArrayList<>()).add(definition);
        }
        for (Map.Entry<String, List<StatLineDefinition>> group : grouped.entrySet()) {
            int insertIndex = findInsertIndex(lore, group.getKey());
            for (StatLineDefinition definition : group.getValue()) {
                lore.add(insertIndex++, MiniMessages.parse(formatPlaceholders(definition.entry().contentTemplate(), aggregatedStats)));
            }
        }
    }

    private void applyLoreEntry(List<Component> lore,
            EmakiPresentationEntry entry,
            Map<String, Double> aggregatedStats,
            UUID feedbackPlayerId,
            AssemblyFeedbackHandler feedbackHandler) {
        if (lore == null || entry == null) {
            return;
        }
        String rendered = formatPlaceholders(entry.contentTemplate(), aggregatedStats);
        switch (Texts.lower(entry.entryType())) {
            case "lore_append" ->
                lore.add(MiniMessages.parse(rendered));
            case "lore_prepend" ->
                lore.add(0, MiniMessages.parse(rendered));
            case "lore_insert_below" ->
                lore.add(findInsertIndex(lore, entry.searchPattern()), MiniMessages.parse(rendered));
            case "lore_insert_above" ->
                lore.add(findInsertIndexAbove(lore, entry.searchPattern()), MiniMessages.parse(rendered));
            case "lore_replace_line" ->
                replaceLine(lore, entry.searchPattern(), rendered);
            case "lore_delete_line" ->
                deleteLine(lore, entry.searchPattern());
            case "lore_regex_replace" ->
                replaceRegexInLore(lore, entry.searchPattern(), entry.contentTemplate(), aggregatedStats);
            case "lore_search_insert" ->
                applySearchInsertEntry(lore, entry, aggregatedStats, feedbackPlayerId, feedbackHandler);
            default -> {
            }
        }
    }

    private void applySearchInsertEntry(List<Component> lore,
            EmakiPresentationEntry entry,
            Map<String, Double> aggregatedStats,
            UUID feedbackPlayerId,
            AssemblyFeedbackHandler feedbackHandler) {
        SearchInsertConfig config;
        try {
            config = SearchInsertConfig.fromJson(entry.searchPattern());
        } catch (SearchInsertValidationException exception) {
            if (exception.reason() == SearchInsertValidationException.Reason.INVALID_REGEX) {
                feedbackHandler.onLoreInvalidRegex(feedbackPlayerId, entry, null, exception.getMessage());
            } else {
                feedbackHandler.onLoreInvalidConfig(feedbackPlayerId, entry, exception.getMessage());
            }
            return;
        }
        LoreSearchInsertResult result = loreSearchInsertProcessor.apply(
                serializeLore(lore),
                config,
                template -> formatPlaceholders(template, aggregatedStats)
        );
        if (result.mutationApplied()) {
            replaceLore(lore, result.modifiedLore());
        }
        if (result.executionStatus() == LoreSearchInsertStatus.ERROR_NOT_FOUND) {
            feedbackHandler.onLoreSearchNotFound(feedbackPlayerId, entry, config);
        } else if (result.executionStatus() == LoreSearchInsertStatus.INVALID_REGEX) {
            feedbackHandler.onLoreInvalidRegex(feedbackPlayerId, entry, config, result.statusMessage());
        }
    }

    private int findInsertIndex(List<Component> lore, String anchor) {
        if (Texts.isBlank(anchor)) {
            return lore.size();
        }
        for (int index = 0; index < lore.size(); index++) {
            if (MiniMessages.plain(lore.get(index)).contains(anchor)) {
                return index + 1;
            }
        }
        return lore.size();
    }

    private int findInsertIndexAbove(List<Component> lore, String anchor) {
        if (Texts.isBlank(anchor)) {
            return 0;
        }
        for (int index = 0; index < lore.size(); index++) {
            if (MiniMessages.plain(lore.get(index)).contains(anchor)) {
                return index;
            }
        }
        return lore.size();
    }

    private void replaceLine(List<Component> lore, String anchor, String replacement) {
        for (int index = 0; index < lore.size(); index++) {
            if (!MiniMessages.plain(lore.get(index)).contains(anchor)) {
                continue;
            }
            lore.set(index, MiniMessages.parse(replacement));
            return;
        }
    }

    private void deleteLine(List<Component> lore, String anchor) {
        for (int index = lore.size() - 1; index >= 0; index--) {
            if (MiniMessages.plain(lore.get(index)).contains(anchor)) {
                lore.remove(index);
            }
        }
    }

    private void replaceRegexInLore(List<Component> lore,
            String regex,
            String replacement,
            Map<String, Double> aggregatedStats) {
        if (Texts.isBlank(regex)) {
            return;
        }
        for (int index = 0; index < lore.size(); index++) {
            String plain = MiniMessages.plain(lore.get(index));
            lore.set(index, MiniMessages.parse(replaceRegex(plain, regex, replacement, aggregatedStats)));
        }
    }

    private String replaceRegex(String text, String regex, String replacement, Map<String, Double> variables) {
        if (Texts.isBlank(regex)) {
            return Texts.toStringSafe(text);
        }
        try {
            Pattern pattern = regexCache.computeIfAbsent(regex, Pattern::compile);
            Matcher matcher = pattern.matcher(Texts.toStringSafe(text));
            String template = replacement == null ? "" : replacement;
            if (template.indexOf('[') < 0 || template.indexOf(']') < 0) {
                return matcher.replaceAll(ExpressionEngine.replaceVariables(template, variables));
            }
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String resolved = resolveExpressionReplacement(template, matcher, variables);
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(resolved));
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        } catch (Exception ignored) {
            return Texts.toStringSafe(text);
        }
    }

    private String resolveExpressionReplacement(String template, Matcher matcher, Map<String, Double> variables) {
        String withExpressions = resolveExpressionBlocks(template, matcher, variables);
        return replaceCaptureGroups(ExpressionEngine.replaceVariables(withExpressions, variables), matcher);
    }

    private String resolveExpressionBlocks(String template, Matcher matcher, Map<String, Double> variables) {
        Matcher blockMatcher = EXPRESSION_BLOCK_PATTERN.matcher(Texts.toStringSafe(template));
        StringBuffer buffer = new StringBuffer();
        while (blockMatcher.find()) {
            String evaluated = ExpressionEngine.evaluateExpressionBlock(blockMatcher.group(1), matcher, variables);
            blockMatcher.appendReplacement(buffer, Matcher.quoteReplacement("[" + evaluated + "]"));
        }
        blockMatcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replaceCaptureGroups(String template, Matcher matcher) {
        if (matcher == null || Texts.isBlank(template)) {
            return Texts.toStringSafe(template);
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < template.length(); index++) {
            char current = template.charAt(index);
            if (current == '$' && index + 1 < template.length()) {
                char next = template.charAt(index + 1);
                if (Character.isDigit(next)) {
                    int group = Character.digit(next, 10);
                    if (group >= 0 && group <= matcher.groupCount()) {
                        String value = matcher.group(group);
                        if (value != null) {
                            builder.append(value);
                        }
                        index++;
                        continue;
                    }
                }
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private String formatPlaceholders(String template, Map<String, Double> aggregatedStats) {
        if (Texts.isBlank(template)) {
            return "";
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String statId = normalizeId(matcher.group(1));
            double value = aggregatedStats == null ? 0D : aggregatedStats.getOrDefault(statId, 0D);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(Numbers.formatNumber(value, "0.##")));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String firstPlaceholder(String template) {
        if (Texts.isBlank(template)) {
            return "";
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        return matcher.find() ? normalizeId(matcher.group(1)) : "";
    }

    private String resolveInitialName(ItemStack itemStack, ItemMeta itemMeta) {
        if (itemMeta != null && itemMeta.hasCustomName()) {
            return MiniMessages.serialize(itemMeta.customName());
        }
        if (itemStack != null) {
            try {
                return MiniMessages.serialize(itemStack.effectiveName());
            } catch (Exception ignored) {
                return "";
            }
        }
        return "";
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private List<String> serializeLore(List<Component> lore) {
        List<String> serialized = new ArrayList<>();
        if (lore == null) {
            return serialized;
        }
        for (Component component : lore) {
            serialized.add(MiniMessages.serialize(component));
        }
        return serialized;
    }

    private void replaceLore(List<Component> lore, List<String> serializedLore) {
        lore.clear();
        if (serializedLore == null || serializedLore.isEmpty()) {
            return;
        }
        for (String line : serializedLore) {
            lore.add(MiniMessages.parse(line));
        }
    }

    private record StatLineDefinition(String statId, EmakiPresentationEntry entry, int globalSequence) {

    }
}

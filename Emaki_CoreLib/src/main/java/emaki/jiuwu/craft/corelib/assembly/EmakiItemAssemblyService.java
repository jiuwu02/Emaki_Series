package emaki.jiuwu.craft.corelib.assembly;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class EmakiItemAssemblyService {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^{}]+)}");
    private static final double ZERO_EPSILON = 1.0E-9D;

    private final PdcService pdcService = new PdcService("emaki");
    private final PdcPartition itemPartition = pdcService.partition("item");
    private final EmakiNamespaceRegistry namespaceRegistry;
    private final EmakiItemLayerCodecRegistry codecRegistry;
    private final ItemSourceService itemSourceService;

    public EmakiItemAssemblyService(EmakiNamespaceRegistry namespaceRegistry,
                                    EmakiItemLayerCodecRegistry codecRegistry,
                                    ItemSourceService itemSourceService) {
        this.namespaceRegistry = Objects.requireNonNull(namespaceRegistry, "namespaceRegistry");
        this.codecRegistry = Objects.requireNonNull(codecRegistry, "codecRegistry");
        this.itemSourceService = Objects.requireNonNull(itemSourceService, "itemSourceService");
    }

    public ItemStack preview(EmakiItemAssemblyRequest request) {
        AssemblyContext context = resolveContext(request);
        if (context == null || context.baseSource() == null) {
            return null;
        }
        ItemStack itemStack = itemSourceService.createItem(context.baseSource(), context.amount());
        if (itemStack == null) {
            return null;
        }
        renderItem(itemStack, context);
        writeAssemblyData(itemStack, context);
        return itemStack;
    }

    public ItemStack rebuild(ItemStack itemStack) {
        if (!isEmakiItem(itemStack)) {
            return itemStack == null ? null : itemStack.clone();
        }
        return preview(new EmakiItemAssemblyRequest(null, 0, itemStack, List.of()));
    }

    public ItemStack give(Player player, EmakiItemAssemblyRequest request) {
        ItemStack itemStack = preview(request);
        if (player == null || itemStack == null) {
            return itemStack;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack.clone());
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return itemStack;
    }

    public boolean isEmakiItem(ItemStack itemStack) {
        return pdcService.has(itemStack, itemPartition, "schema_version", PersistentDataType.INTEGER)
            && pdcService.has(itemStack, itemPartition, "base_source", PersistentDataType.STRING);
    }

    public ItemSource readBaseSource(ItemStack itemStack) {
        String shorthand = pdcService.get(itemStack, itemPartition, "base_source", PersistentDataType.STRING);
        return Texts.isBlank(shorthand) ? null : ItemSourceUtil.parseShorthand(shorthand);
    }

    public int readBaseAmount(ItemStack itemStack) {
        Integer amount = pdcService.get(itemStack, itemPartition, "base_amount", PersistentDataType.INTEGER);
        return amount == null || amount <= 0 ? 1 : amount;
    }

    public List<String> readActiveLayers(ItemStack itemStack) {
        String raw = pdcService.get(itemStack, itemPartition, "active_layers", PersistentDataType.STRING);
        if (Texts.isBlank(raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String entry : raw.split(",")) {
            String normalized = normalizeId(entry);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return result.isEmpty() ? List.of() : namespaceRegistry.orderNamespaces(result);
    }

    public Map<String, EmakiItemLayerSnapshot> readLayerSnapshots(ItemStack itemStack) {
        if (itemStack == null) {
            return Map.of();
        }
        Map<String, EmakiItemLayerSnapshot> result = new LinkedHashMap<>();
        for (String namespaceId : readActiveLayers(itemStack)) {
            EmakiItemLayerSnapshot snapshot = readLayerSnapshot(itemStack, namespaceId);
            if (snapshot != null) {
                result.put(namespaceId, snapshot);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    public EmakiItemLayerSnapshot readLayerSnapshot(ItemStack itemStack, String namespaceId) {
        if (itemStack == null || Texts.isBlank(namespaceId)) {
            return null;
        }
        String field = normalizeId(namespaceId) + ".snapshot";
        return pdcService.readBlob(itemStack, pdcService.partition(""), field, codecRegistry.codecFor(namespaceId));
    }

    private AssemblyContext resolveContext(EmakiItemAssemblyRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, EmakiItemLayerSnapshot> mergedLayers = new LinkedHashMap<>();
        ItemSource baseSource = request.baseSource();
        int amount = request.amount() > 0 ? request.amount() : 1;
        if (request.existingItem() != null && isEmakiItem(request.existingItem())) {
            if (baseSource == null) {
                baseSource = readBaseSource(request.existingItem());
            }
            if (request.amount() <= 0) {
                amount = readBaseAmount(request.existingItem());
            }
            mergedLayers.putAll(readLayerSnapshots(request.existingItem()));
        }
        if (request.layerSnapshots() != null) {
            for (EmakiItemLayerSnapshot snapshot : request.layerSnapshots()) {
                if (snapshot == null || Texts.isBlank(snapshot.namespaceId())) {
                    continue;
                }
                mergedLayers.put(normalizeId(snapshot.namespaceId()), snapshot);
            }
        }
        if (baseSource == null && request.existingItem() != null && !request.existingItem().getType().isAir()) {
            baseSource = itemSourceService.identifyItem(request.existingItem());
        }
        if (baseSource == null) {
            return null;
        }
        List<String> activeLayers = namespaceRegistry.orderNamespaces(mergedLayers.keySet());
        Map<String, EmakiItemLayerSnapshot> orderedLayers = new LinkedHashMap<>();
        for (String namespaceId : activeLayers) {
            EmakiItemLayerSnapshot snapshot = mergedLayers.get(namespaceId);
            if (snapshot != null) {
                orderedLayers.put(namespaceId, snapshot);
            }
        }
        String signature = SignatureUtil.stableSignature(List.of(
            ItemSourceUtil.toShorthand(baseSource),
            amount,
            orderedLayers.values().stream().map(EmakiItemLayerSnapshot::toMap).toList()
        ));
        return new AssemblyContext(baseSource, Math.max(1, amount), orderedLayers, activeLayers, signature);
    }

    private void renderItem(ItemStack itemStack, AssemblyContext context) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        Map<String, Double> aggregatedStats = aggregateStats(context.layerSnapshots().values());
        boolean writeCustomName = itemMeta.hasCustomName();
        String currentName = resolveInitialName(itemStack, itemMeta);
        List<Component> lore = new ArrayList<>(itemMeta.hasLore() && itemMeta.lore() != null ? itemMeta.lore() : List.<Component>of());
        Map<String, StatLineDefinition> statLineDefinitions = new LinkedHashMap<>();
        int globalSequence = 0;
        for (EmakiPresentationEntry entry : flattenPresentation(context.layerSnapshots().values())) {
            if (entry == null) {
                continue;
            }
            switch (Texts.lower(entry.type())) {
                case "name_prepend", "name_prepend_prefix" -> {
                    currentName = entry.template() + currentName;
                    writeCustomName = true;
                }
                case "name_append", "name_append_suffix" -> {
                    currentName = currentName + entry.template();
                    writeCustomName = true;
                }
                case "name_replace" -> {
                    currentName = entry.template();
                    writeCustomName = true;
                }
                case "name_regex_replace" -> {
                    currentName = replaceRegex(currentName, entry.anchor(), entry.template());
                    writeCustomName = true;
                }
                case "stat_line", "lore_stat_line" -> {
                    String statId = Texts.isBlank(entry.sourceId()) ? firstPlaceholder(entry.template()) : normalizeId(entry.sourceId());
                    if (Texts.isNotBlank(statId)) {
                        statLineDefinitions.put(statId, new StatLineDefinition(statId, entry, globalSequence));
                    }
                }
                default -> applyLoreEntry(lore, entry, aggregatedStats);
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

    private void writeAssemblyData(ItemStack itemStack, AssemblyContext context) {
        pdcService.set(itemStack, itemPartition, "schema_version", PersistentDataType.INTEGER, CURRENT_SCHEMA_VERSION);
        pdcService.set(itemStack, itemPartition, "base_source", PersistentDataType.STRING, ItemSourceUtil.toShorthand(context.baseSource()));
        pdcService.set(itemStack, itemPartition, "base_amount", PersistentDataType.INTEGER, context.amount());
        pdcService.set(itemStack, itemPartition, "active_layers", PersistentDataType.STRING, String.join(",", context.activeLayers()));
        pdcService.set(itemStack, itemPartition, "assembly_signature", PersistentDataType.STRING, context.assemblySignature());
        for (EmakiItemLayerSnapshot snapshot : context.layerSnapshots().values()) {
            if (snapshot == null) {
                continue;
            }
            String field = normalizeId(snapshot.namespaceId()) + ".snapshot";
            pdcService.writeBlob(itemStack, pdcService.partition(""), field, codecRegistry.codecFor(snapshot.namespaceId()), snapshot);
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
            orderedEntries.sort(Comparator.comparingInt(EmakiPresentationEntry::sequence)
                .thenComparing(EmakiPresentationEntry::type)
                .thenComparing(EmakiPresentationEntry::template));
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
            grouped.computeIfAbsent(definition.entry().anchor(), key -> new ArrayList<>()).add(definition);
        }
        for (Map.Entry<String, List<StatLineDefinition>> group : grouped.entrySet()) {
            int insertIndex = findInsertIndex(lore, group.getKey());
            for (StatLineDefinition definition : group.getValue()) {
                lore.add(insertIndex++, MiniMessages.parse(formatPlaceholders(definition.entry().template(), aggregatedStats)));
            }
        }
    }

    private void applyLoreEntry(List<Component> lore, EmakiPresentationEntry entry, Map<String, Double> aggregatedStats) {
        if (lore == null || entry == null) {
            return;
        }
        String rendered = formatPlaceholders(entry.template(), aggregatedStats);
        switch (Texts.lower(entry.type())) {
            case "lore_append", "append", "append_line", "append_lines" -> lore.add(MiniMessages.parse(rendered));
            case "lore_prepend", "prepend_line", "prepend_lines", "insert_first" -> lore.add(0, MiniMessages.parse(rendered));
            case "lore_insert_below", "insert_below" -> lore.add(findInsertIndex(lore, entry.anchor()), MiniMessages.parse(rendered));
            case "lore_insert_above", "insert_above" -> lore.add(findInsertIndexAbove(lore, entry.anchor()), MiniMessages.parse(rendered));
            case "lore_replace_line", "replace_line" -> replaceLine(lore, entry.anchor(), rendered);
            case "lore_delete_line", "delete_line" -> deleteLine(lore, entry.anchor());
            case "lore_regex_replace", "regex_replace" -> replaceRegexInLore(lore, entry.anchor(), rendered, aggregatedStats);
            default -> {
            }
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
            lore.set(index, MiniMessages.parse(formatPlaceholders(replaceRegex(plain, regex, replacement), aggregatedStats)));
        }
    }

    private String replaceRegex(String text, String regex, String replacement) {
        if (Texts.isBlank(regex)) {
            return Texts.toStringSafe(text);
        }
        try {
            return Texts.toStringSafe(text).replaceAll(regex, replacement == null ? "" : replacement);
        } catch (Exception ignored) {
            return Texts.toStringSafe(text);
        }
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

    private record AssemblyContext(ItemSource baseSource,
                                   int amount,
                                   Map<String, EmakiItemLayerSnapshot> layerSnapshots,
                                   List<String> activeLayers,
                                   String assemblySignature) {
    }

    private record StatLineDefinition(String statId, EmakiPresentationEntry entry, int globalSequence) {
    }
}

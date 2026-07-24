package emaki.jiuwu.craft.forge.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateParser;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader.LoadedYamlEntry;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;

public final class ForgeGuiTemplateLoader extends GuiTemplateLoader {
    private final ItemIdentifierService itemIdentifierService;
    private final boolean deferRuntimeValidation;

    public ForgeGuiTemplateLoader(EmakiForgePlugin plugin, ItemIdentifierService itemIdentifierService) {
        this(plugin, itemIdentifierService, false);
    }

    public ForgeGuiTemplateLoader(EmakiForgePlugin plugin,
            ItemIdentifierService itemIdentifierService,
            boolean deferRuntimeValidation) {
        super(plugin);
        this.itemIdentifierService = itemIdentifierService;
        this.deferRuntimeValidation = deferRuntimeValidation;
    }

    @Override
    protected GuiTemplate parse(File file, YamlSection configuration) {
        if (deferRuntimeValidation) {
            return GuiTemplateParser.parse(configuration);
        }
        return parsePrioritized(configuration);
    }

    public void completeDeferredRuntimeValidation() {
        if (!deferRuntimeValidation) {
            return;
        }
        synchronized (stateLock) {
            Map<String, LoadedYamlEntry<GuiTemplate>> parsedEntries = new LinkedHashMap<>(loadedEntries);
            items.clear();
            loadedEntries.clear();
            for (LoadedYamlEntry<GuiTemplate> entry : parsedEntries.values()) {
                GuiTemplate template = parsePrioritized(entry.configuration());
                if (template == null) {
                    continue;
                }
                items.put(entry.id(), template);
                loadedEntries.put(entry.id(), new LoadedYamlEntry<>(
                        entry.id(), entry.file(), entry.configuration().copy(), template));
            }
        }
    }

    private GuiTemplate parsePrioritized(YamlSection configuration) {
        YamlSection effective = configuration == null ? null : configuration.copy();
        if (effective == null || itemIdentifierService == null) {
            return GuiTemplateParser.parse(configuration);
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ConfigNodes.entries(effective.get("slots")).entrySet()) {
            Map<String, Object> slot = new LinkedHashMap<>(ConfigNodes.entries(entry.getValue()));
            if (slot.containsKey("item")) {
                slot.put("item", prioritizeItem(slot.get("item")));
            }
            if (slot.containsKey("item_sources")) {
                slot.put("item_sources", prioritizeAlternatives(slot.get("item_sources")));
            }
            slots.put(entry.getKey(), slot);
        }
        effective.set("slots", slots);
        return GuiTemplateParser.parse(effective);
    }

    private Object prioritizeItem(Object raw) {
        Map<String, Object> item = new LinkedHashMap<>(ConfigNodes.entries(raw));
        if (item.isEmpty()) {
            return ConfigNodes.toPlainData(raw);
        }
        if (item.containsKey("item_sources")) {
            item.put("item_sources", prioritizeAlternatives(item.get("item_sources")));
        }
        Object source = item.get("source");
        if (source instanceof List<?>) {
            List<Object> prioritized = prioritizeAlternatives(source);
            if (!prioritized.isEmpty()) {
                ItemSource selected = ItemSourceUtil.parse(prioritized.get(0));
                item.put("source", selected == null ? prioritized.get(0) : ItemSourceUtil.toShorthand(selected));
            }
        }
        return item;
    }

    private List<Object> prioritizeAlternatives(Object raw) {
        List<Object> ready = new ArrayList<>();
        List<Object> unavailable = new ArrayList<>();
        List<Object> alternatives = ConfigNodes.asObjectList(raw);
        if (alternatives.isEmpty() && raw != null) {
            alternatives = List.of(raw);
        }
        for (Object alternative : alternatives) {
            ItemSource source = ItemSourceUtil.parse(alternative);
            Object plain = ConfigNodes.toPlainData(alternative);
            if (itemIdentifierService.probeSource(source).ready()) {
                ready.add(plain);
            } else {
                unavailable.add(plain);
            }
        }
        ready.addAll(unavailable);
        return ready;
    }
}

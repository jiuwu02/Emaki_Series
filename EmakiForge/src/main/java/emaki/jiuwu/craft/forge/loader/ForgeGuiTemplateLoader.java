package emaki.jiuwu.craft.forge.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateParser;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.loader.RecipeLoader.CandidateDocument;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;

public final class ForgeGuiTemplateLoader extends GuiTemplateLoader {
    public record CandidateIssue(File file, String code, String summary) {
    }

    private final ItemIdentifierService itemIdentifierService;
    private final boolean deferRuntimeValidation;
    private final List<CandidateIssue> candidateIssues = new ArrayList<>();

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

    public void prepareCandidateFiles(File directory, List<File> files) {
        synchronized (stateLock) {
            items.clear();
            loadedEntries.clear();
            issues.clear();
            candidateIssues.clear();
            loaded = false;
        }
    }

    public List<CandidateIssue> candidateIssues() {
        synchronized (stateLock) {
            return List.copyOf(candidateIssues);
        }
    }

    public void loadCandidateDocuments(List<CandidateDocument> documents) {
        synchronized (stateLock) {
            items.clear();
            loadedEntries.clear();
            loaded = false;
            if (documents != null) {
                for (CandidateDocument document : documents) {
                    if (document == null || document.file() == null) {
                        continue;
                    }
                    if (document.failure() != null) {
                        onLoadFailure(document.file(), document.failure());
                        recordCandidateIssue(document.file(), "GUI_LOAD_FAILED",
                                "GUI template file load failed: " + failureSummary(document.failure()));
                        continue;
                    }
                    try {
                        YamlSection configuration = document.content() == null
                                ? new MapYamlSection()
                                : YamlFiles.load(document.content());
                        GuiTemplate value = parse(document.file(), configuration);
                        if (value == null) {
                            recordCandidateIssue(document.file(), "GUI_INVALID_CONFIG",
                                    "GUI template configuration could not be parsed.");
                            continue;
                        }
                        String id = idOf(value);
                        if (Texts.isBlank(id)) {
                            onBlankId(document.file());
                            recordCandidateIssue(document.file(), "GUI_BLANK_ID",
                                    "GUI template id cannot be blank.");
                            continue;
                        }
                        if (items.containsKey(id)) {
                            onDuplicateId(document.file(), id);
                            recordCandidateIssue(document.file(), "GUI_DUPLICATE_ID",
                                    "Duplicate GUI template id '" + id + "'.");
                            continue;
                        }
                        items.put(id, value);
                        loadedEntries.put(id, new LoadedYamlEntry<>(
                                id,
                                document.file(),
                                configuration,
                                value));
                    } catch (Exception exception) {
                        onLoadFailure(document.file(), exception);
                        recordCandidateIssue(document.file(), "GUI_LOAD_FAILED",
                                "GUI template file load failed: " + failureSummary(exception));
                    }
                }
            }
            loaded = true;
        }
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
                try {
                    GuiTemplate template = parsePrioritized(entry.configuration());
                    if (template == null) {
                        recordCandidateIssue(entry.file(), "GUI_FINALIZATION_FAILED",
                                "GUI template could not be finalized after source validation.");
                        continue;
                    }
                    items.put(entry.id(), template);
                    loadedEntries.put(entry.id(), new LoadedYamlEntry<>(
                            entry.id(), entry.file(), entry.configuration(), template));
                } catch (RuntimeException | LinkageError failure) {
                    recordCandidateIssue(entry.file(), "GUI_FINALIZATION_FAILED",
                            "GUI template finalization failed: " + failureSummary(failure));
                }
            }
        }
    }

    private void recordCandidateIssue(File file, String code, String summary) {
        candidateIssues.add(new CandidateIssue(file, Texts.toStringSafe(code), Texts.toStringSafe(summary)));
    }

    private String failureSummary(Throwable throwable) {
        if (throwable == null) {
            return "unknown failure";
        }
        String message = Texts.toStringSafe(throwable.getMessage()).trim();
        return message.isEmpty() ? throwable.getClass().getSimpleName() : message;
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
                ItemSourceRef selected = ItemSourceUtil.parse(prioritized.get(0));
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
            ItemSourceRef source = ItemSourceUtil.parse(alternative);
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

package emaki.jiuwu.craft.forge.loader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionEngine;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceProbeStatus;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;

public final class RecipeLoader extends YamlDirectoryLoader<Recipe> {

    public enum IssueSeverity {
        ERROR,
        WARNING,
        INFO;

        public boolean blocking() {
            return this == ERROR;
        }
    }

    public record RecipeLoadIssue(long generation,
                                  String recipeId,
                                  String filePath,
                                  String yamlPath,
                                  IssueSeverity severity,
                                  String code,
                                  String summary,
                                  boolean skipped,
                                  String source,
                                  String provider,
                                  ItemSourceProbeStatus sourceStatus) {

        public RecipeLoadIssue {
            recipeId = Texts.toStringSafe(recipeId);
            filePath = Texts.toStringSafe(filePath);
            yamlPath = Texts.toStringSafe(yamlPath);
            severity = severity == null ? IssueSeverity.ERROR : severity;
            code = Texts.toStringSafe(code);
            summary = Texts.toStringSafe(summary);
            source = Texts.toStringSafe(source);
            provider = Texts.toStringSafe(provider);
        }

        public boolean blocking() {
            return severity.blocking();
        }

        public boolean capabilityIssue() {
            return sourceStatus == ItemSourceProbeStatus.RESOLVER_MISSING
                    || sourceStatus == ItemSourceProbeStatus.PROVIDER_NOT_READY
                    || sourceStatus == ItemSourceProbeStatus.INCOMPATIBLE;
        }
    }

    public record RecipeLoadReport(long generation,
                                   int discovered,
                                   int parsed,
                                   int skipped,
                                   int registered,
                                   int duplicates,
                                   int executable,
                                   int guiVisible,
                                   Map<String, Integer> inputSourceTypes,
                                   Map<String, Integer> outputSourceTypes,
                                   Map<ItemSourceProbeStatus, Integer> sourceStatuses,
                                   List<RecipeLoadIssue> issues,
                                   String directoryPath,
                                   String fileSummaryHash,
                                   long durationNanos) {

        public RecipeLoadReport {
            inputSourceTypes = inputSourceTypes == null ? Map.of() : Map.copyOf(inputSourceTypes);
            outputSourceTypes = outputSourceTypes == null ? Map.of() : Map.copyOf(outputSourceTypes);
            sourceStatuses = sourceStatuses == null ? Map.of() : Map.copyOf(sourceStatuses);
            issues = issues == null ? List.of() : List.copyOf(issues);
            directoryPath = Texts.toStringSafe(directoryPath);
            fileSummaryHash = Texts.toStringSafe(fileSummaryHash);
            durationNanos = Math.max(0L, durationNanos);
        }

        public static RecipeLoadReport empty(long generation) {
            return new RecipeLoadReport(generation, 0, 0, 0, 0, 0, 0, 0,
                    Map.of(), Map.of(), Map.of(), List.of(), "", "", 0L);
        }

        public boolean hasBlockingIssues() {
            return issues.stream().anyMatch(RecipeLoadIssue::blocking);
        }

        public boolean hasBlockingCapabilityIssues() {
            return issues.stream().anyMatch(issue -> issue.blocking() && issue.capabilityIssue());
        }

        public boolean hasWarnings() {
            return issues.stream().anyMatch(issue -> issue.severity() == IssueSeverity.WARNING);
        }

        public int issueCount() {
            return issues.size();
        }

        public int warningCount() {
            return (int) issues.stream().filter(issue -> issue.severity() == IssueSeverity.WARNING).count();
        }

        public int sourceStatusCount() {
            return sourceStatuses.values().stream().mapToInt(Integer::intValue).sum();
        }

        public String summary() {
            return "generation=" + generation
                    + " files=" + discovered
                    + " parsed=" + parsed
                    + " skipped=" + skipped
                    + " registered=" + registered
                    + " duplicates=" + duplicates
                    + " issues=" + issueCount()
                    + " warnings=" + warningCount()
                    + " hash=" + fileSummaryHash
                    + " duration_ms=" + String.format(java.util.Locale.ROOT, "%.3f", durationNanos / 1_000_000D);
        }
    }

    private static final List<String> SUPPORTED_CONDITION_TYPES = List.of("all_of", "any_of", "none_of", "at_least", "exactly");

    public record CandidateDocument(File file, String content, Exception failure) {
    }

    private record DeferredRecipe(File file, YamlSection configuration, String recipeId) {
    }

    private record DeferredSourceValidation(File file,
            String recipeId,
            String yamlPath,
            Object raw,
            boolean required,
            Map<String, Integer> typeDistribution) {
    }

    private final EmakiForgePlugin forgePlugin;
    private final Supplier<ActionEngine> actionEngineSupplier;
    private final ItemIdentifierService itemIdentifierService;
    private final boolean deferRuntimeValidation;
    private final List<RecipeLoadIssue> structuredIssues = new ArrayList<>();
    private final Map<String, Integer> inputSourceTypes = new LinkedHashMap<>();
    private final Map<String, Integer> outputSourceTypes = new LinkedHashMap<>();
    private final Map<ItemSourceProbeStatus, Integer> sourceStatuses = new LinkedHashMap<>();
    private final List<File> discoveredFiles = new ArrayList<>();
    private final List<DeferredRecipe> deferredRecipes = new ArrayList<>();
    private final List<DeferredSourceValidation> deferredSourceValidations = new ArrayList<>();
    private long generation;
    private long startedNanos;
    private int parsed;
    private int skipped;
    private int duplicates;
    private boolean completingDeferredRuntimeValidation;
    private String candidateFileSummaryHash = "";
    private volatile RecipeLoadReport report = RecipeLoadReport.empty(0L);

    public RecipeLoader(EmakiForgePlugin plugin,
                        Supplier<ActionEngine> actionEngineSupplier) {
        this(plugin, actionEngineSupplier, plugin == null ? null : plugin.itemIdentifierService(), false);
    }

    public RecipeLoader(EmakiForgePlugin plugin,
                        Supplier<ActionEngine> actionEngineSupplier,
                        ItemIdentifierService itemIdentifierService) {
        this(plugin, actionEngineSupplier, itemIdentifierService, false);
    }

    /**
     * Creates a loader.
     *
     * <p>The engine arrives as a supplier rather than a value because a CoreLib reload replaces it; reading
     * it per validation pass is what keeps this loader from checking recipes against a retired stage table.</p>
     *
     * @param plugin the owning plugin
     * @param actionEngineSupplier reads the live pipeline engine, may yield {@code null} before first load
     * @param itemIdentifierService resolves configured item ids
     * @param deferRuntimeValidation whether runtime-only checks are postponed to a later pass
     */
    public RecipeLoader(EmakiForgePlugin plugin,
                        Supplier<ActionEngine> actionEngineSupplier,
                        ItemIdentifierService itemIdentifierService,
                        boolean deferRuntimeValidation) {
        super(plugin);
        this.forgePlugin = plugin;
        this.actionEngineSupplier = actionEngineSupplier;
        this.itemIdentifierService = itemIdentifierService;
        this.deferRuntimeValidation = deferRuntimeValidation;
    }

    public void prepareLoad(long generation) {
        synchronized (stateLock) {
            this.generation = generation;
            this.startedNanos = System.nanoTime();
            this.parsed = 0;
            this.skipped = 0;
            this.duplicates = 0;
            this.candidateFileSummaryHash = "";
            this.structuredIssues.clear();
            this.inputSourceTypes.clear();
            this.outputSourceTypes.clear();
            this.sourceStatuses.clear();
            this.discoveredFiles.clear();
            this.deferredRecipes.clear();
            this.deferredSourceValidations.clear();
            this.report = RecipeLoadReport.empty(generation);
        }
    }

    public void loadCandidateDocuments(List<CandidateDocument> documents) {
        synchronized (stateLock) {
            items.clear();
            loadedEntries.clear();
            issues.clear();
            loaded = false;
            discoveredFiles.clear();
            candidateFileSummaryHash = summarizeDocuments(documents);
            if (documents != null) {
                for (CandidateDocument document : documents) {
                    if (document == null || document.file() == null) {
                        continue;
                    }
                    discoveredFiles.add(document.file());
                    if (document.failure() != null) {
                        onLoadFailure(document.file(), document.failure());
                        continue;
                    }
                    try {
                        YamlSection configuration = document.content() == null
                                ? new MapYamlSection()
                                : YamlFiles.load(document.content());
                        Recipe value = parse(document.file(), configuration);
                        if (value == null) {
                            continue;
                        }
                        String id = idOf(value);
                        if (Texts.isBlank(id)) {
                            onBlankId(document.file());
                            continue;
                        }
                        if (items.containsKey(id)) {
                            onDuplicateId(document.file(), id);
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
            completingDeferredRuntimeValidation = true;
            try {
                for (DeferredRecipe deferred : List.copyOf(deferredRecipes)) {
                    try {
                        Recipe prioritized = Recipe.fromConfig(prioritizeSourceCandidates(deferred.configuration()));
                        if (prioritized == null) {
                            recordFinalizationFailure(deferred, "Recipe configuration could not be finalized after source validation.");
                            continue;
                        }
                        if (!validateActions(deferred.file(), prioritized)) {
                            items.remove(deferred.recipeId());
                            loadedEntries.remove(deferred.recipeId());
                            parsed = Math.max(0, parsed - 1);
                            skipped++;
                            continue;
                        }
                        items.put(deferred.recipeId(), prioritized);
                        loadedEntries.put(deferred.recipeId(), new LoadedYamlEntry<>(
                                deferred.recipeId(),
                                deferred.file(),
                                deferred.configuration(),
                                prioritized));
                    } catch (RuntimeException | LinkageError failure) {
                        recordFinalizationFailure(deferred,
                                "Recipe finalization failed: " + exceptionSummary(failure));
                    }
                }
                for (DeferredSourceValidation deferred : List.copyOf(deferredSourceValidations)) {
                    try {
                        validateAlternativeGroup(
                                deferred.file(),
                                deferred.recipeId(),
                                deferred.yamlPath(),
                                deferred.raw(),
                                deferred.required(),
                                deferred.typeDistribution());
                    } catch (RuntimeException | LinkageError failure) {
                        recordIssue(deferred.file(), deferred.recipeId(), deferred.yamlPath(), IssueSeverity.ERROR,
                                "SOURCE_VALIDATION_FAILED",
                                "Item source validation failed: " + exceptionSummary(failure),
                                true, null, null);
                    }
                }
            } finally {
                completingDeferredRuntimeValidation = false;
                deferredRecipes.clear();
                deferredSourceValidations.clear();
            }
        }
    }

    public RecipeLoadReport completeReport(GuiTemplateLoader guiTemplateLoader) {
        synchronized (stateLock) {
            boolean previousCompleting = completingDeferredRuntimeValidation;
            if (deferRuntimeValidation) {
                completingDeferredRuntimeValidation = true;
            }
            try {
                mergeGuiCandidateIssues(guiTemplateLoader);
                validateGuiSources(guiTemplateLoader);
            } finally {
                completingDeferredRuntimeValidation = previousCompleting;
            }
            String directoryPath = canonicalPath(new File(forgePlugin.getDataFolder(), directoryName()));
            report = new RecipeLoadReport(
                    generation,
                    discoveredFiles.size(),
                    parsed,
                    skipped,
                    items.size(),
                    duplicates,
                    items.size(),
                    items.size(),
                    inputSourceTypes,
                    outputSourceTypes,
                    sourceStatuses,
                    structuredIssues,
                    directoryPath,
                    Texts.isBlank(candidateFileSummaryHash)
                            ? summarizeFiles(discoveredFiles)
                            : candidateFileSummaryHash,
                    startedNanos == 0L ? 0L : System.nanoTime() - startedNanos
            );
            issues.clear();
            report.issues().stream().map(RecipeLoadIssue::summary).forEach(issues::add);
            return report;
        }
    }

    public RecipeLoadReport report() {
        return report;
    }

    @Override
    protected String directoryName() {
        return "recipes";
    }

    @Override
    protected String typeName() {
        return localized("loader.type.recipe");
    }

    @Override
    protected void beforeFilesLoaded(File directory, List<File> files) {
        discoveredFiles.clear();
        if (files != null) {
            discoveredFiles.addAll(files);
        }
    }

    @Override
    protected Recipe parse(File file, YamlSection configuration) {
        if (configuration == null) {
            recordIssue(file, "", "", IssueSeverity.ERROR, "INVALID_CONFIG",
                    "Recipe configuration is empty.", true, null, null);
            skipped++;
            return null;
        }
        String recipeId = configuration.getString("id");
        if (Texts.isBlank(recipeId)) {
            recordIssue(file, "", "id", IssueSeverity.ERROR, "BLANK_ID",
                    "Recipe id cannot be blank.", true, null, null);
            skipped++;
            return null;
        }
        YamlSection effectiveConfiguration = deferRuntimeValidation
                ? configuration
                : prioritizeSourceCandidates(configuration);
        Recipe recipe = Recipe.fromConfig(effectiveConfiguration);
        if (recipe == null) {
            recordIssue(file, recipeId, "", IssueSeverity.ERROR, "INVALID_CONFIG",
                    "Recipe configuration could not be parsed.", true, null, null);
            skipped++;
            return null;
        }
        if (!SUPPORTED_CONDITION_TYPES.contains(Texts.lower(recipe.conditionType()))) {
            recordIssue(file, recipe.id(), "condition.type", IssueSeverity.ERROR, "INVALID_CONDITION_MODE",
                    "Unsupported condition mode '" + recipe.conditionType() + "'. Allowed: "
                            + String.join(", ", SUPPORTED_CONDITION_TYPES), true, null, null);
            skipped++;
            return null;
        }
        if (!deferRuntimeValidation && !validateActions(file, recipe)) {
            skipped++;
            return null;
        }
        if (deferRuntimeValidation) {
            deferredRecipes.add(new DeferredRecipe(file, configuration, recipe.id()));
        }
        validateRecipeSources(file, configuration, recipe);
        parsed++;
        return recipe;
    }

    public Recipe parseDocument(File file, YamlSection configuration) {
        return parse(file, configuration);
    }

    @Override
    protected String idOf(Recipe value) {
        return value.id();
    }

    @Override
    protected void onBlankId(File file) {
        recordIssue(file, "", "id", IssueSeverity.ERROR, "BLANK_ID",
                "Recipe id cannot be blank.", true, null, null);
        skipped++;
    }

    @Override
    protected void onDuplicateId(File file, String id) {
        duplicates++;
        skipped++;
        recordIssue(file, id, "id", IssueSeverity.ERROR, "DUPLICATE_ID",
                "Duplicate recipe id '" + id + "'.", true, null, null);
    }

    @Override
    protected void onDirectoryCreateFailed(File directory) {
        recordIssue(directory, "", "", IssueSeverity.ERROR, "DIRECTORY_CREATE_FAILED",
                "Unable to create recipe directory.", true, null, null);
    }

    @Override
    protected void onLoadFailure(File file, Exception exception) {
        skipped++;
        recordIssue(file, "", "", IssueSeverity.ERROR, "LOAD_FAILED",
                "Recipe file load failed: " + exceptionSummary(exception), true, null, null);
    }

    @Override
    protected void onPreparationFailure(File directory, RuntimeException exception) {
        recordIssue(directory, "", "", IssueSeverity.ERROR, "PREPARATION_FAILED",
                "Recipe directory preparation failed: " + exceptionSummary(exception), true, null, null);
    }

    public List<Recipe> byPermission(Player player) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : all().values()) {
            if (!recipe.requiresPermission() || (player != null && player.hasPermission(recipe.permission()))) {
                result.add(recipe);
            }
        }
        return result;
    }

    private boolean validateActions(File file, Recipe recipe) {
        if (recipe == null || recipe.action() == null) {
            return recipe != null;
        }
        ActionEngine engine = actionEngineSupplier == null ? null : actionEngineSupplier.get();
        if (engine == null) {
            recordIssue(file, recipe.id(), "actions", IssueSeverity.WARNING, "ACTION_VALIDATION_SKIPPED",
                    "Action validation was skipped because the CoreLib action pipeline is unavailable.",
                    false, null, null);
            return true;
        }
        return validatePhase(file, recipe, "pre", recipe.action().pre(), engine)
                && validatePhase(file, recipe, "result", recipe.result() == null ? List.of() : recipe.result().action(), engine)
                && validatePhase(file, recipe, "success", recipe.action().success(), engine)
                && validatePhase(file, recipe, "failure", recipe.action().failure(), engine);
    }

    /**
     * Rejects a recipe whose action lines do not compile.
     *
     * <p>Compilation replaces what used to be three separate checks against the v1 registries: parse the
     * line, look the action up, then validate its arguments. The pipeline compiler does all three and additionally
     * verifies stage position and referenced sequences, so a recipe that compiles here cannot fail at forge
     * time for a configuration reason.</p>
     *
     * <p>Only the first diagnostic per line is reported. The compiler emits every problem it finds, and the
     * later ones are usually consequences of the first, so surfacing them all would bury the real cause.</p>
     */
    private boolean validatePhase(File file,
                                  Recipe recipe,
                                  String phase,
                                  List<String> lines,
                                  ActionEngine engine) {
        if (lines == null || lines.isEmpty()) {
            return true;
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (Texts.isBlank(line)) {
                continue;
            }
            ActionEngine.Result compiled = engine.compile(line, null);
            if (compiled.successful()) {
                continue;
            }
            String reason = compiled.diagnostics().isEmpty()
                    ? "did not compile"
                    : Texts.toStringSafe(compiled.diagnostics().get(0).reasonKey());
            recordIssue(file, recipe.id(), "actions." + phase + "[" + index + "]", IssueSeverity.ERROR,
                    "INVALID_ACTION_LINE", "Action line did not compile: " + reason, true, null, null);
            return false;
        }
        return true;
    }

    private void validateRecipeSources(File file, YamlSection configuration, Recipe recipe) {
        List<Object> blueprints = ConfigNodes.asObjectList(configuration.get("blueprint_requirements"));
        for (int index = 0; index < blueprints.size(); index++) {
            validateAlternativeGroup(file, recipe.id(), "blueprint_requirements[" + index + "].item_sources",
                    ConfigNodes.get(blueprints.get(index), "item_sources"), true, inputSourceTypes);
        }
        List<Object> materials = ConfigNodes.asObjectList(configuration.get("materials"));
        for (int index = 0; index < materials.size(); index++) {
            validateAlternativeGroup(file, recipe.id(), "materials[" + index + "].item_sources",
                    ConfigNodes.get(materials.get(index), "item_sources"), true, inputSourceTypes);
        }
        Object success = ConfigNodes.get(configuration.get("result"), "success");
        List<Object> outputs = ConfigNodes.asObjectList(ConfigNodes.get(success, "outputs"));
        for (int index = 0; index < outputs.size(); index++) {
            validateAlternativeGroup(file, recipe.id(), "result.success.outputs[" + index + "].item_sources",
                    ConfigNodes.get(outputs.get(index), "item_sources"), true, outputSourceTypes);
        }
    }

    private YamlSection prioritizeSourceCandidates(YamlSection configuration) {
        YamlSection copy = configuration == null ? null : configuration.copy();
        if (copy == null || itemIdentifierService == null) {
            return configuration;
        }
        copy.set("blueprint_requirements", prioritizeContainers(copy.get("blueprint_requirements")));
        copy.set("materials", prioritizeContainers(copy.get("materials")));
        Map<String, Object> result = new LinkedHashMap<>(ConfigNodes.entries(copy.get("result")));
        Map<String, Object> success = new LinkedHashMap<>(ConfigNodes.entries(result.get("success")));
        success.put("outputs", prioritizeContainers(success.get("outputs")));
        result.put("success", success);
        copy.set("result", result);
        return copy;
    }

    private List<Object> prioritizeContainers(Object raw) {
        List<Object> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            Map<String, Object> values = new LinkedHashMap<>(ConfigNodes.entries(entry));
            if (values.containsKey("item_sources")) {
                values.put("item_sources", prioritizeAlternatives(values.get("item_sources")));
            }
            result.add(values.isEmpty() ? ConfigNodes.toPlainData(entry) : values);
        }
        return result;
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

    private void recordFinalizationFailure(DeferredRecipe deferred, String summary) {
        if (deferred == null) {
            return;
        }
        recordIssue(deferred.file(), deferred.recipeId(), "", IssueSeverity.ERROR,
                "RUNTIME_VALIDATION_FAILED", summary, true, null, null);
        items.remove(deferred.recipeId());
        loadedEntries.remove(deferred.recipeId());
        parsed = Math.max(0, parsed - 1);
        skipped++;
    }

    private void mergeGuiCandidateIssues(GuiTemplateLoader guiTemplateLoader) {
        if (!(guiTemplateLoader instanceof ForgeGuiTemplateLoader forgeGuiTemplateLoader)) {
            return;
        }
        for (ForgeGuiTemplateLoader.CandidateIssue issue : forgeGuiTemplateLoader.candidateIssues()) {
            if (issue == null) {
                continue;
            }
            recordIssue(issue.file(), "", "gui", IssueSeverity.ERROR,
                    issue.code(), issue.summary(), true, null, null);
        }
    }

    private void validateGuiSources(GuiTemplateLoader guiTemplateLoader) {
        if (guiTemplateLoader == null) {
            return;
        }
        for (Map.Entry<String, GuiTemplate> entry : guiTemplateLoader.all().entrySet()) {
            String templateId = entry.getKey();
            var loadedEntry = guiTemplateLoader.entry(templateId);
            File file = loadedEntry == null ? null : loadedEntry.file();
            YamlSection configuration = loadedEntry == null ? null : loadedEntry.configuration();
            if (configuration == null) {
                continue;
            }
            for (Map.Entry<String, Object> slotEntry : ConfigNodes.entries(configuration.get("slots")).entrySet()) {
                Object item = ConfigNodes.get(slotEntry.getValue(), "item");
                Object alternatives = guiItemAlternatives(item, slotEntry.getValue());
                if (alternatives == null) {
                    continue;
                }
                String yamlPath = "gui." + templateId + ".slots." + slotEntry.getKey() + ".item";
                try {
                    validateAlternativeGroup(file, "", yamlPath,
                            alternatives, true, new LinkedHashMap<>());
                } catch (RuntimeException | LinkageError failure) {
                    recordIssue(file, "", yamlPath, IssueSeverity.ERROR,
                            "GUI_SOURCE_VALIDATION_FAILED",
                            "GUI item source validation failed: " + exceptionSummary(failure),
                            true, null, null);
                }
            }
        }
    }

    private Object guiItemAlternatives(Object item, Object slot) {
        Object source = ConfigNodes.get(item, "source");
        if (source != null) {
            return source;
        }
        source = ConfigNodes.get(item, "item_sources");
        if (source != null) {
            return source;
        }
        source = ConfigNodes.get(slot, "item_sources");
        if (source != null) {
            return source;
        }
        return item;
    }

    private void validateAlternativeGroup(File file,
                                          String recipeId,
                                          String yamlPath,
                                          Object raw,
                                           boolean required,
                                           Map<String, Integer> typeDistribution) {
        if (deferRuntimeValidation && !completingDeferredRuntimeValidation) {
            deferredSourceValidations.add(new DeferredSourceValidation(
                    file,
                    recipeId,
                    yamlPath,
                    ConfigNodes.toPlainData(raw),
                    required,
                    typeDistribution));
            return;
        }
        List<Object> alternatives = ConfigNodes.asObjectList(raw);
        if (alternatives.isEmpty() && raw != null) {
            alternatives = List.of(raw);
        }
        if (alternatives.isEmpty()) {
            if (required) {
                recordIssue(file, recipeId, yamlPath, IssueSeverity.ERROR, "SOURCE_ALTERNATIVES_EMPTY",
                        "The required item source alternatives are empty.", true, null, null);
            }
            return;
        }
        int ready = 0;
        List<ItemIdentifierService.SourceProbe> probes = new ArrayList<>();
        for (Object alternative : alternatives) {
            ItemSource source = ItemSourceUtil.parse(alternative);
            ItemIdentifierService.SourceProbe probe = itemIdentifierService == null
                    ? new ItemIdentifierService.SourceProbe(source,
                    ItemSourceProbeStatus.RESOLVER_MISSING,
                    "EmakiCoreLib",
                    "Item source probing is unavailable.")
                    : itemIdentifierService.probeSource(source, yamlPath);
            probes.add(probe);
            sourceStatuses.merge(probe.status(), 1, Integer::sum);
            if (source != null && source.getType() != null) {
                typeDistribution.merge(source.getType().name(), 1, Integer::sum);
            }
            if (probe.ready()) {
                ready++;
            }
        }
        for (ItemIdentifierService.SourceProbe probe : probes) {
            if (probe.ready()) {
                continue;
            }
            IssueSeverity severity = ready > 0 ? IssueSeverity.WARNING : IssueSeverity.ERROR;
            String shorthand = probe.source() == null ? Texts.toStringSafe(raw) : ItemSourceUtil.toShorthand(probe.source());
            recordIssue(file, recipeId, yamlPath, severity,
                    ready > 0 ? "SOURCE_ALTERNATIVE_UNAVAILABLE" : "SOURCE_ALTERNATIVES_UNAVAILABLE",
                    "Item source is unavailable: status=" + probe.status()
                            + (Texts.isBlank(probe.detail()) ? "" : ", detail=" + probe.detail()),
                    ready == 0, shorthand, probe);
        }
    }

    private void recordIssue(File file,
                             String recipeId,
                             String yamlPath,
                             IssueSeverity severity,
                             String code,
                             String summary,
                             boolean skipped,
                             String source,
                             ItemIdentifierService.SourceProbe probe) {
        RecipeLoadIssue issue = new RecipeLoadIssue(
                generation,
                recipeId,
                canonicalPath(file),
                yamlPath,
                severity,
                code,
                summary,
                skipped,
                source,
                probe == null ? "" : probe.provider(),
                probe == null ? null : probe.status()
        );
        structuredIssues.add(issue);
        if (severity != IssueSeverity.INFO && forgePlugin != null) {
            forgePlugin.getLogger().warning("[RecipeLoad] " + code
                    + " path=" + issue.filePath()
                    + " yaml=" + issue.yamlPath()
                    + " recipe=" + issue.recipeId()
                    + " detail=" + issue.summary());
        }
    }

    private String summarizeDocuments(List<CandidateDocument> documents) {
        if (documents == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CandidateDocument document : documents) {
                if (document == null || document.file() == null) {
                    continue;
                }
                digest.update(document.file().getPath().getBytes(StandardCharsets.UTF_8));
                if (document.content() != null) {
                    digest.update(document.content().getBytes(StandardCharsets.UTF_8));
                } else if (document.failure() != null) {
                    digest.update(exceptionSummary(document.failure()).getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest(), 0, 8);
        } catch (Exception exception) {
            return "unavailable";
        }
    }

    private String summarizeFiles(List<File> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (File file : files) {
                if (file == null) {
                    continue;
                }
                digest.update(canonicalPath(file).getBytes(StandardCharsets.UTF_8));
                digest.update(Long.toString(file.length()).getBytes(StandardCharsets.UTF_8));
                digest.update(Long.toString(file.lastModified()).getBytes(StandardCharsets.UTF_8));
                if (file.isFile() && file.canRead()) {
                    digest.update(Files.readAllBytes(file.toPath()));
                }
            }
            return HexFormat.of().formatHex(digest.digest(), 0, 8);
        } catch (Exception exception) {
            return "unavailable";
        }
    }

    private String canonicalPath(File file) {
        if (file == null) {
            return "";
        }
        try {
            return file.getCanonicalPath();
        } catch (Exception exception) {
            return file.getAbsolutePath();
        }
    }

    private String exceptionSummary(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = Texts.toStringSafe(throwable.getMessage());
        return throwable.getClass().getSimpleName() + (message.isBlank() ? "" : ": " + message);
    }
}

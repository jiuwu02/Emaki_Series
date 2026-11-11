package emaki.jiuwu.craft.forge.loader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.pipeline.ActionEngine;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProbeState;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
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
                                  ItemSourceProbeState sourceStatus) {

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
            return sourceStatus == ItemSourceProbeState.PROVIDER_MISSING
                    || sourceStatus == ItemSourceProbeState.PROVIDER_NOT_READY
                    || sourceStatus == ItemSourceProbeState.INCOMPATIBLE;
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
                                   Map<ItemSourceProbeState, Integer> sourceStatuses,
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
                    + " duration_ms=" + String.format(Locale.ROOT, "%.3f", durationNanos / 1_000_000D);
        }
    }

    private static final List<String> SUPPORTED_CONDITION_TYPES = List.of("all_of", "any_of", "none_of", "at_least", "exactly");

    public record CandidateDocument(File file, String content, Exception failure) {
    }

    record DeferredRecipe(File file, YamlSection configuration, String recipeId) {
    }

    record DeferredSourceValidation(File file,
            String recipeId,
            String yamlPath,
            Object raw,
            boolean required,
            Map<String, Integer> typeDistribution) {
    }

    final EmakiForgePlugin forgePlugin;
    private final Supplier<ActionEngine> actionEngineSupplier;
    final ItemIdentifierService itemIdentifierService;
    private final boolean deferRuntimeValidation;
    final List<RecipeLoadIssue> structuredIssues = new ArrayList<>();
    private final Map<String, Integer> inputSourceTypes = new LinkedHashMap<>();
    private final Map<String, Integer> outputSourceTypes = new LinkedHashMap<>();
    private final Map<ItemSourceProbeState, Integer> sourceStatuses = new LinkedHashMap<>();
    final List<File> discoveredFiles = new ArrayList<>();
    final List<DeferredRecipe> deferredRecipes = new ArrayList<>();
    final List<DeferredSourceValidation> deferredSourceValidations = new ArrayList<>();
    private long generation;
    private long startedNanos;
    private int parsed;
    int skipped;
    private int duplicates;
    private boolean completingDeferredRuntimeValidation;
    private String candidateFileSummaryHash = "";
    volatile RecipeLoadReport report = RecipeLoadReport.empty(0L);

    public RecipeLoader(EmakiForgePlugin plugin,
                        Supplier<ActionEngine> actionEngineSupplier) {
        this(plugin, actionEngineSupplier, plugin == null ? null : plugin.itemIdentifierService(), false);
    }

    public RecipeLoader(EmakiForgePlugin plugin,
                        Supplier<ActionEngine> actionEngineSupplier,
                        ItemIdentifierService itemIdentifierService) {
        this(plugin, actionEngineSupplier, itemIdentifierService, false);
    }

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
        loadFromCandidateDocuments(documents, new CandidateDocumentAdapter<CandidateDocument>() {
            @Override
            public File fileOf(CandidateDocument document) {
                return document.file();
            }

            @Override
            public String contentOf(CandidateDocument document) {
                return document.content();
            }

            @Override
            public Exception failureOf(CandidateDocument document) {
                return document.failure();
            }

            @Override
            public void beforeLoad(List<CandidateDocument> documents) {
                issues.clear();
                discoveredFiles.clear();
                candidateFileSummaryHash = summarizeDocuments(documents);
            }

            @Override
            public void onCandidate(File file) {
                discoveredFiles.add(file);
            }
        });
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
                            recordFinalizationFailure(deferred, "物品来源校验后无法完成配方配置定稿");
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
                                "配方定稿失败: " + exceptionSummary(failure));
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
                                "物品来源校验失败: " + exceptionSummary(failure),
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

    public RecipeLoadReport report() { return new RecipeLoaderDelegate(this).report(); }

    @Override
    protected String directoryName() { return new RecipeLoaderDelegate(this).directoryName(); }

    @Override
    protected String typeName() {
        return localized("loader.type.recipe");
    }

    @Override
    protected void beforeFilesLoaded(File directory, List<File> files) { new RecipeLoaderDelegate(this).beforeFilesLoaded(directory, files); }

    @Override
    protected Recipe parse(File file, YamlSection configuration) {
        if (configuration == null) {
            recordIssue(file, "", "", IssueSeverity.ERROR, "INVALID_CONFIG",
                    "配方配置为空", true, null, null);
            skipped++;
            return null;
        }
        String recipeId = configuration.getString("id");
        if (Texts.isBlank(recipeId)) {
            recordIssue(file, "", "id", IssueSeverity.ERROR, "BLANK_ID",
                    "配方 ID 不能为空", true, null, null);
            skipped++;
            return null;
        }
        if (!validateOutputSchema(file, recipeId, configuration)) {
            skipped++;
            return null;
        }
        if (!validateIdentitySchema(file, recipeId, configuration)) {
            skipped++;
            return null;
        }
        YamlSection effectiveConfiguration = deferRuntimeValidation
                ? configuration
                : prioritizeSourceCandidates(configuration);
        Recipe recipe = Recipe.fromConfig(effectiveConfiguration);
        if (recipe == null) {
            recordIssue(file, recipeId, "", IssueSeverity.ERROR, "INVALID_CONFIG",
                    "无法解析配方配置", true, null, null);
            skipped++;
            return null;
        }
        if (!SUPPORTED_CONDITION_TYPES.contains(Texts.lower(recipe.conditionType()))) {
            recordIssue(file, recipe.id(), "condition.type", IssueSeverity.ERROR, "INVALID_CONDITION_MODE",
                    "不支持的 condition 模式 '" + recipe.conditionType() + "', 允许值: "
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

    public Recipe parseDocument(File file, YamlSection configuration) { return new RecipeLoaderDelegate(this).parseDocument(file, configuration); }

    @Override
    protected String idOf(Recipe value) { return new RecipeLoaderDelegate(this).idOf(value); }

    @Override
    protected void onBlankId(File file) { new RecipeLoaderDelegate(this).onBlankId(file); }

    @Override
    protected void onDuplicateId(File file, String id) {
        duplicates++;
        skipped++;
        recordIssue(file, id, "id", IssueSeverity.ERROR, "DUPLICATE_ID",
                "重复的配方 ID '" + id + "'", true, null, null);
    }

    @Override
    protected void onDirectoryCreateFailed(File directory) { new RecipeLoaderDelegate(this).onDirectoryCreateFailed(directory); }

    @Override
    protected void onLoadFailure(File file, Exception exception) {
        skipped++;
        recordIssue(file, "", "", IssueSeverity.ERROR, "LOAD_FAILED",
                "配方文件加载失败: " + exceptionSummary(exception), true, null, null);
    }

    @Override
    protected void onPreparationFailure(File directory, RuntimeException exception) { new RecipeLoaderDelegate(this).onPreparationFailure(directory, exception); }

    public List<Recipe> byPermission(Player player) { return new RecipeLoaderDelegate(this).byPermission(player); }

    private boolean validateOutputSchema(File file, String recipeId, YamlSection configuration) {
        Object result = ConfigNodes.get(configuration, "result");
        Object success = ConfigNodes.get(result, "success");
        List<Object> outputs = ConfigNodes.asObjectList(ConfigNodes.get(success, "outputs"));
        for (int index = 0; index < outputs.size(); index++) {
            Object output = outputs.get(index);
            String path = "result.success.outputs[" + index + "]";
            boolean canonical = ConfigNodes.contains(output, "item_source");
            boolean legacy = ConfigNodes.contains(output, "item_sources");
            if (canonical && legacy) {
                recordIssue(file, recipeId, path, IssueSeverity.ERROR, "OUTPUT_SOURCE_FIELD_CONFLICT",
                        "输出不能同时声明 item_source 和 item_sources", true, null, null);
                return false;
            }
            if (ConfigNodes.contains(output, "matcher")) {
                recordIssue(file, recipeId, path + ".matcher", IssueSeverity.ERROR, "OUTPUT_MATCHER_FORBIDDEN",
                        "输出节点不能声明 matcher", true, null, null);
                return false;
            }
            if (canonical) {
                Object raw = ConfigNodes.get(output, "item_source");
                if (raw instanceof Iterable<?> && !(raw instanceof String)) {
                    recordIssue(file, recipeId, path + ".item_source", IssueSeverity.ERROR, "OUTPUT_SOURCE_NOT_SINGLE",
                            "item_source 必须解析为单一来源", true, null, null);
                    return false;
                }
                if (ItemSourceUtil.parse(raw) == null) {
                    recordIssue(file, recipeId, path + ".item_source", IssueSeverity.ERROR, "OUTPUT_SOURCE_INVALID",
                            "item_source 无效", true, null, null);
                    return false;
                }
                continue;
            }
            if (!legacy) {
                recordIssue(file, recipeId, path + ".item_source", IssueSeverity.ERROR, "OUTPUT_SOURCE_MISSING",
                        "输出必须声明 item_source", true, null, null);
                return false;
            }
            List<Object> values = ConfigNodes.asObjectList(ConfigNodes.get(output, "item_sources"));
            if (values.size() != 1 || ItemSourceUtil.parse(values.getFirst()) == null) {
                recordIssue(file, recipeId, path + ".item_sources", IssueSeverity.ERROR, "OUTPUT_LEGACY_SOURCE_INVALID",
                        "旧 item_sources 必须恰好包含一个有效来源", true, null, null);
                return false;
            }
            recordIssue(file, recipeId, path + ".item_sources", IssueSeverity.WARNING, "OUTPUT_LEGACY_SOURCE",
                    "接受旧 item_sources; 请迁移到 item_source", false, null, null);
        }
        return true;
    }

    private boolean validateIdentitySchema(File file, String recipeId, YamlSection configuration) {
        Map<String, String> materialCountKeys = new LinkedHashMap<>();
        Map<String, String> materialAuditIds = new LinkedHashMap<>();
        Map<String, String> auditOwners = new LinkedHashMap<>();
        Map<String, Integer> materialAmounts = new LinkedHashMap<>();
        Map<String, Boolean> materialOptional = new LinkedHashMap<>();
        List<Object> materials = ConfigNodes.asObjectList(configuration.get("materials"));
        for (int index = 0; index < materials.size(); index++) {
            Object entry = materials.get(index);
            String path = "materials[" + index + "]";
            if (!validateDeclaredIdentity(file, recipeId, entry, path, "material_id")
                    || !validateDeclaredIdentity(file, recipeId, entry, path, "count_key")
                    || !validateDeclaredIdentity(file, recipeId, entry, path, "audit_id")) {
                return false;
            }
            if (ConfigNodes.get(entry, "item_sources") == null && ConfigNodes.get(entry, "matcher") == null) {
                recordIssue(file, recipeId, path, IssueSeverity.ERROR, "MATERIAL_MATCH_EMPTY",
                        "材料必须声明 item_sources, matcher 或两者都声明", true, null, null);
                return false;
            }
            String materialId = normalizedIdentity(entry, "material_id", "count_key", "audit_id");
            if (Texts.isBlank(materialId)) {
                continue;
            }
            String countKey = normalizedIdentity(entry, "count_key");
            String auditId = normalizedIdentity(entry, "audit_id");
            countKey = Texts.isBlank(countKey) ? materialId : countKey;
            auditId = Texts.isBlank(auditId) ? materialId : auditId;
            int amount = Numbers.tryParseInt(ConfigNodes.get(entry, "amount"), 1);
            boolean optional = ConfigNodes.bool(entry, "optional", false);
            if (materialCountKeys.containsKey(materialId)
                    && (!countKey.equals(materialCountKeys.get(materialId))
                    || !auditId.equals(materialAuditIds.get(materialId))
                    || amount != materialAmounts.get(materialId)
                    || optional != materialOptional.get(materialId))) {
                recordIssue(file, recipeId, path, IssueSeverity.ERROR, "MATERIAL_IDENTITY_CONFLICT",
                        "material_id '" + materialId
                                + "' 的所有形式必须使用相同的 count_key, audit_id, amount 与 optional 标志",
                        true, null, null);
                return false;
            }
            String auditOwner = auditOwners.putIfAbsent(auditId, materialId);
            if (auditOwner != null && !auditOwner.equals(materialId)) {
                recordIssue(file, recipeId, path + ".audit_id", IssueSeverity.ERROR,
                        "AUDIT_ID_CONFLICT",
                        "audit_id '" + auditId + "' 被不同的 material_id 值共享",
                        true, null, null);
                return false;
            }
            materialCountKeys.putIfAbsent(materialId, countKey);
            materialAuditIds.putIfAbsent(materialId, auditId);
            materialAmounts.putIfAbsent(materialId, amount);
            materialOptional.putIfAbsent(materialId, optional);
        }
        Map<String, Integer> blueprintAmounts = new LinkedHashMap<>();
        List<Object> blueprints = ConfigNodes.asObjectList(configuration.get("blueprint_requirements"));
        for (int index = 0; index < blueprints.size(); index++) {
            Object entry = blueprints.get(index);
            String path = "blueprint_requirements[" + index + "]";
            if (!validateDeclaredIdentity(file, recipeId, entry, path, "id")
                    || !validateDeclaredIdentity(file, recipeId, entry, path, "blueprint_id")) {
                return false;
            }
            if (ConfigNodes.get(entry, "item_sources") == null && ConfigNodes.get(entry, "matcher") == null) {
                recordIssue(file, recipeId, path, IssueSeverity.ERROR, "BLUEPRINT_MATCH_EMPTY",
                        "图纸需求必须声明 item_sources, matcher 或两者都声明",
                        true, null, null);
                return false;
            }
            String blueprintId = normalizedIdentity(entry, "id", "blueprint_id");
            if (Texts.isBlank(blueprintId)) {
                continue;
            }
            int amount = Numbers.tryParseInt(ConfigNodes.get(entry, "amount"), 1);
            Integer existing = blueprintAmounts.putIfAbsent(blueprintId, amount);
            if (existing != null && existing != amount) {
                recordIssue(file, recipeId, path, IssueSeverity.ERROR, "BLUEPRINT_IDENTITY_CONFLICT",
                        "blueprint id '" + blueprintId + "' 的所有形式必须使用相同的 amount",
                        true, null, null);
                return false;
            }
        }
        return true;
    }

    private boolean validateDeclaredIdentity(File file, String recipeId, Object entry,
            String path, String key) { return new RecipeLoaderDelegate(this).validateDeclaredIdentity(file, recipeId, entry, path, key); }

    private String normalizedIdentity(Object entry, String... keys) { return new RecipeLoaderDelegate(this).normalizedIdentity(entry, keys); }

    private boolean validateActions(File file, Recipe recipe) {
        if (recipe == null || recipe.action() == null) {
            return recipe != null;
        }
        ActionEngine engine = actionEngineSupplier == null ? null : actionEngineSupplier.get();
        if (engine == null) {
            recordIssue(file, recipe.id(), "actions", IssueSeverity.WARNING, "ACTION_VALIDATION_SKIPPED",
                    "EmakiCoreLib 操作管线不可用, 已跳过操作校验",
                    false, null, null);
            return true;
        }
        return validatePhase(file, recipe, "pre", recipe.action().pre(), engine)
                && validatePhase(file, recipe, "result", recipe.result() == null ? List.of() : recipe.result().action(), engine)
                && validatePhase(file, recipe, "success", recipe.action().success(), engine)
                && validatePhase(file, recipe, "failure", recipe.action().failure(), engine);
    }

    private boolean validatePhase(File file,
                                  Recipe recipe,
                                  String phase,
                                  List<String> lines,
                                  ActionEngine engine) { return new RecipeLoaderDelegate(this).validatePhase(file, recipe, phase, lines, engine); }

    private void validateRecipeSources(File file, YamlSection configuration, Recipe recipe) {
        List<Object> blueprints = ConfigNodes.asObjectList(configuration.get("blueprint_requirements"));
        for (int index = 0; index < blueprints.size(); index++) {
            Object entry = blueprints.get(index);
            validateAlternativeGroup(file, recipe.id(), "blueprint_requirements[" + index + "].item_sources",
                    ConfigNodes.get(entry, "item_sources"), !ConfigNodes.contains(entry, "matcher"), inputSourceTypes);
        }
        List<Object> materials = ConfigNodes.asObjectList(configuration.get("materials"));
        for (int index = 0; index < materials.size(); index++) {
            Object entry = materials.get(index);
            validateAlternativeGroup(file, recipe.id(), "materials[" + index + "].item_sources",
                    ConfigNodes.get(entry, "item_sources"), !ConfigNodes.contains(entry, "matcher"), inputSourceTypes);
        }
        Object success = ConfigNodes.get(configuration.get("result"), "success");
        List<Object> outputs = ConfigNodes.asObjectList(ConfigNodes.get(success, "outputs"));
        for (int index = 0; index < outputs.size(); index++) {
            Object output = outputs.get(index);
            boolean singular = ConfigNodes.contains(output, "item_source");
            String sourcePath = "result.success.outputs[" + index + "].item_source";
            validateAlternativeGroup(file, recipe.id(), sourcePath,
                    singular
                            ? List.of(ConfigNodes.get(output, "item_source"))
                            : ConfigNodes.get(output, "item_sources"),
                    true, outputSourceTypes);
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

    private List<Object> prioritizeContainers(Object raw) { return new RecipeLoaderDelegate(this).prioritizeContainers(raw); }

    List<Object> prioritizeAlternatives(Object raw) { return new RecipeLoaderDelegate(this).prioritizeAlternatives(raw); }

    void recordFinalizationFailure(DeferredRecipe deferred, String summary) {
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

    private void mergeGuiCandidateIssues(GuiTemplateLoader guiTemplateLoader) { new RecipeLoaderDelegate(this).mergeGuiCandidateIssues(guiTemplateLoader); }

    private void validateGuiSources(GuiTemplateLoader guiTemplateLoader) { new RecipeLoaderDelegate(this).validateGuiSources(guiTemplateLoader); }

    Object guiItemAlternatives(Object item, Object slot) { return new RecipeLoaderDelegate(this).guiItemAlternatives(item, slot); }

    void validateAlternativeGroup(File file,
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
                        "必需的物品来源候选为空", true, null, null);
            }
            return;
        }
        int ready = 0;
        List<ItemIdentifierService.SourceProbe> probes = new ArrayList<>();
        for (Object alternative : alternatives) {
            ItemSourceRef source = ItemSourceUtil.parse(alternative);
            ItemIdentifierService.SourceProbe probe = itemIdentifierService == null
                    ? new ItemIdentifierService.SourceProbe(source,
                    ItemSourceProbeState.PROVIDER_MISSING,
                    "EmakiCoreLib",
                    "物品来源探测不可用")
                    : itemIdentifierService.probeSource(source, yamlPath);
            probes.add(probe);
            sourceStatuses.merge(probe.status(), 1, Integer::sum);
            if (source != null) {

                typeDistribution.merge(source.kind().key(), 1, Integer::sum);
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
                    "物品来源不可用: status=" + probe.status()
                            + (Texts.isBlank(probe.detail()) ? "" : ", detail=" + probe.detail()),
                    ready == 0, shorthand, probe);
        }
    }

    void recordIssue(File file,
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
            forgePlugin.getLogger().warning("[loader] " + code
                    + " path=" + issue.filePath()
                    + " yaml=" + issue.yamlPath()
                    + " recipe=" + issue.recipeId()
                    + " detail=" + issue.summary());
        }
    }

    String summarizeDocuments(List<CandidateDocument> documents) { return new RecipeLoaderDelegate(this).summarizeDocuments(documents); }

    private String summarizeFiles(List<File> files) { return new RecipeLoaderDelegate(this).summarizeFiles(files); }

    String canonicalPath(File file) { return new RecipeLoaderDelegate(this).canonicalPath(file); }

    String exceptionSummary(Throwable throwable) { return new RecipeLoaderDelegate(this).exceptionSummary(throwable); }
}

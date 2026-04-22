package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiLoreSectionContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiNameContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.assembly.NamePosition;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationTemplateResolver;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationValidator;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeLayerSnapshotBuilder {

    private static final String NAMESPACE_ID = "forge";
    private static final int NAME_ORDER_BASE = 100;
    private static final int LORE_SECTION_ORDER = 100;

    private final EmakiForgePlugin plugin;
    private final ForgeMaterialContributionCollector contributionCollector;
    private final StructuredPresentationTemplateResolver structuredResolver = new StructuredPresentationTemplateResolver();
    private final StructuredPresentationValidator structuredValidator = new StructuredPresentationValidator();

    ForgeLayerSnapshotBuilder(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.contributionCollector = new ForgeMaterialContributionCollector(plugin);
    }

    EmakiItemLayerSnapshot buildLayerSnapshot(Recipe recipe,
            GuiItems guiItems,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            long forgedAt) {
        return buildLayerSnapshot(recipe, collectMaterialContributions(recipe, guiItems), multiplier, qualityTier, forgedAt);
    }

    EmakiItemLayerSnapshot buildLayerSnapshot(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            long forgedAt) {
        List<ForgeMaterialContribution> contributions = materials == null ? List.of() : List.copyOf(materials);
        List<EmakiStatContribution> stats = buildStatContributions(contributions, multiplier);
        EmakiStructuredPresentation structuredPresentation = buildStructuredPresentation(recipe, contributions, qualityTier, multiplier, stats);
        Map<String, Object> audit = buildAudit(recipe, contributions, qualityTier, multiplier, forgedAt);
        return new EmakiItemLayerSnapshot(
                NAMESPACE_ID,
                1,
                audit,
                stats,
                structuredPresentation == null || structuredPresentation.isEmpty() ? null : structuredPresentation
        );
    }

    List<ForgeMaterialContribution> collectMaterialContributions(Recipe recipe, GuiItems guiItems) {
        return contributionCollector.collectMaterialContributions(recipe, guiItems);
    }

    List<ForgeMaterial.QualityModifier> collectQualityModifiers(List<ForgeMaterialContribution> materials) {
        return contributionCollector.collectQualityModifiers(materials);
    }

    String buildMaterialsSignature(List<ForgeMaterialContribution> materials) {
        List<Map<String, Object>> signatureData = new ArrayList<>();
        if (materials != null) {
            for (ForgeMaterialContribution material : materials) {
                if (material != null && material.amount() > 0) {
                    signatureData.add(material.toSignatureData());
                }
            }
        }
        return SignatureUtil.stableSignature(signatureData);
    }

    private List<EmakiStatContribution> buildStatContributions(List<ForgeMaterialContribution> materials, double multiplier) {
        List<EmakiStatContribution> stats = new ArrayList<>();
        int sequence = 0;
        if (materials == null) {
            return stats;
        }
        for (ForgeMaterialContribution material : materials) {
            if (material == null || material.amount() <= 0 || material.material() == null) {
                continue;
            }
            for (Map.Entry<String, Double> entry : material.material().statContributions().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                stats.add(new EmakiStatContribution(
                        entry.getKey(),
                        entry.getValue() * material.amount() * multiplier,
                        material.material().key() + "#" + material.sequence(),
                        sequence++
                ));
            }
        }
        return stats;
    }

    private EmakiStructuredPresentation buildStructuredPresentation(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            QualitySettings.QualityTier qualityTier,
            double multiplier,
            List<EmakiStatContribution> stats) {
        Map<String, Double> aggregatedStats = aggregateStats(stats);
        Map<String, Object> variables = buildVariables(aggregatedStats, qualityTier, multiplier);
        LocalNameState nameState = new LocalNameState();
        ConfiguredStructuredState configuredState = new ConfiguredStructuredState();
        EmakiStructuredPresentation recipePresentation = null;

        if (recipe != null && recipe.result() != null) {
            recipePresentation = resolveStructuredPresentation(recipe.result().structuredPresentation(), variables);
            if (recipePresentation != null
                    && (recipePresentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                    || !recipePresentation.nameContributions().isEmpty()
                    || !recipePresentation.loreSections().isEmpty())) {
                configuredState.merge(recipePresentation);
            }
            if (recipePresentation == null
                    || (recipePresentation.baseNamePolicy() != BaseNamePolicy.EXPLICIT_TEMPLATE
                    && recipePresentation.nameContributions().isEmpty())) {
                applyNameModifications(nameState, recipe.result().nameModifications(), variables);
            }
        }
        if (materials != null) {
            for (ForgeMaterialContribution material : materials) {
                if (material == null || material.material() == null) {
                    continue;
                }
                List<Object> structuredFragments = material.material().structuredPresentations();
                if (!structuredFragments.isEmpty()) {
                    for (Object rawStructured : structuredFragments) {
                        EmakiStructuredPresentation configuredPresentation = resolveStructuredPresentation(rawStructured, variables);
                        if (configuredPresentation != null) {
                            configuredState.merge(configuredPresentation);
                        }
                    }
                }
                applyNameModifications(nameState, material.material().nameModifications(), variables);
            }
        }

        QualitySettings settings = qualitySettings();
        if (qualityTier != null && settings.itemMetaEnabled()) {
            EmakiStructuredPresentation configuredPresentation = resolveStructuredPresentation(
                    settings.itemMetaStructuredPresentation(qualityTier.name()),
                    variables
            );
            if (configuredPresentation != null
                    && (configuredPresentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                    || !configuredPresentation.nameContributions().isEmpty()
                    || !configuredPresentation.loreSections().isEmpty())) {
                configuredState.merge(configuredPresentation);
            }
            if (configuredPresentation == null
                    || (configuredPresentation.baseNamePolicy() != BaseNamePolicy.EXPLICIT_TEMPLATE
                    && configuredPresentation.nameContributions().isEmpty())) {
                applyNameModifications(nameState, settings.itemMetaNameModifications(qualityTier.name()), variables);
            }
        }

        List<String> loreLines = new ArrayList<>();
        if (recipe != null && recipe.result() != null) {
            if (recipePresentation == null || recipePresentation.loreSections().isEmpty()) {
                applyLoreActions(loreLines, recipe.result().loreActions(), variables);
            }
        }
        if (materials != null) {
            for (ForgeMaterialContribution material : materials) {
                if (material == null || material.material() == null) {
                    continue;
                }
                applyLoreActions(loreLines, material.material().loreActions(), variables);
            }
        }
        if (qualityTier != null && settings.itemMetaEnabled()) {
            EmakiStructuredPresentation configuredPresentation = resolveStructuredPresentation(
                    settings.itemMetaStructuredPresentation(qualityTier.name()),
                    variables
            );
            if (configuredPresentation == null || configuredPresentation.loreSections().isEmpty()) {
                applyLoreActions(loreLines, settings.itemMetaLoreActions(qualityTier.name()), variables);
            }
        }

        List<EmakiNameContribution> nameContributions = new ArrayList<>(configuredState.nameContributions());
        nameContributions.addAll(buildNameContributions(nameState));
        List<EmakiLoreSectionContribution> loreSections = new ArrayList<>(configuredState.loreSections());
        addSection(loreSections, "forge.display", LORE_SECTION_ORDER, loreLines);
        StructuredPresentationValidator.ValidationResult validation = structuredValidator.sanitize(new EmakiStructuredPresentation(
                configuredState.baseNamePolicyOr(nameState.baseNamePolicy()),
                configuredState.baseNameTemplateOr(nameState.baseNameTemplate()),
                nameContributions,
                loreSections
        ));
        EmakiStructuredPresentation presentation = validation.presentation();
        return presentation == null || presentation.isEmpty() ? null : presentation;
    }

    private Map<String, Double> aggregateStats(List<EmakiStatContribution> stats) {
        Map<String, Double> aggregated = new LinkedHashMap<>();
        if (stats == null) {
            return aggregated;
        }
        for (EmakiStatContribution contribution : stats) {
            if (contribution == null || Texts.isBlank(contribution.statId())) {
                continue;
            }
            aggregated.merge(Texts.lower(contribution.statId()), contribution.amount(), Double::sum);
        }
        return aggregated;
    }

    private Map<String, Object> buildVariables(Map<String, Double> aggregatedStats,
            QualitySettings.QualityTier qualityTier,
            double multiplier) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (aggregatedStats != null) {
            for (Map.Entry<String, Double> entry : aggregatedStats.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                variables.put(entry.getKey(), Numbers.formatNumber(entry.getValue(), "0.##"));
            }
        }
        String qualityName = qualityTier == null ? "" : qualityTier.name();
        variables.put("quality", qualityName);
        variables.put("quality_name", qualityName);
        variables.put("quality_multiplier", Numbers.formatNumber(multiplier, "0.##"));
        variables.put("multiplier", Numbers.formatNumber(multiplier, "0.##"));
        return variables;
    }

    private void applyNameModifications(LocalNameState state, Object operations, Map<String, Object> variables) {
        if (state == null) {
            return;
        }
        for (Map<String, Object> operation : normalizeOperations(operations)) {
            String action = Texts.lower(operation.get("action"));
            String value = renderTemplate(resolveOperationValue(operation), variables);
            switch (action) {
                case "replace" -> state.replaceBase(value);
                case "prepend_prefix" -> {
                    if (Texts.isNotBlank(value)) {
                        state.addPrefix(value);
                    }
                }
                case "append_suffix" -> {
                    if (Texts.isNotBlank(value)) {
                        state.addPostfix(value);
                    }
                }
                case "regex_replace" -> state.applyRegexReplace(
                        Texts.toStringSafe(operation.get("regex_pattern")),
                        Texts.toStringSafe(operation.get("replacement")),
                        variables
                );
                default -> {
                }
            }
        }
    }

    private void applyLoreActions(List<String> lines, Object operations, Map<String, Object> variables) {
        if (lines == null) {
            return;
        }
        for (Map<String, Object> operation : normalizeOperations(operations)) {
            String action = Texts.lower(operation.get("action"));
            List<String> content = renderContent(operation, variables);
            String anchor = renderTemplate(resolveSearchPattern(operation), variables);
            switch (action) {
                case "append" -> lines.addAll(content);
                case "prepend" -> {
                    for (String line : content) {
                        lines.add(0, line);
                    }
                }
                case "insert_below", "search_insert_below", "search_insert" -> {
                    for (String line : content) {
                        lines.add(findInsertIndex(lines, anchor, true), line);
                    }
                }
                case "insert_above", "search_insert_above" -> {
                    for (String line : content) {
                        lines.add(findInsertIndex(lines, anchor, false), line);
                    }
                }
                case "replace_line" -> replaceLine(lines, anchor, content.isEmpty() ? "" : content.get(0));
                case "delete_line" -> deleteLine(lines, anchor);
                case "regex_replace" -> replaceRegexInLore(
                        lines,
                        Texts.toStringSafe(operation.get("regex_pattern")),
                        Texts.toStringSafe(operation.get("replacement")),
                        variables
                );
                default -> {
                }
            }
        }
    }

    private List<EmakiNameContribution> buildNameContributions(LocalNameState state) {
        if (state == null) {
            return List.of();
        }
        List<EmakiNameContribution> contributions = new ArrayList<>();
        int order = NAME_ORDER_BASE;
        int index = 0;
        for (String prefix : state.prefixes()) {
            contributions.add(new EmakiNameContribution(
                    stableNameSlotId("prefix", index++),
                    NamePosition.PREFIX,
                    order++,
                    prefix,
                    NAMESPACE_ID
            ));
        }
        index = 0;
        for (String postfix : state.postfixes()) {
            contributions.add(new EmakiNameContribution(
                    stableNameSlotId("postfix", index++),
                    NamePosition.POSTFIX,
                    order++,
                    postfix,
                    NAMESPACE_ID
            ));
        }
        return contributions;
    }

    private List<Map<String, Object>> normalizeOperations(Object raw) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object operation : emaki.jiuwu.craft.corelib.config.ConfigNodes.asObjectList(raw)) {
            Object plain = emaki.jiuwu.craft.corelib.config.ConfigNodes.toPlainData(operation);
            if (!(plain instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> normalizedOperation = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalizedOperation.put(
                        String.valueOf(entry.getKey()),
                        emaki.jiuwu.craft.corelib.config.ConfigNodes.toPlainData(entry.getValue())
                );
            }
            normalized.add(normalizedOperation);
        }
        return normalized;
    }

    private List<String> renderContent(Map<String, Object> operation, Map<String, Object> variables) {
        List<String> rendered = new ArrayList<>();
        for (String line : Texts.asStringList(operation == null ? null : operation.get("content"))) {
            rendered.add(renderTemplate(line, variables));
        }
        return rendered;
    }

    private String resolveOperationValue(Map<String, Object> operation) {
        if (operation == null) {
            return "";
        }
        String value = Texts.toStringSafe(operation.get("value"));
        if (Texts.isNotBlank(value)) {
            return value;
        }
        value = Texts.toStringSafe(operation.get("replacement"));
        return Texts.isBlank(value) ? Texts.toStringSafe(operation.get("content")) : value;
    }

    private String resolveSearchPattern(Map<String, Object> operation) {
        if (operation == null) {
            return "";
        }
        String pattern = Texts.toStringSafe(operation.get("target_pattern"));
        if (Texts.isNotBlank(pattern)) {
            return pattern;
        }
        pattern = Texts.toStringSafe(operation.get("pattern"));
        return Texts.isBlank(pattern) ? Texts.toStringSafe(operation.get("anchor")) : pattern;
    }

    private int findInsertIndex(List<String> lines, String anchor, boolean below) {
        if (Texts.isBlank(anchor)) {
            return below ? (lines == null ? 0 : lines.size()) : 0;
        }
        for (int index = 0; index < lines.size(); index++) {
            if (Texts.toStringSafe(lines.get(index)).contains(anchor)) {
                return below ? index + 1 : index;
            }
        }
        return lines.size();
    }

    private void replaceLine(List<String> lines, String anchor, String replacement) {
        if (lines == null) {
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            if (!Texts.toStringSafe(lines.get(index)).contains(anchor)) {
                continue;
            }
            lines.set(index, Texts.toStringSafe(replacement));
            return;
        }
    }

    private void deleteLine(List<String> lines, String anchor) {
        if (lines == null) {
            return;
        }
        for (int index = lines.size() - 1; index >= 0; index--) {
            if (Texts.toStringSafe(lines.get(index)).contains(anchor)) {
                lines.remove(index);
            }
        }
    }

    private void replaceRegexInLore(List<String> lines,
            String regex,
            String replacement,
            Map<String, Object> variables) {
        if (lines == null || Texts.isBlank(regex)) {
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            lines.set(index, replaceRegex(lines.get(index), regex, replacement, variables));
        }
    }

    private static String replaceRegex(String text,
            String regex,
            String replacement,
            Map<String, Object> variables) {
        if (Texts.isBlank(regex)) {
            return Texts.toStringSafe(text);
        }
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(Texts.toStringSafe(text));
            return matcher.replaceAll(Matcher.quoteReplacement(Texts.formatTemplate(
                    Texts.toStringSafe(replacement),
                    variables == null ? Map.of() : variables
            )));
        } catch (Exception ignored) {
            return Texts.toStringSafe(text);
        }
    }

    private String renderTemplate(String template, Map<String, Object> variables) {
        return Texts.formatTemplate(Texts.toStringSafe(template), variables == null ? Map.of() : variables);
    }

    private String stableNameSlotId(String role, int index) {
        return index <= 0 ? NAMESPACE_ID + "." + role : NAMESPACE_ID + "." + role + "." + index;
    }

    private EmakiStructuredPresentation resolveStructuredPresentation(Object raw, Map<String, ?> variables) {
        StructuredPresentationValidator.ValidationResult validation = structuredValidator.sanitize(
                structuredResolver.fromConfig(raw, variables, NAMESPACE_ID)
        );
        return validation.presentation();
    }

    private void addSection(List<EmakiLoreSectionContribution> sections,
            String sectionId,
            int order,
            List<String> lines) {
        if (sections == null || Texts.isBlank(sectionId) || lines == null || lines.isEmpty()) {
            return;
        }
        sections.add(new EmakiLoreSectionContribution(sectionId, order, List.copyOf(lines), NAMESPACE_ID));
    }

    private QualitySettings qualitySettings() {
        if (plugin == null || plugin.appConfig() == null || plugin.appConfig().qualitySettings() == null) {
            return QualitySettings.defaults();
        }
        return plugin.appConfig().qualitySettings();
    }

    private Map<String, Object> buildAudit(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            QualitySettings.QualityTier qualityTier,
            double multiplier,
            long forgedAt) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("recipe_id", recipe == null ? "" : recipe.id());
        audit.put("quality", qualityTier == null ? "" : qualityTier.name());
        audit.put("multiplier", multiplier);
        audit.put("forged_at", forgedAt);
        audit.put("materials_signature", buildMaterialsSignature(materials));
        if (recipe != null && recipe.configuredOutputSource() != null) {
            audit.put("output_item", ItemSourceUtil.toShorthand(recipe.configuredOutputSource()));
        }
        List<Map<String, Object>> materialMaps = new ArrayList<>();
        if (materials != null) {
            for (ForgeMaterialContribution material : materials) {
                if (material != null) {
                    materialMaps.add(material.toAuditMap());
                }
            }
        }
        audit.put("materials", materialMaps);
        return audit;
    }

    private static final class LocalNameState {

        private BaseNamePolicy baseNamePolicy = BaseNamePolicy.SOURCE_EFFECTIVE_NAME;
        private String baseNameTemplate = "";
        private final List<String> prefixes = new ArrayList<>();
        private final List<String> postfixes = new ArrayList<>();

        BaseNamePolicy baseNamePolicy() {
            return baseNamePolicy;
        }

        String baseNameTemplate() {
            return baseNameTemplate;
        }

        List<String> prefixes() {
            return prefixes;
        }

        List<String> postfixes() {
            return postfixes;
        }

        void replaceBase(String value) {
            baseNamePolicy = BaseNamePolicy.EXPLICIT_TEMPLATE;
            baseNameTemplate = Texts.toStringSafe(value);
            prefixes.clear();
            postfixes.clear();
        }

        void addPrefix(String value) {
            prefixes.add(0, Texts.toStringSafe(value));
        }

        void addPostfix(String value) {
            postfixes.add(Texts.toStringSafe(value));
        }

        void applyRegexReplace(String regex, String replacement, Map<String, Object> variables) {
            if (Texts.isBlank(regex) || baseNamePolicy != BaseNamePolicy.EXPLICIT_TEMPLATE) {
                return;
            }
            baseNameTemplate = ForgeLayerSnapshotBuilder.replaceRegex(baseNameTemplate, regex, replacement, variables);
        }
    }

    private static final class ConfiguredStructuredState {

        private BaseNamePolicy baseNamePolicy = BaseNamePolicy.SOURCE_EFFECTIVE_NAME;
        private String baseNameTemplate = "";
        private final List<EmakiNameContribution> nameContributions = new ArrayList<>();
        private final List<EmakiLoreSectionContribution> loreSections = new ArrayList<>();

        void merge(EmakiStructuredPresentation presentation) {
            if (presentation == null || presentation.isEmpty()) {
                return;
            }
            if (presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                    && Texts.isNotBlank(presentation.baseNameTemplate())) {
                baseNamePolicy = BaseNamePolicy.EXPLICIT_TEMPLATE;
                baseNameTemplate = presentation.baseNameTemplate();
            }
            nameContributions.addAll(presentation.nameContributions());
            loreSections.addAll(presentation.loreSections());
        }

        List<EmakiNameContribution> nameContributions() {
            return nameContributions;
        }

        List<EmakiLoreSectionContribution> loreSections() {
            return loreSections;
        }

        BaseNamePolicy baseNamePolicyOr(BaseNamePolicy fallback) {
            return baseNamePolicy == BaseNamePolicy.EXPLICIT_TEMPLATE ? baseNamePolicy : fallback;
        }

        String baseNameTemplateOr(String fallback) {
            return baseNamePolicy == BaseNamePolicy.EXPLICIT_TEMPLATE ? baseNameTemplate : fallback;
        }
    }
}

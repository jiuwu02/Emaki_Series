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

final class RecipeLoaderDelegate {

    private final RecipeLoader ownerRef;

    RecipeLoaderDelegate(RecipeLoader ownerRef) {
        this.ownerRef = ownerRef;
    }

    RecipeLoader.RecipeLoadReport report() {
        return ownerRef.report;
    }

    String directoryName() {
        return "recipes";
    }

    void beforeFilesLoaded(File directory, List<File> files) {
        ownerRef.discoveredFiles.clear();
        if (files != null) {
            ownerRef.discoveredFiles.addAll(files);
        }
    }

    Recipe parseDocument(File file, YamlSection configuration) {
        return ownerRef.parse(file, configuration);
    }

    String idOf(Recipe value) {
        return value.id();
    }

    void onBlankId(File file) {
        ownerRef.recordIssue(file, "", "id", RecipeLoader.IssueSeverity.ERROR, "BLANK_ID",
                "配方 ID 不能为空", true, null, null);
        ownerRef.skipped++;
    }

    void onDirectoryCreateFailed(File directory) {
        ownerRef.recordIssue(directory, "", "", RecipeLoader.IssueSeverity.ERROR, "DIRECTORY_CREATE_FAILED",
                "无法创建配方目录", true, null, null);
    }

    void onPreparationFailure(File directory, RuntimeException exception) {
        ownerRef.recordIssue(directory, "", "", RecipeLoader.IssueSeverity.ERROR, "PREPARATION_FAILED",
                "配方目录准备失败: " + ownerRef.exceptionSummary(exception), true, null, null);
    }

    List<Recipe> byPermission(Player player) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : ownerRef.all().values()) {
            if (!recipe.requiresPermission() || (player != null && player.hasPermission(recipe.permission()))) {
                result.add(recipe);
            }
        }
        return result;
    }

    boolean validateDeclaredIdentity(File file, String recipeId, Object entry, String path, String key) {
        if (!ConfigNodes.contains(entry, key)) {
            return true;
        }
        if (Texts.isNotBlank(ConfigNodes.string(entry, key, null))) {
            return true;
        }
        ownerRef.recordIssue(file, recipeId, path + "." + key, RecipeLoader.IssueSeverity.ERROR, "IDENTITY_BLANK",
                key + " 声明后不能为空", true, null, null);
        return false;
    }

    String normalizedIdentity(Object entry, String... keys) {
        for (String key : keys) {
            String value = ConfigNodes.string(entry, key, null);
            if (Texts.isNotBlank(value)) {
                return Texts.lower(value.trim());
            }
        }
        return "";
    }

    boolean validatePhase(File file, Recipe recipe, String phase, List<String> lines, ActionEngine engine) {
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
                    ? "未通过编译"
                    : ownerRef.forgePlugin.coreLib().messageService().renderFirstDiagnostic(compiled.diagnostics());
            ownerRef.recordIssue(file, recipe.id(), "actions." + phase + "[" + index + "]", RecipeLoader.IssueSeverity.ERROR,
                    "INVALID_ACTION_LINE", "操作行未通过编译: " + reason, true, null, null);
            return false;
        }
        return true;
    }

    List<Object> prioritizeContainers(Object raw) {
        List<Object> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            Map<String, Object> values = new LinkedHashMap<>(ConfigNodes.entries(entry));
            if (values.containsKey("item_sources")) {
                values.put("item_sources", ownerRef.prioritizeAlternatives(values.get("item_sources")));
            }
            result.add(values.isEmpty() ? ConfigNodes.toPlainData(entry) : values);
        }
        return result;
    }

    List<Object> prioritizeAlternatives(Object raw) {
        List<Object> ready = new ArrayList<>();
        List<Object> unavailable = new ArrayList<>();
        List<Object> alternatives = ConfigNodes.asObjectList(raw);
        if (alternatives.isEmpty() && raw != null) {
            alternatives = List.of(raw);
        }
        for (Object alternative : alternatives) {
            ItemSourceRef source = ItemSourceUtil.parse(alternative);
            Object plain = ConfigNodes.toPlainData(alternative);
            if (ownerRef.itemIdentifierService.probeSource(source).ready()) {
                ready.add(plain);
            } else {
                unavailable.add(plain);
            }
        }
        ready.addAll(unavailable);
        return ready;
    }

    void mergeGuiCandidateIssues(GuiTemplateLoader guiTemplateLoader) {
        if (!(guiTemplateLoader instanceof ForgeGuiTemplateLoader forgeGuiTemplateLoader)) {
            return;
        }
        for (ForgeGuiTemplateLoader.CandidateIssue issue : forgeGuiTemplateLoader.candidateIssues()) {
            if (issue == null) {
                continue;
            }
            ownerRef.recordIssue(issue.file(), "", "gui", RecipeLoader.IssueSeverity.ERROR,
                    issue.code(), issue.summary(), true, null, null);
        }
    }

    void validateGuiSources(GuiTemplateLoader guiTemplateLoader) {
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
                Object alternatives = ownerRef.guiItemAlternatives(item, slotEntry.getValue());
                if (alternatives == null) {
                    continue;
                }
                String yamlPath = "gui." + templateId + ".slots." + slotEntry.getKey() + ".item";
                try {
                    ownerRef.validateAlternativeGroup(file, "", yamlPath,
                            alternatives, true, new LinkedHashMap<>());
                } catch (RuntimeException | LinkageError failure) {
                    ownerRef.recordIssue(file, "", yamlPath, RecipeLoader.IssueSeverity.ERROR,
                            "GUI_SOURCE_VALIDATION_FAILED",
                            "GUI 物品来源校验失败: " + ownerRef.exceptionSummary(failure),
                            true, null, null);
                }
            }
        }
    }

    Object guiItemAlternatives(Object item, Object slot) {
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

    String summarizeDocuments(List<RecipeLoader.CandidateDocument> documents) {
        if (documents == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (RecipeLoader.CandidateDocument document : documents) {
                if (document == null || document.file() == null) {
                    continue;
                }
                digest.update(document.file().getPath().getBytes(StandardCharsets.UTF_8));
                if (document.content() != null) {
                    digest.update(document.content().getBytes(StandardCharsets.UTF_8));
                } else if (document.failure() != null) {
                    digest.update(ownerRef.exceptionSummary(document.failure()).getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest(), 0, 8);
        } catch (Exception exception) {
            return "unavailable";
        }
    }

    String summarizeFiles(List<File> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (File file : files) {
                if (file == null) {
                    continue;
                }
                digest.update(ownerRef.canonicalPath(file).getBytes(StandardCharsets.UTF_8));
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

    String canonicalPath(File file) {
        if (file == null) {
            return "";
        }
        try {
            return file.getCanonicalPath();
        } catch (Exception exception) {
            return file.getAbsolutePath();
        }
    }

    String exceptionSummary(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String message = Texts.toStringSafe(throwable.getMessage());
        return throwable.getClass().getSimpleName() + (message.isBlank() ? "" : ": " + message);
    }

}

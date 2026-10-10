package emaki.jiuwu.craft.forge.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemBuildResult;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.editor.EditorBrowser;
import emaki.jiuwu.craft.corelib.editor.EditorBrowserRoot;
import emaki.jiuwu.craft.corelib.editor.EditorFieldSpec;
import emaki.jiuwu.craft.corelib.editor.EditorMenuMeta;
import emaki.jiuwu.craft.corelib.editor.EditorModule;
import emaki.jiuwu.craft.corelib.editor.EditorService;
import emaki.jiuwu.craft.corelib.editor.EditorSession;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;

public final class ForgeEditorModule implements EditorModule {

    public static final String ROOT_RECIPES = "recipes";
    public static final String TYPE_PREVIEW = "preview";

    private final EmakiForgePlugin plugin;
    private final GuiTemplateLoader templateLoader;
    private EditorService service;

    public ForgeEditorModule(EmakiForgePlugin plugin, GuiTemplateLoader templateLoader) {
        this.plugin = plugin;
        this.templateLoader = templateLoader;
    }

    public void bind(EditorService service) {
        this.service = service;
    }

    public EditorService service() {
        return service;
    }

    @Override
    public String rootCommand() {
        return "emakiforge";
    }

    @Override
    public List<EditorBrowserRoot> roots() {
        return List.of(recipesRoot());
    }

    public static EditorBrowserRoot recipesRoot() {
        return new EditorBrowserRoot() {
            @Override
            public String id() {
                return ROOT_RECIPES;
            }

            @Override
            public String directory() {
                return ROOT_RECIPES;
            }

            @Override
            public String labelKey() {
                return "editor.root.recipes";
            }

            @Override
            public Material icon() {
                return Material.ANVIL;
            }

            @Override
            public boolean supportsCreate() {
                return true;
            }

            @Override
            public boolean supportsDelete() {
                return true;
            }

            @Override
            public BiFunction<File, String, String> fileId() {
                return (file, directory) -> resolveFileId(file);
            }
        };
    }

    @Override
    public EditorMenuMeta meta(String menuId) {
        return ForgeEditorMenus.meta(menuId);
    }

    @Override
    public List<EditorFieldSpec> fields(EditorSession session, String menuId) {
        return ForgeEditorMenus.fields(session, menuId);
    }

    @Override
    public String homeMenuId() {
        return ForgeEditorMenus.HOME;
    }

    @Override
    public String titleKey(String menuId) {
        return ForgeEditorMenus.titleKey(menuId);
    }

    @Override
    public String templateId(String menuId) {
        return EditorBrowser.MENU_ROOT_SELECT.equals(menuId)
                ? EditorService.TEMPLATE_BROWSER
                : EditorService.TEMPLATE_PAGE;
    }

    @Override
    public EditorValidator validator(EditorBrowserRoot root, Path file) {
        return validatorFor(plugin);
    }

    @Override
    public void reload() {
        if (plugin != null) {
            plugin.reloadPluginStateAsync(true);
        }
    }

    @Override
    public ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        String type = Texts.lower(slot.definition().type());
        if (!TYPE_PREVIEW.equals(type)) {
            return null;
        }
        return renderPreview(session);
    }

    @Override
    public Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        if (root == null || !ROOT_RECIPES.equals(root.id())) {
            return null;
        }
        return recipeSkeleton(id);
    }

    public void openBrowser(Player player) {
        if (service == null) {
            return;
        }
        ensureTemplates();
        service.openBrowser(player);
    }

    public EditorSession openById(Player player, String rawId) {
        String id = Texts.normalizeId(rawId);
        if (service == null || id.isEmpty()) {
            return null;
        }
        File file = findFile(id);
        if (file == null) {
            return null;
        }
        ensureTemplates();
        EditorBrowserRoot root = recipesRoot();
        return service.open(player, file.toPath(), id, validator(root, file.toPath()), null);
    }

    public List<String> knownIds() {
        List<String> ids = new ArrayList<>();
        for (File file : recipeFiles()) {
            String id = resolveFileId(file);
            if (!id.isEmpty() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    public static EditorValidator validatorFor(EmakiForgePlugin plugin) {
        EditorValidator parseCheck = parseCheck(plugin);
        return (candidate, docId, source) -> EditorValidator.allOf(
                EditorValidator.yamlOnly(),
                EditorValidator.idStable(docId),
                parseCheck,
                outputsRequired()).validate(candidate, docId, source);
    }

    private static EditorValidator parseCheck(EmakiForgePlugin plugin) {
        return (candidate, docId, source) -> {
            try {
                YamlSection section = YamlFiles.load(candidate.text());
                if (section == null) {
                    return "unparseable_yaml";
                }
                RecipeLoader loader = plugin == null
                        ? new RecipeLoader(null, null)
                        : new RecipeLoader(plugin, plugin.coreLib()::actionEngine);
                File file = source == null ? null : source.toFile();
                return loader.parseDocument(file, section) == null ? "definition_parse_failed" : null;
            } catch (RuntimeException failure) {
                return "definition_parse_failed";
            }
        };
    }

    private static EditorValidator outputsRequired() {
        return (candidate, docId, source) -> {
            Object outputs = candidate.value("result", "success", "outputs");
            if (outputs == null) {
                return null;
            }
            return outputs instanceof List<?> list && !list.isEmpty() ? null : "definition_parse_failed";
        };
    }

    private ItemStack renderPreview(EditorSession session) {
        try {
            String source = null;
            int amount = 1;
            Object outputs = session.draft().value("result", "success", "outputs");
            if (outputs instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> output) {
                Object raw = output.get("item_source");
                if (!(raw instanceof String) && output.get("item_sources") instanceof List<?> alternatives
                        && !alternatives.isEmpty()) {
                    raw = alternatives.get(0);
                }
                if (raw instanceof String text) {
                    source = text;
                }
                amount = Numbers.tryParseInt(output.get("amount"), 1);
            }
            if (source != null && !source.isBlank()) {
                ConfiguredItemDefinition definition =
                        new ConfiguredItemDefinition(source, Math.max(1, amount), Map.of());
                ItemBuildResult result = GuiItemBuilder.buildResult(definition, Map.of(),
                        plugin.coreLib().configuredItemService());
                if (!result.hasErrors() && result.itemStack() != null) {
                    return result.itemStack();
                }
            }
            return previewFallback(session);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private ItemStack previewFallback(EditorSession session) {
        Path path = session.document().path();
        String fileName = path.getFileName() == null ? String.valueOf(path) : path.getFileName().toString();
        ConfiguredItemDefinition definition = new ConfiguredItemDefinition("minecraft-paper", 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set(fileName),
                "minecraft:lore", ItemComponentPatch.set(List.of(String.valueOf(path)))));
        return GuiItemBuilder.build(definition, Map.of(), plugin.coreLib().configuredItemService());
    }

    private void ensureTemplates() {
        if (templateLoader != null) {
            templateLoader.load();
        }
    }

    private File findFile(String normalizedId) {
        for (File file : recipeFiles()) {
            if (normalizedId.equals(resolveFileId(file))) {
                return file;
            }
        }
        return null;
    }

    private List<File> recipeFiles() {
        File directory = new File(plugin.getDataFolder(), ROOT_RECIPES);
        File[] files = directory.listFiles((dir, name) ->
                name != null && (name.endsWith(".yml") || name.endsWith(".yaml")));
        return files == null ? List.of() : List.of(files);
    }

    private static String resolveFileId(File file) {
        try {
            Object raw = YamlFiles.load(file).asMap().get("id");
            String id = Texts.normalizeId(raw == null ? "" : Texts.toStringSafe(raw));
            if (!id.isEmpty()) {
                return id;
            }
        } catch (RuntimeException failure) {
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static Map<String, Object> recipeSkeleton(String id) {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("id", id);
        skeleton.put("display_name", "<white>" + id + "</white>");
        skeleton.put("forge_capacity", 20);
        skeleton.put("blueprint_requirements", List.of());
        skeleton.put("materials", List.of());
        skeleton.put("optional_material_limit", 1);
        Map<String, Object> guarantee = new LinkedHashMap<>();
        guarantee.put("enabled", false);
        guarantee.put("attempts", 10);
        guarantee.put("minimum", "平庸");
        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("enabled", false);
        quality.put("guarantee", guarantee);
        skeleton.put("quality", quality);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("item_source", "minecraft-iron_block");
        output.put("amount", 1);
        Map<String, Object> success = new LinkedHashMap<>();
        success.put("outputs", List.of(output));
        success.put("name_actions", List.of());
        success.put("lore_actions", List.of());
        success.put("actions", List.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        skeleton.put("result", result);
        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("pre", List.of());
        actions.put("failure", List.of());
        skeleton.put("actions", actions);
        return skeleton;
    }
}

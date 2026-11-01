package emaki.jiuwu.craft.corelib.editor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;

public interface EditorModule {

    String rootCommand();

    List<EditorBrowserRoot> roots();

    EditorMenuMeta meta(String menuId);

    List<EditorFieldSpec> fields(EditorSession session, String menuId);

    String homeMenuId();

    String titleKey(String menuId);

    EditorValidator validator(EditorBrowserRoot root, Path file);

    void reload();

    default String templateId(String menuId) {
        return EditorBrowser.MENU_ROOT_SELECT.equals(menuId)
                ? EditorService.TEMPLATE_BROWSER
                : EditorService.TEMPLATE_PAGE;
    }

    default String browserTemplateId() {
        return EditorService.TEMPLATE_BROWSER;
    }

    default List<EditorField> extraFields(EditorSession session, String menuId) {
        return List.of();
    }

    default Material fieldIcon(EditorField field) {
        return null;
    }

    default List<String> valueLines(EditorSession session, EditorField field, YamlTextDocument draft) {
        return null;
    }

    default ItemStack renderCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot) {
        return null;
    }

    default boolean handleCustomSlot(EditorSession session, String menuId, GuiTemplate.ResolvedSlot slot,
            GuiClickContext click) {
        return false;
    }

    default boolean handleFieldOverride(EditorSession session, EditorField field, GuiClickContext click) {
        return false;
    }

    default String validateMutation(EditorSession session, EditorField field, YamlTextDocument draft) {
        return null;
    }

    default String validateInput(EditorSession session, String listPath, String text) {
        return null;
    }

    default String fieldHint(EditorSession session, EditorFieldSpec spec) {
        return null;
    }

    default String listHint(EditorSession session, String listPath, String current) {
        return null;
    }

    default String scopeForMenu(String menuId) {
        return EditorSession.SCOPE_DEFAULT;
    }

    default Map<String, Object> skeleton(EditorBrowserRoot root, String id) {
        return null;
    }

    default EditorDocument openSecondary(EditorSession session, Path file) {
        return null;
    }
}

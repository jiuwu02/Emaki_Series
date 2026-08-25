package emaki.jiuwu.craft.accessory.loader;

import java.io.File;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.accessory.model.AccessoryPage;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;

public final class AccessoryPageLoader extends YamlDirectoryLoader<AccessoryPage> {

    public AccessoryPageLoader(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "pages";
    }

    @Override
    protected String typeName() {
        return "accessory page";
    }

    @Override
    protected String idOf(AccessoryPage value) {
        return value == null ? null : value.pageId();
    }

    @Override
    protected AccessoryPage parse(File file, YamlSection configuration) {
        if (configuration == null) {
            return null;
        }
        String pageId = Texts.normalizeId(configuration.getString("page_id", ""));
        if (Texts.isBlank(pageId)) {
            return null;
        }
        AccessoryPage page = new AccessoryPage(
                pageId,
                Texts.toStringSafe(configuration.getString("display_name", "")),
                configuration.getInt("order", Integer.MAX_VALUE),
                configuration.getString("gui_template", AccessoryPage.DEFAULT_TEMPLATE),
                configuration.getString("permission", ""),
                configuration.getStringList("parts")
        );
        if (page.parts().isEmpty()) {
            issue("accessory.page_without_parts", Map.of(
                    "file", file.getName(),
                    "page", pageId
            ));
            return null;
        }
        return page;
    }
}

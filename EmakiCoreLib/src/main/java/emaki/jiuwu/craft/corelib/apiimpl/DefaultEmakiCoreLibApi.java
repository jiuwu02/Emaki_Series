package emaki.jiuwu.craft.corelib.apiimpl;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class DefaultEmakiCoreLibApi implements EmakiCoreLibApi.Bridge {

    private final EmakiCoreLibPlugin plugin;

    public DefaultEmakiCoreLibApi(EmakiCoreLibPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String apiVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String pluginName() {
        return plugin.getName();
    }

    @Override
    public boolean isReady() {
        return plugin.isEnabled() && plugin.messageService() != null;
    }

    @Override
    public String itemDisplayName(String itemSource) {
        ItemSource source = ItemSourceUtil.parse(itemSource);
        String displayName = plugin.itemSourceService().displayName(source);
        return Texts.isBlank(displayName) ? Texts.toStringSafe(itemSource) : displayName;
    }

    @Override
    public String itemDisplayName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "";
        }
        ItemSource source = plugin.itemSourceService().identifyItem(itemStack);
        String displayName = plugin.itemSourceService().displayName(source);
        return Texts.isBlank(displayName) ? ItemTextBridge.effectiveNameText(itemStack) : displayName;
    }
}

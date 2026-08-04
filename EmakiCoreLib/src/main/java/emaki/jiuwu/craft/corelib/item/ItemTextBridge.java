package emaki.jiuwu.craft.corelib.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.item.ItemTextBridge}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 17 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.item.ItemTextBridge}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class ItemTextBridge {

    private ItemTextBridge() {
    }

    public static boolean hasCustomName(ItemMeta itemMeta) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.hasCustomName(itemMeta);
    }

    public static Component customName(ItemMeta itemMeta) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.customName(itemMeta);
    }

    public static void customName(ItemMeta itemMeta, Component name) {
        emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.customName(itemMeta, name);
    }

    public static void customNameText(ItemMeta itemMeta, String name) {
        emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.customNameText(itemMeta, name);
    }

    public static List<Component> lore(ItemMeta itemMeta) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.lore(itemMeta);
    }

    public static void lore(ItemMeta itemMeta, List<Component> lore) {
        emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.lore(itemMeta, lore);
    }

    public static List<String> loreLines(ItemMeta itemMeta) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.loreLines(itemMeta);
    }

    public static void setLoreLines(ItemMeta itemMeta, List<String> loreLines) {
        emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.setLoreLines(itemMeta, loreLines);
    }

    public static Component effectiveName(ItemStack itemStack) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.effectiveName(itemStack);
    }

    public static String effectiveNameText(ItemStack itemStack) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.effectiveNameText(itemStack);
    }

    public static String effectiveNamePlain(ItemStack itemStack) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.effectiveNamePlain(itemStack);
    }

    public static Component displayWithItemHover(ItemStack itemStack) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.displayWithItemHover(itemStack);
    }

    public static Component displayWithItemHover(Component display, ItemStack itemStack) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.displayWithItemHover(display, itemStack);
    }

    public static String displayWithItemHoverText(ItemStack itemStack) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.displayWithItemHoverText(itemStack);
    }

    public static String displayWithItemHoverText(String displayText, ItemStack itemStack) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.displayWithItemHoverText(displayText, itemStack);
    }

    public static String itemTranslationKey(Material material) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.itemTranslationKey(material);
    }

    public static String blockTranslationKey(Material material) {
        return emaki.jiuwu.craft.corelib.api.item.ItemTextBridge.blockTranslationKey(material);
    }
}

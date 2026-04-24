package emaki.jiuwu.craft.corelib.text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public final class AdventureSupport {

    private static final Map<Plugin, BukkitAudiences> AUDIENCES = new ConcurrentHashMap<>();

    private AdventureSupport() {
    }

    public static void sendMessage(Plugin plugin, CommandSender sender, Component component) {
        if (plugin == null || sender == null || component == null) {
            return;
        }
        try {
            audiences(plugin).sender(sender).sendMessage(component);
            return;
        } catch (Exception ignored) {
        }
        sender.sendMessage(sender instanceof Player ? MiniMessages.legacy(component) : MiniMessages.plain(component));
    }

    public static void sendMiniMessage(Plugin plugin, CommandSender sender, String text) {
        if (plugin == null || sender == null || Texts.isBlank(text)) {
            return;
        }
        sendMessage(plugin, sender, MiniMessages.parse(text));
    }

    public static void broadcast(Plugin plugin, Component component) {
        if (plugin == null || component == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendMessage(plugin, player, component);
        }
        sendMessage(plugin, Bukkit.getConsoleSender(), component);
    }

    public static void sendActionBar(Plugin plugin, Player player, Component component) {
        if (plugin == null || player == null || component == null) {
            return;
        }
        try {
            audiences(plugin).player(player).sendActionBar(component);
            return;
        } catch (Exception ignored) {
        }
        player.sendMessage(MiniMessages.legacy(component));
    }

    public static void sendActionBar(Plugin plugin, Player player, String miniMessageText) {
        if (plugin == null || player == null || Texts.isBlank(miniMessageText)) {
            return;
        }
        sendActionBar(plugin, player, MiniMessages.parse(miniMessageText));
    }

    public static void showTitle(Plugin plugin, Player player, Title title) {
        if (plugin == null || player == null || title == null) {
            return;
        }
        audiences(plugin).player(player).showTitle(title);
    }

    public static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return Bukkit.createInventory(holder, size, MiniMessages.legacy(title));
    }

    public static void close(Plugin plugin) {
        if (plugin == null) {
            return;
        }
        BukkitAudiences audiences = AUDIENCES.remove(plugin);
        if (audiences != null) {
            audiences.close();
        }
    }

    private static BukkitAudiences audiences(Plugin plugin) {
        return AUDIENCES.computeIfAbsent(plugin, BukkitAudiences::create);
    }
}

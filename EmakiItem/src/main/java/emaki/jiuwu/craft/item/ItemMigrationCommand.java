package emaki.jiuwu.craft.item;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.model.EmakiItemAlias;

final class ItemMigrationCommand {

    private final EmakiItemPlugin plugin;
    private final ItemCommandExecutors executors;

    ItemMigrationCommand(EmakiItemPlugin plugin, ItemCommandExecutors executors) {
        this.plugin = plugin;
        this.executors = executors;
    }

    boolean handleAlias(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ItemCommandRouter.PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            plugin.messageService().sendRaw(sender, "<gray>EmakiItem aliases: <white>" + plugin.aliasLoader().all().size() + "</white></gray>");
            for (EmakiItemAlias alias : plugin.aliasLoader().all().values()) {
                plugin.messageService().sendRaw(sender, "<gray>- <aqua>" + alias.oldId() + "</aqua> -> <green>" + alias.targetId() + "</green></gray>");
            }
            return true;
        }
        if ("add".equalsIgnoreCase(args[1]) && args.length >= 4) {
            String oldId = Texts.normalizeId(args[2]);
            String newId = Texts.normalizeId(args[3]);
            if (plugin.itemLoader().get(newId) == null) {
                plugin.messageService().send(sender, "general.item_not_found", Map.of("id", newId));
                return true;
            }
            plugin.aliasLoader().put(oldId, newId);
            plugin.itemFactory().clearCache();
            plugin.messageService().sendRaw(sender, "<green>Alias 已添加：</green> <aqua>" + oldId + "</aqua> -> <green>" + newId + "</green>");
            return true;
        }
        if ("remove".equalsIgnoreCase(args[1]) && args.length >= 3) {
            String oldId = Texts.normalizeId(args[2]);
            boolean removed = plugin.aliasLoader().remove(oldId);
            plugin.itemFactory().clearCache();
            plugin.messageService().sendRaw(sender, removed ? "<green>Alias 已删除：</green> <aqua>" + oldId + "</aqua>" : "<yellow>Alias 不存在：</yellow> <aqua>" + oldId + "</aqua>");
            return true;
        }
        plugin.messageService().sendRaw(sender, "<red>用法：</red> /ei alias list | add <old> <new> | remove <old>");
        return true;
    }

    boolean handleMigrate(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ItemCommandRouter.PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length >= 5 && "id".equalsIgnoreCase(args[1])) {
            String oldId = Texts.normalizeId(args[2]);
            String newId = Texts.normalizeId(args[3]);
            String mode = args[4].toLowerCase(Locale.ROOT);
            try {
                if ("--dry-run".equals(mode)) {
                    Map<String, Object> preview = plugin.migrationService().preview(oldId, newId);
                    plugin.messageService().sendRaw(sender, "<gray>迁移预览：<aqua>" + oldId + "</aqua> -> <green>" + newId + "</green>，替换数 <white>" + preview.get("replacementCount") + "</white></gray>");
                    for (Object file : (List<?>) preview.getOrDefault("files", List.of())) {
                        plugin.messageService().sendRaw(sender, "<gray>- " + file + "</gray>");
                    }
                    return true;
                }
                if ("--apply".equals(mode)) {
                    Map<String, Object> result = plugin.migrationService().apply(oldId, newId, true, true);
                    plugin.aliasLoader().load();
                    plugin.itemFactory().clearCache();
                    plugin.messageService().sendRaw(sender, "<green>迁移完成：</green> 替换数 <white>" + result.get("replacementCount") + "</white>，并保留 alias。");
                    return true;
                }
            } catch (Exception exception) {
                plugin.messageService().sendRaw(sender, "<red>迁移失败：</red> " + MiniMessages.escape(exception.getMessage()));
                return true;
            }
        }
        if (args.length >= 3 && "inventory".equalsIgnoreCase(args[1])) {
            if ("all".equalsIgnoreCase(args[2])) {
                List<Player> targets = List.copyOf(Bukkit.getOnlinePlayers());
                if (targets.isEmpty()) {
                    plugin.messageService().sendRaw(sender, "<green>在线玩家背包迁移完成：</green> 0 件物品。");
                    return true;
                }
                AtomicInteger totalChanged = new AtomicInteger();
                AtomicInteger remaining = new AtomicInteger(targets.size());
                IntConsumer complete = changed -> {
                    totalChanged.addAndGet(changed);
                    if (remaining.decrementAndGet() == 0) {
                        executors.runForSender(sender, () -> plugin.messageService().sendRaw(sender,
                                "<green>在线玩家背包迁移完成：</green> " + totalChanged.get() + " 件物品。"));
                    }
                };
                for (Player target : targets) {
                    boolean accepted = executors.runForPlayer(target, "migrate_inventory", () -> {
                        int changed = target.isOnline() ? plugin.migrationService().migrateInventory(target) : 0;
                        int refreshed = target.isOnline() ? plugin.setService().refreshEquippedSets(target, "command") : 0;
                        if (changed + refreshed > 0) {
                            plugin.scheduleAttributeEquipmentSync(target);
                        }
                        complete.accept(changed);
                    });
                    if (!accepted) {
                        complete.accept(0);
                    }
                }
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.messageService().send(sender, "general.player_not_found");
                return true;
            }
            executors.runForPlayer(target, "migrate_inventory", () -> {
                int changed = plugin.migrationService().migrateInventory(target);
                int refreshed = plugin.setService().refreshEquippedSets(target, "command");
                if (changed + refreshed > 0) {
                    plugin.scheduleAttributeEquipmentSync(target);
                }
                executors.runForSender(sender, () -> plugin.messageService().sendRaw(sender,
                        "<green>背包迁移完成：</green> " + target.getName() + " / " + changed + " 件物品。"));
            });
            return true;
        }
        plugin.messageService().sendRaw(sender, "<red>用法：</red> /ei migrate id <old> <new> --dry-run|--apply 或 /ei migrate inventory <player|all>");
        return true;
    }
}

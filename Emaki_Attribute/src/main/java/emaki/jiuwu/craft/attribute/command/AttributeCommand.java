package emaki.jiuwu.craft.attribute.command;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

public final class AttributeCommand implements TabExecutor {

    private final EmakiAttributePlugin plugin;
    private final AttributeService attributeService;

    public AttributeCommand(EmakiAttributePlugin plugin, AttributeService attributeService) {
        this.plugin = plugin;
        this.attributeService = attributeService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "resync" -> handleResync(sender, args);
            case "preview" -> handlePreview(sender, args);
            case "dump" -> handleDump(sender, args);
            case "lint" -> handleLint(sender);
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            default -> {
                sender.sendMessage(prefix() + ChatColor.RED + "未知子命令，输入 /" + label + " help 查看帮助。");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String candidate : List.of("help", "reload", "resync", "preview", "dump", "lint")) {
                if (candidate.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(candidate);
                }
            }
            return result;
        }
        if (args.length == 2 && "resync".equalsIgnoreCase(args[0])) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(player.getName());
                }
            }
            result.add("all");
            return result;
        }
        if (args.length == 2 && "preview".equalsIgnoreCase(args[0])) {
            for (String candidate : List.of("hand", "offhand", "helmet", "chestplate", "leggings", "boots")) {
                if (candidate.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(candidate);
                }
            }
            return result;
        }
        if (args.length == 2 && "dump".equalsIgnoreCase(args[0])) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(player.getName());
                }
            }
            return result;
        }
        return result;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("emakiattribute.reload") && !sender.hasPermission("emakiattribute.admin")) {
            sender.sendMessage(prefix() + ChatColor.RED + "你没有重载权限。");
            return true;
        }
        plugin.reloadPluginState(true);
        sender.sendMessage(prefix() + ChatColor.GREEN + "已重载属性配置。");
        sender.sendMessage(prefix() + ChatColor.GRAY + "属性数量: " + attributeService.attributeRegistry().all().size()
            + ", 伤害类型: " + attributeService.damageTypeRegistry().all().size()
            + ", 默认组: " + attributeService.defaultProfileRegistry().all().size());
        return true;
    }

    private boolean handleResync(CommandSender sender, String[] args) {
        if (!sender.hasPermission("emakiattribute.resync") && !sender.hasPermission("emakiattribute.admin")) {
            sender.sendMessage(prefix() + ChatColor.RED + "你没有重同步权限。");
            return true;
        }
        if (args.length < 2) {
            if (sender instanceof Player player) {
                attributeService.resyncPlayer(player);
                sender.sendMessage(prefix() + ChatColor.GREEN + "已同步你的属性状态。");
                return true;
            }
            sender.sendMessage(prefix() + ChatColor.YELLOW + "控制台需要指定玩家或 all。");
            return true;
        }
        if ("all".equalsIgnoreCase(args[1])) {
            attributeService.resyncAllPlayers();
            sender.sendMessage(prefix() + ChatColor.GREEN + "已同步所有在线玩家。");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(prefix() + ChatColor.RED + "找不到玩家: " + args[1]);
            return true;
        }
        attributeService.resyncPlayer(target);
        sender.sendMessage(prefix() + ChatColor.GREEN + "已同步玩家 " + target.getName() + "。");
        return true;
    }

    private boolean handlePreview(CommandSender sender, String[] args) {
        Player player;
        String slot;
        if (sender instanceof Player self) {
            player = self;
            slot = args.length >= 2 ? args[1] : "hand";
        } else {
            if (args.length < 3) {
                sender.sendMessage(prefix() + ChatColor.YELLOW + "控制台用法: /emakiattribute preview <player> <slot>");
                return true;
            }
            player = Bukkit.getPlayerExact(args[1]);
            if (player == null) {
                sender.sendMessage(prefix() + ChatColor.RED + "找不到玩家: " + args[1]);
                return true;
            }
            slot = args[2];
        }
        ItemStack itemStack = resolveItem(player, slot);
        if (itemStack == null || itemStack.getType().isAir()) {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "该槽位没有物品。");
            return true;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        AttributeSnapshot snapshot = itemMeta != null && itemMeta.hasLore()
            ? attributeService.loreParser().parse(itemMeta.getLore()).snapshot()
            : AttributeSnapshot.empty("");
        sender.sendMessage(prefix() + ChatColor.AQUA + "物品: " + itemStack.getType().name());
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            sender.sendMessage(prefix() + ChatColor.GRAY + "名称: " + itemMeta.getDisplayName());
        }
        sender.sendMessage(prefix() + ChatColor.GRAY + "签名: " + snapshot.sourceSignature());
        sender.sendMessage(prefix() + ChatColor.GRAY + "值: " + snapshot.values());
        return true;
    }

    private boolean handleDump(CommandSender sender, String[] args) {
        if (!sender.hasPermission("emakiattribute.debug") && !sender.hasPermission("emakiattribute.admin")) {
            sender.sendMessage(prefix() + ChatColor.RED + "你没有导出权限。");
            return true;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(prefix() + ChatColor.RED + "找不到玩家: " + args[1]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(prefix() + ChatColor.YELLOW + "控制台用法: /emakiattribute dump <player>");
            return true;
        }
        AttributeSnapshot snapshot = attributeService.collectCombatSnapshot(target);
        sender.sendMessage(prefix() + ChatColor.AQUA + "玩家: " + target.getName());
        sender.sendMessage(prefix() + ChatColor.GRAY + "快照: " + snapshot.sourceSignature());
        sender.sendMessage(prefix() + ChatColor.GRAY + "属性: " + snapshot.values());
        for (Map.Entry<String, ResourceState> entry : dumpResources(target).entrySet()) {
            ResourceState state = entry.getValue();
            sender.sendMessage(prefix() + ChatColor.GRAY + entry.getKey() + " => default=" + state.defaultMax()
                + ", bonus=" + state.bonusMax()
                + ", currentMax=" + state.currentMax()
                + ", current=" + state.currentValue());
        }
        return true;
    }

    private boolean handleLint(CommandSender sender) {
        if (!sender.hasPermission("emakiattribute.debug") && !sender.hasPermission("emakiattribute.admin")) {
            sender.sendMessage(prefix() + ChatColor.RED + "你没有调试权限。");
            return true;
        }
        sender.sendMessage(prefix() + ChatColor.AQUA + "配置状态: " + plugin.configModel());
        reportIssues(sender, "属性", attributeService.attributeRegistry().issues());
        reportIssues(sender, "伤害类型", attributeService.damageTypeRegistry().issues());
        reportIssues(sender, "默认组", attributeService.defaultProfileRegistry().issues());
        reportIssues(sender, "词条格式", attributeService.loreFormatRegistry().issues());
        reportIssues(sender, "预设", attributeService.presetRegistry().issues());
        return true;
    }

    private void reportIssues(CommandSender sender, String name, List<String> issues) {
        if (issues.isEmpty()) {
            sender.sendMessage(prefix() + ChatColor.GREEN + name + "配置无错误。");
            return;
        }
        sender.sendMessage(prefix() + ChatColor.YELLOW + name + "配置问题: " + issues.size());
        for (String issue : issues) {
            sender.sendMessage(prefix() + ChatColor.GRAY + "- " + issue);
        }
    }

    private Map<String, ResourceState> dumpResources(Player player) {
        Map<String, ResourceState> resources = new java.util.LinkedHashMap<>();
        attributeService.resourceDefinitions().forEach((id, definition) -> {
            ResourceState state = attributeService.readResourceState(player, id);
            if (state != null) {
                resources.put(id, state);
            }
        });
        return resources;
    }

    private ItemStack resolveItem(Player player, String slot) {
        PlayerInventory inventory = player.getInventory();
        if (slot == null) {
            return inventory.getItemInMainHand();
        }
        return switch (slot.toLowerCase(Locale.ROOT)) {
            case "hand", "main", "mainhand" -> inventory.getItemInMainHand();
            case "off", "offhand" -> inventory.getItemInOffHand();
            case "head", "helmet" -> inventory.getHelmet();
            case "chest", "chestplate" -> inventory.getChestplate();
            case "legs", "leggings" -> inventory.getLeggings();
            case "boots" -> inventory.getBoots();
            default -> inventory.getItemInMainHand();
        };
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(prefix() + ChatColor.AQUA + "/emakiattribute reload" + ChatColor.GRAY + " 重载配置");
        sender.sendMessage(prefix() + ChatColor.AQUA + "/emakiattribute resync [all|玩家]" + ChatColor.GRAY + " 重新同步属性");
        sender.sendMessage(prefix() + ChatColor.AQUA + "/emakiattribute preview [槽位]" + ChatColor.GRAY + " 预览物品词条解析");
        sender.sendMessage(prefix() + ChatColor.AQUA + "/emakiattribute dump [玩家]" + ChatColor.GRAY + " 导出快照与资源");
        sender.sendMessage(prefix() + ChatColor.AQUA + "/emakiattribute lint" + ChatColor.GRAY + " 检查配置错误");
    }

    private String prefix() {
        return ChatColor.DARK_AQUA + "[Emaki_Attribute] " + ChatColor.RESET;
    }
}

package emaki.jiuwu.craft.skills;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;

final class SkillsCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakiskills";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";

    private final EmakiSkillsPlugin plugin;

    SkillsCommandRouter(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "gui" -> handleGui(sender);
            case "reload" -> handleReload(sender);
            case "castmode" -> handleCastMode(sender, args);
            case "debug" -> handleDebug(sender, args);
            case "inspect" -> handleInspect(sender, args);
            case "clearslot" -> handleClearSlot(sender, args);
            case "resync" -> handleResync(sender, args);
            default -> {
                plugin.messageService().send(sender, "general.unknown_command");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("help", "gui", "reload", "castmode", "debug", "inspect", "clearslot", "resync")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "castmode" -> {
                    for (String sub : List.of("on", "off", "toggle")) {
                        if (sub.startsWith(args[1].toLowerCase())) {
                            result.add(sub);
                        }
                    }
                }
                case "debug", "inspect", "resync" -> completePlayers(result, args[1]);
                case "clearslot" -> completePlayers(result, args[1]);
                default -> {
                }
            }
            return result;
        }
        if (args.length == 3) {
            if ("clearslot".equalsIgnoreCase(args[0])) {
                for (int i = 0; i < 9; i++) {
                    String slot = String.valueOf(i);
                    if (slot.startsWith(args[2])) {
                        result.add(slot);
                    }
                }
            }
            return result;
        }
        return result;
    }

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (!plugin.skillsGuiService().open(player)) {
            plugin.messageService().send(sender, "gui.open_failed");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.reloadPluginState(true);
        plugin.messageService().send(sender, "general.reload_success");
        plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                "skills", plugin.skillDefinitionLoader().all().size(),
                "resources", plugin.localResourceDefinitionLoader().all().size(),
                "guis", plugin.guiTemplateLoader().all().size()
        )));
        return true;
    }

    private boolean handleCastMode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "on" -> {
                plugin.castModeService().setCastMode(player, true);
                plugin.messageService().send(sender, "command.castmode.enabled");
            }
            case "off" -> {
                plugin.castModeService().setCastMode(player, false);
                plugin.messageService().send(sender, "command.castmode.disabled");
            }
            case "toggle" -> {
                plugin.castModeService().toggleCastMode(player);
                boolean nowEnabled = plugin.castModeService().isCastModeEnabled(player);
                plugin.messageService().send(sender,
                        nowEnabled ? "command.castmode.enabled" : "command.castmode.disabled");
            }
            default -> plugin.messageService().send(sender, "general.invalid_args");
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        PlayerSkillProfile profile = plugin.playerSkillDataStore().get(target);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.debug.header", Map.of("player", target.getName())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.debug.line", Map.of(
                "key", "cast_mode",
                "value", profile != null && profile.castModeEnabled() ? "ON" : "OFF"
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.debug.line", Map.of(
                "key", "slot_count",
                "value", profile != null ? profile.bindings().size() : 0
        )));
        List<UnlockedSkillEntry> unlocked = plugin.playerSkillStateService().getUnlockedSkills(target);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.debug.line", Map.of(
                "key", "unlocked_skills",
                "value", unlocked.size()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.debug.line", Map.of(
                "key", "ea_bridge",
                "value", plugin.eaBridge() != null && plugin.eaBridge().isAvailable() ? "CONNECTED" : "DISABLED"
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.debug.line", Map.of(
                "key", "ea_bridge_mode",
                "value", plugin.eaBridge() != null && plugin.eaBridge().isAvailable()
                        ? plugin.eaBridge().providerMode()
                        : "DISABLED"
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.debug.line", Map.of(
                "key", "mythic_bridge",
                "value", plugin.mythicBridge() != null && plugin.mythicBridge().isAvailable() ? "CONNECTED" : "DISABLED"
        )));
        return true;
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        PlayerSkillProfile profile = plugin.playerSkillDataStore().get(target);
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.header", Map.of("player", target.getName())));
        if (profile == null) {
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                    "key", "profile", "value", "NOT LOADED"
            )));
            return true;
        }
        for (int i = 0; i < profile.bindings().size(); i++) {
            SkillSlotBinding binding = profile.getBinding(i);
            String slotInfo;
            if (binding == null || binding.isEmpty()) {
                slotInfo = "[空]";
            } else {
                String skillName = binding.skillId() != null ? binding.skillId() : "-";
                String triggerName = binding.triggerId() != null ? binding.triggerId() : "-";
                slotInfo = skillName + " -> " + triggerName;
            }
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                    "key", "slot_" + i,
                    "value", slotInfo
            )));
        }
        return true;
    }

    private boolean handleClearSlot(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[2]);
        } catch (NumberFormatException ignored) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        boolean success = plugin.playerSkillStateService().unequipSkill(target, slot);
        if (success) {
            plugin.messageService().send(sender, "command.clearslot.success");
        } else {
            plugin.messageService().send(sender, "command.clearslot.failed");
        }
        return true;
    }

    private boolean handleResync(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        plugin.playerSkillStateService().validateBindings(target);
        plugin.messageService().send(sender, "command.resync.success");
        return true;
    }

    private void completePlayers(List<String> result, String prefix) {
        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                .forEach(result::add);
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("help", "显示帮助信息");
        lines.put("gui", "打开技能 GUI");
        lines.put("reload", "重载技能配置与资源");
        lines.put("castmode <on|off|toggle>", "切换施法模式");
        lines.put("debug [player]", "查看调试信息");
        lines.put("inspect [player]", "查看玩家技能槽位状态");
        lines.put("clearslot <player> <slot>", "清除指定槽位的技能绑定");
        lines.put("resync [player]", "重新同步玩家技能池");
        lines.forEach((name, description) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.help.line", Map.of("cmd", name, "desc", description))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }
}

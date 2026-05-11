package emaki.jiuwu.craft.skills;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;

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
            case "upgrade" -> handleUpgrade(sender, args);
            case "level" -> handleLevel(sender, args);
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
            for (String sub : List.of("help", "gui", "reload", "castmode", "upgrade", "level",
                    "debug", "inspect", "clearslot", "resync")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
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
                case "upgrade" -> completeUpgradeableSkills(sender, result, args[1]);
                case "level" -> completeLiteral(result, args[1], "get", "set", "add");
                case "inspect", "resync" -> completePlayers(result, args[1]);
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
            } else if ("level".equalsIgnoreCase(args[0])) {
                completePlayers(result, args[2]);
            }
            return result;
        }
        if (args.length == 4 && "level".equalsIgnoreCase(args[0])) {
            completeSkillIds(result, args[3]);
            return result;
        }
        if (args.length == 5 && "level".equalsIgnoreCase(args[0])
                && ("set".equalsIgnoreCase(args[1]) || "add".equalsIgnoreCase(args[1]))) {
            completeLiteral(result, args[4], "1", "2", "5", "10");
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

    private boolean handleUpgrade(CommandSender sender, String[] args) {
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
        SkillUpgradeService.UpgradeResult result = plugin.skillUpgradeService().upgrade(player, args[1]);
        plugin.messageService().send(sender, result.messageKey(), result.placeholders());
        return true;
    }

    private boolean handleLevel(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 4) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        String action = args[1].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        String skillId = Texts.normalizeId(args[3]);
        SkillDefinition definition = plugin.skillRegistryService().getDefinition(skillId);
        if (definition == null) {
            plugin.messageService().send(sender, "skill.not_found", Map.of("skill_id", skillId));
            return true;
        }
        return switch (action) {
            case "get" -> {
                int currentLevel = plugin.skillLevelService().currentLevel(target, definition);
                plugin.messageService().send(sender, "command.level.get", Map.of(
                        "player", target.getName(),
                        "skill_id", definition.id(),
                        "skill", definition.displayName(),
                        "level", currentLevel,
                        "max_level", plugin.skillLevelService().maxLevel(definition)
                ));
                yield true;
            }
            case "set", "add" -> handleLevelMutation(sender, args, action, target, definition);
            default -> {
                plugin.messageService().send(sender, "general.invalid_args");
                yield true;
            }
        };
    }

    private boolean handleLevelMutation(CommandSender sender,
            String[] args,
            String action,
            Player target,
            SkillDefinition definition) {
        if (args.length < 5) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        int value;
        try {
            value = Integer.parseInt(args[4]);
        } catch (NumberFormatException _) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        int level = "set".equals(action)
                ? plugin.skillLevelService().setLevel(target, definition, value)
                : plugin.skillLevelService().addLevel(target, definition, value);
        plugin.playerSkillDataStore().save(target);
        plugin.messageService().send(sender, "command.level.changed", Map.of(
                "player", target.getName(),
                "skill_id", definition.id(),
                "skill", definition.displayName(),
                "level", level,
                "max_level", plugin.skillLevelService().maxLevel(definition)
        ));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        plugin.reloadPluginStateAsync(true, progress -> {
            if (progress != null && !progress.isBlank()) {
                plugin.messageService().sendRaw(sender, progress);
            }
        }).thenRun(() -> {
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                    "skills", plugin.skillDefinitionLoader().all().size(),
                    "resources", plugin.localResourceDefinitionLoader().all().size(),
                    "guis", plugin.guiTemplateLoader().all().size()
            )));
        }).exceptionally(throwable -> {
            plugin.messageService().send(sender, "general.reload_failed");
            plugin.getLogger().warning("[Reload] Async reload failed: " + throwable.getMessage());
            return null;
        });
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
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
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
                slotInfo = plugin.messageService().message("command.inspect.empty_slot");
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
        } catch (NumberFormatException _) {
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

    private void completeUpgradeableSkills(CommandSender sender, List<String> result, String prefix) {
        if (sender instanceof Player player) {
            plugin.playerSkillStateService().getUnlockedSkills(player).stream()
                    .map(UnlockedSkillEntry::skillId)
                    .filter(skillId -> {
                        SkillDefinition definition = plugin.skillRegistryService().getDefinition(skillId);
                        return definition != null
                                && definition.upgrade().enabled()
                                && !plugin.skillLevelService().isMaxLevel(player, definition);
                    })
                    .filter(skillId -> skillId.toLowerCase().startsWith(prefix.toLowerCase()))
                    .distinct()
                    .forEach(result::add);
            return;
        }
        completeSkillIds(result, prefix);
    }

    private void completeSkillIds(List<String> result, String prefix) {
        String lowered = prefix == null ? "" : prefix.toLowerCase();
        plugin.skillRegistryService().allDefinitions().keySet().stream()
                .filter(skillId -> skillId.toLowerCase().startsWith(lowered))
                .forEach(result::add);
    }

    private void completeLiteral(List<String> result, String prefix, String... values) {
        String lowered = prefix == null ? "" : prefix.toLowerCase();
        for (String value : values) {
            if (value.toLowerCase().startsWith(lowered)) {
                result.add(value);
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        var ms = plugin.messageService();
        ms.sendRaw(sender, ms.message("command.help.header"));
        Map<String, String> commands = new LinkedHashMap<>();
        commands.put("help", "command.help.desc.help");
        commands.put("gui", "command.help.desc.gui");
        commands.put("reload", "command.help.desc.reload");
        commands.put("castmode <on|off|toggle>", "command.help.desc.castmode");
        commands.put("upgrade <skill>", "command.help.desc.upgrade");
        commands.put("level get|set|add <player> <skill> [value]", "command.help.desc.level");
        commands.put("debug <status|player|module|all> [...]", "command.help.desc.debug");
        commands.put("inspect [player]", "command.help.desc.inspect");
        commands.put("clearslot <player> <slot>", "command.help.desc.clearslot");
        commands.put("resync [player]", "command.help.desc.resync");
        commands.forEach((cmd, descKey) -> ms.sendRaw(sender,
                ms.message("command.help.line", Map.of("cmd", cmd, "desc", ms.message(descKey)))));
        ms.sendRaw(sender, ms.message("command.help.footer"));
    }
}

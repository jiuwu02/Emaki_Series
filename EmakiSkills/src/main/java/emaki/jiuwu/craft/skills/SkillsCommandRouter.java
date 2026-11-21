package emaki.jiuwu.craft.skills;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport.Guard;
import emaki.jiuwu.craft.corelib.log.LogTag;

final class SkillsCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakiskills";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_EDIT = PERMISSION_ROOT + ".edit";

    private final EmakiSkillsPlugin plugin;

    SkillsCommandRouter(EmakiSkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpPage(sender, helpRoot(label), List.of());
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelpPage(sender, helpRoot(label), Arrays.asList(args).subList(1, args.length));
                yield true;
            }
            case "gui" -> handleGui(sender);
            case "reload" -> handleReload(sender);
            case "edit" -> handleEdit(sender, args);
            case "castmode" -> handleCastMode(sender, args);
            case "upgrade" -> handleUpgrade(sender, args);
            case "level" -> handleLevel(sender, args);
            case "grant" -> handleGrantRevoke(sender, args, true);
            case "revoke" -> handleGrantRevoke(sender, args, false);
            case "list" -> handleList(sender, args);
            case "reset" -> handleReset(sender, args);
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
            return CommandTabHelper.completeSubcommands(
                    List.of("help", "gui", "reload", "edit", "castmode", "upgrade", "level",
                            "grant", "revoke", "list", "reset", "debug", "inspect", "clearslot", "resync"),
                    args[0]);
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "castmode" -> {
                    for (String sub : List.of("on", "off", "toggle")) {
                        if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            result.add(sub);
                        }
                    }
                }
                case "upgrade" -> completeUpgradeableSkills(sender, result, args[1]);
                case "edit" -> {
                    if (plugin.editorModule() != null) {
                        result.addAll(CommandTabHelper.filterByPrefix(
                                plugin.editorModule().knownIds(), args[1]));
                    }
                }
                case "level" -> result.addAll(CommandTabHelper.completeLiterals(args[1], "get", "set", "add"));
                case "grant", "revoke", "list", "reset", "inspect", "resync" ->
                        result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
                case "clearslot" -> result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
                default -> {
                }
            }
            return result;
        }
        if (args.length == 3) {
            if ("clearslot".equalsIgnoreCase(args[0])) {
                int slotCount = plugin.appConfig().defaultSlotCount();
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target != null) {
                    var profile = plugin.playerSkillStateService().getProfile(target);
                    if (profile != null) {
                        slotCount = profile.bindings().size();
                    }
                }
                result.addAll(CommandTabHelper.completeIntRange(args[2], 0, Math.max(0, slotCount - 1)));
            } else if ("level".equalsIgnoreCase(args[0])) {
                result.addAll(CommandTabHelper.completeOnlinePlayers(args[2]));
            } else if ("grant".equalsIgnoreCase(args[0]) || "revoke".equalsIgnoreCase(args[0])) {
                completeSkillIds(result, args[2]);
            }
            return result;
        }
        if (args.length == 4 && "level".equalsIgnoreCase(args[0])) {
            completeSkillIds(result, args[3]);
            return result;
        }
        if (args.length == 5 && "level".equalsIgnoreCase(args[0])
                && ("set".equalsIgnoreCase(args[1]) || "add".equalsIgnoreCase(args[1]))) {
            result.addAll(CommandTabHelper.completeLiterals(args[4], "1", "2", "5", "10"));
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length < 4) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
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
                callForPlayer(target, () -> Map.<String, Object>of(
                        "player", target.getName(),
                        "skill_id", definition.id(),
                        "skill", definition.displayName(),
                        "level", plugin.skillLevelService().currentLevel(target, definition),
                        "max_level", plugin.skillLevelService().maxLevel(definition)
                )).whenComplete((placeholders, throwable) -> replyToLevelCommand(
                        sender, placeholders, throwable, "command.level.get"));
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
        callForPlayer(target, () -> {
            int level = "set".equals(action)
                    ? plugin.skillLevelService().setLevel(target, definition, value)
                    : plugin.skillLevelService().addLevel(target, definition, value);
            plugin.playerSkillDataStore().save(target);
            return Map.<String, Object>of(
                    "player", target.getName(),
                    "skill_id", definition.id(),
                    "skill", definition.displayName(),
                    "level", level,
                    "max_level", plugin.skillLevelService().maxLevel(definition)
            );
        }).whenComplete((placeholders, throwable) -> replyToLevelCommand(
                sender, placeholders, throwable, "command.level.changed"));
        return true;
    }

    private boolean handleGrantRevoke(CommandSender sender, String[] args, boolean grant) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
        String skillId = Texts.normalizeId(args[2]);
        SkillDefinition definition = plugin.skillRegistryService().getDefinition(skillId);
        if (definition == null) {
            plugin.messageService().send(sender, "skill.not_found", Map.of("skill_id", skillId));
            return true;
        }
        callForPlayer(target, () -> {
            boolean changed = grant
                    ? plugin.manualSkillSourceService().learn(target, definition.id())
                    : plugin.manualSkillSourceService().forget(target, definition.id());
            if (changed) {
                plugin.playerSkillDataStore().save(target);
            }
            return changed;
        }).whenComplete((changed, throwable) -> runForSender(sender, () -> {
            if (throwable != null) {
                plugin.messageService().warning(LogTag.COMMAND, "console.command_grant_revoke_failed",
                        Map.of("error", Texts.toStringSafe(throwable.getMessage())));
                plugin.messageService().send(sender, "general.invalid_args");
                return;
            }
            if (changed == null) {
                plugin.messageService().send(sender, "general.player_not_found");
                return;
            }
            String messageKey = grant
                    ? (changed ? "command.grant.success" : "command.grant.failed")
                    : (changed ? "command.revoke.success" : "command.revoke.failed");
            plugin.messageService().send(sender, messageKey, Map.<String, Object>of(
                    "player", target.getName(),
                    "skill", definition.displayName(),
                    "skill_id", definition.id()
            ));
        }));
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        PlayerSkillProfile profile = plugin.playerSkillDataStore().get(target);
        plugin.messageService().sendRaw(sender, plugin.messageService().message(
                "command.list.header", Map.of("player", target.getName())));
        if (profile == null) {
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.empty"));
            return true;
        }
        List<String> learned = profile.manualSkillIds().stream().sorted().toList();
        if (learned.isEmpty()) {
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.empty"));
        } else {
            for (String skillId : learned) {
                plugin.messageService().sendRaw(sender, plugin.messageService().message(
                        "command.list.learned", Map.of("skill_id", skillId)));
            }
        }
        for (int i = 0; i < profile.bindings().size(); i++) {
            SkillSlotBinding binding = profile.getBinding(i);
            if (binding == null || binding.isEmpty()) {
                continue;
            }
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.list.slot", Map.of(
                    "slot", String.valueOf(i),
                    "skill_id", binding.skillId() == null ? "-" : binding.skillId(),
                    "trigger_id", binding.triggerId() == null ? "-" : binding.triggerId()
            )));
        }
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        callForPlayer(target, () -> {
            int removed = plugin.manualSkillSourceService().forgetAll(target);
            if (removed > 0) {
                plugin.playerSkillDataStore().save(target);
            }
            return removed;
        }).whenComplete((removed, throwable) -> runForSender(sender, () -> {
            if (throwable != null) {
                plugin.messageService().warning(LogTag.COMMAND, "console.command_reset_failed",
                        Map.of("error", Texts.toStringSafe(throwable.getMessage())));
                plugin.messageService().send(sender, "general.invalid_args");
                return;
            }
            if (removed == null) {
                plugin.messageService().send(sender, "general.player_not_found");
                return;
            }
            plugin.messageService().send(sender, "command.reset.success", Map.<String, Object>of(
                    "player", target.getName(),
                    "count", removed
            ));
        }));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        long startTime = System.currentTimeMillis();
        plugin.reloadPluginStateAsync(true, progress -> {
            if (progress != null && !progress.isBlank()) {
                runForSender(sender, () -> plugin.messageService().sendRaw(sender, progress));
            }
        }).thenRun(() -> runForSender(sender, () -> {
            long elapsedMs = System.currentTimeMillis() - startTime;
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                    "skills", plugin.skillRegistryService().allDefinitions().size(),
                    "resources", plugin.localResourceDefinitionLoader().all().size(),
                    "guis", plugin.guiTemplateLoader().all().size()
            )));
            plugin.messageService().sendRaw(sender, plugin.messageService().message(
                    "general.reload_elapsed", Map.of("elapsed", elapsedMs)));
        })).exceptionally(throwable -> {
            runForSender(sender, () -> plugin.messageService().send(sender, "general.reload_failed"));
            plugin.messageService().warning(LogTag.RELOAD, "console.reload_async_failed",
                    Map.of("error", Texts.toStringSafe(throwable.getMessage())));
            return null;
        });
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        EditorCommandSupport.Guard editGuard = EditorCommandSupport.check(
                sender, () -> plugin.editorService() != null && plugin.editorModule() != null, PERMISSION_EDIT);
        if (editGuard == Guard.DENIED) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (editGuard == Guard.NON_PLAYER) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (editGuard == Guard.UNAVAILABLE) {
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            plugin.editorService().openBrowser(player);
            return true;
        }
        if (plugin.editorModule().openById(player, args[1]) == null) {
            plugin.messageService().send(sender, "editor.open_failed",
                    Map.of("id", Texts.normalizeId(args[1])));
        }
        return true;
    }

    private <T> CompletableFuture<T> callForPlayer(Player player, Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable operation = () -> {
            try {
                if (!player.isOnline()) {
                    future.complete(null);
                    return;
                }
                future.complete(task.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        try {
            if (plugin.scheduling().ownsEntity(player)) {
                operation.run();
                return future;
            }
            plugin.scheduling().runForEntity(plugin, player, operation,
                    () -> future.completeExceptionally(new RejectedExecutionException(
                            "技能命令的玩家操作在执行前已退役.")));
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private void replyToLevelCommand(CommandSender sender,
            Map<String, Object> placeholders,
            Throwable throwable,
            String messageKey) {
        runForSender(sender, () -> {
            if (throwable != null) {
                plugin.messageService().warning(LogTag.COMMAND, "console.command_level_failed",
                        Map.of("error", Texts.toStringSafe(throwable.getMessage())));
                plugin.messageService().send(sender, "general.invalid_args");
            } else if (placeholders == null) {
                plugin.messageService().send(sender, "general.player_not_found");
            } else {
                plugin.messageService().send(sender, messageKey, placeholders);
            }
        });
    }

    private void runForSender(CommandSender sender, Runnable task) {
        try {
            if (sender instanceof Player player) {
                if (plugin.scheduling().ownsEntity(player)) {
                    task.run();
                    return;
                }
                plugin.scheduling().runForEntity(plugin, player, task, () -> { });
                return;
            }
            if (plugin.scheduling().ownsGlobal()) {
                task.run();
                return;
            }
            plugin.scheduling().runGlobal(plugin, task);
        } catch (Throwable throwable) {
            plugin.messageService().warning(LogTag.COMMAND, "console.command_schedule_failed",
                    Map.of("error", Texts.toStringSafe(throwable.getMessage())));
        }
    }

    private boolean handleCastMode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
                    "key", "profile", "value", plugin.messageService().message("command.inspect.not_loaded")
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
                    .filter(skillId -> skillId.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                    .distinct()
                    .forEach(result::add);
            return;
        }
        completeSkillIds(result, prefix);
    }

    private void completeSkillIds(List<String> result, String prefix) {
        result.addAll(CommandTabHelper.filterByPrefix(plugin.skillRegistryService().allDefinitions().keySet(), prefix));
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "eskills" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_help", "command.help_entry_gui", "command.help_entry_reload",
                    "command.help_entry_edit", "command.help_entry_castmode", "command.help_entry_upgrade",
                    "command.help_entry_level", "command.help_entry_grant", "command.help_entry_revoke",
                    "command.help_entry_list", "command.help_entry_reset", "command.help_entry_debug",
                    "command.help_entry_inspect", "command.help_entry_clearslot", "command.help_entry_resync");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "castmode" -> {
                sendHelpLines(sender, root, "command.help_castmode_header",
                        "command.help_castmode_entry_on", "command.help_castmode_entry_off",
                        "command.help_castmode_entry_toggle");
                sendHelpFooter(sender);
            }
            case "level" -> {
                sendHelpLines(sender, root, "command.help_level_header",
                        "command.help_level_entry_get", "command.help_level_entry_set",
                        "command.help_level_entry_add");
                sendHelpFooter(sender);
            }
            case "upgrade" -> sendUpgradeSkillHelp(sender, root);
            case "reset" -> sendResetPlayerHelp(sender, root);
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendUpgradeSkillHelp(CommandSender sender, String root) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message("command.help_upgrade_skill_header", Map.of("root", root)));
        List<String> skillIds = new ArrayList<>(plugin.skillRegistryService().allDefinitions().keySet());
        skillIds.sort(Comparator.naturalOrder());
        if (skillIds.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String skillId : skillIds) {
            sendValueEntry(sender, skillId,
                    messageService.message("command.help_value_desc_upgrade_skill", Map.of("value", skillId)),
                    root + " upgrade " + skillId);
        }
        sendHelpFooter(sender);
    }

    private void sendResetPlayerHelp(CommandSender sender, String root) {
        sendPlayerValueHelp(sender, root, "command.help_reset_player_header",
                "command.help_value_desc_reset_player", "reset");
    }

    private void sendPlayerValueHelp(CommandSender sender, String root, String headerKey, String descKey, String runPrefix) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message(headerKey, Map.of("root", root)));
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName));
        if (players.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (Player player : players) {
            String name = player.getName();
            sendValueEntry(sender, name,
                    messageService.message(descKey, Map.of("value", name)),
                    root + " " + runPrefix + " " + name);
        }
        sendHelpFooter(sender);
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            sendHelpLines(sender, root, "command.help_debug_header",
                    "command.help_debug_entry_status", "command.help_debug_entry_on",
                    "command.help_debug_entry_off", "command.help_debug_entry_player",
                    "command.help_debug_entry_module", "command.help_debug_entry_action");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(1).toLowerCase(Locale.ROOT)) {
            case "player" -> sendPlayerValueHelp(sender, root, "command.help_debug_player_header",
                    "command.help_value_desc_debug_player", "debug player");
            case "module" -> sendDebugModuleHelp(sender, root);
            case "action" -> sendDebugActionHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of("debug"));
        }
    }

    private void sendDebugModuleHelp(CommandSender sender, String root) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message("command.help_debug_module_header", Map.of("root", root)));
        List<String> modules = new ArrayList<>(plugin.debugCommand().tabComplete(new String[]{"module", ""}));
        modules.sort(Comparator.naturalOrder());
        if (modules.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String module : modules) {
            sendValueEntry(sender, module,
                    messageService.message("command.help_value_desc_debug_module", Map.of("value", module)),
                    root + " debug module " + module);
        }
        sendHelpFooter(sender);
    }

    private void sendDebugActionHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 3 && "run".equalsIgnoreCase(path.get(2))) {
            sendHelpLines(sender, root, "command.help_debug_action_run_header",
                    "command.help_debug_action_run_entry_shadow", "command.help_debug_action_run_entry_execute");
            sendHelpFooter(sender);
            return;
        }
        sendHelpLines(sender, root, "command.help_debug_action_header",
                "command.help_debug_action_entry_run", "command.help_debug_action_entry_check",
                "command.help_debug_action_entry_registry");
        sendHelpFooter(sender);
    }

    private void sendValueEntry(CommandSender sender, String value, String desc, String run) {
        sendHelpLine(sender, plugin.messageService().message("command.help_value_entry", Map.of(
                "value", value,
                "desc", desc,
                "run", run)));
    }

    private void sendHelpLines(CommandSender sender, String root, String... keys) {
        var messageService = plugin.messageService();
        Map<String, ?> replacements = Map.of("root", root);
        for (String key : keys) {
            sendHelpLine(sender, messageService.message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        var messageService = plugin.messageService();
        messageService.sendComponent(sender, messageService.render(line));
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, plugin.messageService().message("command.help_footer"));
    }
}

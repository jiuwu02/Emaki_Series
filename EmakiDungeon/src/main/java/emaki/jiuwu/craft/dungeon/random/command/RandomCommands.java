package emaki.jiuwu.craft.dungeon.random.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.random.RandomDungeonService;
import emaki.jiuwu.craft.dungeon.random.RandomDungeonService.Result;
import emaki.jiuwu.craft.dungeon.random.template.CaptureService;

public final class RandomCommands {

    private final RandomDungeonService service;
    private final MessageService messages;
    private emaki.jiuwu.craft.dungeon.random.template.CaptureGui gui;

    public void bindGui(emaki.jiuwu.craft.dungeon.random.template.CaptureGui gui) {
        this.gui = gui;
    }

    public RandomCommands(RandomDungeonService service, MessageService messages) {
        this.service = service;
        this.messages = messages;
    }

    public void route(Player player, String[] args, boolean admin) {
        if (args.length < 2) {
            messages.send(player, "random.usage", Map.of());
            return;
        }
        String sub = args[1].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (!player.hasPermission("emakidungeon.play")) {
                    messages.send(player, "random.no_permission", Map.of());
                    return;
                }
                if (args.length < 3 || args[2].isBlank()) {
                    messages.send(player, "random.create_usage", Map.of());
                    return;
                }
                String dungeonId = args[2];
                int players = args.length >= 4 ? parseInt(args[3], 1) : 1;
                Long seed = args.length >= 5 ? parseLong(args[4]) : null;
                report(player, service.create(dungeonId, Math.max(1, players), seed));
            }
            case "enter" -> {
                if (!player.hasPermission("emakidungeon.play")) {
                    messages.send(player, "random.no_permission", Map.of());
                    return;
                }
                if (args.length < 3) {
                    messages.send(player, "random.usage", Map.of());
                    return;
                }
                report(player, service.enter(player, args[2]));
            }
            case "leave" -> {
                if (!player.hasPermission("emakidungeon.play")) {
                    messages.send(player, "random.no_permission", Map.of());
                    return;
                }
                report(player, service.leave(player));
            }
            case "list" -> {
                messages.send(player, "random.list_header", Map.of());
                for (var instance : service.instances()) {
                    messages.send(player, "random.list_entry", Map.of(
                            "instance", instance.instanceId(),
                            "state", instance.state().name(),
                            "rooms", String.valueOf(instance.roomCount()),
                            "seed", String.valueOf(instance.seed())));
                }
            }
            case "records" -> {
                messages.send(player, "random.records_header", Map.of());
                for (String record : service.records()) {
                    messages.send(player, "random.records_entry", Map.of("record", record));
                }
            }
            case "stop" -> {
                if (!admin) {
                    messages.send(player, "random.no_permission", Map.of());
                    return;
                }
                if (args.length < 3) {
                    messages.send(player, "random.usage", Map.of());
                    return;
                }
                report(player, service.stop(args[2]));
            }
            case "replay" -> {
                if (!admin) {
                    messages.send(player, "random.no_permission", Map.of());
                    return;
                }
                if (args.length < 3) {
                    messages.send(player, "random.usage", Map.of());
                    return;
                }
                report(player, service.replay(args[2]));
            }
            case "reload" -> {
                if (!admin) {
                    messages.send(player, "random.no_permission", Map.of());
                    return;
                }
                service.reloadConfig();
                service.reloadTemplates();
                if (service.config() == null) {
                    messages.send(player, "random.config_invalid", Map.of(
                            "errors", String.join("; ", service.configErrors())));
                } else {
                    messages.send(player, "random.reloaded", Map.of());
                }
            }
            default -> messages.send(player, "random.usage", Map.of());
        }
    }

    public void regionMenu(Player player) {
        regionMenu(player, null);
    }

    public void regionMenu(Player player, String templateId) {
        if (!ensureRegionSession(player)) {
            return;
        }
        if (templateId != null && !templateId.isBlank()) {
            CaptureService captures = service.captures();
            var session = captures.sessionOf(player.getUniqueId());
            if (captures.structure(session, templateId) == null) {
                messages.send(player, "random.gui.structure_missing", Map.of("id", templateId));
                return;
            }
            session.targetId = templateId;
        }
        sendTarget(player);
        if (gui == null) {
            return;
        }
        if (templateId != null && !templateId.isBlank()) {
            gui.openStructure(player, templateId);
        } else {
            gui.openMain(player);
        }
    }

    public void regionPos(Player player, int which) {
        if (!ensureRegionSession(player)) {
            return;
        }
        report(player, service.captures().pos(player, which, service.config()));
    }

    public void regionPosClear(Player player) {
        if (!ensureRegionSession(player)) {
            return;
        }
        report(player, service.captures().posClear(player));
    }

    public void regionStatus(Player player) {
        var session = service.captures().sessionOf(player.getUniqueId());
        if (session == null) {
            messages.send(player, "random.template.no_session", Map.of());
            return;
        }
        sendTarget(player);
        messages.send(player, "random.template.status", Map.of(
                "type", session.structureType,
                "tags", String.join(",", session.tags),
                "anchors", String.valueOf(service.captures().doorsOf(session).size()),
                "nodes", String.valueOf(session.nodes.size()),
                "floors", String.valueOf(session.floors)));
    }

    private void sendTarget(Player player) {
        var captures = service.captures();
        var session = captures.sessionOf(player.getUniqueId());
        if (session == null) {
            return;
        }
        String target = captures.targetOf(session);
        if (target == null) {
            messages.send(player, "random.template.menu_new", Map.of());
        } else {
            messages.send(player, "random.template.menu_target", Map.of("target", target));
        }
    }

    private boolean ensureRegionSession(Player player) {
        CaptureService captures = service.captures();
        if (captures.hasSession(player.getUniqueId())) {
            return true;
        }
        report(player, captures.start(player));
        return captures.hasSession(player.getUniqueId());
    }

    public void regionDoor(Player player, String[] args) {
        if (!ensureRegionSession(player)) {
            return;
        }
        CaptureService captures = service.captures();
        var session = captures.sessionOf(player.getUniqueId());
        sendTarget(player);
        if (args.length < 3) {
            messages.send(player, "editor.door_usage", Map.of());
            return;
        }
        String action = args[2].toLowerCase(java.util.Locale.ROOT);
        if ("remove".equals(action)) {
            if (args.length < 4) {
                messages.send(player, "editor.door_usage", Map.of());
                return;
            }
            report(player, captures.doorRemove(player, args[3]));
            return;
        }
        if ("list".equals(action)) {
            messages.send(player, "random.template.door_list_header", Map.of());
            for (var anchor : captures.doorsOf(session)) {
                messages.send(player, "random.template.door_list_entry", Map.of(
                        "id", anchor.id(),
                        "facing", anchor.facing(),
                        "size", anchor.width() + "x" + anchor.height()));
            }
            return;
        }
        messages.send(player, "editor.door_usage", Map.of());
    }

    private void report(Player player, Result result) {
        messages.send(player, result.messageKey(), result.params());
    }

    private void report(Player player, emaki.jiuwu.craft.dungeon.random.template.CaptureService.Result result) {
        messages.send(player, result.messageKey(), result.params());
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static Long parseLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public List<String> complete(String[] args) {
        List<String> candidates = new ArrayList<>();
        if (args.length <= 1) {
            candidates.addAll(List.of("create", "enter", "leave", "list", "records", "stop", "replay", "reload"));
        } else if (args.length == 2 && "create".equalsIgnoreCase(args[0])) {
            candidates.addAll(service.dungeonIds());
        } else if (args.length == 2 && ("enter".equalsIgnoreCase(args[0]) || "stop".equalsIgnoreCase(args[0]))) {
            for (var instance : service.instances()) {
                candidates.add(instance.instanceId());
            }
        } else if (args.length == 2 && "replay".equalsIgnoreCase(args[0])) {
            candidates.addAll(service.records());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        candidates.removeIf(candidate -> !candidate.toLowerCase(java.util.Locale.ROOT).startsWith(prefix));
        return candidates;
    }

    public List<String> structureIds(Player player) {
        var session = service.captures().sessionOf(player.getUniqueId());
        if (session == null) {
            return List.of();
        }
        return new ArrayList<>(service.storeOf(session.worldName.startsWith(CaptureService.EDIT_WORLD_PREFIX)
                ? session.worldName.substring(CaptureService.EDIT_WORLD_PREFIX.length()) : "").ids());
    }

    public List<String> instanceIds() {
        List<String> ids = new ArrayList<>();
        for (var instance : service.instances()) {
            ids.add(instance.instanceId());
        }
        return ids;
    }

    public List<String> recordIds() {
        return new ArrayList<>(service.records());
    }
}

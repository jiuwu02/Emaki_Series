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
            case "template" -> routeTemplate(player, args);
            case "create" -> {
                if (!player.hasPermission("emakidungeon.play")) {
                    messages.send(player, "random.no_permission", Map.of());
                    return;
                }
                int players = args.length >= 3 ? parseInt(args[2], 1) : 1;
                Long seed = args.length >= 4 ? parseLong(args[3]) : null;
                report(player, service.create(Math.max(1, players), seed));
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

    private void routeTemplate(Player player, String[] args) {
        if (!player.hasPermission("emakidungeon.editor")) {
            messages.send(player, "random.no_permission", Map.of());
            return;
        }
        CaptureService captures = service.captures();
        if (args.length < 3) {
            messages.send(player, "random.template.usage", Map.of());
            return;
        }
        String sub = args[2].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "start" -> report(player, captures.start(player));
            case "pos1" -> report(player, captures.pos(player, 1));
            case "pos2" -> report(player, captures.pos(player, 2));
            case "meta" -> {
                if (args.length < 5) {
                    messages.send(player, "random.template.usage", Map.of());
                    return;
                }
                report(player, captures.meta(player, args[3], join(args, 4)));
            }
            case "anchor" -> routeAnchor(player, args);
            case "node" -> routeNode(player, args);
            case "save" -> {
                if (args.length < 4) {
                    messages.send(player, "random.template.usage", Map.of());
                    return;
                }
                boolean confirmed = args.length >= 5 && "confirm".equalsIgnoreCase(args[4]);
                report(player, captures.save(player, args[3], service.config(), confirmed));
            }
            case "discard" -> report(player, captures.discard(player));
            case "status" -> {
                var session = captures.sessionOf(player.getUniqueId());
                if (session == null) {
                    messages.send(player, "random.template.no_session", Map.of());
                    return;
                }
                messages.send(player, "random.template.status", Map.of(
                        "type", session.structureType,
                        "tags", String.join(",", session.tags),
                        "anchors", String.valueOf(session.anchors.size()),
                        "nodes", String.valueOf(session.nodes.size()),
                        "floors", String.valueOf(session.floors)));
            }
            default -> messages.send(player, "random.template.usage", Map.of());
        }
    }

    private void routeAnchor(Player player, String[] args) {
        CaptureService captures = service.captures();
        if (args.length < 4) {
            messages.send(player, "random.template.usage", Map.of());
            return;
        }
        String action = args[3].toLowerCase(java.util.Locale.ROOT);
        if ("add".equals(action)) {
            if (args.length < 8) {
                messages.send(player, "random.template.usage", Map.of());
                return;
            }
            report(player, captures.anchorAdd(player, args[4], args[5],
                    parseInt(args[6], 1), parseInt(args[7], 1)));
            return;
        }
        if ("remove".equals(action)) {
            if (args.length < 5) {
                messages.send(player, "random.template.usage", Map.of());
                return;
            }
            report(player, captures.anchorRemove(player, args[4]));
            return;
        }
        messages.send(player, "random.template.usage", Map.of());
    }

    private void routeNode(Player player, String[] args) {
        CaptureService captures = service.captures();
        if (args.length < 4) {
            messages.send(player, "random.template.usage", Map.of());
            return;
        }
        String action = args[3].toLowerCase(java.util.Locale.ROOT);
        if ("add".equals(action)) {
            if (args.length < 6) {
                messages.send(player, "random.template.usage", Map.of());
                return;
            }
            report(player, captures.nodeAdd(player, args[4], args[5],
                    args.length >= 7 ? args[6] : ""));
            return;
        }
        if ("param".equals(action)) {
            if (args.length < 7) {
                messages.send(player, "random.template.usage", Map.of());
                return;
            }
            report(player, captures.nodeParam(player, args[4], args[5], join(args, 6)));
            return;
        }
        messages.send(player, "random.template.usage", Map.of());
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

    private static String join(String[] args, int from) {
        List<String> parts = new ArrayList<>();
        for (int i = from; i < args.length; i++) {
            parts.add(args[i]);
        }
        return String.join(" ", parts);
    }

    public List<String> complete(String[] args) {
        List<String> candidates = new ArrayList<>();
        if (args.length <= 1) {
            candidates.addAll(List.of("template", "create", "enter", "leave", "list", "records", "stop", "replay", "reload"));
        } else if (args.length == 2 && "template".equalsIgnoreCase(args[0])) {
            candidates.addAll(List.of("start", "pos1", "pos2", "meta", "anchor", "node", "save", "discard", "status"));
        } else if (args.length == 3 && "template".equalsIgnoreCase(args[0]) && "meta".equalsIgnoreCase(args[1])) {
            candidates.addAll(List.of("type", "tags", "floors", "mirrorable", "intrusion-policy", "door-policy", "seal-block", "rotations"));
        } else if (args.length == 4 && "template".equalsIgnoreCase(args[0]) && "save".equalsIgnoreCase(args[1])) {
            candidates.add("confirm");
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

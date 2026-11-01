package emaki.jiuwu.craft.dummy.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.command.CommandSender;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dummy.scenario.ScenarioDefinition;
import emaki.jiuwu.craft.dummy.scenario.ScenarioEngine;
import emaki.jiuwu.craft.dummy.scenario.ScenarioRunner;
import emaki.jiuwu.craft.dummy.session.DummyManager;
import emaki.jiuwu.craft.dummy.session.DummySession;
import emaki.jiuwu.craft.dummy.transport.TransportEndpoint;

public final class DummyCommandRouter {

    private static final List<String> SUB_COMMANDS = List.of(
            "help", "join", "leave", "say", "cmd", "walk", "look", "interact", "attack",
            "click", "list", "scenario", "stop-all");
    private static final List<String> SCENARIO_ACTIONS = List.of("run", "stop");
    private static final String PERMISSION_ADMIN = "emakidummy.admin";

    private final MessageService messages;
    private final DummyManager manager;
    private final ScenarioEngine engine;

    public DummyCommandRouter(MessageService messages, DummyManager manager, ScenarioEngine engine) {
        this.messages = messages;
        this.manager = manager;
        this.engine = engine;
    }

    public void route(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "command.help");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> messages.send(sender, "command.help");
            case "join" -> routeJoin(sender, args);
            case "leave" -> routeLeave(sender, args);
            case "say" -> routeSay(sender, args);
            case "cmd" -> routeCommand(sender, args);
            case "walk" -> routeWalk(sender, args);
            case "look" -> routeLook(sender, args);
            case "interact" -> routeInteract(sender, args);
            case "attack" -> routeAttack(sender, args);
            case "click" -> routeClick(sender, args);
            case "list" -> routeList(sender);
            case "scenario" -> routeScenario(sender, args);
            case "stop-all" -> routeStopAll(sender);
            default -> messages.send(sender, "general.unknown_command", Map.of("sub", args[0]));
        }
    }

    public Collection<String> suggest(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            return prefixFilter(SUB_COMMANDS, args.length == 0 ? "" : args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (List.of("leave", "say", "cmd", "walk", "look", "interact", "attack", "click")
                .contains(sub)) {
            return dummyNames(args);
        }
        if ("scenario".equals(sub) && args.length == 2) {
            return prefixFilter(SCENARIO_ACTIONS, args[1]);
        }
        if ("scenario".equals(sub) && args.length == 3) {
            return prefixFilter(engine.ids().stream().sorted().toList(), args[2]);
        }
        return List.of();
    }

    private void routeJoin(CommandSender sender, String[] args) {
        String name = args.length >= 2 ? args[1] : manager.nextName();
        DummyManager.JoinResult result = manager.join(name);
        TransportEndpoint endpoint = manager.endpoint();
        switch (result.outcome()) {
            case OK -> messages.send(sender, "command.join.connecting", Map.of(
                    "name", name, "host", endpoint.host(), "port", endpoint.port()));
            case DUPLICATE -> messages.send(sender, "command.join.duplicate", Map.of("name", name));
            case LIMIT -> messages.send(sender, "command.join.limit", Map.of("limit", result.detail()));
            case INVALID_NAME -> messages.send(sender, "general.invalid_args");
            case FAILED -> messages.send(sender, "command.join.failed", Map.of(
                    "name", name, "reason", result.detail()));
        }
    }

    private void routeLeave(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "command.leave.usage");
            return;
        }
        DummyManager.LeaveOutcome outcome = manager.leave(args[1]);
        if (outcome == DummyManager.LeaveOutcome.NOT_FOUND) {
            messages.send(sender, "command.leave.not_found", Map.of("name", args[1]));
            return;
        }
        messages.send(sender, "command.leave.done", Map.of("name", args[1]));
    }

    private void routeSay(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "command.say.usage");
            return;
        }
        String text = joinArgs(args, 2);
        if (!manager.say(args[1], text)) {
            messages.send(sender, "command.say.not_found", Map.of("name", args[1]));
            return;
        }
        messages.send(sender, "command.say.sent", Map.of("name", args[1], "text", text));
    }

    private void routeCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "command.cmd.usage");
            return;
        }
        String command = joinArgs(args, 2);
        if (!manager.runCommand(args[1], command)) {
            messages.send(sender, "command.cmd.not_found", Map.of("name", args[1]));
            return;
        }
        messages.send(sender, "command.cmd.sent", Map.of("name", args[1], "command", command));
    }

    private void routeWalk(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messages.send(sender, "command.walk.usage");
            return;
        }
        double x;
        double y;
        double z;
        try {
            x = Double.parseDouble(args[2]);
            y = Double.parseDouble(args[3]);
            z = Double.parseDouble(args[4]);
        } catch (NumberFormatException failure) {
            messages.send(sender, "general.invalid_args");
            return;
        }
        if (!manager.startWalk(args[1], x, y, z)) {
            messages.send(sender, "command.walk.not_found", Map.of("name", args[1]));
            return;
        }
        messages.send(sender, "command.walk.started", Map.of(
                "name", args[1], "x", args[2], "y", args[3], "z", args[4]));
    }

    private void routeLook(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "command.look.usage");
            return;
        }
        float yaw;
        float pitch;
        try {
            yaw = Float.parseFloat(args[2]);
            pitch = Float.parseFloat(args[3]);
        } catch (NumberFormatException failure) {
            messages.send(sender, "general.invalid_args");
            return;
        }
        if (!manager.look(args[1], yaw, pitch)) {
            messages.send(sender, "command.look.not_found", Map.of("name", args[1]));
            return;
        }
        messages.send(sender, "command.look.done", Map.of(
                "name", args[1], "yaw", args[2], "pitch", args[3]));
    }

    private void routeInteract(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length == 3 || args.length == 4 || args.length > 5) {
            messages.send(sender, "command.interact.usage");
            return;
        }
        Double x = null;
        Double y = null;
        Double z = null;
        if (args.length == 5) {
            try {
                x = Double.parseDouble(args[2]);
                y = Double.parseDouble(args[3]);
                z = Double.parseDouble(args[4]);
            } catch (NumberFormatException failure) {
                messages.send(sender, "general.invalid_args");
                return;
            }
        }
        DummyManager.InteractOutcome outcome = manager.interact(args[1], x, y, z);
        if (outcome.code() == -1) {
            messages.send(sender, "command.interact.not_found", Map.of("name", args[1]));
            return;
        }
        if (outcome.code() != 0) {
            messages.send(sender, "general.invalid_args");
            return;
        }
        messages.send(sender, "command.interact.done", Map.of(
                "name", args[1], "block", outcome.block()));
    }

    private void routeAttack(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "command.attack.usage");
            return;
        }
        int entityId = manager.attack(args[1]);
        if (entityId == -2) {
            messages.send(sender, "command.attack.not_found", Map.of("name", args[1]));
            return;
        }
        if (entityId == -1) {
            messages.send(sender, "command.attack.no_target", Map.of("name", args[1]));
            return;
        }
        messages.send(sender, "command.attack.done", Map.of(
                "name", args[1], "entity", entityId));
    }

    private void routeClick(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.send(sender, "command.click.usage");
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[2]);
        } catch (NumberFormatException failure) {
            messages.send(sender, "general.invalid_args");
            return;
        }
        int outcome = manager.click(args[1], slot);
        if (outcome == -1) {
            messages.send(sender, "command.click.not_found", Map.of("name", args[1]));
            return;
        }
        if (outcome != 0) {
            messages.send(sender, "command.click.no_window", Map.of("name", args[1]));
            return;
        }
        messages.send(sender, "command.click.done", Map.of(
                "name", args[1], "slot", slot));
    }

    private void routeList(CommandSender sender) {
        List<DummySession> sessions = new ArrayList<>(manager.all());
        if (sessions.isEmpty()) {
            messages.send(sender, "command.list.empty");
            return;
        }
        messages.send(sender, "command.list.header", Map.of("count", sessions.size()));
        for (DummySession session : sessions) {
            messages.send(sender, "command.list.entry", Map.of(
                    "name", session.name(),
                    "state", session.state().name().toLowerCase(Locale.ROOT),
                    "position", describePosition(session)));
        }
    }

    private void routeScenario(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            messages.send(sender, "general.no_permission");
            return;
        }
        if (args.length < 3) {
            messages.send(sender, "command.scenario.usage");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String id = args[2];
        if ("run".equals(action)) {
            ScenarioEngine.RunOutcome outcome = engine.run(id, sender);
            switch (outcome) {
                case NOT_FOUND -> messages.send(sender, "command.scenario.not_found", Map.of("id", id));
                case BUSY -> {
                    ScenarioRunner active = engine.active();
                    messages.send(sender, "command.scenario.busy", Map.of(
                            "active", active == null ? "-" : active.id()));
                }
                case STARTED -> {
                    ScenarioDefinition definition = engine.definition(id);
                    List<String> players = definition == null ? List.of() : definition.players();
                    messages.send(sender, "command.scenario.started", Map.of(
                            "id", id, "players", String.join(",", players)));
                }
            }
            return;
        }
        if ("stop".equals(action)) {
            if (engine.stop(id)) {
                messages.send(sender, "command.scenario.stopped", Map.of("id", id));
                return;
            }
            messages.send(sender, "command.scenario.none_active");
            return;
        }
        messages.send(sender, "command.scenario.usage");
    }

    private void routeStopAll(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            messages.send(sender, "general.no_permission");
            return;
        }
        engine.stopActive();
        int count = manager.stopAll();
        if (count == 0) {
            messages.send(sender, "command.stopall.none");
            return;
        }
        messages.send(sender, "command.stopall.done", Map.of("count", count));
    }

    private static String describePosition(DummySession session) {
        if (session.link() == null || !session.link().hasPose()) {
            return "-";
        }
        double[] pose = session.link().pose();
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f", pose[0], pose[1], pose[2]);
    }

    private static String joinArgs(String[] args, int fromIndex) {
        StringBuilder text = new StringBuilder(64);
        for (int index = fromIndex; index < args.length; index++) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(args[index]);
        }
        return text.toString();
    }

    private static Collection<String> prefixFilter(Collection<String> options, String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matched = new ArrayList<>(options.size());
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matched.add(option);
            }
        }
        return matched;
    }

    private Collection<String> dummyNames(String[] args) {
        if (args.length != 2) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (DummySession session : manager.all()) {
            names.add(session.name());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return prefixFilter(names, args[1]);
    }
}

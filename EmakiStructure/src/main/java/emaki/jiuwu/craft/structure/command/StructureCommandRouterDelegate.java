package emaki.jiuwu.craft.structure.command;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.config.NameRules;
import emaki.jiuwu.craft.structure.furniture.FurnitureEntry;
import emaki.jiuwu.craft.structure.furniture.FurnitureSidecarParser;
import emaki.jiuwu.craft.structure.generate.GenerationService;
import emaki.jiuwu.craft.structure.generate.StructureFacing;
import emaki.jiuwu.craft.structure.generate.TemplateTransform;
import emaki.jiuwu.craft.structure.integration.FurnitureIdAdapter;
import emaki.jiuwu.craft.structure.integration.furniture.FurniturePlacer;
import emaki.jiuwu.craft.structure.selection.AdjustDirection;
import emaki.jiuwu.craft.structure.selection.SelectionBox;
import emaki.jiuwu.craft.structure.multiblock.MultiblockDefinition;
import emaki.jiuwu.craft.structure.multiblock.MultiblockRepository;
import emaki.jiuwu.craft.structure.selection.SelectionManager;
import emaki.jiuwu.craft.structure.selection.SelectionSession;
import emaki.jiuwu.craft.structure.template.StructureExporter;
import emaki.jiuwu.craft.structure.template.StructureTemplate;
import emaki.jiuwu.craft.structure.template.TemplateRepository;
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;
import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class StructureCommandRouterDelegate {

    private final StructureCommandRouter ownerRef;

    StructureCommandRouterDelegate(StructureCommandRouter ownerRef) {
        this.ownerRef = ownerRef;
    }

    void handleStructure(CommandSender sender, String[] args) {
        if (!ownerRef.requirePermission(sender, StructureCommandRouter.PERMISSION_EDIT)) {
            return;
        }
        if (args.length < 2) {
            ownerRef.plugin.messageService().send(sender, "command.structure_usage");
            return;
        }
        String[] shifted = Arrays.copyOfRange(args, 1, args.length);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "export" -> ownerRef.handleExport(sender, shifted);
            case "place" -> ownerRef.handlePlace(sender, shifted);
            default -> ownerRef.plugin.messageService().send(sender, "command.structure_usage");
        }
    }

    List<String> suggestStructure(String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return StructureCommandRouter.STRUCTURE_LEVEL.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3 && "place".equalsIgnoreCase(args[1])) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return ownerRef.repository.templateIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }
        if (args.length == 4 && "place".equalsIgnoreCase(args[1])) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return StructureFacing.configValues().stream().filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length == 4 && "export".equalsIgnoreCase(args[1]) && !args[2].isBlank()) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return "entities".startsWith(prefix) ? List.of("entities") : List.of();
        }
        return List.of();
    }

    boolean hasMultiblockPermission(CommandSender sender, String subcommand) {
        return "export".equals(subcommand) || "place".equals(subcommand)
                ? sender.hasPermission(StructureCommandRouter.PERMISSION_EDIT)
                : sender.hasPermission(StructureCommandRouter.PERMISSION_ADMIN);
    }

    List<String> suggestTemplateCommand(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return ownerRef.repository.templateIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return StructureFacing.configValues().stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    List<String> suggestSelect(String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return StructureCommandRouter.SELECT_LEVEL.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3 && "adjust".equals(args[1].toLowerCase(Locale.ROOT))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return StructureCommandRouter.ADJUST_OPERATIONS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 4 && "adjust".equals(args[1].toLowerCase(Locale.ROOT))) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return AdjustDirection.configValues().stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    void handleGet(CommandSender sender, String[] args) {
        if (!ownerRef.requirePermission(sender, StructureCommandRouter.PERMISSION_USE)) {
            return;
        }
        Player player = ownerRef.requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            ownerRef.plugin.messageService().send(sender, "command.get_usage");
            return;
        }
        StructureFacing facing = ownerRef.resolveFacing(sender, args.length >= 3 ? args[2] : "south");
        if (facing == null) {
            return;
        }
        String templateName = args[1].toLowerCase(Locale.ROOT);
        if (!ownerRef.requireTemplateExists(sender, templateName, args[1])) {
            return;
        }
        Location front = ownerRef.frontAnchor(player, 3);
        ownerRef.loadAndStart(sender, templateName, front, facing);
    }

    void handlePlace(CommandSender sender, String[] args) {
        if (!ownerRef.requirePermission(sender, StructureCommandRouter.PERMISSION_EDIT)) {
            return;
        }
        Player player = ownerRef.requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            ownerRef.plugin.messageService().send(sender, "command.place_usage");
            return;
        }
        StructureFacing facing = ownerRef.resolveFacing(sender, args[2]);
        if (facing == null) {
            return;
        }
        String templateName = args[1].toLowerCase(Locale.ROOT);
        if (!ownerRef.requireTemplateExists(sender, templateName, args[1])) {
            return;
        }
        Location anchor = player.getLocation();
        ownerRef.loadAndStart(sender, templateName,
                new Location(anchor.getWorld(), anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ()),
                facing);
    }

    void startGeneration(CommandSender sender, StructureTemplate template, Location anchor, StructureFacing facing) {
        World world = anchor.getWorld();
        if (world == null) {
            ownerRef.plugin.messageService().send(sender, "error.generate.out_of_bounds",
                    Map.of("detail", "world unavailable"));
            return;
        }
        GenerationService.Request request = new GenerationService.Request(world, template,
                anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ(), facing, sender);
        ownerRef.generationService.start(request).ifPresent(rejection ->
                ownerRef.plugin.messageService().send(sender, rejection.reasonKey(), rejection.placeholders()));
    }

    void setCorner(CommandSender sender, Player player, boolean first) {
        SelectionSession session = ownerRef.selectionManager.sessionFor(player, previousWorld ->
                ownerRef.plugin.messageService().send(sender, "error.selection.world_mismatch",
                        Map.of("pos1_world", previousWorld,
                                "pos2_world", player.getWorld().getName())));
        SelectionSession.Corner previousFirst = session.first().orElse(null);
        SelectionSession.Corner previousSecond = session.second().orElse(null);
        Location location = player.getLocation();
        SelectionSession.Corner corner = new SelectionSession.Corner(
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (first) {
            session.setFirst(corner);
        } else {
            session.setSecond(corner);
            if (session.first().isEmpty()) {
                session.setFirst(corner);
            }
        }
        Optional<SelectionBox> box = session.box();
        if (box.isPresent()) {
            AppConfig.GenerateSettings settings = ownerRef.config.get().generate();
            if (!box.get().withinLimits(settings.sizeLimitX(), settings.sizeLimitY(), settings.sizeLimitZ())) {
                session.restore(previousFirst, previousSecond);
                ownerRef.plugin.messageService().send(sender, "error.selection.too_large", Map.of(
                        "size_x", box.get().sizeX(), "size_y", box.get().sizeY(), "size_z", box.get().sizeZ(),
                        "limit_x", settings.sizeLimitX(), "limit_y", settings.sizeLimitY(),
                        "limit_z", settings.sizeLimitZ()));
                return;
            }
            ownerRef.reportSelectionReady(sender, session, box.get());
        } else {
            ownerRef.plugin.messageService().send(sender, "command.select_pos_set", Map.of(
                    "label", first ? "pos1" : "pos2",
                    "pos", corner.format()));
        }
        ownerRef.selectionManager.refresh(player, session);
    }

    void reportSelectionReady(CommandSender sender, SelectionSession session, SelectionBox box) {
        ownerRef.plugin.messageService().send(sender, "command.select_ready", Map.of(
                "pos1", StructureCommandRouterSupport.describe(box, true),
                "pos2", StructureCommandRouterSupport.describe(box, false),
                "size_x", box.sizeX(),
                "size_y", box.sizeY(),
                "size_z", box.sizeZ()));
    }

    void clearSelection(CommandSender sender, Player player) {
        ownerRef.selectionManager.clear(player);
        ownerRef.plugin.messageService().send(sender, "command.select_cleared");
    }

    int writeFurnitureSidecar(CommandSender sender, String name, List<StructureExporter.CapturedFurniture> furniture) {
        if (!ownerRef.furnitureAdapter.anySourcePresent()) {
            return 0;
        }
        List<FurnitureEntry> entries = new ArrayList<>(furniture.size());
        for (StructureExporter.CapturedFurniture captured : furniture) {
            entries.add(new FurnitureEntry(captured.id(), captured.x(), captured.y(),
                    captured.z(), captured.yaw(), captured.facing()));
        }
        Path target = ownerRef.plugin.dataPath("furniture", name + ".yml");
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, FurnitureSidecarParser.encode(entries).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            ownerRef.runGlobalMessage(sender, "error.export.write_failed", Map.of(
                    "file", target.getFileName().toString(),
                    "error", String.valueOf(exception.getMessage())));
        }
        return entries.size();
    }

    void runGlobalMessage(CommandSender sender, String key, Map<String, Object> placeholders) {
        ownerRef.plugin.executionDispatcher().runGlobal(ownerRef.plugin,
                () -> ownerRef.plugin.messageService().send(sender, key, placeholders));
    }

    void handleReload(CommandSender sender) {
        if (!ownerRef.requirePermission(sender, StructureCommandRouter.PERMISSION_RELOAD)) {
            return;
        }
        int count = ownerRef.plugin.reloadContent();
        ownerRef.plugin.messageService().send(sender, "console.reload_complete", Map.of("count", count));
    }

    void handleMultiblock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            ownerRef.plugin.messageService().send(sender, "command.multiblock_usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> ownerRef.handleMultiblockList(sender);
            case "validate" -> ownerRef.handleMultiblockValidate(sender, args);
            case "export" -> ownerRef.handleMultiblockExport(sender, args);
            case "place" -> ownerRef.handleMultiblockPlace(sender, args);
            default -> ownerRef.plugin.messageService().send(sender, "command.multiblock_usage");
        }
    }

    void handleMultiblockList(CommandSender sender) {
        if (!ownerRef.requirePermission(sender, StructureCommandRouter.PERMISSION_ADMIN)) {
            return;
        }
        var service = ownerRef.plugin.multiblockService();
        List<String> ids = service.repository().ids();
        ownerRef.plugin.messageService().send(sender, "command.multiblock_list_header",
                Map.of("count", ids.size(),
                        "candidates", service.registry().candidateInstanceCount(),
                        "formed", service.registry().formedInstanceCount()));
        for (String id : ids) {
            var definition = service.repository().byId(id).orElse(null);
            if (definition == null) {
                continue;
            }
            ownerRef.plugin.messageService().send(sender, "command.multiblock_list_entry", Map.of(
                    "id", definition.id(),
                    "members", definition.members().size(),
                    "rotation", String.valueOf(definition.allowRotation())));
        }
    }

    int countFormedFor(String definitionId) {
        int count = 0;
        for (var instance : ownerRef.plugin.multiblockService().registry().allFormed()) {
            if (instance.pose().definitionId().equals(definitionId)) {
                count++;
            }
        }
        return count;
    }

    void captureMembers(World world, SelectionBox region, SelectionBox full, List<int[]> positions, List<String> specs) {
        var adapter = ownerRef.plugin.multiblockService().engine().customBlocks();
        for (int y = region.minY(); y <= region.maxY(); y++) {
            for (int z = region.minZ(); z <= region.maxZ(); z++) {
                for (int x = region.minX(); x <= region.maxX(); x++) {
                    var block = world.getBlockAt(x, y, z);
                    String identified = adapter == null ? "" : adapter.identify(block);
                    positions.add(new int[] {
                            x - full.minX(), y - full.minY(), z - full.minZ() });
                    specs.add(identified == null || identified.isBlank()
                            ? block.getBlockData().getAsString()
                            : identified);
                }
            }
        }
    }

    static String renderMultiblockYaml(String id, List<int[]> positions, List<String> specs) {
        StringBuilder builder = new StringBuilder();
        builder.append("# EmakiStructure 多方块结构定义。\n");
        builder.append("# 由 /ems multiblock export 生成，可手工补充 trigger 与 cooldown。\n");
        builder.append("version: 1\n");
        builder.append("id: ").append(id).append('\n');
        builder.append("shape:\n");
        builder.append("  allow_rotation: false\n");
        builder.append("  blocks:\n");
        for (int index = 0; index < positions.size(); index++) {
            int[] pos = positions.get(index);
            builder.append("    - pos: [").append(pos[0]).append(", ").append(pos[1])
                    .append(", ").append(pos[2]).append("]\n");
            builder.append("      block: \"").append(StructureCommandRouterSupport.escapeYaml(specs.get(index))).append("\"\n");
        }
        builder.append("trigger:\n");
        builder.append("  targets: any\n");
        return builder.toString();
    }

    boolean placeMember(World world, MultiblockDefinition.Member member, StructureFacing facing, int worldX, int worldY, int worldZ) {
        Block block = world.getBlockAt(worldX, worldY, worldZ);
        switch (member.kind()) {
            case VANILLA_STATE -> block.setBlockData(
                    TemplateTransform.apply(member.blockData(), facing), false);
            case VANILLA_MATERIAL -> block.setType(member.material(), false);
            case CUSTOM_BLOCK -> {
                var adapter = ownerRef.plugin.multiblockService().engine().customBlocks();
                if (adapter == null || !adapter.place(block, adapter.bind(member.namespaceId()))) {
                    return false;
                }
            }
            case FURNITURE -> {
                Location spot = new Location(world, worldX + 0.5d, worldY + 1.0d, worldZ + 0.5d,
                        (float) FurnitureSidecarParser.normalizeYaw(facing.yawDegrees()), 0.0f);
                if (ownerRef.furniturePlacer.place(member.spec(), spot) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    void debug(String langKey, Map<String, ?> replacements) {
        var logger = ownerRef.plugin.debugLogger();
        if (logger == null || !logger.shouldLog("structure", (UUID) null)) {
            return;
        }
        logger.log("structure", (UUID) null, langKey, replacements);
    }

    void reportMultiblockProblems(CommandSender sender, MultiblockRepository repository) {
        for (var problem : repository.drainProblems()) {
            ownerRef.plugin.messageService().send(sender, problem.reasonKey(), problem.placeholders());
        }
    }

    void sendHelp(CommandSender sender) {
        var messages = ownerRef.plugin.messageService();
        messages.sendRaw(sender, messages.message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("get <模板> [朝向]", messages.message("command.help.commands.get"));
        lines.put("structure place <模板> <朝向>", messages.message("command.help.commands.place"));
        lines.put("structure export <名称> [entities]", messages.message("command.help.commands.export"));
        lines.put("select pos1|pos2|adjust|clear", messages.message("command.help.commands.select"));
        lines.put("multiblock export <id> [force]", messages.message("command.help.commands.multiblock_export"));
        lines.put("multiblock place <id> [朝向]", messages.message("command.help.commands.multiblock_place"));
        lines.put("multiblock list", messages.message("command.help.commands.list"));
        lines.put("multiblock validate <id>", messages.message("command.help.commands.validate"));
        lines.put("reload", messages.message("command.help.commands.reload"));
        lines.put("debug", messages.message("command.help.commands.debug"));
        lines.forEach((usage, description) -> messages.sendRaw(sender,
                messages.message("command.help.line", Map.of("cmd", usage, "desc", description))));
        messages.sendRaw(sender, messages.message("command.help.footer"));
    }

    void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(StructureCommandRouter.PERMISSION_ADMIN)) {
            ownerRef.plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return;
        }
        if (ownerRef.plugin.debugCommand() == null) {
            return;
        }
        ownerRef.plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length),
                ownerRef.plugin.messageService());
    }

    boolean requireTemplateExists(CommandSender sender, String templateName, String raw) {
        if (!NameRules.isValid(templateName)) {
            ownerRef.plugin.messageService().send(sender, "error.name.invalid", Map.of("value", raw));
            return false;
        }
        if (!ownerRef.repository.templateIds().contains(templateName)) {
            ownerRef.plugin.messageService().send(sender, "error.template.not_found",
                    Map.of("template", templateName));
            return false;
        }
        return true;
    }

    void reportSkippedPalette(StructureTemplate template) {
        if (template.skippedPaletteEntries() > 0 && ownerRef.legacySkipWarnings.add(template.id())) {
            ownerRef.plugin.messageService().warning("console.template_legacy_skipped", Map.of(
                    "template", template.id(),
                    "count", template.skippedPaletteEntries()));
        }
    }

    StructureFacing resolveFacing(CommandSender sender, String raw) {
        StructureFacing facing = StructureFacing.parse(raw);
        if (facing == null) {
            ownerRef.plugin.messageService().send(sender, "command.invalid_facing", Map.of(
                    "value", raw,
                    "allowed", String.join(", ", StructureFacing.configValues())));
        }
        return facing;
    }

    Location frontAnchor(Player player, int distance) {
        Location location = player.getLocation();
        AdjustDirection.AxisVector vector = AdjustDirection.FRONT.resolve(location.getYaw());
        int dx = vector.axis() == AdjustDirection.Axis.X && vector.positive() ? 1
                : vector.axis() == AdjustDirection.Axis.X ? -1 : 0;
        int dz = vector.axis() == AdjustDirection.Axis.Z && vector.positive() ? 1
                : vector.axis() == AdjustDirection.Axis.Z ? -1 : 0;
        return new Location(location.getWorld(),
                location.getBlockX() + dx * distance,
                location.getBlockY(),
                location.getBlockZ() + dz * distance);
    }

    boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        ownerRef.plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
        return false;
    }

    Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        ownerRef.plugin.messageService().send(sender, "general.player_only");
        return null;
    }

}

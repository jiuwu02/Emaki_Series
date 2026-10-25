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

public final class StructureCommandRouter {

    public static final String PERMISSION_USE = "emakistructure.use";
    public static final String PERMISSION_EDIT = "emakistructure.edit";
    public static final String PERMISSION_RELOAD = "emakistructure.reload";
    public static final String PERMISSION_ADMIN = "emakistructure.admin";

    private static final List<String> TOP_LEVEL =
            List.of("help", "get", "select", "structure", "reload", "multiblock", "debug");
    private static final List<String> SELECT_LEVEL = List.of("pos1", "pos2", "adjust", "clear");
    private static final List<String> ADJUST_OPERATIONS = List.of("add", "reduce");
    private static final List<String> STRUCTURE_LEVEL = List.of("export", "place");
    private static final List<String> MULTIBLOCK_LEVEL = List.of("export", "place", "list", "validate");

    private final EmakiStructurePlugin plugin;
    private final SelectionManager selectionManager;
    private final GenerationService generationService;
    private final TemplateRepository repository;
    private final FurnitureIdAdapter furnitureAdapter;
    private final FurniturePlacer furniturePlacer;
    private final Supplier<AppConfig> config;
    private final Set<String> legacySkipWarnings = ConcurrentHashMap.newKeySet();

    public StructureCommandRouter(EmakiStructurePlugin plugin,
            SelectionManager selectionManager,
            GenerationService generationService,
            TemplateRepository repository,
            FurnitureIdAdapter furnitureAdapter,
            FurniturePlacer furniturePlacer,
            Supplier<AppConfig> config) {
        this.plugin = plugin;
        this.selectionManager = selectionManager;
        this.generationService = generationService;
        this.repository = repository;
        this.furnitureAdapter = furnitureAdapter;
        this.furniturePlacer = furniturePlacer;
        this.config = config;
    }

    public void route(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.messageService().send(sender, "command.usage");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> sendHelp(sender);
            case "get" -> handleGet(sender, args);
            case "select" -> handleSelect(sender, args);
            case "structure" -> handleStructure(sender, args);
            case "reload" -> handleReload(sender);
            case "multiblock" -> handleMultiblock(sender, args);
            case "debug" -> handleDebug(sender, args);
            default -> plugin.messageService().send(sender, "general.unknown_command");
        }
    }

    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String sub : TOP_LEVEL) {
                if (sub.startsWith(prefix) && hasPermission(sender, sub)) {
                    result.add(sub);
                }
            }
            return result;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "get" -> suggestTemplateCommand(sender, args);
            case "structure" -> suggestStructure(args);
            case "select" -> suggestSelect(args);
            case "multiblock" -> suggestMultiblock(sender, args);
            case "reload", "help" -> List.of();
            case "debug" -> plugin.debugCommand() == null ? List.of()
                    : plugin.debugCommand().tabComplete(
                            Arrays.copyOfRange(args, 1, args.length));
            default -> List.of();
        };
    }

    private void handleStructure(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_EDIT)) {
            return;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.structure_usage");
            return;
        }
        String[] shifted = Arrays.copyOfRange(args, 1, args.length);
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "export" -> handleExport(sender, shifted);
            case "place" -> handlePlace(sender, shifted);
            default -> plugin.messageService().send(sender, "command.structure_usage");
        }
    }

    private List<String> suggestStructure(String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return STRUCTURE_LEVEL.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3 && "place".equalsIgnoreCase(args[1])) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return repository.templateIds().stream().filter(id -> id.startsWith(prefix)).toList();
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

    private List<String> suggestMultiblock(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return MULTIBLOCK_LEVEL.stream()
                    .filter(value -> value.startsWith(prefix) && hasMultiblockPermission(sender, value))
                    .toList();
        }
        if (args.length == 3 && "validate".equalsIgnoreCase(args[1])
                && sender.hasPermission(PERMISSION_ADMIN)) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return plugin.multiblockService().repository().ids().stream()
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }
        if (args.length == 4 && "export".equalsIgnoreCase(args[1])
                && sender.hasPermission(PERMISSION_EDIT) && !args[2].isBlank()) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return "force".startsWith(prefix) ? List.of("force") : List.of();
        }
        if (args.length == 3 && "place".equalsIgnoreCase(args[1])
                && sender.hasPermission(PERMISSION_EDIT)) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return plugin.multiblockService().repository().ids().stream()
                    .filter(id -> id.startsWith(prefix))
                    .toList();
        }
        if (args.length == 4 && "place".equalsIgnoreCase(args[1])
                && sender.hasPermission(PERMISSION_EDIT)) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return StructureFacing.configValues().stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private boolean hasMultiblockPermission(CommandSender sender, String subcommand) {
        return "export".equals(subcommand) || "place".equals(subcommand)
                ? sender.hasPermission(PERMISSION_EDIT)
                : sender.hasPermission(PERMISSION_ADMIN);
    }

    private List<String> suggestTemplateCommand(CommandSender sender, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return repository.templateIds().stream().filter(id -> id.startsWith(prefix)).toList();
        }
        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return StructureFacing.configValues().stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private List<String> suggestSelect(String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return SELECT_LEVEL.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 3 && "adjust".equals(args[1].toLowerCase(Locale.ROOT))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return ADJUST_OPERATIONS.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 4 && "adjust".equals(args[1].toLowerCase(Locale.ROOT))) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            return AdjustDirection.configValues().stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private void handleGet(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_USE)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.get_usage");
            return;
        }
        StructureFacing facing = resolveFacing(sender, args.length >= 3 ? args[2] : "south");
        if (facing == null) {
            return;
        }
        String templateName = args[1].toLowerCase(Locale.ROOT);
        if (!requireTemplateExists(sender, templateName, args[1])) {
            return;
        }
        Location front = frontAnchor(player, 3);
        loadAndStart(sender, templateName, front, facing);
    }

    private void handlePlace(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_EDIT)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "command.place_usage");
            return;
        }
        StructureFacing facing = resolveFacing(sender, args[2]);
        if (facing == null) {
            return;
        }
        String templateName = args[1].toLowerCase(Locale.ROOT);
        if (!requireTemplateExists(sender, templateName, args[1])) {
            return;
        }
        Location anchor = player.getLocation();
        loadAndStart(sender, templateName,
                new Location(anchor.getWorld(), anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ()),
                facing);
    }

    private void loadAndStart(CommandSender sender, String templateName, Location anchor,
            StructureFacing facing) {
        debug("place_load_request", Map.of(
                "template", templateName,
                "anchor", anchor.getBlockX() + "," + anchor.getBlockY() + "," + anchor.getBlockZ(),
                "world", anchor.getWorld() == null ? "null" : anchor.getWorld().getName(),
                "facing", facing.configValue(),
                "known", repository.templateIds().contains(templateName)));
        plugin.executionDispatcher().runAsync(plugin, () -> {
            Optional<StructureTemplate> loaded = repository.template(templateName);
            plugin.executionDispatcher().runGlobal(plugin, () -> {
                if (loaded.isEmpty()) {
                    debug("place_load_missing", Map.of("template", templateName));
                    plugin.messageService().send(sender, "error.template.not_found",
                            Map.of("template", templateName));
                    return;
                }
                StructureTemplate template = loaded.get();
                debug("place_load_done", Map.of(
                        "template", templateName,
                        "size", template.sizeX() + "x" + template.sizeY() + "x" + template.sizeZ(),
                        "blocks", template.blockCount(),
                        "entities", template.entities().size(),
                        "skipped_palette", template.skippedPaletteEntries()));
                warnLegacySkip(template);
                startGeneration(sender, template, anchor, facing);
            });
        });
    }

    private void startGeneration(CommandSender sender, StructureTemplate template, Location anchor,
            StructureFacing facing) {
        World world = anchor.getWorld();
        if (world == null) {
            plugin.messageService().send(sender, "error.generate.out_of_bounds",
                    Map.of("detail", "world unavailable"));
            return;
        }
        GenerationService.Request request = new GenerationService.Request(world, template,
                anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ(), facing, sender);
        generationService.start(request).ifPresent(rejection ->
                plugin.messageService().send(sender, rejection.reasonKey(), rejection.placeholders()));
    }

    private void handleSelect(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_EDIT)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.select_usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "pos1" -> setCorner(sender, player, true);
            case "pos2" -> setCorner(sender, player, false);
            case "clear" -> clearSelection(sender, player);
            case "adjust" -> adjustSelection(sender, player, args);
            default -> plugin.messageService().send(sender, "command.select_usage");
        }
    }

    private void setCorner(CommandSender sender, Player player, boolean first) {
        SelectionSession session = selectionManager.sessionFor(player, previousWorld ->
                plugin.messageService().send(sender, "error.selection.world_mismatch",
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
            AppConfig.GenerateSettings settings = config.get().generate();
            if (!box.get().withinLimits(settings.sizeLimitX(), settings.sizeLimitY(), settings.sizeLimitZ())) {
                session.restore(previousFirst, previousSecond);
                plugin.messageService().send(sender, "error.selection.too_large", Map.of(
                        "size_x", box.get().sizeX(), "size_y", box.get().sizeY(), "size_z", box.get().sizeZ(),
                        "limit_x", settings.sizeLimitX(), "limit_y", settings.sizeLimitY(),
                        "limit_z", settings.sizeLimitZ()));
                return;
            }
            reportSelectionReady(sender, session, box.get());
        } else {
            plugin.messageService().send(sender, "command.select_pos_set", Map.of(
                    "label", first ? "pos1" : "pos2",
                    "pos", corner.format()));
        }
        selectionManager.refresh(player, session);
    }

    private void reportSelectionReady(CommandSender sender, SelectionSession session, SelectionBox box) {
        plugin.messageService().send(sender, "command.select_ready", Map.of(
                "pos1", describe(box, true),
                "pos2", describe(box, false),
                "size_x", box.sizeX(),
                "size_y", box.sizeY(),
                "size_z", box.sizeZ()));
    }

    private void clearSelection(CommandSender sender, Player player) {
        selectionManager.clear(player);
        plugin.messageService().send(sender, "command.select_cleared");
    }

    private void adjustSelection(CommandSender sender, Player player, String[] args) {
        if (args.length < 5) {
            plugin.messageService().send(sender, "command.adjust_usage");
            return;
        }
        boolean grow = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> true;
            case "reduce" -> false;
            default -> {
                plugin.messageService().send(sender, "error.select.invalid_operation",
                        Map.of("value", args[2]));
                yield false;
            }
        };
        if (!args[2].equalsIgnoreCase("add") && !args[2].equalsIgnoreCase("reduce")) {
            return;
        }
        AdjustDirection direction = AdjustDirection.parse(args[3]);
        if (direction == null) {
            plugin.messageService().send(sender, "error.select.invalid_direction", Map.of(
                    "value", args[3],
                    "allowed", String.join(", ", AdjustDirection.configValues())));
            return;
        }
        long amount = parsePositiveInt(args[4]);
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            plugin.messageService().send(sender, "error.select.invalid_amount",
                    Map.of("value", args[4]));
            return;
        }
        SelectionSession session = selectionManager.boundSession(player);
        if (session == null) {
            plugin.messageService().send(sender, "error.select.not_set");
            return;
        }
        Optional<SelectionBox> current = session.box();
        if (current.isEmpty()) {
            plugin.messageService().send(sender, "error.select.not_set");
            return;
        }
        AdjustDirection.AxisVector axisVector = direction.resolve(player.getLocation().getYaw());
        Optional<SelectionBox> adjusted = current.get().adjust(axisVector.axis(),
                axisVector.positive(), grow, (int) amount);
        if (adjusted.isEmpty()) {
            SelectionBox box = current.get();
            plugin.messageService().send(sender, "error.select.shrink_too_far", Map.of(
                    "axis", axisVector.axis().name(),
                    "size", box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ()));
            return;
        }
        AppConfig.GenerateSettings settings = config.get().generate();
        SelectionBox next = adjusted.get();
        if (!next.withinLimits(settings.sizeLimitX(), settings.sizeLimitY(), settings.sizeLimitZ())) {
            plugin.messageService().send(sender, "error.selection.too_large", Map.of(
                    "size_x", next.sizeX(), "size_y", next.sizeY(), "size_z", next.sizeZ(),
                    "limit_x", settings.sizeLimitX(), "limit_y", settings.sizeLimitY(),
                    "limit_z", settings.sizeLimitZ()));
            return;
        }
        plugin.messageService().send(sender, "command.select_adjusted", Map.of(
                "before", describe(current.get(), true) + " -> " + describe(current.get(), false),
                "after", describe(next, true) + " -> " + describe(next, false)));
        session.replaceBox(next);
        selectionManager.refresh(player, session);
    }

    private void handleExport(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_EDIT)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.export_usage");
            return;
        }
        boolean includeEntities = args.length >= 3 && "entities".equalsIgnoreCase(args[2]);
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!NameRules.isValid(name)) {
            plugin.messageService().send(sender, "error.name.invalid", Map.of("value", args[1]));
            return;
        }
        SelectionSession session = selectionManager.boundSession(player);
        if (session == null || session.box().isEmpty()) {
            plugin.messageService().send(sender, "error.select.not_set");
            return;
        }
        SelectionBox box = session.box().get();
        AppConfig.GenerateSettings settings = config.get().generate();
        if (!box.withinLimits(settings.sizeLimitX(), settings.sizeLimitY(), settings.sizeLimitZ())) {
            plugin.messageService().send(sender, "error.export.too_large", Map.of(
                    "size_x", box.sizeX(), "size_y", box.sizeY(), "size_z", box.sizeZ(),
                    "limit_x", settings.sizeLimitX(), "limit_y", settings.sizeLimitY(),
                    "limit_z", settings.sizeLimitZ()));
            return;
        }
        World world = player.getWorld();
        String exportName = name;
        Map<Long, SelectionBox> regions = splitRegions(box);
        List<StructureExporter.CapturedBlock> blocks = Collections.synchronizedList(new ArrayList<>());
        List<StructureExporter.CapturedEntity> entities = Collections.synchronizedList(new ArrayList<>());
        List<StructureExporter.CapturedFurniture> furniture = Collections.synchronizedList(new ArrayList<>());
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger furnitureSkipped = new AtomicInteger();
        AtomicInteger remaining = new AtomicInteger(regions.size());
        for (SelectionBox region : regions.values()) {
            Location sliceOrigin = new Location(world, region.minX(), region.minY(), region.minZ());
            boolean dispatched;
            try {
                TaskToken token = plugin.executionDispatcher()
                        .runAtLocation(plugin, sliceOrigin, () -> {
                            try {
                                blocks.addAll(StructureExporter.captureBlocks(world, region, box));
                                StructureExporter.EntityCapture capture =
                                        StructureExporter.captureEntities(world, region, box, furnitureAdapter);
                                if (includeEntities) {
                                    entities.addAll(capture.entities());
                                }
                                furniture.addAll(capture.furniture());
                                furnitureSkipped.addAndGet(capture.outOfSelection());
                            } catch (RuntimeException exception) {
                                failures.add(String.valueOf(exception.getMessage()));
                            } finally {
                                if (remaining.decrementAndGet() == 0) {
                                    finishExport(sender, exportName, world, box, blocks, entities,
                                            furniture, furnitureSkipped.get(), failures);
                                }
                            }
                        });
                dispatched = token != null && token != TaskToken.UNAVAILABLE;
            } catch (RuntimeException exception) {
                dispatched = false;
                failures.add(String.valueOf(exception.getMessage()));
            }
            if (!dispatched && remaining.decrementAndGet() == 0) {
                finishExport(sender, exportName, world, box, blocks, entities,
                        furniture, furnitureSkipped.get(), failures);
            }
        }
    }

    private void finishExport(CommandSender sender, String name, World world, SelectionBox box,
            List<StructureExporter.CapturedBlock> blocks,
            List<StructureExporter.CapturedEntity> entities,
            List<StructureExporter.CapturedFurniture> furniture,
            int furnitureSkipped,
            List<String> failures) {
        plugin.executionDispatcher().runAsync(plugin, () -> {
            if (!failures.isEmpty()) {
                runGlobalMessage(sender, "error.export.write_failed", Map.of(
                        "file", name + ".nbt",
                        "error", failures.get(0)));
                return;
            }
            if (blocks.isEmpty()) {
                runGlobalMessage(sender, "error.export.no_blocks", Map.of("count", 0));
                return;
            }
            StructureExporter.Captured captured = new StructureExporter.Captured(
                    box.sizeX(), box.sizeY(), box.sizeZ(),
                    List.copyOf(blocks), List.copyOf(entities));
            byte[] payload = StructureExporter.toGzip(StructureExporter.encode(captured));
            Path target = plugin.dataPath("structures", name + ".nbt");
            try {
                Path parent = target.getParent();
                Files.createDirectories(parent);
                Files.write(target, payload);
            } catch (IOException exception) {
                runGlobalMessage(sender, "error.export.write_failed", Map.of(
                        "file", target.getFileName().toString(),
                        "error", String.valueOf(exception.getMessage())));
                return;
            }
            int furnitureCount = writeFurnitureSidecar(sender, name, furniture);
            plugin.executionDispatcher().runGlobal(plugin, () -> {
                repository.reload();
                plugin.spawnSidecars().reload();
                plugin.furnitureSidecars().reload();
                plugin.messageService().send(sender, "command.export_saved", Map.of(
                        "name", name,
                        "blocks", captured.blocks().size(),
                        "size_x", captured.sizeX(),
                        "size_y", captured.sizeY(),
                        "size_z", captured.sizeZ(),
                        "file", target.getFileName().toString(),
                        "furniture", furnitureCount,
                        "furniture_skipped", furnitureSkipped));
                if (!furnitureAdapter.anySourcePresent()) {
                    plugin.messageService().send(sender, "console.export_furniture_absent");
                }
            });
        });
    }

    private int writeFurnitureSidecar(CommandSender sender, String name,
            List<StructureExporter.CapturedFurniture> furniture) {
        if (!furnitureAdapter.anySourcePresent()) {
            return 0;
        }
        List<FurnitureEntry> entries = new ArrayList<>(furniture.size());
        for (StructureExporter.CapturedFurniture captured : furniture) {
            entries.add(new FurnitureEntry(captured.id(), captured.x(), captured.y(),
                    captured.z(), captured.yaw(), captured.facing()));
        }
        Path target = plugin.dataPath("furniture", name + ".yml");
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, FurnitureSidecarParser.encode(entries).getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            runGlobalMessage(sender, "error.export.write_failed", Map.of(
                    "file", target.getFileName().toString(),
                    "error", String.valueOf(exception.getMessage())));
        }
        return entries.size();
    }

    private void runGlobalMessage(CommandSender sender, String key, Map<String, Object> placeholders) {
        plugin.executionDispatcher().runGlobal(plugin,
                () -> plugin.messageService().send(sender, key, placeholders));
    }

    private static Map<Long, SelectionBox> splitRegions(SelectionBox box) {
        Map<Long, SelectionBox> regions = new LinkedHashMap<>();
        int startRegionX = box.minX() >> 4;
        int endRegionX = box.maxX() >> 4;
        int startRegionZ = box.minZ() >> 4;
        int endRegionZ = box.maxZ() >> 4;
        for (int regionX = startRegionX; regionX <= endRegionX; regionX++) {
            for (int regionZ = startRegionZ; regionZ <= endRegionZ; regionZ++) {
                int subMinX = Math.max(box.minX(), regionX << 4);
                int subMaxX = Math.min(box.maxX(), (regionX << 4) + 15);
                int subMinZ = Math.max(box.minZ(), regionZ << 4);
                int subMaxZ = Math.min(box.maxZ(), (regionZ << 4) + 15);
                long key = ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
                regions.put(key, new SelectionBox(subMinX, box.minY(), subMinZ,
                        subMaxX, box.maxY(), subMaxZ));
            }
        }
        return regions;
    }

    private void handleReload(CommandSender sender) {
        if (!requirePermission(sender, PERMISSION_RELOAD)) {
            return;
        }
        int count = plugin.reloadContent();
        plugin.messageService().send(sender, "console.reload_complete", Map.of("count", count));
    }

    private void handleMultiblock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.multiblock_usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> handleMultiblockList(sender);
            case "validate" -> handleMultiblockValidate(sender, args);
            case "export" -> handleMultiblockExport(sender, args);
            case "place" -> handleMultiblockPlace(sender, args);
            default -> plugin.messageService().send(sender, "command.multiblock_usage");
        }
    }

    private void handleMultiblockList(CommandSender sender) {
        if (!requirePermission(sender, PERMISSION_ADMIN)) {
            return;
        }
        var service = plugin.multiblockService();
        List<String> ids = service.repository().ids();
        plugin.messageService().send(sender, "command.multiblock_list_header",
                Map.of("count", ids.size(),
                        "candidates", service.registry().candidateInstanceCount(),
                        "formed", service.registry().formedInstanceCount()));
        for (String id : ids) {
            var definition = service.repository().byId(id).orElse(null);
            if (definition == null) {
                continue;
            }
            plugin.messageService().send(sender, "command.multiblock_list_entry", Map.of(
                    "id", definition.id(),
                    "members", definition.members().size(),
                    "rotation", String.valueOf(definition.allowRotation())));
        }
    }

    private void handleMultiblockValidate(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_ADMIN)) {
            return;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "command.multiblock_validate_usage");
            return;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        if (!NameRules.isValid(id)) {
            plugin.messageService().send(sender, "error.name.invalid", Map.of("value", args[2]));
            return;
        }
        var definition = plugin.multiblockService().repository().byId(id).orElse(null);
        if (definition == null) {
            plugin.messageService().send(sender, "error.multiblock.not_found",
                    Map.of("id", id));
            return;
        }
        var service = plugin.multiblockService();
        int unresolvableBlocks = 0;
        Set<String> missingSources = new LinkedHashSet<>();
        for (var member : definition.members()) {
            if (member.requiresCustomBlock()
                    && !plugin.multiblockService().engine().customBlocks()
                            .bind(member.namespaceId()).resolvable()) {
                unresolvableBlocks++;
            }
            if (member.requiresFurniture()) {
                String source = FurnitureIdAdapter.sourceOfNamespace(
                        FurnitureIdAdapter.namespaceOf(member.spec()));
                if (source != null && !service.engine().furniture().sourcePresent(source)) {
                    missingSources.add(source);
                }
            }
        }
        plugin.messageService().send(sender, "command.multiblock_validate_result", Map.of(
                "id", definition.id(),
                "members", definition.members().size(),
                "unresolvable", unresolvableBlocks,
                "furniture_missing", missingSources.isEmpty()
                        ? plugin.messageService()
                                .messageOrFallback("command.multiblock_missing_none", "none")
                        : String.join(", ", missingSources),
                "candidates", service.registry().candidateInstanceCount(),
                "formed", countFormedFor(definition.id())));
    }

    private int countFormedFor(String definitionId) {
        int count = 0;
        for (var instance : plugin.multiblockService().registry().allFormed()) {
            if (instance.pose().definitionId().equals(definitionId)) {
                count++;
            }
        }
        return count;
    }

    private void handleMultiblockExport(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_EDIT)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "command.multiblock_export_usage");
            return;
        }
        String rawId = args[2];
        String id = rawId.toLowerCase(Locale.ROOT);
        if (!NameRules.isValid(id)) {
            plugin.messageService().send(sender, "error.name.invalid", Map.of("value", rawId));
            return;
        }
        boolean force = args.length >= 4 && "force".equalsIgnoreCase(args[3]);
        var service = plugin.multiblockService();
        Path definitionFile = plugin.dataPath("multiblocks", id + ".yml");
        if (!force && (service.repository().byId(id).isPresent()
                || Files.isRegularFile(definitionFile))) {
            plugin.messageService().send(sender, "error.multiblock.already_exists",
                    Map.of("id", id, "file", definitionFile.getFileName().toString()));
            return;
        }
        SelectionSession session = selectionManager.boundSession(player);
        if (session == null || session.box().isEmpty()) {
            plugin.messageService().send(sender, "error.select.not_set");
            return;
        }
        SelectionBox box = session.box().get();
        AppConfig.GenerateSettings settings = config.get().generate();
        if (!box.withinLimits(settings.sizeLimitX(), settings.sizeLimitY(), settings.sizeLimitZ())) {
            plugin.messageService().send(sender, "error.export.too_large", Map.of(
                    "size_x", box.sizeX(), "size_y", box.sizeY(), "size_z", box.sizeZ(),
                    "limit_x", settings.sizeLimitX(), "limit_y", settings.sizeLimitY(),
                    "limit_z", settings.sizeLimitZ()));
            return;
        }
        World world = player.getWorld();
        String definitionId = id;
        Map<Long, SelectionBox> regions = splitRegions(box);
        List<int[]> positions = Collections.synchronizedList(new ArrayList<>());
        List<String> specs = Collections.synchronizedList(new ArrayList<>());
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger remaining = new AtomicInteger(regions.size());
        for (SelectionBox region : regions.values()) {
            Location sliceOrigin = new Location(world, region.minX(), region.minY(), region.minZ());
            boolean dispatched;
            try {
                TaskToken token = plugin.executionDispatcher()
                        .runAtLocation(plugin, sliceOrigin, () -> {
                            try {
                                if (!world.isChunkLoaded(region.minX() >> 4, region.minZ() >> 4)) {
                                    failures.add("chunk " + (region.minX() >> 4) + ","
                                            + (region.minZ() >> 4));
                                } else {
                                    captureMembers(world, region, box, positions, specs);
                                }
                            } catch (RuntimeException exception) {
                                failures.add(String.valueOf(exception.getMessage()));
                            } finally {
                                if (remaining.decrementAndGet() == 0) {
                                    finishExport(sender, definitionId, world, box,
                                            positions, specs, failures);
                                }
                            }
                        });
                dispatched = token != null && token != TaskToken.UNAVAILABLE;
            } catch (RuntimeException exception) {
                dispatched = false;
                failures.add(String.valueOf(exception.getMessage()));
            }
            if (!dispatched && remaining.decrementAndGet() == 0) {
                finishExport(sender, definitionId, world, box, positions, specs, failures);
            }
        }
    }

    private void captureMembers(World world, SelectionBox region, SelectionBox full,
            List<int[]> positions, List<String> specs) {
        var adapter = plugin.multiblockService().engine().customBlocks();
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

    private void finishExport(CommandSender sender, String id, World world, SelectionBox box,
            List<int[]> positions, List<String> specs, List<String> failures) {
        plugin.executionDispatcher().runAsync(plugin, () -> {
            if (!failures.isEmpty()) {
                runGlobalMessage(sender, "error.multiblock.export_failed", Map.of(
                        "id", id, "detail", failures.get(0)));
                return;
            }
            if (positions.isEmpty()) {
                runGlobalMessage(sender, "error.export.no_blocks", Map.of("count", 0));
                return;
            }
            String content = renderMultiblockYaml(id, positions, specs);
            Path target = plugin.dataPath("multiblocks", id + ".yml");
            try {
                Path parent = target.getParent();
                Files.createDirectories(parent);
                Path temporary = parent.resolve(id + ".yml.tmp");
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                runGlobalMessage(sender, "error.multiblock.write_failed", Map.of(
                        "id", id, "detail", String.valueOf(exception.getMessage())));
                return;
            }
            plugin.executionDispatcher().runGlobal(plugin, () -> {
                plugin.multiblockService().repository().reload();
                reportMultiblockProblems(sender, plugin.multiblockService().repository());
                plugin.messageService().send(sender, "command.multiblock_export_saved", Map.of(
                        "id", id,
                        "members", positions.size(),
                        "file", target.getFileName().toString()));
            });
        });
    }

    public static String renderMultiblockYaml(String id, List<int[]> positions, List<String> specs) {
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
            builder.append("      block: \"").append(escapeYaml(specs.get(index))).append("\"\n");
        }
        builder.append("trigger:\n");
        builder.append("  targets: any\n");
        return builder.toString();
    }

    private static String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void handleMultiblockPlace(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_EDIT)) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "command.multiblock_place_usage");
            return;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        var definition = plugin.multiblockService().repository().byId(id).orElse(null);
        if (definition == null) {
            plugin.messageService().send(sender, "error.multiblock.not_found", Map.of("id", id));
            return;
        }
        StructureFacing requested = StructureFacing.SOUTH;
        if (args.length >= 4) {
            StructureFacing parsed = resolveFacing(sender, args[3]);
            if (parsed == null) {
                return;
            }
            if (definition.allowRotation()) {
                requested = parsed;
            }
        }
        StructureFacing facing = requested;
        World world = player.getWorld();
        Location anchor = player.getLocation();
        int anchorX = anchor.getBlockX();
        int anchorY = anchor.getBlockY();
        int anchorZ = anchor.getBlockZ();
        debug("multiblock_place", Map.of(
                "id", id,
                "members", definition.members().size(),
                "anchor", anchorX + "," + anchorY + "," + anchorZ,
                "world", world.getName(),
                "facing", facing.configValue(),
                "allow_rotation", definition.allowRotation()));
        AtomicInteger placed = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<String> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger remaining = new AtomicInteger(definition.members().size());
        for (var member : definition.members()) {
            Vector offset = TemplateTransform.localToWorld(member.x(), member.y(),
                    member.z(), definition.sizeX(), definition.sizeZ(), facing);
            int worldX = anchorX + offset.getBlockX();
            int worldY = anchorY + offset.getBlockY();
            int worldZ = anchorZ + offset.getBlockZ();
            Location target = new Location(world, worldX, worldY, worldZ);
            boolean dispatched;
            try {
                TaskToken token = plugin.executionDispatcher().runAtLocation(plugin, target, () -> {
                    try {
                        if (placeMember(world, member, facing, worldX, worldY, worldZ)) {
                            placed.incrementAndGet();
                        } else {
                            failed.incrementAndGet();
                            failures.add(member.spec() + "@" + worldX + "," + worldY + "," + worldZ);
                        }
                    } catch (RuntimeException exception) {
                        failed.incrementAndGet();
                        failures.add(member.spec() + "@" + worldX + "," + worldY + "," + worldZ
                                + ": " + exception.getMessage());
                    } finally {
                        if (remaining.decrementAndGet() == 0) {
                            finishMultiblockPlace(sender, player, id, world,
                                    anchorX, anchorY, anchorZ, facing,
                                    placed.get(), failed.get(), failures);
                        }
                    }
                });
                dispatched = token != null && token != TaskToken.UNAVAILABLE;
            } catch (RuntimeException exception) {
                dispatched = false;
                failed.incrementAndGet();
                failures.add(member.spec() + ": " + exception.getMessage());
            }
            if (!dispatched && remaining.decrementAndGet() == 0) {
                finishMultiblockPlace(sender, player, id, world,
                        anchorX, anchorY, anchorZ, facing,
                        placed.get(), failed.get(), failures);
            }
        }
    }

    private boolean placeMember(World world, MultiblockDefinition.Member member,
            StructureFacing facing, int worldX, int worldY, int worldZ) {
        Block block = world.getBlockAt(worldX, worldY, worldZ);
        switch (member.kind()) {
            case VANILLA_STATE -> block.setBlockData(
                    TemplateTransform.apply(member.blockData(), facing), false);
            case VANILLA_MATERIAL -> block.setType(member.material(), false);
            case CUSTOM_BLOCK -> {
                var adapter = plugin.multiblockService().engine().customBlocks();
                if (adapter == null || !adapter.place(block, adapter.bind(member.namespaceId()))) {
                    return false;
                }
            }
            case FURNITURE -> {
                Location spot = new Location(world, worldX + 0.5d, worldY + 1.0d, worldZ + 0.5d,
                        (float) FurnitureSidecarParser.normalizeYaw(facing.yawDegrees()), 0.0f);
                if (furniturePlacer.place(member.spec(), spot) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private void finishMultiblockPlace(CommandSender sender, Player player, String id, World world,
            int anchorX, int anchorY, int anchorZ, StructureFacing facing,
            int placed, int failed, List<String> failures) {
        debug("multiblock_place_done", Map.of(
                "id", id,
                "placed", placed,
                "failed", failed,
                "anchor", anchorX + "," + anchorY + "," + anchorZ,
                "first_failures", failures.stream().limit(3).toList()));
        try {
            plugin.executionDispatcher().runAtLocation(plugin,
                    new Location(world, anchorX, anchorY, anchorZ),
                    () -> plugin.multiblockService().onBlockPlaced(world, anchorX, anchorY,
                            anchorZ, player.getUniqueId()));
        } catch (RuntimeException exception) {
            debug("multiblock_place_error", Map.of(
                    "step", "detection",
                    "detail", "type=" + exception.getClass().getSimpleName()
                            + " message=" + exception.getMessage()));
        }
        plugin.executionDispatcher().runGlobal(plugin, () -> {
            plugin.messageService().send(sender, "command.multiblock_place_done", Map.of(
                    "id", id,
                    "placed", placed,
                    "failed", failed,
                    "facing", facing.configValue(),
                    "x", anchorX,
                    "y", anchorY,
                    "z", anchorZ));
            for (int index = 0; index < Math.min(failures.size(), 5); index++) {
                plugin.messageService().send(sender, "command.multiblock_place_entry_failed",
                        Map.of("detail", failures.get(index)));
            }
        });
    }

    private void debug(String langKey, Map<String, ?> replacements) {
        var logger = plugin.debugLogger();
        if (logger == null || !logger.shouldLog("structure", (UUID) null)) {
            return;
        }
        logger.log("structure", (UUID) null, langKey, replacements);
    }

    private void reportMultiblockProblems(CommandSender sender, MultiblockRepository repository) {
        for (var problem : repository.drainProblems()) {
            plugin.messageService().send(sender, problem.reasonKey(), problem.placeholders());
        }
    }

    private void sendHelp(CommandSender sender) {
        var messages = plugin.messageService();
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

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return;
        }
        if (plugin.debugCommand() == null) {
            return;
        }
        plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length),
                plugin.messageService());
    }

    private boolean requireTemplateExists(CommandSender sender, String templateName, String raw) {
        if (!NameRules.isValid(templateName)) {
            plugin.messageService().send(sender, "error.name.invalid", Map.of("value", raw));
            return false;
        }
        if (!repository.templateIds().contains(templateName)) {
            plugin.messageService().send(sender, "error.template.not_found",
                    Map.of("template", templateName));
            return false;
        }
        return true;
    }
    private void warnLegacySkip(StructureTemplate template) {
        if (template.skippedPaletteEntries() > 0 && legacySkipWarnings.add(template.id())) {
            plugin.messageService().warning("console.template_legacy_skipped", Map.of(
                    "template", template.id(),
                    "count", template.skippedPaletteEntries()));
        }
    }

    private StructureFacing resolveFacing(CommandSender sender, String raw) {
        StructureFacing facing = StructureFacing.parse(raw);
        if (facing == null) {
            plugin.messageService().send(sender, "command.invalid_facing", Map.of(
                    "value", raw,
                    "allowed", String.join(", ", StructureFacing.configValues())));
        }
        return facing;
    }

    private Location frontAnchor(Player player, int distance) {
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

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.messageService().send(sender, "general.no_permission");
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.messageService().send(sender, "general.player_only");
        return null;
    }

    private boolean hasPermission(CommandSender sender, String subcommand) {
        return switch (subcommand) {
            case "help" -> true;
            case "debug" -> sender.hasPermission(PERMISSION_ADMIN);
            case "get" -> sender.hasPermission(PERMISSION_USE);
            case "reload" -> sender.hasPermission(PERMISSION_RELOAD);
            case "structure" -> sender.hasPermission(PERMISSION_EDIT);
            case "multiblock" -> sender.hasPermission(PERMISSION_EDIT)
                    || sender.hasPermission(PERMISSION_ADMIN);
            default -> sender.hasPermission(PERMISSION_EDIT);
        };
    }

    private static String describe(SelectionBox box, boolean first) {
        return first
                ? box.minX() + ", " + box.minY() + ", " + box.minZ()
                : box.maxX() + ", " + box.maxY() + ", " + box.maxZ();
    }

    private static long parsePositiveInt(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }
}

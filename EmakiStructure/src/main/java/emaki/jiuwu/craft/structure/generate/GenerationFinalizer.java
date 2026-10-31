package emaki.jiuwu.craft.structure.generate;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.furniture.FurnitureEntry;
import emaki.jiuwu.craft.structure.furniture.FurnitureSidecarParser;
import emaki.jiuwu.craft.structure.furniture.FurnitureSidecarRepository;
import emaki.jiuwu.craft.structure.integration.FurnitureIdAdapter;
import emaki.jiuwu.craft.structure.integration.furniture.FurniturePlacer;
import emaki.jiuwu.craft.structure.rollback.GenerationJournal;
import emaki.jiuwu.craft.structure.rollback.RollbackService;
import emaki.jiuwu.craft.structure.spawn.SpawnContext;
import emaki.jiuwu.craft.structure.spawn.SpawnEntry;
import emaki.jiuwu.craft.structure.spawn.SpawnExecutor;
import emaki.jiuwu.craft.structure.spawn.SpawnSidecarParser;
import emaki.jiuwu.craft.structure.spawn.SpawnSidecarRepository;
import emaki.jiuwu.craft.structure.spawn.VanillaSpawnBridge;
import emaki.jiuwu.craft.structure.template.StructureTemplate;
import emaki.jiuwu.craft.structure.template.TemplateEntity;
import emaki.jiuwu.craft.structure.template.nbt.NbtValue;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class GenerationFinalizer {

    private static final String DETAIL_JOURNAL_FAILED = "回滚日志写入失败";

    private record BlockEntityWrite(int x, int y, int z, NbtValue.CompoundTag nbt) {
    }

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final BlockEntityWriter blockEntityWriter;
    private final SpawnSidecarRepository spawnSidecars;
    private final SpawnExecutor spawnExecutor;
    private final FurnitureSidecarRepository furnitureSidecars;
    private final FurniturePlacer furniturePlacer;
    private final RollbackService rollbackService;
    private final Supplier<AppConfig> config;

    public GenerationFinalizer(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher,
            BlockEntityWriter blockEntityWriter,
            SpawnSidecarRepository spawnSidecars,
            SpawnExecutor spawnExecutor,
            FurnitureSidecarRepository furnitureSidecars,
            FurniturePlacer furniturePlacer,
            RollbackService rollbackService,
            Supplier<AppConfig> config) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.blockEntityWriter = blockEntityWriter;
        this.spawnSidecars = spawnSidecars;
        this.spawnExecutor = spawnExecutor;
        this.furnitureSidecars = furnitureSidecars;
        this.furniturePlacer = furniturePlacer;
        this.rollbackService = rollbackService;
        this.config = config;
    }

    public void reportRejection(GenerationService.Request request, GenerationService.Rejection rejection) {
        plugin.messageService().send(request.initiator(), rejection.reasonKey(), rejection.placeholders());
    }

    private static UUID initiatorId(GenerationService.Request request) {
        return request.initiator() instanceof Player player ? player.getUniqueId() : null;
    }

    private void debug(String langKey, Map<String, ?> replacements) {
        var logger = plugin.debugLogger();
        if (logger == null || !logger.shouldLog("structure", (UUID) null)) {
            return;
        }
        logger.log("structure", (UUID) null, langKey, replacements);
    }

    public void onSettled(GenerationService.Request request, GenerationJob job,
            AtomicLong overwritten, long startedNanos) {
        if (job.outcome() != GenerationJob.Outcome.COMPLETED) {
            debug("place_settled_failed", Map.of(
                    "template", request.template().id(),
                    "outcome", String.valueOf(job.outcome()),
                    "reason", String.valueOf(job.abortReasonKey()),
                    "detail", String.valueOf(job.abortDetail()),
                    "overwritten", overwritten.get()));
            plugin.messageService().send(request.initiator(),
                    job.abortReasonKey() == null ? "error.generate.failed" : job.abortReasonKey(),
                    Map.of("template", request.template().id(),
                            "reason", String.valueOf(job.abortDetail())));
            plugin.debugLogger().log("structure", initiatorId(request), "generate_aborted", Map.of(
                    "template", request.template().id(),
                    "reason", String.valueOf(job.abortReasonKey()),
                    "detail", String.valueOf(job.abortDetail()),
                    "initiator", request.initiatorName()));
            rollbackService.settle(job);
            return;
        }
        StructureTemplate template = request.template();
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        debug("place_settled_done", Map.of(
                "template", template.id(),
                "blocks", template.blockCount(),
                "overwritten", overwritten.get(),
                "millis", elapsedMillis,
                "journal", job.journal() == null ? "none" : "present"));
        plugin.debugLogger().log("structure", initiatorId(request), "generate_complete", Map.of(
                "template", template.id(),
                "blocks", template.blockCount(),
                "millis", elapsedMillis,
                "initiator", request.initiatorName()));
        runStageTwo(request, job);
        if (!rollbackService.settle(job)) {
            reportSettleFailure(request, job);
            return;
        }
        plugin.messageService().send(request.initiator(), "command.place_done", Map.of(
                "template", template.id(),
                "facing", request.facing().configValue(),
                "x", request.anchorX(),
                "y", request.anchorY(),
                "z", request.anchorZ(),
                "overwritten", overwritten.get()));
    }

    private void reportSettleFailure(GenerationService.Request request, GenerationJob job) {
        plugin.getLogger().warning("回滚日志提交失败, 事务 " + job.id()
                + " 已中止, 模板 " + request.template().id());
        plugin.messageService().send(request.initiator(), "error.generate.failed",
                Map.of("reason", DETAIL_JOURNAL_FAILED));
    }

    private boolean journalWritable(GenerationJob job) {
        GenerationJournal journal = job.journal();
        if (journal == null) {
            return true;
        }
        if (journal.isHealthy()) {
            return true;
        }
        plugin.getLogger().warning("回滚日志已写入失败, 跳过生成后处理, 事务 " + job.id());
        return false;
    }

    private void runStageTwo(GenerationService.Request request, GenerationJob job) {
        if (!journalWritable(job)) {
            return;
        }
        GenerationJournal journal = job.journal();
        StructureTemplate template = request.template();
        World world = request.world();
        for (Map.Entry<Long, List<BlockEntityWrite>> entry : groupBlockEntities(request).entrySet()) {
            List<BlockEntityWrite> group = entry.getValue();
            BlockEntityWrite head = group.get(0);
            dispatchAt(world, head.x(), head.y(), head.z(), () -> {
                for (BlockEntityWrite write : group) {
                    blockEntityWriter.apply(world, write.x(), write.y(), write.z(), write.nbt());
                }
            });
        }
        spawnTemplateEntities(request, journal);
        restoreFurniture(request, journal);
        scheduleSidecarSpawns(request, journal);
        int transformedX = TemplateTransform.transformedSizeX(template.sizeX(), template.sizeZ(), request.facing());
        int transformedZ = TemplateTransform.transformedSizeZ(template.sizeX(), template.sizeZ(), request.facing());
        int rescueX = request.anchorX();
        int rescueY = request.anchorY();
        int rescueZ = request.anchorZ();
        dispatchAt(world, rescueX, rescueY, rescueZ, () -> {
            List<Player> trapped = PlayerRescue.liftTrapped(world, rescueX, rescueY, rescueZ,
                    rescueX + transformedX - 1, rescueY + template.sizeY() - 1, rescueZ + transformedZ - 1);
            if (!trapped.isEmpty()) {
                plugin.messageService().send(request.initiator(), "error.generate.player_trapped",
                        Map.of("count", trapped.size()));
            }
        });
    }

    private Map<Long, List<BlockEntityWrite>> groupBlockEntities(GenerationService.Request request) {
        StructureTemplate template = request.template();
        Map<Long, List<BlockEntityWrite>> grouped = new LinkedHashMap<>();
        for (int y = 0; y < template.sizeY(); y++) {
            for (int z = 0; z < template.sizeZ(); z++) {
                for (int x = 0; x < template.sizeX(); x++) {
                    NbtValue.CompoundTag nbt = template.blockEntityAt(x, y, z).orElse(null);
                    if (nbt == null) {
                        continue;
                    }
                    Vector offset = TemplateTransform.localToWorld(x, y, z,
                            template.sizeX(), template.sizeZ(), request.facing());
                    int worldX = request.anchorX() + offset.getBlockX();
                    int worldY = request.anchorY() + offset.getBlockY();
                    int worldZ = request.anchorZ() + offset.getBlockZ();
                    long key = ((long) (worldX >> 4) << 32) | ((worldZ >> 4) & 0xFFFFFFFFL);
                    grouped.computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add(new BlockEntityWrite(worldX, worldY, worldZ, nbt));
                }
            }
        }
        return grouped;
    }

    private void spawnTemplateEntities(GenerationService.Request request, GenerationJournal journal) {
        StructureTemplate template = request.template();
        AppConfig.EntityPolicy policy = config.get().generate().entityPolicy();
        if (policy == AppConfig.EntityPolicy.NONE) {
            return;
        }
        for (TemplateEntity entity : template.entities()) {
            Vector offset = TemplateTransform.localToWorld((int) Math.floor(entity.x()),
                    (int) Math.floor(entity.y()), (int) Math.floor(entity.z()),
                    template.sizeX(), template.sizeZ(), request.facing());
            double worldX = request.anchorX() + offset.getBlockX() + (entity.x() - Math.floor(entity.x()));
            double worldY = request.anchorY() + offset.getBlockY() + (entity.y() - Math.floor(entity.y()));
            double worldZ = request.anchorZ() + offset.getBlockZ() + (entity.z() - Math.floor(entity.z()));
            float yaw = TemplateTransform.combinedYaw(entity.yaw(), request.facing());
            Location location = new Location(request.world(), worldX, worldY, worldZ, yaw, entity.pitch());
            dispatchAt(request.world(), (int) Math.floor(worldX), (int) Math.floor(worldY),
                    (int) Math.floor(worldZ), () -> spawnTemplateEntity(location, entity, policy, journal));
        }
    }

    private void spawnTemplateEntity(Location location, TemplateEntity entity,
            AppConfig.EntityPolicy policy, GenerationJournal journal) {
        if (location.getWorld() == null) {
            return;
        }
        try {
            Entity spawned = location.getWorld().spawnEntity(location, resolveEntityType(entity.type()));
            if (policy == AppConfig.EntityPolicy.PASSIVE && spawned instanceof LivingEntity living
                    && VanillaSpawnBridge.isHostile(living)) {
                living.remove();
                return;
            }
            if (journal != null) {
                journal.appendEntity(location.getWorld().getName(), spawned.getUniqueId(),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ());
            }
        } catch (GenerationJournal.JournalWriteException exception) {
            plugin.getLogger().log(Level.WARNING, "模板实体回滚日志写入失败 " + entity.type(), exception);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("模板实体生成失败 " + entity.type() + ": " + exception.getMessage());
        }
    }

    private EntityType resolveEntityType(String typeId) {
        NamespacedKey key = NamespacedKey.fromString(typeId);
        if (key == null) {
            throw new IllegalArgumentException("非法实体 id: " + typeId);
        }
        EntityType type = Registry.ENTITY_TYPE.get(key);
        if (type == null) {
            throw new IllegalArgumentException("注册表中不存在: " + typeId);
        }
        return type;
    }

    private void restoreFurniture(GenerationService.Request request, GenerationJournal journal) {
        StructureTemplate template = request.template();
        FurnitureSidecarParser.Result sidecar = furnitureSidecars.resolve(template);
        if (sidecar.fileRejected() || sidecar.entries().isEmpty()) {
            return;
        }
        StructureFacing facing = request.facing();
        int index = 0;
        for (FurnitureEntry entry : sidecar.entries()) {
            String namespace = FurnitureIdAdapter.namespaceOf(entry.id());
            if (!furniturePlacer.sourcePresent(namespace)) {
                plugin.messageService().warning("console.furniture_source_absent", Map.of(
                        "template", template.id(),
                        "index", index,
                        "id", entry.id()));
                index++;
                continue;
            }
            Vector offset = TemplateTransform.localToWorld(entry.x(), entry.y(), entry.z(),
                    template.sizeX(), template.sizeZ(), facing);
            int worldX = request.anchorX() + offset.getBlockX();
            int worldY = request.anchorY() + offset.getBlockY();
            int worldZ = request.anchorZ() + offset.getBlockZ();
            float yaw = (float) FurnitureSidecarParser.normalizeYaw(
                    entry.yaw() + facing.yawDegrees());
            Location location = new Location(request.world(),
                    worldX + 0.5d, worldY + 1.0d, worldZ + 0.5d, yaw, 0.0f);
            String id = entry.id();
            int entryIndex = index;
            dispatchAt(request.world(), worldX, worldY, worldZ,
                    () -> placeFurniture(location, id, template.id(), entryIndex, journal));
            index++;
        }
    }

    private void placeFurniture(Location location, String id, String templateId,
            int entryIndex, GenerationJournal journal) {
        if (location.getWorld() == null) {
            return;
        }
        try {
            Entity placed = furniturePlacer.place(id, location);
            if (placed == null) {
                plugin.messageService().warning("console.furniture_place_failed", Map.of(
                        "template", templateId,
                        "index", entryIndex,
                        "id", id));
                return;
            }
            if (journal != null) {
                Location entityLocation = placed.getLocation();
                journal.appendEntity(location.getWorld().getName(), placed.getUniqueId(),
                        entityLocation.getBlockX(), entityLocation.getBlockY(),
                        entityLocation.getBlockZ());
            }
        } catch (GenerationJournal.JournalWriteException exception) {
            plugin.getLogger().log(Level.WARNING, "家具回滚日志写入失败 " + templateId, exception);
        } catch (RuntimeException exception) {
            plugin.messageService().warning("console.furniture_place_failed", Map.of(
                    "template", templateId,
                    "index", entryIndex,
                    "id", id));
        }
    }

    private void scheduleSidecarSpawns(GenerationService.Request request, GenerationJournal journal) {
        StructureTemplate template = request.template();
        SpawnSidecarParser.Result sidecar = spawnSidecars.resolve(template);
        if (sidecar.fileRejected() || sidecar.entries().isEmpty()) {
            return;
        }
        SpawnContext context = new SpawnContext(request.world(), template, request.facing(),
                request.anchorX(), request.anchorY(), request.anchorZ(),
                request.facing().yawDegrees());
        int index = 0;
        for (SpawnEntry entry : sidecar.entries()) {
            spawnExecutor.schedule(context, entry, index, template.id(), journal);
            index++;
        }
    }

    private void dispatchAt(World world, int x, int y, int z, Runnable task) {
        try {
            dispatcher.runAtLocation(plugin, new Location(world, x, y, z), task);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("生成后处理任务提交失败: " + exception.getMessage());
        }
    }
}

package emaki.jiuwu.craft.structure.generate;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.config.AppConfig;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class GenerationFinalizer {

    private record BlockEntityWrite(int x, int y, int z, NbtValue.CompoundTag nbt) {
    }

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final BlockEntityWriter blockEntityWriter;
    private final SpawnSidecarRepository spawnSidecars;
    private final SpawnExecutor spawnExecutor;
    private final Supplier<AppConfig> config;

    public GenerationFinalizer(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher,
            BlockEntityWriter blockEntityWriter,
            SpawnSidecarRepository spawnSidecars,
            SpawnExecutor spawnExecutor,
            Supplier<AppConfig> config) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.blockEntityWriter = blockEntityWriter;
        this.spawnSidecars = spawnSidecars;
        this.spawnExecutor = spawnExecutor;
        this.config = config;
    }

    public void reportRejection(GenerationService.Request request, GenerationService.Rejection rejection) {
        plugin.messageService().send(request.initiator(), rejection.reasonKey(), rejection.placeholders());
    }

    public void onSettled(GenerationService.Request request, GenerationJob job,
            AtomicLong overwritten, long startedNanos) {
        if (job.outcome() != GenerationJob.Outcome.COMPLETED) {
            plugin.messageService().send(request.initiator(),
                    job.abortReasonKey() == null ? "error.generate.failed" : job.abortReasonKey(),
                    Map.of("template", request.template().id(),
                            "reason", String.valueOf(job.abortDetail())));
            return;
        }
        StructureTemplate template = request.template();
        long elapsedMillis = (System.nanoTime() - startedNanos) / 1_000_000L;
        if (request.initiator() instanceof Player player) {
            plugin.debugLogger().log("structure", player, "generate_complete", Map.of(
                    "template", template.id(),
                    "blocks", template.blockCount(),
                    "millis", elapsedMillis));
        }
        plugin.messageService().send(request.initiator(), "command.place_done", Map.of(
                "template", template.id(),
                "facing", request.facing().configValue(),
                "x", request.anchorX(),
                "y", request.anchorY(),
                "z", request.anchorZ(),
                "overwritten", overwritten.get()));
        runStageTwo(request);
    }

    private void runStageTwo(GenerationService.Request request) {
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
        spawnTemplateEntities(request);
        scheduleSidecarSpawns(request);
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

    private void spawnTemplateEntities(GenerationService.Request request) {
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
                    (int) Math.floor(worldZ), () -> spawnTemplateEntity(location, entity, policy));
        }
    }

    private void spawnTemplateEntity(Location location, TemplateEntity entity, AppConfig.EntityPolicy policy) {
        if (location.getWorld() == null) {
            return;
        }
        try {
            Entity spawned = location.getWorld().spawnEntity(location, resolveEntityType(entity.type()));
            if (policy == AppConfig.EntityPolicy.PASSIVE && spawned instanceof LivingEntity living
                    && VanillaSpawnBridge.isHostile(living)) {
                living.remove();
            }
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

    private void scheduleSidecarSpawns(GenerationService.Request request) {
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
            spawnExecutor.schedule(context, entry, index, template.id());
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

package emaki.jiuwu.craft.structure.generate;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.rollback.GenerationJournal;
import emaki.jiuwu.craft.structure.rollback.RollbackService;
import emaki.jiuwu.craft.structure.template.StructureTemplate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class GenerationService {

    private static final int BATCH_SIZE = 512;

    public record Request(World world,
                          StructureTemplate template,
                          int anchorX,
                          int anchorY,
                          int anchorZ,
                          StructureFacing facing,
                          CommandSender initiator) {

        public String initiatorName() {
            return initiator.getName();
        }
    }

    public record Rejection(String reasonKey, Map<String, Object> placeholders) {
    }

    private final EmakiStructurePlugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final GenerationScheduler scheduler;
    private final GenerationFinalizer finalizer;
    private final RollbackService rollbackService;
    private final Supplier<AppConfig> config;

    public GenerationService(EmakiStructurePlugin plugin,
            ExecutionDispatcher dispatcher,
            GenerationScheduler scheduler,
            GenerationFinalizer finalizer,
            RollbackService rollbackService,
            Supplier<AppConfig> config) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.scheduler = scheduler;
        this.finalizer = finalizer;
        this.rollbackService = rollbackService;
        this.config = config;
    }

    public Optional<Rejection> start(Request request) {
        if (rollbackService.recovering()) {
            return Optional.of(new Rejection("error.generate.recovering", Map.of()));
        }
        AppConfig.GenerateSettings settings = config.get().generate();
        StructureTemplate template = request.template();
        World world = request.world();
        int newSizeX = TemplateTransform.transformedSizeX(template.sizeX(), template.sizeZ(), request.facing());
        int newSizeZ = TemplateTransform.transformedSizeZ(template.sizeX(), template.sizeZ(), request.facing());
        if (newSizeX > settings.sizeLimitX() || template.sizeY() > settings.sizeLimitY()
                || newSizeZ > settings.sizeLimitZ()) {
            return Optional.of(new Rejection("error.generate.rejected_by_limit",
                    Map.of("template", template.id())));
        }
        if (!WorldBounds.contains(world, request.anchorX(), request.anchorY(), request.anchorZ())
                || !WorldBounds.contains(world,
                        request.anchorX() + newSizeX - 1,
                        request.anchorY() + template.sizeY() - 1,
                        request.anchorZ() + newSizeZ - 1)) {
            return Optional.of(new Rejection("error.generate.out_of_bounds",
                    Map.of("detail", WorldBounds.describe(world, request.anchorX(),
                            request.anchorY(), request.anchorZ()))));
        }
        List<RegionBatch.Write> writes = planWrites(template, request);
        if (writes.isEmpty()) {
            return Optional.of(new Rejection("error.export.no_blocks", Map.of("count", 0)));
        }
        Map<Long, List<RegionBatch.Write>> regions = groupByRegion(writes);
        if (settings.groundMode() == AppConfig.GroundMode.KEEP) {
            submit(request, writes, regions);
            return Optional.empty();
        }
        verifyGround(request, settings, regions, writes);
        return Optional.empty();
    }

    private List<RegionBatch.Write> planWrites(StructureTemplate template, Request request) {
        List<RegionBatch.Write> writes = new ArrayList<>();
        StructureFacing facing = request.facing();
        for (int y = 0; y < template.sizeY(); y++) {
            for (int z = 0; z < template.sizeZ(); z++) {
                for (int x = 0; x < template.sizeX(); x++) {
                    BlockData local = template.blockAt(x, y, z);
                    if (local == null) {
                        continue;
                    }
                    Vector offset = TemplateTransform.localToWorld(x, y, z,
                            template.sizeX(), template.sizeZ(), facing);
                    writes.add(new RegionBatch.Write(
                            request.anchorX() + offset.getBlockX(),
                            request.anchorY() + offset.getBlockY(),
                            request.anchorZ() + offset.getBlockZ(),
                            TemplateTransform.apply(local, facing)));
                }
            }
        }
        return writes;
    }

    private record ColumnProbe(int worldX, int worldZ, int surfaceY) {
    }

    private static Map<Long, List<RegionBatch.Write>> groupByRegion(List<RegionBatch.Write> writes) {
        Map<Long, List<RegionBatch.Write>> regions = new LinkedHashMap<>();
        for (RegionBatch.Write write : writes) {
            long key = regionKey(write.x(), write.z());
            regions.computeIfAbsent(key, ignored -> new ArrayList<>()).add(write);
        }
        return regions;
    }

    private static long regionKey(int x, int z) {
        return ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
    }

    private static List<ColumnProbe> collectProbes(List<RegionBatch.Write> writes) {
        Map<Long, ColumnProbe> perColumn = new LinkedHashMap<>();
        for (RegionBatch.Write write : writes) {
            if (write.data().getMaterial().isAir()) {
                continue;
            }
            long key = ((long) write.x() << 32) | (write.z() & 0xFFFFFFFFL);
            ColumnProbe existing = perColumn.get(key);
            if (existing == null || write.y() < existing.surfaceY()) {
                perColumn.put(key, new ColumnProbe(write.x(), write.z(), write.y()));
            }
        }
        return new ArrayList<>(perColumn.values());
    }

    private static Map<Long, List<ColumnProbe>> probesByRegion(List<RegionBatch.Write> writes) {
        Map<Long, List<ColumnProbe>> grouped = new LinkedHashMap<>();
        for (ColumnProbe probe : collectProbes(writes)) {
            long key = regionKey(probe.worldX(), probe.worldZ());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(probe);
        }
        return grouped;
    }

    private void verifyGround(Request request, AppConfig.GenerateSettings settings,
            Map<Long, List<RegionBatch.Write>> regions, List<RegionBatch.Write> writes) {
        Map<Long, List<ColumnProbe>> probes = probesByRegion(writes);
        List<CompletableFuture<Boolean>> checks = new ArrayList<>();
        for (List<ColumnProbe> regionProbes : probes.values()) {
            ColumnProbe head = regionProbes.get(0);
            CompletableFuture<Boolean> check = new CompletableFuture<>();
            Location probeLocation = new Location(request.world(), head.worldX(), head.surfaceY(), head.worldZ());
            if (!dispatchAtLocation(probeLocation, () -> {
                GroundGuard.Verdict verdict = GroundGuard.Verdict.SUPPORTED;
                for (ColumnProbe probe : regionProbes) {
                    verdict = GroundGuard.ensure(request.world(), probe.worldX(),
                            probe.surfaceY(), probe.worldZ(), settings.groundMode());
                    if (verdict != GroundGuard.Verdict.SUPPORTED) {
                        break;
                    }
                }
                check.complete(verdict == GroundGuard.Verdict.SUPPORTED);
            })) {
                check.complete(Boolean.FALSE);
            }
            checks.add(check);
        }
        CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) ->
                        settleGround(request, regions, writes, failure, checks, probes.size()));
    }

    private void settleGround(Request request, Map<Long, List<RegionBatch.Write>> regions,
            List<RegionBatch.Write> writes, Throwable failure, List<CompletableFuture<Boolean>> checks,
            int probedRegions) {
        boolean supported = failure == null && checks.stream()
                .allMatch(future -> Boolean.TRUE.equals(future.getNow(Boolean.FALSE)));
        try {
            dispatcher.runGlobal(plugin, () -> {
                if (supported) {
                    submit(request, writes, regions);
                    return;
                }
                UUID groundCheckId = request.initiator() instanceof Player player
                        ? player.getUniqueId() : null;
                plugin.debugLogger().log("structure", groundCheckId, "ground_skipped", Map.of(
                        "columns", probedRegions,
                        "template", request.template().id(),
                        "initiator", request.initiatorName()));
                finalizer.reportRejection(request, new Rejection(
                        "error.generate.ground_unsupported", Map.of()));
            });
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("生成预检回调提交失败: " + exception.getMessage());
        }
    }

    private boolean dispatchAtLocation(Location location, Runnable task) {
        try {
            var token = dispatcher.runAtLocation(plugin, location, task);
            return token != null && token != TaskToken.UNAVAILABLE;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("生成预检任务提交失败: " + exception.getMessage());
            return false;
        }
    }

    private void submit(Request request, List<RegionBatch.Write> writes, Map<Long, List<RegionBatch.Write>> regions) {
        GenerationJournal journal;
        try {
            journal = rollbackService.open(UUID.randomUUID().toString());
        } catch (IOException exception) {
            plugin.getLogger().warning("journal 创建失败: " + exception.getMessage());
            finalizer.reportRejection(request, new Rejection("error.generate.journal_failed",
                    Map.of("error", String.valueOf(exception.getMessage()))));
            return;
        }
        long startedNanos = System.nanoTime();
        AtomicLong overwritten = new AtomicLong();
        List<ChunkBatch> batches = new ArrayList<>();
        int sliceSize = Math.max(1, Math.min(BATCH_SIZE,
                config.get().generate().maxBlocksPerTick() / 4));
        for (List<RegionBatch.Write> regionWrites : regions.values()) {
            for (int start = 0; start < regionWrites.size(); start += sliceSize) {
                List<RegionBatch.Write> slice = regionWrites.subList(start,
                        Math.min(start + sliceSize, regionWrites.size()));
                RegionBatch.Write head = slice.get(0);
                Location location = new Location(request.world(), head.x(), head.y(), head.z());
                batches.add(new RegionBatch(request.world(), location, List.copyOf(slice),
                        overwritten, journal));
            }
        }
        GenerationJob job = new GenerationJob(request.template().id(), batches.size(),
                settled -> finalizer.onSettled(request, settled, overwritten, startedNanos));
        job.attachJournal(journal);
        scheduler.submit(job, batches);
        UUID initiatorId = request.initiator() instanceof Player player
                ? player.getUniqueId() : null;
        plugin.debugLogger().log("structure", initiatorId, "generate_start", Map.of(
                "template", request.template().id(),
                "facing", request.facing().configValue(),
                "x", request.anchorX(),
                "y", request.anchorY(),
                "z", request.anchorZ(),
                "blocks", writes.size(),
                "initiator", request.initiatorName()));
    }
}

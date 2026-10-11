package emaki.jiuwu.craft.structure.multiblock;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.generate.StructureFacing;
import emaki.jiuwu.craft.structure.generate.TemplateTransform;
import emaki.jiuwu.craft.structure.integration.CustomBlockIdAdapter;
import emaki.jiuwu.craft.structure.integration.FurnitureIdAdapter;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class MultiblockService {

    private final EmakiStructurePlugin plugin;
    private final MultiblockRepository repository;
    private final InstanceRegistry registry;
    private final MatchEngine engine;
    private final TriggerCooldown cooldown;
    private final Supplier<AppConfig> config;

    private TaskToken sweepToken;

    public MultiblockService(EmakiStructurePlugin plugin,
            MultiblockRepository repository,
            InstanceRegistry registry,
            CustomBlockIdAdapter customBlocks,
            FurnitureIdAdapter furniture,
            TriggerCooldown cooldown,
            Supplier<AppConfig> config) {
        this.plugin = plugin;
        this.repository = repository;
        this.registry = registry;
        this.engine = new MatchEngine(customBlocks, furniture);
        this.cooldown = cooldown;
        this.config = config;
    }

    public InstanceRegistry registry() {
        return registry;
    }

    public MatchEngine engine() {
        return engine;
    }

    public MultiblockRepository repository() {
        return repository;
    }

    public TriggerCooldown cooldown() {
        return cooldown;
    }

    public void onMemberChanged(World world, int blockX, int blockY, int blockZ) {
        if (world == null) {
            return;
        }
        String worldName = world.getName();
        long chunkKey = InstanceRegistry.chunkKeyAt(blockX, blockZ);
        registry.scheduleDebounced(chunkKey,
                () -> revalidateChunk(world, worldName, chunkKey));
    }

    private void revalidateChunk(World world, String worldName, long chunkKey) {
        for (MultiblockInstance instance : registry.allAt(worldName, chunkKey)) {
            try {
                if (instance.formed()) {
                    if (engine.matches(instance.pose().definition(), world,
                            instance.pose().anchorX(), instance.pose().anchorY(),
                            instance.pose().anchorZ(), instance.pose().facing())) {
                        instance.touch();
                    } else {
                        registry.degrade(instance);
                    }
                } else {
                    Optional<StructurePose> refreshed = engine.match(
                            instance.pose().definition(), world,
                            instance.pose().anchorX(), instance.pose().anchorY(),
                            instance.pose().anchorZ());
                    if (refreshed.isPresent()) {
                        registry.markFormed(instance);
                    } else {
                        instance.touch();
                    }
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("多方块实例校验失败 " + instance + ": "
                        + exception.getMessage());
                registry.remove(instance);
            }
        }
    }

    public void onBlockPlaced(World world, int blockX, int blockY, int blockZ, UUID placer) {
        if (world == null) {
            return;
        }
        String worldName = world.getName();
        for (MultiblockDefinition definition : repository.all()) {
            for (StructurePose anchor : engine.anchorCandidates(definition, world,
                    blockX, blockY, blockZ, candidateLimit())) {
                if (candidateExists(worldName, definition, anchor)) {
                    continue;
                }
                Optional<StructurePose> matched = engine.match(definition, world,
                        anchor.anchorX(), anchor.anchorY(), anchor.anchorZ());
                MultiblockInstance instance = registry.registerCandidate(worldName,
                        matched.orElse(anchor), placer);
                if (matched.isPresent()) {
                    registry.markFormed(instance);
                }
            }
        }
        long chunkKey = InstanceRegistry.chunkKeyAt(blockX, blockZ);
        registry.scheduleDebounced(chunkKey,
                () -> revalidateChunk(world, worldName, chunkKey));
    }

    private boolean candidateExists(String worldName, MultiblockDefinition definition,
            StructurePose pose) {
        for (MultiblockInstance existing : registry.allAt(worldName, pose.chunkKey())) {
            if (existing.pose().definitionId().equals(definition.id())
                    && existing.pose().anchorX() == pose.anchorX()
                    && existing.pose().anchorY() == pose.anchorY()
                    && existing.pose().anchorZ() == pose.anchorZ()
                    && existing.pose().facing() == pose.facing()) {
                return true;
            }
        }
        return false;
    }

    public boolean onInteract(Player player, Block clicked) {
        if (player == null || clicked == null) {
            return false;
        }
        String worldName = clicked.getWorld().getName();
        long chunkKey = InstanceRegistry.chunkKeyAt(clicked.getX(), clicked.getZ());
        for (MultiblockInstance instance : registry.formedAt(worldName, chunkKey)) {
            if (tryTrigger(player, instance, clicked)) {
                return true;
            }
        }
        return coldHit(player, clicked);
    }

    public boolean onFurnitureInteract(Player player, Block anchor, String furnitureId) {
        if (player == null || anchor == null || furnitureId == null) {
            return false;
        }
        String worldName = anchor.getWorld().getName();
        long chunkKey = InstanceRegistry.chunkKeyAt(anchor.getX(), anchor.getZ());
        for (MultiblockInstance instance : registry.formedAt(worldName, chunkKey)) {
            boolean declared = instance.pose().definition().members().stream()
                    .anyMatch(member -> member.requiresFurniture()
                            && member.spec().equalsIgnoreCase(furnitureId));
            if (!declared) {
                continue;
            }
            if (tryTrigger(player, instance, anchor)) {
                return true;
            }
        }
        return coldHit(player, anchor);
    }

    private boolean coldHit(Player player, Block clicked) {
        int budget = config.get().detection().maxColdChecks();
        int startBudget = budget;
        for (MultiblockDefinition definition : repository.all()) {
            if (budget <= 0) {
                break;
            }
            for (StructurePose anchor : engine.anchorCandidates(definition, clicked.getWorld(),
                    clicked.getX(), clicked.getY(), clicked.getZ(), budget)) {
                budget--;
                Optional<StructurePose> matched = engine.match(definition, clicked.getWorld(),
                        anchor.anchorX(), anchor.anchorY(), anchor.anchorZ());
                if (matched.isEmpty()) {
                    continue;
                }
                MultiblockInstance instance = adopt(matched.get(), player.getUniqueId());
                if (tryTrigger(player, instance, clicked)) {
                    return true;
                }
            }
        }
        if (budget < startBudget) {
            plugin.debugLogger().log("structure", player, "cold_hit_checked",
                    Map.of("used", startBudget - budget, "limit", startBudget));
        }
        return false;
    }

    private MultiblockInstance adopt(StructurePose pose, UUID owner) {
        for (MultiblockInstance existing : registry.allAt(pose.worldName(), pose.chunkKey())) {
            if (existing.pose().equals(pose)) {
                if (!existing.formed()) {
                    registry.markFormed(existing);
                }
                return existing;
            }
        }
        MultiblockInstance created = registry.registerCandidate(pose.worldName(), pose, owner);
        registry.markFormed(created);
        return created;
    }

    private boolean tryTrigger(Player player, MultiblockInstance instance, Block clicked) {
        try {
            MultiblockDefinition definition = instance.pose().definition();
            if (!acceptsClick(definition, instance.pose(), clicked)) {
                return false;
            }
            Map<String, Object> placeholders = buildPlaceholders(definition, player, instance.pose());
            if (!conditionAllows(definition, player, placeholders)) {
                return false;
            }
            if (!cooldown.tryAcquire(definition.id(), player.getUniqueId(),
                    resolveGlobalCooldown(definition), resolvePlayerCooldown(definition))) {
                return false;
            }
            if (!verifyInstance(instance)) {
                registry.degrade(instance);
                plugin.messageService().send(player, "error.multiblock.verification_failed",
                        Map.of("id", definition.id()));
                return false;
            }
            executeActions(definition, player, placeholders);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("多方块触发失败 " + instance + ": "
                    + exception.getMessage());
            return false;
        }
    }

    private boolean conditionAllows(MultiblockDefinition definition, Player player,
            Map<String, Object> placeholders) {
        String condition = definition.condition().orElse(null);
        if (condition == null || condition.isBlank()) {
            return true;
        }
        return ConditionEvaluator.evaluate(
                ConditionGroup.of(List.of(condition), "all_of", 0),
                line -> line,
                true,
                ConditionContext.of(player, null, placeholders));
    }

    private Map<String, Object> buildPlaceholders(MultiblockDefinition definition, Player player,
            StructurePose pose) {
        Map<String, Object> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("structure", definition.id());
        placeholders.put("x", pose.anchorX());
        placeholders.put("y", pose.anchorY());
        placeholders.put("z", pose.anchorZ());
        placeholders.put("world", pose.worldName());
        return placeholders;
    }

    private boolean acceptsClick(MultiblockDefinition definition, StructurePose pose, Block clicked) {
        if (definition.hasCoreBlock()) {
            int[] core = definition.coreBlock();
            if (pose.anchorX() + core[0] == clicked.getX()
                    && pose.anchorY() + core[1] == clicked.getY()
                    && pose.anchorZ() + core[2] == clicked.getZ()) {
                return true;
            }
        }
        for (MultiblockDefinition.Member member : definition.members()) {
            Vector offset = TemplateTransform.localToWorld(member.x(), member.y(), member.z(),
                    definition.sizeX(), definition.sizeZ(), pose.facing());
            if (pose.anchorX() + offset.getBlockX() == clicked.getX()
                    && pose.anchorY() + offset.getBlockY() == clicked.getY()
                    && pose.anchorZ() + offset.getBlockZ() == clicked.getZ()) {
                return definition.trigger().accepts(member.x(), member.y(), member.z());
            }
        }
        return false;
    }

    private boolean verifyInstance(MultiblockInstance instance) {
        if (!instance.formed()) {
            return false;
        }
        World world = plugin.getServer().getWorld(instance.pose().worldName());
        if (world == null) {
            return false;
        }
        return engine.matches(instance.pose().definition(), world,
                instance.pose().anchorX(), instance.pose().anchorY(),
                instance.pose().anchorZ(), instance.pose().facing());
    }

    private void executeActions(MultiblockDefinition definition, Player player,
            Map<String, Object> placeholders) {
        List<String> actions = definition.trigger().actions();
        if (actions.isEmpty()) {
            return;
        }
        try {
            var runner = EmakiCoreLibPlugin.lookup().actionLineRunner(plugin);
            var context = runner.context(player, "structure", false, placeholders);
            runner.run(actions, context, false);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("多方块动作执行失败 " + definition.id() + ": "
                    + exception.getMessage());
        }
    }

    public void sweep() {
        registry.sweepStale();
        if (config.get().detection().sampleRecheck()) {
            sampleRecheck();
        }
    }

    private void sampleRecheck() {
        for (MultiblockInstance instance : registry.allFormed()) {
            World world = plugin.getServer().getWorld(instance.pose().worldName());
            if (world == null) {
                continue;
            }
            try {
                if (!engine.matches(instance.pose().definition(), world,
                        instance.pose().anchorX(), instance.pose().anchorY(),
                        instance.pose().anchorZ(), instance.pose().facing())) {
                    registry.degrade(instance);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("抽样复验失败 " + instance + ": "
                        + exception.getMessage());
                registry.remove(instance);
            }
        }
    }

    public List<MultiblockDefinition> definitions() {
        return repository.all();
    }

    private Duration resolveGlobalCooldown(MultiblockDefinition definition) {
        return definition.cooldown()
                .map(MultiblockDefinition.CooldownSpec::global)
                .orElseGet(() -> config.get().cooldown().global());
    }

    private Duration resolvePlayerCooldown(MultiblockDefinition definition) {
        return definition.cooldown()
                .map(MultiblockDefinition.CooldownSpec::perPlayer)
                .orElseGet(() -> config.get().cooldown().perPlayer());
    }

    private int candidateLimit() {
        return Math.max(1, config.get().detection().maxColdChecks());
    }

    public void clearAll() {
        stopSweep();
        registry.clearAll();
        cooldown.clear();
    }

    public void startSweep() {
        Duration interval = config.get().detection().sweepInterval();
        if (interval.isZero()) {
            return;
        }
        long periodTicks = Math.max(20L, interval.toMillis() / 50L);
        sweepToken = plugin.executionDispatcher().runGlobalTimer(plugin, this::sweep,
                periodTicks, periodTicks);
    }

    public void stopSweep() {
        if (sweepToken != null) {
            sweepToken.cancel();
            sweepToken = null;
        }
    }

    public void describeFor(List<String> lines) {
        lines.add("definitions=" + repository.ids().size()
                + " candidates=" + registry.candidateInstanceCount()
                + " formed=" + registry.formedInstanceCount()
                + " cooldowns=" + cooldown.trackedEntries());
    }
}

package emaki.jiuwu.craft.structure.generate;

import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.config.AppConfig;
import emaki.jiuwu.craft.structure.template.StructureTemplate;
import emaki.jiuwu.craft.structure.template.TemplateRepository;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class StructureReplaceListener implements Listener {

    private static final String NAMESPACE_MINECRAFT = "minecraft:";

    private final EmakiStructurePlugin plugin;
    private final GenerationService generationService;
    private final TemplateRepository templates;
    private final ReplaceMarkerStore markers;
    private final Supplier<AppConfig> config;
    private final Set<String> warnedMissingTemplates = ConcurrentHashMap.newKeySet();

    public StructureReplaceListener(EmakiStructurePlugin plugin,
            GenerationService generationService,
            TemplateRepository templates,
            ReplaceMarkerStore markers,
            Supplier<AppConfig> config) {
        this.plugin = plugin;
        this.generationService = generationService;
        this.templates = templates;
        this.markers = markers;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        List<AppConfig.ReplaceRule> rules = config.get().replaceRules();
        if (rules.isEmpty()) {
            return;
        }
        Collection<GeneratedStructure> structures;
        try {
            structures = event.getChunk().getStructures();
        } catch (RuntimeException exception) {
            return;
        }
        if (structures.isEmpty()) {
            return;
        }
        World world = event.getChunk().getWorld();
        for (GeneratedStructure generated : structures) {
            matchAndReplace(world, generated, rules);
        }
    }

    private void matchAndReplace(World world, GeneratedStructure generated,
            List<AppConfig.ReplaceRule> rules) {
        String structureId;
        try {
            structureId = generated.getStructure().getKey().toString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException exception) {
            return;
        }
        if (!structureId.startsWith(NAMESPACE_MINECRAFT)) {
            return;
        }
        BoundingBox box = generated.getBoundingBox();
        int startX = (int) Math.floor(box.getMinX());
        int startY = (int) Math.floor(box.getMinY());
        int startZ = (int) Math.floor(box.getMinZ());
        for (AppConfig.ReplaceRule rule : rules) {
            if (!rule.enabled() || !rule.structure().equals(structureId)) {
                continue;
            }
            if (!rule.matchesWorld(world.getName())) {
                continue;
            }
            if (!matchesBiome(world, rule, startX, startY, startZ)) {
                continue;
            }
            if (rule.chance() < 1.0d
                    && ThreadLocalRandom.current().nextDouble() >= rule.chance()) {
                continue;
            }
            replace(world, structureId, startX, startY, startZ, rule);
            return;
        }
    }

    private static boolean matchesBiome(World world, AppConfig.ReplaceRule rule,
            int x, int y, int z) {
        if (rule.biomes().isEmpty()) {
            return true;
        }
        try {
            String key = world.getBiome(x, y, z).getKey().toString().toLowerCase(Locale.ROOT);
            String path = key.substring(key.indexOf(':') + 1);
            return rule.biomes().contains(key) || rule.biomes().contains(path);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void replace(World world, String structureId, int startX, int startY, int startZ,
            AppConfig.ReplaceRule rule) {
        Optional<StructureTemplate> resolved = templates.template(rule.template());
        if (resolved.isEmpty()) {
            if (warnedMissingTemplates.add(rule.template())) {
                plugin.messageService().warning("console.replace_template_missing", Map.of(
                        "template", rule.template(),
                        "structure", structureId));
            }
            return;
        }
        String markerKey = structureId + "|" + startX + "|" + startY + "|" + startZ;
        if (!markers.firstProcess(world.getName(), markerKey)) {
            return;
        }
        GenerationService.Request request = new GenerationService.Request(world, resolved.get(),
                startX, startY, startZ, StructureFacing.SOUTH, Bukkit.getConsoleSender());
        Optional<GenerationService.Rejection> rejection = generationService.start(request);
        if (rejection.isPresent()) {
            plugin.debugLogger().log("structure", (UUID) null, "replace_rejected", Map.of(
                    "structure", structureId,
                    "template", rule.template(),
                    "key", rejection.get().reasonKey()));
            return;
        }
        plugin.debugLogger().log("structure", (UUID) null, "replace_started", Map.of(
                "structure", structureId,
                "template", rule.template(),
                "x", startX,
                "y", startY,
                "z", startZ));
    }
}

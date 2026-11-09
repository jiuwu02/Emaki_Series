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

final class StructureCommandRouterSupport {

    static Map<Long, SelectionBox> splitRegions(SelectionBox box) {
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

    static String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
}

    static String describe(SelectionBox box, boolean first) {
        return first
                ? box.minX() + ", " + box.minY() + ", " + box.minZ()
                : box.maxX() + ", " + box.maxY() + ", " + box.maxZ();
}

    static long parsePositiveInt(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            return -1L;
        }
}
}

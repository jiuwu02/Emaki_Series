package emaki.jiuwu.craft.structure.multiblock;

import emaki.jiuwu.craft.structure.generate.StructureFacing;
import emaki.jiuwu.craft.structure.generate.TemplateTransform;
import emaki.jiuwu.craft.structure.integration.CustomBlockIdAdapter;
import emaki.jiuwu.craft.structure.integration.FurnitureIdAdapter;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MatchEngine {

    private static final StructureFacing[] ROTATIONS = {
            StructureFacing.SOUTH, StructureFacing.WEST,
            StructureFacing.NORTH, StructureFacing.EAST
    };

    private final CustomBlockIdAdapter customBlocks;
    private final FurnitureIdAdapter furniture;

    public MatchEngine(CustomBlockIdAdapter customBlocks, FurnitureIdAdapter furniture) {
        this.customBlocks = customBlocks;
        this.furniture = furniture;
    }

    public CustomBlockIdAdapter customBlocks() {
        return customBlocks;
    }

    public FurnitureIdAdapter furniture() {
        return furniture;
    }

    public Optional<StructurePose> match(MultiblockDefinition definition, World world,
            int anchorX, int anchorY, int anchorZ) {
        if (definition == null || world == null) {
            return Optional.empty();
        }
        for (StructureFacing facing : rotationsFor(definition)) {
            if (matches(definition, world, anchorX, anchorY, anchorZ, facing)) {
                return Optional.of(new StructurePose(world.getName(), definition,
                        anchorX, anchorY, anchorZ, facing));
            }
        }
        return Optional.empty();
    }

    public boolean matches(MultiblockDefinition definition, World world,
            int anchorX, int anchorY, int anchorZ, StructureFacing facing) {
        for (MultiblockDefinition.Member member : definition.members()) {
            if (!matchesMember(definition, world, anchorX, anchorY, anchorZ, facing, member)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesMember(MultiblockDefinition definition, World world,
            int anchorX, int anchorY, int anchorZ, StructureFacing facing,
            MultiblockDefinition.Member member) {
        Vector offset = TemplateTransform.localToWorld(member.x(), member.y(), member.z(),
                definition.sizeX(), definition.sizeZ(), facing);
        int worldX = anchorX + offset.getBlockX();
        int worldY = anchorY + offset.getBlockY();
        int worldZ = anchorZ + offset.getBlockZ();
        if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) {
            return false;
        }
        Block block = world.getBlockAt(worldX, worldY, worldZ);
        return switch (member.kind()) {
            case VANILLA_STATE -> matchesVanillaState(member, block, facing);
            case VANILLA_MATERIAL -> member.matchesVanillaMaterial(block.getType());
            case CUSTOM_BLOCK -> customBlocks != null && customBlocks.matches(block,
                    customBlocks.bind(member.namespaceId()));
            case FURNITURE -> furniture != null && furniture.matchesAt(block, member.spec());
        };
    }

    private boolean matchesVanillaState(MultiblockDefinition.Member member, Block block,
            StructureFacing facing) {
        BlockData rotated = TemplateTransform.apply(member.blockData(), facing);
        return rotated.getAsString().equals(block.getBlockData().getAsString());
    }

    public List<StructurePose> anchorCandidates(MultiblockDefinition definition, World world,
            int clickX, int clickY, int clickZ, int maxCandidates) {
        Set<StructurePose> candidates = new LinkedHashSet<>();
        if (definition == null || world == null || maxCandidates <= 0) {
            return List.of();
        }
        String worldName = world.getName();
        for (StructureFacing facing : rotationsFor(definition)) {
            for (MultiblockDefinition.Member member : definition.members()) {
                if (candidates.size() >= maxCandidates) {
                    return new ArrayList<>(candidates);
                }
                if (member.requiresFurniture() && !furnitureTriggerable(member)) {
                    continue;
                }
                Vector offset = TemplateTransform.localToWorld(member.x(), member.y(), member.z(),
                        definition.sizeX(), definition.sizeZ(), facing);
                int anchorX = clickX - offset.getBlockX();
                int anchorY = clickY - offset.getBlockY();
                int anchorZ = clickZ - offset.getBlockZ();
                candidates.add(new StructurePose(worldName, definition,
                        anchorX, anchorY, anchorZ, facing));
            }
        }
        return new ArrayList<>(candidates);
    }

    private boolean furnitureTriggerable(MultiblockDefinition.Member member) {
        return furniture != null && furniture.anySourcePresent()
                && FurnitureIdAdapter.knownNamespace(FurnitureIdAdapter.namespaceOf(member.spec()));
    }

    private static StructureFacing[] rotationsFor(MultiblockDefinition definition) {
        return definition.allowRotation() ? ROTATIONS : new StructureFacing[] { StructureFacing.SOUTH };
    }
}

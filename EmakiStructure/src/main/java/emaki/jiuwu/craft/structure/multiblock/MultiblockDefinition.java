package emaki.jiuwu.craft.structure.multiblock;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MultiblockDefinition {

    public enum MemberKind {
        VANILLA_STATE, VANILLA_MATERIAL, CUSTOM_BLOCK, FURNITURE
    }

    public enum TargetMode {
        ANY, SPECIFIC, ALL
    }

    public record Member(int x, int y, int z, String spec, MemberKind kind,
                         BlockData blockData, Material material, String namespaceId) {

        public Member {
            if (spec == null) {
                throw new NullPointerException("spec");
            }
            if (kind == null) {
                throw new NullPointerException("kind");
            }
        }

        public boolean matchesVanillaState(String worldState) {
            return kind == MemberKind.VANILLA_STATE && blockData != null
                    && worldState.equals(blockData.getAsString());
        }

        public boolean matchesVanillaMaterial(Material worldMaterial) {
            return kind == MemberKind.VANILLA_MATERIAL && material != null
                    && worldMaterial == material;
        }

        public boolean requiresCustomBlock() {
            return kind == MemberKind.CUSTOM_BLOCK;
        }

        public boolean requiresFurniture() {
            return kind == MemberKind.FURNITURE;
        }
    }

    public record Trigger(TargetMode targets, Set<int[]> positions, List<String> actions) {

        public Trigger {
            positions = positions == null ? Set.of() : Set.copyOf(positions);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }

        public boolean accepts(int localX, int localY, int localZ) {
            return switch (targets) {
                case ANY, ALL -> true;
                case SPECIFIC -> positions.stream().anyMatch(pos ->
                        pos[0] == localX && pos[1] == localY && pos[2] == localZ);
            };
        }
    }

    public record CooldownSpec(Duration global, Duration perPlayer) {
    }

    private final String id;
    private final boolean allowRotation;
    private final int[] coreBlock;
    private final List<Member> members;
    private final Trigger trigger;
    private final CooldownSpec cooldown;
    private final String condition;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    public MultiblockDefinition(String id, boolean allowRotation, int[] coreBlock,
            List<Member> members, Trigger trigger, CooldownSpec cooldown, String condition) {
        this.id = id;
        this.allowRotation = allowRotation;
        this.coreBlock = coreBlock == null ? null : coreBlock.clone();
        this.members = List.copyOf(members);
        this.trigger = trigger;
        this.cooldown = cooldown;
        this.condition = condition;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        for (Member member : this.members) {
            maxX = Math.max(maxX, member.x());
            maxY = Math.max(maxY, member.y());
            maxZ = Math.max(maxZ, member.z());
        }
        this.sizeX = maxX + 1;
        this.sizeY = maxY + 1;
        this.sizeZ = maxZ + 1;
    }

    public String id() {
        return id;
    }

    public boolean allowRotation() {
        return allowRotation;
    }

    public boolean hasCoreBlock() {
        return coreBlock != null;
    }

    public int[] coreBlock() {
        return coreBlock == null ? null : coreBlock.clone();
    }

    public boolean matchesCoreBlock(int localX, int localY, int localZ) {
        return coreBlock != null
                && coreBlock[0] == localX && coreBlock[1] == localY && coreBlock[2] == localZ;
    }

    public List<Member> members() {
        return members;
    }

    public Trigger trigger() {
        return trigger;
    }

    public Optional<CooldownSpec> cooldown() {
        return Optional.ofNullable(cooldown);
    }

    public Optional<String> condition() {
        return Optional.ofNullable(condition);
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public long chunkFootprint() {
        return (long) sizeX * sizeZ;
    }

    @Override
    public String toString() {
        return id + " members=" + members.size();
    }
}

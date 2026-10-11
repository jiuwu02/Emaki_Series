package emaki.jiuwu.craft.structure.multiblock;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.structure.config.Durations;
import emaki.jiuwu.craft.structure.config.NameRules;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MultiblockLoader {

    public static final int SUPPORTED_VERSION = 1;

    private static final List<String> FURNITURE_PREFIXES =
            List.of("itemsadder:", "oraxen:", "nexo:", "craftengine:");
    private static final List<String> TARGET_MODES = List.of("any", "specific", "all");

    public record Issue(int index, String reasonKey, Map<String, Object> placeholders) {
    }

    public record LoadResult(String fileName,
                             boolean fileRejected,
                             String rejectKey,
                             Map<String, Object> rejectPlaceholders,
                             MultiblockDefinition definition,
                             List<Issue> issues) {

        public static LoadResult rejected(String fileName, String reasonKey,
                Map<String, Object> placeholders) {
            return new LoadResult(fileName, true, reasonKey, placeholders, null, List.of());
        }
    }

    private record TriggerOutcome(MultiblockDefinition.Trigger trigger, LoadResult failure) {
    }

    private record MemberOutcome(MultiblockDefinition.Member member, Issue issue) {

        static MemberOutcome rejected(Issue issue) {
            return new MemberOutcome(null, issue);
        }
    }

    private MultiblockLoader() {
    }

    public static LoadResult load(YamlSection section, String fileName) {
        String baseName = stripExtension(fileName);
        if (section == null) {
            return LoadResult.rejected(fileName, "error.multiblock.parse_failed",
                    Map.of("file", fileName, "detail", "empty"));
        }
        if (!NameRules.isValid(baseName)) {
            return LoadResult.rejected(fileName, "error.name.invalid",
                    Map.of("value", fileName));
        }
        Integer version = section.getInt("version");
        if (version == null || version.intValue() != SUPPORTED_VERSION) {
            return LoadResult.rejected(fileName, "error.multiblock.version_unsupported",
                    Map.of("file", fileName, "value", String.valueOf(version),
                            "expected", String.valueOf(SUPPORTED_VERSION)));
        }
        String declaredId = section.getString("id");
        if (declaredId == null || !declaredId.equals(baseName)) {
            return LoadResult.rejected(fileName, "error.multiblock.id_mismatch",
                    Map.of("file", fileName, "value", String.valueOf(declaredId),
                            "expected", baseName));
        }
        YamlSection shape = section.getSection("shape");
        if (shape == null) {
            return LoadResult.rejected(fileName, "error.multiblock.shape_missing",
                    Map.of("file", fileName));
        }
        boolean allowRotation = shape.getBoolean("allow_rotation", true);
        int[] coreBlock = readTriple(shape.get("core_block"));
        if (shape.contains("core_block") && (coreBlock == null || !isNonNegative(coreBlock))) {
            return LoadResult.rejected(fileName, "error.multiblock.core_block_invalid",
                    Map.of("file", fileName, "value", String.valueOf(shape.get("core_block"))));
        }
        YamlSection triggerSection = section.getSection("trigger");
        if (triggerSection == null) {
            return LoadResult.rejected(fileName, "error.multiblock.trigger_missing",
                    Map.of("file", fileName));
        }
        TriggerOutcome triggerOutcome = parseTrigger(triggerSection, fileName);
        if (triggerOutcome.failure() != null) {
            return triggerOutcome.failure();
        }
        List<MultiblockDefinition.Member> members = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        Set<Long> seenPositions = new HashSet<>();
        List<Map<?, ?>> rawBlocks = shape.getMapList("blocks");
        for (int index = 0; index < rawBlocks.size(); index++) {
            MemberOutcome outcome = parseMember(rawBlocks.get(index), index, fileName, seenPositions);
            if (outcome.member() != null) {
                members.add(outcome.member());
            } else if (outcome.issue() != null) {
                issues.add(outcome.issue());
            }
        }
        if (members.isEmpty()) {
            return LoadResult.rejected(fileName, "error.multiblock.blocks_empty",
                    Map.of("file", fileName));
        }
        MultiblockDefinition definition = new MultiblockDefinition(baseName, allowRotation,
                coreBlock, members, triggerOutcome.trigger(), readCooldown(section),
                readCondition(section));
        return new LoadResult(fileName, false, null, Map.of(), definition, List.copyOf(issues));
    }

    private static TriggerOutcome parseTrigger(YamlSection triggerSection, String fileName) {
        String rawTargets = triggerSection.getString("targets", "any").trim().toLowerCase(Locale.ROOT);
        if (!TARGET_MODES.contains(rawTargets)) {
            return new TriggerOutcome(null, LoadResult.rejected(fileName,
                    "error.multiblock.targets_invalid",
                    Map.of("file", fileName, "value", rawTargets,
                            "allowed", String.join(", ", TARGET_MODES))));
        }
        MultiblockDefinition.TargetMode mode = switch (rawTargets) {
            case "specific" -> MultiblockDefinition.TargetMode.SPECIFIC;
            case "all" -> MultiblockDefinition.TargetMode.ALL;
            default -> MultiblockDefinition.TargetMode.ANY;
        };
        Set<int[]> positions = new HashSet<>();
        if (mode == MultiblockDefinition.TargetMode.SPECIFIC) {
            List<?> rawPositions = triggerSection.getList("positions");
            if (rawPositions == null || rawPositions.isEmpty()) {
                return new TriggerOutcome(null, LoadResult.rejected(fileName,
                        "error.multiblock.positions_missing", Map.of("file", fileName)));
            }
            for (Object entry : rawPositions) {
                int[] position = readTriple(entry);
                if (position == null || !isNonNegative(position)) {
                    return new TriggerOutcome(null, LoadResult.rejected(fileName,
                            "error.multiblock.positions_invalid",
                            Map.of("file", fileName, "value", String.valueOf(entry))));
                }
                positions.add(position);
            }
        }
        String actions = triggerSection.getString("actions", "");
        List<String> actionLines = splitActions(actions);
        return new TriggerOutcome(
                new MultiblockDefinition.Trigger(mode, positions, actionLines), null);
    }

    private static List<String> splitActions(String actions) {
        if (actions == null || actions.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String raw : actions.split("\n")) {
            String line = raw.strip();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return List.copyOf(lines);
    }

    private static MemberOutcome parseMember(Map<?, ?> raw, int index, String fileName,
            Set<Long> seenPositions) {
        int[] pos = readTriple(raw.get("pos"));
        if (pos == null || !isNonNegative(pos)) {
            return MemberOutcome.rejected(issue(index, "error.multiblock.pos_invalid",
                    Map.of("file", fileName, "value", String.valueOf(raw.get("pos")))));
        }
        long key = ((long) pos[0] << 40) | ((long) pos[1] << 20) | (pos[2] & 0xFFFFFL);
        if (!seenPositions.add(key)) {
            return MemberOutcome.rejected(issue(index, "error.multiblock.pos_duplicate",
                    Map.of("file", fileName, "value", formatTriple(pos))));
        }
        boolean hasBlock = raw.containsKey("block");
        boolean hasFurniture = raw.containsKey("furniture");
        if (hasBlock == hasFurniture) {
            return MemberOutcome.rejected(issue(index, "error.multiblock.member_exclusive",
                    Map.of("file", fileName, "value", formatTriple(pos))));
        }
        if (hasFurniture) {
            Object rawFurniture = raw.get("furniture");
            if (!(rawFurniture instanceof String furniture)
                    || !hasAllowedFurniturePrefix(furniture)) {
                return MemberOutcome.rejected(issue(index, "error.multiblock.furniture_prefix",
                        Map.of("file", fileName, "value", String.valueOf(rawFurniture),
                                "allowed", String.join(", ", FURNITURE_PREFIXES))));
            }
            return new MemberOutcome(new MultiblockDefinition.Member(pos[0], pos[1], pos[2],
                    furniture, MultiblockDefinition.MemberKind.FURNITURE, null, null, null), null);
        }
        Object rawBlock = raw.get("block");
        if (!(rawBlock instanceof String spec) || spec.isBlank()) {
            return MemberOutcome.rejected(issue(index, "error.multiblock.block_missing",
                    Map.of("file", fileName, "value", String.valueOf(rawBlock))));
        }
        String normalized = spec.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("minecraft:")) {
            if (normalized.contains("[")) {
                return MemberOutcome.rejected(issue(index,
                        "error.multiblock.custom_block_state",
                        Map.of("file", fileName, "value", spec)));
            }
            return new MemberOutcome(new MultiblockDefinition.Member(pos[0], pos[1], pos[2],
                    normalized, MultiblockDefinition.MemberKind.CUSTOM_BLOCK,
                    null, null, normalized), null);
        }
        BlockData blockData;
        try {
            blockData = Bukkit.createBlockData(spec.trim());
        } catch (IllegalArgumentException exception) {
            return MemberOutcome.rejected(issue(index, "error.multiblock.block_state_invalid",
                    Map.of("file", fileName, "value", spec,
                            "detail", String.valueOf(exception.getMessage()))));
        }
        if (normalized.contains("[")) {
            return new MemberOutcome(new MultiblockDefinition.Member(pos[0], pos[1], pos[2],
                    spec, MultiblockDefinition.MemberKind.VANILLA_STATE, blockData, null, null),
                    null);
        }
        Material material = blockData.getMaterial();
        return new MemberOutcome(new MultiblockDefinition.Member(pos[0], pos[1], pos[2],
                spec, MultiblockDefinition.MemberKind.VANILLA_MATERIAL, null, material, null), null);
    }

    private static MultiblockDefinition.CooldownSpec readCooldown(YamlSection section) {
        YamlSection cooldown = section.getSection("cooldown");
        if (cooldown == null) {
            return null;
        }
        Duration global = readCooldownValue(cooldown.get("global"));
        Duration perPlayer = readCooldownValue(cooldown.get("per_player"));
        if (global == null && perPlayer == null) {
            return null;
        }
        return new MultiblockDefinition.CooldownSpec(
                global == null ? Duration.ZERO : global,
                perPlayer == null ? Duration.ZERO : perPlayer);
    }

    private static Duration readCooldownValue(Object raw) {
        if (raw == null) {
            return null;
        }
        String text = raw instanceof Number number
                ? String.valueOf(number.longValue())
                : String.valueOf(raw);
        if (!Durations.isValid(text)) {
            return null;
        }
        Duration parsed = Durations.parse(text, Duration.ZERO);
        return parsed.isNegative() ? null : parsed;
    }

    private static String readCondition(YamlSection section) {
        String condition = section.getString("condition");
        return condition == null || condition.isBlank() ? null : condition.trim();
    }

    private static boolean hasAllowedFurniturePrefix(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String prefix : FURNITURE_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static int[] readTriple(Object raw) {
        if (!(raw instanceof List<?> list) || list.size() < 3) {
            return null;
        }
        if (!(list.get(0) instanceof Number first)
                || !(list.get(1) instanceof Number second)
                || !(list.get(2) instanceof Number third)) {
            return null;
        }
        return new int[] { first.intValue(), second.intValue(), third.intValue() };
    }

    private static boolean isNonNegative(int[] triple) {
        return triple[0] >= 0 && triple[1] >= 0 && triple[2] >= 0;
    }

    private static String formatTriple(int[] triple) {
        return "[" + triple[0] + ", " + triple[1] + ", " + triple[2] + "]";
    }

    private static Issue issue(int index, String reasonKey, Map<String, Object> placeholders) {
        return new Issue(index, reasonKey, placeholders);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

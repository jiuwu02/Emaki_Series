package emaki.jiuwu.craft.structure.template;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.structure.template.ImportLimits.LegacyPolicy;
import emaki.jiuwu.craft.structure.template.nbt.NbtFormatException;
import emaki.jiuwu.craft.structure.template.nbt.NbtFormatException.Issue;
import emaki.jiuwu.craft.structure.template.nbt.NbtIo;
import emaki.jiuwu.craft.structure.template.nbt.NbtReadResult;
import emaki.jiuwu.craft.structure.template.nbt.NbtValue;
import emaki.jiuwu.craft.structure.template.nbt.NbtWriter;

public final class StructureTemplateLoader {

    private final ImportLimits limits;

    public StructureTemplateLoader(ImportLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public EmakiResult<StructureTemplate> load(String templateId, byte[] fileBytes) {
        if (!TemplateRepository.isValidId(templateId)) {
            return EmakiResult.failure(FailureKind.INVALID_INPUT, "error.template.invalid_id",
                    Map.<String, Object>of("id", String.valueOf(templateId)));
        }
        if (fileBytes == null) {
            return EmakiResult.failure(FailureKind.INVALID_INPUT, "error.template.parse_failed",
                    Map.<String, Object>of("file", templateId, "detail", "missing file payload"));
        }
        if (fileBytes.length > limits.maxFileBytes()) {
            return EmakiResult.failure(FailureKind.REJECTED, "error.template.file_too_large",
                    Map.<String, Object>of("file", templateId, "limit", limits.maxFileBytes(),
                            "actual", (long) fileBytes.length));
        }
        try {
            NbtReadResult parsed = NbtIo.readGzip(fileBytes, limits.maxOutputBytes(), limits.maxDepth());
            return fromRoot(templateId, parsed.root());
        } catch (NbtFormatException exception) {
            return EmakiResult.failure(kindFor(exception.issue()), keyFor(exception.issue()),
                    Map.<String, Object>of("file", templateId, "detail", describe(exception),
                            "code", exception.issue().code()));
        } catch (RuntimeException exception) {
            return EmakiResult.failure(FailureKind.INTERNAL_ERROR, "error.template.parse_failed",
                    Map.<String, Object>of("file", templateId, "detail", describe(exception)));
        }
    }

    private EmakiResult<StructureTemplate> fromRoot(String templateId, NbtValue.CompoundTag root) {
        int[] size = vec3Int(root, "size");
        if (!sizeValid(size)) {
            return EmakiResult.failure(FailureKind.INVALID_INPUT, "error.template.size_invalid",
                    Map.<String, Object>of("file", templateId, "detail", describeVector(size)));
        }
        Optional<NbtValue.ListTag> blocksTag = root.getList("blocks");
        if (blocksTag.isEmpty() || blocksTag.get().isEmpty()) {
            return EmakiResult.failure(FailureKind.INVALID_INPUT, "error.template.blocks_empty",
                    Map.<String, Object>of("file", templateId));
        }
        List<NbtValue> blockEntries = blocksTag.get().values();
        long volume = (long) size[0] * size[1] * size[2];
        if (blockEntries.size() > limits.maxTotalBlocks() || blockEntries.size() > volume) {
            return EmakiResult.failure(FailureKind.REJECTED, "error.template.total_blocks_exceeded",
                    Map.<String, Object>of("file", templateId, "limit", limits.maxTotalBlocks(),
                            "actual", (long) blockEntries.size()));
        }
        List<NbtValue> paletteEntries = root.getList("palette").map(NbtValue.ListTag::values).orElse(List.of());
        if (paletteEntries.size() > limits.paletteLimit()) {
            return EmakiResult.failure(FailureKind.REJECTED, "error.template.palette_exceeded",
                    Map.<String, Object>of("file", templateId, "limit", limits.paletteLimit(),
                            "actual", paletteEntries.size()));
        }

        int[] paletteIndices = new int[(int) volume];
        Arrays.fill(paletteIndices, -1);
        BitSet seenPositions = new BitSet((int) volume);
        Map<Long, NbtValue.CompoundTag> blockEntities = new HashMap<>();
        for (NbtValue rawEntry : blockEntries) {
            if (!(rawEntry instanceof NbtValue.CompoundTag entry)) {
                return EmakiResult.failure(FailureKind.INVALID_INPUT, "error.template.parse_failed",
                        Map.<String, Object>of("file", templateId, "detail", "blocks entry is not a compound"));
            }
            OptionalInt state = intValue(entry, "state");
            if (state.isEmpty() || state.getAsInt() < 0 || state.getAsInt() >= paletteEntries.size()) {
                return EmakiResult.failure(FailureKind.INVALID_INPUT, "error.template.state_index_out_of_range",
                        Map.<String, Object>of("file", templateId,
                                "index", state.isPresent() ? state.getAsInt() : "missing",
                                "palette", paletteEntries.size()));
            }
            int[] pos = vec3Int(entry, "pos");
            if (!withinSize(pos, size)) {
                return EmakiResult.failure(FailureKind.INVALID_INPUT, "error.template.block_pos_out_of_range",
                        Map.<String, Object>of("file", templateId, "pos", describeVector(pos),
                                "size", describeVector(size)));
            }
            int flat = (pos[1] * size[2] + pos[2]) * size[0] + pos[0];
            if (seenPositions.get(flat)) {
                return EmakiResult.failure(FailureKind.INVALID_INPUT, "error.template.block_pos_duplicate",
                        Map.<String, Object>of("file", templateId, "pos", describeVector(pos)));
            }
            seenPositions.set(flat);
            paletteIndices[flat] = state.getAsInt();
            Optional<NbtValue.CompoundTag> inlineNbt = entry.getCompound("nbt");
            if (inlineNbt.isPresent()) {
                EmakiResult<Unit> stored = storeBlockEntity(templateId, blockEntities, inlineNbt.get(), pos);
                if (stored.isFailure()) {
                    return stored.retypeFailure();
                }
            }
        }

        for (NbtValue rawBlockEntity : root.getList("block_entities").map(NbtValue.ListTag::values).orElse(List.of())) {
            if (!(rawBlockEntity instanceof NbtValue.CompoundTag blockEntity)) {
                continue;
            }
            NbtValue.CompoundTag payload = blockEntity.getCompound("nbt").orElse(blockEntity);
            int[] pos = vec3Int(blockEntity, "pos");
            if (pos == null) {
                pos = vec3Int(payload, "pos");
            }
            if (!withinSize(pos, size)) {
                continue;
            }
            EmakiResult<Unit> stored = storeBlockEntity(templateId, blockEntities, payload, pos);
            if (stored.isFailure()) {
                return stored.retypeFailure();
            }
        }

        BlockData[] palette = new BlockData[paletteEntries.size()];
        int skippedPaletteEntries = 0;
        for (int index = 0; index < paletteEntries.size(); index++) {
            String blockString = paletteString(paletteEntries.get(index));
            BlockData blockData = parseBlockData(blockString);
            if (blockData == null) {
                if (limits.legacyPolicy() == LegacyPolicy.REJECT) {
                    return EmakiResult.failure(FailureKind.REJECTED, "error.template.legacy_block_rejected",
                            Map.<String, Object>of("file", templateId,
                                    "block", blockString == null ? "missing name" : blockString,
                                    "palette_index", index));
                }
                palette[index] = Material.AIR.createBlockData();
                skippedPaletteEntries++;
            } else {
                palette[index] = blockData;
            }
        }

        List<TemplateEntity> entities = readEntities(root);
        return EmakiResult.success(new StructureTemplate(templateId, size[0], size[1], size[2],
                paletteIndices, palette, blockEntities, entities, skippedPaletteEntries));
    }

    private EmakiResult<Unit> storeBlockEntity(
            String templateId,
            Map<Long, NbtValue.CompoundTag> target,
            NbtValue.CompoundTag payload,
            int[] pos) {
        long payloadBytes = NbtWriter.write(payload).length;
        if (payloadBytes > limits.blockEntityMaxBytes()) {
            return EmakiResult.failure(FailureKind.REJECTED, "error.template.block_entity_too_large",
                    Map.<String, Object>of("file", templateId, "pos", describeVector(pos),
                            "limit", limits.blockEntityMaxBytes(), "actual", payloadBytes));
        }
        target.put(StructureTemplate.packPosition(pos[0], pos[1], pos[2]), payload);
        return EmakiResult.ok();
    }

    private boolean sizeValid(int[] size) {
        return size != null
                && size[0] >= 1 && size[0] <= limits.maxSizeX()
                && size[1] >= 1 && size[1] <= limits.maxSizeY()
                && size[2] >= 1 && size[2] <= limits.maxSizeZ();
    }

    private static boolean withinSize(int[] vector, int[] size) {
        return vector != null
                && vector[0] >= 0 && vector[0] < size[0]
                && vector[1] >= 0 && vector[1] < size[1]
                && vector[2] >= 0 && vector[2] < size[2];
    }

    private static List<TemplateEntity> readEntities(NbtValue.CompoundTag root) {
        List<TemplateEntity> entities = new ArrayList<>();
        for (NbtValue rawEntity : root.getList("entities").map(NbtValue.ListTag::values).orElse(List.of())) {
            if (!(rawEntity instanceof NbtValue.CompoundTag entry)) {
                continue;
            }
            NbtValue.CompoundTag payload = entry.getCompound("nbt").orElse(entry);
            String type = payload.getString("id").orElse(entry.getString("id").orElse(null));
            if (type == null || type.isBlank()) {
                continue;
            }
            double[] pos = vec3Double(entry, "pos");
            if (pos == null) {
                pos = vec3Double(payload, "Pos");
            }
            if (pos == null) {
                continue;
            }
            float[] rotation = readRotation(entry, payload);
            entities.add(new TemplateEntity(type, pos[0], pos[1], pos[2], rotation[0], rotation[1], payload));
        }
        return entities;
    }

    private static float[] readRotation(NbtValue.CompoundTag entry, NbtValue.CompoundTag payload) {
        float[] fromEntry = vec2Float(entry, "rot");
        if (fromEntry != null) {
            return fromEntry;
        }
        float[] fromPayload = vec2Float(payload, "Rotation");
        return fromPayload == null ? new float[]{0.0f, 0.0f} : fromPayload;
    }

    private static int[] vec3Int(NbtValue.CompoundTag source, String key) {
        NbtValue raw = source.get(key).orElse(null);
        if (raw instanceof NbtValue.IntsTag array && array.value().length == 3) {
            return new int[]{array.value()[0], array.value()[1], array.value()[2]};
        }
        if (raw instanceof NbtValue.ListTag list && list.size() == 3) {
            int[] vector = new int[3];
            for (int index = 0; index < 3; index++) {
                if (!(list.values().get(index) instanceof NbtValue.IntTag component)) {
                    return null;
                }
                vector[index] = component.value();
            }
            return vector;
        }
        return null;
    }

    private static double[] vec3Double(NbtValue.CompoundTag source, String key) {
        NbtValue raw = source.get(key).orElse(null);
        if (raw instanceof NbtValue.IntsTag array && array.value().length == 3) {
            return new double[]{array.value()[0], array.value()[1], array.value()[2]};
        }
        if (raw instanceof NbtValue.ListTag list && list.size() == 3) {
            double[] vector = new double[3];
            for (int index = 0; index < 3; index++) {
                OptionalDouble component = numericValue(list.values().get(index));
                if (component.isEmpty()) {
                    return null;
                }
                vector[index] = component.getAsDouble();
            }
            return vector;
        }
        return null;
    }

    private static float[] vec2Float(NbtValue.CompoundTag source, String key) {
        NbtValue raw = source.get(key).orElse(null);
        if (!(raw instanceof NbtValue.ListTag list) || list.size() != 2) {
            return null;
        }
        float[] vector = new float[2];
        for (int index = 0; index < 2; index++) {
            OptionalDouble component = numericValue(list.values().get(index));
            if (component.isEmpty()) {
                return null;
            }
            vector[index] = (float) component.getAsDouble();
        }
        return vector;
    }

    private static OptionalDouble numericValue(NbtValue value) {
        if (value instanceof NbtValue.DoubleTag tag) {
            return OptionalDouble.of(tag.value());
        }
        if (value instanceof NbtValue.FloatTag tag) {
            return OptionalDouble.of(tag.value());
        }
        if (value instanceof NbtValue.IntTag tag) {
            return OptionalDouble.of(tag.value());
        }
        if (value instanceof NbtValue.LongTag tag) {
            return OptionalDouble.of(tag.value());
        }
        if (value instanceof NbtValue.ShortTag tag) {
            return OptionalDouble.of(tag.value());
        }
        if (value instanceof NbtValue.ByteTag tag) {
            return OptionalDouble.of(tag.value());
        }
        return OptionalDouble.empty();
    }

    private static OptionalInt intValue(NbtValue.CompoundTag source, String key) {
        return source.get(key).orElse(null) instanceof NbtValue.IntTag tag
                ? OptionalInt.of(tag.value())
                : OptionalInt.empty();
    }

    private static String paletteString(NbtValue rawEntry) {
        if (!(rawEntry instanceof NbtValue.CompoundTag entry)) {
            return null;
        }
        Optional<String> name = entry.getString("Name");
        if (name.isEmpty() || name.get().isBlank()) {
            return null;
        }
        Optional<NbtValue.CompoundTag> properties = entry.getCompound("Properties");
        if (properties.isEmpty()) {
            return name.get();
        }
        StringBuilder builder = new StringBuilder(name.get()).append('[');
        boolean appended = false;
        for (Map.Entry<String, NbtValue> property : properties.get().entries().entrySet()) {
            if (!(property.getValue() instanceof NbtValue.StringTag value)) {
                continue;
            }
            if (appended) {
                builder.append(',');
            }
            builder.append(property.getKey()).append('=').append(value.value());
            appended = true;
        }
        return appended ? builder.append(']').toString() : name.get();
    }

    private static BlockData parseBlockData(String blockString) {
        if (blockString == null || blockString.isBlank()) {
            return null;
        }
        try {
            return Bukkit.createBlockData(blockString);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String describeVector(int[] vector) {
        if (vector == null) {
            return "missing";
        }
        return vector[0] + "," + vector[1] + "," + vector[2];
    }

    private static String describe(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static FailureKind kindFor(Issue issue) {
        return switch (issue) {
            case OUTPUT_LIMIT, DEPTH_EXCEEDED -> FailureKind.REJECTED;
            default -> FailureKind.INVALID_INPUT;
        };
    }

    private static String keyFor(Issue issue) {
        return switch (issue) {
            case GZIP_HEADER -> "error.template.not_gzip";
            case OUTPUT_LIMIT -> "error.template.output_too_large";
            case DEPTH_EXCEEDED -> "error.template.depth_exceeded";
            case BAD_ROOT_TYPE -> "error.template.bad_root_type";
            case PARSE_BROKEN -> "error.template.parse_failed";
            case IO_FAILURE -> "error.template.parse_failed";
        };
    }
}

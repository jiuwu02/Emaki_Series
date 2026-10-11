package emaki.jiuwu.craft.structure.template.nbt;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public sealed interface NbtValue {

    byte tagId();

    record EndTag() implements NbtValue {

        @Override
        public byte tagId() {
            return 0;
        }
    }

    record ByteTag(byte value) implements NbtValue {

        @Override
        public byte tagId() {
            return 1;
        }
    }

    record ShortTag(short value) implements NbtValue {

        @Override
        public byte tagId() {
            return 2;
        }
    }

    record IntTag(int value) implements NbtValue {

        @Override
        public byte tagId() {
            return 3;
        }
    }

    record LongTag(long value) implements NbtValue {

        @Override
        public byte tagId() {
            return 4;
        }
    }

    record FloatTag(float value) implements NbtValue {

        @Override
        public byte tagId() {
            return 5;
        }
    }

    record DoubleTag(double value) implements NbtValue {

        @Override
        public byte tagId() {
            return 6;
        }
    }

    final class BytesTag implements NbtValue {

        private final byte[] value;

        public BytesTag(byte[] value) {
            this.value = value;
        }

        public byte[] value() {
            return value;
        }

        @Override
        public byte tagId() {
            return 7;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || (other instanceof BytesTag that && Arrays.equals(value, that.value));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }

    record StringTag(String value) implements NbtValue {

        @Override
        public byte tagId() {
            return 8;
        }
    }

    record ListTag(List<NbtValue> values) implements NbtValue {

        public ListTag {
            values = List.copyOf(values);
        }

        @Override
        public byte tagId() {
            return 9;
        }

        public int size() {
            return values.size();
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }
    }

    record CompoundTag(Map<String, NbtValue> entries) implements NbtValue {

        public CompoundTag {
            entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }

        @Override
        public byte tagId() {
            return 10;
        }

        public Optional<NbtValue> get(String name) {
            return Optional.ofNullable(entries.get(name));
        }

        public boolean contains(String name) {
            return entries.containsKey(name);
        }

        public int size() {
            return entries.size();
        }

        public Optional<CompoundTag> getCompound(String name) {
            return get(name).filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast);
        }

        public Optional<ListTag> getList(String name) {
            return get(name).filter(ListTag.class::isInstance).map(ListTag.class::cast);
        }

        public Optional<String> getString(String name) {
            return get(name).filter(StringTag.class::isInstance).map(value -> ((StringTag) value).value());
        }

        public OptionalInt getInt(String name) {
            return entries.get(name) instanceof IntTag tag ? OptionalInt.of(tag.value()) : OptionalInt.empty();
        }

        public OptionalLong getLong(String name) {
            return entries.get(name) instanceof LongTag tag ? OptionalLong.of(tag.value()) : OptionalLong.empty();
        }

        public OptionalDouble getDouble(String name) {
            return entries.get(name) instanceof DoubleTag tag ? OptionalDouble.of(tag.value()) : OptionalDouble.empty();
        }

        public Optional<Byte> getByte(String name) {
            return entries.get(name) instanceof ByteTag tag ? Optional.of(tag.value()) : Optional.empty();
        }

        public Optional<Short> getShort(String name) {
            return entries.get(name) instanceof ShortTag tag ? Optional.of(tag.value()) : Optional.empty();
        }

        public Optional<Float> getFloat(String name) {
            return entries.get(name) instanceof FloatTag tag ? Optional.of(tag.value()) : Optional.empty();
        }

        public Optional<byte[]> getByteArray(String name) {
            return entries.get(name) instanceof BytesTag tag ? Optional.of(tag.value()) : Optional.empty();
        }

        public Optional<int[]> getIntArray(String name) {
            return entries.get(name) instanceof IntsTag tag ? Optional.of(tag.value()) : Optional.empty();
        }

        public Optional<long[]> getLongArray(String name) {
            return entries.get(name) instanceof LongsTag tag ? Optional.of(tag.value()) : Optional.empty();
        }
    }

    final class IntsTag implements NbtValue {

        private final int[] value;

        public IntsTag(int[] value) {
            this.value = value;
        }

        public int[] value() {
            return value;
        }

        @Override
        public byte tagId() {
            return 11;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || (other instanceof IntsTag that && Arrays.equals(value, that.value));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }

    final class LongsTag implements NbtValue {

        private final long[] value;

        public LongsTag(long[] value) {
            this.value = value;
        }

        public long[] value() {
            return value;
        }

        @Override
        public byte tagId() {
            return 12;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || (other instanceof LongsTag that && Arrays.equals(value, that.value));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }
}

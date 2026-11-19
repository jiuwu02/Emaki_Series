package emaki.jiuwu.craft.dungeon.random.model;

import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;

public final class Mask {

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BitSet cells;

    public Mask(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = Math.max(1, sizeX);
        this.sizeY = Math.max(1, sizeY);
        this.sizeZ = Math.max(1, sizeZ);
        this.cells = new BitSet(this.sizeX * this.sizeY * this.sizeZ);
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

    private int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    public void add(int x, int y, int z) {
        if (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ) {
            cells.set(index(x, y, z));
        }
    }

    public boolean has(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            return false;
        }
        return cells.get(index(x, y, z));
    }

    public int count() {
        return cells.cardinality();
    }

    public boolean intersects(Mask other, int offsetX, int offsetY, int offsetZ) {
        for (int cell = cells.nextSetBit(0); cell >= 0; cell = cells.nextSetBit(cell + 1)) {
            int x = cell % sizeX;
            int z = (cell / sizeX) % sizeZ;
            int y = cell / (sizeX * sizeZ);
            if (other.has(x - offsetX, y - offsetY, z - offsetZ)) {
                return true;
            }
            if (cell == Integer.MAX_VALUE) {
                break;
            }
        }
        return false;
    }

    public List<long[]> cellsList() {
        List<long[]> list = new ArrayList<>(cells.cardinality());
        for (int cell = cells.nextSetBit(0); cell >= 0; cell = cells.nextSetBit(cell + 1)) {
            int x = cell % sizeX;
            int z = (cell / sizeX) % sizeZ;
            int y = cell / (sizeX * sizeZ);
            list.add(new long[] {TemplateModel.packCell(x, y, z)});
            if (cell == Integer.MAX_VALUE) {
                break;
            }
        }
        return list;
    }

    public String serialize() {
        byte[] bytes = cells.toByteArray();
        return sizeX + "," + sizeY + "," + sizeZ + ":" + Base64.getEncoder().encodeToString(bytes);
    }

    public static Mask deserialize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int sep = raw.indexOf(':');
        if (sep <= 0) {
            return null;
        }
        String[] dims = raw.substring(0, sep).split(",");
        if (dims.length != 3) {
            return null;
        }
        try {
            Mask mask = new Mask(
                    Integer.parseInt(dims[0].trim()),
                    Integer.parseInt(dims[1].trim()),
                    Integer.parseInt(dims[2].trim()));
            byte[] bytes = Base64.getDecoder().decode(raw.substring(sep + 1));
            BitSet restored = BitSet.valueOf(bytes);
            mask.cells.or(restored);
            return mask;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}

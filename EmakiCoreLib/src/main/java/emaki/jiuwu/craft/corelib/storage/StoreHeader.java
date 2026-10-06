package emaki.jiuwu.craft.corelib.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

final class StoreHeader {

    static final int MAGIC = 0x454D4B31;
    static final int VERSION = 1;

    private StoreHeader() {
    }

    static void write(DataOutputStream output) throws IOException {
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
    }

    static void verify(DataInputStream input) throws IOException {
        int magic = input.readInt();
        if (magic != MAGIC) {
            throw new StorageException("存储文件标识无效");
        }
        int version = input.readInt();
        if (version != VERSION) {
            throw new StorageException("不支持的存储格式版本: " + version);
        }
    }
}

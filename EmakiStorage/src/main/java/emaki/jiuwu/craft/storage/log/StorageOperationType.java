package emaki.jiuwu.craft.storage.log;

public enum StorageOperationType {

    DEPOSIT,
    WITHDRAW,
    UNLOCK,
    ADMIN_SET,
    ADMIN_GIVE,
    ADMIN_CLEAR,

    OVERFLOW;

    public boolean forced() {
        return this == ADMIN_SET || this == ADMIN_GIVE || this == ADMIN_CLEAR;
    }
}

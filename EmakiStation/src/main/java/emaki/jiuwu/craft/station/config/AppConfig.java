package emaki.jiuwu.craft.station.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_VERSION = "1.0.2";

    private final boolean releaseDefaultData;
    private final QueueSettings queueSettings;
    private final LimitSettings limitSettings;
    private final StorageSettings storageSettings;
    private final PersistenceSettings persistenceSettings;
    private final GuiSettings guiSettings;
    private final PurchaseSettings purchaseSettings;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            QueueSettings queueSettings,
            LimitSettings limitSettings,
            StorageSettings storageSettings,
            PersistenceSettings persistenceSettings,
            GuiSettings guiSettings,
            PurchaseSettings purchaseSettings) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
        this.queueSettings = queueSettings == null ? QueueSettings.defaults() : queueSettings.normalized();
        this.limitSettings = limitSettings == null ? LimitSettings.defaults() : limitSettings.normalized();
        this.storageSettings = storageSettings == null ? StorageSettings.defaults() : storageSettings.normalized();
        this.persistenceSettings = persistenceSettings == null
                ? PersistenceSettings.defaults()
                : persistenceSettings.normalized();
        this.guiSettings = guiSettings == null ? GuiSettings.defaults() : guiSettings.normalized();
        this.purchaseSettings = purchaseSettings == null
                ? PurchaseSettings.defaults()
                : purchaseSettings.normalized();
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN",
                CURRENT_VERSION,
                true,
                QueueSettings.defaults(),
                LimitSettings.defaults(),
                StorageSettings.defaults(),
                PersistenceSettings.defaults(),
                GuiSettings.defaults(),
                PurchaseSettings.defaults());
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public QueueSettings queueSettings() {
        return queueSettings;
    }

    public LimitSettings limitSettings() {
        return limitSettings;
    }

    public StorageSettings storageSettings() {
        return storageSettings;
    }

    public PersistenceSettings persistenceSettings() {
        return persistenceSettings;
    }

    public GuiSettings guiSettings() {
        return guiSettings;
    }

    public PurchaseSettings purchaseSettings() {
        return purchaseSettings;
    }
}

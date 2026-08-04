package emaki.jiuwu.craft.station.config;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;

/**
 * Parsed {@code config.yml} for EmakiStation.
 *
 * <p>{@link #CURRENT_VERSION} is kept in step with the module POM version; the bootstrap merge uses it
 * to decide whether a shipped default file needs to be merged into the administrator's copy.
 */
public final class AppConfig extends BaseAppConfig {

    /** The config version this build ships, matching the module POM. */
    public static final String CURRENT_VERSION = "1.0.0";

    private final boolean releaseDefaultData;
    private final QueueSettings queueSettings;
    private final LimitSettings limitSettings;
    private final StorageSettings storageSettings;
    private final PersistenceSettings persistenceSettings;
    private final GuiSettings guiSettings;

    /**
     * Creates a configuration snapshot.
     *
     * @param language            the active language file name
     * @param configVersion       the version found in the administrator's file
     * @param releaseDefaultData  whether bundled example data is written on first run
     * @param queueSettings       queue defaults
     * @param limitSettings       volume ceilings
     * @param storageSettings     warehouse-channel settings
     * @param persistenceSettings save-timing settings
     * @param guiSettings         GUI timing settings
     */
    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            QueueSettings queueSettings,
            LimitSettings limitSettings,
            StorageSettings storageSettings,
            PersistenceSettings persistenceSettings,
            GuiSettings guiSettings) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
        this.queueSettings = queueSettings == null ? QueueSettings.defaults() : queueSettings.normalized();
        this.limitSettings = limitSettings == null ? LimitSettings.defaults() : limitSettings.normalized();
        this.storageSettings = storageSettings == null ? StorageSettings.defaults() : storageSettings.normalized();
        this.persistenceSettings = persistenceSettings == null
                ? PersistenceSettings.defaults()
                : persistenceSettings.normalized();
        this.guiSettings = guiSettings == null ? GuiSettings.defaults() : guiSettings.normalized();
    }

    /** {@return the shipped defaults} */
    public static AppConfig defaults() {
        return new AppConfig("zh_CN",
                CURRENT_VERSION,
                true,
                QueueSettings.defaults(),
                LimitSettings.defaults(),
                StorageSettings.defaults(),
                PersistenceSettings.defaults(),
                GuiSettings.defaults());
    }

    /** {@return whether bundled example data is written on first run} */
    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    /** {@return queue defaults inherited by stations that do not override them} */
    public QueueSettings queueSettings() {
        return queueSettings;
    }

    /** {@return volume ceilings} */
    public LimitSettings limitSettings() {
        return limitSettings;
    }

    /** {@return warehouse-channel settings} */
    public StorageSettings storageSettings() {
        return storageSettings;
    }

    /** {@return save-timing settings} */
    public PersistenceSettings persistenceSettings() {
        return persistenceSettings;
    }

    /** {@return GUI timing settings} */
    public GuiSettings guiSettings() {
        return guiSettings;
    }
}

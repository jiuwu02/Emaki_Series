package emaki.jiuwu.craft.station.config;

public record PersistenceSettings(int autosaveIntervalSeconds, boolean saveOnSubmit) {

    public static PersistenceSettings defaults() {
        return new PersistenceSettings(60, true);
    }

    public PersistenceSettings normalized() {
        return new PersistenceSettings(Math.clamp(autosaveIntervalSeconds, 5, 3_600), saveOnSubmit);
    }
}

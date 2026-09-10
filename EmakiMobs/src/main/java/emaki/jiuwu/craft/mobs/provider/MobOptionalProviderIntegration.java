package emaki.jiuwu.craft.mobs.provider;

interface MobOptionalProviderIntegration {

    boolean registered();

    void register();

    void close();
}

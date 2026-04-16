package emaki.jiuwu.craft.corelib.plugin;

import java.util.Objects;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;

public abstract class AbstractConfigurableEmakiPlugin<C> extends AbstractEmakiPlugin {

    private final Supplier<C> defaultConfigSupplier;

    protected AbstractConfigurableEmakiPlugin(Supplier<C> defaultConfigSupplier) {
        this.defaultConfigSupplier = Objects.requireNonNull(defaultConfigSupplier, "defaultConfigSupplier");
    }

    public final C appConfig() {
        YamlConfigLoader<C> loader = appConfigLoader();
        return loader == null ? defaultConfigSupplier.get() : loader.current();
    }

    public abstract YamlConfigLoader<C> appConfigLoader();
}

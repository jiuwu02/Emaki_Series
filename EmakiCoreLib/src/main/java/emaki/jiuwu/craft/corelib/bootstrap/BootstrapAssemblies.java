package emaki.jiuwu.craft.corelib.bootstrap;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;

public final class BootstrapAssemblies {

    @FunctionalInterface
    public interface VersionedMergeHook {
        void apply(String relativePath, YamlSection runtime, YamlSection bundled);
    }

    public static BootstrapHooks hooks() {
        return hooks(null, null, null);
    }

    public static BootstrapHooks hooks(BooleanSupplier installDefaultData,
            VersionedMergeHook afterVersionedMerge,
            Runnable afterBootstrap) {
        return new BootstrapHooks() {
            @Override
            public boolean shouldInstallDefaultData() {
                return installDefaultData == null || installDefaultData.getAsBoolean();
            }

            @Override
            public void afterVersionedMerge(String relativePath, YamlSection runtime, YamlSection bundled) {
                if (afterVersionedMerge != null) {
                    afterVersionedMerge.apply(relativePath, runtime, bundled);
                }
            }

            @Override
            public void afterBootstrap() {
                if (afterBootstrap != null) {
                    afterBootstrap.run();
                }
            }
        };
    }

    public static <T> BooleanSupplier releaseDefaultData(YamlConfigLoader<T> loader,
            Predicate<T> enabled) {
        return () -> {
            T current = loader.current();
            return current == null || enabled.test(current);
        };
    }

    public static LanguageLoader moduleLanguageLoader(JavaPlugin plugin) {
        return new LanguageLoader(plugin, "lang", "lang", "zh_CN", "zh_CN");
    }
}

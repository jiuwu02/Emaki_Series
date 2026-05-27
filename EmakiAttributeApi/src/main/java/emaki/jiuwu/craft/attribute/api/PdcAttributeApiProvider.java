package emaki.jiuwu.craft.attribute.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class PdcAttributeApiProvider {

    private PdcAttributeApiProvider() {
    }

    public static Optional<PdcAttributeApi> get() {
        RegisteredServiceProvider<PdcAttributeApi> provider = Bukkit.getServicesManager().getRegistration(PdcAttributeApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}

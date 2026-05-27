package emaki.jiuwu.craft.skills.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EmakiSkillsApiProvider {

    private EmakiSkillsApiProvider() {
    }

    public static Optional<EmakiSkillsApi> get() {
        RegisteredServiceProvider<EmakiSkillsApi> provider = Bukkit.getServicesManager().getRegistration(EmakiSkillsApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}

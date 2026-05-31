package emaki.jiuwu.craft.skills.api;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Entry point for obtaining the {@link EmakiSkillsApi} that EmakiSkills
 * registers with the Bukkit {@code ServicesManager}.
 *
 * <p>The service only exists after EmakiSkills has enabled, so resolve it
 * lazily rather than caching it during your own plugin's load phase.
 */
public final class EmakiSkillsApiProvider {

    private EmakiSkillsApiProvider() {
    }

    /**
     * {@return the registered {@link EmakiSkillsApi}, or an empty optional}
     * Empty when EmakiSkills is absent or has not finished enabling.
     */
    public static Optional<EmakiSkillsApi> get() {
        RegisteredServiceProvider<EmakiSkillsApi> provider = Bukkit.getServicesManager().getRegistration(EmakiSkillsApi.class);
        return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
    }
}

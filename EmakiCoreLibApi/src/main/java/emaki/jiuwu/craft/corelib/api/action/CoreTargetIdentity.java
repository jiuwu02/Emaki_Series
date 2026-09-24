package emaki.jiuwu.craft.corelib.api.action;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;

/**
 * One entity's identity inside an external mob system.
 *
 * @param systemId the owning system, as declared by
 *                 {@link CoreTargetIdentityProvider#systemId()}, lowercase
 * @param id       the system's own mob type id, kept verbatim because those ids are case-sensitive
 * @param level    the system's level value, or {@code 0} when the system has no level concept
 */
public record CoreTargetIdentity(@NotNull String systemId, @NotNull String id, double level) {

    public CoreTargetIdentity {
        systemId = systemId == null ? "" : systemId.trim().toLowerCase(Locale.ROOT);
        id = id == null ? "" : id.trim();
    }

    /** {@return whether this identity names a real mob type} */
    public boolean present() {
        return !id.isEmpty();
    }
}
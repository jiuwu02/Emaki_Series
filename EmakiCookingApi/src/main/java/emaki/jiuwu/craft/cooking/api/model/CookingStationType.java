package emaki.jiuwu.craft.cooking.api.model;

import java.util.Locale;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The seven cooking station kinds EmakiCooking provides.
 *
 * <p>{@link #configKey()} is the stable identifier used in configuration files and in the
 * {@code stationType} field of EmakiCooking's events. Do not rely on {@link #name()} for persistence.
 */
public enum CookingStationType {

    /** Chopping board: reduces one input into pieces. */
    CHOPPING_BOARD("chopping_board"),

    /** Wok: stir-fries several ingredients at a heat level. */
    WOK("wok"),

    /** Grinder: mills one input into powder. */
    GRINDER("grinder"),

    /** Steamer: steams inputs, tracking a steam reservoir. */
    STEAMER("steamer"),

    /** Oven: bakes inputs, tracking heat and moisture. */
    OVEN("oven"),

    /** Juicer: presses inputs into a fluid. */
    JUICER("juicer"),

    /** Fermentation barrel: ferments inputs over a long duration. */
    FERMENTATION_BARREL("fermentation_barrel");

    private final String configKey;

    CookingStationType(String configKey) {
        this.configKey = configKey;
    }

    /** {@return the stable identifier used in configuration and events} */
    public @NotNull String configKey() {
        return configKey;
    }

    /**
     * Resolves a station type from its configuration key.
     *
     * @param configKey the key to resolve, case-insensitive
     * @return the matching type, or an empty optional when the key is unknown
     */
    public static @NotNull Optional<CookingStationType> fromConfigKey(@Nullable String configKey) {
        if (configKey == null || configKey.isBlank()) {
            return Optional.empty();
        }
        String normalized = configKey.trim().toLowerCase(Locale.ROOT);
        for (CookingStationType type : values()) {
            if (type.configKey.equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}

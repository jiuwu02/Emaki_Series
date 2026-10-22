package emaki.jiuwu.craft.corelib.api.integration;

import org.bukkit.block.Block;
import org.jetbrains.annotations.ApiStatus;

/**
 * Read and write access to the custom blocks of one third-party block provider (Oraxen, Nexo,
 * ItemsAdder, CraftEngine).
 *
 * <p>CoreLib exposes one bridge per provider and swaps in a no-op bridge when the backing plugin
 * is absent, so consumers gate on {@link #available()} instead of catching failures. Every method
 * is failure tolerant: backend errors, {@code null} blocks and blank identifiers are reported
 * through the neutral result ({@code false} or an empty string) rather than thrown, and both
 * sides of an identifier comparison are trimmed and lowercased with {@code Locale.ROOT} first.
 *
 * <p>Marked experimental: the method set may still change between releases.
 */
@ApiStatus.Experimental
public interface CustomBlockBridge {

    /**
     * {@return {@code true} when the backing provider is present and its API can be reached; the
     * no-op bridge always answers {@code false}}
     */
    boolean available();

    /**
     * Checks whether {@code block} is one of this provider's custom blocks.
     *
     * @param block block to inspect; {@code null} yields {@code false}
     * @return {@code true} only for a custom block of this provider; backend failures answer
     *         {@code false}
     */
    boolean isCustomBlock(Block block);

    /**
     * Resolves the provider identifier of a custom block.
     *
     * @param block block to inspect; {@code null} yields an empty string
     * @return the normalized (trimmed, {@code Locale.ROOT}-lowercased) identifier of the custom
     *         block, or an empty string when the block does not belong to this provider or the
     *         lookup failed
     */
    String identifyBlock(Block block);

    /**
     * Checks a block against an identifier.
     *
     * @param block block to inspect; {@code null} behaves like a non-matching block
     * @param identifier expected identifier; normalized the same way as {@link #identifyBlock}
     * @return {@code true} when the normalized block identifier equals the normalized expected
     *         identifier; blank or unresolvable identifiers answer {@code false}
     */
    boolean matches(Block block, String identifier);

    /**
     * Switches the {@code lit} state of a supported custom block.
     *
     * @param block block to change; {@code null} yields {@code false}
     * @param lit requested lit state
     * @return {@code true} only when the provider applied the state; blocks without a lit
     *         property, unsupported backends and failures answer {@code false}
     */
    boolean setLit(Block block, boolean lit);

    /**
     * Places a custom block at the position of {@code block}.
     *
     * @param block position to place at; {@code null} yields {@code false}
     * @param identifier identifier of the custom block to place; blank yields {@code false}
     * @return {@code true} only when the placement succeeded for a known identifier; unknown
     *         identifiers and failures answer {@code false}
     */
    boolean placeBlock(Block block, String identifier);
}

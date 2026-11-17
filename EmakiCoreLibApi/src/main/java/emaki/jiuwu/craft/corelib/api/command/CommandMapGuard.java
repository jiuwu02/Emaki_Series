package emaki.jiuwu.craft.corelib.api.command;

import java.util.function.BiPredicate;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Runtime bridge that wraps the server {@code CommandMap}, so command dispatches issued on behalf of
 * a player by plugins can be inspected and swallowed before they reach any command.
 *
 * <p>Consumers obtain the implementation from
 * {@link emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi#commandMapGuard()}. The implementation is
 * delivered by EmakiCoreLib as the runtime library {@code emaki.jiuwu.craft:emakinms}, is loaded by
 * EmakiCoreLib itself, is never shaded into a plugin jar, and must not be referenced at compile
 * time beyond this interface.</p>
 *
 * <p><strong>Threading:</strong> {@link #install(BiPredicate)} and {@link Handle#close()} run from
 * the caller's thread and should stay on the server owner thread. The interceptor itself is invoked
 * from whatever thread issues the dispatch, so it must be thread-safe.</p>
 */
public interface CommandMapGuard {

    /**
     * Wraps the server command map so every dispatch first consults {@code interceptor}.
     *
     * <p>When the interceptor returns {@code true} the dispatch is swallowed and reported as
     * handled; when it returns {@code false} the dispatch proceeds unchanged. Installs stack:
     * closing a handle restores exactly the command map instance installed before its own.</p>
     *
     * @param interceptor dispatch predicate, invoked with the sender and the raw command line
     * @return a handle that restores the previous command map when closed
     * @throws IllegalStateException when the platform's command map cannot be wrapped
     */
    @NotNull
    Handle install(@NotNull BiPredicate<CommandSender, String> interceptor);

    /** Restores the command map captured by a matching {@link #install(BiPredicate)} call. */
    interface Handle extends AutoCloseable {

        @Override
        void close();
    }
}

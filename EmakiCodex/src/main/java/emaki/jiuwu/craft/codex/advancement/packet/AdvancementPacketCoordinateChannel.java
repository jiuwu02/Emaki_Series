package emaki.jiuwu.craft.codex.advancement.packet;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.NamespacedKey;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementDisplay;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementHolder;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAdvancements;

import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.codex.advancement.model.AdvancementDefinition;

/**
 * Overrides the on-screen grid position of EmakiCodex advancements by rewriting the
 * outgoing {@link WrapperPlayServerUpdateAdvancements} packet.
 *
 * <p>Vanilla advancement JSON (used by {@code Bukkit.getUnsafe().loadAdvancement}) cannot
 * express a node position, so the client auto-lays-out the tree from the {@code parent}
 * graph. This listener leaves that server-side registration untouched and only patches the
 * {@link AdvancementDisplay#setX(float)}/{@link AdvancementDisplay#setY(float)} values of
 * our own advancements as they leave the server, giving servers precise control over layout
 * when PacketEvents is present. It never adds, removes, or completes advancements, so the
 * {@code on_complete} action pipeline and {@code PlayerAdvancementDoneEvent} are unaffected.
 *
 * <p>This class references PacketEvents types directly, so it is only ever instantiated by
 * {@link AdvancementPacketGateway} after PacketEvents is confirmed present. When PacketEvents
 * is absent the class is never loaded, avoiding {@code NoClassDefFoundError}. Callbacks run on
 * a netty thread and only read the registrar's concurrent map plus mutate the packet object;
 * no Bukkit API is touched.
 */
public final class AdvancementPacketCoordinateChannel extends PacketListenerAbstract {

    private final AdvancementRegistrar registrar;
    private final String namespace;
    private final Logger logger;

    /**
     * @param registrar the registrar used to resolve a packet advancement key back to its
     *                  configured definition (and therefore its x/y coordinates)
     * @param namespace the namespace of EmakiCodex advancement keys (e.g. {@code emakicodex});
     *                  packets for any other namespace are ignored
     * @param logger    the plugin logger for best-effort warnings
     */
    public AdvancementPacketCoordinateChannel(AdvancementRegistrar registrar, String namespace, Logger logger) {
        super(PacketListenerPriority.NORMAL);
        this.registrar = registrar;
        this.namespace = namespace;
        this.logger = logger;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.UPDATE_ADVANCEMENTS) {
            return;
        }
        try {
            WrapperPlayServerUpdateAdvancements packet = new WrapperPlayServerUpdateAdvancements(event);
            List<AdvancementHolder> added = packet.getAddedAdvancements();
            if (added == null || added.isEmpty()) {
                return;
            }
            boolean patched = false;
            for (AdvancementHolder holder : added) {
                if (applyCoordinates(holder)) {
                    patched = true;
                }
            }
            if (patched) {
                event.markForReEncode(true);
            }
        } catch (Throwable throwable) {
            // A malformed/edge packet must never break advancement delivery; fall back to
            // the client's auto-layout by leaving the packet untouched.
            logger.log(Level.WARNING, "[Codex] Advancement coordinate injection skipped: " + throwable.getMessage());
        }
    }

    private boolean applyCoordinates(AdvancementHolder holder) {
        if (holder == null || holder.getAdvancement() == null) {
            return false;
        }
        ResourceLocation identifier = holder.getIdentifier();
        if (identifier == null || !namespace.equals(identifier.getNamespace())) {
            return false;
        }
        AdvancementDisplay display = holder.getAdvancement().getDisplay();
        if (display == null) {
            return false;
        }
        NamespacedKey key = NamespacedKey.fromString(identifier.toString());
        AdvancementDefinition definition = registrar.definitionByKey(key);
        if (definition == null || !definition.hasExplicitPosition()) {
            return false;
        }
        display.setX((float) definition.x());
        display.setY((float) definition.y());
        return true;
    }
}

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

public final class AdvancementPacketCoordinateChannel extends PacketListenerAbstract {

    private final AdvancementRegistrar registrar;
    private final String namespace;
    private final Logger logger;

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

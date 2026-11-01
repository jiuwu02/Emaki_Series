package emaki.jiuwu.craft.dummy.transport.mcpl;

import java.util.BitSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundDisconnectPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundCustomQueryPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundBossEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundChangeDifficultyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCooldownPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetActionBarTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundClearTitlesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetSubtitleTextPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.ClientboundSetTitlesAnimationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTakeItemEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerClosePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundOpenScreenPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelParticlesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.dummy.transport.TransportCallbacks;
import emaki.jiuwu.craft.dummy.transport.TransportLink;

final class McplLink extends SessionAdapter implements TransportLink {

    private static final String PLAYER_TYPE = "player";
    private static final String PLAYER_TYPE_NAMESPACED = "minecraft:player";

    private final Session session;
    private final TransportCallbacks callbacks;
    private final AtomicInteger actionSequence = new AtomicInteger();
    private final Map<Integer, EntitySpot> entities = new ConcurrentHashMap<>();

    private volatile boolean active = true;
    private volatile boolean playState;
    private volatile boolean hasPose;
    private volatile int currentWindowId;
    private volatile int windowStateId;
    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile double yaw;
    private volatile double pitch;

    McplLink(Session session, TransportCallbacks callbacks) {
        this.session = session;
        this.callbacks = callbacks;
    }

    record EntitySpot(double x, double y, double z, String type, boolean player) {
    }

    private void send(Packet packet) {
        if (!playState) {
            callbacks.onProtocolPacket("send", packet.getClass().getSimpleName());
        }
        session.send(packet);
    }

    @Override
    public void packetReceived(Session currentSession, Packet packet) {
        if (!playState) {
            callbacks.onProtocolPacket("recv", packet.getClass().getSimpleName());
        }
        if (packet instanceof ClientboundLoginPacket loginPacket) {
            playState = true;
            callbacks.onJoined(loginPacket.getEntityId());
        } else if (packet instanceof ClientboundCustomQueryPacket queryPacket) {
            callbacks.onProtocolPacket("query", queryPacket.getChannel().asString());
            send(new ServerboundCustomQueryAnswerPacket(queryPacket.getMessageId()));
        } else if (packet instanceof ClientboundResourcePackPushPacket packPushPacket) {
            UUID packId = packPushPacket.getId();
            send(new ServerboundResourcePackPacket(packId, ResourcePackStatus.ACCEPTED));
            send(new ServerboundResourcePackPacket(packId, ResourcePackStatus.DOWNLOADED));
            send(new ServerboundResourcePackPacket(packId, ResourcePackStatus.SUCCESSFULLY_LOADED));
        } else if (packet instanceof ClientboundSetHealthPacket healthPacket) {
            callbacks.onProtocolPacket("health", String.valueOf(healthPacket.getHealth()));
            if (healthPacket.getHealth() <= 0.0F) {
                send(new ServerboundClientCommandPacket(ClientCommand.PERFORM_RESPAWN));
                callbacks.onProtocolPacket("respawn", "requested");
            }
        } else if (packet instanceof ClientboundRespawnPacket respawnPacket) {
            callbacks.onProtocolPacket("respawn", "accepted");
            entities.clear();
        } else if (packet instanceof ClientboundPlayerPositionPacket positionPacket) {
            applyServerPosition(positionPacket);
            send(new ServerboundAcceptTeleportationPacket(positionPacket.getId()));
            sendCurrentPose();
            callbacks.onTeleport(x, y, z, (float) yaw, (float) pitch);
        } else if (packet instanceof ClientboundKeepAlivePacket keepAlivePacket) {
            send(new ServerboundKeepAlivePacket(keepAlivePacket.getPingId()));
        } else if (packet instanceof ClientboundPingPacket pingPacket) {
            send(new ServerboundPongPacket(pingPacket.getId()));
        } else if (packet instanceof ClientboundSystemChatPacket systemChatPacket) {
            callbacks.onServerMessage(MiniMessages.plain(systemChatPacket.getContent()),
                    systemChatPacket.isOverlay());
        } else if (packet instanceof ClientboundSetActionBarTextPacket actionBarPacket) {
            callbacks.onServerMessage(MiniMessages.plain(actionBarPacket.getText()), true);
        } else if (packet instanceof ClientboundSetTitleTextPacket titlePacket) {
            callbacks.onServerMessage(MiniMessages.plain(titlePacket.getText()), false);
        } else if (packet instanceof ClientboundPlayerChatPacket playerChatPacket) {
            String text = playerChatPacket.getUnsignedContent() != null
                    ? MiniMessages.plain(playerChatPacket.getUnsignedContent())
                    : playerChatPacket.getContent();
            callbacks.onServerMessage(text, false);
        } else if (packet instanceof ClientboundDisconnectPacket disconnectPacket) {
            callbacks.onKicked(MiniMessages.plain(disconnectPacket.getReason()));
        } else if (packet instanceof ClientboundContainerClosePacket closePacket) {
            callbacks.onWindowClosed(closePacket.getContainerId());
        } else if (packet instanceof ClientboundOpenScreenPacket openScreenPacket) {
            currentWindowId = openScreenPacket.getContainerId();
            callbacks.onWindowOpened(openScreenPacket.getContainerId(),
                    MiniMessages.plain(openScreenPacket.getTitle()));
        } else if (packet instanceof ClientboundContainerSetContentPacket contentPacket) {
            int windowId = contentPacket.getContainerId();
            if (windowId != 0) {
                currentWindowId = windowId;
            }
            windowStateId = contentPacket.getStateId();
            callbacks.onWindowContent(windowId, contentPacket.getStateId(),
                    countStacks(contentPacket.getItems()));
        } else if (packet instanceof ClientboundAddEntityPacket spawnPacket) {
            String typeName = spawnPacket.getType().name().toLowerCase(Locale.ROOT);
            entities.put(spawnPacket.getEntityId(), new EntitySpot(
                    spawnPacket.getX(), spawnPacket.getY(), spawnPacket.getZ(),
                    typeName, isPlayerType(typeName)));
        } else if (packet instanceof ClientboundSoundPacket soundPacket) {
            callbacks.onRecvEvent("sound", soundPacket.getSound()
                    + " @ " + soundPacket.getX() + "," + soundPacket.getY() + "," + soundPacket.getZ()
                    + " vol=" + soundPacket.getVolume() + " pitch=" + soundPacket.getPitch());
        } else if (packet instanceof ClientboundLevelParticlesPacket particlePacket) {
            callbacks.onRecvEvent("particle", particlePacket.getParticle()
                    + " @ " + particlePacket.getX() + "," + particlePacket.getY() + ","
                    + particlePacket.getZ() + " amount=" + particlePacket.getAmount());
        } else if (packet instanceof ClientboundBossEventPacket bossPacket) {
            String bossTitle = bossPacket.getTitle() == null ? "" : MiniMessages.plain(bossPacket.getTitle());
            callbacks.onRecvEvent("bossevent", bossPacket.getAction()
                    + " uuid=" + bossPacket.getUuid()
                    + " title=" + singleLineText(bossTitle)
                    + " health=" + bossPacket.getHealth()
                    + " color=" + bossPacket.getColor());
        } else if (packet instanceof ClientboundSetSubtitleTextPacket subtitlePacket) {
            callbacks.onRecvEvent("subtitle", MiniMessages.plain(subtitlePacket.getText()));
        } else if (packet instanceof ClientboundSetTitlesAnimationPacket animationPacket) {
            callbacks.onRecvEvent("titleanim", "in=" + animationPacket.getFadeIn()
                    + " stay=" + animationPacket.getStay() + " out=" + animationPacket.getFadeOut());
        } else if (packet instanceof ClientboundClearTitlesPacket) {
            callbacks.onRecvEvent("cleartitles", "reset");
        } else if (packet instanceof ClientboundUpdateMobEffectPacket effectPacket) {
            callbacks.onRecvEvent("mobeffect", "entity=" + effectPacket.getEntityId()
                    + " effect=" + effectPacket.getEffect()
                    + " amp=" + effectPacket.getAmplifier()
                    + " dur=" + effectPacket.getDuration());
        } else if (packet instanceof ClientboundRemoveMobEffectPacket removeEffectPacket) {
            callbacks.onRecvEvent("removemobeffect", "entity=" + removeEffectPacket.getEntityId()
                    + " effect=" + removeEffectPacket.getEffect());
        } else if (packet instanceof ClientboundSetTimePacket timePacket) {
            callbacks.onRecvEvent("settime", "gametime=" + timePacket.getGameTime()
                    + " clocks=" + timePacket.getClockUpdates().size());
        } else if (packet instanceof ClientboundTakeItemEntityPacket takeItemPacket) {
            callbacks.onRecvEvent("takeitem", "collected=" + takeItemPacket.getCollectedEntityId()
                    + " collector=" + takeItemPacket.getCollectorEntityId()
                    + " count=" + takeItemPacket.getItemCount());
        } else if (packet instanceof ClientboundChangeDifficultyPacket difficultyPacket) {
            callbacks.onRecvEvent("difficulty", difficultyPacket.getDifficulty()
                    + " locked=" + difficultyPacket.isDifficultyLocked());
        } else if (packet instanceof ClientboundGameEventPacket gameEventPacket) {
            callbacks.onRecvEvent("gameevent", gameEventPacket.getNotification()
                    + "=" + gameEventPacket.getValue());
        } else if (packet instanceof ClientboundCooldownPacket cooldownPacket) {
            callbacks.onRecvEvent("cooldown", "group=" + cooldownPacket.getCooldownGroup()
                    + " ticks=" + cooldownPacket.getCooldownTicks());
        }
    }

    private static String singleLineText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
    }

    @Override
    public void disconnected(DisconnectedEvent event) {
        active = false;
        String reason = event.getReason() != null
                ? MiniMessages.plain(event.getReason())
                : (event.getCause() != null ? String.valueOf(event.getCause().getMessage()) : "closed");
        callbacks.onClosed(reason);
    }

    @Override
    public boolean isActive() {
        return active && session.isConnected();
    }

    @Override
    public void sendChat(String text) {
        send(new ServerboundChatPacket(text, System.currentTimeMillis(), 0L, null, 0,
                new BitSet(0), 0));
    }

    @Override
    public void sendCommand(String command) {
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        send(new ServerboundChatCommandPacket(normalized));
    }

    @Override
    public void sendMovement(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch) {
        x = targetX;
        y = targetY;
        z = targetZ;
        yaw = targetYaw;
        pitch = targetPitch;
        hasPose = true;
        sendCurrentPose();
    }

    @Override
    public void sendLook(float targetYaw, float targetPitch) {
        yaw = targetYaw;
        pitch = targetPitch;
        hasPose = true;
        sendCurrentPose();
    }

    @Override
    public void sendInteractBlock(int blockX, int blockY, int blockZ, int face) {
        send(new ServerboundUseItemOnPacket(Vector3i.from(blockX, blockY, blockZ),
                toDirection(face), Hand.MAIN_HAND, 0.5F, 0.5F, 0.5F, false, false,
                actionSequence.incrementAndGet()));
    }

    @Override
    public int sendAttackNearest(double maxDistance) {
        if (!hasPose) {
            return -1;
        }
        int nearestId = -1;
        double nearestDistance = maxDistance;
        for (Map.Entry<Integer, EntitySpot> entry : entities.entrySet()) {
            EntitySpot spot = entry.getValue();
            if (spot.player()) {
                continue;
            }
            double distance = Math.sqrt(Math.pow(spot.x() - x, 2)
                    + Math.pow(spot.y() - y, 2)
                    + Math.pow(spot.z() - z, 2));
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearestId = entry.getKey();
            }
        }
        if (nearestId < 0) {
            return -1;
        }
        send(new ServerboundAttackPacket(nearestId));
        entities.remove(nearestId);
        return nearestId;
    }

    @Override
    public int clickSlot(int slot) {
        if (currentWindowId == 0) {
            return -1;
        }
        send(new ServerboundContainerClickPacket(currentWindowId, windowStateId, slot,
                ContainerActionType.CLICK_ITEM, ClickItemAction.LEFT_CLICK,
                new HashedStack(0, 0, Map.of(), Set.of()), Map.of()));
        return 0;
    }

    @Override
    public double[] pose() {
        return new double[] { x, y, z, yaw, pitch };
    }

    @Override
    public boolean hasPose() {
        return hasPose;
    }

    @Override
    public void disconnect(String reason) {
        session.disconnect(reason == null || reason.isBlank() ? "bye" : reason);
    }

    @Override
    public void close() {
        active = false;
        session.disconnect("closed");
    }

    private void applyServerPosition(ClientboundPlayerPositionPacket positionPacket) {
        double nextX = positionPacket.getPosition().getX();
        double nextY = positionPacket.getPosition().getY();
        double nextZ = positionPacket.getPosition().getZ();
        double nextYaw = positionPacket.getYRot();
        double nextPitch = positionPacket.getXRot();
        for (PositionElement element : positionPacket.getRelatives()) {
            switch (element) {
                case X -> nextX += x;
                case Y -> nextY += y;
                case Z -> nextZ += z;
                case Y_ROT -> nextYaw += yaw;
                case X_ROT -> nextPitch += pitch;
                default -> {
                }
            }
        }
        x = nextX;
        y = nextY;
        z = nextZ;
        yaw = nextYaw;
        pitch = nextPitch;
        hasPose = true;
    }

    private void sendCurrentPose() {
        send(new ServerboundMovePlayerPosRotPacket(true, false, x, y, z,
                (float) yaw, (float) pitch));
    }

    private static Direction toDirection(int face) {
        return switch (face) {
            case 0 -> Direction.DOWN;
            case 1 -> Direction.UP;
            case 2 -> Direction.NORTH;
            case 3 -> Direction.SOUTH;
            case 4 -> Direction.WEST;
            default -> Direction.EAST;
        };
    }

    private static boolean isPlayerType(String typeName) {
        return PLAYER_TYPE.equals(typeName) || PLAYER_TYPE_NAMESPACED.equals(typeName);
    }

    private static int countStacks(ItemStack[] items) {
        int count = 0;
        if (items == null) {
            return 0;
        }
        for (ItemStack item : items) {
            if (item != null && item.getAmount() > 0) {
                count++;
            }
        }
        return count;
    }
}

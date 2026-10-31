package emaki.jiuwu.craft.dummy.transport.mcpl;

import java.net.InetSocketAddress;

import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;

import emaki.jiuwu.craft.dummy.transport.DummyTransport;
import emaki.jiuwu.craft.dummy.transport.TransportCallbacks;
import emaki.jiuwu.craft.dummy.transport.TransportEndpoint;
import emaki.jiuwu.craft.dummy.transport.TransportLink;

public final class McplTransport implements DummyTransport {

    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    @Override
    public TransportLink open(String dummyName, TransportEndpoint endpoint, TransportCallbacks callbacks) {
        MinecraftProtocol protocol = new MinecraftProtocol(dummyName);
        ClientSession session = ClientNetworkSessionFactory.factory()
                .setRemoteSocketAddress(new InetSocketAddress(endpoint.host(), endpoint.port()))
                .setProtocol(protocol)
                .create();
        session.setFlag(BuiltinFlags.CLIENT_CONNECT_TIMEOUT, CONNECT_TIMEOUT_SECONDS);
        McplLink link = new McplLink(session, callbacks);
        session.addListener(link);
        session.connect(false);
        return link;
    }

    @Override
    public void shutdown() {
    }
}

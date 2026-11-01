package emaki.jiuwu.craft.dummy.transport;

public interface DummyTransport {

    TransportLink open(String dummyName, TransportEndpoint endpoint, TransportCallbacks callbacks);

    void shutdown();
}

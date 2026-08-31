package emaki.jiuwu.craft.corelib.item;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProbeResult;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProbeState;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.itemsource.LifecycleStatus;
import emaki.jiuwu.craft.corelib.api.text.Texts;

abstract class AbstractManagedItemSourceProvider<A extends AbstractManagedItemSourceProvider.Accessor>
        implements ManagedItemSourceProvider {

    private final PluginAvailability pluginAvailability;
    private final A accessor;
    private final AtomicBoolean loaded = new AtomicBoolean();

    protected AbstractManagedItemSourceProvider(PluginAvailability pluginAvailability, A accessor) {
        this.pluginAvailability = pluginAvailability == null ? PluginAvailability.BUKKIT : pluginAvailability;
        this.accessor = accessor;
    }

    protected abstract String waitingDetail();

    protected final A accessor() {
        return accessor;
    }

    protected final boolean isOperational() {
        return loaded.get() && pluginAvailability.isPluginEnabled(providerPluginName())
                && accessor.ensureAvailable();
    }

    @Override
    public boolean supports(ItemSourceRef ref) {
        return ref != null && kind().equals(ref.kind());
    }

    @Override
    public ItemSourceProbeResult probe(ItemSourceRef ref) {
        String providerId = kind().key();
        if (!supports(ref)) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INVALID_SOURCE, ref, providerId,
                    "This provider does not handle the given item source.");
        }
        if (!pluginAvailability.isPluginEnabled(providerPluginName())) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.PROVIDER_NOT_READY, ref, providerId,
                    waitingDetail());
        }
        try {
            if (!accessor.ensureAvailable()) {
                return ItemSourceProbeResult.of(ItemSourceProbeState.INCOMPATIBLE, ref, providerId,
                        accessor.failureReason());
            }
            if (!loaded.get()) {
                return ItemSourceProbeResult.of(ItemSourceProbeState.PROVIDER_NOT_READY, ref, providerId,
                        waitingDetail());
            }
            ItemStack itemStack = accessor.createItem(ref.identifier(), 1);
            return itemStack == null || itemStack.getType().isAir()
                    ? ItemSourceProbeResult.of(ItemSourceProbeState.SOURCE_NOT_FOUND, ref, providerId,
                            "The provider does not contain the requested item source.")
                    : ItemSourceProbeResult.ready(ref, providerId);
        } catch (LinkageError exception) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.INCOMPATIBLE, ref, providerId, detail(exception));
        } catch (RuntimeException exception) {
            return ItemSourceProbeResult.of(ItemSourceProbeState.RESOLUTION_ERROR, ref, providerId, detail(exception));
        }
    }

    @Override
    public ItemSourceRef identify(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !isOperational()) {
            return null;
        }
        String identifier = accessor.identifyIdentifier(itemStack);
        return Texts.isBlank(identifier) ? null : ItemSourceRef.orNull(kind(), identifier);
    }

    @Override
    public ItemStack create(ItemSourceRef ref, int amount) {
        if (!supports(ref) || !isOperational()) {
            return null;
        }
        int normalizedAmount = Math.max(1, amount);
        ItemStack itemStack = accessor.createItem(ref.identifier(), normalizedAmount);
        if (itemStack == null) {
            return null;
        }
        ItemStack cloned = itemStack.clone();
        cloned.setAmount(normalizedAmount);
        return cloned;
    }

    @Override
    public String displayName(ItemSourceRef ref) {
        if (!supports(ref) || !isOperational()) {
            return null;
        }
        String displayName = accessor.displayName(ref.identifier());
        return Texts.isBlank(displayName) ? null : displayName;
    }

    @Override
    public LifecycleStatus bootstrap() {
        return refresh(false);
    }

    @Override
    public LifecycleStatus onProviderReady(boolean itemsLoaded) {
        return refresh(itemsLoaded);
    }

    @Override
    public void onProviderDisabled() {
        loaded.set(false);
        accessor.reset();
    }

    private LifecycleStatus refresh(boolean loadedSignal) {
        if (!pluginAvailability.isPluginEnabled(providerPluginName())) {
            loaded.set(false);
            accessor.reset();
            return LifecycleStatus.absent();
        }
        if (!accessor.ensureAvailable()) {
            loaded.set(false);
            return LifecycleStatus.incompatible(accessor.failureReason());
        }
        if (loadedSignal) {
            loaded.set(true);
        } else if (!loaded.get()) {
            loaded.compareAndSet(false, accessor.detectLoaded());
        }
        return loaded.get() ? LifecycleStatus.ready() : LifecycleStatus.waiting(waitingDetail());
    }

    private static String detail(Throwable throwable) {
        if (throwable == null) {
            return "Unknown resolution failure";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    interface Accessor {

        boolean ensureAvailable();

        String failureReason();

        boolean detectLoaded();

        String identifyIdentifier(ItemStack itemStack);

        ItemStack createItem(String identifier, int amount);

        default String displayName(String identifier) {
            return null;
        }

        void reset();
    }
}

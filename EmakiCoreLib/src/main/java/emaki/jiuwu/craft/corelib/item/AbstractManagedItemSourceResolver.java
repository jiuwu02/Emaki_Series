package emaki.jiuwu.craft.corelib.item;

import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

abstract class AbstractManagedItemSourceResolver<A extends AbstractManagedItemSourceResolver.Accessor>
        implements ManagedItemSourceResolver {

    private final PluginAvailability pluginAvailability;
    private final A accessor;
    private final AtomicBoolean loaded = new AtomicBoolean();

    protected AbstractManagedItemSourceResolver(PluginAvailability pluginAvailability, A accessor) {
        this.pluginAvailability = pluginAvailability == null ? PluginAvailability.BUKKIT : pluginAvailability;
        this.accessor = accessor;
    }

    protected abstract ItemSourceType sourceType();

    protected abstract String waitingDetail();

    protected final A accessor() {
        return accessor;
    }

    protected final boolean isOperational() {
        return loaded.get() && pluginAvailability.isPluginEnabled(pluginName()) && accessor.ensureAvailable();
    }

    @Override
    public boolean supports(ItemSource source) {
        return source != null && source.getType() == sourceType();
    }

    @Override
    public boolean isAvailable(ItemSource source) {
        return supports(source) && isOperational();
    }

    @Override
    public ItemSource identify(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !isOperational()) {
            return null;
        }
        String identifier = accessor.identifyIdentifier(itemStack);
        return Texts.isBlank(identifier) ? null : new ItemSource(sourceType(), identifier);
    }

    @Override
    public ItemStack create(ItemSource source, int amount) {
        if (!supports(source) || !isOperational()) {
            return null;
        }
        int normalizedAmount = Math.max(1, amount);
        ItemStack itemStack = accessor.createItem(source.getIdentifier(), normalizedAmount);
        if (itemStack == null) {
            return null;
        }
        ItemStack cloned = itemStack.clone();
        cloned.setAmount(normalizedAmount);
        return cloned;
    }

    @Override
    public Status bootstrap() {
        return refresh(false);
    }

    @Override
    public Status onPluginEnabled() {
        return refresh(false);
    }

    @Override
    public Status onItemsLoaded() {
        return refresh(true);
    }

    @Override
    public void onPluginDisabled() {
        loaded.set(false);
        accessor.reset();
    }

    private Status refresh(boolean loadedSignal) {
        if (!pluginAvailability.isPluginEnabled(pluginName())) {
            loaded.set(false);
            accessor.reset();
            return new Status(State.ABSENT, "");
        }
        if (!accessor.ensureAvailable()) {
            loaded.set(false);
            return new Status(State.INCOMPATIBLE, accessor.failureReason());
        }
        if (loadedSignal) {
            loaded.set(true);
        } else if (!loaded.get()) {
            loaded.compareAndSet(false, accessor.detectLoaded());
        }
        return loaded.get()
                ? new Status(State.READY, "")
                : new Status(State.WAITING, waitingDetail());
    }

    interface Accessor {

        boolean ensureAvailable();

        String failureReason();

        boolean detectLoaded();

        String identifyIdentifier(ItemStack itemStack);

        ItemStack createItem(String identifier, int amount);

        void reset();
    }
}

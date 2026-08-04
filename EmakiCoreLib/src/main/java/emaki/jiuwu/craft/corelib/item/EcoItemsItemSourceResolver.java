package emaki.jiuwu.craft.corelib.item;

import java.util.Set;

import org.bukkit.inventory.ItemStack;

import com.willfp.ecoitems.items.EcoItem;
import com.willfp.ecoitems.items.EcoItems;
import com.willfp.ecoitems.items.ItemUtilsKt;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.text.Texts;




































final class EcoItemsItemSourceResolver
        extends AbstractManagedItemSourceProvider<EcoItemsItemSourceResolver.DirectAccessor> {

    private static final String PLUGIN_NAME = "EcoItems";

    EcoItemsItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new DirectAccessor());
    }

    EcoItemsItemSourceResolver(PluginAvailability pluginAvailability, DirectAccessor accessor) {
        super(pluginAvailability, accessor == null ? new DirectAccessor() : accessor);
    }

    @Override
    public ItemSourceKind kind() {
        return ItemSourceKind.ECOITEMS;
    }

    @Override
    public Set<String> shorthandPrefixes() {
        return Set.of("ecoitems-", "eci-");
    }

    @Override
    public int priority() {
        return 95;
    }

    @Override
    public String providerPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected String waitingDetail() {
        return "EcoItems items are not loaded yet.";
    }

    static final class DirectAccessor implements AbstractManagedItemSourceProvider.Accessor {

        private String failureReason = "";

        @Override
        public boolean ensureAvailable() {
            try {
                EcoItems.INSTANCE.values();
                failureReason = "";
                return true;
            } catch (RuntimeException | LinkageError exception) {
                failureReason = describe(exception);
                return false;
            }
        }

        @Override
        public String failureReason() {
            return failureReason;
        }

        @Override
        public boolean detectLoaded() {
            try {
                return EcoItems.INSTANCE.values() != null;
            } catch (RuntimeException | LinkageError exception) {
                failureReason = describe(exception);
                return false;
            }
        }

        @Override
        public String identifyIdentifier(ItemStack itemStack) {
            if (itemStack == null) {
                return "";
            }
            try {
                EcoItem ecoItem = ItemUtilsKt.getEcoItem(itemStack);
                return ecoItem == null ? "" : Texts.trim(ecoItem.getID());
            } catch (RuntimeException | LinkageError exception) {
                return "";
            }
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            if (Texts.isBlank(identifier)) {
                return null;
            }
            try {
                EcoItem ecoItem = EcoItems.INSTANCE.getByID(identifier);
                return ecoItem == null ? null : ecoItem.getItemStack();
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public String displayName(String identifier) {
            if (Texts.isBlank(identifier)) {
                return null;
            }
            try {
                EcoItem ecoItem = EcoItems.INSTANCE.getByID(identifier);
                if (ecoItem == null) {
                    return null;
                }
                String displayName = ecoItem.getDisplayName();
                return Texts.isBlank(displayName) ? null : displayName;
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public void reset() {
            failureReason = "";
        }

        private String describe(Throwable throwable) {
            return throwable.getMessage() == null
                    ? throwable.getClass().getSimpleName()
                    : throwable.getMessage();
        }
    }
}

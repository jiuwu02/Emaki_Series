package emaki.jiuwu.craft.item.service;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceResolver;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.api.EmakiItemApi;

public final class EmakiItemSourceResolver implements ItemSourceResolver {

    private final EmakiItemApi api;

    public EmakiItemSourceResolver(EmakiItemApi api) {
        this.api = api;
    }

    @Override
    public String id() {
        return "emakiitem";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(ItemSource source) {
        return source != null && source.getType() == ItemSourceType.EMAKIITEM;
    }

    @Override
    public boolean isAvailable(ItemSource source) {
        return supports(source) && api.exists(source.getIdentifier());
    }

    @Override
    public ItemSource identify(ItemStack itemStack) {
        String id = api.identify(itemStack);
        return Texts.isBlank(id) ? null : new ItemSource(ItemSourceType.EMAKIITEM, id);
    }

    @Override
    public ItemStack create(ItemSource source, int amount) {
        return supports(source) ? api.create(source.getIdentifier(), amount) : null;
    }

    @Override
    public String displayName(ItemSource source) {
        return supports(source) ? api.displayName(source.getIdentifier()) : null;
    }
}

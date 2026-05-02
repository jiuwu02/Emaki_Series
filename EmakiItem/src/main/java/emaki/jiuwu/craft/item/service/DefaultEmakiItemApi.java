package emaki.jiuwu.craft.item.service;

import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.api.EmakiItemApi;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;

public final class DefaultEmakiItemApi implements EmakiItemApi {

    private final EmakiItemLoader loader;
    private final EmakiItemFactory factory;
    private final EmakiItemIdentifier identifier;

    public DefaultEmakiItemApi(EmakiItemLoader loader, EmakiItemFactory factory, EmakiItemIdentifier identifier) {
        this.loader = loader;
        this.factory = factory;
        this.identifier = identifier;
    }

    @Override
    public boolean exists(String id) {
        return loader.get(id) != null;
    }

    @Override
    public ItemStack create(String id, int amount) {
        return factory.create(id, amount);
    }

    @Override
    public String identify(ItemStack itemStack) {
        return identifier.identify(itemStack);
    }

    @Override
    public Set<String> definitionIds() {
        return loader.all().keySet();
    }

    @Override
    public String displayName(String id) {
        ItemStack itemStack = create(id, 1);
        if (itemStack == null) {
            return "";
        }
        String text = ItemTextBridge.effectiveNameText(itemStack);
        return Texts.isBlank(text) ? MiniMessages.serialize(ItemTextBridge.effectiveName(itemStack)) : text;
    }
}

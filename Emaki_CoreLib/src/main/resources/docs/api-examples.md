# Emaki CoreLib API Examples

## Item Source

```java
ItemSource source = ItemSourceUtil.parse("ni-flame_sword");
ItemSourceUtil.registerParser(text -> text.startsWith("myplugin-")
    ? new ItemSource(ItemSourceType.VANILLA, text.substring("myplugin-".length()))
    : null);
```

## GUI Components

```java
ItemComponentParser.ItemComponents components = ItemComponentParser.parse(Map.of(
    "display_name", "<gold>确认锻造</gold>",
    "lore", List.of("<gray>点击执行锻造</gray>"),
    "item_model", "emaki:forge/confirm"
));
ItemComponentParser.apply(itemMeta, components);
```

## Sound

```java
SoundParser.SoundDefinition sound = SoundParser.parse(Map.of(
    "sound", "block.anvil.use",
    "volume", 1.0,
    "pitch", 0.9
));
```

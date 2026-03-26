# Emaki CoreLib - GUI 系统

## 概述

GUI 系统是 Emaki CoreLib 的核心模块之一，提供了一套完整的 GUI（图形用户界面）解决方案。该系统采用模板化设计，支持动态渲染、事件处理和会话管理，让开发者能够快速构建复杂的交互界面。

---

## 核心组件

### 架构图

```
GuiService (服务管理器)
    │
    ├── GuiTemplate (模板定义)
    │       ├── id: 模板标识
    │       ├── title: 界面标题
    │       ├── rows: 行数 (1-6)
    │       └── slots: 槽位映射
    │
    ├── GuiSession (会话实例)
    │       ├── viewer: 查看玩家
    │       ├── inventory: 背包实例
    │       ├── replacements: 变量替换
    │       └── handler: 事件处理器
    │
    └── GuiTemplateParser (模板解析器)
            └── 从 YAML 配置解析模板
```

---

## GuiService - GUI 服务管理器

### 功能说明

`GuiService` 是 GUI 系统的核心服务类，负责：
- 管理 GUI 会话的生命周期
- 处理库存点击、拖拽、关闭事件
- 协调模板、渲染器和事件处理器

### API 接口

#### 构造方法

```java
public GuiService(JavaPlugin plugin)
```

**参数**：
- `plugin` - 插件实例，用于事件注册和调度

**示例**：

```java
GuiService guiService = new GuiService(myPlugin);
Bukkit.getPluginManager().registerEvents(guiService, myPlugin);
```

---

#### open() - 打开 GUI

```java
public GuiSession open(GuiOpenRequest request)
```

**参数**：
- `request` - GUI 打开请求对象

**返回值**：
- 成功返回 `GuiSession` 实例
- 失败返回 `null`

**示例**：

```java
GuiSession session = guiService.open(new GuiOpenRequest(
    plugin,                    // 插件实例
    player,                    // 目标玩家
    template,                  // GUI 模板
    Map.of("title", "商店"),   // 标题变量替换
    itemFactory,               // 物品工厂
    renderer,                  // 渲染器 (可为 null)
    handler                    // 事件处理器
));
```

---

#### getSession() - 获取会话

```java
public GuiSession getSession(UUID playerId)
```

**参数**：
- `playerId` - 玩家 UUID

**返回值**：
- 存在则返回 `GuiSession`
- 不存在返回 `null`

---

#### close() - 关闭 GUI

```java
public void close(UUID playerId)
```

**参数**：
- `playerId` - 玩家 UUID

**说明**：关闭指定玩家的 GUI 会话

---

## GuiTemplate - GUI 模板

### 模板结构

```java
public final class GuiTemplate {
    private final String id;                      // 模板ID
    private final String title;                   // 界面标题
    private final int rows;                       // 行数 (1-6)
    private final Map<String, GuiSlot> slots;     // 槽位映射
    private final Map<Integer, ResolvedSlot> resolvedSlots;  // 解析后的槽位
}
```

### 从 YAML 配置创建模板

```yaml
id: "example_gui"
title: "<dark_gray>示例界面 - {title}</dark_gray>"
rows: 3

slots:
  border:
    slots: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26]
    item: "GRAY_STAINED_GLASS_PANE"
    display_name: "<gray>"

  center_button:
    slots: [13]
    action: "confirm"
    item: "EMERALD"
    display_name: "<green>确认"
    lore:
      - "<gray>点击确认操作"
    click:
      sound: "ui.button.click"
      volume: 1.0
      pitch: 1.0
```

### 解析模板

```java
ConfigurationSection section = config.getConfigurationSection("gui.example");
GuiTemplate template = GuiTemplateParser.parse(section);
```

---

## GuiSlot - 槽位定义

### 槽位属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `key` | String | 槽位标识键 |
| `slots` | List\<Integer\> | 占用的槽位索引列表 |
| `action` | String | 动作标识 (用于事件处理) |
| `item` | String | 物品简写 |
| `components` | ItemComponents | 物品组件 (名称、描述等) |
| `sounds` | Map | 点击音效配置 |

### 槽位索引规则

槽位索引从 0 开始，按从左到右、从上到下的顺序编号：

```
行1: [0]  [1]  [2]  [3]  [4]  [5]  [6]  [7]  [8]
行2: [9]  [10] [11] [12] [13] [14] [15] [16] [17]
行3: [18] [19] [20] [21] [22] [23] [24] [25] [26]
...
```

### YAML 配置示例

```yaml
slots:
  # 装饰性边框
  border:
    slots: [0, 1, 2, 3, 4, 5, 6, 7, 8]
    item: "BLACK_STAINED_GLASS_PANE"
    display_name: "<dark_gray>"

  # 功能按钮
  confirm_button:
    slots: [22]
    action: "confirm"
    item: "LIME_WOOL"
    display_name: "<green>确认"
    lore:
      - "<gray>点击执行操作"
      - "<yellow>当前状态: {status}"
    click:
      sound: "block.note_block.pling"
      volume: 1.0
      pitch: 1.5
```

---

## GuiSession - GUI 会话

### 会话生命周期

```
创建会话 → 打开界面 → 交互事件 → 关闭界面 → 清理会话
```

### 核心方法

#### open() - 打开界面

```java
public void open()
```

刷新界面内容并打开给玩家。

#### refresh() - 刷新界面

```java
public void refresh()
```

重新渲染所有槽位，保持界面打开状态。

#### 替换变量

```java
public void replaceReplacements(Map<String, ?> values)
public void putReplacement(String key, Object value)
```

**示例**：

```java
session.putReplacement("count", 10);
session.putReplacement("status", "就绪");
session.refresh();
```

---

## GuiSessionHandler - 事件处理器

### 接口定义

```java
public interface GuiSessionHandler {
    // 点击 GUI 槽位
    default void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {}
    
    // 点击玩家背包
    default void onPlayerInventoryClick(GuiSession session, InventoryClickEvent event) {}
    
    // 拖拽物品
    default void onDrag(GuiSession session, InventoryDragEvent event) {}
    
    // 关闭界面
    default void onClose(GuiSession session, InventoryCloseEvent event) {}
}
```

### 实现示例

```java
public class MyGuiHandler implements GuiSessionHandler {
    
    @Override
    public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
        if (slot == null) return;
        
        String action = slot.definition().action();
        Player player = session.viewer();
        
        switch (action) {
            case "confirm" -> handleConfirm(player, session);
            case "cancel" -> player.closeInventory();
            case "next_page" -> handleNextPage(player, session);
        }
    }
    
    @Override
    public void onClose(GuiSession session, InventoryCloseEvent event) {
        // 清理资源
        cleanupSession(session);
    }
}
```

---

## GuiOpenRequest - 打开请求

### 构建请求

```java
public record GuiOpenRequest(
    Plugin owner,                           // 插件实例
    Player viewer,                          // 目标玩家
    GuiTemplate template,                   // GUI 模板
    Map<String, ?> replacements,            // 标题变量替换
    GuiItemBuilder.ItemFactory itemFactory, // 物品工厂
    GuiRenderer renderer,                   // 自定义渲染器
    GuiSessionHandler handler               // 事件处理器
)
```

### 使用示例

```java
GuiOpenRequest request = new GuiOpenRequest(
    plugin,
    player,
    template,
    Map.of("player", player.getName()),
    source -> itemIdentifierService.createItem(source, 1),
    (session, slot) -> customRender(slot),
    new MyGuiHandler()
);

GuiSession session = guiService.open(request);
```

---

## GuiRenderer - 自定义渲染器

### 接口定义

```java
@FunctionalInterface
public interface GuiRenderer {
    ItemStack render(GuiSession session, GuiTemplate.ResolvedSlot slot);
}
```

### 使用场景

- 动态内容渲染（如玩家数据、实时状态）
- 条件性显示（如权限检查后显示不同物品）
- 复杂物品构建逻辑

### 实现示例

```java
GuiRenderer renderer = (session, resolvedSlot) -> {
    String action = resolvedSlot.definition().action();
    
    if ("player_balance".equals(action)) {
        double balance = economy.getBalance(session.viewer());
        return GuiItemBuilder.build(
            "GOLD_INGOT",
            new ItemComponentParser.ItemComponents(
                "<gold>余额: " + balance,
                true,
                List.of("<gray>点击查看详情"),
                null, null, Map.of(), List.of()
            ),
            1,
            Map.of(),
            itemFactory
        );
    }
    
    return null;  // 返回 null 使用默认渲染
};
```

---

## 音效系统

### 音效配置格式

```yaml
slots:
  button:
    slots: [13]
    item: "DIAMOND"
    display_name: "<aqua>点击我"
    # 单击音效
    click:
      sound: "ui.button.click"
      volume: 1.0
      pitch: 1.0
    # 或分别配置左右键
    leftclick:
      sound: "block.note_block.pling"
      volume: 0.8
      pitch: 1.2
    rightclick:
      sound: "block.anvil.use"
      volume: 1.0
      pitch: 0.8
```

### 音效格式说明

音效字符串格式：`sound-volume-pitch`

```yaml
click: "ui.button.click-1.0-1.0"
```

等价于：

```yaml
click:
  sound: "ui.button.click"
  volume: 1.0
  pitch: 1.0
```

---

## 完整示例

### 创建商店界面

```java
public class ShopGui {
    
    private final GuiService guiService;
    private final GuiTemplate template;
    
    public void openShop(Player player) {
        GuiOpenRequest request = new GuiOpenRequest(
            plugin,
            player,
            template,
            Map.of("shop_name", "钻石商店"),
            this::createItem,
            this::renderSlot,
            new ShopHandler()
        );
        
        guiService.open(request);
    }
    
    private ItemStack renderSlot(GuiSession session, GuiTemplate.ResolvedSlot slot) {
        String action = slot.definition().action();
        
        if ("shop_item".equals(action)) {
            int index = slot.slotIndex();
            ShopItem item = shopItems.get(index);
            if (item == null) return null;
            
            return GuiItemBuilder.build(
                item.icon(),
                new ItemComponentParser.ItemComponents(
                    item.displayName(),
                    true,
                    List.of(
                        "<gray>价格: <gold>" + item.price(),
                        "<yellow>点击购买"
                    ),
                    null, null, Map.of(), List.of()
                ),
                1,
                Map.of(),
                itemFactory
            );
        }
        
        return null;
    }
    
    private class ShopHandler implements GuiSessionHandler {
        @Override
        public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            if (slot == null || !"shop_item".equals(slot.definition().action())) return;
            
            int index = slot.slotIndex();
            ShopItem item = shopItems.get(index);
            if (item != null) {
                purchaseItem(session.viewer(), item);
                session.refresh();
            }
        }
    }
}
```

---

## 最佳实践

### 1. 模板复用

将常用 GUI 模板定义为独立配置文件，便于复用：

```yaml
# templates/confirm_dialog.yml
id: "confirm_dialog"
title: "<dark_gray>确认操作</dark_gray>"
rows: 3
slots:
  message:
    slots: [10, 11, 12, 13, 14, 15, 16]
    item: "PAPER"
    display_name: "{message}"
  confirm:
    slots: [12]
    action: "confirm"
    item: "LIME_WOOL"
    display_name: "<green>确认"
  cancel:
    slots: [14]
    action: "cancel"
    item: "RED_WOOL"
    display_name: "<red>取消"
```

### 2. 会话状态管理

使用会话存储临时数据：

```java
public class MySession implements GuiSessionHandler {
    private final Map<String, Object> state = new HashMap<>();
    
    @Override
    public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
        state.put("last_click", System.currentTimeMillis());
        state.put("selected_item", slot.slotIndex());
    }
}
```

### 3. 异步操作处理

避免在 GUI 事件中执行耗时操作：

```java
@Override
public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
    Player player = session.viewer();
    
    // 异步查询数据库
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        Data data = database.query(player.getUniqueId());
        
        // 回到主线程更新 GUI
        Bukkit.getScheduler().runTask(plugin, () -> {
            session.putReplacement("data", data);
            session.refresh();
        });
    });
}
```

---

## 下一步

- [物品源系统](./04-物品源系统.md) - 了解物品创建机制
- [API文档](./08-API接口文档.md) - 查看完整 API 参考

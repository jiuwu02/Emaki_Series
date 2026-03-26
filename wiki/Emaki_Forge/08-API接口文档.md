# Emaki Forge - API 接口文档

## 概述

本文档详细描述了 Emaki Forge 的主要 API 接口，供开发者进行插件集成和扩展开发使用。

---

## 目录

1. [ForgeService - 锻造服务](#forgeservice---锻造服务)
2. [ForgeGuiService - 锻造GUI服务](#forgeguiservice---锻造gui服务)
3. [RecipeBookGuiService - 配方图鉴服务](#recipebookguiservice---配方图鉴服务)
4. [ItemIdentifierService - 物品识别服务](#itemidentifierservice---物品识别服务)
5. [Blueprint - 图纸模型](#blueprint---图纸模型)
6. [ForgeMaterial - 材料模型](#forgematerial---材料模型)
7. [Recipe - 配方模型](#recipe---配方模型)
8. [PlayerDataStore - 玩家数据存储](#playerdatastore---玩家数据存储)
9. [MessageService - 消息服务](#messageservice---消息服务)
10. [操作系统集成](#操作系统集成)

---

## ForgeService - 锻造服务

### 类定义

```java
package emaki.jiuwu.craft.forge.service;

public final class ForgeService
```

### 核心方法

#### findMatchingRecipe()

```java
public RecipeMatch findMatchingRecipe(Player player, GuiItems guiItems)
```

查找匹配当前 GUI 物品的配方。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `player` | Player | 玩家实例 |
| `guiItems` | GuiItems | GUI 中的物品集合 |

**返回值**：`RecipeMatch` 记录类

```java
public record RecipeMatch(Recipe recipe, String errorKey, Map<String, Object> replacements)
```

| 字段 | 说明 |
|------|------|
| `recipe` | 匹配的配方（未找到时为 null） |
| `errorKey` | 错误消息键 |
| `replacements` | 错误消息变量 |

**示例**：

```java
ForgeService forgeService = plugin.forgeService();
ForgeService.GuiItems guiItems = session.toGuiItems();

ForgeService.RecipeMatch match = forgeService.findMatchingRecipe(player, guiItems);
if (match.recipe() != null) {
    Recipe recipe = match.recipe();
} else {
    String errorKey = match.errorKey();
}
```

---

#### canForge()

```java
public ValidationResult canForge(Player player, Recipe recipe, GuiItems guiItems)
```

检查是否可以执行锻造。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `player` | Player | 玩家实例 |
| `recipe` | Recipe | 配方实例 |
| `guiItems` | GuiItems | GUI 中的物品集合 |

**返回值**：`ValidationResult` 记录类

```java
public record ValidationResult(boolean success, String errorKey, Map<String, Object> replacements)
```

**示例**：

```java
ForgeService.ValidationResult result = forgeService.canForge(player, recipe, guiItems);
if (result.success()) {
} else {
    plugin.messageService().send(player, result.errorKey(), result.replacements());
}
```

---

#### executeForge()

```java
public ForgeResult executeForge(Player player, Recipe recipe, GuiItems guiItems)
```

执行锻造操作。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `player` | Player | 玩家实例 |
| `recipe` | Recipe | 配方实例 |
| `guiItems` | GuiItems | GUI 中的物品集合 |

**返回值**：`ForgeResult` 实例

```java
public static final class ForgeResult {
    public boolean success();           
    public String errorKey();           
    public Map<String, Object> replacements();  
    public ItemStack resultItem();      
    public String quality();            
    public double multiplier();         
    public String operationFailureReason();
}
```

**示例**：

```java
ForgeService.ForgeResult result = forgeService.executeForge(player, recipe, guiItems);
if (result.success()) {
    ItemStack item = result.resultItem();
    String quality = result.quality();
    double multiplier = result.multiplier();
    
    player.sendMessage("锻造成功！品质: " + quality);
} else {
    plugin.messageService().send(player, result.errorKey(), result.replacements());
}
```

---

### GuiItems 记录类

```java
public record GuiItems(
    ItemStack targetItem,
    Map<Integer, ItemStack> blueprints,
    Map<Integer, ItemStack> requiredMaterials,
    Map<Integer, ItemStack> optionalMaterials
)
```

| 字段 | 说明 |
|------|------|
| `targetItem` | 目标物品 |
| `blueprints` | 图纸物品映射（槽位 → 物品） |
| `requiredMaterials` | 必需材料映射 |
| `optionalMaterials` | 可选材料映射 |

---

## ForgeGuiService - 锻造GUI服务

### 类定义

```java
package emaki.jiuwu.craft.forge.service;

public final class ForgeGuiService
```

### 核心方法

#### openForgeGui()

```java
public boolean openForgeGui(Player player, Recipe recipe)
```

打开指定配方的锻造界面。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `player` | Player | 玩家实例 |
| `recipe` | Recipe | 配方实例（可为 null） |

**返回值**：`boolean` 是否成功打开

---

#### openGeneralForgeGui()

```java
public boolean openGeneralForgeGui(Player player)
```

打开通用锻造界面（自动匹配配方）。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `player` | Player | 玩家实例 |

**返回值**：`boolean` 是否成功打开

---

#### getSession()

```java
public ForgeGuiSession getSession(Player player)
```

获取玩家当前的锻造会话。

**返回值**：`ForgeGuiSession` 或 `null`

---

#### clearAllSessions()

```java
public void clearAllSessions()
```

清除所有锻造会话。

---

### ForgeGuiSession 类

```java
public static final class ForgeGuiSession {
    public Player player();                    
    public String templateId();                
    public Recipe recipe();                    
    public Recipe previewRecipe();             
    public GuiSession guiSession();            
    public Map<Integer, ItemStack> blueprintItems();   
    public ItemStack targetItem();             
    public Map<Integer, ItemStack> requiredMaterialItems();  
    public Map<Integer, ItemStack> optionalMaterialItems();  
    public int currentCapacity();              
    public int maxCapacity();                  
    public boolean forgeCompleted();           
    
    public GuiItems toGuiItems();              
}
```

---

## RecipeBookGuiService - 配方图鉴服务

### 类定义

```java
package emaki.jiuwu.craft.forge.service;

public final class RecipeBookGuiService
```

### 核心方法

#### openRecipeBook()

```java
public boolean openRecipeBook(Player player)
public boolean openRecipeBook(Player player, int page)
```

打开配方图鉴界面。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `player` | Player | 玩家实例 |
| `page` | int | 页码（从 0 开始） |

**返回值**：`boolean` 是否成功打开

---

#### isRecipeBookInventory()

```java
public boolean isRecipeBookInventory(Player player)
```

检查玩家是否正在查看配方图鉴。

---

#### clearAllBooks()

```java
public void clearAllBooks()
```

清除所有配方图鉴会话。

---

## ItemIdentifierService - 物品识别服务

### 类定义

```java
package emaki.jiuwu.craft.forge.service;

public final class ItemIdentifierService
```

### 核心方法

#### identifyItem()

```java
public ItemSource identifyItem(ItemStack itemStack)
```

识别物品的来源类型。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `itemStack` | ItemStack | 物品实例 |

**返回值**：`ItemSource` 物品源

**示例**：

```java
ItemIdentifierService identifier = plugin.itemIdentifierService();
ItemStack item = player.getInventory().getItemInMainHand();

ItemSource source = identifier.identifyItem(item);
if (source != null) {
    System.out.println("类型: " + source.getType());
    System.out.println("标识: " + source.getIdentifier());
}
```

---

#### matchesSource()

```java
public boolean matchesSource(ItemStack itemStack, ItemSource source)
```

检查物品是否匹配指定源。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `itemStack` | ItemStack | 物品实例 |
| `source` | ItemSource | 目标物品源 |

**返回值**：`boolean` 是否匹配

---

#### createItem()

```java
public ItemStack createItem(ItemSource source, int amount)
```

根据物品源创建物品。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `source` | ItemSource | 物品源 |
| `amount` | int | 数量 |

**返回值**：`ItemStack` 或 `null`

---

## Blueprint - 图纸模型

### 类定义

```java
package emaki.jiuwu.craft.forge.model;

public final class Blueprint
```

### 属性访问器

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `id()` | String | 图纸ID |
| `displayName()` | String | 显示名称 |
| `description()` | List\<String\> | 描述文本 |
| `source()` | ItemSource | 物品源 |
| `tags()` | List\<String\> | 标签列表 |
| `forgeCapacity()` | int | 锻造容量 |

### 实例方法

#### hasTag()

```java
public boolean hasTag(String tag)
```

检查是否拥有指定标签。

---

#### matchesSelector()

```java
public boolean matchesSelector(Map<String, Object> selector)
```

检查是否匹配选择器。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `selector` | Map\<String, Object\> | 选择器配置 |

**选择器格式**：

```java
Map<String, Object> selector = Map.of(
    "kind", "id",      // 或 "tag"
    "value", "weapon_sword"
);
```

---

### 静态方法

#### fromConfig()

```java
public static Blueprint fromConfig(ConfigurationSection section)
```

从配置节点创建图纸实例。

---

## ForgeMaterial - 材料模型

### 类定义

```java
package emaki.jiuwu.craft.forge.model;

public final class ForgeMaterial
```

### 属性访问器

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `id()` | String | 材料ID |
| `displayName()` | String | 显示名称 |
| `description()` | List\<String\> | 描述文本 |
| `source()` | ItemSource | 物品源 |
| `capacityCost()` | int | 容量消耗 |
| `priority()` | int | 优先级 |
| `effects()` | List\<MaterialEffect\> | 效果列表 |

### 实例方法

#### statContributions()

```java
public Map<String, Double> statContributions()
```

获取属性贡献映射。

---

#### nameModifications()

```java
public List<Map<String, Object>> nameModifications()
```

获取名称修改操作列表。

---

#### loreOperations()

```java
public List<Map<String, Object>> loreOperations()
```

获取描述操作列表。

---

## Recipe - 配方模型

### 类定义

```java
package emaki.jiuwu.craft.forge.model;

public final class Recipe
```

### 属性访问器

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `id()` | String | 配方ID |
| `displayName()` | String | 显示名称 |
| `targetItemSource()` | ItemSource | 目标物品源 |
| `forgeCapacity()` | int | 锻造容量 |
| `blueprintRequirements()` | List\<BlueprintRequirement\> | 图纸需求 |
| `requiredMaterials()` | List\<RequiredMaterial\> | 必需材料 |
| `optionalMaterials()` | OptionalMaterialsConfig | 可选材料配置 |
| `conditionType()` | String | 条件类型 |
| `conditions()` | List\<String\> | 条件列表 |
| `quality()` | QualityConfig | 品质配置 |
| `gui()` | GuiConfig | GUI配置 |
| `result()` | ResultConfig | 结果配置 |
| `permission()` | String | 权限节点 |
| `operationPhases()` | OperationPhases | 操作阶段配置 |

### 辅助方法

#### requiresPermission()

```java
public boolean requiresPermission()
```

检查是否需要权限。

---

## PlayerDataStore - 玩家数据存储

### 类定义

```java
package emaki.jiuwu.craft.forge.loader;

public final class PlayerDataStore
```

### 核心方法

#### recordCraft()

```java
public void recordCraft(UUID playerId, String recipeId)
```

记录玩家锻造历史。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `playerId` | UUID | 玩家UUID |
| `recipeId` | String | 配方ID |

---

#### hasCrafted()

```java
public boolean hasCrafted(UUID playerId, String recipeId)
```

检查玩家是否锻造过指定配方。

---

#### getCraftCount()

```java
public int getCraftCount(UUID playerId, String recipeId)
```

获取玩家锻造指定配方的次数。

---

#### save()

```java
public void save(UUID playerId)
```

保存指定玩家的数据。

---

#### saveAll()

```java
public int saveAll()
```

保存所有玩家数据。

**返回值**：保存的玩家数量

---

## MessageService - 消息服务

### 类定义

```java
package emaki.jiuwu.craft.forge.service;

public final class MessageService
```

### 核心方法

#### send()

```java
public void send(CommandSender sender, String key)
public void send(CommandSender sender, String key, Map<String, Object> replacements)
```

发送消息给目标。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `sender` | CommandSender | 消息接收者 |
| `key` | String | 消息键 |
| `replacements` | Map\<String, Object\> | 变量替换 |

---

#### sendRaw()

```java
public void sendRaw(CommandSender sender, Component component)
public void sendRaw(CommandSender sender, String text)
```

发送原始消息（不经过语言文件）。

---

#### message()

```java
public String message(String key)
public String message(String key, Map<String, Object> replacements)
```

获取格式化后的消息文本。

---

## 操作系统集成

### 概述

Emaki Forge 深度集成了 Emaki CoreLib 的操作系统，允许在锻造流程的各个阶段执行自定义操作。

### 配方操作配置

在配方配置中可以定义多个操作阶段：

```yaml
recipes:
  flame_sword:
    id: flame_sword
    display_name: "<gold>烈焰之剑</gold>"
    
    operations:
      pre_forge:
        - 'send_message text="<yellow>开始锻造烈焰之剑...</yellow>"'
        - 'play_sound sound=block.anvil.use volume=1.0 pitch=0.8'
      
      post_forge:
        - 'send_message text="<green>锻造成功！</green>"'
        - 'play_sound sound=entity.experience_orb.pickup volume=0.8 pitch=1.2'
        - 'give_exp amount=50'
        - '@if="%forge_quality% == legendary" broadcast_message text="<gold>%player_name% 锻造出了传说品质的烈焰之剑！"'
      
      on_failure:
        - 'send_message text="<red>锻造失败...</red>"'
        - 'play_sound sound=block.anvil.break volume=1.0 pitch=1.0'
```

### 操作阶段

| 阶段 | 触发时机 | 说明 |
|------|---------|------|
| `pre_forge` | 锻造开始前 | 执行前置操作（如提示、音效） |
| `post_forge` | 锻造成功后 | 执行后置操作（如奖励、通知） |
| `on_failure` | 锻造失败时 | 执行失败处理（如返还材料） |

### 可用占位符

在操作中可以使用以下占位符：

| 占位符 | 说明 |
|--------|------|
| `%player_name%` | 玩家名称 |
| `%forge_recipe_id%` | 配方ID |
| `%forge_recipe_name%` | 配方显示名称 |
| `%forge_quality%` | 品质名称 |
| `%forge_multiplier%` | 品质倍率 |
| `%forge_result_item_name%` | 结果物品名称 |

### 通过 API 执行操作

```java
public class CustomForgeHandler {
    
    private final EmakiForgePlugin forgePlugin;
    private final OperationExecutor operationExecutor;
    
    public void executeCustomOperations(Player player, Recipe recipe, String phase) {
        OperationPhases phases = recipe.operationPhases();
        List<String> operations = phases.get(phase);
        
        if (operations == null || operations.isEmpty()) {
            return;
        }
        
        OperationContext context = OperationContext.create(
            forgePlugin,
            player,
            phase,
            false,
            false
        ).withPlaceholders(Map.of(
            "forge_recipe_id", recipe.id(),
            "forge_recipe_name", recipe.displayName()
        ));
        
        operationExecutor.executeAll(context, operations, true)
            .thenAccept(batch -> {
                if (!batch.success()) {
                    OperationStepResult failure = batch.firstFailure();
                    forgePlugin.getLogger().warning(
                        "操作执行失败: " + failure.result().errorMessage()
                    );
                }
            });
    }
}
```

---

## 获取插件实例

### 通过 Bukkit

```java
EmakiForgePlugin plugin = (EmakiForgePlugin) Bukkit.getPluginManager().getPlugin("Emaki_Forge");
if (plugin != null && plugin.isEnabled()) {
    ForgeService forgeService = plugin.forgeService();
}
```

### 通过依赖注入

如果您的插件依赖 Emaki Forge，可以在启动时获取实例：

```java
public class MyPlugin extends JavaPlugin {
    
    private EmakiForgePlugin forgePlugin;
    
    @Override
    public void onEnable() {
        Plugin plugin = getServer().getPluginManager().getPlugin("Emaki_Forge");
        if (plugin instanceof EmakiForgePlugin forge) {
            this.forgePlugin = forge;
        }
    }
}
```

---

## 最佳实践

### 1. 检查服务可用性

```java
public void safeForge(Player player) {
    EmakiForgePlugin plugin = getForgePlugin();
    if (plugin == null) {
        player.sendMessage("锻造系统不可用");
        return;
    }
    
    ForgeService forgeService = plugin.forgeService();
}
```

### 2. 异步操作

```java
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    PlayerData data = plugin.playerDataStore().get(playerId);
    
    Bukkit.getScheduler().runTask(plugin, () -> {
    });
});
```

### 3. 错误处理

```java
ForgeService.ForgeResult result = forgeService.executeForge(player, recipe, guiItems);
if (!result.success()) {
    plugin.messageService().send(player, result.errorKey(), result.replacements());
    return;
}
```

---

## 下一步

- [常见问题](./09-常见问题.md) - 查看常见问题解答
- [使用示例](./10-使用示例.md) - 查看完整示例代码

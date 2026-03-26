# Emaki CoreLib - API 接口文档

## 概述

本文档详细描述了 Emaki CoreLib 的所有公开 API 接口，包括方法签名、参数说明、返回值、使用示例和错误处理机制。

---

## 目录

1. [GuiService - GUI服务](#guiservice---gui服务)
2. [GuiTemplate - GUI模板](#guitemplate---gui模板)
3. [GuiSession - GUI会话](#guisession---gui会话)
4. [ItemSourceRegistry - 物品源注册表](#itemsourceregistry---物品源注册表)
5. [ExpressionEngine - 表达式引擎](#expressionengine---表达式引擎)
6. [ConditionEvaluator - 条件评估器](#conditionevaluator---条件评估器)
7. [OperationRegistry - 操作注册表](#operationregistry---操作注册表)
8. [OperationExecutor - 操作执行器](#operationexecutor---操作执行器)
9. [OperationContext - 执行上下文](#operationcontext---执行上下文)
10. [Numbers - 数值工具](#numbers---数值工具)
11. [Randoms - 随机工具](#randoms---随机工具)
12. [Texts - 文本工具](#texts---文本工具)
13. [MiniMessages - 消息解析](#minimessages---消息解析)

---

## GuiService - GUI服务

### 类定义

```java
package emaki.jiuwu.craft.corelib.gui;

public final class GuiService implements Listener
```

### 构造方法

#### GuiService(JavaPlugin plugin)

创建 GUI 服务实例。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `plugin` | JavaPlugin | 插件实例，用于事件注册 |

**示例**：

```java
GuiService guiService = new GuiService(myPlugin);
Bukkit.getPluginManager().registerEvents(guiService, myPlugin);
```

---

### open()

```java
public GuiSession open(GuiOpenRequest request)
```

打开 GUI 界面。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `request` | GuiOpenRequest | GUI 打开请求对象 |

**返回值**：
- 成功：`GuiSession` 实例
- 失败：`null`

**错误处理**：
- `request` 为 null 时返回 null
- `viewer` 或 `template` 为 null 时返回 null
- 已存在会话时会先关闭旧会话

**示例**：

```java
GuiSession session = guiService.open(new GuiOpenRequest(
    plugin,
    player,
    template,
    Map.of("title", "商店"),
    itemFactory,
    renderer,
    handler
));

if (session == null) {
    player.sendMessage("无法打开界面");
}
```

---

### getSession()

```java
public GuiSession getSession(UUID playerId)
```

获取玩家当前的 GUI 会话。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `playerId` | UUID | 玩家 UUID |

**返回值**：
- 存在：`GuiSession` 实例
- 不存在：`null`

---

### close()

```java
public void close(UUID playerId)
```

关闭玩家的 GUI 会话。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `playerId` | UUID | 玩家 UUID |

---

## GuiTemplate - GUI模板

### 类定义

```java
package emaki.jiuwu.craft.corelib.gui;

public final class GuiTemplate
```

### 内部记录类

#### ResolvedSlot

```java
public record ResolvedSlot(GuiSlot definition, int inventorySlot, int slotIndex)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `definition` | GuiSlot | 槽位定义 |
| `inventorySlot` | int | 背包槽位索引 |
| `slotIndex` | int | 在槽位组内的索引 |

---

### 构造方法

```java
public GuiTemplate(String id, String title, int rows, Map<String, GuiSlot> slots)
```

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `id` | String | 模板标识 |
| `title` | String | 界面标题（支持变量） |
| `rows` | int | 行数（1-6） |
| `slots` | Map\<String, GuiSlot\> | 槽位映射 |

---

### 实例方法

#### slot()

```java
public GuiSlot slot(String key)
```

获取指定键的槽位定义。

**返回值**：`GuiSlot` 或 `null`

---

#### resolvedSlotAt()

```java
public ResolvedSlot resolvedSlotAt(int inventorySlot)
```

获取指定背包槽位的解析信息。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `inventorySlot` | int | 背包槽位索引 |

**返回值**：`ResolvedSlot` 或 `null`

---

#### slotsByAction()

```java
public List<GuiSlot> slotsByAction(String action)
```

获取指定动作的所有槽位。

**返回值**：`List<GuiSlot>`（可能为空列表）

---

### 访问器方法

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `id()` | String | 模板标识 |
| `title()` | String | 界面标题 |
| `rows()` | int | 行数 |
| `slots()` | Map\<String, GuiSlot\> | 槽位映射 |

---

## GuiSession - GUI会话

### 类定义

```java
package emaki.jiuwu.craft.corelib.gui;

public final class GuiSession implements InventoryHolder
```

### 实例方法

#### open()

```java
public void open()
```

刷新界面并打开给玩家。

---

#### refresh()

```java
public void refresh()
```

重新渲染所有槽位。

---

#### replaceReplacements()

```java
public void replaceReplacements(Map<String, ?> values)
```

替换所有变量。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `values` | Map\<String, ?\> | 变量映射 |

---

#### putReplacement()

```java
public void putReplacement(String key, Object value)
```

添加或更新单个变量。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `key` | String | 变量名 |
| `value` | Object | 变量值 |

---

### 访问器方法

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `owner()` | Plugin | 插件实例 |
| `viewer()` | Player | 查看玩家 |
| `template()` | GuiTemplate | GUI 模板 |
| `handler()` | GuiSessionHandler | 事件处理器 |
| `replacements()` | Map\<String, Object\> | 变量映射 |
| `getInventory()` | Inventory | 背包实例 |

---

## ItemSourceRegistry - 物品源注册表

### 类定义

```java
package emaki.jiuwu.craft.corelib.item;

public final class ItemSourceRegistry
```

### 静态方法

#### system()

```java
public static ItemSourceRegistry system()
```

获取系统全局注册表。

**返回值**：`ItemSourceRegistry` 单例

---

### 实例方法

#### registerParser()

```java
public void registerParser(ItemSourceParser parser)
```

注册自定义解析器（添加到列表头部，优先级最高）。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `parser` | ItemSourceParser | 解析器实例 |

---

#### setFallbackParser()

```java
public void setFallbackParser(ItemSourceParser parser)
```

设置回退解析器。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `parser` | ItemSourceParser | 解析器实例 |

---

#### parseShorthand()

```java
public ItemSource parseShorthand(String shorthand)
```

解析物品源简写。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `shorthand` | String | 物品源简写 |

**返回值**：
- 成功：`ItemSource` 实例
- 失败：`null`

**错误处理**：
- 空白字符串返回 null
- 无解析器能处理时使用回退解析器

**示例**：

```java
ItemSourceRegistry registry = ItemSourceRegistry.system();
ItemSource source = registry.parseShorthand("ni-flame_sword");
```

---

## ExpressionEngine - 表达式引擎

### 类定义

```java
package emaki.jiuwu.craft.corelib.expression;

public final class ExpressionEngine
```

### 静态方法

#### evaluate(String expression)

```java
public static double evaluate(String expression)
```

计算数学表达式。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `expression` | String | 数学表达式 |

**返回值**：计算结果（失败返回 0.0）

---

#### evaluate(String expression, Map<String, ?> variables)

```java
public static double evaluate(String expression, Map<String, ?> variables)
```

计算带变量的数学表达式。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `expression` | String | 数学表达式 |
| `variables` | Map\<String, ?\> | 变量映射 |

**返回值**：计算结果

**错误处理**：
- 表达式为空返回 0.0
- 解析失败返回 0.0
- 变量格式：`{variable_name}`

**示例**：

```java
double result = ExpressionEngine.evaluate(
    "{level} * 10 + {base}",
    Map.of("level", 5, "base", 100)
);
// 结果: 150.0
```

---

#### evaluateRandomConfig(Object config)

```java
public static double evaluateRandomConfig(Object config)
```

评估随机配置。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `config` | Object | 随机配置对象 |

**返回值**：随机生成的数值

**支持的配置类型**：
- `constant` / `const` / `fixed`：常量
- `uniform`：均匀分布
- `gaussian` / `normal`：高斯分布
- `skew_normal`：偏态正态分布
- `triangle`：三角分布
- `expression`：表达式

**示例**：

```java
// YAML 配置
// value:
//   type: "gaussian"
//   mean: 100
//   std_dev: 15
//   min: 50
//   max: 150

double value = ExpressionEngine.evaluateRandomConfig(config.get("value"));
```

---

## ConditionEvaluator - 条件评估器

### 类定义

```java
package emaki.jiuwu.craft.corelib.condition;

public final class ConditionEvaluator
```

### 内部记录类

#### ParsedCondition

```java
public record ParsedCondition(String left, String operator, String right)
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `left` | String | 左值 |
| `operator` | String | 运算符 |
| `right` | String | 右值 |

---

### 静态方法

#### parse()

```java
public static ParsedCondition parse(String line)
```

解析条件表达式。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `line` | String | 条件表达式 |

**返回值**：
- 成功：`ParsedCondition`
- 失败：`null`

---

#### evaluateSingle()

```java
public static Boolean evaluateSingle(String line, Function<String, String> placeholderReplacer)
```

评估单个条件。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `line` | String | 条件表达式 |
| `placeholderReplacer` | Function\<String, String\> | 占位符替换器 |

**返回值**：
- 成功：`Boolean` 结果
- 失败：`null`

---

#### evaluate()

```java
public static boolean evaluate(
    List<String> conditions,
    String conditionType,
    Integer requiredCount,
    Function<String, String> placeholderReplacer,
    boolean invalidAsFailure
)
```

评估条件列表。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `conditions` | List\<String\> | 条件列表 |
| `conditionType` | String | 组合模式 |
| `requiredCount` | Integer | 所需数量 |
| `placeholderReplacer` | Function\<String, String\> | 占位符替换器 |
| `invalidAsFailure` | boolean | 无效条件处理方式 |

**返回值**：`boolean` 结果

**支持的组合模式**：
- `all_of`：所有条件都满足
- `any_of`：任一条件满足
- `at_least`：至少满足 N 个
- `exactly`：恰好满足 N 个

**示例**：

```java
boolean result = ConditionEvaluator.evaluate(
    List.of("%player_level% >= 10", "%player_exp% >= 1000"),
    "all_of",
    null,
    text -> PlaceholderAPI.setPlaceholders(player, text),
    true
);
```

---

## OperationRegistry - 操作注册表

### 类定义

```java
package emaki.jiuwu.craft.corelib.operation;

public final class OperationRegistry
```

### 构造方法

```java
public OperationRegistry()
```

创建空的操作注册表。

---

### 实例方法

#### register()

```java
public OperationResult register(Operation operation)
```

注册操作。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `operation` | Operation | 操作实例 |

**返回值**：`OperationResult` 注册结果

**错误类型**：
- `INVALID_ARGUMENT`：操作ID为空或已存在

**示例**：

```java
OperationRegistry registry = new OperationRegistry();
OperationResult result = registry.register(new CustomOperation());

if (!result.success()) {
    plugin.getLogger().warning("注册失败: " + result.errorMessage());
}
```

---

#### unregister()

```java
public void unregister(String operationId)
```

注销操作。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `operationId` | String | 操作ID |

---

#### get()

```java
public Operation get(String operationId)
```

获取操作实例。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `operationId` | String | 操作ID（不区分大小写） |

**返回值**：`Operation` 或 `null`

---

#### byCategory()

```java
public List<Operation> byCategory(String category)
```

按分类获取操作列表。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `category` | String | 分类名称 |

**返回值**：`List<Operation>`

---

#### all()

```java
public Map<String, Operation> all()
```

获取所有已注册的操作。

**返回值**：`Map<String, Operation>` 不可变映射

---

## OperationExecutor - 操作执行器

### 类定义

```java
package emaki.jiuwu.craft.corelib.operation;

public final class OperationExecutor
```

### 构造方法

```java
public OperationExecutor(
    Plugin plugin,
    OperationRegistry registry,
    OperationLineParser lineParser,
    PlaceholderRegistry placeholderRegistry,
    OperationTemplateRegistry templateRegistry
)
```

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `plugin` | Plugin | 插件实例 |
| `registry` | OperationRegistry | 操作注册表 |
| `lineParser` | OperationLineParser | 行解析器 |
| `placeholderRegistry` | PlaceholderRegistry | 占位符注册表 |
| `templateRegistry` | OperationTemplateRegistry | 模板注册表 |

---

### execute()

```java
public CompletableFuture<OperationResult> execute(
    OperationContext context,
    String operationId,
    Map<String, String> arguments
)
```

执行单个操作。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `context` | OperationContext | 执行上下文 |
| `operationId` | String | 操作ID |
| `arguments` | Map\<String, String\> | 参数映射 |

**返回值**：`CompletableFuture<OperationResult>`

**示例**：

```java
CompletableFuture<OperationResult> future = executor.execute(
    context,
    "give_money",
    Map.of("amount", "1000")
);

future.thenAccept(result -> {
    if (result.success()) {
        plugin.getLogger().info("操作成功");
    }
});
```

---

### executeAll()

```java
public CompletableFuture<OperationBatchResult> executeAll(
    OperationContext context,
    List<String> lines,
    boolean stopOnFailure
)
```

批量执行操作行。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `context` | OperationContext | 执行上下文 |
| `lines` | List\<String\> | 操作行列表 |
| `stopOnFailure` | boolean | 失败时是否停止 |

**返回值**：`CompletableFuture<OperationBatchResult>`

**示例**：

```java
List<String> operations = List.of(
    "send_message text=\"<green>奖励发放中...</green>\"",
    "give_money amount=1000",
    "play_sound sound=entity.experience_orb.pickup"
);

CompletableFuture<OperationBatchResult> future = executor.executeAll(context, operations, true);

future.thenAccept(batch -> {
    if (batch.success()) {
        plugin.getLogger().info("所有操作执行成功");
    } else {
        OperationStepResult failure = batch.firstFailure();
        plugin.getLogger().warning("失败于第 " + failure.lineNumber() + " 行");
    }
});
```

---

## OperationContext - 执行上下文

### 类定义

```java
package emaki.jiuwu.craft.corelib.operation;

public final class OperationContext
```

### 静态工厂方法

#### create()

```java
public static OperationContext create(
    Plugin sourcePlugin,
    Player player,
    String phase,
    boolean silent,
    boolean debug
)
```

创建执行上下文。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `sourcePlugin` | Plugin | 源插件实例 |
| `player` | Player | 目标玩家 |
| `phase` | String | 执行阶段标识 |
| `silent` | boolean | 静默模式 |
| `debug` | boolean | 调试模式 |

**返回值**：`OperationContext`

---

### 实例方法

#### withPlaceholder()

```java
public OperationContext withPlaceholder(String key, Object value)
```

添加占位符变量。

---

#### withPlaceholders()

```java
public OperationContext withPlaceholders(Map<String, ?> values)
```

批量添加占位符变量。

---

#### withAttribute()

```java
public OperationContext withAttribute(String key, Object value)
```

添加自定义属性。

---

#### withPhase()

```java
public OperationContext withPhase(String value)
```

设置执行阶段。

---

### 访问器方法

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `sourcePlugin()` | Plugin | 源插件实例 |
| `player()` | Player | 目标玩家 |
| `phase()` | String | 执行阶段 |
| `silent()` | boolean | 是否静默模式 |
| `debug()` | boolean | 是否调试模式 |
| `placeholders()` | Map\<String, String\> | 占位符映射 |
| `attributes()` | Map\<String, Object\> | 属性映射 |
| `placeholder(key)` | String | 获取占位符值 |
| `attribute(key)` | Object | 获取属性值 |

---

## Numbers - 数值工具

### 类定义

```java
package emaki.jiuwu.craft.corelib.math;

public final class Numbers
```

### 静态方法

#### tryParseInt()

```java
public static int tryParseInt(Object value, int defaultValue)
public static Integer tryParseInt(Object value, Integer defaultValue)
```

安全解析整数。

| 参数 | 类型 | 说明 |
|------|------|------|
| `value` | Object | 待解析值 |
| `defaultValue` | int/Integer | 默认值 |

**返回值**：解析后的整数

---

#### tryParseDouble()

```java
public static double tryParseDouble(Object value, double defaultValue)
public static Double tryParseDouble(Object value, Double defaultValue)
```

安全解析浮点数。

---

#### isNumeric()

```java
public static boolean isNumeric(String text)
```

检查字符串是否为数值。

---

#### clamp()

```java
public static int clamp(int value, int min, int max)
public static double clamp(double value, double min, double max)
```

限制数值范围。

---

#### roundToInt()

```java
public static int roundToInt(double value)
```

四舍五入到整数。

---

## Randoms - 随机工具

### 类定义

```java
package emaki.jiuwu.craft.corelib.math;

public final class Randoms
```

### 内部记录类

#### Weighted\<T\>

```java
public record Weighted<T>(T item, double weight)
```

---

### 静态方法

#### randomInt()

```java
public static int randomInt(int min, int max)
```

生成随机整数 [min, max]。

---

#### randomDouble()

```java
public static double randomDouble()
```

生成随机浮点数 [0.0, 1.0)。

---

#### chance()

```java
public static boolean chance(double probability)
```

概率判断。

---

#### uniform()

```java
public static double uniform(double min, double max)
```

均匀分布随机。

---

#### gaussian()

```java
public static double gaussian(double mean, double stdDev, Double min, Double max, int maxAttempts)
```

高斯分布随机。

---

#### skewNormal()

```java
public static double skewNormal(double mean, double stdDev, double skewness, Double min, Double max, int maxAttempts)
```

偏态正态分布随机。

---

#### triangle()

```java
public static double triangle(double mode, double deviation)
```

三角分布随机。

---

#### weightedRandom()

```java
public static <T> T weightedRandom(List<Weighted<T>> items)
```

加权随机选择。

---

#### shuffle()

```java
public static <T> List<T> shuffle(List<T> values)
```

随机打乱列表。

---

## Texts - 文本工具

### 类定义

```java
package emaki.jiuwu.craft.corelib.text;

public final class Texts
```

### 静态方法

#### isBlank() / isNotBlank()

```java
public static boolean isBlank(String text)
public static boolean isNotBlank(String text)
```

检查字符串是否为空白。

---

#### trim()

```java
public static String trim(String text)
```

去除首尾空白（null 安全）。

---

#### lower()

```java
public static String lower(String text)
```

转换为小写（null 安全）。

---

#### formatTemplate()

```java
public static String formatTemplate(String template, Map<String, ?> replacements)
```

格式化模板字符串。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `template` | String | 模板字符串 |
| `replacements` | Map\<String, ?\> | 变量映射 |

**示例**：

```java
String result = Texts.formatTemplate(
    "你好，{player}！你有{count}条消息。",
    Map.of("player", "Steve", "count", 5)
);
// 结果: "你好，Steve！你有5条消息。"
```

---

#### asStringList()

```java
public static List<String> asStringList(Object value)
```

将对象转换为字符串列表。

---

#### stripMiniTags()

```java
public static String stripMiniTags(String text)
```

移除 MiniMessage 标签。

---

## MiniMessages - 消息解析

### 类定义

```java
package emaki.jiuwu.craft.corelib.text;

public final class MiniMessages
```

### 静态方法

#### parse()

```java
public static Component parse(String text)
```

解析 MiniMessage 格式文本。

**参数**：
| 参数名 | 类型 | 说明 |
|--------|------|------|
| `text` | String | MiniMessage 格式文本 |

**返回值**：`Component` 实例

**示例**：

```java
Component component = MiniMessages.parse("<gold>欢迎来到服务器！");
player.sendMessage(component);
```

---

#### serialize()

```java
public static String serialize(Component component)
```

将组件序列化为 MiniMessage 格式。

---

#### plain()

```java
public static String plain(Component component)
```

将组件转换为纯文本。

---

#### miniMessage()

```java
public static MiniMessage miniMessage()
```

获取 MiniMessage 实例。

---

## 错误处理最佳实践

### 1. 空值检查

```java
public void safeOperation(ItemSource source) {
    if (source == null) {
        plugin.getLogger().warning("物品源为空");
        return;
    }
}
```

### 2. 异常捕获

```java
public double safeEvaluate(String expression) {
    try {
        return ExpressionEngine.evaluate(expression);
    } catch (Exception e) {
        plugin.getLogger().warning("表达式计算失败: " + expression);
        return 0.0;
    }
}
```

### 3. 默认值处理

```java
int value = Numbers.tryParseInt(config.get("value"), 10);
```

### 4. 日志记录

```java
if (plugin.appConfig().debug()) {
    plugin.getLogger().info("调试信息: " + debugData);
}
```

---

## 下一步

- [常见问题](./10-常见问题.md) - 查看常见问题解答
- [使用示例](./11-使用示例.md) - 查看完整示例代码

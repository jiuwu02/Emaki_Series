# Emaki Forge - GUI 配置

## 概述

Emaki Forge 提供了两个主要的 GUI 界面：锻造台界面和配方图鉴界面。这些界面可以通过 YAML 配置文件完全自定义。

---

## GUI 模板位置

```
plugins/Emaki_Forge/gui/
├── forge_gui.yml      # 锻造台界面
└── recipe_book.yml    # 配方图鉴界面
```

---

## 锻造台 GUI

### 基础配置

```yaml
id: "forge_gui"
title: "<dark_gray>锻造台 - {recipe}</dark_gray>"
rows: 5
```

### 配置项说明

| 配置项 | 类型 | 说明 |
|--------|------|------|
| `id` | String | GUI 模板标识 |
| `title` | String | 界面标题（支持变量） |
| `rows` | Integer | 行数（1-6） |

### 标题变量

| 变量 | 说明 |
|------|------|
| `{recipe}` | 当前配方名称 |

---

### 槽位配置

```yaml
slots:
  # 装饰边框
  top_frame:
    slots: [0, 1, 2, 3, 4, 5, 6, 7, 8]
    item: "GRAY_STAINED_GLASS_PANE"
    display_name: "<gray>"

  # 图纸输入槽
  blueprint_inputs:
    slots: [10, 11]
    action: "blueprint_inputs"
    item: "PAPER"
    display_name: "<light_purple>放入图纸</light_purple>"
    lore:
      - "<gray>支持通用图纸与武器图纸"

  # 目标物品槽
  target_item:
    slots: [13]
    action: "target_item"
    item: "LIGHT_BLUE_STAINED_GLASS_PANE"
    display_name: "<aqua>放入目标物品</aqua>"
    lore:
      - "<gray>放入要进行锻造的主物品"

  # 容量显示
  capacity_display:
    slots: [22]
    action: "capacity_display"
    item: "HOPPER"
    display_name: "<gray>锻造容量</gray>"
    lore:
      - "<gray>当前容量: <yellow>{current}</yellow>"
      - "<gray>容量上限: <gold>{max}</gold>"
      - "<gray>状态: {capacity_state}</gray>"

  # 结果预览
  result_preview:
    slots: [24]
    action: "result_preview"
    item: "ANVIL"
    display_name: "<gold>结果预览</gold>"
    lore:
      - "<gray>满足配方后会显示结果物品"

  # 必需材料槽
  required_materials:
    slots: [19, 20, 21]
    action: "required_materials"
    item: "YELLOW_STAINED_GLASS_PANE"
    display_name: "<gold>放入必需材料</gold>"
    lore:
      - "<gray>这里会优先接收固定需求材料"

  # 可选材料槽
  optional_materials:
    slots: [28, 29, 30, 31, 32]
    action: "optional_materials"
    item: "CYAN_STAINED_GLASS_PANE"
    display_name: "<green>放入可选材料</green>"
    lore:
      - "<gray>可选材料会消耗锻造容量"

  # 确认按钮
  confirm:
    slots: [40]
    action: "confirm"
    item: "ANVIL"
    display_name: "<green>确认锻造</green>"
    lore:
      - "<gray>当前容量: <yellow>{current}/{max}</yellow>"
      - "<yellow>点击后开始执行锻造"
    click:
      sound: "ui.button.click"
      volume: 1.0
      pitch: 1.0
```

---

### 系统保留 Action

| Action | 说明 |
|--------|------|
| `blueprint_inputs` | 图纸输入槽 |
| `target_item` | 目标物品槽 |
| `required_materials` | 必需材料槽 |
| `optional_materials` | 可选材料槽 |
| `capacity_display` | 容量显示 |
| `result_preview` | 结果预览 |
| `confirm` | 确认按钮 |

---

### 槽位变量

| 变量 | 说明 |
|------|------|
| `{recipe}` | 配方名称 |
| `{current}` | 当前容量 |
| `{max}` | 最大容量 |
| `{capacity_state}` | 容量状态文本 |

---

## 完整锻造台配置

```yaml
id: "forge_gui"
title: "<dark_gray>锻造台 - {recipe}</dark_gray>"
rows: 5

slots:
  # 顶部边框
  top_frame:
    slots: [0, 1, 2, 3, 4, 5, 6, 7, 8]
    item: "GRAY_STAINED_GLASS_PANE"
    display_name: "<gray>"

  # 侧边框
  side_frame:
    slots: [9, 17, 18, 26, 27, 35, 36, 37, 38, 39, 41, 42, 43, 44]
    item: "GRAY_STAINED_GLASS_PANE"
    display_name: "<gray>"

  # 内部边框
  core_frame:
    slots: [12, 14, 15, 16, 23, 25, 33, 34]
    item: "BLACK_STAINED_GLASS_PANE"
    display_name: "<dark_gray>"

  # 图纸输入
  blueprint_inputs:
    slots: [10, 11]
    action: "blueprint_inputs"
    item: "PAPER"
    display_name: "<light_purple>放入图纸</light_purple>"
    lore:
      - "<gray>支持通用图纸与武器图纸"
      - "<gray>不同图纸提供不同锻造容量"
    leftclick:
      sound: "ui.button.click"
      volume: 0.8
      pitch: 1.0
    rightclick:
      sound: "ui.button.click"
      volume: 0.8
      pitch: 0.8

  # 目标物品
  target_item:
    slots: [13]
    action: "target_item"
    item: "LIGHT_BLUE_STAINED_GLASS_PANE"
    display_name: "<aqua>放入目标物品</aqua>"
    lore:
      - "<gray>放入要进行锻造的主物品"
      - "<gray>锻造后将获得强化版本"
    click:
      sound: "block.anvil.place"
      volume: 0.5
      pitch: 1.0

  # 容量显示
  capacity_display:
    slots: [22]
    action: "capacity_display"
    item: "HOPPER"
    display_name: "<gray>锻造容量</gray>"
    lore:
      - "<gray>当前容量: <yellow>{current}</yellow>"
      - "<gray>容量上限: <gold>{max}</gold>"
      - "<gray>状态: {capacity_state}</gray>"
      - ""
      - "<gray>可选材料会消耗容量"

  # 结果预览
  result_preview:
    slots: [24]
    action: "result_preview"
    item: "ANVIL"
    display_name: "<gold>结果预览</gold>"
    lore:
      - "<gray>满足配方后会显示结果物品"
      - "<gray>包括品质和属性预览"

  # 必需材料
  required_materials:
    slots: [19, 20, 21]
    action: "required_materials"
    item: "YELLOW_STAINED_GLASS_PANE"
    display_name: "<gold>放入必需材料</gold>"
    lore:
      - "<gray>这里会优先接收固定需求材料"
      - "<gray>必需材料不消耗锻造容量"

  # 可选材料
  optional_materials:
    slots: [28, 29, 30, 31, 32]
    action: "optional_materials"
    item: "CYAN_STAINED_GLASS_PANE"
    display_name: "<green>放入可选材料</green>"
    lore:
      - "<gray>可选材料会消耗锻造容量"
      - "<gray>为产物提供额外属性"

  # 确认按钮
  confirm:
    slots: [40]
    action: "confirm"
    item: "ANVIL"
    display_name: "<green>确认锻造</green>"
    lore:
      - "<gray>当前容量: <yellow>{current}/{max}</yellow>"
      - "<yellow>点击后开始执行锻造"
    click:
      sound: "block.anvil.use"
      volume: 1.0
      pitch: 1.0
```

---

## 配方图鉴 GUI

### 基础配置

```yaml
id: "recipe_book"
title: "<dark_gray>配方图鉴</dark_gray>"
rows: 6
```

### 槽位配置

```yaml
slots:
  # 配方列表槽位
  recipe_list:
    slots: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44]
    action: "recipe_list"
    item: "BOOK"
    display_name: "<gray>配方</gray>"

  # 底部填充
  footer_fill:
    slots: [46, 47, 48, 50, 51, 52]
    item: "GRAY_STAINED_GLASS_PANE"
    display_name: "<gray>"

  # 上一页
  prev_page:
    slots: [45]
    action: "prev_page"
    item: "ARROW"
    display_name: "<gray>上一页</gray>"
    lore:
      - "<gray>当前页: <yellow>{page}/{pages}</yellow>"

  # 下一页
  next_page:
    slots: [53]
    action: "next_page"
    item: "ARROW"
    display_name: "<gray>下一页</gray>"
    lore:
      - "<gray>当前页: <yellow>{page}/{pages}</yellow>"

  # 关闭按钮
  close:
    slots: [49]
    action: "close"
    item: "BARRIER"
    display_name: "<red>关闭</red>"
```

---

### 图鉴变量

| 变量 | 说明 |
|------|------|
| `{page}` | 当前页码 |
| `{pages}` | 总页数 |

---

## 配方槽位覆盖

配方可以覆盖默认的 GUI 槽位配置：

```yaml
# recipes/flame_sword.yml
gui:
  template: "forge_gui"
  slots:
    blueprint_inputs: [10, 11]
    target_item: [13]
    required_materials: [19, 20, 21]
    optional_materials: [28, 29, 30, 31, 32]
    capacity_display: [22]
    confirm: [40]
    result_preview: [24]
```

---

## 音效配置

### 音效格式

```yaml
# 完整格式
click:
  sound: "ui.button.click"
  volume: 1.0
  pitch: 1.0

# 简写格式
click: "ui.button.click-1.0-1.0"
```

### 分别配置左右键

```yaml
leftclick:
  sound: "block.note_block.pling"
  volume: 0.8
  pitch: 1.2

rightclick:
  sound: "block.anvil.use"
  volume: 1.0
  pitch: 0.8
```

---

## 槽位索引图

### 5行界面 (45格)

```
[0]  [1]  [2]  [3]  [4]  [5]  [6]  [7]  [8]
[9]  [10] [11] [12] [13] [14] [15] [16] [17]
[18] [19] [20] [21] [22] [23] [24] [25] [26]
[27] [28] [29] [30] [31] [32] [33] [34] [35]
[36] [37] [38] [39] [40] [41] [42] [43] [44]
```

### 6行界面 (54格)

```
[0]  [1]  [2]  [3]  [4]  [5]  [6]  [7]  [8]
[9]  [10] [11] [12] [13] [14] [15] [16] [17]
[18] [19] [20] [21] [22] [23] [24] [25] [26]
[27] [28] [29] [30] [31] [32] [33] [34] [35]
[36] [37] [38] [39] [40] [41] [42] [43] [44]
[45] [46] [47] [48] [49] [50] [51] [52] [53]
```

---

## 最佳实践

### 1. 清晰的视觉层次

```yaml
# 边框使用灰色
side_frame:
  item: "GRAY_STAINED_GLASS_PANE"

# 功能槽使用彩色
required_materials:
  item: "YELLOW_STAINED_GLASS_PANE"

optional_materials:
  item: "CYAN_STAINED_GLASS_PANE"
```

### 2. 提供操作提示

```yaml
confirm:
  display_name: "<green>确认锻造</green>"
  lore:
    - "<gray>当前容量: <yellow>{current}/{max}</yellow>"
    - "<yellow>点击后开始执行锻造"
    - ""
    - "<red>注意: 锻造将消耗所有材料"
```

### 3. 合理的音效反馈

```yaml
# 确认按钮使用明显的音效
confirm:
  click:
    sound: "block.anvil.use"
    volume: 1.0
    pitch: 1.0

# 普通按钮使用轻微音效
blueprint_inputs:
  click:
    sound: "ui.button.click"
    volume: 0.5
    pitch: 1.0
```

---

## 故障排查

### GUI 无法打开

**检查项**：
1. GUI 模板文件是否存在
2. `id` 配置是否正确
3. YAML 语法是否正确

### 槽位不响应

**检查项**：
1. `action` 是否正确设置
2. 槽位索引是否有效
3. 是否有重复的槽位索引

### 变量不替换

**检查项**：
1. 变量名是否正确
2. 是否使用了正确的花括号格式

---

## 下一步

- [API文档](./API接口文档.md) - 开发者接口参考
- [常见问题](./常见问题.md) - 查看常见问题解答

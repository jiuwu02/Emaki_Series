import{_ as a,o as i,c as n,ae as t}from"./chunks/framework.DTwQaIIa.js";const c=JSON.parse('{"title":"EmakiForge 总览","description":"","frontmatter":{},"headers":[],"relativePath":"modules/forge/index.md","filePath":"modules/forge/index.md"}'),e={name:"modules/forge/index.md"};function l(p,s,h,d,r,k){return i(),n("div",null,[...s[0]||(s[0]=[t(`<h1 id="emakiforge-总览" tabindex="-1">EmakiForge 总览 <a class="header-anchor" href="#emakiforge-总览" aria-label="Permalink to &quot;EmakiForge 总览&quot;">​</a></h1><p>EmakiForge 是一套配方驱动的锻造系统。玩家在锻造界面中放入图纸和材料，系统根据材料贡献和品质随机生成最终产物。产物的属性数值、名称、Lore 都由配方和材料共同决定，结果写入物品的 PDC 数据中。</p><p>除了锻造本身，EmakiForge 还内置了配方书（让玩家浏览可用配方）和运行时编辑器（在游戏内直接修改配方、图纸、材料定义，改完自动备份和热重载）。</p><h2 id="基本信息" tabindex="-1">基本信息 <a class="header-anchor" href="#基本信息" aria-label="Permalink to &quot;基本信息&quot;">​</a></h2><table tabindex="0"><thead><tr><th>属性</th><th>值</th></tr></thead><tbody><tr><td>模块 ID</td><td><code>emaki-forge</code></td></tr><tr><td>版本</td><td><code>3.3.0</code></td></tr><tr><td>主类</td><td><code>emaki.jiuwu.craft.forge.EmakiForgePlugin</code></td></tr><tr><td>根命令</td><td><code>/emakiforge</code></td></tr><tr><td>别名</td><td><code>/eforge</code>, <code>/ef</code></td></tr><tr><td>强依赖</td><td>EmakiCoreLib</td></tr><tr><td>软依赖</td><td>EmakiAttribute, PlaceholderAPI</td></tr></tbody></table><h2 id="它做了什么" tabindex="-1">它做了什么 <a class="header-anchor" href="#它做了什么" aria-label="Permalink to &quot;它做了什么&quot;">​</a></h2><ul><li><strong>配方锻造</strong> — 图纸 + 材料 → 品质随机 → 属性计算 → 产出装备，完整的锻造链路</li><li><strong>材料贡献</strong> — 每种材料可以贡献属性值、修正品质、增加容量，也可以改名或追加 Lore</li><li><strong>品质系统</strong> — 加权随机决定品质等级，品质倍率直接影响最终属性。还有保底计数器兜底，防止玩家连续几十次都出低品质</li><li><strong>锻造层快照</strong> — <code>ForgeLayerSnapshotBuilder</code> 把锻造结果打包成 layer snapshot 写入 PDC，后续由 <code>StructuredPresentationRenderer</code> 渲染到物品上</li><li><strong>GUI</strong> — 锻造界面、配方书、运行时编辑器，三套界面各司其职</li><li><strong>热重载编辑</strong> — <code>blueprints/</code>、<code>materials/</code>、<code>recipes/</code> 都支持运行时编辑，改完自动备份到 <code>backups/</code></li><li><strong>物品刷新</strong> — 配方改了之后，已锻造的物品签名会对不上，<code>ForgeItemRefreshService</code> 会自动刷新展示层</li></ul><h2 id="配置文件" tabindex="-1">配置文件 <a class="header-anchor" href="#配置文件" aria-label="Permalink to &quot;配置文件&quot;">​</a></h2><h3 id="config-yml" tabindex="-1">config.yml <a class="header-anchor" href="#config-yml" aria-label="Permalink to &quot;config.yml&quot;">​</a></h3><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 配置文件版本，请勿手动修改</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">config_version</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">3</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 语言设置</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">language</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">zh_CN</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 首次启动时释放默认数据（配方、图纸、材料示例）</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 释放完成后建议改为 false，避免重载时覆盖你的修改</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">release_default_data</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ===== 品质系统 =====</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">quality</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 品质等级定义，格式：&quot;名称-权重-倍率&quot;</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 权重越高越容易抽中，倍率影响材料贡献的最终数值</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  tiers</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;粗糙-40-0.6&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;普通-30-1.0&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;精良-20-1.3&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;史诗-8-1.6&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;传说-2-2.0&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 当品质无法确定时的回退值</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  default_tier</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;普通&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 保底系统 — 防止玩家连续太多次出低品质</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  guarantee</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    enabled</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # 连续多少次没出目标品质后触发保底</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    count</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">50</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">    # 保底触发时强制给予的品质</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    tier</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;史诗&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 品质在物品上的展示方式</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  item_meta</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    structured_presentation</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      name_contributions</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        quality_prefix</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">          order</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">0</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">          format</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;{quality} &quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 数字格式化（影响属性值的显示精度）</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">number_format</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;#.##&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 权限配置</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">permission</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  enabled</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 条件系统</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">condition</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 全局条件，所有配方都会检查</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  global</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: []</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 锻造历史记录</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">history</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  enabled</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 每个玩家最多保留多少条记录</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  max_records</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">100</span></span></code></pre></div><div class="warning custom-block"><p class="custom-block-title">注意</p><p><code>config_version</code> 由插件自动管理，用于配置版本迁移。手动改这个值可能导致配置被覆盖或迁移出错。</p></div><h3 id="品质等级格式" tabindex="-1">品质等级格式 <a class="header-anchor" href="#品质等级格式" aria-label="Permalink to &quot;品质等级格式&quot;">​</a></h3><p>品质等级用 <code>&quot;名称-权重-倍率&quot;</code> 的紧凑格式定义，三段用短横线分隔：</p><table tabindex="0"><thead><tr><th>字段</th><th>说明</th><th>示例</th></tr></thead><tbody><tr><td>名称</td><td>品质的显示名称</td><td><code>传说</code></td></tr><tr><td>权重</td><td>加权随机的权重值，越大越容易抽中</td><td><code>2</code></td></tr><tr><td>倍率</td><td>材料贡献的乘数，直接乘到最终属性上</td><td><code>2.0</code></td></tr></tbody></table><h2 id="文件目录结构" tabindex="-1">文件目录结构 <a class="header-anchor" href="#文件目录结构" aria-label="Permalink to &quot;文件目录结构&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>plugins/EmakiForge/</span></span>
<span class="line"><span>├── config.yml              ← 主配置</span></span>
<span class="line"><span>├── lang/                   ← 语言文件</span></span>
<span class="line"><span>│   └── zh_CN.yml</span></span>
<span class="line"><span>├── gui/                    ← GUI 配置</span></span>
<span class="line"><span>│   ├── forge_gui.yml       ← 锻造界面</span></span>
<span class="line"><span>│   ├── recipe_book.yml     ← 配方书界面</span></span>
<span class="line"><span>│   └── editor_gui.yml      ← 编辑器界面</span></span>
<span class="line"><span>├── recipes/                ← 锻造配方（可热重载）</span></span>
<span class="line"><span>│   └── example_sword.yml</span></span>
<span class="line"><span>├── blueprints/             ← 图纸定义（可热重载）</span></span>
<span class="line"><span>│   └── iron_sword.yml</span></span>
<span class="line"><span>├── materials/              ← 材料定义（可热重载）</span></span>
<span class="line"><span>│   └── common_materials.yml</span></span>
<span class="line"><span>├── playerdata/             ← 玩家数据（保底计数器、锻造历史等）</span></span>
<span class="line"><span>│   └── &lt;uuid&gt;.yml</span></span>
<span class="line"><span>└── backups/                ← 编辑器自动备份</span></span>
<span class="line"><span>    └── recipes/</span></span>
<span class="line"><span>        └── &lt;timestamp&gt;/</span></span></code></pre></div><div class="tip custom-block"><p class="custom-block-title">提示</p><p><code>recipes/</code>、<code>blueprints/</code>、<code>materials/</code> 三个目录都支持运行时热重载。通过编辑器修改时，系统会先在 <code>backups/</code> 下创建带时间戳的备份，再写入新文件并触发重载。手动改文件的话，需要执行 <code>/ef reload</code> 才能生效。</p></div><h2 id="关键服务" tabindex="-1">关键服务 <a class="header-anchor" href="#关键服务" aria-label="Permalink to &quot;关键服务&quot;">​</a></h2><table tabindex="0"><thead><tr><th>服务</th><th>做什么</th></tr></thead><tbody><tr><td><code>ForgeService</code></td><td>锻造核心，协调整个锻造流程</td></tr><tr><td><code>ForgeExecutionService</code></td><td>实际执行锻造逻辑（材料消耗、品质计算、产物生成）</td></tr><tr><td><code>RecipeBookGuiService</code></td><td>配方书界面的渲染和交互</td></tr><tr><td><code>ForgeGuiService</code></td><td>锻造界面的渲染和交互</td></tr><tr><td><code>EditorGuiService</code></td><td>编辑器界面的渲染和交互</td></tr><tr><td><code>EditorGuiRenderer</code></td><td>编辑器的渲染层，负责把编辑状态映射到 GUI 槽位</td></tr><tr><td><code>EditorPersistenceService</code></td><td>编辑器的持久化层：验证 → 备份 → 写入 → 重载</td></tr><tr><td><code>ForgeLayerSnapshotBuilder</code></td><td>把锻造结果构建成 layer snapshot 写入 PDC</td></tr><tr><td><code>ForgeItemRefreshService</code></td><td>签名变化时自动刷新已锻造物品的展示层</td></tr></tbody></table>`,19)])])}const E=a(e,[["render",l]]);export{c as __pageData,E as default};

import{_ as s,o as n,c as p,ae as e}from"./chunks/framework.DTwQaIIa.js";const u=JSON.parse('{"title":"GUI 界面","description":"","frontmatter":{},"headers":[],"relativePath":"modules/forge/gui.md","filePath":"modules/forge/gui.md"}'),t={name:"modules/forge/gui.md"};function l(i,a,c,d,o,r){return n(),p("div",null,[...a[0]||(a[0]=[e(`<h1 id="gui-界面" tabindex="-1">GUI 界面 <a class="header-anchor" href="#gui-界面" aria-label="Permalink to &quot;GUI 界面&quot;">​</a></h1><p>EmakiForge 有两套 GUI 界面：锻造界面（玩家用来锻造）、配方书（浏览可用配方）。所有 GUI 配置文件放在 <code>gui/</code> 目录下。</p><h2 id="锻造界面" tabindex="-1">锻造界面 <a class="header-anchor" href="#锻造界面" aria-label="Permalink to &quot;锻造界面&quot;">​</a></h2><h3 id="forge-gui-yml-布局" tabindex="-1">forge_gui.yml 布局 <a class="header-anchor" href="#forge-gui-yml-布局" aria-label="Permalink to &quot;forge_gui.yml 布局&quot;">​</a></h3><p>锻造界面是一个 5 行（45 格）的箱子界面：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>行 1: [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ]</span></span>
<span class="line"><span>行 2: [  ] [BP] [BP] [  ] [CP] [  ] [RM] [RM] [  ]</span></span>
<span class="line"><span>行 3: [  ] [BP] [BP] [  ] [CP] [  ] [RM] [RM] [  ]</span></span>
<span class="line"><span>行 4: [  ] [OM] [OM] [OM] [  ] [  ] [  ] [CF] [  ]</span></span>
<span class="line"><span>行 5: [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ]</span></span>
<span class="line"><span></span></span>
<span class="line"><span>BP = blueprint_inputs    图纸输入槽</span></span>
<span class="line"><span>CP = capacity_display    容量显示槽</span></span>
<span class="line"><span>RM = required_materials  必选材料槽</span></span>
<span class="line"><span>OM = optional_materials  可选材料槽</span></span>
<span class="line"><span>CF = confirm             确认按钮</span></span></code></pre></div><table tabindex="0"><thead><tr><th>槽位区域</th><th>槽位编号</th><th>说明</th></tr></thead><tbody><tr><td><code>blueprint_inputs</code></td><td>10, 11, 19, 20</td><td>放图纸的地方。配方要求什么图纸，就在这里放</td></tr><tr><td><code>capacity_display</code></td><td>13, 22</td><td>显示当前已用容量和最大容量</td></tr><tr><td><code>required_materials</code></td><td>15, 16, 24, 25</td><td>必选材料区域，这些材料必须放齐才能锻造</td></tr><tr><td><code>optional_materials</code></td><td>28, 29, 30</td><td>可选材料区域，放了有额外效果，不放也能锻造</td></tr><tr><td><code>confirm</code></td><td>34</td><td>确认锻造按钮，所有条件满足后点击执行锻造</td></tr></tbody></table><h2 id="配方书界面" tabindex="-1">配方书界面 <a class="header-anchor" href="#配方书界面" aria-label="Permalink to &quot;配方书界面&quot;">​</a></h2><h3 id="recipe-book-yml-布局" tabindex="-1">recipe_book.yml 布局 <a class="header-anchor" href="#recipe-book-yml-布局" aria-label="Permalink to &quot;recipe_book.yml 布局&quot;">​</a></h3><p>配方书是一个 6 行（54 格）的箱子界面，用来展示玩家有权限查看的所有配方：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>行 1: [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ]</span></span>
<span class="line"><span>行 2: [RL] [RL] [RL] [RL] [RL] [RL] [RL] [  ] [  ]</span></span>
<span class="line"><span>行 3: [RL] [RL] [RL] [RL] [RL] [RL] [RL] [  ] [  ]</span></span>
<span class="line"><span>行 4: [RL] [RL] [RL] [RL] [RL] [RL] [RL] [  ] [  ]</span></span>
<span class="line"><span>行 5: [RL] [RL] [RL] [RL] [RL] [RL] [RL] [  ] [  ]</span></span>
<span class="line"><span>行 6: [  ] [  ] [  ] [PV] [  ] [NX] [  ] [  ] [  ]</span></span>
<span class="line"><span></span></span>
<span class="line"><span>RL = recipe_list   配方列表槽位</span></span>
<span class="line"><span>PV = prev_page     上一页按钮</span></span>
<span class="line"><span>NX = next_page     下一页按钮</span></span></code></pre></div><table tabindex="0"><thead><tr><th>槽位区域</th><th>槽位编号</th><th>说明</th></tr></thead><tbody><tr><td><code>recipe_list</code></td><td>10-16, 19-25, 28-34, 37-43</td><td>配方展示区域，每页最多 28 个配方</td></tr><tr><td><code>prev_page</code></td><td>48</td><td>上一页</td></tr><tr><td><code>next_page</code></td><td>50</td><td>下一页</td></tr></tbody></table><h2 id="完整锻造执行流程" tabindex="-1">完整锻造执行流程 <a class="header-anchor" href="#完整锻造执行流程" aria-label="Permalink to &quot;完整锻造执行流程&quot;">​</a></h2><p>从玩家点击确认按钮到锻造完成，系统内部经历了这些步骤：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>玩家点击确认按钮 (confirm)</span></span>
<span class="line"><span>    ↓</span></span>
<span class="line"><span>canForge — 前置检查</span></span>
<span class="line"><span>    ├── 图纸是否匹配 blueprint_requirements</span></span>
<span class="line"><span>    ├── 必选材料是否齐全</span></span>
<span class="line"><span>    ├── 可选材料数量是否超过 optional_material_limit</span></span>
<span class="line"><span>    ├── 材料总容量是否超过 forge_capacity</span></span>
<span class="line"><span>    ├── 条件是否满足（condition_type + conditions）</span></span>
<span class="line"><span>    └── 权限是否通过</span></span>
<span class="line"><span>    ├── 任一检查失败 → 提示错误，中止</span></span>
<span class="line"><span>    └── 全部通过 ↓</span></span>
<span class="line"><span></span></span>
<span class="line"><span>prepareForge — 准备锻造</span></span>
<span class="line"><span>    ├── 收集所有材料的 effects</span></span>
<span class="line"><span>    ├── 汇总 stat_contribution</span></span>
<span class="line"><span>    ├── 汇总 quality_modify</span></span>
<span class="line"><span>    ├── 汇总 capacity_bonus</span></span>
<span class="line"><span>    └── 汇总 structured_presentation</span></span>
<span class="line"><span></span></span>
<span class="line"><span>execute — 执行锻造</span></span>
<span class="line"><span>    ├── 执行 action.pre 动作</span></span>
<span class="line"><span>    ├── 确定品质等级（走品质计算流程）</span></span>
<span class="line"><span>    ├── 把品质倍率乘到所有属性值上</span></span>
<span class="line"><span>    └── 消耗材料和图纸</span></span>
<span class="line"><span></span></span>
<span class="line"><span>build snapshot — 构建快照</span></span>
<span class="line"><span>    ├── ForgeLayerSnapshotBuilder 构建 layer snapshot</span></span>
<span class="line"><span>    ├── 写入 stat 数据</span></span>
<span class="line"><span>    ├── 写入 attribute 数据</span></span>
<span class="line"><span>    └── 写入 structured_presentation 数据</span></span>
<span class="line"><span></span></span>
<span class="line"><span>preview — 预览产物</span></span>
<span class="line"><span>    └── 生成输出物品并应用 meta_actions</span></span>
<span class="line"><span></span></span>
<span class="line"><span>give — 给予产物</span></span>
<span class="line"><span>    ├── 将产物放入玩家背包</span></span>
<span class="line"><span>    ├── 执行 action.success 动作</span></span>
<span class="line"><span>    └── 执行品质动作（如有）</span></span>
<span class="line"><span></span></span>
<span class="line"><span>record — 记录审计</span></span>
<span class="line"><span>    ├── 写入玩家数据文件</span></span>
<span class="line"><span>    └── 更新保底计数器</span></span></code></pre></div><div class="tip custom-block"><p class="custom-block-title">提示</p><p>整个锻造流程是同步执行的，保证物品操作的原子性。如果中途出了异常，系统会回滚已消耗的材料，不会出现&quot;材料扣了但没出东西&quot;的情况。</p></div>`,16)])])}const b=s(t,[["render",l]]);export{u as __pageData,b as default};

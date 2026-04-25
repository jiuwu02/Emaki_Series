import{_ as a,o as n,c as p,ae as e}from"./chunks/framework.DTwQaIIa.js";const k=JSON.parse('{"title":"宝石流程","description":"","frontmatter":{},"headers":[],"relativePath":"modules/gem/process.md","filePath":"modules/gem/process.md"}'),i={name:"modules/gem/process.md"};function l(t,s,c,d,o,h){return n(),p("div",null,[...s[0]||(s[0]=[e(`<h1 id="宝石流程" tabindex="-1">宝石流程 <a class="header-anchor" href="#宝石流程" aria-label="Permalink to &quot;宝石流程&quot;">​</a></h1><p>这里详细描述 EmakiGem 四个核心操作的完整流程，以及底层的数据模型。如果你只是写配置，可以跳过这页；如果你想理解系统内部是怎么运作的，或者需要排查问题，这些信息会很有用。</p><h2 id="镶嵌流程-inlay" tabindex="-1">镶嵌流程 (Inlay) <a class="header-anchor" href="#镶嵌流程-inlay" aria-label="Permalink to &quot;镶嵌流程 (Inlay)&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>玩家放入装备和宝石 → 点击确认</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 1. 验证装备和宝石有效性</span></span>
<span class="line"><span>    │     检查物品非空、非空气</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 2. 匹配装备模板 (GemItemMatcher)</span></span>
<span class="line"><span>    │     遍历所有 GemItemDefinition，通过 item_sources / slot_groups / lore_contains 匹配</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 3. 检查目标孔位</span></span>
<span class="line"><span>    │     确认选中的孔位已开启且未被占用</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 4. 检查兼容性</span></span>
<span class="line"><span>    │     宝石的 socket_compatibility 是否包含孔位的 type</span></span>
<span class="line"><span>    │     检查 max_same_type 和 max_same_id 限制</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 5. 计算成功率</span></span>
<span class="line"><span>    │     使用 inlay_success.rate_formula 或 default_chance</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 6. 收费时机</span></span>
<span class="line"><span>    │     根据 failure_action 决定：</span></span>
<span class="line"><span>    │     - return_gem: 失败前收费（费用不退）</span></span>
<span class="line"><span>    │     - destroy_gem: 失败前收费</span></span>
<span class="line"><span>    │     - none: 成功后收费</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 7. 掷骰判定</span></span>
<span class="line"><span>    │     随机数 &lt; 成功率 → 成功</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 成功路径:</span></span>
<span class="line"><span>    │     ├─ 8a. 更新 GemState（添加 socketAssignment）</span></span>
<span class="line"><span>    │     ├─ 9a. 重建物品（applyState）</span></span>
<span class="line"><span>    │     └─ 10a. 消耗宝石物品</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    └─ 失败路径:</span></span>
<span class="line"><span>          ├─ failure_action = return_gem → 宝石退回玩家</span></span>
<span class="line"><span>          ├─ failure_action = destroy_gem → 宝石销毁</span></span>
<span class="line"><span>          └─ failure_action = none → 宝石退回</span></span></code></pre></div><p>注意第 6 步的收费时机：如果 <code>failure_action</code> 是 <code>return_gem</code> 或 <code>destroy_gem</code>，费用在判定之前就扣了，失败也不退。这是为了防止玩家零成本反复尝试。而 <code>none</code> 模式下只有成功才收费，因为宝石本身就会退回。</p><h2 id="开孔流程-open" tabindex="-1">开孔流程 (Open) <a class="header-anchor" href="#开孔流程-open" aria-label="Permalink to &quot;开孔流程 (Open)&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>玩家放入装备和开孔器 → 点击确认</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 1. 匹配开孔器 (SocketOpenerService)</span></span>
<span class="line"><span>    │     遍历 socket_openers 配置，匹配 item_source</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 2. 查找第一个未开启的兼容孔位</span></span>
<span class="line"><span>    │     遍历装备模板的 slots，找到 type 在 opens_gem_types 中且未开启的孔位</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 3. 更新 GemState（添加 openedSlotIndex）</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 4. 重建物品（applyState）</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 5. 消耗开孔器（如果 consume_on_success = true）</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    └─ 6. 执行 success_actions</span></span></code></pre></div><p>开孔是按顺序来的——系统会找到第一个未开启且类型兼容的孔位。所以孔位在模板中的定义顺序很重要，它决定了开孔的先后。</p><h2 id="提取流程-extract" tabindex="-1">提取流程 (Extract) <a class="header-anchor" href="#提取流程-extract" aria-label="Permalink to &quot;提取流程 (Extract)&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>玩家选择要提取的孔位 → 点击确认</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 1. 验证选中孔位有宝石</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 2. 收取提取费用 (extract_cost)</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 3. 确定返还模式 (extract_return.mode)</span></span>
<span class="line"><span>    │     ├─ exact: 返还原宝石（含等级）</span></span>
<span class="line"><span>    │     ├─ downgrade: 返还降级宝石</span></span>
<span class="line"><span>    │     └─ degraded: 概率返还降级或原宝石</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 4. 更新 GemState（移除 socketAssignment，孔位保持开启）</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 5. 重建物品（applyState）</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    └─ 6. 返还宝石到玩家背包</span></span></code></pre></div><p>提取后孔位仍然是开启状态，不需要重新开孔。这是有意为之——开孔是对装备的永久改造。</p><h2 id="升级流程-upgrade" tabindex="-1">升级流程 (Upgrade) <a class="header-anchor" href="#升级流程-upgrade" aria-label="Permalink to &quot;升级流程 (Upgrade)&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>玩家放入宝石 → 确认升级</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 1. 验证宝石有效性</span></span>
<span class="line"><span>    │     检查宝石定义存在且 upgrade.enabled = true</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 2. 检查等级上限</span></span>
<span class="line"><span>    │     当前等级 &lt; max_level</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 3. 验证升级材料</span></span>
<span class="line"><span>    │     检查 economy.currencies 和材料是否满足</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 4. 收取费用</span></span>
<span class="line"><span>    │     扣除货币和消耗材料</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 5. 掷骰判定</span></span>
<span class="line"><span>    │     使用 success_rates[next_level] 或 global_success_rates</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 成功:</span></span>
<span class="line"><span>    │     生成新等级的宝石物品，替换原宝石</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    └─ 失败:</span></span>
<span class="line"><span>          根据 failure_penalty / global_failure_penalty:</span></span>
<span class="line"><span>          ├─ none: 保持当前等级</span></span>
<span class="line"><span>          ├─ downgrade: 降低一级</span></span>
<span class="line"><span>          └─ destroy: 销毁宝石</span></span></code></pre></div><p>升级的成功率查找顺序：先看宝石定义中的 <code>success_rates</code>，没有再看全局的 <code>global_success_rates</code>。失败惩罚同理。这样你可以为特殊宝石设置独立的升级规则。</p><h2 id="applystate-内部流程" tabindex="-1">applyState 内部流程 <a class="header-anchor" href="#applystate-内部流程" aria-label="Permalink to &quot;applyState 内部流程&quot;">​</a></h2><p><code>applyState</code> 是宝石系统的核心重建流程。每次宝石状态发生变化（镶嵌、提取、开孔），都会调用它来重新生成物品的展示和属性：</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>1. GemSnapshotBuilder 构建宝石层快照</span></span>
<span class="line"><span>   ├─ 遍历所有 socketAssignments</span></span>
<span class="line"><span>   ├─ 为每个镶嵌的宝石收集 stats / attributes</span></span>
<span class="line"><span>   ├─ 构建 structured_presentation 数据</span></span>
<span class="line"><span>   └─ 生成 LayerSnapshot</span></span>
<span class="line"><span></span></span>
<span class="line"><span>2. AssemblyService.preview 预览重建</span></span>
<span class="line"><span>   ├─ 将宝石层快照与其他层（锻造、强化等）合并</span></span>
<span class="line"><span>   ├─ 渲染名称贡献（name_contributions）</span></span>
<span class="line"><span>   └─ 渲染 Lore 段落（lore_sections）</span></span>
<span class="line"><span></span></span>
<span class="line"><span>3. GemPdcAttributeWriter 写入属性</span></span>
<span class="line"><span>   ├─ 将宝石属性写入 PDC（通过 PdcAttributeApi）</span></span>
<span class="line"><span>   └─ 仅在 EmakiAttribute 可用时执行</span></span></code></pre></div><h2 id="gemstate-数据模型" tabindex="-1">GemState 数据模型 <a class="header-anchor" href="#gemstate-数据模型" aria-label="Permalink to &quot;GemState 数据模型&quot;">​</a></h2><p>宝石状态数据存储在装备 PDC 中，记录了所有孔位的开启和镶嵌情况。详细的数据结构定义和编程接口参见 <a href="./api.html">API 与集成</a> 页面。</p><h3 id="gemiteminstance" tabindex="-1">GemItemInstance <a class="header-anchor" href="#gemiteminstance" aria-label="Permalink to &quot;GemItemInstance&quot;">​</a></h3><p>每个镶嵌的宝石实例包含：</p><table tabindex="0"><thead><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead><tbody><tr><td><code>gemId</code></td><td>string</td><td>宝石定义 ID</td></tr><tr><td><code>level</code></td><td>int</td><td>宝石等级</td></tr><tr><td><code>token</code></td><td>string</td><td>唯一标识令牌，格式为 <code>{gemId}:{level}:{随机串}</code></td></tr></tbody></table><p><code>token</code> 的作用是唯一标识一次镶嵌操作，即使两颗相同的宝石镶嵌到同一件装备上，它们的 token 也不同。</p><h3 id="pdc-序列化格式" tabindex="-1">PDC 序列化格式 <a class="header-anchor" href="#pdc-序列化格式" aria-label="Permalink to &quot;PDC 序列化格式&quot;">​</a></h3><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 存储在物品 PDC 中的审计数据</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">item_definition_id</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;diamond_sword&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">opened_slots</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: [</span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">0</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">, </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">1</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">, </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">2</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">]</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">socket_assignments</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">  0</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    gem_id</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;ruby_attack&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    level</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">3</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    token</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;ruby_attack:3:abc123&quot;</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">  1</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    gem_id</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;sapphire_defense&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    level</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">1</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    token</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;sapphire_defense:1:def456&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">updated_at</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">1700000000000</span></span></code></pre></div><div class="tip custom-block"><p class="custom-block-title">提示</p><p><code>GemState</code> 是不可变记录类型（record），每次修改都会创建新实例。<code>withOpenedSlots()</code> 和 <code>withAssignment()</code> 方法返回新的 <code>GemState</code> 对象，原对象不受影响。</p></div><div class="warning custom-block"><p class="custom-block-title">注意</p><p><code>applyState</code> 会触发完整的物品重建流程，包括所有已注册的装配层（宝石、锻造、强化等）。这保证了跨模块状态的一致性，但也意味着每次宝石操作都会刷新整个物品的名称和 Lore。如果你发现物品展示异常，可以检查是否有其他装配层的配置冲突。</p></div>`,27)])])}const g=a(i,[["render",l]]);export{k as __pageData,g as default};

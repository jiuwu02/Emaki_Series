import{_ as i,o as a,c as n,ae as l}from"./chunks/framework.DTwQaIIa.js";const g=JSON.parse('{"title":"宝石定义","description":"","frontmatter":{},"headers":[],"relativePath":"modules/gem/gems.md","filePath":"modules/gem/gems.md"}'),t={name:"modules/gem/gems.md"};function p(h,s,k,e,d,E){return a(),n("div",null,[...s[0]||(s[0]=[l(`<h1 id="宝石定义" tabindex="-1">宝石定义 <a class="header-anchor" href="#宝石定义" aria-label="Permalink to &quot;宝石定义&quot;">​</a></h1><p>宝石定义文件存放在 <code>gems/</code> 目录下，每个文件定义一种宝石。一颗宝石的定义涵盖了它的类型、属性加成、孔位兼容性、展示方式、镶嵌/提取费用，以及升级系统的完整配置。</p><h2 id="完整-yaml-格式" tabindex="-1">完整 YAML 格式 <a class="header-anchor" href="#完整-yaml-格式" aria-label="Permalink to &quot;完整 YAML 格式&quot;">​</a></h2><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># gems/ruby_attack.yml</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 宝石唯一标识</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">id</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;ruby_attack&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 宝石类型：attack / defense / utility / universal</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">gem_type</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;attack&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 宝石品阶（用于成功率计算等）</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">tier</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">1</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 宝石物品来源</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">item_source</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;minecraft-redstone&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 显示名称</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">display_name</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;red&gt;红宝石&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 物品 Lore</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">lore</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">  - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;gray&gt;攻击型宝石&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">  - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;gray&gt;攻击力 +10&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 属性加成（键值对，键为属性 ID）</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">stats</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  physical_attack</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">10</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  critical_chance</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">5.0</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># EmakiAttribute 属性写入（可选，需要 EA 软依赖）</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">attributes</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  physical_attack</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    value</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">10</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    operation</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;ADD&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  critical_chance</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    value</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">5.0</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    operation</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;ADD&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 孔位兼容性 —— 此宝石可以镶嵌到哪些类型的孔位</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">socket_compatibility</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">  - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;attack&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">  - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;universal&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ============================================================</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 结构化展示 (Structured Presentation)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 控制宝石镶嵌后在装备 Lore 中的展示</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ============================================================</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">structured_presentation</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 名称贡献 —— 镶嵌后追加到装备名称</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  name_contributions</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">template</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot; &lt;red&gt;[红宝石]&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      position</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;SUFFIX&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # Lore 段落贡献</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  lore_sections</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">section_id</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;gem_stats&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      lines</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;gray&gt;  ├ &lt;red&gt;攻击力 +{physical_attack}&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;gray&gt;  └ &lt;red&gt;暴击率 +{critical_chance}%&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ============================================================</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 镶嵌费用 (Inlay Cost)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ============================================================</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">inlay_cost</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 货币费用</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  currencies</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">provider</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;vault&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      amount</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">100</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 材料费用</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  materials</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">source</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;minecraft-gold_ingot&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      amount</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">5</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ============================================================</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 提取费用与返还 (Extract)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ============================================================</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">extract_cost</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  currencies</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    - </span><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">provider</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;vault&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      amount</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">50</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">extract_return</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 返还模式：exact / downgrade / degraded</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  mode</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;exact&quot;</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # downgrade 模式下降低的等级数</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  downgrade_levels</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">1</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # degraded 模式下返还降级宝石的概率（百分比）</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  degraded_chance</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">50</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ============================================================</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 升级系统 (Upgrade)</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># ============================================================</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">upgrade</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  enabled</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">true</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 最大等级</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  max_level</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">5</span></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 升级 GUI 模板</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  gui_template</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;upgrade/default&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 升级经济</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  economy</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    currencies</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">      - </span><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">provider</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;vault&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        amount_formula</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;level * 200&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 每级成功率（覆盖全局 global_success_rates）</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  success_rates</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">    1</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">100</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">    2</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">90</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">    3</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">70</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">    4</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">50</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">    5</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">30</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 失败惩罚（覆盖全局 global_failure_penalty）</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  failure_penalty</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;none&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">  # 等级定义</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">  levels</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">    1</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      display_name</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;red&gt;红宝石 I&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      stats</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        physical_attack</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">10</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      lore</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;gray&gt;攻击力 +10&quot;</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">    2</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      display_name</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;red&gt;红宝石 II&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      stats</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        physical_attack</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">20</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      lore</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;gray&gt;攻击力 +20&quot;</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">    3</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      display_name</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;red&gt;红宝石 III&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      stats</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        physical_attack</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">35</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      lore</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;gray&gt;攻击力 +35&quot;</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">    4</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      display_name</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;red&gt;红宝石 IV&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      stats</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        physical_attack</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">55</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      lore</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;gray&gt;攻击力 +55&quot;</span></span>
<span class="line"><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">    5</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      display_name</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;red&gt;红宝石 V&quot;</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      stats</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">        physical_attack</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">80</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">      lore</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;&lt;gray&gt;攻击力 +80&quot;</span></span></code></pre></div><h2 id="宝石类型" tabindex="-1">宝石类型 <a class="header-anchor" href="#宝石类型" aria-label="Permalink to &quot;宝石类型&quot;">​</a></h2><p>宝石类型决定了它能镶嵌到哪些孔位。孔位也有类型，两者通过 <code>socket_compatibility</code> 匹配。</p><table tabindex="0"><thead><tr><th>类型</th><th>说明</th></tr></thead><tbody><tr><td><code>attack</code></td><td>攻击型宝石，通常加攻击力、暴击等</td></tr><tr><td><code>defense</code></td><td>防御型宝石，通常加防御力、生命值等</td></tr><tr><td><code>utility</code></td><td>功能型宝石，通常加移速、经验加成等</td></tr><tr><td><code>universal</code></td><td>万能型宝石，可以镶嵌到任何类型的孔位</td></tr></tbody></table><p>类型本身只是一个字符串标签，实际效果完全由 <code>stats</code> 和 <code>attributes</code> 决定。你也可以自定义类型名称，只要和装备模板的孔位类型对应上就行。</p><h2 id="技能效果" tabindex="-1">技能效果 <a class="header-anchor" href="#技能效果" aria-label="Permalink to &quot;技能效果&quot;">​</a></h2><p>宝石定义可以通过 <code>effects</code> 列表声明技能 ID。镶嵌宝石后，装备的 PDC 中会写入所有已镶嵌宝石声明的技能 ID，EmakiSkills 扫描装备时就能解锁对应技能。</p><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">effects</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">  - </span><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">type</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">skill</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    skill</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;mana_shield&quot;</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># 或者一次声明多个</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">effects</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">  - </span><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">type</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">skill</span></span>
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">    skills</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">:</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">      - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;mana_shield&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">      - </span><span style="--shiki-light:#032F62;--shiki-dark:#9ECBFF;">&quot;arcane_barrier&quot;</span></span></code></pre></div><p>升级等级定义（<code>upgrade.levels</code>）中也可以声明 <code>effects</code>，高等级的技能列表会覆盖基础定义的。提取宝石后，对应的技能 ID 会从装备上移除。</p><h2 id="默认宝石一览" tabindex="-1">默认宝石一览 <a class="header-anchor" href="#默认宝石一览" aria-label="Permalink to &quot;默认宝石一览&quot;">​</a></h2><table tabindex="0"><thead><tr><th>宝石 ID</th><th>类型</th><th>品阶</th><th>可升级</th><th>说明</th></tr></thead><tbody><tr><td><code>ruby_attack</code></td><td>attack</td><td>1</td><td>是（最高 5 级）</td><td>红宝石，提供攻击力</td></tr><tr><td><code>sapphire_defense</code></td><td>defense</td><td>1</td><td>是（最高 5 级）</td><td>蓝宝石，提供防御力</td></tr><tr><td><code>emerald_utility</code></td><td>utility</td><td>1</td><td>是（最高 3 级）</td><td>绿宝石，提供移速加成</td></tr></tbody></table><h2 id="提取返还模式" tabindex="-1">提取返还模式 <a class="header-anchor" href="#提取返还模式" aria-label="Permalink to &quot;提取返还模式&quot;">​</a></h2><p>提取宝石时，返还给玩家的宝石不一定和镶嵌时完全一样，取决于 <code>extract_return.mode</code> 的设置：</p><table tabindex="0"><thead><tr><th>模式</th><th>说明</th></tr></thead><tbody><tr><td><code>exact</code></td><td>原样返还，等级不变</td></tr><tr><td><code>downgrade</code></td><td>返还降低 <code>downgrade_levels</code> 级的宝石。如果降到 0 级以下则销毁</td></tr><tr><td><code>degraded</code></td><td>有 <code>degraded_chance</code>% 概率返还降级宝石，否则返还原宝石。适合想加点风险但又不想太狠的场景</td></tr></tbody></table><h2 id="升级失败惩罚" tabindex="-1">升级失败惩罚 <a class="header-anchor" href="#升级失败惩罚" aria-label="Permalink to &quot;升级失败惩罚&quot;">​</a></h2><p>升级失败时的惩罚可以在宝石定义中单独设置（<code>failure_penalty</code>），也可以用全局配置（<code>global_failure_penalty</code>）。宝石定义的设置优先级更高。</p><table tabindex="0"><thead><tr><th>惩罚</th><th>说明</th></tr></thead><tbody><tr><td><code>none</code></td><td>无惩罚，保持当前等级</td></tr><tr><td><code>downgrade</code></td><td>降低一级</td></tr><tr><td><code>destroy</code></td><td>销毁宝石</td></tr></tbody></table>`,20)])])}const y=i(t,[["render",p]]);export{g as __pageData,y as default};

import{_ as n,o as s,c as e,ae as p}from"./chunks/framework.DTwQaIIa.js";const g=JSON.parse('{"title":"GUI","description":"","frontmatter":{},"headers":[],"relativePath":"en/modules/forge/gui.md","filePath":"en/modules/forge/gui.md"}'),t={name:"en/modules/forge/gui.md"};function i(l,a,o,c,r,d){return s(),e("div",null,[...a[0]||(a[0]=[p(`<h1 id="gui" tabindex="-1">GUI <a class="header-anchor" href="#gui" aria-label="Permalink to &quot;GUI&quot;">​</a></h1><p>EmakiForge provides two GUI interfaces: the forging interface and the recipe book interface. All GUI configuration files are stored in the <code>gui/</code> directory.</p><h2 id="forging-interface" tabindex="-1">Forging Interface <a class="header-anchor" href="#forging-interface" aria-label="Permalink to &quot;Forging Interface&quot;">​</a></h2><h3 id="forge-gui-yml-layout" tabindex="-1">forge_gui.yml Layout <a class="header-anchor" href="#forge-gui-yml-layout" aria-label="Permalink to &quot;forge_gui.yml Layout&quot;">​</a></h3><p>The forging interface is a 5-row (45-slot) chest GUI:</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Row 1: [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ]</span></span>
<span class="line"><span>Row 2: [  ] [BP] [BP] [  ] [CP] [  ] [RM] [RM] [  ]</span></span>
<span class="line"><span>Row 3: [  ] [BP] [BP] [  ] [CP] [  ] [RM] [RM] [  ]</span></span>
<span class="line"><span>Row 4: [  ] [OM] [OM] [OM] [  ] [  ] [  ] [CF] [  ]</span></span>
<span class="line"><span>Row 5: [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ]</span></span>
<span class="line"><span></span></span>
<span class="line"><span>BP = blueprint_inputs    Blueprint input slots</span></span>
<span class="line"><span>CP = capacity_display    Capacity display slots</span></span>
<span class="line"><span>RM = required_materials  Required material slots</span></span>
<span class="line"><span>OM = optional_materials  Optional material slots</span></span>
<span class="line"><span>CF = confirm             Confirm button</span></span></code></pre></div><table tabindex="0"><thead><tr><th>Slot Area</th><th>Slot Numbers</th><th>Description</th></tr></thead><tbody><tr><td><code>blueprint_inputs</code></td><td>10, 11, 19, 20</td><td>Blueprint placement area</td></tr><tr><td><code>capacity_display</code></td><td>13, 22</td><td>Capacity display (current/max)</td></tr><tr><td><code>required_materials</code></td><td>15, 16, 24, 25</td><td>Required material placement area</td></tr><tr><td><code>optional_materials</code></td><td>28, 29, 30</td><td>Optional material placement area</td></tr><tr><td><code>confirm</code></td><td>34</td><td>Confirm forging button</td></tr></tbody></table><h2 id="recipe-book-interface" tabindex="-1">Recipe Book Interface <a class="header-anchor" href="#recipe-book-interface" aria-label="Permalink to &quot;Recipe Book Interface&quot;">​</a></h2><h3 id="recipe-book-yml-layout" tabindex="-1">recipe_book.yml Layout <a class="header-anchor" href="#recipe-book-yml-layout" aria-label="Permalink to &quot;recipe_book.yml Layout&quot;">​</a></h3><p>The recipe book interface is a 6-row (54-slot) chest GUI:</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Row 1: [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ] [  ]</span></span>
<span class="line"><span>Row 2: [RL] [RL] [RL] [RL] [RL] [RL] [RL] [  ] [  ]</span></span>
<span class="line"><span>Row 3: [RL] [RL] [RL] [RL] [RL] [RL] [RL] [  ] [  ]</span></span>
<span class="line"><span>Row 4: [RL] [RL] [RL] [RL] [RL] [RL] [RL] [  ] [  ]</span></span>
<span class="line"><span>Row 5: [RL] [RL] [RL] [RL] [RL] [RL] [RL] [  ] [  ]</span></span>
<span class="line"><span>Row 6: [  ] [  ] [  ] [PV] [  ] [NX] [  ] [  ] [  ]</span></span>
<span class="line"><span></span></span>
<span class="line"><span>RL = recipe_list   Recipe list slots</span></span>
<span class="line"><span>PV = prev_page     Previous page button</span></span>
<span class="line"><span>NX = next_page     Next page button</span></span></code></pre></div><table tabindex="0"><thead><tr><th>Slot Area</th><th>Slot Numbers</th><th>Description</th></tr></thead><tbody><tr><td><code>recipe_list</code></td><td>10-16, 19-25, 28-34, 37-43</td><td>Recipe display area (28 per page)</td></tr><tr><td><code>prev_page</code></td><td>48</td><td>Previous page</td></tr><tr><td><code>next_page</code></td><td>50</td><td>Next page</td></tr></tbody></table><h2 id="complete-forging-execution-pipeline" tabindex="-1">Complete Forging Execution Pipeline <a class="header-anchor" href="#complete-forging-execution-pipeline" aria-label="Permalink to &quot;Complete Forging Execution Pipeline&quot;">​</a></h2><p>The complete pipeline from when a player clicks the confirm button to forging completion:</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Player clicks confirm button (confirm)</span></span>
<span class="line"><span>    ↓</span></span>
<span class="line"><span>canForge — Pre-checks</span></span>
<span class="line"><span>    ├── Check if blueprint matches blueprint_requirements</span></span>
<span class="line"><span>    ├── Check if required materials are complete</span></span>
<span class="line"><span>    ├── Check if optional material count exceeds optional_material_limit</span></span>
<span class="line"><span>    ├── Check if total material capacity exceeds forge_capacity</span></span>
<span class="line"><span>    ├── Check if conditions are met (condition_type + conditions)</span></span>
<span class="line"><span>    └── Check permissions</span></span>
<span class="line"><span>    ├── Any check fails → Display error, abort</span></span>
<span class="line"><span>    └── All pass ↓</span></span>
<span class="line"><span></span></span>
<span class="line"><span>prepareForge — Prepare forging</span></span>
<span class="line"><span>    ├── Collect all material effects</span></span>
<span class="line"><span>    ├── Aggregate stat_contribution</span></span>
<span class="line"><span>    ├── Aggregate quality_modify</span></span>
<span class="line"><span>    ├── Aggregate capacity_bonus</span></span>
<span class="line"><span>    └── Aggregate structured_presentation</span></span>
<span class="line"><span></span></span>
<span class="line"><span>execute — Execute forging</span></span>
<span class="line"><span>    ├── Execute action.pre actions</span></span>
<span class="line"><span>    ├── Determine quality tier (quality calculation pipeline)</span></span>
<span class="line"><span>    ├── Apply quality multiplier to all attribute values</span></span>
<span class="line"><span>    └── Consume materials and blueprint</span></span>
<span class="line"><span></span></span>
<span class="line"><span>build snapshot — Build snapshot</span></span>
<span class="line"><span>    ├── ForgeLayerSnapshotBuilder builds layer snapshot</span></span>
<span class="line"><span>    ├── Write stat data</span></span>
<span class="line"><span>    ├── Write attribute data</span></span>
<span class="line"><span>    └── Write structured_presentation data</span></span>
<span class="line"><span></span></span>
<span class="line"><span>preview — Preview product</span></span>
<span class="line"><span>    └── Generate output item and apply meta_actions</span></span>
<span class="line"><span></span></span>
<span class="line"><span>give — Give product</span></span>
<span class="line"><span>    ├── Place product in player inventory</span></span>
<span class="line"><span>    ├── Execute action.success actions</span></span>
<span class="line"><span>    └── Execute quality actions (if any)</span></span>
<span class="line"><span></span></span>
<span class="line"><span>record — Record audit</span></span>
<span class="line"><span>    ├── Write to player data file</span></span>
<span class="line"><span>    └── Update pity counter</span></span></code></pre></div><div class="tip custom-block"><p class="custom-block-title">Tip</p><p>The entire forging pipeline executes synchronously to ensure atomicity of item operations. If an exception occurs at any stage, the system will roll back consumed materials.</p></div>`,16)])])}const h=n(t,[["render",i]]);export{g as __pageData,h as default};

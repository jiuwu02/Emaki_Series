import{_ as a,o as n,c as e,ae as p}from"./chunks/framework.DTwQaIIa.js";const m=JSON.parse('{"title":"Gem Processes","description":"","frontmatter":{},"headers":[],"relativePath":"en/modules/gem/process.md","filePath":"en/modules/gem/process.md"}'),i={name:"en/modules/gem/process.md"};function t(l,s,c,r,o,d){return n(),e("div",null,[...s[0]||(s[0]=[p(`<h1 id="gem-processes" tabindex="-1">Gem Processes <a class="header-anchor" href="#gem-processes" aria-label="Permalink to &quot;Gem Processes&quot;">​</a></h1><p>This page describes the complete processes and internal data models for EmakiGem&#39;s four major operations in detail.</p><h2 id="inlay-process" tabindex="-1">Inlay Process <a class="header-anchor" href="#inlay-process" aria-label="Permalink to &quot;Inlay Process&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Player places equipment and gem → Clicks confirm</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 1. Validate equipment and gem</span></span>
<span class="line"><span>    │     Check items are non-null, non-air</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 2. Match equipment template (GemItemMatcher)</span></span>
<span class="line"><span>    │     Iterate all GemItemDefinitions, match via item_sources / slot_groups / lore_contains</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 3. Check target socket</span></span>
<span class="line"><span>    │     Confirm the selected socket is opened and unoccupied</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 4. Check compatibility</span></span>
<span class="line"><span>    │     Whether the gem&#39;s socket_compatibility includes the socket&#39;s type</span></span>
<span class="line"><span>    │     Check max_same_type and max_same_id limits</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 5. Calculate success rate</span></span>
<span class="line"><span>    │     Use inlay_success.rate_formula or default_chance</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 6. Fee timing</span></span>
<span class="line"><span>    │     Based on failure_action:</span></span>
<span class="line"><span>    │     - return_gem: Charge before failure (fee non-refundable)</span></span>
<span class="line"><span>    │     - destroy_gem: Charge before failure</span></span>
<span class="line"><span>    │     - none: Charge after success</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 7. Roll dice</span></span>
<span class="line"><span>    │     Random number &lt; success rate → Success</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ Success path:</span></span>
<span class="line"><span>    │     ├─ 8a. Update GemState (add socketAssignment)</span></span>
<span class="line"><span>    │     ├─ 9a. Rebuild item (applyState)</span></span>
<span class="line"><span>    │     └─ 10a. Consume gem item</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    └─ Failure path:</span></span>
<span class="line"><span>          ├─ failure_action = return_gem → Gem returned to player</span></span>
<span class="line"><span>          ├─ failure_action = destroy_gem → Gem destroyed</span></span>
<span class="line"><span>          └─ failure_action = none → Gem returned</span></span></code></pre></div><h2 id="socket-opening-process" tabindex="-1">Socket Opening Process <a class="header-anchor" href="#socket-opening-process" aria-label="Permalink to &quot;Socket Opening Process&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Player places equipment and socket opener → Clicks confirm</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 1. Match socket opener (SocketOpenerService)</span></span>
<span class="line"><span>    │     Iterate socket_openers config, match item_source</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 2. Find the first unopened compatible socket</span></span>
<span class="line"><span>    │     Iterate the equipment template&#39;s slots, find a socket whose type is in opens_gem_types and is not yet opened</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 3. Update GemState (add openedSlotIndex)</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 4. Rebuild item (applyState)</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 5. Consume socket opener (if consume_on_success = true)</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    └─ 6. Execute success_actions</span></span></code></pre></div><h2 id="extraction-process" tabindex="-1">Extraction Process <a class="header-anchor" href="#extraction-process" aria-label="Permalink to &quot;Extraction Process&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Player selects the socket to extract from → Clicks confirm</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 1. Validate selected socket has a gem</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 2. Charge extraction fee (extract_cost)</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 3. Determine return mode (extract_return.mode)</span></span>
<span class="line"><span>    │     ├─ exact: Return original gem (including level)</span></span>
<span class="line"><span>    │     ├─ downgrade: Return downgraded gem</span></span>
<span class="line"><span>    │     └─ degraded: Probabilistically return degraded or original gem</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 4. Update GemState (remove socketAssignment, socket remains opened)</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 5. Rebuild item (applyState)</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    └─ 6. Return gem to player&#39;s inventory</span></span></code></pre></div><h2 id="upgrade-process" tabindex="-1">Upgrade Process <a class="header-anchor" href="#upgrade-process" aria-label="Permalink to &quot;Upgrade Process&quot;">​</a></h2><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>Player places gem → Confirms upgrade</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 1. Validate gem</span></span>
<span class="line"><span>    │     Check gem definition exists and upgrade.enabled = true</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 2. Check level cap</span></span>
<span class="line"><span>    │     Current level &lt; max_level</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 3. Validate upgrade materials</span></span>
<span class="line"><span>    │     Check economy.currencies and materials are satisfied</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 4. Charge fees</span></span>
<span class="line"><span>    │     Deduct currency and consume materials</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ 5. Roll dice</span></span>
<span class="line"><span>    │     Use success_rates[next_level] or global_success_rates</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    ├─ Success:</span></span>
<span class="line"><span>    │     Generate new level gem item, replace original gem</span></span>
<span class="line"><span>    │</span></span>
<span class="line"><span>    └─ Failure:</span></span>
<span class="line"><span>          Based on failure_penalty / global_failure_penalty:</span></span>
<span class="line"><span>          ├─ none: Maintain current level</span></span>
<span class="line"><span>          ├─ downgrade: Downgrade by one level</span></span>
<span class="line"><span>          └─ destroy: Destroy the gem</span></span></code></pre></div><h2 id="applystate-internal-flow" tabindex="-1">applyState Internal Flow <a class="header-anchor" href="#applystate-internal-flow" aria-label="Permalink to &quot;applyState Internal Flow&quot;">​</a></h2><p><code>applyState</code> is the core rebuild flow of the gem system, called after every state change:</p><div class="language- vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang"></span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span>1. GemSnapshotBuilder builds the gem layer snapshot</span></span>
<span class="line"><span>   ├─ Iterate all socketAssignments</span></span>
<span class="line"><span>   ├─ Collect stats / attributes for each inlaid gem</span></span>
<span class="line"><span>   ├─ Build structured_presentation data</span></span>
<span class="line"><span>   └─ Generate LayerSnapshot</span></span>
<span class="line"><span></span></span>
<span class="line"><span>2. AssemblyService.preview previews the rebuild</span></span>
<span class="line"><span>   ├─ Merge gem layer snapshot with other layers (forging, enhancement, etc.)</span></span>
<span class="line"><span>   ├─ Render name contributions (name_contributions)</span></span>
<span class="line"><span>   └─ Render Lore sections (lore_sections)</span></span>
<span class="line"><span></span></span>
<span class="line"><span>3. GemPdcAttributeWriter writes attributes</span></span>
<span class="line"><span>   ├─ Write gem attributes to PDC (via PdcAttributeApi)</span></span>
<span class="line"><span>   └─ Only executes when EmakiAttribute is available</span></span></code></pre></div><h2 id="gemstate-data-model" tabindex="-1">GemState Data Model <a class="header-anchor" href="#gemstate-data-model" aria-label="Permalink to &quot;GemState Data Model&quot;">​</a></h2><p>Gem state data is stored in the equipment&#39;s PDC, recording all socket opening and inlay information. See the <a href="./api.html">API &amp; Integration</a> page for detailed data structure definitions and programming interfaces.</p><h3 id="gemiteminstance" tabindex="-1">GemItemInstance <a class="header-anchor" href="#gemiteminstance" aria-label="Permalink to &quot;GemItemInstance&quot;">​</a></h3><p>Each inlaid gem instance contains:</p><table tabindex="0"><thead><tr><th>Field</th><th>Type</th><th>Description</th></tr></thead><tbody><tr><td><code>gemId</code></td><td>string</td><td>Gem definition ID</td></tr><tr><td><code>level</code></td><td>int</td><td>Gem level</td></tr><tr><td><code>token</code></td><td>string</td><td>Unique identification token</td></tr></tbody></table><h3 id="pdc-serialization-format" tabindex="-1">PDC Serialization Format <a class="header-anchor" href="#pdc-serialization-format" aria-label="Permalink to &quot;PDC Serialization Format&quot;">​</a></h3><div class="language-yaml vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">yaml</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;"># Audit data stored in item PDC</span></span>
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
<span class="line"><span style="--shiki-light:#22863A;--shiki-dark:#85E89D;">updated_at</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">: </span><span style="--shiki-light:#005CC5;--shiki-dark:#79B8FF;">1700000000000</span></span></code></pre></div><div class="tip custom-block"><p class="custom-block-title">Tip</p><p><code>GemState</code> is an immutable record type. Every modification creates a new instance. The <code>withOpenedSlots()</code> and <code>withAssignment()</code> methods return new <code>GemState</code> objects.</p></div><div class="warning custom-block"><p class="custom-block-title">Note</p><p><code>applyState</code> triggers a complete item rebuild flow, including all registered assembly layers (gem, forging, enhancement, etc.). This ensures cross-module state consistency, but also means every gem operation refreshes the entire item display.</p></div>`,22)])])}const k=a(i,[["render",t]]);export{m as __pageData,k as default};

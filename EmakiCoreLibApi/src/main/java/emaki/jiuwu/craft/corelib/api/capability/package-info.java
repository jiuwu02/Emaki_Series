/**
 * Capability registry: ask whether an optional method of another Emaki plugin is really callable.
 *
 * <h2>Why it lives in CoreLib</h2>
 * A probe cannot live in the plugin being probed. Calling {@code EmakiStorageApi.capabilities()} on a
 * build whose shaded API predates that method throws {@link java.lang.NoSuchMethodError} &mdash; the
 * probe is itself the thing that needed probing. EmakiCoreLib is a hard dependency of every Emaki
 * module ({@code required: true, load: BEFORE}), so a registry placed here is always present.
 *
 * <h2>Publishing</h2>
 * Providers call {@code EmakiCoreLibApi.publishCapabilities(this, ...)} right after installing their
 * own API bridge in {@code onEnable}, and revoke through the returned
 * {@link emaki.jiuwu.craft.corelib.api.capability.CapabilityRegistration} right before uninstalling
 * it in {@code onDisable}. A capability must only be published while the matching method is genuinely
 * usable: when a config switch disables the feature, do not publish it rather than publish it and
 * reject the calls, or the consumer's gate stops meaning anything.
 *
 * <h2>Consuming</h2>
 * Read
 * {@link emaki.jiuwu.craft.corelib.api.capability.ApiCapability} for the two rules that make gating
 * sound: keep the guarded call inside the {@code if} body, and build the identifier with
 * {@link emaki.jiuwu.craft.corelib.api.capability.ApiCapability#of(String)} instead of referencing a
 * constant from the provider's API jar.
 *
 * <h2>Degradation</h2>
 * With EmakiCoreLib absent, {@code hasCapability} is {@code false}, both capability sets are empty
 * and publication returns
 * {@link emaki.jiuwu.craft.corelib.api.capability.CapabilityRegistration#unavailable(String)}.
 * Nothing returns {@code null} and nothing throws.
 */
package emaki.jiuwu.craft.corelib.api.capability;

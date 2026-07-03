package emaki.jiuwu.craft.codex.recipe.sync.nms;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Runtime-reflection bridge into the server's Mojang-mapped NMS recipe pipeline.
 *
 * <p>EmakiCodex compiles against spigot-api only; this class never imports any
 * {@code net.minecraft.*} or {@code org.bukkit.craftbukkit.*} type. Instead it resolves
 * everything it needs by reflection at runtime and caches the handles. It targets
 * <b>Paper/Purpur/Folia 1.21.2+</b>, where the server jar exposes Mojang-mapped names
 * (e.g. {@code RecipeHolder.STREAM_CODEC}, {@code connection}, {@code send}) and the
 * CraftBukkit package is de-versioned ({@code org.bukkit.craftbukkit.entity.CraftPlayer}).
 *
 * <p>Since Minecraft 1.21.2 the recipe protocol was rewritten and full recipes are
 * server-side only. A standard JEI/REI client (Fabric or NeoForge build) rebuilds its
 * recipe list from the loader's recipe-sync custom payload:
 * <ul>
 *   <li>Fabric client → {@code fabric:recipe_sync}</li>
 *   <li>NeoForge client → {@code neoforge:recipe_content}</li>
 * </ul>
 * Both payloads carry real {@code RecipeHolder}s encoded with Mojang's {@code StreamCodec},
 * which is why a hand-written byte frame (the old {@code jei:network} approach) never worked.
 * This class produces those exact bytes, mirroring the JEIRecipeBridge plugin's format but
 * via reflection instead of paperweight/NMS compile-time access.
 *
 * <p>All resolution failures are captured and surfaced through {@link #unavailableReason()}
 * so the calling channel can log a precise diagnostic and degrade gracefully.
 */
public final class NmsRecipeBridge {

    /** Result of an encode attempt: the payload bytes and how many recipes went in. */
    public record EncodedPayload(byte[] bytes, int recipeCount, int totalCandidates) {
    }

    private final boolean available;
    private final String unavailableReason;

    // Cached reflection handles (resolved once when available).
    private Method craftPlayerGetHandle;
    private Field serverPlayerConnection;
    private Method connectionSend;
    private Class<?> packetInterface;

    private Object minecraftServer;
    private Object registryAccess;

    private Method recipeHolderId;
    private Method recipeHolderValue;
    private Method recipeGetSerializer;
    private Method recipeGetType;
    private Object recipeSerializerRegistry;
    private Object recipeTypeRegistry;
    private Method registryGetKey;

    private Object recipeHolderStreamCodec;
    private Method streamEncoderEncode;
    private Method recipeSerializerStreamCodec;

    private Constructor<?> registryFriendlyBufCtor;
    private Method unpooledBuffer;
    private Method byteBufReadableBytes;
    private Method byteBufGetBytes;
    private Method byteBufRelease;

    // For writing serializer/recipe ids into the Fabric frame we reuse a friendly buf's methods.
    private Method bufWriteVarInt;
    private Method bufWriteResourceLocation; // writeResourceLocation / writeIdentifier
    private Method bufWriteResourceKey;      // writeResourceKey

    // NeoForge extras.
    private Method minecraftServerRegistries; // registries() -> LayeredRegistryAccess or RegistryAccess
    private Method tagSerializeToNetwork;
    private Constructor<?> updateTagsPacketCtor;

    private Class<?> resourceLocationClass;
    private Method resourceLocationCreate; // fromNamespaceAndPath

    public NmsRecipeBridge() {
        String reason = null;
        boolean ok = false;
        try {
            resolve();
            ok = true;
        } catch (Throwable throwable) {
            reason = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        }
        this.available = ok;
        this.unavailableReason = reason;
    }

    /** {@return whether all required NMS handles resolved and encoding can be attempted} */
    public boolean isAvailable() {
        return available;
    }

    /** {@return a short human-readable reason the bridge is unavailable, or {@code null}} */
    public String unavailableReason() {
        return unavailableReason;
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    private void resolve() throws Exception {
        // Bukkit Player -> CraftPlayer#getHandle -> ServerPlayer (de-versioned CraftBukkit package).
        String craftBase = Bukkit.getServer().getClass().getPackageName(); // org.bukkit.craftbukkit(.vX)
        Class<?> craftPlayerClass = Class.forName(craftBase + ".entity.CraftPlayer");
        craftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");

        Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        serverPlayerConnection = findField(serverPlayerClass, "connection", "f");
        packetInterface = Class.forName("net.minecraft.network.protocol.Packet");
        Class<?> connectionClass = serverPlayerConnection.getType();
        connectionSend = findMethodByParam(connectionClass, packetInterface, "send", "b", "a");

        // CraftServer#getServer -> MinecraftServer.
        Object craftServer = Bukkit.getServer();
        minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
        Class<?> minecraftServerClass = minecraftServer.getClass();

        // registryAccess()
        Method registryAccessMethod = findMethodNoArg(minecraftServerClass, "registryAccess", "H_", "ba");
        registryAccess = registryAccessMethod.invoke(minecraftServer);

        // RegistryFriendlyByteBuf(ByteBuf, RegistryAccess) + Unpooled.buffer()
        Class<?> byteBufClass = Class.forName("io.netty.buffer.ByteBuf");
        Class<?> registryAccessClass = Class.forName("net.minecraft.core.RegistryAccess");
        Class<?> registryFriendlyBufClass = Class.forName("net.minecraft.network.RegistryFriendlyByteBuf");
        registryFriendlyBufCtor = registryFriendlyBufClass.getConstructor(byteBufClass, registryAccessClass);
        unpooledBuffer = Class.forName("io.netty.buffer.Unpooled").getMethod("buffer");
        byteBufReadableBytes = byteBufClass.getMethod("readableBytes");
        byteBufGetBytes = byteBufClass.getMethod("getBytes", int.class, byte[].class);
        byteBufRelease = byteBufClass.getMethod("release");

        // FriendlyByteBuf write helpers (RegistryFriendlyByteBuf extends FriendlyByteBuf).
        bufWriteVarInt = findMethodByParam(registryFriendlyBufClass, int.class, "writeVarInt", "c");

        // RecipeHolder record: id(), value(), STREAM_CODEC.
        Class<?> recipeHolderClass = Class.forName("net.minecraft.world.item.crafting.RecipeHolder");
        recipeHolderId = findMethodNoArg(recipeHolderClass, "id", "f", "a");
        recipeHolderValue = findMethodNoArg(recipeHolderClass, "value", "g", "b");
        recipeHolderStreamCodec = findStaticField(recipeHolderClass, "STREAM_CODEC");

        // Recipe#getSerializer / #getType.
        Class<?> recipeClass = Class.forName("net.minecraft.world.item.crafting.Recipe");
        recipeGetSerializer = findMethodNoArg(recipeClass, "getSerializer", "a");
        recipeGetType = findMethodNoArg(recipeClass, "getType", "getRecipeType");

        // RecipeSerializer#streamCodec().
        Class<?> recipeSerializerClass = Class.forName("net.minecraft.world.item.crafting.RecipeSerializer");
        recipeSerializerStreamCodec = findMethodNoArg(recipeSerializerClass, "streamCodec", "codec");

        // StreamCodec / StreamEncoder#encode(Object, Object).
        Class<?> streamEncoderClass = Class.forName("net.minecraft.network.codec.StreamEncoder");
        streamEncoderEncode = streamEncoderClass.getMethod("encode", Object.class, Object.class);

        // BuiltInRegistries.RECIPE_SERIALIZER / RECIPE_TYPE and Registry#getKey(Object).
        Class<?> builtInRegistries = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        recipeSerializerRegistry = builtInRegistries.getField("RECIPE_SERIALIZER").get(null);
        recipeTypeRegistry = builtInRegistries.getField("RECIPE_TYPE").get(null);
        Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
        registryGetKey = findMethodByParam(registryClass, Object.class, "getKey", "c");

        // ResourceLocation.fromNamespaceAndPath (may be named Identifier in newer mappings).
        resolveResourceLocation();

        // writeResourceKey(ResourceKey) for fabric entries; fall back to writing the id's location.
        try {
            Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
            bufWriteResourceKey = findMethodByParamOptional(registryFriendlyBufClass, resourceKeyClass,
                    "writeResourceKey");
        } catch (Throwable ignored) {
            bufWriteResourceKey = null;
        }

        // Optional: writeResourceLocation / writeIdentifier for fabric serializer id + recipe id fallback.
        bufWriteResourceLocation = findMethodByParamOptional(registryFriendlyBufClass, resourceLocationClass,
                "writeResourceLocation", "writeIdentifier", "a");

        // NeoForge tag update packet extras (optional; only used for the neoforge branch).
        resolveNeoForgeTagExtras();
    }

    private void resolveResourceLocation() throws Exception {
        Class<?> rl;
        try {
            rl = Class.forName("net.minecraft.resources.ResourceLocation");
        } catch (ClassNotFoundException ex) {
            rl = Class.forName("net.minecraft.resources.Identifier");
        }
        resourceLocationClass = rl;
        resourceLocationCreate = findStaticMethod(rl, new Class<?>[] {String.class, String.class},
                "fromNamespaceAndPath", "of", "a");
    }

    private void resolveNeoForgeTagExtras() {
        try {
            Class<?> tagNetworkSerialization = Class.forName("net.minecraft.tags.TagNetworkSerialization");
            Method registriesMethod = findMethodNoArgOptional(minecraftServer.getClass(), "registries");
            minecraftServerRegistries = registriesMethod;
            for (Method method : tagNetworkSerialization.getMethods()) {
                if (method.getName().equals("serializeTagsToNetwork") && method.getParameterCount() == 1) {
                    tagSerializeToNetwork = method;
                    break;
                }
            }
            Class<?> updateTagsPacket = Class.forName(
                    "net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket");
            for (Constructor<?> ctor : updateTagsPacket.getConstructors()) {
                if (ctor.getParameterCount() == 1) {
                    updateTagsPacketCtor = ctor;
                    break;
                }
            }
        } catch (Throwable ignored) {
            // NeoForge tag补发是可选增强；解析失败不影响 recipe payload 本身。
            minecraftServerRegistries = null;
            tagSerializeToNetwork = null;
            updateTagsPacketCtor = null;
        }
    }

    // ------------------------------------------------------------------
    // Recipe collection
    // ------------------------------------------------------------------

    /**
     * Reads every server {@code RecipeHolder} and pairs it with its id/serializer/type keys.
     * Only holders whose id is contained in {@code visibleIds} are returned (or all, when
     * {@code visibleIds} is {@code null}). Must be called on the main/region thread.
     *
     * @param visibleIds the recipe ids that should be advertised, or {@code null} for all
     * @return the matching holders in registry order (never {@code null})
     * @throws Exception if reflection into the recipe manager fails
     */
    public List<Object> collectHolders(Set<String> visibleIds) throws Exception {
        Object recipeManager = resolveRecipeManager();
        Object recipeMapOrCollection = extractHolderCollection(recipeManager);

        @SuppressWarnings("unchecked")
        Iterable<Object> holders = (Iterable<Object>) recipeMapOrCollection;
        List<Object> result = new ArrayList<>();
        for (Object holder : holders) {
            if (visibleIds == null) {
                result.add(holder);
                continue;
            }
            String id = recipeIdString(holder);
            if (id != null && visibleIds.contains(id)) {
                result.add(holder);
            }
        }
        return result;
    }

    private Object resolveRecipeManager() throws Exception {
        // Prefer MinecraftServer#getRecipeManager(); fall back to a ServerLevel#recipeAccess().
        Method getRecipeManager = findMethodNoArgOptional(minecraftServer.getClass(),
                "getRecipeManager", "aI", "aH");
        if (getRecipeManager != null) {
            Object rm = getRecipeManager.invoke(minecraftServer);
            if (rm != null) {
                return rm;
            }
        }
        // Fallback via overworld level.
        Method overworld = findMethodNoArgOptional(minecraftServer.getClass(), "overworld");
        if (overworld != null) {
            Object level = overworld.invoke(minecraftServer);
            Method recipeAccess = findMethodNoArgOptional(level.getClass(), "recipeAccess", "getRecipeManager");
            if (recipeAccess != null) {
                return recipeAccess.invoke(level);
            }
        }
        throw new IllegalStateException("无法定位 RecipeManager (getRecipeManager/recipeAccess 均未命中)");
    }

    @SuppressWarnings("unchecked")
    private Object extractHolderCollection(Object recipeManager) throws Exception {
        // Try RecipeManager#getRecipes() -> Collection<RecipeHolder<?>>.
        Method getRecipes = findMethodNoArgOptional(recipeManager.getClass(), "getRecipes", "recipes", "values");
        if (getRecipes != null) {
            Object value = getRecipes.invoke(recipeManager);
            if (value instanceof Iterable<?>) {
                return value;
            }
            // getRecipes may return a Stream; collect it.
            if (value != null && "java.util.stream".equals(value.getClass().getPackageName())) {
                Method toList = value.getClass().getMethod("toList");
                return toList.invoke(value);
            }
        }
        // Try the private 'recipes' field (type RecipeMap) then RecipeMap#values().
        Field recipesField = findFieldByTypeNameContains(recipeManager.getClass(), "RecipeMap");
        if (recipesField != null) {
            recipesField.setAccessible(true);
            Object recipeMap = recipesField.get(recipeManager);
            Method values = findMethodNoArgOptional(recipeMap.getClass(), "values");
            if (values != null) {
                return values.invoke(recipeMap);
            }
        }
        throw new IllegalStateException("无法从 RecipeManager 提取 RecipeHolder 集合");
    }

    private String recipeIdString(Object holder) {
        try {
            Object key = recipeHolderId.invoke(holder); // ResourceKey<Recipe<?>>
            // ResourceKey#location() -> ResourceLocation; toString() gives "namespace:path".
            Method location = findMethodNoArgOptional(key.getClass(), "location", "a");
            Object rl = location != null ? location.invoke(key) : key;
            return String.valueOf(rl);
        } catch (Throwable throwable) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Encoding — NeoForge (neoforge:recipe_content)
    // ------------------------------------------------------------------

    /**
     * Encodes a {@code neoforge:recipe_content} payload: a set of recipe types followed by
     * the list of recipe holders (each via {@code RecipeHolder.STREAM_CODEC}).
     *
     * @param holders the recipe holders to advertise
     * @return the encoded payload
     * @throws Exception if reflective encoding fails
     */
    public EncodedPayload encodeNeoForge(List<Object> holders) throws Exception {
        Object buf = newRegistryBuf();
        try {
            // Collect distinct recipe types.
            Set<Object> types = new LinkedHashSet<>();
            for (Object holder : holders) {
                Object recipe = recipeHolderValue.invoke(holder);
                types.add(recipeGetType.invoke(recipe));
            }
            // Set<RecipeType<?>>: VarInt count + each type registry id.
            bufWriteVarInt.invoke(buf, types.size());
            for (Object type : types) {
                writeRegistryId(buf, recipeTypeRegistry, type);
            }
            // List<RecipeHolder<?>>: VarInt count + each via RecipeHolder.STREAM_CODEC.
            int written = 0;
            bufWriteVarInt.invoke(buf, holders.size());
            for (Object holder : holders) {
                streamEncoderEncode.invoke(recipeHolderStreamCodec, buf, holder);
                written++;
            }
            return new EncodedPayload(toBytes(buf), written, holders.size());
        } finally {
            releaseQuietly(buf);
        }
    }

    // ------------------------------------------------------------------
    // Encoding — Fabric (fabric:recipe_sync)
    // ------------------------------------------------------------------

    /**
     * Encodes a {@code fabric:recipe_sync} payload: recipes grouped by serializer, each group
     * writing the serializer id, the recipe count, then per recipe its {@code ResourceKey} and
     * the serializer's own {@code streamCodec()} body.
     *
     * @param holders the recipe holders to advertise
     * @return the encoded payload
     * @throws Exception if reflective encoding fails
     */
    public EncodedPayload encodeFabric(List<Object> holders) throws Exception {
        // Group holders by serializer, preserving encounter order.
        Map<Object, List<Object>> bySerializer = new LinkedHashMap<>();
        for (Object holder : holders) {
            Object recipe = recipeHolderValue.invoke(holder);
            Object serializer = recipeGetSerializer.invoke(recipe);
            bySerializer.computeIfAbsent(serializer, key -> new ArrayList<>()).add(holder);
        }

        Object buf = newRegistryBuf();
        try {
            bufWriteVarInt.invoke(buf, bySerializer.size());
            int written = 0;
            for (Map.Entry<Object, List<Object>> entry : bySerializer.entrySet()) {
                Object serializer = entry.getKey();
                List<Object> group = entry.getValue();
                // serializer id
                writeRegistryId(buf, recipeSerializerRegistry, serializer);
                // count
                bufWriteVarInt.invoke(buf, group.size());
                // per recipe: ResourceKey + serializer.streamCodec().encode(recipe)
                Object serializerCodec = recipeSerializerStreamCodec.invoke(serializer);
                for (Object holder : group) {
                    Object id = recipeHolderId.invoke(holder); // ResourceKey<Recipe<?>>
                    writeRecipeKey(buf, id);
                    Object recipe = recipeHolderValue.invoke(holder);
                    streamEncoderEncode.invoke(serializerCodec, buf, recipe);
                    written++;
                }
            }
            return new EncodedPayload(toBytes(buf), written, holders.size());
        } finally {
            releaseQuietly(buf);
        }
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    /**
     * Sends a raw NeoForge tag-update packet to refresh client tags after a recipe sync.
     * No-op when the optional tag handles were not resolved.
     *
     * @param player the target player
     */
    public void sendNeoForgeTagUpdate(Player player) {
        if (minecraftServerRegistries == null || tagSerializeToNetwork == null || updateTagsPacketCtor == null) {
            return;
        }
        try {
            Object registries = minecraftServerRegistries.invoke(minecraftServer);
            Object networkPayload = tagSerializeToNetwork.invoke(null, registries);
            Object packet = updateTagsPacketCtor.newInstance(networkPayload);
            sendPacket(player, packet);
        } catch (Throwable ignored) {
            // 标签补发是可选增强，失败不影响配方本身。
        }
    }

    private void sendPacket(Player player, Object packet) throws Exception {
        Object serverPlayer = craftPlayerGetHandle.invoke(player);
        Object connection = serverPlayerConnection.get(serverPlayer);
        connectionSend.invoke(connection, packet);
    }

    // ------------------------------------------------------------------
    // Reflection helpers
    // ------------------------------------------------------------------

    private Object newRegistryBuf() throws Exception {
        Object raw = unpooledBuffer.invoke(null);
        return registryFriendlyBufCtor.newInstance(raw, registryAccess);
    }

    private byte[] toBytes(Object buf) throws Exception {
        int len = (int) byteBufReadableBytes.invoke(buf);
        byte[] out = new byte[len];
        byteBufGetBytes.invoke(buf, 0, out);
        return out;
    }

    private void releaseQuietly(Object buf) {
        try {
            byteBufRelease.invoke(buf);
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private void writeRegistryId(Object buf, Object registry, Object element) throws Exception {
        Object id = registryGetKey.invoke(registry, element); // ResourceLocation
        writeResourceLocation(buf, id);
    }

    private void writeResourceLocation(Object buf, Object resourceLocation) throws Exception {
        if (bufWriteResourceLocation != null) {
            bufWriteResourceLocation.invoke(buf, resourceLocation);
            return;
        }
        throw new IllegalStateException("无法写入 ResourceLocation：writeResourceLocation/writeIdentifier 未解析");
    }

    private void writeRecipeKey(Object buf, Object resourceKey) throws Exception {
        if (bufWriteResourceKey != null) {
            bufWriteResourceKey.invoke(buf, resourceKey);
            return;
        }
        // Fallback: write the underlying ResourceLocation of the key.
        Method location = findMethodNoArgOptional(resourceKey.getClass(), "location", "a");
        Object rl = location != null ? location.invoke(resourceKey) : resourceKey;
        writeResourceLocation(buf, rl);
    }

    // -- generic name-fallback lookups --

    private static Field findField(Class<?> owner, String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                return owner.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // try next candidate
            }
        }
        // Deep search superclasses.
        Class<?> current = owner.getSuperclass();
        while (current != null) {
            for (String name : names) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    // try next
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchFieldException(owner.getName() + " 无字段候选 " + String.join("/", names));
    }

    private static Object findStaticField(Class<?> owner, String name) throws Exception {
        Field field = owner.getField(name);
        return field.get(null);
    }

    private static Method findMethodNoArg(Class<?> owner, String... names) throws NoSuchMethodException {
        Method method = findMethodNoArgOptional(owner, names);
        if (method == null) {
            throw new NoSuchMethodException(owner.getName() + " 无无参方法候选 " + String.join("/", names));
        }
        return method;
    }

    private static Method findMethodNoArgOptional(Class<?> owner, String... names) {
        for (String name : names) {
            Class<?> current = owner;
            while (current != null) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                }
            }
        }
        // Also try interfaces / public method view.
        for (String name : names) {
            try {
                Method method = owner.getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // next
            }
        }
        return null;
    }

    private static Method findMethodByParam(Class<?> owner, Class<?> param, String... names)
            throws NoSuchMethodException {
        Method method = findMethodByParamOptional(owner, param, names);
        if (method == null) {
            throw new NoSuchMethodException(owner.getName() + " 无单参方法候选 " + String.join("/", names));
        }
        return method;
    }

    private static Method findMethodByParamOptional(Class<?> owner, Class<?> param, String... names) {
        for (String name : names) {
            Class<?> current = owner;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                        continue;
                    }
                    if (param == null || method.getParameterTypes()[0].isAssignableFrom(param)
                            || param.isAssignableFrom(method.getParameterTypes()[0])) {
                        method.setAccessible(true);
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method findStaticMethod(Class<?> owner, Class<?>[] params, String... names)
            throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method method = owner.getMethod(name, params);
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // next
            }
        }
        throw new NoSuchMethodException(owner.getName() + " 无静态方法候选 " + String.join("/", names));
    }

    private static Field findFieldByTypeNameContains(Class<?> owner, String typeSimpleNameFragment) {
        Class<?> current = owner;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType().getSimpleName().contains(typeSimpleNameFragment)) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}

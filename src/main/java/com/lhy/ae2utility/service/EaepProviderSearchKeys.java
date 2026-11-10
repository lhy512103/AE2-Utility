package com.lhy.ae2utility.service;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;

import com.lhy.ae2utility.integration.eaep.EaepReflection;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.SyncEaepProviderSearchKeyPacket;

/** 推导 EAEP 供应器搜索关键字，写入样板 NBT 并同步给客户端的供应器选择界面。 */
final class EaepProviderSearchKeys {
    static final String PATTERN_TAG = "Ae2UtilityProviderSearchKey";

    private EaepProviderSearchKeys() {}

    /** 把搜索关键字写进样板，供后续按样板反查供应器。 */
    static void writeToPattern(ServerPlayer player, EncodePatternPacket payload, ItemStack encodedPattern) {
        String key = payload.providerSearchKey();
        if (key == null || key.isBlank()) {
            Recipe<?> recipe = recipe(player, payload);
            key = recipe == null ? "" : recipe instanceof CraftingRecipe ? "crafting" : recipeTypePath(recipe);
        }
        if (!key.isBlank()) {
            encodedPattern.getOrCreateTag().putString(PATTERN_TAG, key);
        }
    }

    /**
     * 与发包时写入的 {@link EncodePatternPacket#providerSearchKey()} 逻辑一致的原始关键字，
     * 客户端还会再经过 EAEP {@code resolveSearchKeyAlias}。
     */
    static String rawKeyForSync(ServerPlayer player, EncodePatternPacket payload) {
        String searchKey = payload.providerSearchKey();
        if (searchKey != null && !searchKey.isBlank()) {
            return searchKey;
        }
        String display = payload.providerDisplayName();
        if (display != null && !display.isBlank()) {
            return display;
        }
        Recipe<?> recipe = recipe(player, payload);
        if (recipe == null) {
            return "";
        }
        if (recipe instanceof CraftingRecipe) {
            return "crafting";
        }
        String mapped = EaepReflection.mapRecipeTypeToSearchKey(recipe);
        return mapped != null && !mapped.isBlank() ? mapped : recipeTypePath(recipe);
    }

    static void syncToClient(ServerPlayer player, EncodePatternPacket payload) {
        String packetRaw = payload.providerSearchKey();
        boolean craftingNeedsPreset = (packetRaw == null || packetRaw.isBlank())
                && recipe(player, payload) instanceof CraftingRecipe;
        ModNetworking.sendToPlayer(player, craftingNeedsPreset
                ? new SyncEaepProviderSearchKeyPacket(true, "")
                : new SyncEaepProviderSearchKeyPacket(false, rawKeyForSync(player, payload)));
    }

    private static Recipe<?> recipe(ServerPlayer player, EncodePatternPacket payload) {
        if (payload.recipeId() == null || player.getServer() == null) {
            return null;
        }
        return player.getServer().getRecipeManager().byKey(payload.recipeId()).orElse(null);
    }

    private static String recipeTypePath(Recipe<?> recipe) {
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return id != null ? id.getPath() : "";
    }
}

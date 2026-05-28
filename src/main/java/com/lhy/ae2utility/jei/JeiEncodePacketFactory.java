package com.lhy.ae2utility.jei;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.GenericStack;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;

import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;

public final class JeiEncodePacketFactory {
    private JeiEncodePacketFactory() {
    }

    public static Optional<EncodePatternPacket> tryCreate(IRecipeLayoutDrawable<?> recipeLayout, boolean shiftUpload,
            boolean jeiFullCategoryBatch, int bulkEncodeSessionId) {
        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();
        Object recipe = recipeLayout.getRecipe();
        List<List<GenericStack>> inputs = RecipeTransferPacketHelper.getGenericStacks(slotsView, RecipeIngredientRole.INPUT);
        List<GenericStack> outputs = RecipeTransferPacketHelper.getEncodingOutputs(recipe, slotsView);

        boolean hasInput = inputs.stream().anyMatch(Objects::nonNull);
        boolean hasOutput = outputs.stream().anyMatch(Objects::nonNull);
        boolean hasUnsupportedInput = inputs.stream().anyMatch(slot -> slot == null);
        boolean hasUnsupportedOutput = outputs.stream().anyMatch(Objects::isNull);

        if (!hasInput || !hasOutput || hasUnsupportedInput || hasUnsupportedOutput) {
            String recipeDesc = recipe instanceof RecipeHolder<?> h && h.id() != null ? h.id().toString()
                : (recipe != null ? recipe.getClass().getName() : "<null>");
            JeiEncodeQueueDebugLog.info(
                "JeiEncodePacketFactory.tryCreate rejected recipe={} hasInput={} hasOutput={} unsupportedIn={} unsupportedOut={} inputsSlots={} outputsCount={}",
                recipeDesc, hasInput, hasOutput, hasUnsupportedInput, hasUnsupportedOutput, inputs.size(), outputs.size());
            return Optional.empty();
        }

        ResourceLocation recipeId = null;
        if (recipe instanceof RecipeHolder<?> holder) {
            recipeId = holder.id();
        }
        String patternName = derivePatternName(recipeId, outputs);

        String providerSearchKey = computeEaepProviderSearchKey(recipe, shiftUpload);

        return Optional.of(new EncodePatternPacket(
                inputs,
                outputs,
                recipeId,
                patternName,
                providerSearchKey,
                providerSearchKey,
                shiftUpload,
                JeiPatternSubstitutionUi.isItemSubstituteOn(),
                JeiPatternSubstitutionUi.isFluidSubstituteOn(),
                false,
                false,
                jeiFullCategoryBatch,
                bulkEncodeSessionId));
    }

    private static String derivePatternName(@Nullable ResourceLocation recipeId, List<GenericStack> outputs) {
        for (GenericStack output : outputs) {
            if (output == null || output.what() == null) {
                continue;
            }
            Component name = output.what().getDisplayName();
            if (name != null) {
                String text = name.getString();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return recipeId != null ? recipeId.toString() : "-";
    }

    public static void sendEaepProviderRefreshIfNeeded(boolean shiftUpload) {
        if (!shiftUpload || !ModList.get().isLoaded("extendedae_plus")) {
            return;
        }
        try {
            Class<?> packetClass = Class.forName("com.extendedae_plus.network.RequestProvidersListC2SPacket");
            Object instance = packetClass.getDeclaredField("INSTANCE").get(null);
            PacketDistributor.sendToServer((net.minecraft.network.protocol.common.custom.CustomPacketPayload) instance);
        } catch (Exception ignored) {
        }
    }

    /**
     * 仅计算写入数据包的字符串，不修改 EAEP 全局静态（批量时会覆盖导致供应器界面过滤错误；服务端会通过
     * {@link com.lhy.ae2utility.network.SyncEaepProviderSearchKeyPacket} 同步客户端）。
     */
    private static String computeEaepProviderSearchKey(Object recipe, boolean shiftUpload) {
        String providerSearchKey = "";
        if (!shiftUpload || !ModList.get().isLoaded("extendedae_plus")) {
            return providerSearchKey;
        }
        try {
            Class<?> uploadUtil = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
            if (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe) {
                java.lang.reflect.Field defaultKey = uploadUtil.getField("DEFAULT_CRAFTING_SEARCH_KEY");
                providerSearchKey = (String) defaultKey.get(null);
                if (providerSearchKey == null) {
                    providerSearchKey = "";
                }
            } else {
                String name = null;
                if (recipe instanceof RecipeHolder<?> h) {
                    java.lang.reflect.Method mapRecipe = uploadUtil.getMethod("mapRecipeTypeToSearchKey", net.minecraft.world.item.crafting.Recipe.class);
                    name = (String) mapRecipe.invoke(null, h.value());
                } else {
                    java.lang.reflect.Method deriveKey = uploadUtil.getMethod("deriveSearchKeyFromUnknownRecipe", Object.class);
                    name = (String) deriveKey.invoke(null, recipe);
                }
                if (name != null && !name.isEmpty()) {
                    providerSearchKey = name;
                }
            }
        } catch (Throwable ignored) {
        }
        return providerSearchKey == null ? "" : providerSearchKey;
    }
}

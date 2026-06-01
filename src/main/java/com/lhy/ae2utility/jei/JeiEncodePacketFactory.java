package com.lhy.ae2utility.jei;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import net.neoforged.fml.ModList;

import appeng.api.stacks.GenericStack;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;

import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;
import com.lhy.ae2utility.integration.eaep.EaepReflection;
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

        boolean craftingCategoryHint = isCraftingCategory(recipeLayout, recipe, inputs);

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
                bulkEncodeSessionId,
                craftingCategoryHint));
    }

    /**
     * 是否应按「工作台合成」处理：与原版 AE2 的判断保持同一边界，只有能放进 3x3 的配方才提示服务端做
     * 合成 fallback。Create 动力合成器这类超过 3x3 的 JEI 页面即使长得像合成网格，也应保持处理样板路径。
     */
    private static boolean isCraftingCategory(IRecipeLayoutDrawable<?> recipeLayout, Object recipe,
            List<List<GenericStack>> inputs) {
        long meaningfulInputs = inputs.stream()
                .filter(slot -> slot != null && !slot.isEmpty())
                .count();
        if (meaningfulInputs > 9) {
            return false;
        }
        if (recipe instanceof RecipeHolder<?> holder
                && holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe) {
            return true;
        }
        try {
            return recipeLayout.getRecipeCategory().getRecipeType().equals(mezz.jei.api.constants.RecipeTypes.CRAFTING);
        } catch (Throwable ignored) {
            return false;
        }
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

    /**
     * 仅计算写入数据包的字符串，不修改 EAEP 全局静态（批量时会覆盖导致供应器界面过滤错误；服务端会通过
     * {@link com.lhy.ae2utility.network.SyncEaepProviderSearchKeyPacket} 同步客户端）。
     */
    private static String computeEaepProviderSearchKey(Object recipe, boolean shiftUpload) {
        String providerSearchKey = "";
        if (!shiftUpload || !ModList.get().isLoaded("extendedae_plus")) {
            return providerSearchKey;
        }
        if (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe) {
            providerSearchKey = EaepReflection.defaultCraftingSearchKey();
        } else {
            String name = null;
            if (recipe instanceof RecipeHolder<?> h) {
                name = EaepReflection.mapRecipeTypeToSearchKey(h.value());
            } else {
                name = EaepReflection.deriveSearchKeyFromUnknownRecipe(recipe);
            }
            if (name != null && !name.isEmpty()) {
                providerSearchKey = name;
            }
        }
        return providerSearchKey == null ? "" : providerSearchKey;
    }
}

package com.lhy.ae2utility.jei;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;

import net.minecraftforge.fml.ModList;

import appeng.api.stacks.GenericStack;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;

import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;

/**
 * 1.20.1 forge：从 JEI {@link IRecipeLayoutDrawable} 构造 {@link EncodePatternPacket}。
 *
 * <p>1.20.1 中 {@code recipe} 即 {@link Recipe} 本身，{@link Recipe#getId()} 返回 id，
 * {@link Recipe#getType()} 返回 {@link net.minecraft.world.item.crafting.RecipeType}。
 * EAEP 搜索关键字直接调用 {@link com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig}（compileOnly）。</p>
 */
public final class JeiEncodePacketFactory {
    private JeiEncodePacketFactory() {
    }

    public static Optional<EncodePatternPacket> tryCreate(IRecipeLayoutDrawable<?> recipeLayout, boolean shiftUpload) {
        return tryCreate(recipeLayout, recipeLayout.getRecipe(), recipeLayout.getRecipeSlotsView(), shiftUpload);
    }

    /**
     * 由 {@link #tryCreate(IRecipeLayoutDrawable, boolean)} 拆出 recipe 与 slotsView 后调用。
     */
    public static Optional<EncodePatternPacket> tryCreate(Object recipe, IRecipeSlotsView slotsView, boolean shiftUpload) {
        return tryCreate(null, recipe, slotsView, shiftUpload);
    }

    private static Optional<EncodePatternPacket> tryCreate(@Nullable IRecipeLayoutDrawable<?> recipeLayout,
            Object recipe, IRecipeSlotsView slotsView, boolean shiftUpload) {
        List<List<GenericStack>> inputs = RecipeTransferPacketHelper.getGenericStacks(slotsView, RecipeIngredientRole.INPUT);
        List<GenericStack> outputs = RecipeTransferPacketHelper.getEncodingOutputs(recipe, slotsView);

        boolean hasInput = inputs.stream().anyMatch(Objects::nonNull);
        boolean hasOutput = outputs.stream().anyMatch(Objects::nonNull);
        boolean hasUnsupportedInput = inputs.stream().anyMatch(slot -> slot == null);
        boolean hasUnsupportedOutput = outputs.stream().anyMatch(Objects::isNull);

        if (!hasInput || !hasOutput || hasUnsupportedInput || hasUnsupportedOutput) {
            return Optional.empty();
        }

        ResourceLocation recipeId = null;
        if (recipe instanceof Recipe<?> r) {
            recipeId = r.getId();
        }
        String patternName = derivePatternName(recipeId, outputs);

        String providerSearchKey = computeEaepProviderSearchKey(recipeLayout, recipe, shiftUpload);
        boolean craftingCategoryHint = isCraftingCategory(recipeLayout, recipe, inputs);

        return Optional.of(new EncodePatternPacket(
                inputs,
                outputs,
                recipeId,
                patternName,
                providerSearchKey,
                shiftUpload,
                JeiPatternSubstitutionUi.isItemSubstituteOn(),
                JeiPatternSubstitutionUi.isFluidSubstituteOn(),
                false,
                craftingCategoryHint));
    }

    private static boolean isCraftingCategory(@Nullable IRecipeLayoutDrawable<?> recipeLayout, Object recipe,
            List<List<GenericStack>> inputs) {
        long meaningfulInputs = inputs.stream()
                .filter(slot -> slot != null && !slot.isEmpty())
                .count();
        if (meaningfulInputs > 9) {
            return false;
        }
        if (recipe instanceof CraftingRecipe) {
            return true;
        }
        try {
            return recipeLayout != null && recipeLayout.getRecipeCategory().getRecipeType().equals(RecipeTypes.CRAFTING);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String derivePatternName(@Nullable ResourceLocation recipeId, List<GenericStack> outputs) {
        for (GenericStack output : outputs) {
            if (output == null || output.what() == null) {
                continue;
            }
            var name = output.what().getDisplayName();
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
     * 仅计算写入数据包的字符串，不修改 EAEP 全局静态。
     */
    private static String computeEaepProviderSearchKey(@Nullable IRecipeLayoutDrawable<?> recipeLayout,
            Object recipe, boolean shiftUpload) {
        if (!shiftUpload || !ModList.get().isLoaded("extendedae_plus")) {
            return "";
        }
        Recipe<?> vanillaRecipe = recipe instanceof Recipe<?> r ? r : null;
        ResourceLocation typeId = recipeTypeId(recipeLayout, vanillaRecipe);

        if (vanillaRecipe instanceof CraftingRecipe) {
            // 合成类使用 EAEP 预置关键字
            return "crafting";
        }
        String mapped = vanillaRecipe != null ? mapRecipeTypeToSearchKey(vanillaRecipe) : null;
        if (isAdvancedAeReaction(typeId)) {
            if (mapped != null && !mapped.isEmpty() && !mapped.equalsIgnoreCase(typeId.getPath())) {
                return mapped;
            }
            return "reaction_chamber";
        }
        if (mapped != null && !mapped.isEmpty()) {
            return mapped;
        }
        // JEI 分类可能使用不实现原版 Recipe 的包装对象，退回分类 UID 的 path。
        return typeId != null ? typeId.getPath() : "";
    }

    private static @Nullable ResourceLocation recipeTypeId(@Nullable IRecipeLayoutDrawable<?> recipeLayout,
            @Nullable Recipe<?> recipe) {
        if (recipe != null) {
            ResourceLocation typeId = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (typeId != null) {
                return typeId;
            }
        }
        if (recipeLayout == null) {
            return null;
        }
        try {
            return recipeLayout.getRecipeCategory().getRecipeType().getUid();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isAdvancedAeReaction(@Nullable ResourceLocation typeId) {
        return typeId != null
                && typeId.getNamespace().equals("advanced_ae")
                && typeId.getPath().equals("reaction");
    }

    private static @Nullable String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        if (!ModList.get().isLoaded("extendedae_plus")) {
            return null;
        }
        try {
            // 直接引用 EAEP 公开 API（compileOnly 依赖）
            String value = com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig.mapRecipeTypeToSearchKey(recipe);
            return value != null && !value.isEmpty() ? value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}

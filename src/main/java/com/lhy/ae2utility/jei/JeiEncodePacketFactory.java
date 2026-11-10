package com.lhy.ae2utility.jei;

import com.lhy.ae2utility.client.EncodePatternDrafts;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;

import appeng.api.stacks.GenericStack;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;

import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;
import com.lhy.ae2utility.integration.multiblock.MultiblockStructurePatternCompat;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;

public final class JeiEncodePacketFactory {
    private JeiEncodePacketFactory() {
    }

    public static Optional<EncodePatternPacket> tryCreate(IRecipeLayoutDrawable<?> recipeLayout, boolean shiftUpload,
            boolean jeiFullCategoryBatch, int bulkEncodeSessionId) {
        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();
        Object recipe = recipeLayout.getRecipe();
        List<GenericStack> outputs = RecipeTransferPacketHelper.getEncodingOutputs(recipe, slotsView);
        boolean multiblockStructureDraft = MultiblockStructurePatternCompat.isSupportedRecipe(
                recipe, hasStructureInputs(slotsView), hasActualOutputs(slotsView));
        if (multiblockStructureDraft) {
            GenericStack draftOutput = MultiblockStructurePatternCompat.createDraftOutput(recipe);
            if (draftOutput == null) {
                return Optional.empty();
            }
            outputs = List.of(draftOutput);
        }
        List<List<GenericStack>> inputs = RecipeTransferPacketHelper.getGenericStacks(
                slotsView, RecipeIngredientRole.INPUT, EncodePatternDrafts.outputKeys(outputs));
        if (multiblockStructureDraft) {
            inputs = filterMultiblockStructureInputs(slotsView, inputs);
            if (!EncodePatternDrafts.hasMeaningfulInputs(inputs)) {
                inputs = MultiblockStructurePatternCompat.extractStructureInputs(recipe).stream()
                        .map(List::of)
                        .toList();
            }
        }

        if (!EncodePatternDrafts.isEncodable(inputs, outputs)) {
            String recipeDesc = recipe instanceof Recipe<?> h && h.getId() != null ? h.getId().toString()
                : (recipe != null ? recipe.getClass().getName() : "<null>");
            JeiEncodeQueueDebugLog.info("JeiEncodePacketFactory.tryCreate rejected recipe={} inputsSlots={} outputsCount={}",
                recipeDesc, inputs.size(), outputs.size());
            return Optional.empty();
        }

        ResourceLocation recipeId = !multiblockStructureDraft && recipe instanceof Recipe<?> holder ? holder.getId() : null;
        String providerSearchKey = EncodePatternDrafts.eaepUploadAvailable(shiftUpload)
                ? EncodePatternDrafts.eaepSearchKey(recipe) : "";
        boolean craftingCategoryHint = !multiblockStructureDraft && isCraftingCategory(recipeLayout, recipe, inputs);
        return Optional.of(EncodePatternDrafts.toPacket(new EncodePatternDrafts.Draft(inputs, outputs, recipeId,
                providerSearchKey, shiftUpload, craftingCategoryHint, jeiFullCategoryBatch, bulkEncodeSessionId, recipe)));
    }

    private static boolean hasStructureInputs(IRecipeSlotsView slotsView) {
        return slotsView.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .anyMatch(slot -> slot.getItemStacks().anyMatch(stack -> !stack.isEmpty()));
    }

    private static boolean hasActualOutputs(IRecipeSlotsView slotsView) {
        return slotsView.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .anyMatch(slot -> slot.getAllIngredients().findAny().isPresent());
    }

    private static List<List<GenericStack>> filterMultiblockStructureInputs(IRecipeSlotsView slotsView,
            List<List<GenericStack>> inputs) {
        List<mezz.jei.api.gui.ingredient.IRecipeSlotView> inputSlots =
                slotsView.getSlotViews(RecipeIngredientRole.INPUT);
        List<List<GenericStack>> filtered = new ArrayList<>(inputs.size());
        for (int index = 0; index < inputs.size(); index++) {
            if (index < inputSlots.size()) {
                List<net.minecraft.world.item.ItemStack> alternatives = inputSlots.get(index).getItemStacks()
                        .filter(stack -> !stack.isEmpty())
                        .map(net.minecraft.world.item.ItemStack::copy)
                        .toList();
                if (MultiblockStructurePatternCompat.isFilteredHatchSlot(alternatives)) {
                    continue;
                }
            }
            filtered.add(inputs.get(index));
        }
        return filtered;
    }

    /** 是否按「工作台合成」处理，边界见 {@link EncodePatternDrafts#fitsCraftingGrid}。 */
    private static boolean isCraftingCategory(IRecipeLayoutDrawable<?> recipeLayout, Object recipe,
            List<List<GenericStack>> inputs) {
        if (!EncodePatternDrafts.fitsCraftingGrid(inputs)) {
            return false;
        }
        if (recipe instanceof CraftingRecipe) {
            return true;
        }
        try {
            return recipeLayout.getRecipeCategory().getRecipeType().equals(mezz.jei.api.constants.RecipeTypes.CRAFTING);
        } catch (Throwable ignored) {
            return false;
        }
    }
}

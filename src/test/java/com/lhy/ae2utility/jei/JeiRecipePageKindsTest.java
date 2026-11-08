package com.lhy.ae2utility.jei;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class JeiRecipePageKindsTest {
    @Test
    void recognizesJeiTagAndInformationImplementationClasses() {
        assertTrue(JeiRecipePageKinds.isTagOrInformationRecipeClass(
                "mezz.jei.library.plugins.jei.tags.TagInfoRecipe"));
        assertTrue(JeiRecipePageKinds.isTagOrInformationRecipeClass(
                "mezz.jei.library.plugins.jei.info.IngredientInfoRecipe"));
    }

    @Test
    void ignoresOrdinaryRecipeClasses() {
        assertFalse(JeiRecipePageKinds.isTagOrInformationRecipeClass(
                "net.minecraft.world.item.crafting.ShapedRecipe"));
        assertFalse(JeiRecipePageKinds.isTagOrInformationRecipeClass(
                "com.sorrowmist.useless.recipe.AlloyFurnaceRecipeCatalog$Entry"));
    }

    @Test
    void recognizesJeiTagRecipeTypeUids() {
        assertTrue(JeiRecipePageKinds.isTagRecipeType(ResourceLocation.fromNamespaceAndPath("jei", "tag_recipes/block")));
        assertTrue(JeiRecipePageKinds.isTagRecipeType(ResourceLocation.fromNamespaceAndPath("jei", "tag_recipes/item")));
        assertFalse(JeiRecipePageKinds.isTagRecipeType(ResourceLocation.fromNamespaceAndPath("minecraft", "crafting")));
        assertFalse(JeiRecipePageKinds.isTagRecipeType(null));
    }
}

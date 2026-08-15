package com.lhy.ae2utility.integration.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

class MultiblockStructurePatternCompatTest {
    private static final class DemoMultiblockInfoPage {
    }

    private static final class DemoRecipePage {
    }

    private record TitledStructure(ResourceLocation id, Component title) {
    }

    private static final class EmiMultiblockWrapper {
        private final TitledStructure structure = new TitledStructure(
                ResourceLocation.fromNamespaceAndPath("ae2lt", "missing_translation"),
                Component.literal("天枢物质扭曲矩阵"));
    }

    @Test
    void recognizesKnownLightningAndNeoEcoWrappers() {
        assertTrue(MultiblockStructurePatternCompat.isKnownStructureRecipe(
                "com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipe"));
        assertTrue(MultiblockStructurePatternCompat.isKnownStructureRecipe(
                "cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper"));
        assertTrue(MultiblockStructurePatternCompat.isKnownStructureRecipe(
                "cn.dancingsnow.neoecoae.integration.emi.recipe.MultiblockEmiRecipe"));
    }

    @Test
    void prefersNestedDisplayTitleOverUntranslatedId() throws ReflectiveOperationException {
        Component resolved = MultiblockStructurePatternCompat.resolveStructureName(new EmiMultiblockWrapper());

        assertEquals("天枢物质扭曲矩阵", resolved.getString());
    }

    @Test
    void acceptsMultiblockInformationPageWithoutActualOutput() {
        assertTrue(MultiblockStructurePatternCompat.isSupportedRecipe(
                new DemoMultiblockInfoPage(), true, false));
    }

    @Test
    void rejectsMultiblockPageWhenStructureInputsAreMissing() {
        assertFalse(MultiblockStructurePatternCompat.isSupportedRecipe(
                new DemoMultiblockInfoPage(), false, false));
    }

    @Test
    void rejectsOrdinaryInformationPageAndRealOutputRecipe() {
        assertFalse(MultiblockStructurePatternCompat.isSupportedRecipe(
                new DemoRecipePage(), true, false));
        assertFalse(MultiblockStructurePatternCompat.isSupportedRecipe(
                new DemoMultiblockInfoPage(), true, true));
    }
}
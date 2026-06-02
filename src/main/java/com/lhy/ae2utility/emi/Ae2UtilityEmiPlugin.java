package com.lhy.ae2utility.emi;

import com.lhy.ae2utility.Ae2UtilityMod;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.registry.EmiRecipeFiller;
import net.minecraft.resources.ResourceLocation;

@EmiEntrypoint
public class Ae2UtilityEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // EMI only renders recipe-decorator widgets when this flag is on. It defaults
        // to off in production (it is meant for developer/compat widgets), which would
        // otherwise hide our encode/upload button entirely. Force-enable it so our
        // player-facing action button shows on every supported recipe.
        EmiConfig.showRecipeDecorators = true;
        registry.addRecipeDecorator(Ae2UtilityEmiPlugin::decorateRecipe);
    }

    private static void decorateRecipe(EmiRecipe recipe, WidgetHolder widgets) {
        if (recipe == null || !recipe.supportsRecipeTree()) {
            return;
        }
        if (EmiEncodePacketFactory.tryCreate(recipe, false).isEmpty()) {
            return;
        }

        int[] pos = computeButtonPosition(recipe);
        // widgets is EMI's WidgetGroup; capture it (direct cast, no reflection) so the
        // button can read the recipe's slot widgets to highlight ingredients on hover.
        dev.emi.emi.screen.WidgetGroup group =
                widgets instanceof dev.emi.emi.screen.WidgetGroup wg ? wg : null;
        widgets.add(new EmiEncodePatternButtonWidget(pos[0], pos[1], recipe, group));
    }

    /**
     * Places our button in the next free cell of EMI's right-side button column,
     * replicating {@code RecipeDisplay.addButtons} so it wraps to a second column
     * exactly like the native fill button does on narrow recipes.
     *
     * <p>EMI fills the inner column (x = width + 5) bottom-up to {@code rows}
     * buttons, then moves outward (+14 px). The set of occupied cells only depends
     * on the count of native right buttons, so we compute that count and target the
     * next cell index.</p>
     */
    private static int[] computeButtonPosition(EmiRecipe recipe) {
        int width = recipe.getDisplayWidth();
        int height = recipe.getDisplayHeight();
        final int displayPadding = 8;

        int nativeButtons = countNativeRightButtons(recipe);
        int rows = Math.max(1, (height + displayPadding + 2) / 14);
        int space = Math.min(8, height + 8 - (Math.min(rows, nativeButtons) * 14 - 2));
        int bottom = height + displayPadding / 2 - 12 - space / 2;

        int cell = nativeButtons; // next free cell after the native buttons
        int col = cell / rows;
        int rowFromBottom = cell % rows;
        int x = width + 5 + col * 14;
        int y = bottom - rowFromBottom * 14;
        return new int[] { x, y };
    }

    private static int countNativeRightButtons(EmiRecipe recipe) {
        int count = 0;
        if (EmiRecipeFiller.isSupported(recipe) && EmiConfig.recipeFillButton) {
            count++;
        }
        if (EmiConfig.recipeTreeButton) {
            count++;
        }
        if (EmiConfig.recipeDefaultButton) {
            count++;
        }
        return count;
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, path);
    }
}

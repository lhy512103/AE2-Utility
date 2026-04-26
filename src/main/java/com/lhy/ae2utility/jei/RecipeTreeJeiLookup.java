package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.client.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeInputViewModel.DisplayOption;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeRecipeViewModel;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.util.GenericIngredientUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.neoforge.NeoForgeTypes;

public final class RecipeTreeJeiLookup {
    private RecipeTreeJeiLookup() {
    }

    public static RecipeTreeRecipeViewModel createRootSnapshot(Object recipe, IRecipeSlotsView recipeSlots) {
        Optional<RecipeTreeRecipeViewModel> resolved = findRecipe(recipe);
        return resolved.orElseGet(() -> createSnapshot(
                recipeSlots,
                null,
                Component.translatable("gui.ae2utility.recipe_tree.root_title"),
                null,
                null));
    }

    public static List<RecipeTreeRecipeViewModel> findRecipesByOutput(@Nullable ITypedIngredient<?> output) {
        if (output == null) {
            return List.of();
        }
        IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) {
            return List.of();
        }

        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        IFocus<?> focus = createFocus(focusFactory, output);
        Map<String, RecipeTreeRecipeViewModel> unique = new LinkedHashMap<>();

        for (RecipeType<?> recipeType : getOrderedRecipeTypes(runtime)) {
            collectFocusedRecipes(runtime, recipeType, focus, unique);
        }

        return unique.values().stream()
                .sorted(Comparator.comparing(view -> view.title().getString()))
                .toList();
    }

    public static Optional<RecipeTreeRecipeViewModel> findRecipe(Object recipe) {
        IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) {
            return Optional.empty();
        }

        for (RecipeType<?> recipeType : getOrderedRecipeTypes(runtime)) {
            Optional<RecipeTreeRecipeViewModel> match = findRecipeInType(runtime, recipeType, recipe);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void collectFocusedRecipes(IJeiRuntime runtime, RecipeType<?> recipeType, IFocus<?> focus,
            Map<String, RecipeTreeRecipeViewModel> unique) {
        IRecipeCategory category = runtime.getRecipeManager().getRecipeCategory((RecipeType) recipeType);
        if (category == null) {
            return;
        }

        List<?> recipes = runtime.getRecipeManager()
                .createRecipeLookup((RecipeType) recipeType)
                .limitFocus(List.of(focus))
                .get()
                .toList();

        for (Object recipe : recipes) {
            createSnapshotForRecipe(runtime, category, recipe)
                    .filter(view -> matchesFocusOutput(runtime.getIngredientManager(), view.primaryOutputIngredient(), focus.getTypedValue()))
                    .ifPresent(view -> unique.putIfAbsent(signatureOf(runtime.getIngredientManager(), view), view));
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Optional<RecipeTreeRecipeViewModel> findRecipeInType(IJeiRuntime runtime, RecipeType<?> recipeType,
            Object targetRecipe) {
        IRecipeCategory category = runtime.getRecipeManager().getRecipeCategory((RecipeType) recipeType);
        if (category == null) {
            return Optional.empty();
        }

        List<?> recipes = runtime.getRecipeManager()
                .createRecipeLookup((RecipeType) recipeType)
                .includeHidden()
                .get()
                .toList();

        for (Object recipe : recipes) {
            if (recipe == targetRecipe || recipe.equals(targetRecipe)) {
                return createSnapshotForRecipe(runtime, category, recipe);
            }
        }

        return Optional.empty();
    }

    private static Optional<RecipeTreeRecipeViewModel> createSnapshotForRecipe(IJeiRuntime runtime, IRecipeCategory category,
            Object recipe) {
        return createSnapshotForRecipeTyped(runtime, category, recipe);
    }

    private static <T> Optional<RecipeTreeRecipeViewModel> createSnapshotForRecipeTyped(IJeiRuntime runtime,
            IRecipeCategory<T> category, Object recipe) {
        T typedRecipe;
        try {
            @SuppressWarnings("unchecked")
            T cast = (T) recipe;
            typedRecipe = cast;
        } catch (ClassCastException ex) {
            return Optional.empty();
        }

        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        var focusGroup = focusFactory.createFocusGroup(List.of());
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, typedRecipe, focusGroup).orElse(null);
        if (layout == null) {
            return Optional.empty();
        }

        Component title = extractTitle(layout.getRecipeSlotsView(), category.getTitle());
        Component subtitle = category.getTitle();
        IDrawable subtitleIcon = category.getIcon();
        ResourceLocation recipeId = category.getRegistryName(typedRecipe);
        return Optional.of(createSnapshot(layout.getRecipeSlotsView(), recipeId, title, subtitle, subtitleIcon));
    }

    private static RecipeTreeRecipeViewModel createSnapshot(IRecipeSlotsView recipeSlots, @Nullable ResourceLocation recipeId,
            Component title, @Nullable Component subtitle, @Nullable IDrawable subtitleIcon) {
        IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        IIngredientManager ingredientManager = runtime == null ? null : runtime.getIngredientManager();
        ITypedIngredient<?> primaryOutputIngredient = null;
        ItemStack primaryOutput = ItemStack.EMPTY;
        int primaryOutputAmount = 1;
        for (IRecipeSlotView outputSlot : recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            ITypedIngredient<?> displayed = getDisplayedIngredient(outputSlot);
            if (displayed != null) {
                primaryOutputIngredient = displayed;
                primaryOutput = extractItemStack(displayed, 1);
                if (ingredientManager != null) {
                    primaryOutputAmount = getIngredientAmount(ingredientManager, displayed, Math.max(1, primaryOutput.getCount()));
                } else {
                    primaryOutputAmount = Math.max(1, primaryOutput.getCount());
                }
                break;
            }
        }

        List<RecipeTreeInputViewModel> inputs = new ArrayList<>();
        for (IRecipeSlotView slotView : recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)) {
            List<DisplayOption> displayOptions = ingredientManager == null ? List.of() : toDisplayOptions(ingredientManager, slotView);
            RequestedIngredient ingredient = toRequestedIngredient(slotView);
            if ((ingredient == null || ingredient.alternatives().isEmpty()) && displayOptions.isEmpty()) {
                continue;
            }
            int amount = 1;
            if (ingredient != null) {
                amount = Math.max(1, ingredient.count());
            } else if (ingredientManager != null) {
                ITypedIngredient<?> displayed = getDisplayedIngredient(slotView);
                if (displayed != null) {
                    amount = getIngredientAmount(ingredientManager, displayed, 1);
                }
            }
            inputs.add(new RecipeTreeInputViewModel(ingredient, displayOptions, amount, formatAmountText(slotView, amount)));
        }

        Component resolvedTitle = title;
        if ((resolvedTitle == null || resolvedTitle.getString().isBlank()) && ingredientManager != null && primaryOutputIngredient != null) {
            resolvedTitle = Component.literal(getIngredientDisplayName(ingredientManager, primaryOutputIngredient));
        } else if (!primaryOutput.isEmpty() && (resolvedTitle == null || resolvedTitle.getString().isBlank())) {
            resolvedTitle = primaryOutput.getHoverName();
        }

        return new RecipeTreeRecipeViewModel(primaryOutputIngredient, primaryOutput, primaryOutputAmount, resolvedTitle, subtitle,
                subtitleIcon, recipeId, inputs);
    }

    private static Component extractTitle(IRecipeSlotsView recipeSlots, Component fallback) {
        IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        IIngredientManager ingredientManager = runtime == null ? null : runtime.getIngredientManager();
        for (IRecipeSlotView outputSlot : recipeSlots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            ITypedIngredient<?> displayed = getDisplayedIngredient(outputSlot);
            if (displayed != null) {
                if (ingredientManager != null) {
                    return Component.literal(getIngredientDisplayName(ingredientManager, displayed));
                }
                ItemStack stack = extractItemStack(displayed, 1);
                if (!stack.isEmpty()) {
                    return stack.getHoverName();
                }
            }
        }
        return fallback;
    }

    private static @Nullable RequestedIngredient toRequestedIngredient(IRecipeSlotView slotView) {
        List<ItemStack> alternatives = slotView.getItemStacks()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .distinct()
                .toList();
        if (alternatives.isEmpty()) {
            return null;
        }
        int count = Math.max(1, getDisplayedStack(slotView).getCount());
        return new RequestedIngredient(alternatives, count);
    }

    private static ItemStack getDisplayedStack(IRecipeSlotView slotView) {
        return slotView.getDisplayedItemStack()
                .or(() -> slotView.getItemStacks().findFirst())
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
    }

    private static @Nullable ITypedIngredient<?> getDisplayedIngredient(IRecipeSlotView slotView) {
        return slotView.getDisplayedIngredient()
                .orElseGet(() -> slotView.getAllIngredients().findFirst().orElse(null));
    }

    private static List<DisplayOption> toDisplayOptions(IIngredientManager ingredientManager, IRecipeSlotView slotView) {
        Map<String, DisplayOption> unique = new LinkedHashMap<>();
        for (ITypedIngredient<?> ingredient : slotView.getAllIngredients().toList()) {
            if (ingredient == null) {
                continue;
            }
            String signature = typedIngredientSignature(ingredientManager, ingredient);
            unique.putIfAbsent(signature, new DisplayOption(ingredient, getIngredientDisplayName(ingredientManager, ingredient),
                    extractItemStack(ingredient, 1)));
        }
        return List.copyOf(unique.values());
    }

    private static String signatureOf(IIngredientManager ingredientManager, RecipeTreeRecipeViewModel view) {
        StringBuilder signature = new StringBuilder();
        ResourceLocation recipeId = view.recipeId();
        if (recipeId != null) {
            signature.append(recipeId);
        }
        ITypedIngredient<?> outputIngredient = view.primaryOutputIngredient();
        if (signature.isEmpty() && outputIngredient != null) {
            signature.append(typedIngredientSignature(ingredientManager, outputIngredient));
        } else if (signature.isEmpty()) {
            ItemStack output = view.primaryOutput();
            signature.append(ItemStack.hashItemAndComponents(output)).append('#').append(output.getItem());
        }
        signature.append('#').append(view.title().getString());
        Component subtitle = view.subtitle();
        if (subtitle != null) {
            signature.append('#').append(subtitle.getString());
        }
        for (RecipeTreeInputViewModel input : view.inputs()) {
            signature.append('|').append(input.amount()).append(':');
            ITypedIngredient<?> displayed = input.displayIngredient();
            if (displayed != null) {
                signature.append(typedIngredientSignature(ingredientManager, displayed));
            } else {
                signature.append(input.displayName());
            }
        }
        return signature.toString();
    }

    private static IFocus<?> createFocus(IFocusFactory focusFactory, ITypedIngredient<?> ingredient) {
        return createFocusTyped(focusFactory, ingredient);
    }

    private static <T> IFocus<T> createFocusTyped(IFocusFactory focusFactory, ITypedIngredient<?> ingredient) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        return focusFactory.createFocus(RecipeIngredientRole.OUTPUT, typed);
    }

    private static boolean matchesFocusOutput(IIngredientManager ingredientManager, @Nullable ITypedIngredient<?> output,
            ITypedIngredient<?> focus) {
        if (output == null) {
            return false;
        }
        return typedIngredientSignature(ingredientManager, output).equals(typedIngredientSignature(ingredientManager, focus));
    }

    private static String typedIngredientSignature(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        return typedIngredientSignatureTyped(ingredientManager, ingredient);
    }

    private static <T> String typedIngredientSignatureTyped(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        IIngredientHelper<T> helper = ingredientManager.getIngredientHelper(typed.getType());
        return typed.getType().getUid() + "#" + helper.getUid(typed, UidContext.Ingredient);
    }

    private static String getIngredientDisplayName(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        return getIngredientDisplayNameTyped(ingredientManager, ingredient);
    }

    private static <T> String getIngredientDisplayNameTyped(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        return ingredientManager.getIngredientHelper(typed.getType()).getDisplayName(typed.getIngredient());
    }

    private static int getIngredientAmount(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient, int fallback) {
        return getIngredientAmountTyped(ingredientManager, ingredient, fallback);
    }

    private static <T> int getIngredientAmountTyped(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient, int fallback) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        long amount = ingredientManager.getIngredientHelper(typed.getType()).getAmount(typed.getIngredient());
        if (amount <= 0) {
            return Math.max(1, fallback);
        }
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, amount));
    }

    private static String formatAmountText(IRecipeSlotView slotView, int fallbackAmount) {
        int itemCount = slotView.getIngredients(VanillaTypes.ITEM_STACK)
                .filter(stack -> !stack.isEmpty())
                .mapToInt(ItemStack::getCount)
                .filter(count -> count > 1)
                .findFirst()
                .orElse(0);
        if (itemCount > 1) {
            return "x" + itemCount;
        }

        int fluidAmount = slotView.getIngredients(NeoForgeTypes.FLUID_STACK)
                .filter(stack -> !stack.isEmpty())
                .mapToInt(FluidStack::getAmount)
                .filter(amount -> amount > 0)
                .findFirst()
                .orElse(0);
        if (fluidAmount > 0) {
            return fluidAmount + " mB";
        }

        ITypedIngredient<?> displayed = getDisplayedIngredient(slotView);
        if (displayed != null) {
            String mekanismChemicalAmount = tryFormatMekanismChemicalAmount(displayed.getIngredient());
            if (!mekanismChemicalAmount.isBlank()) {
                return mekanismChemicalAmount;
            }
        }

        return "x" + Math.max(1, fallbackAmount);
    }

    private static String tryFormatMekanismChemicalAmount(Object ingredient) {
        long amount = GenericIngredientUtil.tryGetMekanismChemicalAmount(ingredient);
        return amount > 0 ? amount + " mB" : "";
    }

    private static ItemStack extractItemStack(ITypedIngredient<?> ingredient, int fallbackCount) {
        return extractItemStackTyped(ingredient, fallbackCount);
    }

    private static <T> ItemStack extractItemStackTyped(ITypedIngredient<?> ingredient, int fallbackCount) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        return typed.getIngredient(VanillaTypes.ITEM_STACK)
                .map(stack -> stack.copyWithCount(Math.max(1, fallbackCount)))
                .orElse(ItemStack.EMPTY);
    }

    private static List<RecipeType<?>> getOrderedRecipeTypes(IJeiRuntime runtime) {
        List<RecipeType<?>> allTypes = new ArrayList<>(runtime.getJeiHelpers().getAllRecipeTypes().toList());
        List<RecipeType<?>> ordered = new ArrayList<>(allTypes.size());
        if (allTypes.remove(RecipeTypes.CRAFTING)) {
            ordered.add(RecipeTypes.CRAFTING);
        }
        ordered.addAll(allTypes);
        return ordered;
    }
}

package com.lhy.ae2utility.compat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import com.lhy.ae2utility.client.RemoteEncodeRules;
import com.lhy.ae2utility.client.RecipeTreeUploadQueue;
import com.lhy.ae2utility.client.jei.BlankPatternClientPrecheck;
import com.lhy.ae2utility.jei.BulkEncodeSessions;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.util.GenericIngredientUtil;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Optional bridge to JEI Crafting Tree.
 *
 * <p>All calls are reflective so AE2 Utility can still build and run without JEICT.
 */
public final class JeictCompat {
    private static volatile boolean backendRegistrationAttempted;

    private JeictCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("jeict");
    }

    public static boolean openFromLayout(IRecipeLayoutDrawable<?> recipeLayout, Screen returnScreen) {
        if (!isLoaded()) {
            return false;
        }
        try {
            Class<?> api = Class.forName("com.lhy.jeict.api.JeiCraftingTreeApi");
            api.getMethod("openFromLayout", IRecipeLayoutDrawable.class, Screen.class)
                    .invoke(null, recipeLayout, returnScreen);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean open(Object recipe, IRecipeSlotsView recipeSlots, Screen returnScreen) {
        if (!isLoaded()) {
            return false;
        }
        try {
            Class<?> api = Class.forName("com.lhy.jeict.api.JeiCraftingTreeApi");
            api.getMethod("open", Object.class, IRecipeSlotsView.class, Screen.class)
                    .invoke(null, recipe, recipeSlots, returnScreen);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void tryRegisterBackend() {
        if (backendRegistrationAttempted || !isLoaded()) {
            return;
        }
        backendRegistrationAttempted = true;
        try {
            Class<?> backendInterface = Class.forName("com.lhy.jeict.api.CraftingTreeBackend");
            Class<?> registry = Class.forName("com.lhy.jeict.api.CraftingTreeBackends");
            Object backend = Proxy.newProxyInstance(
                    backendInterface.getClassLoader(),
                    new Class<?>[] { backendInterface },
                    new BackendInvocationHandler());
            registry.getMethod("register", backendInterface).invoke(null, backend);
        } catch (Throwable ignored) {
        }
    }

    private static final class BackendInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "supportsExistingPatternHints", "supportsEncode", "supportsSubstitution" -> true;
                case "supportsUpload" -> EaepCompat.isExtendedAePlusLoaded();
                case "isCraftable" -> isCraftable(args == null ? null : args[0]);
                case "pollExistingPatternCachesStale" -> CraftableStateCache.pollRecipeTreeOverlayCachesStale();
                case "isStrictEncodable" -> toEncodePacket(args[0], false, 0) != null;
                case "encodePatterns" -> encodePatterns(castRecipeList(args[0]), false);
                case "uploadPatterns" -> encodePatterns(castRecipeList(args[0]), true);
                case "itemSubstituteOn" -> JeiPatternSubstitutionUi.isItemSubstituteOn();
                case "fluidSubstituteOn" -> JeiPatternSubstitutionUi.isFluidSubstituteOn();
                case "toggleItemSubstitute" -> {
                    JeiPatternSubstitutionUi.toggleItemSubstitute();
                    yield null;
                }
                case "toggleFluidSubstitute" -> {
                    JeiPatternSubstitutionUi.toggleFluidSubstitute();
                    yield null;
                }
                case "renderSubstitutionIcon" -> {
                    renderSubstitutionIcon((GuiGraphics) args[0], (Integer) args[1], (Integer) args[2],
                            (Integer) args[3], (Integer) args[4], (Boolean) args[5]);
                    yield null;
                }
                case "substitutionTooltip" -> substitutionTooltip((Boolean) args[0]);
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    private static boolean isCraftable(@Nullable Object rawIngredient) {
        var key = GenericIngredientUtil.toAEKey(rawIngredient);
        return key != null && CraftableStateCache.isCraftable(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castRecipeList(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    private static boolean encodePatterns(List<Object> recipes, boolean uploadMode) {
        int bulkSid = BulkEncodeSessions.next();
        List<EncodePatternPacket> packets = new ArrayList<>();
        for (Object recipe : recipes) {
            EncodePatternPacket packet = toEncodePacket(recipe, uploadMode, bulkSid);
            if (packet != null && !isRecipeOutputCraftable(recipe)) {
                packets.add(packet);
            }
        }
        if (packets.isEmpty()) {
            return false;
        }
        int originalCount = packets.size();
        packets = new ArrayList<>(RemoteEncodeRules.capPacketsToServerBulkLimit(packets));
        if (packets.size() < originalCount && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("message.ae2utility.bulk_encode_truncated_client_notice", originalCount, packets.size())
                            .withStyle(ChatFormatting.GOLD),
                    false);
        }
        if (stopBatchEncodeIfLocallyNoDetectableBlank()) {
            return false;
        }
        if (uploadMode) {
            RecipeTreeUploadQueue.startReplacing(packets);
        } else {
            for (EncodePatternPacket packet : packets) {
                PacketDistributor.sendToServer(packet);
            }
        }
        return true;
    }

    private static boolean stopBatchEncodeIfLocallyNoDetectableBlank() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (BlankPatternClientPrecheck.lacksAnyDetectableBlankPattern(player)) {
            player.displayClientMessage(Component.translatable("message.ae2utility.batch_encode_precheck_no_blank")
                    .withStyle(ChatFormatting.GOLD), false);
            return true;
        }
        return false;
    }

    private static boolean isRecipeOutputCraftable(Object recipe) {
        Object typed = invoke(recipe, "primaryOutputIngredient");
        if (typed instanceof ITypedIngredient<?> ingredient) {
            return isCraftable(ingredient.getIngredient());
        }
        return false;
    }

    private static @Nullable EncodePatternPacket toEncodePacket(Object recipe, boolean uploadMode, int bulkSid) {
        Object outputIngredient = invoke(recipe, "primaryOutputIngredient");
        if (!(outputIngredient instanceof ITypedIngredient<?> typedOutput)) {
            return null;
        }
        int outputCount = intValue(invoke(recipe, "primaryOutputCount"), 1);
        GenericStack output = GenericIngredientUtil.toGenericStack(typedOutput, outputCount);
        if (output == null) {
            return null;
        }

        List<List<GenericStack>> inputs = new ArrayList<>();
        Object rawInputs = invoke(recipe, "inputs");
        if (rawInputs instanceof List<?> inputList) {
            for (Object input : inputList) {
                List<GenericStack> alternatives = collectInputAlternatives(input);
                inputs.add(alternatives.isEmpty() ? null : alternatives);
            }
        }

        ResourceLocation recipeId = invoke(recipe, "recipeId") instanceof ResourceLocation id ? id : null;
        String patternName = componentString(invoke(recipe, "title"), "-");
        String providerKey = deriveProviderSearchKey(recipe);
        return new EncodePatternPacket(inputs, List.of(output), recipeId, patternName, providerKey, providerKey,
                uploadMode, JeiPatternSubstitutionUi.isItemSubstituteOn(), JeiPatternSubstitutionUi.isFluidSubstituteOn(),
                true, uploadMode, false, bulkSid);
    }

    private static List<GenericStack> collectInputAlternatives(Object input) {
        List<GenericStack> alternatives = new ArrayList<>();
        Object requested = invoke(input, "selectedRequestedIngredient");
        if (requested != null) {
            int count = intValue(invoke(requested, "count"), 1);
            Object rawAlternatives = invoke(requested, "alternatives");
            if (rawAlternatives instanceof List<?> stacks) {
                for (Object raw : stacks) {
                    if (raw instanceof ItemStack stack && !stack.isEmpty()) {
                        alternatives.add(GenericStack.fromItemStack(stack.copyWithCount(Math.max(1, count))));
                    }
                }
            }
            return alternatives;
        }

        int amount = intValue(invoke(input, "amount"), 1);
        Object displayOptions = invoke(input, "orderedDisplayOptions");
        if (displayOptions instanceof List<?> options) {
            for (Object option : options) {
                Object typed = invoke(option, "typedIngredient");
                if (typed instanceof ITypedIngredient<?> ingredient) {
                    GenericStack stack = GenericIngredientUtil.toGenericStack(ingredient, amount);
                    if (stack != null) {
                        alternatives.add(stack);
                    }
                }
            }
        }
        return alternatives;
    }

    private static String deriveProviderSearchKey(Object recipe) {
        if (!EaepCompat.isExtendedAePlusLoaded()) {
            return "";
        }
        try {
            Class<?> uploadUtil = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
            Object recipeId = invoke(recipe, "recipeId");
            if (recipeId instanceof ResourceLocation id && Minecraft.getInstance().level != null) {
                var holder = Minecraft.getInstance().level.getRecipeManager().byKey(id).orElse(null);
                if (holder != null) {
                    if (holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe) {
                        return (String) uploadUtil.getField("DEFAULT_CRAFTING_SEARCH_KEY").get(null);
                    }
                    Object key = uploadUtil.getMethod("mapRecipeTypeToSearchKey",
                            net.minecraft.world.item.crafting.Recipe.class).invoke(null, holder.value());
                    if (key instanceof String s && !s.isBlank()) {
                        return s;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return componentString(invoke(recipe, "subtitle"), "");
    }

    private static void renderSubstitutionIcon(GuiGraphics graphics, int x, int y, int srcSize, int dstSize, boolean fluid) {
        float scale = dstSize / (float) srcSize;
        Icon icon = fluid
                ? (JeiPatternSubstitutionUi.isFluidSubstituteOn() ? Icon.S_FLUID_SUBSTITUTION_ENABLED : Icon.S_FLUID_SUBSTITUTION_DISABLED)
                : (JeiPatternSubstitutionUi.isItemSubstituteOn() ? Icon.S_SUBSTITUTION_ENABLED : Icon.S_SUBSTITUTION_DISABLED);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        icon.getBlitter().dest(0, 0, srcSize, srcSize).blit(graphics);
        graphics.pose().popPose();
    }

    private static List<Component> substitutionTooltip(boolean fluid) {
        List<Component> lines = new ArrayList<>(3);
        if (fluid) {
            lines.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutions"));
            lines.add(Component.translatable(JeiPatternSubstitutionUi.isFluidSubstituteOn()
                    ? "gui.tooltips.ae2.FluidSubstitutionsDescEnabled"
                    : "gui.tooltips.ae2.FluidSubstitutionsDescDisabled").withStyle(ChatFormatting.GRAY));
        } else if (JeiPatternSubstitutionUi.isItemSubstituteOn()) {
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsOn"));
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsDescEnabled").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsOff"));
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsDescDisabled").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.ae2utility.recipe_tree.overview_substitution_encode_hint")
                .withStyle(ChatFormatting.DARK_AQUA));
        return lines;
    }

    private static @Nullable Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static String componentString(Object value, String fallback) {
        return value instanceof Component component && !component.getString().isBlank()
                ? component.getString()
                : fallback;
    }

    private static @Nullable Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        return null;
    }
}

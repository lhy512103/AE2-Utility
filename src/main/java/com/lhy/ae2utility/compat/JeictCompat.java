package com.lhy.ae2utility.compat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.RemoteEncodeRules;
import com.lhy.ae2utility.jei.BlankPatternClientPrecheck;
import com.lhy.ae2utility.jei.BulkEncodeSessions;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.JeiBatchEncodeQueue;
import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.util.GenericIngredientUtil;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraftforge.fml.ModList;

/**
 * JEI Crafting Tree（jeict）可选桥接：全部通过反射调用，未安装时不影响其它功能。
 */
public final class JeictCompat {
    private static final int MAX_DRAFT_INPUTS = 81;
    private static final int MAX_DRAFT_OUTPUTS = 27;
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
            Class.forName("com.lhy.jeict.api.JeiCraftingTreeApi")
                    .getMethod("openFromLayout", IRecipeLayoutDrawable.class, Screen.class)
                    .invoke(null, recipeLayout, returnScreen);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            Ae2UtilityMod.LOGGER.error("Failed to open JEI Crafting Tree", e);
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
            Object backend = Proxy.newProxyInstance(backendInterface.getClassLoader(),
                    new Class<?>[] { backendInterface }, new BackendInvocationHandler());
            registry.getMethod("register", backendInterface).invoke(null, backend);
        } catch (ReflectiveOperationException | LinkageError e) {
            Ae2UtilityMod.LOGGER.warn("Failed to register AE2 Utility as JEI Crafting Tree backend", e);
        }
    }

    private static final class BackendInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "supportsExistingPatternHints", "supportsEncode", "supportsSubstitution",
                        "supportsEditablePatternDrafts" -> true;
                case "supportsUpload" -> ModList.get().isLoaded("extendedae_plus");
                case "patternMode" -> patternMode(method.getReturnType(), args[0]);
                case "isCraftable", "isOutputCraftable" -> isCraftable(args == null ? null : args[0]);
                case "hasExactPattern" -> isRecipeOutputCraftable(args[0]);
                case "exactPatternFingerprint" -> String.valueOf(JeictCompat.invoke(args[0], "stableIdentity"));
                case "pollExistingPatternCachesStale" -> CraftableStateCache.pollExistingPatternCachesStale();
                case "isStrictEncodable" -> toEncodePacket(args[0], false) != null;
                case "encodePatterns" -> encodePatterns(castList(args[0]), false);
                case "uploadPatterns" -> encodePatterns(castList(args[0]), true);
                case "validatePatternDraft" -> validatePatternDraft(args[0]);
                case "hasExactPatternDraft" -> hasExactPatternDraft(args[0]);
                case "encodePatternDrafts" -> encodePatternDrafts(castList(args[0]), false);
                case "uploadPatternDrafts" -> encodePatternDrafts(castList(args[0]), true);
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
                case "machineId" -> componentString(JeictCompat.invoke(args[0], "subtitle"), "crafting");
                case "isReusableInput" -> false;
                case "toString" -> "AE2 Utility JEI Crafting Tree backend";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> defaultValue(method.getReturnType());
            };
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object patternMode(Class<?> returnType, Object recipe) {
        return returnType.isEnum() ? Enum.valueOf((Class<? extends Enum>) returnType, patternModeName(recipe)) : null;
    }

    private static String patternModeName(Object recipe) {
        Recipe<?> resolved = resolveRecipe(recipe);
        if (resolved instanceof SmithingRecipe) {
            return "SMITHING_TABLE";
        }
        if (resolved instanceof StonecutterRecipe) {
            return "STONECUTTING";
        }
        if (resolved instanceof CraftingRecipe) {
            return "CRAFTING";
        }
        return "PROCESSING";
    }

    private static @Nullable Recipe<?> resolveRecipe(Object recipe) {
        var level = Minecraft.getInstance().level;
        if (!(invoke(recipe, "recipeId") instanceof ResourceLocation id) || level == null) {
            return null;
        }
        return level.getRecipeManager().byKey(id).orElse(null);
    }

    private static List<Component> validatePatternDraft(Object request) {
        if (invoke(request, "draft") == null) {
            return List.of(Component.literal("Missing pattern draft"));
        }
        return toEncodePacketFromDraft(request, false) == null
                ? List.of(Component.translatable("message.ae2utility.jeict_pattern_draft_invalid"))
                : List.of();
    }

    private static boolean hasExactPatternDraft(Object request) {
        if (Boolean.TRUE.equals(invoke(invoke(request, "draft"), "isDirty"))) {
            return false;
        }
        Object recipe = invoke(request, "recipe");
        return recipe != null && isRecipeOutputCraftable(recipe);
    }

    private static boolean encodePatternDrafts(List<Object> requests, boolean uploadMode) {
        List<EncodePatternPacket> packets = new ArrayList<>();
        for (Object request : requests) {
            EncodePatternPacket packet = toEncodePacketFromDraft(request, uploadMode);
            if (packet != null) {
                packets.add(packet);
            }
        }
        return dispatchPackets(packets, uploadMode);
    }

    private static boolean encodePatterns(List<Object> recipes, boolean uploadMode) {
        List<EncodePatternPacket> packets = new ArrayList<>();
        for (Object recipe : recipes) {
            EncodePatternPacket packet = toEncodePacket(recipe, uploadMode);
            if (packet != null && !isRecipeOutputCraftable(recipe)) {
                packets.add(packet);
            }
        }
        return dispatchPackets(packets, uploadMode);
    }

    private static boolean dispatchPackets(List<EncodePatternPacket> source, boolean uploadMode) {
        var player = Minecraft.getInstance().player;
        List<EncodePatternPacket> fitting = source.stream()
                .filter(packet -> packet.fitsNetworkLimits())
                .toList();
        if (fitting.isEmpty() || player == null) {
            return false;
        }
        List<EncodePatternPacket> capped = RemoteEncodeRules.capToSessionLimit(fitting);
        if (capped.size() < fitting.size()) {
            player.displayClientMessage(Objects.requireNonNull(
                    Component.translatable("message.ae2utility.bulk_encode_truncated_client_notice",
                            fitting.size(), capped.size()).withStyle(ChatFormatting.GOLD)), false);
        }
        if (BlankPatternClientPrecheck.lacksAnyDetectableBlankPattern(player)) {
            player.displayClientMessage(Objects.requireNonNull(
                    Component.translatable("message.ae2utility.batch_encode_precheck_no_blank")
                            .withStyle(ChatFormatting.GOLD)), false);
            return false;
        }
        int sessionId = BulkEncodeSessions.next();
        List<EncodePatternPacket> tagged = capped.stream().map(packet -> packet.withBatch(false, sessionId)).toList();
        if (uploadMode) {
            return JeiBatchEncodeQueue.start(tagged, "");
        }
        tagged.forEach(ModNetworking::sendToServer);
        return true;
    }

    private static boolean isCraftable(@Nullable Object rawIngredient) {
        var key = GenericIngredientUtil.toAEKey(rawIngredient);
        return key != null && CraftableStateCache.isCraftable(key);
    }

    private static boolean isRecipeOutputCraftable(Object recipe) {
        return invoke(recipe, "primaryOutputIngredient") instanceof ITypedIngredient<?> ingredient
                && isCraftable(ingredient.getIngredient());
    }

    private static @Nullable EncodePatternPacket toEncodePacketFromDraft(Object request, boolean uploadMode) {
        Object recipe = invoke(request, "recipe");
        Object draft = invoke(request, "draft");
        if (recipe == null || draft == null) {
            return null;
        }
        List<List<GenericStack>> inputs = new ArrayList<>();
        if (invoke(draft, "inputs") instanceof List<?> slots) {
            if (slots.size() > MAX_DRAFT_INPUTS) {
                return null;
            }
            for (Object slot : slots) {
                inputs.add(slot == null ? null : collectDraftAlternatives(slot));
            }
        }
        List<GenericStack> outputs = new ArrayList<>();
        if (invoke(draft, "outputs") instanceof List<?> slots) {
            if (slots.size() > MAX_DRAFT_OUTPUTS) {
                return null;
            }
            for (Object slot : slots) {
                outputs.add(slot != null && invoke(slot, "ingredient") instanceof ITypedIngredient<?> typed
                        ? GenericIngredientUtil.toGenericStackExact(typed.getIngredient(),
                                longValue(invoke(slot, "amount"), 1L))
                        : null);
            }
        }
        boolean hasInput = inputs.stream().anyMatch(slot -> slot != null && !slot.isEmpty());
        if (!hasInput || outputs.isEmpty() || outputs.get(0) == null) {
            return null;
        }
        ResourceLocation recipeId = invoke(draft, "recipeId") instanceof ResourceLocation id ? id : null;
        String patternName = stringValue(invoke(draft, "patternName"), componentString(invoke(recipe, "title"), "-"));
        boolean crafting = !"PROCESSING".equals(String.valueOf(invoke(draft, "mode")));
        return new EncodePatternPacket(inputs, outputs, recipeId, patternName, deriveProviderSearchKey(recipe),
                uploadMode, booleanValue(invoke(draft, "substituteItems")),
                booleanValue(invoke(draft, "substituteFluids")),
                !Boolean.FALSE.equals(invoke(draft, "preserveInputOrder")), crafting);
    }

    private static List<GenericStack> collectDraftAlternatives(Object slot) {
        List<GenericStack> converted = new ArrayList<>();
        long amount = longValue(invoke(slot, "amount"), 1L);
        int selected = intValue(invoke(slot, "selectedAlternative"), 0);
        if (invoke(slot, "alternatives") instanceof List<?> alternatives && !alternatives.isEmpty()) {
            for (int i = 0; i < alternatives.size(); i++) {
                if (alternatives.get(Math.floorMod(selected + i, alternatives.size())) instanceof ITypedIngredient<?> typed) {
                    GenericStack stack = GenericIngredientUtil.toGenericStackExact(typed.getIngredient(), amount);
                    if (stack != null) {
                        converted.add(stack);
                    }
                }
            }
        }
        if (converted.isEmpty() && invoke(slot, "ingredient") instanceof ITypedIngredient<?> selectedIngredient) {
            GenericStack stack = GenericIngredientUtil.toGenericStackExact(selectedIngredient.getIngredient(), amount);
            if (stack != null) {
                converted.add(stack);
            }
        }
        return converted;
    }

    private static @Nullable EncodePatternPacket toEncodePacket(Object recipe, boolean uploadMode) {
        if (!(invoke(recipe, "primaryOutputIngredient") instanceof ITypedIngredient<?> typedOutput)) {
            return null;
        }
        GenericStack output = GenericIngredientUtil.toGenericStack(typedOutput,
                intValue(invoke(recipe, "primaryOutputCount"), 1));
        if (output == null) {
            return null;
        }
        List<List<GenericStack>> inputs = new ArrayList<>();
        if (invoke(recipe, "inputs") instanceof List<?> inputList) {
            for (Object input : inputList) {
                List<GenericStack> alternatives = collectInputAlternatives(input);
                inputs.add(alternatives.isEmpty() ? null : alternatives);
            }
        }
        ResourceLocation recipeId = invoke(recipe, "recipeId") instanceof ResourceLocation id ? id : null;
        return new EncodePatternPacket(inputs, List.of(output), recipeId, componentString(invoke(recipe, "title"), "-"),
                deriveProviderSearchKey(recipe), uploadMode, JeiPatternSubstitutionUi.isItemSubstituteOn(),
                JeiPatternSubstitutionUi.isFluidSubstituteOn(), true, false);
    }

    private static List<GenericStack> collectInputAlternatives(Object input) {
        List<GenericStack> alternatives = new ArrayList<>();
        Object requested = invoke(input, "selectedRequestedIngredient");
        if (requested != null) {
            int count = intValue(invoke(requested, "count"), 1);
            if (invoke(requested, "alternatives") instanceof List<?> stacks) {
                for (Object raw : stacks) {
                    if (raw instanceof ItemStack stack && !stack.isEmpty()) {
                        alternatives.add(GenericStack.fromItemStack(stack.copyWithCount(Math.max(1, count))));
                    }
                }
            }
            return alternatives;
        }
        int amount = intValue(invoke(input, "amount"), 1);
        if (invoke(input, "orderedDisplayOptions") instanceof List<?> options) {
            for (Object option : options) {
                if (invoke(option, "typedIngredient") instanceof ITypedIngredient<?> ingredient) {
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
        if (!ModList.get().isLoaded("extendedae_plus")) {
            return "";
        }
        Recipe<?> resolved = resolveRecipe(recipe);
        if (resolved instanceof CraftingRecipe) {
            return "crafting";
        }
        if (resolved != null) {
            try {
                String key = com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig.mapRecipeTypeToSearchKey(resolved);
                if (key != null && !key.isBlank()) {
                    return key;
                }
            } catch (LinkageError ignored) {
            }
        }
        return componentString(invoke(recipe, "subtitle"), "");
    }

    private static void renderSubstitutionIcon(GuiGraphics graphics, int x, int y, int srcSize, int dstSize,
            boolean fluid) {
        Icon icon = fluid
                ? (JeiPatternSubstitutionUi.isFluidSubstituteOn() ? Icon.FLUID_SUBSTITUTION_ENABLED
                        : Icon.FLUID_SUBSTITUTION_DISABLED)
                : (JeiPatternSubstitutionUi.isItemSubstituteOn() ? Icon.SUBSTITUTION_ENABLED
                        : Icon.SUBSTITUTION_DISABLED);
        icon.getBlitter().dest(x, y, dstSize, dstSize).blit(graphics);
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
        lines.add(Component.translatable("gui.ae2utility.bulk_encode.substitution_hint").withStyle(ChatFormatting.DARK_AQUA));
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    private static @Nullable Object invoke(@Nullable Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static int intValue(@Nullable Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long longValue(@Nullable Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static boolean booleanValue(@Nullable Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static String stringValue(@Nullable Object value, String fallback) {
        return value instanceof String string && !string.isBlank() ? string : fallback;
    }

    private static String componentString(@Nullable Object value, String fallback) {
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
        if (returnType == long.class) {
            return 0L;
        }
        if (List.class.isAssignableFrom(returnType)) {
            return List.of();
        }
        return null;
    }
}

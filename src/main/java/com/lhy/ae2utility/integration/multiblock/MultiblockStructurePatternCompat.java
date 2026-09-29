package com.lhy.ae2utility.integration.multiblock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;
import com.lhy.ae2utility.Ae2UtilityMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;

/**
 * 把没有真实产物的多方块结构信息页（GTCEu/GTL、Neo ECO）转成处理样板草稿：
 * 输入为结构材料，产物为以结构名命名的附魔书。
 */
public final class MultiblockStructurePatternCompat {
    private static final String GTLCORE_MOD_ID = "gtlcore";
    private static final String GTCEU_MULTIBLOCK_INFO_WRAPPER =
            "com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper";
    private static final String NEOECO_MULTIBLOCK_RECIPE =
            "cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiblockInfoRecipe";
    private static final String NEOECO_DEFINITION_CLASS =
            "cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition";
    private static final String NEOECO_REQUIREMENTS_CLASS =
            "cn.dancingsnow.neoecoae.multiblock.StructureTerminalMaterialRequirements";
    private static final String GTL_CONFIG_CLASS = "org.gtlcore.gtlcore.config.ConfigHolder";
    private static final Set<Class<?>> UNSUPPORTED_RECIPE_CLASSES = ConcurrentHashMap.newKeySet();
    private static volatile String @Nullable [] cachedGtlHatchFilters;

    private MultiblockStructurePatternCompat() {
    }

    public static boolean isSupportedRecipe(@Nullable Object recipe, boolean hasStructureInputs, boolean hasActualOutputs) {
        if (recipe == null || UNSUPPORTED_RECIPE_CLASSES.contains(recipe.getClass())) {
            return false;
        }
        boolean knownStructureRecipe = isKnownStructureRecipe(recipe.getClass().getName());
        if (!knownStructureRecipe
                && !recipe.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("multiblock")) {
            return false;
        }
        if (!hasStructureInputs && !canExtractStructureInputs(recipe)) {
            return false;
        }
        return knownStructureRecipe || !hasActualOutputs;
    }

    static boolean isKnownStructureRecipe(String className) {
        return GTCEU_MULTIBLOCK_INFO_WRAPPER.equals(className) || NEOECO_MULTIBLOCK_RECIPE.equals(className);
    }

    private static boolean canExtractStructureInputs(Object recipe) {
        return NEOECO_MULTIBLOCK_RECIPE.equals(recipe.getClass().getName());
    }

    public static boolean isFilteredHatchSlot(List<ItemStack> alternatives) {
        String[] filters = getGtlHatchFilters();
        return filters.length > 0 && !alternatives.isEmpty() && alternatives.stream().allMatch(stack -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String itemId = id == null ? "" : id.toString();
            return Arrays.stream(filters)
                    .filter(filter -> filter != null && !filter.isBlank())
                    .anyMatch(itemId::contains);
        });
    }

    public static List<GenericStack> extractStructureInputs(Object recipe) {
        try {
            Object definition = unwrapStructureMetadata(recipe);
            if (definition != null && NEOECO_DEFINITION_CLASS.equals(definition.getClass().getName())) {
                return extractNeoEcoInputs(definition);
            }
            return List.of();
        } catch (ReflectiveOperationException | LinkageError e) {
            UNSUPPORTED_RECIPE_CLASSES.add(recipe.getClass());
            Ae2UtilityMod.LOGGER.warn("Failed to read multiblock structure inputs from {}", recipe.getClass().getName(), e);
            return List.of();
        }
    }

    private static List<GenericStack> extractNeoEcoInputs(Object definition) throws ReflectiveOperationException {
        Class<?> definitionClass = Class.forName(NEOECO_DEFINITION_CLASS);
        int length = definitionClass.getMethod("getExpandMin").invoke(definition) instanceof Number n ? n.intValue() : 1;
        Method collect = Class.forName(NEOECO_REQUIREMENTS_CLASS)
                .getMethod("collectRequiredItems", definitionClass, int.class);
        if (!(collect.invoke(null, definition, length) instanceof List<?> items)) {
            return List.of();
        }
        List<GenericStack> inputs = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item instanceof ItemStack stack && !stack.isEmpty()) {
                GenericStack generic = GenericStack.fromItemStack(stack);
                if (generic != null) {
                    inputs.add(generic);
                }
            }
        }
        return List.copyOf(inputs);
    }

    public static @Nullable GenericStack createDraftOutput(Object recipe) {
        try {
            ItemStack draft = new ItemStack(Items.ENCHANTED_BOOK);
            draft.setHoverName(resolveStructureName(recipe).copy().withStyle(Style.EMPTY.withColor(0xFC5AFC)));
            return GenericStack.fromItemStack(draft);
        } catch (ReflectiveOperationException | LinkageError e) {
            UNSUPPORTED_RECIPE_CLASSES.add(recipe.getClass());
            Ae2UtilityMod.LOGGER.warn("Failed to read multiblock structure metadata from {}", recipe.getClass().getName(), e);
            return null;
        }
    }

    private static String[] getGtlHatchFilters() {
        String[] cached = cachedGtlHatchFilters;
        if (cached != null) {
            return cached;
        }
        String[] filters = new String[0];
        if (ModList.get().isLoaded(GTLCORE_MOD_ID)) {
            try {
                Class<?> config = Class.forName(GTL_CONFIG_CLASS);
                Object instance = getField(config, "INSTANCE").get(null);
                Object value = getField(config, "filterHatch").get(instance);
                if (value instanceof String[] array) {
                    filters = array.clone();
                } else if (value instanceof List<?> list) {
                    filters = list.stream().filter(String.class::isInstance).map(String.class::cast).toArray(String[]::new);
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        cachedGtlHatchFilters = filters;
        return filters;
    }

    static Component resolveStructureName(Object recipe) throws ReflectiveOperationException {
        Object target = unwrapStructureMetadata(recipe);
        Component component = tryReadComponent(recipe, "title", "getTitle", "getDisplayName", "getName", "name");
        if (component == null && target != recipe) {
            component = tryReadComponent(target, "title", "getTitle", "getDisplayName", "getName", "name");
        }
        if (component != null && !component.getString().isBlank()) {
            return component;
        }
        ResourceLocation id = tryReadId(target);
        if (id == null && target != recipe) {
            id = tryReadId(recipe);
        }
        if (id != null) {
            return Component.translatable(id.toLanguageKey("block"));
        }
        String fallback = target.getClass().getSimpleName()
                .replaceAll("(?i)multiblock|wrapper|recipe|info|page|structure", " ")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        return Component.literal(fallback.isEmpty() ? "Multiblock Structure" : fallback);
    }

    private static Object unwrapStructureMetadata(Object recipe) throws ReflectiveOperationException {
        Object nested = tryInvokeNoArg(recipe, "getDefinition", "definition", "structure");
        if (nested == null) {
            nested = tryReadField(recipe, "definition");
        }
        if (nested == null) {
            nested = tryReadField(recipe, "structure");
        }
        return nested != null ? nested : recipe;
    }

    private static @Nullable Object tryInvokeNoArg(Object target, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            Method method = findNoArgMethod(target.getClass(), name);
            if (method != null) {
                method.setAccessible(true);
                return method.invoke(target);
            }
        }
        return null;
    }

    private static @Nullable Method findNoArgMethod(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            try {
                return owner.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignoredDeclared) {
                return null;
            }
        }
    }

    private static @Nullable ResourceLocation tryReadId(@Nullable Object target) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Object value = tryInvokeNoArg(target, "id", "getId", "getRegistryName");
        if (value instanceof ResourceLocation id) {
            return id;
        }
        value = tryReadField(target, "id");
        return value instanceof ResourceLocation id ? id : null;
    }

    private static @Nullable Component tryReadComponent(Object target, String... names) throws ReflectiveOperationException {
        return tryInvokeNoArg(target, names) instanceof Component component ? component : null;
    }

    private static @Nullable Object tryReadField(Object target, String name) throws IllegalAccessException {
        try {
            return getField(target.getClass(), name).get(target);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static Field getField(Class<?> owner, String name) throws NoSuchFieldException {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException ignored) {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }
}

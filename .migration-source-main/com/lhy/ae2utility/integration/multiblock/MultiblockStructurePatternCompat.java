package com.lhy.ae2utility.integration.multiblock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;
import com.lhy.ae2utility.Ae2UtilityMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/**
 * Adapts multiblock structure information pages without real outputs into processing-pattern drafts.
 */
public final class MultiblockStructurePatternCompat {
    private static final String GTLCORE_MOD_ID = "gtlcore";
    private static final String GTCEU_MULTIBLOCK_INFO_WRAPPER =
            "com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper";
    private static final String NEOECO_JEI_MULTIBLOCK_WRAPPER =
            "cn.dancingsnow.neoecoae.integration.xei.multiblock.MultiBlockInfoWrapper";
    private static final String NEOECO_EMI_MULTIBLOCK_RECIPE =
            "cn.dancingsnow.neoecoae.integration.emi.recipe.MultiblockEmiRecipe";
    private static final String LIGHTNING_MULTIBLOCK_RECIPE =
            "com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipe";
    private static final String LIGHTNING_EMI_MULTIBLOCK_RECIPE =
            "com.moakiee.ae2lt.integration.emi.EmiMultiblockStructureRecipe";
    private static final String NEOECO_CONTEXT_CLASS =
            "cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext";
    private static final String NEOECO_DUMMY_WORLD_CLASS =
            "com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld";
    private static final String GTL_CONFIG_CLASS = "org.gtlcore.gtlcore.config.ConfigHolder";
    private static final Set<Class<?>> UNSUPPORTED_RECIPE_CLASSES = ConcurrentHashMap.newKeySet();
    private static volatile @Nullable String[] cachedGtlHatchFilters;

    private MultiblockStructurePatternCompat() {
    }

    public static boolean isSupportedRecipe(@Nullable Object recipe, boolean hasStructureInputs, boolean hasActualOutputs) {
        if (recipe == null || UNSUPPORTED_RECIPE_CLASSES.contains(recipe.getClass())) {
            return false;
        }
        String className = recipe.getClass().getName();
        boolean knownStructureRecipe = isKnownStructureRecipe(className);
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
        return GTCEU_MULTIBLOCK_INFO_WRAPPER.equals(className)
                || NEOECO_JEI_MULTIBLOCK_WRAPPER.equals(className)
                || NEOECO_EMI_MULTIBLOCK_RECIPE.equals(className)
                || LIGHTNING_MULTIBLOCK_RECIPE.equals(className)
                || LIGHTNING_EMI_MULTIBLOCK_RECIPE.equals(className);
    }

    private static boolean canExtractStructureInputs(Object recipe) {
        String className = recipe.getClass().getName();
        return NEOECO_JEI_MULTIBLOCK_WRAPPER.equals(className)
                || NEOECO_EMI_MULTIBLOCK_RECIPE.equals(className);
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
            Object target = unwrapStructureMetadata(recipe);
            if (target == null) {
                return List.of();
            }
            if ("cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition"
                    .equals(target.getClass().getName())) {
                return extractNeoEcoInputs(target);
            }
            if (LIGHTNING_MULTIBLOCK_RECIPE.equals(target.getClass().getName())) {
                return extractLightningInputs(target);
            }
            return List.of();
        } catch (ReflectiveOperationException | LinkageError e) {
            UNSUPPORTED_RECIPE_CLASSES.add(recipe.getClass());
            Ae2UtilityMod.LOGGER.warn("Failed to read multiblock structure inputs from {}",
                    recipe.getClass().getName(), e);
            return List.of();
        }
    }

    private static List<GenericStack> extractNeoEcoInputs(Object definition) throws ReflectiveOperationException {
        Class<?> contextClass = Class.forName(NEOECO_CONTEXT_CLASS);
        Class<?> worldClass = Class.forName(NEOECO_DUMMY_WORLD_CLASS);
        Object world = worldClass.getConstructor().newInstance();
        Object context = contextClass.getMethod("dummyDelegated", int.class, worldClass)
                .invoke(null, 1, world);
        definition.getClass().getMethod("createLevel", contextClass).invoke(definition, context);
        Object requiredItems = context.getClass().getMethod("getRequiredItems").invoke(context);
        return requiredItems instanceof List<?> list ? genericStacksFromRequiredItems(list) : List.of();
    }

    private static List<GenericStack> genericStacksFromRequiredItems(List<?> requiredItems)
            throws ReflectiveOperationException {
        List<GenericStack> inputs = new ArrayList<>(requiredItems.size());
        for (Object requiredItem : requiredItems) {
            if (requiredItem == null) {
                continue;
            }
            Object value = tryInvokeNoArg(requiredItem, "stackWithCount", "stack");
            if (!(value instanceof ItemStack stack) || stack.isEmpty()) {
                continue;
            }
            Object countValue = tryInvokeNoArg(requiredItem, "count");
            int count = countValue instanceof Number number ? Math.max(1, number.intValue()) : stack.getCount();
            GenericStack generic = GenericStack.fromItemStack(stack.copyWithCount(count));
            if (generic != null) {
                inputs.add(generic);
            }
        }
        return List.copyOf(inputs);
    }

    private static List<GenericStack> extractLightningInputs(Object structure) throws ReflectiveOperationException {
        Object materials = tryInvokeNoArg(structure, "materials");
        if (!(materials instanceof List<?> list)) {
            return List.of();
        }
        List<GenericStack> inputs = new ArrayList<>(list.size());
        for (Object material : list) {
            if (material == null) {
                continue;
            }
            Object blockValue = tryInvokeNoArg(material, "block");
            if (!(blockValue instanceof net.minecraft.world.level.block.Block block)) {
                continue;
            }
            Object countValue = tryInvokeNoArg(material, "count");
            int count = countValue instanceof Number number ? Math.max(1, number.intValue()) : 1;
            GenericStack generic = GenericStack.fromItemStack(new ItemStack(block, count));
            if (generic != null) {
                inputs.add(generic);
            }
        }
        return List.copyOf(inputs);
    }

    public static @Nullable GenericStack createDraftOutput(Object recipe) {
        try {
            ItemStack draft = new ItemStack(Items.ENCHANTED_BOOK);
            draft.set(DataComponents.CUSTOM_NAME, resolveStructureName(recipe)
                    .copy().withStyle(Style.EMPTY.withColor(0xFC5AFC)));
            return GenericStack.fromItemStack(draft);
        } catch (ReflectiveOperationException | LinkageError e) {
            UNSUPPORTED_RECIPE_CLASSES.add(recipe.getClass());
            Ae2UtilityMod.LOGGER.warn("Failed to read multiblock structure metadata from {}",
                    recipe.getClass().getName(), e);
            return null;
        }
    }

    private static String[] getGtlHatchFilters() {
        String[] cached = cachedGtlHatchFilters;
        if (cached != null) {
            return cached;
        }
        if (!ModList.get().isLoaded(GTLCORE_MOD_ID)) {
            return cachedGtlHatchFilters = new String[0];
        }
        try {
            Class<?> config = Class.forName(GTL_CONFIG_CLASS);
            Object instance = getField(config, "INSTANCE").get(null);
            Object value = getField(config, "filterHatch").get(instance);
            if (value instanceof String[] filters) {
                cachedGtlHatchFilters = filters.clone();
            } else if (value instanceof List<?> list) {
                cachedGtlHatchFilters = list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toArray(String[]::new);
            } else {
                cachedGtlHatchFilters = new String[0];
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            cachedGtlHatchFilters = new String[0];
        }
        return cachedGtlHatchFilters;
    }

    static Component resolveStructureName(Object recipe) throws ReflectiveOperationException {
        Object target = unwrapStructureMetadata(recipe);
        Component component = tryReadComponent(recipe,
                "title", "getTitle", "getDisplayName", "getName", "name");
        if (component == null && target != recipe) {
            component = tryReadComponent(target,
                    "title", "getTitle", "getDisplayName", "getName", "name");
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

    private static @Nullable Object tryInvokeNoArg(Object target, String... names)
            throws ReflectiveOperationException {
        for (String name : names) {
            try {
                var method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                try {
                    var method = target.getClass().getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException ignoredDeclared) {
                }
            }
        }
        return null;
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

    private static @Nullable Component tryReadComponent(Object target, String... names)
            throws ReflectiveOperationException {
        Object value = tryInvokeNoArg(target, names);
        return value instanceof Component component ? component : null;
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
package com.lhy.ae2utility.recipe_finder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

public final class RecipeFinderFeatureClassifier {
    private static final List<String> WOOD_SPECIES = List.of(
            "oak",
            "spruce",
            "birch",
            "jungle",
            "acacia",
            "dark_oak",
            "mangrove",
            "cherry",
            "bamboo",
            "crimson",
            "warped",
            "rubber");

    private RecipeFinderFeatureClassifier() {
    }

    public static Set<String> classifyIngredient(Object ingredient) {
        if (ingredient instanceof ItemStack stack) {
            return classifyItemStack(stack);
        }
        if (ingredient instanceof FluidStack stack) {
            return classifyFluidStack(stack);
        }
        return Set.of("special");
    }

    public static Set<String> classifyItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Set.of();
        }

        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        Set<String> features = new LinkedHashSet<>();

        if (isWoodLike(path)) {
            features.add("wood");
        }
        if (containsAny(path, "log", "stem", "hyphae")) {
            features.add("log");
        }
        if (containsAny(path, "plank", "planks", "board", "boards")) {
            features.add("plank");
        }
        if (containsAny(path, "dust", "sawdust")) {
            features.add("dust");
        }
        if (containsAny(path, "ingot", "alloy")) {
            features.add("ingot");
        }
        if (containsAny(path, "nugget")) {
            features.add("nugget");
        }
        if (containsAny(path, "gear")) {
            features.add("gear");
        }
        if (containsAny(path, "rod", "stick")) {
            features.add("rod");
        }
        if (containsAny(path, "plate", "sheet")) {
            features.add("plate");
        }
        if (containsAny(path, "ore", "raw_")) {
            features.add("ore");
        }
        if (containsAny(path, "gem", "crystal", "shard")) {
            features.add("gem");
        }
        if (containsAny(path, "glass", "pane")) {
            features.add("glass");
        }
        if (containsAny(path, "circuit", "processor", "component", "part", "chip", "core", "card")) {
            features.add("component");
        }
        if (containsAny(path, "machine", "factory", "provider", "assembler", "furnace", "crusher", "enricher")) {
            features.add("machine");
        }
        if (containsAny(path, "block", "brick", "cobble", "stone", "deepslate")) {
            features.add("stone");
        }

        if (features.isEmpty()) {
            features.add("other");
        }

        return Set.copyOf(features);
    }

    public static Set<String> classifyFluidStack(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Set.of();
        }

        String path = BuiltInRegistries.FLUID.getKey(stack.getFluid()).getPath().toLowerCase(Locale.ROOT);
        Set<String> features = new LinkedHashSet<>();
        features.add("fluid");

        if (containsAny(path, "water", "steam")) {
            features.add("water");
        }
        if (containsAny(path, "lava")) {
            features.add("lava");
        }
        if (containsAny(path, "acid")) {
            features.add("acid");
        }
        if (containsAny(path, "slurry", "brine", "oil")) {
            features.add("chemical");
        }

        return Set.copyOf(features);
    }

    public static String primaryFeature(Set<String> features) {
        if (features.contains("wood")) {
            return "wood";
        }
        if (features.contains("plank")) {
            return "plank";
        }
        if (features.contains("dust")) {
            return "dust";
        }
        if (features.contains("ingot")) {
            return "ingot";
        }
        if (features.contains("gear")) {
            return "gear";
        }
        if (features.contains("component")) {
            return "component";
        }
        if (features.contains("fluid")) {
            return "fluid";
        }
        return features.stream().findFirst().orElse("other");
    }

    public static String featureLabel(String key) {
        return switch (key) {
            case "wood" -> "Wood";
            case "log" -> "Log";
            case "plank" -> "Plank";
            case "dust" -> "Dust";
            case "ingot" -> "Ingot";
            case "nugget" -> "Nugget";
            case "gear" -> "Gear";
            case "rod" -> "Rod";
            case "plate" -> "Plate";
            case "ore" -> "Ore";
            case "gem" -> "Gem";
            case "glass" -> "Glass";
            case "component" -> "Component";
            case "machine" -> "Machine";
            case "stone" -> "Stone";
            case "fluid" -> "Fluid";
            case "water" -> "Water";
            case "lava" -> "Lava";
            case "acid" -> "Acid";
            case "chemical" -> "Chemical";
            case "special" -> "Special";
            default -> "Other";
        };
    }

    public static String modDisplayName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(modId);
    }

    private static boolean isWoodLike(String path) {
        if (containsAny(path, "wood", "log", "plank", "planks", "stem", "hyphae", "sawdust")) {
            return true;
        }
        for (String species : WOOD_SPECIES) {
            if (path.contains(species)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}

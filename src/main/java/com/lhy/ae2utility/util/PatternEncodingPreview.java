package com.lhy.ae2utility.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

import com.lhy.ae2utility.network.EncodePatternPacket;

/**
 * Read-only snapshot of the AE2 pattern-encoding terminal for one recipe.
 */
public final class PatternEncodingPreview {
    public static final int VISIBLE_INPUTS = 9;
    public static final int VISIBLE_OUTPUTS = 3;
    public static final int CRAFTING_SLOTS = 9;
    public static final int SMITHING_SLOTS = 3;
    public static final int STONECUTTING_SLOTS = 1;

    private final PatternEncodingPreviewMode mode;
    private final List<GenericStack> inputs;
    private final List<GenericStack> outputs;
    private final boolean substituteItems;
    private final boolean substituteFluids;
    private final int extraInputs;
    private final int extraOutputs;

    public PatternEncodingPreview(PatternEncodingPreviewMode mode, List<GenericStack> inputs,
            List<GenericStack> outputs, boolean substituteItems, boolean substituteFluids,
            int extraInputs, int extraOutputs) {
        this.mode = mode;
        this.inputs = copyNullable(inputs);
        this.outputs = copyNullable(outputs);
        this.substituteItems = substituteItems;
        this.substituteFluids = substituteFluids;
        this.extraInputs = Math.max(0, extraInputs);
        this.extraOutputs = Math.max(0, extraOutputs);
    }

    public PatternEncodingPreviewMode mode() {
        return mode;
    }

    public List<GenericStack> inputs() {
        return inputs;
    }

    public List<GenericStack> outputs() {
        return outputs;
    }

    public boolean substituteItems() {
        return substituteItems;
    }

    public boolean substituteFluids() {
        return substituteFluids;
    }

    public int extraInputs() {
        return extraInputs;
    }

    public int extraOutputs() {
        return extraOutputs;
    }

    public static int extraCount(int total, int visible) {
        return Math.max(0, total - visible);
    }

    public static int presentCount(@Nullable List<GenericStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (GenericStack stack : stacks) {
            if (stack != null && stack.what() != null) {
                count++;
            }
        }
        return count;
    }

    public static PatternEncodingPreviewMode resolveMode(@Nullable ResourceLocation recipeId, boolean craftingHint,
            int meaningfulInputs, @Nullable Level level) {
        if (recipeId != null && level != null) {
            RecipeHolder<?> holder = level.getRecipeManager().byKey(recipeId).orElse(null);
            if (holder != null) {
                if (holder.value() instanceof CraftingRecipe crafting
                        && meaningfulInputs <= CRAFTING_SLOTS
                        && crafting.canCraftInDimensions(3, 3)) {
                    return PatternEncodingPreviewMode.CRAFTING;
                }
                if (holder.value() instanceof SmithingRecipe) {
                    return PatternEncodingPreviewMode.SMITHING_TABLE;
                }
                if (holder.value() instanceof StonecutterRecipe) {
                    return PatternEncodingPreviewMode.STONECUTTING;
                }
                return PatternEncodingPreviewMode.PROCESSING;
            }
        }
        if (craftingHint && meaningfulInputs <= CRAFTING_SLOTS) {
            return PatternEncodingPreviewMode.CRAFTING;
        }
        return PatternEncodingPreviewMode.PROCESSING;
    }

    public static PatternEncodingPreview fromSlots(PatternEncodingPreviewMode mode, List<GenericStack> inputs,
            List<GenericStack> outputs, boolean substituteItems, boolean substituteFluids) {
        return switch (mode) {
            case CRAFTING -> new PatternEncodingPreview(mode, pad(inputs, CRAFTING_SLOTS),
                    pad(outputs, 1), substituteItems, substituteFluids, 0, 0);
            case SMITHING_TABLE -> new PatternEncodingPreview(mode, pad(inputs, SMITHING_SLOTS),
                    pad(outputs, 1), substituteItems, false, 0, 0);
            case STONECUTTING -> new PatternEncodingPreview(mode, pad(inputs, STONECUTTING_SLOTS),
                    pad(outputs, 1), substituteItems, false, 0, 0);
            case PROCESSING -> {
                List<GenericStack> sparseIn = trimTrailing(inputs);
                List<GenericStack> sparseOut = trimTrailing(outputs);
                yield new PatternEncodingPreview(mode, pad(sparseIn, VISIBLE_INPUTS),
                        pad(sparseOut, VISIBLE_OUTPUTS), false, false,
                        extraCount(sparseIn.size(), VISIBLE_INPUTS),
                        extraCount(sparseOut.size(), VISIBLE_OUTPUTS));
            }
        };
    }

    public static PatternEncodingPreview guess(EncodePatternPacket packet, @Nullable Level level,
            @Nullable Predicate<AEKey> craftable, @Nullable Iterable<ItemStack> playerInventory) {
        List<GenericStack> packetOutputs = packet.outputs() == null ? List.of() : packet.outputs();
        Set<AEKey> outputKeys = packetOutputs.stream()
                .filter(stack -> stack != null && stack.what() != null)
                .map(GenericStack::what)
                .collect(Collectors.toUnmodifiableSet());
        List<GenericStack> chosen = chooseInputs(packet.inputs(), craftable, packet.preserveInputOrder(), outputKeys);
        int meaningful = presentCount(chosen);
        PatternEncodingPreviewMode mode = resolveMode(packet.recipeId(), packet.craftingCategoryHint(), meaningful, level);
        if (mode == PatternEncodingPreviewMode.CRAFTING && packet.recipeId() != null && level != null) {
            RecipeHolder<?> holder = level.getRecipeManager().byKey(packet.recipeId()).orElse(null);
            if (holder != null && holder.value() instanceof CraftingRecipe crafting
                    && crafting.canCraftInDimensions(3, 3)) {
                ItemStack[] grid = EncodePatternInputChooser.buildCraftingGridFromRecipe(
                        crafting, null, craftable, outputKeys, playerInventory);
                List<GenericStack> gridInputs = new ArrayList<>(CRAFTING_SLOTS);
                for (int i = 0; i < CRAFTING_SLOTS; i++) {
                    ItemStack stack = i < grid.length ? grid[i] : ItemStack.EMPTY;
                    gridInputs.add(stack == null || stack.isEmpty() ? null : GenericStack.fromItemStack(stack));
                }
                return fromSlots(mode, gridInputs, packetOutputs, packet.substitute(), packet.substituteFluids());
            }
        }
        return fromSlots(mode, chosen, packetOutputs, packet.substitute(), packet.substituteFluids());
    }

    public static @Nullable PatternEncodingPreview fromEncodedPattern(ItemStack encoded, Level level,
            boolean substituteItems, boolean substituteFluids) {
        if (encoded == null || encoded.isEmpty() || level == null) {
            return null;
        }
        IPatternDetails details;
        try {
            details = PatternDetailsHelper.decodePattern(encoded, level);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (details instanceof AECraftingPattern crafting) {
            return fromSlots(PatternEncodingPreviewMode.CRAFTING, crafting.getSparseInputs(),
                    crafting.getSparseOutputs(), crafting.canSubstitute(), crafting.canSubstituteFluids);
        }
        if (details instanceof AEProcessingPattern processing) {
            return fromSlots(PatternEncodingPreviewMode.PROCESSING, processing.getSparseInputs(),
                    processing.getSparseOutputs(), false, false);
        }
        if (details instanceof AESmithingTablePattern smithing) {
            return fromSlots(PatternEncodingPreviewMode.SMITHING_TABLE, List.of(
                    stackOf(smithing.getTemplate()),
                    stackOf(smithing.getBase()),
                    stackOf(smithing.getAddition())),
                    smithing.getOutputs(), smithing.canSubstitute, false);
        }
        if (details instanceof AEStonecuttingPattern stonecutting) {
            return fromSlots(PatternEncodingPreviewMode.STONECUTTING,
                    List.of(stackOf(stonecutting.getInput())),
                    stonecutting.getOutputs(), stonecutting.canSubstitute, false);
        }
        if (details == null) {
            return null;
        }
        List<GenericStack> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            inputs.add(possible == null || possible.length == 0 ? null : possible[0]);
        }
        return fromSlots(PatternEncodingPreviewMode.PROCESSING, inputs, details.getOutputs(),
                substituteItems, substituteFluids);
    }

    private static List<GenericStack> chooseInputs(@Nullable List<List<GenericStack>> slots,
            @Nullable Predicate<AEKey> craftable, boolean preserveInputOrder, Set<AEKey> outputKeys) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        List<GenericStack> chosen = new ArrayList<>(slots.size());
        Predicate<AEKey> excluded = outputKeys == null ? key -> false : outputKeys::contains;
        for (List<GenericStack> alts : slots) {
            if (alts == null || alts.isEmpty()) {
                chosen.add(null);
            } else {
                chosen.add(EncodePatternInputChooser.pickEncodedInput(alts, null, craftable,
                        preserveInputOrder, excluded));
            }
        }
        return chosen;
    }

    private static List<GenericStack> trimTrailing(@Nullable List<GenericStack> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        int last = source.size() - 1;
        while (last >= 0) {
            GenericStack stack = source.get(last);
            if (stack != null && stack.what() != null) {
                break;
            }
            last--;
        }
        if (last < 0) {
            return List.of();
        }
        return new ArrayList<>(source.subList(0, last + 1));
    }

    private static List<GenericStack> copyNullable(@Nullable List<GenericStack> source) {
        return source == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static List<GenericStack> pad(@Nullable List<GenericStack> source, int size) {
        List<GenericStack> padded = new ArrayList<>(size);
        if (source != null) {
            int limit = Math.min(size, source.size());
            for (int i = 0; i < limit; i++) {
                padded.add(source.get(i));
            }
        }
        while (padded.size() < size) {
            padded.add(null);
        }
        return padded;
    }

    private static @Nullable GenericStack stackOf(@Nullable AEItemKey key) {
        return key == null ? null : new GenericStack(key, 1);
    }
}

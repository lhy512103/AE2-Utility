package com.lhy.ae2utility.util;

import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

import com.lhy.ae2utility.network.EncodePatternPacket;

/**
 * 一条配方在 AE2 样板编码终端中的只读快照。
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

    public static int presentCount(@Nullable List<GenericStack> stacks) {
        if (stacks == null) {
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
            Recipe<?> recipe = level.getRecipeManager().byKey(recipeId).orElse(null);
            if (recipe != null) {
                if (recipe instanceof CraftingRecipe crafting && meaningfulInputs <= CRAFTING_SLOTS
                        && crafting.canCraftInDimensions(3, 3)) {
                    return PatternEncodingPreviewMode.CRAFTING;
                }
                if (recipe instanceof SmithingRecipe) {
                    return PatternEncodingPreviewMode.SMITHING_TABLE;
                }
                if (recipe instanceof StonecutterRecipe) {
                    return PatternEncodingPreviewMode.STONECUTTING;
                }
                return PatternEncodingPreviewMode.PROCESSING;
            }
        }
        return craftingHint && meaningfulInputs <= CRAFTING_SLOTS
                ? PatternEncodingPreviewMode.CRAFTING
                : PatternEncodingPreviewMode.PROCESSING;
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
                        Math.max(0, sparseIn.size() - VISIBLE_INPUTS),
                        Math.max(0, sparseOut.size() - VISIBLE_OUTPUTS));
            }
        };
    }

    /** 服务端回包前的本地占位：用客户端已知信息猜测会写入的材料。 */
    public static PatternEncodingPreview guess(EncodePatternPacket packet, @Nullable Level level,
            @Nullable Predicate<AEKey> craftable) {
        List<GenericStack> packetOutputs = packet.outputs();
        Set<AEKey> outputKeys = packetOutputs.stream()
                .filter(stack -> stack != null && stack.what() != null)
                .map(GenericStack::what)
                .collect(Collectors.toUnmodifiableSet());
        List<GenericStack> chosen = new ArrayList<>(packet.inputs().size());
        for (List<GenericStack> alts : packet.inputs()) {
            chosen.add(alts == null || alts.isEmpty() ? null
                    : EncodePatternInputChooser.pickEncodedInput(alts, null, craftable, packet.preserveInputOrder(),
                            outputKeys));
        }
        PatternEncodingPreviewMode mode = resolveMode(packet.recipeId(), packet.craftingCategoryHint(),
                presentCount(chosen), level);
        return fromSlots(mode, chosen, packetOutputs, packet.substitute(), packet.substituteFluids());
    }

    public static @Nullable PatternEncodingPreview fromEncodedPattern(ItemStack encoded, @Nullable Level level) {
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
            return fromSlots(PatternEncodingPreviewMode.CRAFTING, Arrays.asList(crafting.getSparseInputs()),
                    Arrays.asList(crafting.getSparseOutputs()), crafting.canSubstitute, crafting.canSubstituteFluids);
        }
        if (details instanceof AEProcessingPattern processing) {
            return fromSlots(PatternEncodingPreviewMode.PROCESSING, Arrays.asList(processing.getSparseInputs()),
                    Arrays.asList(processing.getSparseOutputs()), false, false);
        }
        if (details instanceof AESmithingTablePattern smithing) {
            return fromSlots(PatternEncodingPreviewMode.SMITHING_TABLE,
                    Arrays.asList(stackOf(smithing.getTemplate()), stackOf(smithing.getBase()),
                            stackOf(smithing.getAddition())),
                    Arrays.asList(smithing.getOutputs()), smithing.canSubstitute, false);
        }
        if (details instanceof AEStonecuttingPattern stonecutting) {
            return fromSlots(PatternEncodingPreviewMode.STONECUTTING,
                    Collections.singletonList(stackOf(stonecutting.getInput())),
                    Arrays.asList(stonecutting.getOutputs()), stonecutting.canSubstitute, false);
        }
        if (details == null) {
            return null;
        }
        List<GenericStack> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : details.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            inputs.add(possible == null || possible.length == 0 ? null : possible[0]);
        }
        return fromSlots(PatternEncodingPreviewMode.PROCESSING, inputs, Arrays.asList(details.getOutputs()),
                false, false);
    }

    private static List<GenericStack> trimTrailing(@Nullable List<GenericStack> source) {
        if (source == null) {
            return List.of();
        }
        int last = source.size() - 1;
        while (last >= 0 && (source.get(last) == null || source.get(last).what() == null)) {
            last--;
        }
        return last < 0 ? List.of() : new ArrayList<>(source.subList(0, last + 1));
    }

    private static List<GenericStack> copyNullable(@Nullable List<GenericStack> source) {
        return source == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static List<GenericStack> pad(@Nullable List<GenericStack> source, int size) {
        List<GenericStack> padded = new ArrayList<>(size);
        if (source != null) {
            for (int i = 0; i < Math.min(size, source.size()); i++) {
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

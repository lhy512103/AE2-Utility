package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import com.lhy.ae2utility.compat.SomeUselessThingsCompat;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.NetworkValidation;
import com.lhy.ae2utility.util.EncodePatternInputChooser;

/**
 * 纯计算：校验编码请求、为每个输入槽选材，并生成编码样板。不扣除空白样板，不上传，不发送消息。
 * 真正编码和悬停预览共用这一套逻辑，保证两者结果一致。
 */
final class PatternEncodingComputer {
    record Computation(ItemStack encodedPattern, boolean canUploadToMatrix, List<GenericStack> encodedInputs) {
        static final Computation EMPTY = new Computation(ItemStack.EMPTY, false, List.of());
    }

    private record CraftingFallbackMatch(CraftingRecipe recipe, ItemStack[] inputs) {}

    private PatternEncodingComputer() {}

    static @Nullable Predicate<AEKey> craftablePredicate(@Nullable IGrid grid) {
        return grid != null ? key -> grid.getCraftingService().isCraftable(key) : null;
    }

    static boolean isValidPayload(List<List<GenericStack>> inputs, List<GenericStack> outputs) {
        if (inputs == null || outputs == null || inputs.size() > NetworkValidation.MAX_RECIPE_INPUT_SLOTS
                || outputs.isEmpty() || outputs.size() > 27 || outputs.get(0) == null) {
            return false;
        }
        long totalStacks = outputs.size();
        boolean hasInput = false;
        for (List<GenericStack> alternatives : inputs) {
            if (alternatives == null) continue;
            if (alternatives.size() > NetworkValidation.MAX_STACKS_PER_SLOT) return false;
            totalStacks += alternatives.size();
            if (totalStacks > NetworkValidation.MAX_TOTAL_PATTERN_STACKS) return false;
            for (GenericStack stack : alternatives) {
                if (stack == null) continue;
                if (stack.what() == null || stack.amount() <= 0) return false;
                hasInput = true;
            }
        }
        if (!hasInput) return false;
        for (GenericStack stack : outputs) {
            if (stack != null && (stack.what() == null || stack.amount() <= 0)) return false;
        }
        return outputs.get(0).what() != null && outputs.get(0).amount() > 0;
    }

    /**
     * 为每个输入槽从候选中选一个；空槽保留为 null 以维持槽位顺序。
     *
     * @return 任一非空槽无法选材时返回 null
     */
    static @Nullable List<GenericStack> chooseInputs(EncodePatternPacket payload, @Nullable MEStorage inventory,
            @Nullable Predicate<AEKey> craftable) {
        Set<AEKey> outputKeys = outputKeys(payload.outputs());
        List<GenericStack> chosen = new ArrayList<>(payload.inputs().size());
        for (List<GenericStack> alts : payload.inputs()) {
            if (alts == null || alts.isEmpty()) {
                chosen.add(null);
                continue;
            }
            GenericStack pick = EncodePatternInputChooser.pickEncodedInput(alts, inventory, craftable,
                    payload.preserveInputOrder(), outputKeys::contains);
            if (pick == null) {
                return null;
            }
            chosen.add(pick);
        }
        return chosen;
    }

    static Computation compute(ServerPlayer player, EncodePatternPacket payload, List<GenericStack> in,
            List<GenericStack> out, @Nullable MEStorage inventory, @Nullable Predicate<AEKey> craftable) {
        if (payload.hasOmniversalIdentity()) {
            ItemStack omniversal = SomeUselessThingsCompat.encodePattern(ItemStack.EMPTY, payload, player.level());
            if (!omniversal.isEmpty()) {
                return new Computation(omniversal, false, in);
            }
        }

        int meaningfulInputs = countMeaningfulInputs(in);
        // 一旦识别为合成/锻造/切石这类结构化配方，就绝不能静默回退成处理样板，
        // 否则会出现「合成样板被写成处理样板」（原版 AE 编码终端不会这样）。
        boolean recognizedStructured = false;
        Computation result = Computation.EMPTY;

        Recipe<?> recipe = payload.recipeId() == null ? null
                : player.getServer().getRecipeManager().byKey(payload.recipeId()).orElse(null);
        if (recipe instanceof CraftingRecipe crafting && meaningfulInputs <= 9 && crafting.canCraftInDimensions(3, 3)) {
            recognizedStructured = true;
            // 对齐原版 AE2 EncodingHelper#encodeCraftingRecipe：按配方自身的 3x3 原料，从网络匹配项里选材。
            ItemStack[] grid = EncodePatternInputChooser.buildCraftingGridFromRecipe(
                    crafting, inventory, craftable, outputKeys(out), player.getInventory().items);
            result = encodeCrafting(crafting, grid, in, out, payload);
        } else if (recipe instanceof SmithingRecipe smithing) {
            recognizedStructured = true;
            ItemStack encoded = encodeSmithing(smithing, payload.substitute(), in, out);
            result = new Computation(encoded, !encoded.isEmpty(), in);
        } else if (recipe instanceof StonecutterRecipe stonecutter) {
            recognizedStructured = true;
            AEItemKey inKey = firstItemKey(in);
            AEItemKey outKey = firstItemKey(out);
            if (inKey != null && outKey != null) {
                result = new Computation(PatternDetailsHelper.encodeStonecuttingPattern(stonecutter, inKey, outKey,
                        payload.substitute()), true, in);
            }
        }

        if (result.encodedPattern().isEmpty() && payload.craftingCategoryHint() && meaningfulInputs <= 9) {
            CraftingFallbackMatch fallback = findCraftingRecipeFromInputs(player, in);
            if (fallback != null) {
                // 仅在真正反查到 3x3 合成配方时才视为结构化合成；否则按处理样板编码，
                // 避免 Create 动力合成器这类「JEI 看似合成、实则放不进 3x3」的配方点击后无任何样板产出。
                recognizedStructured = true;
                result = encodeCrafting(fallback.recipe(), fallback.inputs(), in, out, payload);
            }
        }

        // 结构化配方编码失败时保持为空（上层按失败处理），绝不把它悄悄写成处理样板。
        if (result.encodedPattern().isEmpty() && !recognizedStructured) {
            result = encodeProcessing(player, payload, in, out);
        }
        return result.encodedPattern().isEmpty() ? Computation.EMPTY : result;
    }

    private static Computation encodeCrafting(CraftingRecipe recipe, ItemStack[] grid, List<GenericStack> in,
            List<GenericStack> out, EncodePatternPacket payload) {
        ItemStack outStack = out.isEmpty() || out.get(0) == null ? null : EncodePatternInputChooser.toItemStack(out.get(0));
        ItemStack encoded = PatternDetailsHelper.encodeCraftingPattern(recipe, grid,
                outStack == null ? ItemStack.EMPTY : outStack, payload.substitute(), payload.substituteFluids());
        return new Computation(encoded, true, EncodePatternInputChooser.alignCraftingGridToJeiSlots(in, grid));
    }

    private static Computation encodeProcessing(ServerPlayer player, EncodePatternPacket payload,
            List<GenericStack> in, List<GenericStack> out) {
        GenericStack[] procIn = in.stream().filter(Objects::nonNull).toArray(GenericStack[]::new);
        GenericStack[] procOut = out.stream().filter(Objects::nonNull).toArray(GenericStack[]::new);
        if (procIn.length == 0 || procOut.length == 0) {
            return Computation.EMPTY;
        }
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(procIn, procOut);
        ItemStack omniversal = SomeUselessThingsCompat.encodePattern(encoded, payload, player.level());
        return new Computation(omniversal.isEmpty() ? encoded : omniversal, false, in);
    }

    private static ItemStack encodeSmithing(SmithingRecipe recipe, boolean substitute, List<GenericStack> in,
            List<GenericStack> out) {
        AEItemKey outKey = firstItemKey(out);
        if (outKey == null) {
            return ItemStack.EMPTY;
        }
        ItemStack positional = encodeSmithingIfComplete(recipe, substitute,
                itemKeyAt(in, 0), itemKeyAt(in, 1), itemKeyAt(in, 2), outKey);
        if (!positional.isEmpty()) {
            return positional;
        }
        // JEI 锻造台分页有时只有 2 个槽（无模板格），或不严格按 [模板, 基底, 添加剂] 排列；按有效物品输入的顺序再试一次。
        List<AEItemKey> keys = new ArrayList<>(4);
        for (GenericStack g : in) {
            if (g != null && g.what() instanceof AEItemKey k) {
                keys.add(k);
            }
        }
        if (keys.size() >= 3) {
            return encodeSmithingIfComplete(recipe, substitute, keys.get(0), keys.get(1), keys.get(2), outKey);
        }
        if (keys.size() == 2) {
            return encodeSmithingIfComplete(recipe, substitute, null, keys.get(0), keys.get(1), outKey);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack encodeSmithingIfComplete(SmithingRecipe recipe, boolean substitute,
            @Nullable AEItemKey template, @Nullable AEItemKey base, @Nullable AEItemKey addition, AEItemKey outKey) {
        if (base == null || addition == null) {
            return ItemStack.EMPTY;
        }
        try {
            return PatternDetailsHelper.encodeSmithingTablePattern(recipe, template, base, addition, outKey, substitute);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * JEI 合成类别里可能出现客户端合成出来的 recipeId（例如 Occultism 已绑定束缚之书的变体），
     * 服务端 RecipeManager.byKey 查不到。此时用 JEI 发来的实际物品反查真实合成配方，并把这些实际输入（含 NBT）写入样板。
     */
    private static @Nullable CraftingFallbackMatch findCraftingRecipeFromInputs(ServerPlayer player,
            List<GenericStack> in) {
        ItemStack[] sparse = sparseCraftingGrid(in);
        CraftingFallbackMatch match = matchCraftingGrid(player, sparse);
        if (match != null) {
            return match;
        }
        ItemStack[] compact = compactCraftingGrid(in);
        return Arrays.equals(sparse, compact) ? null : matchCraftingGrid(player, compact);
    }

    private static @Nullable CraftingFallbackMatch matchCraftingGrid(ServerPlayer player, ItemStack[] grid) {
        TransientCraftingContainer container = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
        for (int i = 0; i < 9; i++) {
            container.setItem(i, grid[i]);
        }
        return player.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, container, player.level())
                .map(recipe -> new CraftingFallbackMatch(recipe, grid))
                .orElse(null);
    }

    private static ItemStack[] sparseCraftingGrid(List<GenericStack> in) {
        ItemStack[] grid = emptyGrid();
        for (int i = 0; i < Math.min(9, in.size()); i++) {
            grid[i] = EncodePatternInputChooser.toItemStack(in.get(i));
        }
        return grid;
    }

    private static ItemStack[] compactCraftingGrid(List<GenericStack> in) {
        ItemStack[] grid = emptyGrid();
        int next = 0;
        for (GenericStack stack : in) {
            if (next >= 9) {
                break;
            }
            ItemStack itemStack = EncodePatternInputChooser.toItemStack(stack);
            if (!itemStack.isEmpty()) {
                grid[next++] = itemStack;
            }
        }
        return grid;
    }

    private static ItemStack[] emptyGrid() {
        ItemStack[] grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        return grid;
    }

    private static Set<AEKey> outputKeys(List<GenericStack> out) {
        return out.stream()
                .filter(Objects::nonNull)
                .map(GenericStack::what)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static @Nullable AEItemKey firstItemKey(List<GenericStack> stacks) {
        return itemKeyAt(stacks, 0);
    }

    private static @Nullable AEItemKey itemKeyAt(List<GenericStack> stacks, int index) {
        if (index < 0 || index >= stacks.size()) {
            return null;
        }
        GenericStack g = stacks.get(index);
        return g != null && g.what() instanceof AEItemKey key ? key : null;
    }

    private static int countMeaningfulInputs(List<GenericStack> inputs) {
        int count = 0;
        for (GenericStack input : inputs) {
            if (input != null && input.what() != null && input.amount() > 0) {
                count++;
            }
        }
        return count;
    }
}

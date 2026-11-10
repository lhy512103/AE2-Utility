package com.lhy.ae2utility.client;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;

import com.lhy.ae2utility.compat.SomeUselessThingsCompat;
import com.lhy.ae2utility.integration.eaep.EaepReflection;
import com.lhy.ae2utility.network.EncodePatternPacket;

/**
 * JEI 和 EMI 编码数据包工厂共用的部分：草稿校验、样板命名、EAEP 搜索关键字和数据包组装。
 * 各查看器只负责把自己的配方视图转换成输入槽和输出列表。
 */
public final class EncodePatternDrafts {
    private EncodePatternDrafts() {}

    /** 编码请求中与查看器无关的部分；查看器转换完成后交给 {@link #toPacket}。 */
    public record Draft(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId,
            String providerSearchKey, boolean upload, boolean craftingCategoryHint,
            boolean fullCategoryBatch, int bulkEncodeSessionId, @Nullable Object identityRecipe) {}

    public static Set<AEKey> outputKeys(List<GenericStack> outputs) {
        return outputs.stream()
                .filter(Objects::nonNull)
                .map(GenericStack::what)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean hasMeaningfulInputs(List<List<GenericStack>> inputs) {
        return inputs.stream().anyMatch(slot -> slot != null && !slot.isEmpty());
    }

    public static long meaningfulInputCount(List<List<GenericStack>> inputs) {
        return inputs.stream().filter(slot -> slot != null && !slot.isEmpty()).count();
    }

    /**
     * 至少有一个输入和一个输出，且没有无法转换的槽（null）。
     */
    public static boolean isEncodable(List<List<GenericStack>> inputs, List<GenericStack> outputs) {
        return hasMeaningfulInputs(inputs)
                && outputs.stream().anyMatch(Objects::nonNull)
                && inputs.stream().noneMatch(Objects::isNull)
                && outputs.stream().noneMatch(Objects::isNull);
    }

    /**
     * 「工作台合成」提示与原版 AE2 保持同一边界：只有能放进 3x3 的配方才让服务端尝试合成回退。
     * Create 动力合成器这类超过 3x3 的页面即使长得像合成网格，也应走处理样板。
     */
    public static boolean fitsCraftingGrid(List<List<GenericStack>> inputs) {
        return meaningfulInputCount(inputs) <= 9;
    }

    public static String patternName(@Nullable ResourceLocation recipeId, List<GenericStack> outputs) {
        for (GenericStack output : outputs) {
            if (output == null || output.what() == null) {
                continue;
            }
            Component name = output.what().getDisplayName();
            if (name != null) {
                String text = name.getString();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return recipeId != null ? recipeId.toString() : "-";
    }

    public static boolean eaepUploadAvailable(boolean upload) {
        return upload && ModList.get().isLoaded("extendedae_plus");
    }

    /**
     * 根据配方对象推导 EAEP 供应器搜索关键字。只计算写入数据包的字符串，不修改 EAEP 全局状态
     * （批量时会互相覆盖导致供应器界面过滤错误；服务端会再同步给客户端）。
     *
     * @return 推导不出时返回空字符串
     */
    public static String eaepSearchKey(@Nullable Object recipe) {
        String key;
        if (recipe instanceof CraftingRecipe) {
            key = EaepReflection.defaultCraftingSearchKey();
        } else if (recipe instanceof Recipe<?> mcRecipe) {
            key = EaepReflection.mapRecipeTypeToSearchKey(mcRecipe);
        } else if (recipe != null) {
            key = EaepReflection.deriveSearchKeyFromUnknownRecipe(recipe);
        } else {
            key = null;
        }
        return key == null ? "" : key;
    }

    public static EncodePatternPacket toPacket(Draft draft) {
        var substitution = PatternSubstitutionState.snapshot();
        EncodePatternPacket packet = new EncodePatternPacket(
                draft.inputs(),
                draft.outputs(),
                draft.recipeId(),
                patternName(draft.recipeId(), draft.outputs()),
                draft.providerSearchKey(),
                draft.providerSearchKey(),
                draft.upload(),
                substitution.items(),
                substitution.fluids(),
                false,
                false,
                draft.fullCategoryBatch(),
                draft.bulkEncodeSessionId(),
                draft.craftingCategoryHint());
        return SomeUselessThingsCompat.attachIdentity(packet, draft.identityRecipe());
    }

    public static Optional<EncodePatternPacket> packetIfEncodable(Draft draft) {
        return isEncodable(draft.inputs(), draft.outputs()) ? Optional.of(toPacket(draft)) : Optional.empty();
    }
}

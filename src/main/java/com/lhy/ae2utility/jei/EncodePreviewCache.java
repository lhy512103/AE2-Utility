package com.lhy.ae2utility.jei;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.EncodePreviewResultPacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.QueryEncodePreviewPacket;
import com.lhy.ae2utility.util.GenericIngredientUtil;
import com.lhy.ae2utility.util.PatternEncodingPreview;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;

/**
 * 悬停 JEI 编码箭头时的预览缓存：每个配方布局向服务端请求一次真实编码结果（5 秒或书签/替换设置变化后失效），
 * 用于固定输入槽显示与 tooltip 中的编码终端预览。
 */
public final class EncodePreviewCache {
    private static final long TTL_MS = 5000L;
    private static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger(1);
    private static final Map<IRecipeLayoutDrawable<?>, Entry> ENTRIES = new WeakHashMap<>();

    private static Map<IRecipeSlotView, ITypedIngredient<?>> pinned = Map.of();
    private static long pinnedTick = Long.MIN_VALUE;

    private EncodePreviewCache() {
    }

    private static final class Entry {
        int requestId;
        long signature;
        long createdAt;
        boolean inFlight;
        boolean failed;
        Map<IRecipeSlotView, ITypedIngredient<?>> pins = Map.of();
        @Nullable PatternEncodingPreview serverPanel;
        @Nullable PatternEncodingPreview localPanel;
        List<IRecipeSlotView> inputSlots = List.of();
    }

    public static void onButtonHovered(IRecipeLayoutDrawable<?> layout) {
        boolean pinInputs = Ae2UtilityClientConfig.previewEncodeIngredientsOnArrowHover();
        if (!pinInputs && !Ae2UtilityClientConfig.previewEncodeTerminalOnArrowHover()) {
            return;
        }
        Entry entry = entry(layout);
        if (!entry.inFlight && !entry.failed && entry.serverPanel == null) {
            JeiEncodePacketFactory.tryCreate(layout, false)
                    .filter(EncodePatternPacket::fitsNetworkLimits)
                    .ifPresentOrElse(packet -> {
                        entry.inFlight = true;
                        ModNetworking.sendToServer(new QueryEncodePreviewPacket(entry.requestId, packet));
                    }, () -> entry.failed = true);
        }
        if (pinInputs) {
            pinned = entry.pins;
            pinnedTick = JeiClientCacheContext.getTickGeneration();
        }
    }

    public static @Nullable PatternEncodingPreview tooltipPreview(IRecipeLayoutDrawable<?> layout) {
        if (!Ae2UtilityClientConfig.previewEncodeTerminalOnArrowHover()) {
            return null;
        }
        Entry entry = entry(layout);
        if (entry.serverPanel != null) {
            return entry.serverPanel;
        }
        if (entry.localPanel == null) {
            var level = Minecraft.getInstance().level;
            entry.localPanel = JeiEncodePacketFactory.tryCreate(layout, false)
                    .map(packet -> PatternEncodingPreview.guess(packet, level, CraftableStateCache::isCraftable))
                    .orElse(null);
        }
        return entry.localPanel;
    }

    public static @Nullable ITypedIngredient<?> pinnedIngredient(IRecipeSlotView slot) {
        if (pinned.isEmpty() || JeiClientCacheContext.getTickGeneration() - pinnedTick > 1) {
            return null;
        }
        return pinned.get(slot);
    }

    public static void handleResult(EncodePreviewResultPacket result) {
        for (Entry entry : ENTRIES.values()) {
            if (entry.requestId != result.requestId()) {
                continue;
            }
            entry.inFlight = false;
            if (!result.hasPanel()) {
                entry.failed = true;
                return;
            }
            entry.serverPanel = result.panelPreview();
            entry.pins = mapToSlots(entry.inputSlots, result.inputs());
            return;
        }
    }

    private static Entry entry(IRecipeLayoutDrawable<?> layout) {
        long signature = signature();
        long now = System.currentTimeMillis();
        Entry entry = ENTRIES.get(layout);
        if (entry == null || entry.signature != signature || (!entry.inFlight && now - entry.createdAt > TTL_MS)) {
            entry = new Entry();
            entry.requestId = NEXT_REQUEST_ID.getAndIncrement();
            entry.signature = signature;
            entry.createdAt = now;
            entry.inputSlots = List.copyOf(layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT));
            ENTRIES.put(layout, entry);
        }
        return entry;
    }

    private static long signature() {
        long signature = JeiBookmarkKeysCache.getBookmarkSignature();
        signature = 31L * signature + (JeiPatternSubstitutionUi.isItemSubstituteOn() ? 1 : 0);
        return 31L * signature + (JeiPatternSubstitutionUi.isFluidSubstituteOn() ? 1 : 0);
    }

    private static Map<IRecipeSlotView, ITypedIngredient<?>> mapToSlots(List<IRecipeSlotView> slots,
            List<GenericStack> encodedInputs) {
        Map<IRecipeSlotView, ITypedIngredient<?>> pins = new IdentityHashMap<>();
        for (int i = 0; i < Math.min(slots.size(), encodedInputs.size()); i++) {
            GenericStack encoded = encodedInputs.get(i);
            if (encoded == null || encoded.what() == null) {
                continue;
            }
            IRecipeSlotView slot = slots.get(i);
            ITypedIngredient<?> typed = findIngredient(slot, encoded.what());
            if (typed != null) {
                pins.put(slot, typed);
            }
        }
        return pins;
    }

    private static @Nullable ITypedIngredient<?> findIngredient(IRecipeSlotView slot, AEKey key) {
        for (ITypedIngredient<?> typed : slot.getAllIngredients().toList()) {
            if (key.equals(GenericIngredientUtil.toAEKey(typed.getIngredient()))) {
                return typed;
            }
        }
        return null;
    }
}

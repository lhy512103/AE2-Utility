package com.lhy.ae2utility.client.jei;

import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.JeiBookmarkKeysCache;
import com.lhy.ae2utility.jei.JeiClientCacheContext;
import com.lhy.ae2utility.jei.JeiEncodePacketFactory;
import com.lhy.ae2utility.jei.TerminalJeRecipeTransferPreview;
import com.lhy.ae2utility.network.EncodePatternPacket;
import java.util.Optional;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import com.lhy.ae2utility.client.PreviewRequestIds;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.client.Minecraft;
import com.lhy.ae2utility.network.ModNetworking;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.common.MEStorageMenu;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.network.EncodePreviewResultPacket;
import com.lhy.ae2utility.network.QueryEncodePreviewPacket;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;
import com.lhy.ae2utility.util.GenericIngredientUtil;
import com.lhy.ae2utility.util.PatternEncodingPreview;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;

import mezz.jei.gui.recipes.RecipesGui;

/** Owns a single JEI layout's query and preview state. No draw or input dispatch lives here. */
public final class EncodePatternQueryState {
    private final IRecipeLayoutDrawable<?> recipeLayout;
    private final EncodingPreparation preparation = new EncodingPreparation();
    private EncodePatternViewModel snapshot = EncodePatternViewModel.EMPTY;
    private record Context(Object level, Object player, Object menu, Object parentMenu,
            long bookmarks, long networkVersion, long terminalContentVersion, Object rules) {}
    private boolean cachedRecipeStructureReady = false;
    private IRecipeSlotsView cachedSlotsView;
    private final Map<IRecipeSlotView, List<ITypedIngredient<?>>> candidateSources = new IdentityHashMap<>();
    private List<GenericStack> cachedOutputs = List.of();
    private List<IRecipeSlotView> cachedInputSlots = List.of();
    private Map<IRecipeSlotView, List<mezz.jei.api.ingredients.ITypedIngredient<?>>> cachedInputIngredients = Map.of();
    /** 每个输入槽的全部候选（书签命中时为该书签项），用于「任一候选已有样板即高亮」判定，随书签签名刷新。 */
    private Map<IRecipeSlotView, List<GenericStack>> cachedInputAlternatives = Map.of();
    private long cachedRepresentativeBookmarkSignature = Long.MIN_VALUE;
    private long cachedInputCraftableSignature = Long.MIN_VALUE;
    private long cachedInputCraftableCacheVersion = Long.MIN_VALUE;
    private Map<IRecipeSlotView, Boolean> cachedInputCraftableStates = Map.of();
    /** 仅悬停编码箭头且配置开启时填充：服务端真正编码回传的材料。 */
    private Map<IRecipeSlotView, ITypedIngredient<?>> cachedChosenTypedIngredients = Map.of();
    private long cachedChosenBookmarkSignature = Long.MIN_VALUE;
    private int previewRequestId;
    private boolean previewPacketPrepared;
    private EncodePatternPacket previewPacket;
    private boolean previewRequestInFlight;
    private boolean previewRequestFailed;
    private List<GenericStack> cachedServerEncodedInputs = List.of();
    private @Nullable PatternEncodingPreview cachedTerminalPreview;
    private @Nullable PatternEncodingPreview cachedLocalTerminalPreview;

    public EncodePatternQueryState(IRecipeLayoutDrawable<?> recipeLayout) {
        this.recipeLayout = recipeLayout;
    }

    public EncodePatternViewModel snapshot() { return snapshot; }

    public boolean accepts(int requestId) {
        return previewRequestInFlight && previewRequestId == requestId;
    }

    /** Drop retained network/preview data without registering another global lifecycle. */
    public void invalidate() {
        previewRequestId = 0;
        previewPacketPrepared = false;
        previewPacket = null;
        previewRequestInFlight = false;
        previewRequestFailed = false;
        cachedServerEncodedInputs = List.of();
        cachedChosenTypedIngredients = Map.of();
        cachedChosenBookmarkSignature = Long.MIN_VALUE;
        cachedTerminalPreview = null;
        cachedLocalTerminalPreview = null;
        cachedRepresentativeBookmarkSignature = Long.MIN_VALUE;
        cachedInputCraftableSignature = Long.MIN_VALUE;
        cachedInputCraftableCacheVersion = Long.MIN_VALUE;
        snapshot = EncodePatternViewModel.EMPTY;
    }

    public void prepare(boolean available, boolean visible, boolean hovered) {
        if (!preparation.begin(JeiClientCacheContext.getTickGeneration())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RecipesGui gui = mc.screen instanceof RecipesGui rg ? rg : null;
        AbstractContainerMenu parent = gui == null ? null
                : ((com.lhy.ae2utility.mixin.RecipesGuiAccessor)(Object)gui).ae2utility$parentMenu();
        Context context = new Context(mc.level, mc.player, mc.player == null ? null : mc.player.containerMenu,
                parent, JeiBookmarkKeysCache.getBookmarkSignature(), CraftableStateCache.cacheVersion(),
                parent instanceof MEStorageMenu storage
                        ? com.lhy.ae2utility.jei.ClientRepoCraftableIndex.contentVersion(storage) : 0L,
                com.lhy.ae2utility.client.PatternSubstitutionState.snapshot());
        if (preparation.contextChanged(context)) {
            invalidate();
        }
        if (!available || !visible || gui == null) {
            snapshot = EncodePatternViewModel.EMPTY;
            return;
        }
        refreshHoveredCraftableState();
        int backdrop = 0;
        for (GenericStack output : cachedOutputs) {
            if (output != null && output.what() != null && CraftableStateCache.isCraftable(output.what())) {
                backdrop = 0x804545FF;
                break;
            }
        }
        if (backdrop == 0) {
            boolean terminalCraftable = parent instanceof MEStorageMenu storage && storage.getClientRepo() != null
                    && TerminalJeRecipeTransferPreview.compute(storage, recipeLayout.getRecipeSlotsView()).anyCraftable();
            if (terminalCraftable || cachedInputCraftableStates.containsValue(Boolean.TRUE)) {
                backdrop = 0x80FFA500;
            }
        }
        boolean preview = EncodingPreparation.shouldPreview(available, hovered,
                Ae2UtilityClientConfig.previewEncodeIngredientsOnArrowHover(),
                Ae2UtilityClientConfig.previewEncodeTerminalOnArrowHover());
        PatternEncodingPreview panel = null;
        if (preview) {
            ensureServerEncodedPreview();
            if (Ae2UtilityClientConfig.previewEncodeTerminalOnArrowHover()) {
                panel = terminalPreviewForTooltip();
            }
        }
        snapshot = new EncodePatternViewModel(backdrop, cachedInputSlots, cachedInputCraftableStates,
                preview ? cachedChosenTypedIngredients : Map.of(), panel);
    }

    private void refreshHoveredCraftableState() {
        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();
        if (recipeStructureChanged(slotsView)) {
            invalidate();
            cachedRecipeStructureReady = false;
        }
        analyzeRecipeSlotsIfNeeded(slotsView);
        ensureInputRepresentativesUpToDate();
        ensureInputCraftableStates();
    }

    private Optional<EncodePatternPacket> preparedPreviewPacket() {
        if (!previewPacketPrepared) {
            previewPacket = JeiEncodePacketFactory.tryCreate(recipeLayout, false, false, 0).orElse(null);
            previewPacketPrepared = true;
        }
        return Optional.ofNullable(previewPacket);
    }

    private void ensureServerEncodedPreview() {
        long bookmarkSig = cachedRepresentativeBookmarkSignature;
        if (cachedChosenBookmarkSignature != Long.MIN_VALUE && cachedChosenBookmarkSignature != bookmarkSig) {
            cachedServerEncodedInputs = List.of();
            cachedChosenTypedIngredients = Map.of();
            cachedTerminalPreview = null;
            cachedLocalTerminalPreview = null;
            previewRequestInFlight = false;
            previewRequestFailed = false;
        }
        if (hasAnyEncodedInput(cachedServerEncodedInputs)) {
            if (cachedChosenTypedIngredients.isEmpty() || cachedChosenBookmarkSignature != bookmarkSig) {
                cachedChosenTypedIngredients = mapServerInputsToSlots(cachedServerEncodedInputs);
                cachedChosenBookmarkSignature = bookmarkSig;
            }
            return;
        }
        if (previewRequestInFlight || previewRequestFailed) {
            return;
        }
        preparedPreviewPacket().ifPresentOrElse(packet -> {
            previewRequestId = PreviewRequestIds.next();
            cachedChosenBookmarkSignature = bookmarkSig;
            previewRequestInFlight = true;
            ModNetworking.sendToServer(new QueryEncodePreviewPacket(previewRequestId, packet));
        }, () -> previewRequestFailed = true);
    }

    public void applyServerEncodedInputs(EncodePreviewResultPacket payload) {
        if (!accepts(payload.requestId())) {
            return;
        }
        previewRequestInFlight = false;
        cachedServerEncodedInputs = payload.inputs();
        cachedTerminalPreview = payload.hasPanel() ? payload.panelPreview() : null;
        cachedLocalTerminalPreview = cachedTerminalPreview;
        if (!hasAnyEncodedInput(cachedServerEncodedInputs)) {
            previewRequestFailed = true;
            cachedChosenTypedIngredients = Map.of();
            return;
        }
        cachedChosenTypedIngredients = mapServerInputsToSlots(cachedServerEncodedInputs);
        cachedChosenBookmarkSignature = cachedRepresentativeBookmarkSignature;
    }

    private Map<IRecipeSlotView, ITypedIngredient<?>> mapServerInputsToSlots(List<GenericStack> encodedInputs) {
        Map<IRecipeSlotView, ITypedIngredient<?>> chosen = new IdentityHashMap<>();
        int n = Math.min(cachedInputSlots.size(), encodedInputs.size());
        for (int i = 0; i < n; i++) {
            GenericStack encoded = encodedInputs.get(i);
            if (encoded == null || encoded.what() == null) {
                continue;
            }
            IRecipeSlotView slotView = cachedInputSlots.get(i);
            ITypedIngredient<?> typed = findCachedTypedIngredient(cachedInputIngredients.get(slotView), encoded);
            if (typed == null) {
                typed = findTypedIngredientAcrossSlots(encoded);
            }
            if (typed != null) {
                chosen.put(slotView, typed);
            }
        }
        return chosen;
    }

    private static boolean hasAnyEncodedInput(List<GenericStack> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return false;
        }
        for (GenericStack input : inputs) {
            if (input != null && input.what() != null) {
                return true;
            }
        }
        return false;
    }

    private @Nullable ITypedIngredient<?> findTypedIngredientAcrossSlots(@Nullable GenericStack chosen) {
        if (chosen == null || chosen.what() == null) {
            return null;
        }
        for (IRecipeSlotView slotView : cachedInputSlots) {
            ITypedIngredient<?> typed = findCachedTypedIngredient(cachedInputIngredients.get(slotView), chosen);
            if (typed != null) {
                return typed;
            }
        }
        return null;
    }

    private static @Nullable ITypedIngredient<?> findCachedTypedIngredient(
            List<ITypedIngredient<?>> allIngredients, @Nullable GenericStack chosen) {
        if (chosen == null || chosen.what() == null || allIngredients == null) {
            return null;
        }
        for (ITypedIngredient<?> typed : allIngredients) {
            AEKey ingKey = GenericIngredientUtil.toAEKey(typed.getIngredient());
            if (ingKey != null && ingKey.equals(chosen.what())) {
                return typed;
            }
        }
        return null;
    }

    /** JEI 15 returns its retained candidate list directly: compare identities, not every alternative. */
    private boolean recipeStructureChanged(IRecipeSlotsView slotsView) {
        if (!cachedRecipeStructureReady || slotsView != cachedSlotsView) {
            return true;
        }
        int count = 0;
        for (var role : mezz.jei.api.recipe.RecipeIngredientRole.values()) {
            for (IRecipeSlotView slot : slotsView.getSlotViews(role)) {
                count++;
                if (candidateSources.get(slot) != slot.getAllIngredientsList()) {
                    return true;
                }
            }
        }
        return count != candidateSources.size();
    }

    private void analyzeRecipeSlotsIfNeeded(IRecipeSlotsView slotsView) {
        if (cachedRecipeStructureReady) {
            return;
        }

        cachedRecipeStructureReady = true;
        cachedSlotsView = slotsView;
        candidateSources.clear();
        for (var role : mezz.jei.api.recipe.RecipeIngredientRole.values()) {
            for (IRecipeSlotView slot : slotsView.getSlotViews(role)) {
                candidateSources.put(slot, slot.getAllIngredientsList());
            }
        }
        cachedRepresentativeBookmarkSignature = Long.MIN_VALUE;
        cachedInputCraftableSignature = Long.MIN_VALUE;
        cachedOutputs = RecipeTransferPacketHelper.getEncodingOutputs(recipeLayout.getRecipe(), slotsView);

        List<IRecipeSlotView> inputSlots = slotsView.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT);
        Map<IRecipeSlotView, List<mezz.jei.api.ingredients.ITypedIngredient<?>>> ingredientMap = new IdentityHashMap<>(inputSlots.size());
        for (IRecipeSlotView slotView : inputSlots) {
            List<mezz.jei.api.ingredients.ITypedIngredient<?>> allIngredients = slotView.getAllIngredients().toList();
            ingredientMap.put(slotView, List.copyOf(allIngredients));
        }

        cachedInputSlots = List.copyOf(inputSlots);
        cachedInputIngredients = ingredientMap;
        cachedInputAlternatives = Map.of();
        cachedInputCraftableStates = Map.of();
        cachedInputCraftableCacheVersion = Long.MIN_VALUE;
        cachedChosenTypedIngredients = Map.of();
        cachedChosenBookmarkSignature = Long.MIN_VALUE;
        cachedServerEncodedInputs = List.of();
        cachedTerminalPreview = null;
        cachedLocalTerminalPreview = null;
        previewRequestInFlight = false;
        previewRequestFailed = false;
    }

    /** 按书签签名重建每个输入槽的「全部候选」列表（书签命中则仅该项）。 */
    private void ensureInputRepresentativesUpToDate() {
        long bookmarkSignature = JeiBookmarkKeysCache.getBookmarkSignature();
        if (cachedRepresentativeBookmarkSignature == bookmarkSignature) {
            return;
        }

        Map<IRecipeSlotView, List<GenericStack>> alternatives = new IdentityHashMap<>(cachedInputSlots.size());
        for (IRecipeSlotView slotView : cachedInputSlots) {
            alternatives.put(slotView, RecipeTransferPacketHelper.collectEncodeAlternativesForInputSlot(slotView));
        }
        cachedInputAlternatives = alternatives;
        cachedRepresentativeBookmarkSignature = bookmarkSignature;
        cachedInputCraftableSignature = Long.MIN_VALUE;
        cachedInputCraftableCacheVersion = Long.MIN_VALUE;
    }

    /**
     * 每槽判定：候选列表里「任一候选」可合成/已有样板即视为满足。
     * 仅在书签签名或可合成快照版本变化时重算，既不每帧抖动，也能在异步查询回包后刷新。
     */
    private void ensureInputCraftableStates() {
        long bookmarkSig = cachedRepresentativeBookmarkSignature;
        long cacheVer = CraftableStateCache.cacheVersion();
        if (cachedInputCraftableSignature == bookmarkSig && cachedInputCraftableCacheVersion == cacheVer) {
            return;
        }

        Map<IRecipeSlotView, Boolean> states = new IdentityHashMap<>(cachedInputSlots.size());
        for (IRecipeSlotView slotView : cachedInputSlots) {
            List<GenericStack> alts = cachedInputAlternatives.get(slotView);
            boolean craftable = false;
            if (alts != null) {
                for (GenericStack gs : alts) {
                    if (gs != null && gs.what() != null && CraftableStateCache.isCraftable(gs.what())) {
                        craftable = true;
                        break;
                    }
                }
            }
            states.put(slotView, craftable);
        }
        cachedInputCraftableStates = states;
        cachedInputCraftableSignature = bookmarkSig;
        cachedInputCraftableCacheVersion = cacheVer;
    }

    private @Nullable PatternEncodingPreview terminalPreviewForTooltip() {
        if (cachedTerminalPreview != null) {
            return cachedTerminalPreview;
        }
        if (cachedLocalTerminalPreview != null) {
            return cachedLocalTerminalPreview;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        cachedLocalTerminalPreview = preparedPreviewPacket()
                .map(packet -> PatternEncodingPreview.guess(packet, mc.level,
                        CraftableStateCache::isCraftable,
                        mc.player == null ? null : mc.player.getInventory().items))
                .orElse(null);
        return cachedLocalTerminalPreview;
    }

}

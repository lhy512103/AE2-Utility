package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.lhy.ae2utility.card.NbtTearCardThreadLocal;
import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.debug.NbtTearCardDebug;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess;
import com.lhy.ae2utility.integration.ae2.PatternProviderFeatureCardCache;
import com.lhy.ae2utility.integration.ae2.PatternProviderSignalAccess;
import com.lhy.ae2utility.item.NbtTearCardItem;

@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = "io.github.lounode.ae2cs.common.me.logic.MeteoritePatternProviderLogic", remap = false)
public abstract class MixinMeteoritePatternProviderLogic implements NbtTearLogicAccess, PatternProviderSignalAccess {
    private static final String AE2UTILITY_TEAR_CARD = "ae2utility_nbt_tear_card";

    @Unique
    private ItemStacksResourceHandler ae2utility$tearHandler;

    @Unique
    private PatternProviderFeatureCardCache ae2utility$featureCardCache;

    @Unique
    private int ae2utility$signalPulseUntilTick;

    @Unique
    private boolean ae2utility$continuousSignalActive;

    @Unique
    private boolean ae2utility$lastRedstoneActive;

    @Override
    public ItemStacksResourceHandler ae2utility$getTearHandler() {
        if (ae2utility$tearHandler == null) {
            PatternProviderLogicHost host = ((PatternProviderLogicInvoker) this).ae2utility$getHost();
            ae2utility$tearHandler = new ItemStacksResourceHandler(1) {
                @Override
                protected void onContentsChanged(int slot, ItemStack previousContents) {
                    if (ae2utility$featureCardCache != null) {
                        ae2utility$featureCardCache.invalidate();
                    }
                    host.saveChanges();
                }
            };
        }
        return ae2utility$tearHandler;
    }

    @Override
    public PatternProviderFeatureCardCache ae2utility$getFeatureCardCache() {
        if (ae2utility$featureCardCache == null) {
            ae2utility$featureCardCache = new PatternProviderFeatureCardCache(this,
                    () -> ae2utility$getTearHandler().getResource(0).toStack(ae2utility$getTearHandler().getAmountAsInt(0)));
        }
        return ae2utility$featureCardCache;
    }

    @Override
    public int ae2utility$getSignalPulseUntilTick() {
        return ae2utility$signalPulseUntilTick;
    }

    @Override
    public void ae2utility$setSignalPulseUntilTick(int gameTime) {
        ae2utility$signalPulseUntilTick = gameTime;
    }

    @Override
    public boolean ae2utility$isContinuousSignalActive() {
        return ae2utility$continuousSignalActive;
    }

    @Override
    public void ae2utility$setContinuousSignalActive(boolean active) {
        ae2utility$continuousSignalActive = active;
    }

    @Override
    public boolean ae2utility$getLastRedstoneActive() {
        return ae2utility$lastRedstoneActive;
    }

    @Override
    public void ae2utility$setLastRedstoneActive(boolean active) {
        ae2utility$lastRedstoneActive = active;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2utility$writeDedicatedCardAndSignal(ValueOutput tag, CallbackInfo ci) {
        ItemStack card = ae2utility$getTearHandler().getResource(0).toStack(ae2utility$getTearHandler().getAmountAsInt(0));
        if (!card.isEmpty()) {
            tag.store(AE2UTILITY_TEAR_CARD, ItemStack.CODEC, card);
        }
        if (ae2utility$signalPulseUntilTick > 0) {
            tag.putInt("ae2utility_signal_pulse_until", ae2utility$signalPulseUntilTick);
        }
        if (ae2utility$continuousSignalActive) {
            tag.putBoolean("ae2utility_continuous_signal_active", true);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2utility$readDedicatedCardAndSignal(ValueInput tag, CallbackInfo ci) {
        ItemStack loadedCard = tag.read(AE2UTILITY_TEAR_CARD, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        ae2utility$getTearHandler().set(0, net.neoforged.neoforge.transfer.item.ItemResource.of(loadedCard), loadedCard.getCount());
        ae2utility$signalPulseUntilTick = tag.getIntOr("ae2utility_signal_pulse_until", 0);
        ae2utility$continuousSignalActive = tag.getBooleanOr("ae2utility_continuous_signal_active", false);
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void ae2utility$dropDedicatedCard(java.util.List<ItemStack> drops, CallbackInfo ci) {
        ItemStack card = ae2utility$getTearHandler().getResource(0).toStack(ae2utility$getTearHandler().getAmountAsInt(0));
        if (!card.isEmpty()) {
            drops.add(card);
            ae2utility$getTearHandler().set(0, net.neoforged.neoforge.transfer.item.ItemResource.EMPTY, 0);
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void ae2utility$clearDedicatedCardAndSignal(CallbackInfo ci) {
        ae2utility$getTearHandler().set(0, net.neoforged.neoforge.transfer.item.ItemResource.EMPTY, 0);
        ae2utility$signalPulseUntilTick = 0;
        ae2utility$continuousSignalActive = false;
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void ae2utility$applyUnlockHandling(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir) {
        PatternProviderLogic logic = (PatternProviderLogic) (Object) this;
        ItemStack card = ((NbtTearLogicAccess) this).ae2utility$getEffectiveTearCardStack();
        NbtTearFilter filter = card.getItem() instanceof NbtTearCardItem
                ? card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT)
                : null;
        NbtTearCardDebug.logProviderCheck("meteorite_push_success", patternDetails, this, card, filter,
                cir.getReturnValueZ(), "lock=" + logic.getCraftingLockedReason());
        if (!cir.getReturnValueZ() || !(patternDetails instanceof IMolecularAssemblerSupportedPattern)) {
            return;
        }
        ((PatternProviderLogicInvoker) this).ae2utility$onPushPatternSuccess(patternDetails);
        NbtTearCardDebug.logProviderCheck("meteorite_push_unlock_applied", patternDetails, this, card, filter, true,
                "lock=" + logic.getCraftingLockedReason());
        PatternProviderLogicHost host = ((PatternProviderLogicInvoker) this).ae2utility$getHost();
        // 自装配式样板绕过 PatternProviderLogic.pushPattern，必须在自己的成功返回点
        // 转发下单事件；完成侧仍由输出返还回调/基类账本负责。
        ae2utility$onSuccessfulPatternPush(host, logic.isBusy(), !logic.getReturnInv().isEmpty());
    }

    @Redirect(method = "workCraftedContents", at = @At(value = "INVOKE", target = "Lappeng/api/storage/MEStorage;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"))
    private long ae2utility$notifyInsertedResults(MEStorage storage, AEKey key, long amount, Actionable mode,
            IActionSource source) {
        PatternProviderLogic logic = (PatternProviderLogic) (Object) this;
        ItemStack card = ((NbtTearLogicAccess) this).ae2utility$getEffectiveTearCardStack();
        NbtTearFilter filter = card.getItem() instanceof NbtTearCardItem
                ? card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT)
                : null;
        if (filter != null) {
            NbtTearCardThreadLocal.set(filter);
        }
        long inserted;
        try {
            inserted = storage.insert(key, amount, mode, source);
        } finally {
            if (filter != null) {
                NbtTearCardThreadLocal.clear();
            }
        }
        if (mode == Actionable.MODULATE && inserted > 0) {
            NbtTearCardDebug.logProviderCheck("meteorite_result_insert", new GenericStack(key, inserted), this, card, filter,
                    true, "inserted=" + inserted + " lock=" + logic.getCraftingLockedReason());
            ((PatternProviderLogicInvoker) this)
                    .ae2utility$onStackReturnedToNetwork(new GenericStack(key, inserted));
        }
        return inserted;
    }
}

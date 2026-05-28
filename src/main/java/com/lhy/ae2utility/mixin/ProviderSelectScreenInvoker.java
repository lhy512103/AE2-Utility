package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "com.extendedae_plus.client.screen.ProviderSelectScreen", remap = false)
public interface ProviderSelectScreenInvoker {
    @Invoker("onChoose")
    void ae2utility$onChoose(int idx, boolean showStatusMessage);
}

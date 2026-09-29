package com.lhy.ae2utility.mixin.eaep;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "com.extendedae_plus.client.screen.ProviderSelectScreen", remap = false)
public interface ProviderSelectScreenAccessor {
    @Accessor("fNames")
    List<String> ae2utility$filteredNames();

    @Accessor("query")
    String ae2utility$query();

    @Accessor("autoUploadUniqueMatchEnabled")
    static boolean ae2utility$autoUploadUniqueMatchEnabled() {
        throw new AssertionError();
    }

    @Invoker("onChoose")
    void ae2utility$choose(int idx, boolean showStatusMessage);
}

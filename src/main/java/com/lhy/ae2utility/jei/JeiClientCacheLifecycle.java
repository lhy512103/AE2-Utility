package com.lhy.ae2utility.jei;

import com.lhy.ae2utility.api.transfer.MachineTransferClientAdapter;
import com.lhy.ae2utility.client.ClientCacheLifecycle;
import com.lhy.ae2utility.client.EncodePreviewClientDispatch;

/** Optional JEI adapter: loaded only inside the JEI-present lifecycle branch. */
public final class JeiClientCacheLifecycle {
    private JeiClientCacheLifecycle() {}

    public static void install() {
        MachineTransferClientAdapter.install(MachineTransferRecipeTypes::register);
        EncodePreviewClientDispatch.addListener(EncodePatternButtonController::handlePreviewResult);
        ClientCacheLifecycle.installOptionalClear(JeiClientCacheLifecycle::clear);
    }

    private static void clear() {
        JeiBookmarkKeysCache.invalidate();
        EncodePatternButtonController.invalidateAllPreviews();
    }
}

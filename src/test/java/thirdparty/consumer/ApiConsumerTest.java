package thirdparty.consumer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.lhy.ae2utility.api.Ae2UtilityApi;
import com.lhy.ae2utility.api.card.CardApi;
import com.lhy.ae2utility.api.card.NbtTearRule;
import com.lhy.ae2utility.api.card.RedstoneSignalHost;
import com.lhy.ae2utility.api.pattern.PatternEncodingBatch;
import com.lhy.ae2utility.api.pattern.PatternEncodingRequest;
import com.lhy.ae2utility.api.pattern.PatternEncodingResult;
import com.lhy.ae2utility.api.pattern.PatternUploadMode;
import com.lhy.ae2utility.api.transfer.IngredientRequest;
import com.lhy.ae2utility.api.transfer.RecipeTransferApi;
import com.lhy.ae2utility.api.transfer.RecipeTransferResult;
import com.lhy.ae2utility.api.transfer.TransferOptions;
import net.minecraft.world.item.ItemStack;

/** A minimal independently packaged consumer: imports only api/** and target platform types. */
class ApiConsumerTest {
    @Test
    void publicEntryPointsLoadAtCurrentVersion() {
        assertEquals(2, Ae2UtilityApi.API_VERSION);
        assertSame(Ae2UtilityApi.cards(), Ae2UtilityApi.cards());
        assertNotNull(Ae2UtilityApi.patternEncoding());
        assertNotNull(Ae2UtilityApi.recipeTransfer());
    }

    @Test
    void cardNullContractsDoNotNeedAClientOrLiveGrid() {
        CardApi cards = Ae2UtilityApi.cards();
        assertFalse(cards.isNbtTearCard(null));
        assertFalse(cards.isRedstoneSignalCard(null));
        assertEquals(NbtTearRule.ALL_ITEMS, cards.getNbtTearRule(null));
        assertThrows(IllegalArgumentException.class, () -> cards.setNbtTearRule(null, NbtTearRule.ALL_ITEMS));
        assertThrows(IllegalArgumentException.class, () -> cards.getRedstoneMode(null));
        assertThrows(IllegalArgumentException.class, () -> cards.setRedstoneMode(null, null));
        assertThrows(IllegalArgumentException.class, () -> cards.getSignalDurationTicks(null));
        assertThrows(IllegalArgumentException.class, () -> cards.setSignalDurationTicks(null, 20));
        assertFalse(cards.matchesNbtTear(null, null, NbtTearRule.ALL_ITEMS));
        assertThrows(IllegalArgumentException.class, () -> cards.matchesNbtTear(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> cards.updateRedstoneSignal(null, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> cards.onSuccessfulPatternPush(null, false, false));
        assertThrows(IllegalArgumentException.class, () -> new NbtTearRule(null));
        assertTrue(new NbtTearRule(Set.of()).itemIds().isEmpty());
    }

    @Test
    void callbacksRunSynchronouslyOnTheOwningCallerThread() {
        Thread owner = Thread.currentThread();
        boolean[] active = { false };
        RedstoneSignalHost host = new RedstoneSignalHost() {
            public ItemStack signalCard() { assertSame(owner, Thread.currentThread()); return null; }
            public boolean lastActive() { assertSame(owner, Thread.currentThread()); return active[0]; }
            public void setLastActive(boolean value) { assertSame(owner, Thread.currentThread()); active[0] = value; }
            public void triggerPulse(int ticks) { fail("missing card cannot pulse"); }
            public void setContinuousSignal(boolean value) { assertSame(owner, Thread.currentThread()); assertFalse(value); }
        };
        Ae2UtilityApi.cards().updateRedstoneSignal(host, false, true, false);
        assertTrue(active[0]);
    }

    @Test
    void encodingNullAndEmptyBatchContractsAreServerAuthoritative() {
        var api = Ae2UtilityApi.patternEncoding();
        assertEquals(PatternEncodingResult.INVALID_REQUEST, api.encode(null, null));
        assertEquals(PatternEncodingResult.INVALID_REQUEST, api.encodeBatch(null, null));
        assertEquals(PatternEncodingResult.INVALID_REQUEST,
                api.encodeBatch(null, new PatternEncodingBatch(List.of(), 0, false)));
        assertThrows(IllegalArgumentException.class, () -> new PatternEncodingBatch(null, 0, false));
        List<PatternEncodingRequest> source = new ArrayList<>();
        var batch = new PatternEncodingBatch(source, 12, true);
        source.add(null);
        assertTrue(batch.requests().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> batch.requests().add(null));
        assertThrows(IllegalArgumentException.class, () -> new PatternEncodingRequest(
                List.of(), List.of(), null, null, null, null, PatternUploadMode.values()[0],
                false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new PatternEncodingRequest(
                null, List.of(), null, null, null, null, PatternUploadMode.values()[0],
                false, false, false, false));
    }

    @Test
    void transferNullAndEmptyInputContractsRemainCallableWithoutViewerInitialization() {
        RecipeTransferApi transfer = Ae2UtilityApi.recipeTransfer();
        assertEquals(RecipeTransferResult.INVALID_REQUEST, transfer.pullToInventory(null, List.of(), TransferOptions.SINGLE));
        assertEquals(RecipeTransferResult.INVALID_REQUEST, transfer.pullToOpenTerminal(null, null, null));
        assertEquals(RecipeTransferResult.INVALID_REQUEST, transfer.pullToMachine(null, null, 0, List.of(), TransferOptions.SINGLE));
        assertThrows(IllegalArgumentException.class, () -> new IngredientRequest(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new IngredientRequest(List.of(), -1));
        IngredientRequest empty = new IngredientRequest(List.of(), 0);
        assertTrue(empty.alternatives().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> empty.alternatives().add(null));
        assertFalse(TransferOptions.SINGLE.maxTransfer());
        assertFalse(TransferOptions.SINGLE.craftMissing());
    }
}

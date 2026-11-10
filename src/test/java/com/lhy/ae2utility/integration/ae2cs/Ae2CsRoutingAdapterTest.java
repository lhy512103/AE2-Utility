package com.lhy.ae2utility.integration.ae2cs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderTarget;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.lhy.ae2utility.Ae2UtilityServerConfig;
import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.testutil.MinecraftTestBootstrap;

class Ae2CsRoutingAdapterTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensure();
        CommentedConfig config = CommentedConfig.inMemory();
        Ae2UtilityServerConfig.SPEC.correct(config);
        Ae2UtilityServerConfig.SPEC.setConfig(config);
        assertTrue(Ae2UtilityServerConfig.SPEC.isLoaded());
        assertEquals(Ae2UtilityServerConfig.ENABLE_NBT_TEAR_PRODUCTIVE_BEES_SPAWN_EGG_BLACKLIST.getDefault(),
                Ae2UtilityServerConfig.ENABLE_NBT_TEAR_PRODUCTIVE_BEES_SPAWN_EGG_BLACKLIST.get());
        assertEquals(Ae2UtilityServerConfig.ENABLE_NBT_TEAR_DRACONIC_EVOLUTION_FUSION_INJECTOR_BLACKLIST.getDefault(),
                Ae2UtilityServerConfig.ENABLE_NBT_TEAR_DRACONIC_EVOLUTION_FUSION_INJECTOR_BLACKLIST.get());
        assertEquals(List.of(), Ae2UtilityServerConfig.NBT_TEAR_CARD_ITEM_BLACKLIST.get());
    }

    private static AEItemKey variant(String value) {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.getOrCreateTag().putString("ae2utility_test_variant", value);
        return AEItemKey.of(stack);
    }

    private static KeyCounter holder(AEKey key, long amount) {
        KeyCounter holder = new KeyCounter();
        holder.add(key, amount);
        return holder;
    }

    /**
     * Same layout as {@link GenericStack#writeTag}: the generic key tag plus the amount under {@code "#"}.
     * {@link GenericStack#readTag} resolves the key type through AE2's Forge registry, which only exists
     * under FML, so item keys are read back directly with {@link AEItemKey#fromTag}.
     */
    private static CompoundTag writeItemStack(GenericStack stack) {
        CompoundTag tag = stack.what().toTagGeneric();
        tag.putLong("#", stack.amount());
        return tag;
    }

    private static GenericStack readItemStack(CompoundTag tag) {
        return new GenericStack(AEItemKey.fromTag(tag), tag.getLong("#"));
    }

    private static Map<AEKey, Long> amounts(List<GenericStack> stacks) {
        Map<AEKey, Long> amounts = new LinkedHashMap<>();
        for (GenericStack stack : stacks) {
            amounts.merge(stack.what(), stack.amount(), Long::sum);
        }
        return amounts;
    }

    @Test
    void actualItemKeysUseCopiedSharedBudgetsPreservingNbtAndOriginalRoutes() {
        AEItemKey encoded = variant("encoded");
        AEItemKey red = variant("red");
        AEItemKey blue = variant("blue");
        assertNotEquals(encoded, red);
        assertNotEquals(red, blue);
        KeyCounter first = holder(red, 1);
        KeyCounter second = holder(red, 1);
        second.add(blue, 3);
        var attempt = new RoutingAttempt<Object, Object>();
        List<GenericStack> resolved = Ae2CsRoutingAdapter.resolve(Arrays.asList(null,
                new GenericStack(encoded, 4), null, new GenericStack(encoded, 1)),
                new KeyCounter[] { first, second }, NbtTearFilter.DEFAULT, attempt);
        assertEquals(Map.of(red, 2L, blue, 3L), amounts(resolved));
        assertEquals(List.of(1, 1, 3), List.of(attempt.sourceIndex(0), attempt.sourceIndex(1), attempt.sourceIndex(2)));
        assertEquals(1, first.get(red));
        assertEquals(1, second.get(red));
        assertEquals(3, second.get(blue));
        assertEquals(0, amounts(resolved).getOrDefault(encoded, 0L));
        assertTrue(attempt.isRouted());
    }

    @Test
    void strictMismatchAndInsufficientRepeatedAlternativesLeaveActualHoldersUntouched() {
        AEItemKey encoded = variant("encoded");
        AEItemKey actual = variant("actual");
        KeyCounter supplied = holder(actual, 3);
        var strict = new RoutingAttempt<Object, Object>();
        assertThrows(Ae2CsRoutingAdapter.UnresolvedInputs.class,
                () -> Ae2CsRoutingAdapter.resolve(List.of(new GenericStack(encoded, 3)),
                        new KeyCounter[] { supplied }, null, strict));
        assertFalse(strict.isRouted());
        assertEquals(3, supplied.get(actual));
        var shortBudget = new RoutingAttempt<Object, Object>();
        assertThrows(Ae2CsRoutingAdapter.UnresolvedInputs.class,
                () -> Ae2CsRoutingAdapter.resolve(List.of(new GenericStack(encoded, 2), new GenericStack(encoded, 2)),
                        new KeyCounter[] { supplied }, NbtTearFilter.DEFAULT, shortBudget));
        assertFalse(shortBudget.isRouted());
        assertEquals(3, supplied.get(actual));
        assertEquals(1, supplied.size());
    }

    @Test
    void realGenericStackTagRoundTripKeepsPendingActualNbtVariantsAndAmounts() {
        AEItemKey encoded = variant("encoded");
        AEItemKey red = variant("red");
        AEItemKey blue = variant("blue");
        KeyCounter supplied = holder(red, 2);
        supplied.add(blue, 3);
        var resolved = Ae2CsRoutingAdapter.resolve(List.of(new GenericStack(encoded, 5)),
                new KeyCounter[] { supplied }, NbtTearFilter.DEFAULT, new RoutingAttempt<>());
        var reloaded = resolved.stream().map(Ae2CsRoutingAdapterTest::writeItemStack).map(tag -> tag.copy())
                .map(Ae2CsRoutingAdapterTest::readItemStack).toList();
        assertEquals(resolved, reloaded);
        assertEquals(Map.of(red, 2L, blue, 3L), amounts(reloaded));
        for (GenericStack stack : reloaded) {
            assertInstanceOf(AEItemKey.class, stack.what());
            assertNotEquals(encoded, stack.what());
            assertNotNull(((AEItemKey) stack.what()).getTag());
        }
    }

    @Test
    void configuredBlacklistStillRejectsNbtAlternatives() {
        var configured = List.of("minecraft:diamond");
        Ae2UtilityServerConfig.NBT_TEAR_CARD_ITEM_BLACKLIST.set(configured);
        try {
            assertTrue(Ae2UtilityServerConfig.isNbtTearCardItemBlacklisted(
                    ResourceLocation.fromNamespaceAndPath("minecraft", "diamond")));
            assertFalse(com.lhy.ae2utility.card.NbtTearFilter.matchesUnlockExpected(
                    variant("expected"), variant("actual"), NbtTearFilter.DEFAULT));
        } finally {
            Ae2UtilityServerConfig.NBT_TEAR_CARD_ITEM_BLACKLIST.set(List.of());
        }
    }

    @Test
    void actualPartialAcceptanceThenMissingCommitTargetBuffersAndReloadsEveryRemainingVariant() {
        AEItemKey encoded = variant("encoded");
        AEItemKey red = variant("red");
        AEItemKey blue = variant("blue");
        var supplied = holder(red, 3);
        supplied.add(blue, 4);
        var attempt = new RoutingAttempt<String, PatternProviderTarget>();
        var resolved = Ae2CsRoutingAdapter.resolve(List.of(new GenericStack(encoded, 7)),
                new KeyCounter[] { supplied }, NbtTearFilter.DEFAULT, attempt);
        Map<AEKey, Long> accepted = new LinkedHashMap<>();
        PatternProviderTarget available = new PatternProviderTarget() {
            @Override
            public long insert(AEKey key, long amount, Actionable mode) {
                if (mode == Actionable.SIMULATE) {
                    return amount;
                }
                long inserted = Math.min(1, amount);
                accepted.merge(key, inserted, Long::sum);
                return inserted;
            }

            @Override
            public boolean containsPatternInput(Set<AEKey> inputs) {
                return false;
            }
        };
        for (int i = 0; i < resolved.size(); i++) {
            var input = resolved.get(i);
            var adapter = attempt.target("remote-" + i, () -> available);
            assertEquals(input.amount(), adapter.insert(input.what(), input.amount(), Actionable.SIMULATE));
        }
        List<GenericStack> pending = new java.util.ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            var input = resolved.get(i);
            // The second destination disappears only after the first one has accepted items.
            int index = i;
            var adapter = attempt.commitTarget("remote-" + i, () -> index == 0 ? available : null,
                    Ae2CsRoutingAdapter.BUFFER_ONLY_TARGET);
            long inserted = adapter.insert(input.what(), input.amount(), Actionable.MODULATE);
            if (inserted < input.amount()) {
                pending.add(new GenericStack(input.what(), input.amount() - inserted));
            }
        }
        assertEquals(1, accepted.values().stream().mapToLong(Long::longValue).sum());
        var reloaded = pending.stream().map(Ae2CsRoutingAdapterTest::writeItemStack)
                .map(Ae2CsRoutingAdapterTest::readItemStack).toList();
        assertEquals(pending, reloaded);
        Map<AEKey, Long> conserved = new LinkedHashMap<>(accepted);
        amounts(reloaded).forEach((key, amount) -> conserved.merge(key, amount, Long::sum));
        assertEquals(Map.of(red, 3L, blue, 4L), conserved);
        assertEquals(3, supplied.get(red));
        assertEquals(4, supplied.get(blue));
        assertFalse(conserved.containsKey(encoded));
    }
}

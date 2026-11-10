package com.lhy.ae2utility.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalResource;
import mekanism.common.capabilities.Capabilities;
import com.lhy.ae2utility.service.MeQuickTransferService.TransferSource;

/** Optional chemical transfer using the target-version transactional capability. */
public final class MekanismTransferCompat {
    private MekanismTransferCompat() {}

    public static @Nullable TransferSource findSource(ServerLevel level, BlockPos pos, Direction side) {
        var handler = Capabilities.CHEMICAL.getCapabilityIfLoaded(level, pos, side);
        if (handler == null) handler = Capabilities.CHEMICAL.getCapabilityIfLoaded(level, pos, null);
        return handler == null ? null : new ChemicalSource(handler);
    }

    private static final class ChemicalSource implements TransferSource {
        private final ResourceHandler<ChemicalResource> handler;
        private final Map<AEKey, List<Integer>> tanksByKey = new HashMap<>();

        private ChemicalSource(ResourceHandler<ChemicalResource> handler) {
            this.handler = handler;
        }

        @Override
        public LinkedHashMap<AEKey, Long> available() {
            var result = new LinkedHashMap<AEKey, Long>();
            tanksByKey.clear();
            for (int tank = 0; tank < handler.size(); tank++) {
                var resource = handler.getResource(tank);
                if (resource.isEmpty()) continue;
                var key = MekanismKey.of(resource.toStack(1));
                if (key != null) {
                    result.merge(key, handler.getAmountAsLong(tank), Long::sum);
                    tanksByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(tank);
                }
            }
            return result;
        }

        @Override
        public long extract(AEKey key, long amount, boolean simulate) {
            if (!(key instanceof MekanismKey chemicalKey) || amount <= 0) return 0;
            var tanks = tanksByKey.get(key);
            if (tanks == null) return 0;
            var resource = ChemicalResource.of(chemicalKey.getStack());
            long remaining = amount;
            int operations = 0;
            try (var transaction = Transaction.openRoot()) {
                for (int tank : tanks) {
                    while (remaining > 0 && operations++ < 64) {
                        int extracted = handler.extract(tank, resource,
                                (int) Math.min(Integer.MAX_VALUE, remaining), transaction);
                        if (extracted == 0) break;
                        remaining -= extracted;
                    }
                    if (remaining == 0 || operations >= 64) break;
                }
                if (!simulate) transaction.commit();
            }
            return amount - remaining;
        }

        @Override
        public long insert(AEKey key, long amount) {
            if (!(key instanceof MekanismKey chemicalKey) || amount <= 0) return 0;
            var resource = ChemicalResource.of(chemicalKey.getStack());
            long remaining = amount;
            try (var transaction = Transaction.openRoot()) {
                while (remaining > 0) {
                    int inserted = handler.insert(resource, (int) Math.min(Integer.MAX_VALUE, remaining), transaction);
                    if (inserted == 0) break;
                    remaining -= inserted;
                }
                transaction.commit();
            }
            return amount - remaining;
        }
    }
}

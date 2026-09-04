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

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;

import com.lhy.ae2utility.service.MeQuickTransferService.TransferSource;

/** Direct optional integration for Mekanism chemicals through Applied Mekanistics. */
public final class MekanismTransferCompat {
    private MekanismTransferCompat() {
    }

    public static @Nullable TransferSource findSource(ServerLevel level, BlockPos pos, Direction side) {
        IChemicalHandler handler = Capabilities.CHEMICAL.getCapabilityIfLoaded(level, pos, side);
        if (handler == null) {
            handler = Capabilities.CHEMICAL.getCapabilityIfLoaded(level, pos, null);
        }
        return handler == null ? null : new ChemicalSource(handler);
    }

    private static final class ChemicalSource implements TransferSource {
        private final IChemicalHandler handler;
        private final Map<AEKey, List<Integer>> tanksByKey = new HashMap<>();

        private ChemicalSource(IChemicalHandler handler) {
            this.handler = handler;
        }

        @Override
        public LinkedHashMap<AEKey, Long> available() {
            LinkedHashMap<AEKey, Long> result = new LinkedHashMap<>();
            tanksByKey.clear();
            for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
                ChemicalStack stack = handler.getChemicalInTank(tank);
                MekanismKey key = MekanismKey.of(stack);
                if (key != null) {
                    result.merge(key, stack.getAmount(), Long::sum);
                    tanksByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(tank);
                }
            }
            return result;
        }

        @Override
        public long extract(AEKey key, long amount, boolean simulate) {
            if (!(key instanceof MekanismKey chemicalKey)) {
                return 0;
            }
            List<Integer> tanks = tanksByKey.get(key);
            if (tanks == null) {
                return 0;
            }

            long remaining = amount;
            long extracted = 0;
            Action action = simulate ? Action.SIMULATE : Action.EXECUTE;
            for (int tank : tanks) {
                if (remaining <= 0) {
                    break;
                }
                ChemicalStack present = handler.getChemicalInTank(tank);
                MekanismKey presentKey = MekanismKey.of(present);
                if (!chemicalKey.equals(presentKey)) {
                    continue;
                }
                ChemicalStack result = handler.extractChemical(tank, remaining, action);
                MekanismKey resultKey = MekanismKey.of(result);
                if (!result.isEmpty() && chemicalKey.equals(resultKey)) {
                    long acceptedAmount = Math.min(remaining, result.getAmount());
                    extracted += acceptedAmount;
                    remaining -= acceptedAmount;
                    if (!simulate && result.getAmount() > acceptedAmount) {
                        handler.insertChemical(result.copyWithAmount(result.getAmount() - acceptedAmount), Action.EXECUTE);
                    }
                } else if (!simulate && !result.isEmpty()) {
                    handler.insertChemical(result, Action.EXECUTE);
                    break;
                }
            }
            return extracted;
        }

        @Override
        public long insert(AEKey key, long amount) {
            if (!(key instanceof MekanismKey chemicalKey)) {
                return 0;
            }
            ChemicalStack left = handler.insertChemical(chemicalKey.withAmount(amount), Action.EXECUTE);
            return amount - left.getAmount();
        }
    }
}

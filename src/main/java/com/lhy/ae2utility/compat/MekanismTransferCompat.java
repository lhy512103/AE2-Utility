package com.lhy.ae2utility.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;

import com.lhy.ae2utility.service.MeQuickTransferService;
import com.lhy.ae2utility.service.MeQuickTransferService.TransferSource;

/** Direct optional integration for Mekanism chemicals through Applied Mekanistics. */
public final class MekanismTransferCompat {
    private MekanismTransferCompat() {
    }

    public static void addSources(BlockEntity blockEntity, Direction side, List<TransferSource> sources) {
        addSource(blockEntity, Capabilities.GAS_HANDLER, side, sources);
        addSource(blockEntity, Capabilities.INFUSION_HANDLER, side, sources);
        addSource(blockEntity, Capabilities.PIGMENT_HANDLER, side, sources);
        addSource(blockEntity, Capabilities.SLURRY_HANDLER, side, sources);
    }

    private static <C extends Chemical<C>, S extends ChemicalStack<C>> void addSource(BlockEntity blockEntity,
            Capability<? extends IChemicalHandler<C, S>> capability, Direction side, List<TransferSource> sources) {
        IChemicalHandler<C, S> handler = MeQuickTransferService.getCapability(blockEntity, capability, side);
        if (handler != null) {
            sources.add(new ChemicalSource<>(handler));
        }
    }

    private static final class ChemicalSource<C extends Chemical<C>, S extends ChemicalStack<C>>
            implements TransferSource {
        private final IChemicalHandler<C, S> handler;
        private final Map<AEKey, List<Integer>> tanksByKey = new HashMap<>();

        private ChemicalSource(IChemicalHandler<C, S> handler) {
            this.handler = handler;
        }

        @Override
        public LinkedHashMap<AEKey, Long> available() {
            LinkedHashMap<AEKey, Long> result = new LinkedHashMap<>();
            tanksByKey.clear();
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                S stack = handler.getChemicalInTank(tank);
                if (stack.isEmpty()) {
                    continue;
                }
                MekanismKey key = MekanismKey.of(stack);
                if (key != null) {
                    result.merge(key, stack.getAmount(), (left, right) -> left + right);
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
                S present = handler.getChemicalInTank(tank);
                if (present.isEmpty() || !chemicalKey.equals(MekanismKey.of(present))) {
                    continue;
                }
                S result = handler.extractChemical(tank, remaining, action);
                if (result.isEmpty()) {
                    continue;
                }
                if (chemicalKey.equals(MekanismKey.of(result))) {
                    long acceptedAmount = Math.min(remaining, result.getAmount());
                    extracted += acceptedAmount;
                    remaining -= acceptedAmount;
                } else if (!simulate) {
                    handler.insertChemical(result, Action.EXECUTE);
                    break;
                }
            }
            return extracted;
        }

        @Override
        @SuppressWarnings("unchecked")
        public long insert(AEKey key, long amount) {
            // Keys indexed here all came from this handler, so the chemical type matches S.
            if (!(key instanceof MekanismKey chemicalKey) || !tanksByKey.containsKey(key)) {
                return 0;
            }
            S left = handler.insertChemical((S) chemicalKey.withAmount(amount), Action.EXECUTE);
            return amount - left.getAmount();
        }
    }
}

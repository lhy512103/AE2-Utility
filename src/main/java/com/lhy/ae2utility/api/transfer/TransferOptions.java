package com.lhy.ae2utility.api.transfer;

/** Transfer behavior independent from JEI or network packet state. */
public record TransferOptions(boolean maxTransfer, boolean craftMissing) {
    public static final TransferOptions SINGLE = new TransferOptions(false, false);
}
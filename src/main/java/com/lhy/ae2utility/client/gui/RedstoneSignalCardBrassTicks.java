package com.lhy.ae2utility.client.gui;

import net.minecraft.util.Mth;

/** 与 Create {@code BrassDiodeScrollValueBehaviour#setValueSettings} 一致：行 0=tick，1=秒×20，2=分×1200。 */
public final class RedstoneSignalCardBrassTicks {
    public static final int MIN_TICKS = 2;
    public static final int MAX_TICKS = 72000;

    private RedstoneSignalCardBrassTicks() {
    }

    public static int clampTotal(int ticks) {
        return Mth.clamp(ticks, MIN_TICKS, MAX_TICKS);
    }

    public static int encode(int row, int col) {
        col = Mth.clamp(col, 0, 60);
        int multiplier = switch (row) {
            case 0 -> 1;
            case 1 -> 20;
            default -> 60 * 20;
        };
        return clampTotal(Math.max(2, Math.max(1, col) * multiplier));
    }

    /** AE 等价 {@code BrassDiodeScrollValueBehaviour#getValueSettings}（仅 row/col）。 */
    public static int canonicalRow(int ticks) {
        ticks = clampTotal(ticks);
        if (ticks > 60 * 20) {
            return 2;
        }
        if (ticks > 60) {
            return 1;
        }
        return 0;
    }

    public static int canonicalColumn(int ticks) {
        ticks = clampTotal(ticks);
        if (ticks > 60 * 20) {
            return Math.min(60, ticks / (60 * 20));
        }
        if (ticks > 60) {
            return Math.min(60, ticks / 20);
        }
        return ticks;
    }

    /** 行 r∈{0..2} 用于绘制滑块的列（tier 不适用时夹在 60）。 */
    public static int sliderColumnForRow(int ticks, int row) {
        ticks = clampTotal(ticks);
        return switch (row) {
            case 0 -> Math.min(60, ticks);
            case 1 -> Math.min(60, ticks / 20);
            default -> Math.min(60, ticks / (60 * 20));
        };
    }

    public static int columnFromMouseX(int mouseX, int trackLeftScreen, int trackLength, int maxCol, boolean shift) {
        if (trackLength <= 0) {
            return 0;
        }
        double ax = mouseX - trackLeftScreen;
        double ratio = ax / trackLength;
        int col = Math.min(maxCol,
                Math.max(0, (int) Math.round(ratio * maxCol)));
        if (shift) {
            col = snapMilestone(col, 10);
        }
        return col;
    }

    public static int snapMilestone(int col, int milestone) {
        if (milestone <= 1) {
            return col;
        }
        int half = milestone / 2;
        col = Math.min(60, Math.max(0, (col + half) / milestone * milestone));
        return Math.min(60, col);
    }
}

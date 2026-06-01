package com.lhy.ae2utility.client;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

/**
 * JEI / 服务端下发的 EAEP 供应器过滤字串可能在 {@link com.extendedae_plus.client.screen.ProviderSelectScreen}
 * 创建之后才到达客户端；在此处缓存镜像，由 mixin 在 {@code init} 末尾写入 {@link EditBox}
 * （仅在框内当前为空时写入，以避免覆盖玩家输入；并多次 {@code init} 时能复用镜像）。
 */
public final class EaepPendingProviderSearch {

    /**
     * 最近一次由 {@link com.lhy.ae2utility.network.SyncEaepProviderSearchKeyPacket} 确认的已解析关键字；
     * 用于 ProviderSelectScreen 因缩放等原因二次 {@code init} 时仍能回填（一次性的 {@link #hasPending} 已在首次 init 被消费）。
     */
    private static volatile String lastSyncedResolvedFilterMirror = "";
    private static volatile boolean hasPending;
    private static volatile String pendingResolvedFilter = "";
    /** 最近一次由 ae2utility 自动写入搜索框的内容；用于识别后续是否被用户手动改动。 */
    private static volatile String lastAutoAppliedFilter = "";
    /** 当前会话内用户一旦手动修改/清空搜索框，就不再自动回填，直到开始新会话。 */
    private static volatile boolean suppressAutoReuseUntilForget;
    /** 当前这次 ProviderSelectScreen 打开后，是否仍允许“晚到的同步包”直接补写搜索框。 */
    private static volatile boolean allowLateDirectApplyForCurrentScreen;

    private EaepPendingProviderSearch() {
    }

    /** 在开始新的配方树上传会话或 EAEP「背包分组」批量前调用，避免复用过期的检索镜像。 */
    public static void forgetResolvedFilterReuse() {
        lastSyncedResolvedFilterMirror = "";
        hasPending = false;
        pendingResolvedFilter = "";
        lastAutoAppliedFilter = "";
        suppressAutoReuseUntilForget = false;
        allowLateDirectApplyForCurrentScreen = false;
        EaepRememberedProviderChoice.forget();
    }

    /**
     * @param resolvedFilter 已通过 EAEP {@code resolveSearchKeyAlias} 的字串；可为 {@code ""} 表示仅合成预设（不写搜索框）
     */
    public static void offerResolvedFilter(String resolvedFilter) {
        pendingResolvedFilter = resolvedFilter != null ? resolvedFilter : "";
        hasPending = true;
        if (!pendingResolvedFilter.isBlank()) {
            lastSyncedResolvedFilterMirror = pendingResolvedFilter;
        }
    }

    /**
     * 写入供应器弹出层搜索框时使用：若有尚未消费的同步结果则取出；否则在非空时使用最近一次同步镜像。
     * 若玩家在二次 {@code init} 前已输入内容，外层应配合 {@link EditBox#getValue()} 空白判断再写入。
     */
    public static @Nullable String populateSearchBoxFilterPreference() {
        allowLateDirectApplyForCurrentScreen = true;
        if (suppressAutoReuseUntilForget) {
            hasPending = false;
            return null;
        }
        if (hasPending) {
            hasPending = false;
            String v = pendingResolvedFilter != null ? pendingResolvedFilter : "";
            if (!v.isBlank()) {
                lastSyncedResolvedFilterMirror = v;
                return v;
            }
        }
        return lastSyncedResolvedFilterMirror.isBlank() ? null : lastSyncedResolvedFilterMirror;
    }

    public static void markAutoAppliedFilter(@Nullable String resolvedFilter) {
        String applied = resolvedFilter != null ? resolvedFilter : "";
        lastAutoAppliedFilter = applied;
        allowLateDirectApplyForCurrentScreen = true;
    }

    public static void observeCurrentSearchBoxValue(@Nullable String currentValue) {
        if (suppressAutoReuseUntilForget) {
            return;
        }
        String current = currentValue != null ? currentValue : "";
        if (lastAutoAppliedFilter.isBlank()) {
            return;
        }
        if (!current.equals(lastAutoAppliedFilter)) {
            suppressAutoReuseUntilForget = true;
            allowLateDirectApplyForCurrentScreen = false;
            EaepUploadDebugLog.info("provider search auto reuse suppressed currentLen={} autoLen={}",
                    current.length(), lastAutoAppliedFilter.length());
        }
    }

    /**
     * 同步包若晚于界面 {@code init}，{@link #populateSearchBoxFilterPreference()} 可能尚未执行；此处对已打开的供应器界面直接写入。
     */
    public static void applyResolvedFilterToOpenScreen(String resolvedFilter) {
        if (resolvedFilter == null || resolvedFilter.isBlank()) {
            return;
        }
        if (suppressAutoReuseUntilForget) {
            EaepUploadDebugLog.info("applyResolvedFilterToOpenScreen skip suppressed len={}", resolvedFilter.length());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null || !"com.extendedae_plus.client.screen.ProviderSelectScreen".equals(screen.getClass().getName())) {
            EaepUploadDebugLog.info("applyResolvedFilterToOpenScreen skip topScreen={}",
                    screen != null ? screen.getClass().getName() : "null");
            return;
        }
        try {
            java.lang.reflect.Field f = screen.getClass().getDeclaredField("searchBox");
            f.setAccessible(true);
            Object box = f.get(screen);
            if (box instanceof EditBox eb) {
                String prev = eb.getValue();
                boolean canApply = prev.isBlank() && allowLateDirectApplyForCurrentScreen
                        || (!lastAutoAppliedFilter.isBlank() && prev.equals(lastAutoAppliedFilter));
                if (!canApply) {
                    EaepUploadDebugLog.info(
                            "applyResolvedFilterToOpenScreen skip overwrite prevLen={} autoLen={} allowLate={}",
                            prev.length(), lastAutoAppliedFilter.length(), allowLateDirectApplyForCurrentScreen);
                    return;
                }
                eb.setValue(resolvedFilter);
                lastAutoAppliedFilter = resolvedFilter;
                allowLateDirectApplyForCurrentScreen = true;
                EaepUploadDebugLog.info("applyResolvedFilterToOpenScreen setValue len={} prevLen={}", resolvedFilter.length(),
                        prev.length());
            }
        } catch (Throwable t) {
            EaepUploadDebugLog.warn("applyResolvedFilterToOpenScreen reflection failed: {}", t.toString());
        }
    }
}

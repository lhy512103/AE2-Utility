package com.lhy.ae2utility.integration.eaep;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Version-neutral EAEP ABI adapter. Contains no Minecraft or EAEP linkage. */
public final class EaepApiAccess {
    private static final String ROOT = "com.extendedae_plus.util.uploadPattern.";
    private static final String LEGACY = ROOT + "ExtendedAEPatternUploadUtil";

    public enum UploadResult {
        SUCCESS, DUPLICATE, FAILED
    }

    private EaepApiAccess() {
    }

    /** A duplicate did not insert anything and must never consume the caller's original stack. */
    public static UploadResult uploadResult(Object value) {
        if (Boolean.TRUE.equals(value)) {
            return UploadResult.SUCCESS;
        }
        if (value instanceof Enum<?> outcome) {
            return switch (outcome.name()) {
                case "SUCCESS" -> UploadResult.SUCCESS;
                case "DUPLICATE" -> UploadResult.DUPLICATE;
                default -> UploadResult.FAILED;
            };
        }
        return UploadResult.FAILED;
    }

    /** Never substitute a different unique provider for an absent remembered name. */
    public static int automaticProviderIndex(List<String> names, boolean hasSearchFilter, String remembered) {
        if (!hasSearchFilter || names == null || names.isEmpty()) {
            return -1;
        }
        if (remembered != null) {
            return names.indexOf(remembered);
        }
        return names.size() == 1 ? 0 : -1;
    }

    static Class<?> uploadOwner(String role) throws ClassNotFoundException {
        try {
            return Class.forName(ROOT + role);
        } catch (ClassNotFoundException absentInLegacyVersion) {
            return Class.forName(LEGACY);
        }
    }

    static Object invokeUpload(String role, String name, Class<?>[] parameters, Object... arguments)
            throws ReflectiveOperationException {
        Method method = uploadOwner(role).getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method.invoke(null, arguments);
    }

    static Object uploadField(String role, String name) throws ReflectiveOperationException {
        return uploadOwner(role).getField(name).get(null);
    }

    /** Old versions persist this on the screen; new versions expose a ModConfigSpec value. */
    public static boolean uniqueProviderAutoUploadEnabled() {
        try {
            Class<?> screen = Class.forName("com.extendedae_plus.client.screen.ProviderSelectScreen");
            try {
                Field legacy = screen.getDeclaredField("autoUploadUniqueMatchEnabled");
                legacy.setAccessible(true);
                return legacy.getBoolean(null);
            } catch (NoSuchFieldException newVersion) {
                Object config = Class.forName("com.extendedae_plus.config.ModConfigs")
                        .getField("UNIQUE_PROVIDER_AUTO_UPLOAD_ENABLE").get(null);
                return Boolean.TRUE.equals(config.getClass().getMethod("get").invoke(config));
            }
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // Never auto-select a provider when its actual switch cannot be read.
            return false;
        }
    }
}

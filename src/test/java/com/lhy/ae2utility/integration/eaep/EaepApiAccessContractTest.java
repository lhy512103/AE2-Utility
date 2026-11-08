package com.lhy.ae2utility.integration.eaep;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure-Java regression checks; no Minecraft or EAEP classes are initialized. */
class EaepApiAccessContractTest {
    private enum Outcome { SUCCESS, DUPLICATE, FAILED, UNKNOWN }

    @Test
    void supportsLegacyBooleanResults() {
        assertAll(
                () -> assertEquals(EaepApiAccess.UploadResult.SUCCESS, EaepApiAccess.uploadResult(true)),
                () -> assertEquals(EaepApiAccess.UploadResult.FAILED, EaepApiAccess.uploadResult(false)));
    }

    @Test
    void distinguishesCurrentOutcomesWithoutTreatingDuplicatesAsSuccess() {
        assertAll(
                () -> assertEquals(EaepApiAccess.UploadResult.SUCCESS, EaepApiAccess.uploadResult(Outcome.SUCCESS)),
                () -> assertEquals(EaepApiAccess.UploadResult.DUPLICATE, EaepApiAccess.uploadResult(Outcome.DUPLICATE)),
                () -> assertEquals(EaepApiAccess.UploadResult.FAILED, EaepApiAccess.uploadResult(Outcome.FAILED)));
    }

    @Test
    void rejectsUnknownOrMissingResults() {
        assertAll(
                () -> assertEquals(EaepApiAccess.UploadResult.FAILED, EaepApiAccess.uploadResult(Outcome.UNKNOWN)),
                () -> assertEquals(EaepApiAccess.UploadResult.FAILED, EaepApiAccess.uploadResult("SUCCESS")),
                () -> assertEquals(EaepApiAccess.UploadResult.FAILED, EaepApiAccess.uploadResult(null)),
                () -> assertEquals(EaepApiAccess.UploadResult.FAILED, EaepApiAccess.uploadResult(new Object())));
    }

    @Test
    void neverSubstitutesAnAbsentRememberedProvider() {
        assertAll(
                () -> assertEquals(-1, EaepApiAccess.automaticProviderIndex(List.of("聚合核心max"), true, "聚合核心")),
                () -> assertEquals(1, EaepApiAccess.automaticProviderIndex(List.of("Other", "Remembered"), true, "Remembered")));
    }

    @Test
    void requiresARealFilterAndAnUnambiguousProvider() {
        assertAll(
                () -> assertEquals(0, EaepApiAccess.automaticProviderIndex(List.of("Unique"), true, null)),
                () -> assertEquals(-1, EaepApiAccess.automaticProviderIndex(List.of("Unique"), false, null)),
                () -> assertEquals(-1, EaepApiAccess.automaticProviderIndex(List.of("Unique"), false, "Unique")),
                () -> assertEquals(-1, EaepApiAccess.automaticProviderIndex(List.of("A", "B"), true, null)));
    }

    @Test
    void handlesMissingProviderLists() {
        assertAll(
                () -> assertEquals(-1, EaepApiAccess.automaticProviderIndex(List.of(), true, null)),
                () -> assertEquals(-1, EaepApiAccess.automaticProviderIndex(null, true, "Remembered")));
    }
}

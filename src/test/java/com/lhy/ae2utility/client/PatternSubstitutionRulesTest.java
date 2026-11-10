package com.lhy.ae2utility.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatternSubstitutionRulesTest {
    @TempDir Path directory;

    @Test
    void retainsExistingDefaultsAndPropertyKeys() throws Exception {
        Path preferences = directory.resolve("ae2utility-jei-substitution.properties");
        assertEquals(new PatternSubstitutionRules(false, true),
                PatternSubstitutionRules.load(preferences, PatternSubstitutionRules.DEFAULT));
        Files.writeString(preferences, "itemSubstitute=true\nfluidSubstitute=false\n");
        assertEquals(new PatternSubstitutionRules(true, false),
                PatternSubstitutionRules.load(preferences, PatternSubstitutionRules.DEFAULT));
    }

    @Test
    void togglesDoNotMutateSnapshotsAndPersistBothRules() throws Exception {
        PatternSubstitutionRules before = PatternSubstitutionRules.DEFAULT;
        PatternSubstitutionRules after = before.toggleItems().toggleFluids();
        assertEquals(new PatternSubstitutionRules(false, true), before);
        assertEquals(new PatternSubstitutionRules(true, false), after);
        Path preferences = directory.resolve("config/ae2utility-jei-substitution.properties");
        after.save(preferences);
        assertEquals(after, PatternSubstitutionRules.load(preferences, before));
        assertEquals(after, PatternSubstitutionRules.load(preferences, after.toggleItems()));
    }

    @Test
    void missingPropertyKeepsTheOtherRule() throws Exception {
        Path preferences = directory.resolve("partial.properties");
        Files.writeString(preferences, "itemSubstitute=true\n");
        assertEquals(new PatternSubstitutionRules(true, true),
                PatternSubstitutionRules.load(preferences, PatternSubstitutionRules.DEFAULT));
    }

    @Test
    void viewersShareRulesWithoutDependingOnJeiUiObjects() throws Exception {
        // Read class bytes without initializing MC, either viewer, or mod registries.
        for (String factory : new String[] { "jei/JeiEncodePacketFactory", "emi/EmiEncodePacketFactory" }) {
            String bytecode = classBytes(factory);
            assertTrue(bytecode.contains("com/lhy/ae2utility/client/EncodePatternDrafts"));
            assertFalse(bytecode.contains("com/lhy/ae2utility/jei/JeiPatternSubstitutionUi"));
        }
        // Both factories build packets through the shared draft assembler, which reads the shared rules.
        String drafts = classBytes("client/EncodePatternDrafts");
        assertTrue(drafts.contains("com/lhy/ae2utility/client/PatternSubstitutionState"));
        assertFalse(drafts.contains("mezz/jei/"));
        assertFalse(drafts.contains("dev/emi/"));
        assertFalse(classBytes("emi/EmiBatchScreenButtons").contains(
                "com/lhy/ae2utility/jei/JeiPatternSubstitutionUi"));
        assertFalse(classBytes("api/Ae2UtilityClientApi").contains(
                "com/lhy/ae2utility/jei/EncodePatternButtonController"));
        assertFalse(classBytes("emi/EmiEncodePatternButtonWidget").contains(
                "com/lhy/ae2utility/jei/EncodePatternButtonController"));
    }

    private static String classBytes(String name) throws Exception {
        try (var stream = PatternSubstitutionRulesTest.class.getResourceAsStream(
                "/com/lhy/ae2utility/" + name + ".class")) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}

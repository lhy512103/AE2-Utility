package com.lhy.ae2utility.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Viewer-independent immutable encoding rules. The properties keys/file remain compatible. */
public record PatternSubstitutionRules(boolean items, boolean fluids) {
    public static final PatternSubstitutionRules DEFAULT = new PatternSubstitutionRules(false, true);

    public PatternSubstitutionRules toggleItems() {
        return new PatternSubstitutionRules(!items, fluids);
    }

    public PatternSubstitutionRules toggleFluids() {
        return new PatternSubstitutionRules(items, !fluids);
    }

    public static PatternSubstitutionRules load(Path path, PatternSubstitutionRules fallback) throws IOException {
        if (!Files.exists(path)) {
            return fallback;
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return new PatternSubstitutionRules(
                Boolean.parseBoolean(properties.getProperty("itemSubstitute", Boolean.toString(fallback.items))),
                Boolean.parseBoolean(properties.getProperty("fluidSubstitute", Boolean.toString(fallback.fluids))));
    }

    public void save(Path path) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("itemSubstitute", Boolean.toString(items));
        properties.setProperty("fluidSubstitute", Boolean.toString(fluids));
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (var writer = Files.newBufferedWriter(path)) {
            properties.store(writer, "AE2 Utility JEI substitution toggles");
        }
    }
}

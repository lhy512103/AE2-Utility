package com.lhy.ae2utility.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class IntegrationTranslationsTest {
    @Test
    void bothLanguagesSupplyAppliedCreateJadeLabels() throws IOException {
        for (String locale : new String[] { "en_us", "zh_cn" }) {
            JsonObject translations = read(locale);
            for (String id : new String[] { "grid_node_state", "power_storage" }) {
                var value = translations.get("config.jade.plugin_appliedcreate." + id);
                assertNotNull(value, locale + ": " + id);
                assertFalse(value.getAsString().isBlank(), locale + ": " + id);
            }
        }
    }

    @Test
    void englishAndChineseExposeTheSameTranslationKeys() throws IOException {
        assertEquals(read("en_us").keySet(), read("zh_cn").keySet());
    }

    private static JsonObject read(String locale) throws IOException {
        Path path = Path.of("src/main/resources/assets/ae2utility/lang", locale + ".json");
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}

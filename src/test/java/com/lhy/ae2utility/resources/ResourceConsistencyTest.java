package com.lhy.ae2utility.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Validates packaged resources without bootstrapping Minecraft registries. */
class ResourceConsistencyTest {
    private static final List<String> ITEMS = List.of(
            "nbt_tear_card", "redstone_signal_card", "me_quick_transfer", "recipe_finder");
    private static final List<String> CRAFTABLE_ITEMS = List.of(
            "nbt_tear_card", "redstone_signal_card", "me_quick_transfer");

    @Test
    void everyItemHasAClientDefinitionModelAndTexture() throws IOException {
        for (String item : ITEMS) {
            JsonObject definition = json("assets/ae2utility/items/" + item + ".json");
            JsonObject modelReference = definition.getAsJsonObject("model");
            assertEquals("minecraft:model", modelReference.get("type").getAsString(), item);
            assertEquals("ae2utility:item/" + item, modelReference.get("model").getAsString(), item);

            JsonObject model = json("assets/ae2utility/models/item/" + item + ".json");
            String parent = model.get("parent").getAsString();
            assertTrue(parent.equals("minecraft:item/generated") || parent.equals("item/generated"), item);
            String texture = model.getAsJsonObject("textures").get("layer0").getAsString();
            if (item.equals("recipe_finder")) {
                assertEquals("minecraft:item/compass", texture);
            } else {
                assertEquals("ae2utility:item/" + item, texture, item);
                try (var stream = getClass().getClassLoader().getResourceAsStream(
                        "assets/ae2utility/textures/item/" + item + ".png")) {
                    assertNotNull(stream, "Missing item texture: " + item);
                }
            }
        }
    }

    @Test
    void recipesUseCurrentIngredientSyntaxAndConsistentOutputs() throws IOException {
        for (String item : CRAFTABLE_ITEMS) {
            JsonObject recipe = json("data/ae2utility/recipe/" + item + ".json");
            String type = recipe.get("type").getAsString();
            Iterable<com.google.gson.JsonElement> ingredients;
            if (item.equals("nbt_tear_card")) {
                assertEquals("minecraft:crafting_shapeless", type, item);
                var entries = recipe.getAsJsonArray("ingredients");
                assertFalse(entries.isEmpty(), item);
                ingredients = entries;
            } else {
                assertEquals("minecraft:crafting_shaped", type, item);
                var keys = recipe.getAsJsonObject("key");
                assertFalse(keys.entrySet().isEmpty(), item);
                var pattern = recipe.getAsJsonArray("pattern");
                assertFalse(pattern.isEmpty(), item);
                for (var row : pattern) {
                    String symbols = row.getAsString();
                    assertTrue(symbols.length() >= 1 && symbols.length() <= 3, item);
                    for (char symbol : symbols.toCharArray()) {
                        assertTrue(symbol == ' ' || keys.has(String.valueOf(symbol)),
                                "Undefined pattern symbol in " + item + ": " + symbol);
                    }
                }
                ingredients = keys.entrySet().stream().map(java.util.Map.Entry::getValue).toList();
            }
            for (var ingredient : ingredients) {
                assertTrue(ingredient.isJsonPrimitive() && ingredient.getAsJsonPrimitive().isString(),
                        "Ingredients must be identifier/tag strings, not legacy item objects: " + item);
                assertTrue(ingredient.getAsString().contains(":"), item);
            }
            JsonObject result = recipe.getAsJsonObject("result");
            assertEquals("ae2utility:" + item, result.get("id").getAsString(), item);
            assertTrue(result.get("count").getAsInt() > 0, item);
        }
    }

    private JsonObject json(String path) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "Missing packaged resource: " + path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}

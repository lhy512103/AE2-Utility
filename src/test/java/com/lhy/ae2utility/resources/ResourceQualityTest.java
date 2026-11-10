package com.lhy.ae2utility.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;

/** File-based checks intentionally discover resources instead of snapshotting removed features. */
class ResourceQualityTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static final Path ASSETS = ROOT.resolve("assets/ae2utility");
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    @Test
    void allJsonIsStrictAndHasNoDuplicateObjectKeys() throws IOException {
        for (Path file : files(ROOT, ".json")) {
            try (var reader = new JsonReader(new StringReader(Files.readString(file)))) {
                reader.setLenient(false);
                validateUniqueKeys(reader, file.toString());
                assertEquals(com.google.gson.stream.JsonToken.END_DOCUMENT, reader.peek(), file.toString());
            }
        }
    }

    @Test
    void translationsAreNonblankAndStaticJavaAndGuiKeysExistInBothLanguages() throws IOException {
        JsonObject english = json(ASSETS.resolve("lang/en_us.json"));
        JsonObject chinese = json(ASSETS.resolve("lang/zh_cn.json"));
        assertEquals(english.keySet(), chinese.keySet());
        for (String key : english.keySet()) {
            assertFalse(english.get(key).getAsString().isBlank(), key + " en_us");
            assertFalse(chinese.get(key).getAsString().isBlank(), key + " zh_cn");
        }
        Pattern translations = Pattern.compile("(?:translatable|translationKey)\\(\\s*\"([^\"]+)\"");
        for (Path source : files(Path.of("src/main/java"), ".java")) {
            var matcher = translations.matcher(Files.readString(source));
            while (matcher.find()) {
                String key = matcher.group(1);
                if (key.contains(".ae2utility.") && !key.endsWith(".")) {
                    assertTrue(english.has(key), source + ": missing translation " + key);
                }
            }
        }
        for (Path gui : files(ASSETS.resolve("gui"), ".json")) {
            validateGui(json(gui), english, gui);
        }
    }

    @Test
    void modelsResolveLocalParentsAndTextures() throws IOException {
        for (Path model : files(ASSETS.resolve("models"), ".json")) {
            JsonObject data = json(model);
            if (data.has("parent")) {
                requireLocal(data.get("parent").getAsString(), "models/", ".json", model);
            }
            if (data.has("textures")) {
                for (var texture : data.getAsJsonObject("textures").entrySet()) {
                    String value = texture.getValue().getAsString();
                    if (!value.startsWith("#")) requireLocal(value, "textures/", ".png", model);
                }
            }
        }
    }

    @Test
    void craftingRecipesHaveValidSymbolsIngredientsAndLocalResults() throws IOException {
        Path data = ROOT.resolve("data/ae2utility");
        assertTrue(files(data.resolve("recipe"), ".json").isEmpty(),
                "Forge 1.20.1 loads recipes/, not ignored JSON in the 1.21 recipe/ directory");
        for (Path recipe : files(data.resolve("recipes"), ".json")) {
            JsonObject value = json(recipe);
            String type = value.get("type").getAsString();
            assertTrue(ID.matcher(type).matches(), recipe.toString());
            if (!type.equals("minecraft:crafting_shaped") && !type.equals("minecraft:crafting_shapeless")) continue;
            JsonObject result = value.getAsJsonObject("result");
            String item = result.get("item").getAsString();
            assertTrue(ID.matcher(item).matches(), recipe + " result");
            if (result.has("count")) assertTrue(result.get("count").getAsInt() > 0, recipe.toString());
            requireLocal(item, "models/item/", ".json", recipe);
            if (type.equals("minecraft:crafting_shaped")) {
                var rows = value.getAsJsonArray("pattern");
                assertTrue(rows.size() >= 1 && rows.size() <= 3, recipe.toString());
                int width = rows.get(0).getAsString().length();
                assertTrue(width >= 1 && width <= 3, recipe.toString());
                Set<String> used = new HashSet<>();
                for (var row : rows) {
                    assertEquals(width, row.getAsString().length(), recipe.toString());
                    row.getAsString().chars().filter(c -> c != ' ').forEach(c -> used.add(String.valueOf((char) c)));
                }
                JsonObject keys = value.getAsJsonObject("key");
                assertEquals(used, keys.keySet(), recipe + " unused or undefined symbol");
                for (var key : keys.entrySet()) {
                    assertEquals(1, key.getKey().length(), recipe.toString());
                    ingredient(key.getValue(), recipe);
                }
            } else {
                var ingredients = value.getAsJsonArray("ingredients");
                assertTrue(ingredients.size() >= 1 && ingredients.size() <= 9, recipe.toString());
                for (var ingredient : ingredients) ingredient(ingredient, recipe);
            }
        }
    }

    @Test
    void soundFilesAreRegisteredAndLocalSoundDefinitionsResolve() throws IOException {
        Path definitions = ASSETS.resolve("sounds.json");
        Set<String> referenced = new HashSet<>();
        if (Files.exists(definitions)) {
            for (var event : json(definitions).entrySet()) {
                assertFalse(event.getKey().isBlank());
                for (var sound : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                    String name = sound.isJsonPrimitive() ? sound.getAsString() : sound.getAsJsonObject().get("name").getAsString();
                    String type = sound.isJsonObject() && sound.getAsJsonObject().has("type")
                            ? sound.getAsJsonObject().get("type").getAsString() : "file";
                    if (type.equals("event")) continue;
                    requireLocal(name.contains(":") ? name : "ae2utility:" + name, "sounds/", ".ogg", definitions);
                    if (!name.contains(":") || name.startsWith("ae2utility:")) {
                        referenced.add(name.substring(name.indexOf(':') + 1));
                    }
                }
            }
        }
        for (Path sound : files(ASSETS.resolve("sounds"), ".ogg")) {
            String id = ASSETS.resolve("sounds").relativize(sound).toString().replace('\\', '/').replaceFirst("\\.ogg$", "");
            assertTrue(referenced.contains(id), "Unregistered sound file: " + sound);
        }
    }

    @Test
    void staticLocalGuiTextureReferencesExist() throws IOException {
        Pattern references = Pattern.compile("\"ae2utility:(textures/gui/[^\"]+\\.png)\"");
        for (Path source : files(Path.of("src/main/java"), ".java")) {
            var matcher = references.matcher(Files.readString(source));
            while (matcher.find()) assertTrue(Files.isRegularFile(ASSETS.resolve(matcher.group(1))),
                    source + ": missing GUI texture " + matcher.group(1));
        }
    }

    private static void validateGui(JsonElement value, JsonObject translations, Path file) {
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(child -> validateGui(child, translations, file));
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("translationKey")) {
                    assertTrue(translations.has(entry.getValue().getAsString()), file + ": " + entry.getValue());
                } else if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()
                        && Set.of("texture", "backgroundTexture").contains(entry.getKey())) {
                    requireLocal(entry.getValue().getAsString(), "", "", file);
                }
                validateGui(entry.getValue(), translations, file);
            }
        }
    }

    private static void ingredient(JsonElement value, Path file) {
        if (value.isJsonArray()) {
            assertFalse(value.getAsJsonArray().isEmpty(), file.toString());
            value.getAsJsonArray().forEach(child -> ingredient(child, file));
        } else {
            JsonObject ingredient = value.getAsJsonObject();
            assertTrue(ingredient.has("item") ^ ingredient.has("tag"), file.toString());
            String id = ingredient.get(ingredient.has("item") ? "item" : "tag").getAsString();
            assertTrue(ID.matcher(id).matches(), file + ": " + id);
        }
    }

    private static void requireLocal(String id, String prefix, String suffix, Path source) {
        if (!id.contains(":")) id = "minecraft:" + id;
        assertTrue(ID.matcher(id).matches(), source + ": invalid resource id " + id);
        if (!id.startsWith("ae2utility:")) return; // dependencies are not this pack's inventory
        Path target = ASSETS.resolve(prefix + id.substring(id.indexOf(':') + 1) + suffix).normalize();
        assertTrue(target.startsWith(ASSETS.normalize()), source + ": escaping path " + id);
        assertTrue(Files.isRegularFile(target), source + ": missing " + target);
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static List<Path> files(Path root, String suffix) throws IOException {
        if (!Files.exists(root)) return List.of();
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).sorted().toList();
        }
    }

    private static void validateUniqueKeys(JsonReader reader, String file) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> keys = new HashSet<>();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    assertTrue(keys.add(key), file + ": duplicate JSON key " + key);
                    validateUniqueKeys(reader, file);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) validateUniqueKeys(reader, file);
                reader.endArray();
            }
            default -> reader.skipValue();
        }
    }
}

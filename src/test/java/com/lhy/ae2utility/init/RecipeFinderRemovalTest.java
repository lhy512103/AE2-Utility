package com.lhy.ae2utility.init;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeFinderRemovalTest {
    @Test
    void onlyTheRetiredFinderRegistryEntryIsMigrated() {
        assertTrue(RetiredItemMappings.isRetiredFinder("ae2utility:recipe_finder"));
        assertFalse(RetiredItemMappings.isRetiredFinder("other:recipe_finder"));
        assertFalse(RetiredItemMappings.isRetiredFinder("ae2utility:nbt_tear_card"));
        assertFalse(RetiredItemMappings.isRetiredFinder(null));
    }

    @Test
    void removedGameplayClassesCannotLoad() {
        for (String name : List.of("item.RecipeFinderItem", "menu.RecipeFinderMenu",
                "client.RecipeFinderScreen", "client.RecipeFinderClientState",
                "client.recipe_finder.RecipeFinderJeiIndexer", "network.RecipeFinderEncodePacket",
                "network.RecipeFinderSamplePacket", "jei.RecipeFinderGhostHandler")) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("com.lhy.ae2utility." + name, false, getClass().getClassLoader()), name);
        }
    }

    @Test
    void finderIsNotRegisteredOrCraftable() throws Exception {
        for (String name : List.of("ModItems", "ModMenus")) {
            assertFalse(Files.readString(Path.of("src/main/java/com/lhy/ae2utility/init", name + ".java"))
                    .contains("RECIPE_FINDER"));
        }
        assertFalse(Files.exists(Path.of("src/main/resources/assets/ae2utility/models/item/recipe_finder.json")));
        assertFalse(Files.exists(Path.of("src/main/resources/data/ae2utility/recipes/recipe_finder.json")));
    }
}

# 26.1.2 migration validation

## Target

- Minecraft 26.1.2; NeoForge 26.1.2.126; Java 25.
- All original implementation files are in the active main source set; the obsolete isolated source copy has been removed.
- AE2 source verified at `v26.1.13-beta` (`c7c1812d337622e40a60930144dd0ba506edf813`).
- JEI 29.53.0.115 official sources and exact optional-mod release binaries were used to verify signatures.
- Mixin configuration is required, with default injection requirement 1; optional absent targets use `@Pseudo`, not zero-required injections.

## Completed checks

1. Full Gradle build: main compilation, test compilation, JAR and sources JAR.
2. 45 tests in 14 suites passed, none skipped (13 original suites plus resource consistency tests).
3. Core client reached the welcome screen and shut down normally (exit 0).
4. Core dedicated server loaded recipes/world and reached `Done`.
5. Compatibility client with ExtendedAE/EAEP/AdvancedAE/AE2CS/AE2LT/PF-EMI/Mekanism ports/Sophisticated Core loaded and exited normally.
6. Compatibility dedicated server with the same set reached `Done`.
7. NeoEco/LDLib2 combination, separately from incompatible AE2LT, reached `Done`.
8. Actual base, extended, advanced, meteorite, resonating and overloaded pattern provider placement succeeded.
9. Base, extended, advanced, meteorite and resonating providers retained utility-card NBT through serialization; base/advanced/meteorite/resonating cards were read back after server restart.
10. Base provider redstone output was asserted in-game with an adjacent redstone wire at power 15 (`AE2UTILITY_REDSTONE_PASS`).
11. A client connected to the compatibility server; the NBT Tear Card and Redstone Signal Card menus opened and rendered.

## Scope update

Recipe Finder was removed at the user's request, including its item, menu, indexing/classification API, dedicated packets and resources; JEI/EMI encoding and uploads remain.

## Issues discovered during runtime validation

- NeoEco 26.2.15-beta requires AE2LT >=1.1.3 if present, whereas the verified target AE2LT release is 1.0.1alpha. The development profiles test these separately rather than overriding the dependency restriction.
- The optional upstream ports emit their own recipe/tag/OnlyIn warnings; no AE2 Utility injection failure was observed in the completed startup runs. Such warnings are not claimed as fixed by this port.
- Some Useless Things has no verified target release in the checked sources; its optional reflective bridge is retained but cannot be represented as tested with a target release.

## Remaining functional coverage

Real crafting requests, NBT fuzzy matching under automation, each redstone mode through an entire crafting cycle, all recipe-viewer transfer and upload paths, and a broad modpack regression need additional checks beyond the tests above. Do not describe startup validation as proof of every gameplay combination.

## Reproducibility

Use `./gradlew build`, core `runClient`/`runServer`, and `-PcompatibilityRuntime` for optional mods. Add `-PecoRuntime` to select NeoEco instead of AE2LT. The test server used during migration was bound to localhost; its world, credentials, logs and downloaded reference sources are ignored and not distributed.

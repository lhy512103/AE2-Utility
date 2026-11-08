# AE2: Utility

AE2: Utility is a **Minecraft 1.20.1 Forge** helper mod for Applied Energistics 2.
It adds fast JEI-driven pattern encoding and ExtendedAE Plus upload workflows
without adding work to JEI's per-frame render path.

## Features

- Encode the currently displayed JEI recipe into an AE2 pattern.
- Hold Shift while encoding to upload through ExtendedAE Plus providers or an assembly matrix.
- Batch encode or upload every recipe on the current JEI page or in the current category.
- Enable item and fluid substitutions directly from JEI's expandable recipe options.
- Highlight craftable outputs and reusable/craftable recipe inputs from the ME network.
- Cancel the remainder of a batch upload after cancelling provider selection.
- Convert encoded patterns back to blank patterns with Ctrl+Shift right-click.
- Use supported wireless encoding terminals from the inventory or Curios slots without opening a terminal.
- Configure terminal requirements from Forge's in-game Mods configuration screen.
- Map Advanced AE Reaction Chamber recipes to matching providers during upload.
- Preview the encoded pattern and pin its actual ingredients while hovering the JEI/EMI encode arrow.
- Find and select recipes with the recipe finder, then batch encode or upload the selection.
- Pull ingredients from ME storage into supported machines or the player inventory.
- Use NBT Tear Cards and configurable redstone signal cards in supported pattern providers.
- Transfer container items, fluids and Mekanism chemicals with the ME Quick Transfer Tool.
- Integrate through the version 1 [Java API](docs/API.md), including custom machine profiles and recipe classifiers.

## Requirements

| Dependency | Version | Required |
| --- | --- | --- |
| Minecraft | 1.20.1 | Yes |
| Forge | 47.x | Yes |
| Applied Energistics 2 | 15.4.10 or newer | Yes |
| Java | 17 | Yes |
| Just Enough Items | 15.74.0.234 or newer, below 16 | For JEI features |
| ExtendedAE Plus | 1.6.4, below 1.7 | For provider/matrix uploads |
| AE2 Lightning Tech | 2.1.4 Forge 1.20.1, below 2.2 | Only for its provider integration |
| Neo ECO AE Extension | 20.5.5, below 20.6 | Only for its crafting CPU integration |

AE2WTLib, Wireless Comprehensive Work Terminal, Curios, Advanced AE, Mekanism,
and Applied Mekanistics are optional compatibility integrations.

## Installation

1. Install Forge for Minecraft 1.20.1.
2. Install Applied Energistics 2 and any optional integrations you use.
3. Place `ae2utility-1.20.1.2.jar` in the Minecraft `mods` directory.

## Configuration

Open **Mods > AE2 Utility > Config** in-game. The configuration file is also
available in the standard Forge `config` directory.

## Build From Source

```bash
./gradlew build
```

The release jar is generated in `build/libs`. The project uses Java 17 and
ForgeGradle 6.

The 1.20.1.2 release ports the 1.21.1 project's feature set through upstream 1.8.0.
See [migration notes](docs/MIGRATION-1.21.1.md) for platform differences and validation.

Run `./gradlew test` for regression tests, or `./gradlew runClient -PmigrationSmokeTest`
for an automatic title-screen and lazy Mixin target check. The smoke run exits without opening a world.
The smoke run uses the full normal client dependency set, including Applied Create; missing Jade integration labels are supplied by this mod.
See [dependency audit](docs/DEPENDENCY-AUDIT.md) for exact release pins and [the refactoring plan](docs/REFACTORING-PLAN.md) for staged maintenance work.
Back up worlds before updating addon storage networks, and update both clients and servers together for release 1.20.1.2.

## Branches

- `forge-1.20.1`: Minecraft 1.20.1 Forge implementation.

## Links

- Source: https://github.com/lhy512103/Create-Package/tree/forge-1.20.1
- Issues: https://github.com/lhy512103/Create-Package/issues
- Changelog: [CHANGELOG.md](CHANGELOG.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)

## License

AE2: Utility is licensed under the [MIT License](LICENSE).

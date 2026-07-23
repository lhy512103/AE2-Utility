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

## Requirements

| Dependency | Version | Required |
| --- | --- | --- |
| Minecraft | 1.20.1 | Yes |
| Forge | 47.x | Yes |
| Applied Energistics 2 | 15.4.10 or newer | Yes |
| Java | 17 | Yes |
| Just Enough Items | 15.20.0 or newer | For JEI features |
| ExtendedAE Plus | 1.5.4 or newer | For provider/matrix uploads |

AE2WTLib, Wireless Comprehensive Work Terminal, Curios, Advanced AE, Mekanism,
and Applied Mekanistics are optional compatibility integrations.

## Installation

1. Install Forge for Minecraft 1.20.1.
2. Install Applied Energistics 2 and any optional integrations you use.
3. Place `ae2utility-20.1.0.jar` in the Minecraft `mods` directory.

## Configuration

Open **Mods > AE2 Utility > Config** in-game. The configuration file is also
available in the standard Forge `config` directory.

## Build From Source

```bash
./gradlew build
```

The release jar is generated in `build/libs`. The project uses Java 17 and
ForgeGradle 6.

## Branches

- `forge-1.20.1`: Minecraft 1.20.1 Forge implementation.

## Links

- Source: https://github.com/lhy512103/Create-Package/tree/forge-1.20.1
- Issues: https://github.com/lhy512103/Create-Package/issues
- Changelog: [CHANGELOG.md](CHANGELOG.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md)

## License

AE2: Utility is licensed under the [MIT License](LICENSE).

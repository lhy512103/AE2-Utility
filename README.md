# AE2: Utility

AE2: Utility is a Forge 1.20.1 helper mod for Applied Energistics 2. It focuses on JEI-driven pattern encoding and ExtendedAE Plus upload workflows, reducing the steps needed to create and place patterns from recipe pages.

## Features

- Adds a JEI recipe-page encode button for creating AE2 encoded patterns.
- Supports Shift-click upload through ExtendedAE Plus provider and assembly matrix workflows.
- Highlights recipe inputs and outputs based on existing ME autocrafting state.
- Works with open AE2/WCWT terminals or supported wireless encoding terminals.
- Supports AE2 pattern substitution options when encoding from JEI.

## Optional Integrations

- Just Enough Items: recipe-page encode button and previews.
- ExtendedAE Plus: provider selection and assembly matrix upload.
- Wireless Comprehensive Work Terminal: terminal context support.
- Mekanism and Applied Mekanistics: recipe ingredient handling compatibility.
- Curios: wireless terminal lookup support.

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Applied Energistics 2 15.4.10 or newer

JEI and ExtendedAE Plus are optional, but most user-facing features are designed around them.

## Development

Build the project with:

```powershell
.\gradlew.bat build
```

Compile only:

```powershell
.\gradlew.bat compileJava
```

The project uses Java 17 and ForgeGradle 6.

## License

This project is licensed under the MIT License. See [LICENSE.txt](LICENSE.txt).

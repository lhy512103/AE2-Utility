# Contributing

Thank you for helping improve AE2: Utility.

## Reporting Issues

Include the Minecraft version, Forge version, AE2 Utility version, relevant mod
versions, reproduction steps, and the related part of `latest.log` or a crash report.

Report issues at:
https://github.com/lhy512103/Create-Package/issues

## Pull Requests

1. Fork the repository and base Forge 1.20.1 changes on the `forge-1.20.1` branch.
2. Keep each pull request focused on one behavior or compatibility change.
3. Follow [the development rules](AGENTS.md) and [the staged refactoring plan](docs/REFACTORING-PLAN.md).
4. Update `CHANGELOG.md` only for user-visible changes, using short numbered Chinese and English entries (`新增：` / `Added:`, `修复：` / `Fixed:`, `移除：` / `Removed:`, `优化：` / `Improved:`).
5. Run `./gradlew build` with Java 17 and relevant runtime checks before committing each independent feature.
6. Inspect the current local branch before committing; create a local branch if none is checked out, and keep commits focused.
7. Use repository-local author/committer `lhy512103 <lhy512103@gmail.com>` for this project's local maintenance; do not push without explicit permission.

## License

Contributions are accepted under the repository's MIT License. Clearly identify
third-party code and confirm that its license is compatible before including it.

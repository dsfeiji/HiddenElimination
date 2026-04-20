# Repository Guidelines

## Project Structure & Module Organization
This repository is a Paper plugin built with Maven and Java 21.

- `src/main/java/com/hiddenelimination/`: plugin source code.
- `src/main/java/com/hiddenelimination/manager/`: core game flow (`GameManager`, `ConditionManager`, `SpawnManager`, `UIManager`).
- `src/main/java/com/hiddenelimination/listener/`: Bukkit event listeners.
- `src/main/java/com/hiddenelimination/command/`: `/he` command handling.
- `src/main/resources/`: runtime config and metadata (`config.yml`, `plugin.yml`).
- `src/test/java/`: test sources (currently empty).
- `target/`: build output (do not edit or commit generated artifacts).

## Build, Test, and Development Commands
- `mvn clean package`: compile and package the plugin JAR.
- `mvn clean package -DskipTests`: fast packaging for local deploy checks.
- `mvn test`: run unit tests under `src/test/java`.

Artifact output: `target/hidden-elimination-1.0.0-SNAPSHOT.jar`.

## Coding Style & Naming Conventions
- Use 4-space indentation and UTF-8 source files.
- Follow Java conventions: `PascalCase` for classes/enums, `camelCase` for methods/fields, `UPPER_SNAKE_CASE` for constants.
- Keep package names lowercase under `com.hiddenelimination.*`.
- Prefer small, focused managers/listeners; avoid mixing command, UI, and world logic in one class.
- Keep config keys stable and descriptive (for example `world-border.start-size`).

## Testing Guidelines
- Place tests in `src/test/java` mirroring production package structure.
- Name test classes `*Test` (example: `GameManagerTest`).
- Cover game-state transitions, elimination rules, and config-driven branches.
- Run `mvn test` before opening a PR; include reproduction steps for in-game behavior that is hard to unit test.

## Commit & Pull Request Guidelines
Git history is not available in this workspace snapshot, so use a clear conventional format:

- Commit message style: `type(scope): summary` (example: `fix(game): prevent border reset during WAITING`).
- Keep commits focused and atomic.
- PRs should include: what changed, why, affected commands/config keys, and test evidence (`mvn test` output or manual Paper server checks).
- For gameplay/UI changes, include screenshots or short clips plus updated `config.yml` examples when relevant.

## Configuration & Deployment Notes
- Validate `plugin.yml` command/permission entries when adding features.
- Keep environment assumptions explicit: Paper `1.21.x`, Java `21`.
- Use `DEPLOYMENT.md` as the source of truth for server deployment steps.

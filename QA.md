# Verification report — 2026-11-05

## Completed in this environment

- Java 21 compiler successfully parsed **all 12 Java source files**: no syntax errors.
- Java 21 compiled **the four pure-Java model/utility classes** successfully.
- 10 assertions passed covering compound and invalid durations, IPv4 input, hostname rejection, punishment expiration/status, and configurable message placeholders.
- YAML parser successfully loaded `config.yml`, `menu.yml`, and `plugin.yml`.
- Paper's official documentation was checked for 26.3 API version, dependency format, and Java 25 requirement.

## Pending (do not represent as completed)

- Resolve dependencies, compile the full project with Java 25 and Gradle 9.3+.
- Execute the JUnit suite (`gradle test`).
- Verify on a Paper 26.3 test server with multiple Minecraft clients.
- Smoke-test each storage backend independently, reconnects, offline UUID resolution, IP bans, GUI pagination, restarts, temporary expiration and command permissions.
- Record a real gameplay demonstration showing each punishment and GUI.
- Publish source to the user's GitHub repository after access is authenticated.

## Why tests are pending

The available execution environment only has Java 21 and cannot reach the Maven/Paper dependency repositories, so it cannot generate a trustworthy Java 25 shaded JAR. The included GitHub Actions workflow builds and uploads the JAR when this source is pushed to GitHub.

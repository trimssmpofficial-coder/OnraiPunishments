# OnraiPunishments

A configurable moderation and punishment plugin for **Paper 26.3** (Java **25**).

## Features

- Permanent and temporary bans and mutes
- IP bans and kicks
- Unban and unmute commands
- Offline player punishment support (except kicks)
- Configurable ban, kick and mute messages
- Paginated, configurable punishment history inventory
- History records for active, expired and revoked punishments
- SQLite, MySQL or MongoDB storage
- Staff permissions and mute-bypass permission

## Installation

1. Download the compiled JAR from the **Artifacts** section of the [successful GitHub build](https://github.com/trimssmpofficial-coder/OnraiPunishments/actions/runs/42138281846). Extract the artifact ZIP.
2. Place `OnraiPunishments-1.0.0.jar` in the Paper server's `plugins/` directory.
3. Start the server once to generate the configuration files.
4. Configure `plugins/OnraiPunishments/config.yml` and `plugins/OnraiPunishments/menu.yml` as needed, then restart.

**Requirements:** Paper 26.3 and Java 25. This version is **not compatible with Paper 1.21.11**.

SQLite works by default without an external database server. To use MySQL or MongoDB, select the database type and configure its connection settings in `config.yml`. Exactly one database backend is active at a time.

## Commands

| Command | Example | Permission |
| --- | --- | --- |
| `/ban` | `/ban Alex Griefing` | `onrai.ban` |
| `/tempban` | `/tempban Alex 7d Griefing` | `onrai.tempban` |
| `/mute` | `/mute Alex Spam` | `onrai.mute` |
| `/tempmute` | `/tempmute Alex 30m Spam` | `onrai.tempmute` |
| `/ipban` | `/ipban Alex Ban evasion` or `/ipban 192.0.2.1` | `onrai.ipban` |
| `/kick` | `/kick Alex Be respectful` | `onrai.kick` |
| `/unban` | `/unban Alex` or `/unban 192.0.2.1` | `onrai.unban` |
| `/unmute` | `/unmute Alex` | `onrai.unmute` |
| `/history` | `/history Alex` | `onrai.history` |

Supported duration suffixes are `s`, `m`, `h`, `d` and `w`; combinations such as `2d12h` are supported. Staff commands default to operators. `onrai.mutebypass` can be granted to exempt trusted staff from mute enforcement.

Punishments can be applied to offline players, provided their identity can be resolved. `/kick` requires an online target. The `/history` GUI can only be opened by an in-game player.

## Configuration

- **`config.yml`** — database selection and connection, custom messages, reasons, mute settings and IP retention options.
- **`menu.yml`** — history menu title, size, item materials, display text, item slots and pagination.

For offline name-based IP bans, the server needs a previously stored IP for that player. A literal IP address can be used directly. Database types are alternatives; switching the configured type does not automatically migrate existing records.

## Building from source

The source is built with Gradle and Java 25. Run:

```sh
gradle clean test shadowJar
```

The output is `build/libs/OnraiPunishments-1.0.0.jar`. Automated compilation, tests and packaging have passed in [GitHub Actions](https://github.com/trimssmpofficial-coder/OnraiPunishments/actions/runs/42138281846).

**Validation status:** Build and automated tests passed. Full live-server gameplay testing and a demonstration recording have not yet been completed.

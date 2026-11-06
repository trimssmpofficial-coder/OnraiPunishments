# OnraiPunishments

Paper 26.3 punishment plugin — source distribution **pending final build and live server validation**.

## Requirements

- Paper **26.3**, Java **25**, Gradle 9.3+ with network access for the dependencies.
- One storage backend: **SQLite** (default), **MySQL** or **MongoDB**. Database drivers are bundled into the shaded output JAR; the Paper API is provided by the server.
- If using an online-mode server, previously unseen offline player names are resolved using Mojang's public profile API. Server must have network access for those lookups.

## Build

```sh
gradle clean test shadowJar
```

Deploy `build/libs/OnraiPunishments-1.0.0.jar` to `plugins/`, restart, then edit:

- `plugins/OnraiPunishments/config.yml`: database credentials, custom messages, cache and IP privacy settings.
- `plugins/OnraiPunishments/menu.yml`: history title, icons, lore, pagination, materials and slots.

> This source package intentionally does not contain a prebuilt JAR because the build environment used to prepare it lacks Java 25, Gradle and network dependency resolution. Build and test before using on a live server.

## Commands

| Command | Example | Offline targets | Permission |
| --- | --- | --- | --- |
| `/ban` | `/ban Alex Griefing` | Yes | `onrai.ban` |
| `/tempban` | `/tempban Alex 7d Griefing` | Yes | `onrai.tempban` |
| `/mute` | `/mute Alex Spam` | Yes | `onrai.mute` |
| `/tempmute` | `/tempmute Alex 30m Spam` | Yes | `onrai.tempmute` |
| `/ipban` | `/ipban Alex Ban evasion` or `/ipban 192.0.2.1` | Yes, if last IP known | `onrai.ipban` |
| `/kick` | `/kick Alex Be respectful` | No | `onrai.kick` |
| `/unban` | `/unban Alex` or `/unban 192.0.2.1` | Yes | `onrai.unban` |
| `/unmute` | `/unmute Alex` | Yes | `onrai.unmute` |
| `/history` | `/history Alex` | Yes (menu is in-game only) | `onrai.history` |

Duration suffixes: `s`, `m`, `h`, `d`, `w`. Combined e.g. `2d12h`.

Console can issue punishments, except that `/history` is a GUI and requires a player.

## Data and operations

- SQL uses prepared statements + HikariCP pool, with WAL + single connection for SQLite. MongoDB uses the official pooled synchronous driver. All database writes from commands are queued on bounded I/O thread count; join verification uses the asynchronous pre-login event.
- History includes expired and manually revoked punishments; expirations are computed by timestamp at query time, so no periodic DB mutation is necessary.
- The system blocks affected users at asynchronous pre-login and prevents muted users from chatting or using configured message commands. Grant `onrai.mutebypass` to exempt trusted staff. The mute cache has a configurable TTL and is invalidated upon unmute or disconnect.
- `store-player-ips` can be disabled to minimize retention, but this limits **offline username-based IP bans**. Never-seen offline player names have no known IP; use a literal IP address if necessary.
- Storage backends are alternatives, **not** synchronized multi-backend replication. Changing `database.type` does not transfer records; migration requires an external process.
- Configure appropriate DB access controls, TLS for remote DBs, backups, and a retention policy for IP records.

## Suggested live validation checklist

1. Start the plugin and verify initialization without errors (`/plugins`).
2. Offline ban a previously seen player, then try joining: the configured ban screen must appear.
3. Tempban a second player; confirm the display shows remaining duration, then wait for automatic expiry.
4. Mute/tempmute, send chat, verify the custom message, unmute and verify chat resumes.
5. IP ban a previously seen offline player and an explicit IP. Verify matching connections are rejected.
6. Kick an online player and confirm the custom message and history record.
7. Open `/history <player>` and navigate pages with the configured GUI.
8. Restart and verify history persists. Repeat with SQLite, MySQL and MongoDB separately.
9. Test DB unavailability, missing/invalid user names, Mojang API failure, and permission denial.

## Known limits / pending QA

- Full Gradle compilation and Paper in-game validation are **not completed** in this development environment.
- The plugin currently targets a **single Paper server**, not automatic cross-server pub/sub cache invalidation.
- Login checks assume Paper's normal proxy/IP forwarding is properly configured.
- Host permissions and server side chat plugins may require additional integration if they override Paper's chat event.
- `/history` uses known identities from stored profiles or prior punishments; players without a matching profile or punishment have no stored history.
- GitHub publication and real gameplay video were **not performed**; both require external authenticated access and/or running a Paper 26.3 server.

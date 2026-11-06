# Live gameplay demonstration — recording checklist

A real Paper 26.3 server with Java 25 is needed to record this honestly.

Recommended 60–90 second clip:

1. Show Paper 26.3 `/version`, plugin loaded in `/plugins`, and `/history` GUI (5 seconds).
2. Issue `/tempban TestUser 30s Testing`, reconnect as TestUser, show custom ban screen (15 seconds).
3. Issue `/unban TestUser`; reconnect; `/tempmute TestUser 30s Testing`, send chat, show custom mute notification (15 seconds).
4. `/unmute TestUser`; send chat successfully (5 seconds).
5. `/kick TestUser Demonstrating kicks`, display kick screen (8 seconds).
6. Show `/history TestUser`, inspect icons/status/reason and page navigation (15 seconds).
7. Show config lines for SQLite/MySQL/MongoDB backend selection (7 seconds).

Keep account/IP data, passwords and database URIs out of the recording. If you cannot test every backend in video, describe only the backend actually demonstrated.

Shortens the time the server stops for the snapshots of Flashback chunk recordings, when there are many entities. Your `config.json` does not have the new key, so the mod logs a warning and uses its default until you add it.

- Add `optimize_snapshot`, on by default. For each chunk of a snapshot, Server Replay went through every entity of the world; the entities are now sorted into their chunks once per snapshot. The recorded packets stay the same

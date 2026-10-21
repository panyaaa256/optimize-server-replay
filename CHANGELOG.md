Saves chunk recordings faster, and recovers the recordings that a crash cut short. Your `config.json` does not have the new keys, so each of them logs a warning and uses its default until you add it.

### Added
- Recover a Flashback chunk recording that the server stopped in the middle of, on the next start, up to its last complete replay chunk ([#1](https://github.com/panyaaa256/optimize-server-replay/pull/1))
- Add `optimize_saving`, on by default, which saves Flashback chunk recordings faster. The unzipped replay is the same as with Server Replay alone, apart from `metadata.json.old`, which is left out ([#2](https://github.com/panyaaa256/optimize-server-replay/pull/2))
- Add `zip_compression_level`, the zip compression level a recording is saved with, from 0 to 9. The default `1` is what Server Replay uses ([#2](https://github.com/panyaaa256/optimize-server-replay/pull/2))
- Add `zip_while_recording`, off by default, which zips a recording while it records. Saving is then over quickly, and the recording takes about as much disk space as its zip. A recording that a crash cut short then needs this mod to be recovered ([#3](https://github.com/panyaaa256/optimize-server-replay/pull/3))

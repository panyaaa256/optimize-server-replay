# Optimize Server Replay

[日本語](README.ja.md)

A Fabric mod that reduces the load of recording with [Server Replay](https://github.com/senseiwells/ServerReplay), by adding filters for the packets that get recorded.

> [!NOTE]
> This mod is at the very start of its development. It builds, but no filter is implemented yet, so installing it does not change anything.

## Requirements

- Minecraft 1.21.11
- [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21 or newer
- [Server Replay](https://github.com/senseiwells/ServerReplay) for Minecraft 1.21.11

Optimize Server Replay is meant to be installed on the server, together with Server Replay.

## Building from source

You need JDK 25 to run the build (the mod itself is compiled for Java 21).

```sh
./gradlew build
```

On Windows, use `gradlew.bat build` instead of `./gradlew build`.

The mod is built to `build/libs/optimize-server-replay-<version>.jar` (the file ending in `-sources.jar` is not the mod).

## License

[MIT](LICENSE)

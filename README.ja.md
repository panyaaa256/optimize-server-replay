# Optimize Server Replay

[English](README.md)

[Server Replay](https://github.com/senseiwells/ServerReplay) の録画の負荷を、記録するパケットのフィルターを追加することで減らす Fabric mod です。

> [!NOTE]
> この mod は開発を始めたばかりです。ビルドはできますが、フィルターはまだ実装していないので、導入しても何も変わりません。

## 必要なもの

- Minecraft 1.21.11
- [Fabric Loader](https://fabricmc.net/use/) 0.19.5 以降
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java 21 以降
- Minecraft 1.21.11 用の [Server Replay](https://github.com/senseiwells/ServerReplay)

Optimize Server Replay は、Server Replay と一緒にサーバーへ導入して使う mod です。

## ソースからのビルド

ビルドの実行には JDK 25 が必要です（mod 自体は Java 21 向けにコンパイルされます）。

```sh
./gradlew build
```

Windows では `./gradlew build` の代わりに `gradlew.bat build` を使ってください。

mod は `build/libs/optimize-server-replay-<バージョン>.jar` にビルドされます（`-sources.jar` で終わるファイルは mod ではありません）。

## ライセンス

[MIT](LICENSE)

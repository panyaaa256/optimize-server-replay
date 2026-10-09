# Optimize Server Replay

[English](README.md)

[Server Replay](https://github.com/senseiwells/ServerReplay) のチャンク録画から指定したパケットを省くことで、様々な場面での負荷を減らす Server Replay の addon  です。

## 削ることのできるパケットの種類

- エンティティに関するパケット（残すエンティティを指定可）
- Block Action（ピストンの伸縮、チェストの開閉など）
- 指定した tick の間のブロック変化パケットをひとまとめに（間隔は調整可）

どの機能も、初期状態では無効です。使うものを[設定](#設定)で有効にしてください。

対象は **Flashback 形式のチャンク録画だけ** です。ReplayMod 形式のチャンク録画と、プレイヤー録画（`/replay start players`）では機能しません。

## 前提

- Minecraft 1.21.11、26.1、26.2、26.3 のいずれかと、そのバージョン用にビルドした本 mod の jar
- [Fabric Loader](https://fabricmc.net/use/) 0.19.5 以降
- Java 21 以降（26.1 以降は Java 25 以降）
- 同じ Minecraft バージョン用の [Server Replay](https://modrinth.com/mod/server-replay)（下表のバージョン以降）
- Server Replay が必要とする [Fabric API](https://modrinth.com/mod/fabric-api) と [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

| Minecraft | Server Replay |
|---|---|
| 1.21.11 | 3.3.1 |
| 26.1 | 3.4.3 |
| 26.2 | 3.5.2 |
| 26.3 | 3.6.0-beta.3 |

Optimize Server Replay は、Server Replay と共にサーバーに導入して使う mod です。クライアント側に入れる必要はありません。

## 設定

設定ファイルは `config/optimize-server-replay/config.json` です。ファイルが存在しなければ、サーバーの起動時に初期値で生成されます。

```json
{
  "ignore_entities": false,
  "entity_whitelist": [
    "minecraft:glow_item_frame",
    "minecraft:hopper_minecart",
    "minecraft:item_frame",
    "minecraft:minecart",
    "minecraft:player"
  ],
  "ignore_block_action": false,
  "block_update_interval_ticks": 0
}
```

| キー | 型 | 初期値 | 説明 |
|---|---|---|---|
| `ignore_entities` | true / false | `false` | エンティティのパケットを記録しません |
| `entity_whitelist` | エンティティ ID の配列 | 上の 5 種類 | `ignore_entities` が `true` のときに、記録を続けるエンティティの種類です。空にすると、すべてのエンティティを記録しません。`ignore_entities` が `false` のときは使われません |
| `ignore_block_action` | true / false | `false` | Block Action をすべて記録しません |
| `block_update_interval_ticks` | 0〜6000 の整数 | `0` | ブロックの変化を、この tick 数ごとにまとめて記録します。`0` ならまとめません |

- 設定は `/replay reload` で読み直せます。読み直した設定は、そのあとに開始した録画から使用されます。録画中のものは、開始したときの設定のままです。
- 値の型が違う、キーが足りない、ファイルが JSON として読めない、といった場合は、警告をログに出して初期値を使います。設定ファイルが書き換えられることはありません。

それぞれの設定で対象になるパケットの一覧は、[docs/packets.ja.md](docs/packets.ja.md) にあります。

### ブロックの変化をまとめるとき

`block_update_interval_ticks` を 1 以上にすると、その間に何度も変わったブロックは、最後の状態だけが記録されます。値を大きくするほどファイルは小さくなり、ブロックの動きは粗くなります。

- ピストンの Block Action は、`ignore_block_action` が `false` でも記録されなくなります。ピストンの Block Action は再生側でもブロックを動かすので、まとめて記録したブロックの状態と食い違うためです。チェストの開閉や音符ブロックなど、ほかの Block Action は記録されます。
- ピストンに押されている途中のブロックは、動き終わってから記録されます。そのため、ピストンとその周りのブロックの表示が、短い間ずれることがあります。
- 明るさの更新はまとめません。Server Replay の `ignore_light_packets` を `false` にしている場合、明るさだけがブロックより先に変わることがあります。

### プレイヤーの扱い

- プレイヤーもエンティティなので、`ignore_entities` の対象です。初期値の `entity_whitelist` には `minecraft:player` が入っているので、初期値のままであればプレイヤーは映ります。リストから外すと、ワールド上の姿が記録されなくなります。
- タブリストは対象外で、常に記録されます。プレイヤーをリストから外しても、タブリストには名前が残ります。
- ブロックを掘っている途中のひび割れも対象外で、常に記録されます。

### Server Replay 側の推奨設定

Server Replay の `config/server-replay/config.json` で、次の設定を合わせて使うと効果的です。

- `"default_encoding": "flashback"`：この mod の対象は Flashback 形式だけです。
- `"ignore_sound_packets": true`：ピストンの音などを記録しません。
- `"optimize_entity_packets": false`：`ignore_entities` を使う場合のおすすめです。エンティティを省くので、Server Replay 側の最適化は要らなくなります。

## うまく動かないとき

録画を始めたときに、ログに次の警告が出ることがあります。

```
Optimize Server Replay is disabled: the installed Server Replay / Arcade versions do not match, ...
```

導入している Server Replay が、この mod の対応していないバージョンであることを表します。この場合、すべての機能が無効になり、録画は Server Replay だけのときと同じ内容で行われます。

## ソースからのビルド

ビルドの実行には JDK 25 が必要です。

```sh
./gradlew build
```

Windows では `./gradlew build` の代わりに `gradlew.bat build` を使ってください。

mod は対応する Minecraft バージョンごとに、`versions/<Minecraft バージョン>/build/libs/optimize-server-replay-<バージョン>+<Minecraft バージョン>.jar` にビルドされます。

各バージョンは [Stonecutter](https://stonecutter.kikugie.dev/) で 1 つのソースを共有しています。アクティブなバージョン（リポジトリ上では 1.21.11）以外のコードは `//?` コメントの中にあります。他のバージョンのコードを編集するときは、Gradle タスク `"Set active project to <バージョン>"` でアクティブなバージョンを切り替え、コミット前に 1.21.11 に戻してください。

## ライセンス

[MIT](LICENSE)

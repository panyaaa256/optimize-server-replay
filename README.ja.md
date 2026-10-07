# Optimize Server Replay

[English](README.md)

[Server Replay](https://github.com/senseiwells/ServerReplay) のチャンク録画から、タイムラプスには要らないパケットを省いて、録画ファイルを小さくする Fabric mod です。

## できること

- エンティティのパケットを記録しない（残すエンティティの種類は指定できます）
- Block Action（ピストンの伸縮、チェストの開閉など）を記録しない
- ブロックの変化を、数 tick ごとにまとめて記録する

どの機能も、初期状態では無効です。使うものを[設定](#設定)で有効にしてください。

対象は **Flashback 形式のチャンク録画だけ** です。ReplayMod 形式のチャンク録画と、プレイヤー録画（`/replay start players`）には一切影響しません。

## 必要なもの

- Minecraft 1.21.11
- [Fabric Loader](https://fabricmc.net/use/) 0.19.5 以降
- Java 21 以降
- Minecraft 1.21.11 用の [Server Replay](https://modrinth.com/mod/server-replay) 3.3.1 以降
- Server Replay が必要とする [Fabric API](https://modrinth.com/mod/fabric-api) と [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

Optimize Server Replay は、Server Replay と一緒にサーバーへ導入して使う mod です。クライアントに入れる必要はありません。

## 設定

設定ファイルは `config/optimize-server-replay/config.json` です。ファイルが無ければ、サーバーの起動時に初期値で作られます。

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
| `entity_whitelist` | エンティティ ID の配列 | 上の 5 種類 | `ignore_entities` が `true` のときに、記録を続けるエンティティの種類です。空にすると、エンティティをすべて記録しません。`ignore_entities` が `false` のときは使われません |
| `ignore_block_action` | true / false | `false` | Block Action をすべて記録しません |
| `block_update_interval_ticks` | 0〜6000 の整数 | `0` | ブロックの変化を、この tick 数ごとにまとめて記録します。`0` ならまとめません |

- 設定は `/replay reload` で読み直せます。読み直した設定は、そのあとに始めた録画から使われます。録画中のものは、始めたときの設定のままです。
- 値の型が違う、キーが足りない、ファイルが JSON として読めない、といった場合は、警告をログに出して初期値を使います。設定ファイルが書き換えられることはありません。

それぞれの設定で対象になるパケットの一覧は、[docs/packets.ja.md](docs/packets.ja.md) にあります。

### ブロックの変化をまとめるとき

`block_update_interval_ticks` を 1 以上にすると、その間に何度も変わったブロックは、最後の状態だけが記録されます。値を大きくするほどファイルは小さくなり、ブロックの動きは粗くなります。

- ピストンの Block Action は、`ignore_block_action` が `false` でも記録されなくなります。ピストンの Block Action は再生側でもブロックを動かすので、まとめて記録したブロックの状態と食い違うためです。チェストの開閉や音符ブロックなど、ほかの Block Action は記録されます。
- ピストンに押されている途中のブロックは、動き終わってから記録されます。そのため、ピストンとその周りのブロックの表示が、短い間ずれることがあります。
- 明るさの更新はまとめません。Server Replay の `ignore_light_packets` を `false` にしている場合、明るさだけがブロックより先に変わることがあります。

### プレイヤーの扱い

- プレイヤーもエンティティなので、`ignore_entities` の対象です。初期値の `entity_whitelist` には `minecraft:player` が入っているので、そのままならプレイヤーは映ります。リストから外すと、ワールド上の姿が記録されなくなります。
- タブリストは対象外で、常に記録されます。プレイヤーをリストから外しても、タブリストには名前が残ります。
- ブロックを掘っている途中のひび割れも対象外で、常に記録されます。

### Server Replay 側のおすすめの設定

Server Replay の `config/server-replay/config.json` で、次の設定を合わせて使うと効果的です。

- `"default_encoding": "flashback"`：この mod が対象にするのは Flashback 形式だけです。
- `"ignore_sound_packets": true`：ピストンの音などを記録しません。
- `"optimize_entity_packets": false`：`ignore_entities` を使う場合のおすすめです。エンティティを省くので、Server Replay 側の最適化は要らなくなります。

## うまく動かないとき

録画を始めたときに、ログに次の警告が出ることがあります。

```
Optimize Server Replay is disabled: the installed Server Replay / Arcade versions do not match, ...
```

導入している Server Replay が、この mod の対応していないバージョンであることを表します。この場合、すべての機能が無効になり、録画は Server Replay だけのときと同じ内容で行われます。

## ソースからのビルド

ビルドの実行には JDK 25 が必要です（mod 自体は Java 21 向けにコンパイルされます）。

```sh
./gradlew build
```

Windows では `./gradlew build` の代わりに `gradlew.bat build` を使ってください。

mod は `build/libs/optimize-server-replay-<バージョン>.jar` にビルドされます。

## ライセンス

[MIT](LICENSE)

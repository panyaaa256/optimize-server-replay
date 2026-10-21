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

- Minecraft 1.21.11
- [Fabric Loader](https://fabricmc.net/use/) 0.19.5 以降
- Java 21 以降
- Minecraft 1.21.11 用の [Server Replay](https://modrinth.com/mod/server-replay) 3.3.1 以降
- Server Replay が必要とする [Fabric API](https://modrinth.com/mod/fabric-api) と [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

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
  "block_update_interval_ticks": 0,
  "optimize_saving": true,
  "zip_compression_level": 1
}
```

| キー | 型 | 初期値 | 説明 |
|---|---|---|---|
| `ignore_entities` | true / false | `false` | エンティティのパケットを記録しません |
| `entity_whitelist` | エンティティ ID の配列 | 上の 5 種類 | `ignore_entities` が `true` のときに、記録を続けるエンティティの種類です。空にすると、すべてのエンティティを記録しません。`ignore_entities` が `false` のときは使われません |
| `ignore_block_action` | true / false | `false` | Block Action をすべて記録しません |
| `block_update_interval_ticks` | 0〜6000 の整数 | `0` | ブロックの変化を、この tick 数ごとにまとめて記録します。`0` ならまとめません |
| `optimize_saving` | true / false | `true` | 録画の保存を速くします。録画の中身は変わりません（[保存の高速化](#保存の高速化)） |
| `zip_compression_level` | 0〜9 の整数 | `1` | 録画を保存するときの zip の圧縮レベルです。大きいほどファイルは小さくなり、保存に時間がかかります。`0` なら圧縮しません。`1` は Server Replay と同じです |

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

### 保存の高速化

`optimize_saving` が `true` のとき、保存の仕方を次のように変えます。zip を展開した中身は、`metadata.json.old` が無いことを除いて、Server Replay だけのときと同じです。

- 録画を zip にまとめるときに、書き込みをまとめて行います。
- 地形のキャッシュを書くたびに、ディスクへの書き込みの完了を待たないようにします。録画のほかのファイルと同じ扱いになります。
- 録画中にだけ使う `metadata.json.old` を、zip に入れません。

## クラッシュ後の録画の復旧

Server Replay は、録画中にサーバーが落ちた録画を次の起動時に復旧しますが、対象は ReplayMod 形式だけです。この mod を入れると、Flashback 形式の録画（チャンク録画・プレイヤー録画とも）も復旧されます。

- 録画フォルダを、通常の保存と同じ `<録画名>.zip` にまとめ、元のフォルダを削除します。同じ名前の zip がすでにあるときは、`<録画名> (recovered 1).zip` のように別の名前で保存します。
- 残るのは、最後に区切られたリプレイチャンクまでです。Flashback 形式は 5 分ごとに区切ってディスクに書くため、落ちる直前の最大 5 分は失われます。
- 区切りが 1 つもない（録画開始から 5 分以内に落ちた）録画や、復旧に失敗した録画は、フォルダをそのまま残します。
- Server Replay の `"recover_unsaved_replays"` が `false` のときは、復旧しません。

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

mod は `build/libs/optimize-server-replay-<バージョン>.jar` にビルドされます。

## ライセンス

[MIT](LICENSE)

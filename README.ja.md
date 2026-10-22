# Optimize Server Replay

[English](README.md)

[Server Replay](https://github.com/senseiwells/ServerReplay) のチャンク録画から指定したパケットを省くことで、様々な場面での負荷を減らす Server Replay の addon  です。

## 削ることのできるパケットの種類

- エンティティに関するパケット（残すエンティティを指定可）
- Block Action（ピストンの伸縮、チェストの開閉など）
- 指定した tick の間のブロック変化パケットをひとまとめに（間隔は調整可）

また、録画範囲で何も変化しない時間が続いたときに、録画を一時停止できます。

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
  "zip_while_recording": false,
  "optimize_snapshot": true,
  "zip_compression_level": 1,
  "pause_when_idle_seconds": 0,
  "idle_ignore_entities": true,
  "idle_entity_whitelist": [
    "minecraft:player"
  ]
}
```

| キー | 型 | 初期値 | 説明 |
|---|---|---|---|
| `ignore_entities` | true / false | `false` | エンティティのパケットを記録しません |
| `entity_whitelist` | エンティティ ID の配列 | 上の 5 種類 | `ignore_entities` が `true` のときに、記録を続けるエンティティの種類です。空にすると、すべてのエンティティを記録しません。`ignore_entities` が `false` のときは使われません |
| `ignore_block_action` | true / false | `false` | Block Action をすべて記録しません |
| `block_update_interval_ticks` | 0〜6000 の整数 | `0` | ブロックの変化を、この tick 数ごとにまとめて記録します。`0` ならまとめません |
| `optimize_saving` | true / false | `true` | 録画の保存を速くします。録画の中身は変わりません（[保存の高速化](#保存の高速化)） |
| `zip_while_recording` | true / false | `false` | 録画しながら zip にまとめます（[録画しながらの zip 化](#録画しながらの-zip-化)） |
| `optimize_snapshot` | true / false | `true` | 録画の開始時と 5 分ごとのスナップショットで、サーバーが止まる時間を短くします。録画の中身は変わりません（[スナップショットの高速化](#スナップショットの高速化)） |
| `zip_compression_level` | 0〜9 の整数 | `1` | 録画を保存するときの zip の圧縮レベルです。大きいほどファイルは小さくなり、保存に時間がかかります。`0` なら圧縮しません。`1` は Server Replay と同じです |
| `pause_when_idle_seconds` | 0〜86400 の整数 | `0` | 録画範囲のブロックとエンティティがこの秒数変化しなかったら、録画を一時停止します。`0` なら一時停止しません（[変化がないときの一時停止](#変化がないときの一時停止)） |
| `idle_ignore_entities` | true / false | `true` | `pause_when_idle_seconds` で、エンティティの変化を数えません。`idle_entity_whitelist` の種類は数えます |
| `idle_entity_whitelist` | エンティティ ID の配列 | `["minecraft:player"]` | `idle_ignore_entities` が `true` のときでも、変化として数えるエンティティの種類です |

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

### 録画しながらの zip 化

`zip_while_recording` を `true` にすると、録画しながら、書き終わったファイルから順に zip に入れ、録画フォルダから削除します。対象は、5 分ごとのリプレイチャンクと、いっぱいになった地形のキャッシュです。

- 停止したときに残っているファイルが少ないので、保存がすぐに終わります。
- 録画中のディスク使用量が、展開した録画と zip の両方を合わせた分から、ほぼ zip の分だけになります。
- 書き途中の zip は、録画フォルダの中の `osr-partial.zip` です。保存すると `<録画名>.zip` に移ります。
- 録画中にサーバーが落ちると、録画フォルダには書き途中の zip と残りのファイルが残ります。この mod を入れたまま起動すれば[復旧](#クラッシュ後の録画の復旧)されますが、フォルダを手で zip にするだけでは開けるリプレイになりません。
- zip に入れられなくなったとき（ディスクがいっぱいなど）は、それ以降のファイルをフォルダに残し、保存のときに、書き途中の zip と合わせて 1 つにまとめます。

### スナップショットの高速化

Flashback 形式は、録画の開始時と 5 分ごとに、その時点の状態（スナップショット）を記録します。Server Replay は、そのチャンク 1 つごとに、ワールドの全エンティティを調べてそのチャンクにいるものを探すため、チャンクとエンティティが多いほど、サーバーが長く止まります。

`optimize_snapshot` が `true` のときは、最初に、エンティティを一度だけチャンクごとに分けておきます。記録されるパケットとその順番は同じです。

手元で 31×31 チャンクを録画し、エンティティを 20,000 体置いたときは、サーバーが止まる時間が次のようになりました。

- 5 分ごとのスナップショット：約 1.35 秒 → 約 0.07 秒
- 録画の開始時：約 2.7 秒 → 約 0.15 秒

### 変化がないときの一時停止

`pause_when_idle_seconds` を 1 以上にすると、録画範囲のブロックとエンティティがその秒数変化しなかったときに、録画を一時停止します。何かが変化すると、次の tick で再開します。一時停止していた時間は、リプレイでは飛ばされます。

Server Replay の `chunk_recording_strategy` を `chunk_contains_non_spectator_player` にしていても、範囲内にサバイバルのプレイヤーが放置されていると、録画は止まりません。この設定を使うと、そうした何も起きていない時間を録画から除けます。範囲内の装置が動いているあいだは、放置していても録画が続きます。

- 変化として数えるのは、ブロックの変化（Block Action、掘っている途中のひび割れ、爆発なども含む）と、エンティティの変化です。時刻、天気、タブリスト、チャット、音、パーティクル、チャンクの読み込みは数えません。一覧は [docs/packets.ja.md](docs/packets.ja.md#pause_when_idle_seconds) にあります。
- `ignore_entities` や `ignore_block_action` で録画から省いたものは数えません。
- `idle_ignore_entities` が `true`（初期値）のときは、エンティティは `idle_entity_whitelist` の種類だけを数えます。初期値ではプレイヤーだけなので、モブがうろついているだけなら一時停止します。モブトラップのようにエンティティだけで動く装置を、放置して録画したいときは、そのモブ（例：`minecraft:zombified_piglin`）をリストに足してください。
- スペクテイターのプレイヤーは、リストに関係なく数えません。
- 再開すると、Flashback 形式はスナップショットを取り直します。そのため再開のたびに、[5 分ごとのスナップショット](#スナップショットの高速化)と同じだけサーバーが止まり、地形のキャッシュも増えます。数秒のような短い値は避けて、30 秒以上にすることをおすすめします。
- 再開のきっかけになった変化そのものは記録されず、変化した後の状態が、再開時のスナップショットに入ります。ピストンの動きや音のような一瞬のものは、その分だけ欠けます。
- 時刻と天気は、一時停止していた分だけ、再開したところで一度に進みます。
- Server Replay が、プレイヤーがいない、チャンクが読み込まれていない、サーバーが無人、といった理由で一時停止しているあいだは、そちらに従います。逆に、この設定で一時停止しているあいだは、Server Replay の条件がそろっても、何かが変化するまで再開しません。
- この設定による一時停止と再開はサーバーのログに出ます。Server Replay の `pause_notify_players` による通知は出ません。

## クラッシュ後の録画の復旧

Server Replay は、録画中にサーバーが落ちた録画を次の起動時に復旧しますが、対象は ReplayMod 形式だけです。この mod を入れると、Flashback 形式の録画（チャンク録画・プレイヤー録画とも）も復旧されます。

- 録画フォルダを、通常の保存と同じ `<録画名>.zip` にまとめ、元のフォルダを削除します。同じ名前の zip がすでにあるときは、`<録画名> (recovered 1).zip` のように別の名前で保存します。
- `zip_while_recording` で録画していた場合は、書き途中の zip のうち最後まで書けているファイルと、フォルダに残っているファイルを合わせて、1 つの zip にします。
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

## 初期設定

設定ファイル（`config/optimize-server-replay/config.json`）がないときに作られる、初期設定の内容です。

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
  "zip_while_recording": false,
  "optimize_snapshot": true,
  "zip_compression_level": 1,
  "pause_when_idle_seconds": 0,
  "idle_ignore_entities": true,
  "idle_entity_whitelist": [
    "minecraft:player"
  ]
}
```

## ライセンス

[MIT](LICENSE)

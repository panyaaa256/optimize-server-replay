# Optimize Server Replay 実装計画

ServerReplay のチャンク録画（Flashback 形式）で、タイムラプス用途に不要なパケットを削減するアドオン mod。

## 1. 概要

| 項目 | 内容 |
|---|---|
| 形態 | ServerReplay のアドオン。サーバー専用の Fabric mod（Java） |
| 対象バージョン | MC 1.21.11 / ServerReplay 3.3.1+1.21.11（同梱 Arcade 0.8.1-beta.39+1.21.11） |
| 適用範囲 | **Flashback 形式のチャンク録画（`ReplayChunkRecorder`）のみ**。ReplayMod 形式のチャンク録画と、プレイヤー録画には一切適用しない |
| mod ID | `optimize-server-replay` |
| パッケージ | `com.panyaaa256.optimizeserverreplay` |

### 提供する機能

1. `ignore_entities`：エンティティ系パケットを記録しない
2. `entity_whitelist`：`ignore_entities` 有効時に、指定した種類のエンティティだけ残す
3. `ignore_block_action`：Block Action（ピストンの伸縮アニメーション等）を記録しない
4. `block_update_interval_ticks`：ブロック変化を n tick ごとにまとめる（0 で無効）。有効時はピストンの Block Action だけを自動で捨てる

爆発パケットは削減効果が小さいため（今回のリプレイで約 0.2%）対象外。

### 期待効果（2026-08-16 の録画データでの試算）

| 対象 | 録画データ（c*.flashback）に占める割合 | 削減 |
|---|---|---|
| エンティティ系 | 約 13〜21% | アイテム・TNT・モブを消すと大半が消える |
| Block Action | 約 15% | 全量 |
| ブロック変化 | 約 33% | 20 tick でまとめると約 81% 減 |

ServerReplay 側の `ignore_sound_packets: true`（約 31%、ほぼピストン音）と併用すると、録画データは約 9 割減、zip 全体は 6.4GB → 約 2.4GB の見込み（残りの大半は地形キャッシュ）。

---

## 2. 設定

ServerReplay と同じ方式（JSON をスネークケースのキーで手書き、`/replay reload` で再読み込み）にする。

### ファイル

`config/optimize-server-replay/config.json`（ファイルが無いときだけ、初期値で自動生成）

```json
{
  "ignore_entities": false,
  "entity_whitelist": [
    "minecraft:player",
    "minecraft:minecart",
    "minecraft:hopper_minecart",
    "minecraft:item_frame",
    "minecraft:glow_item_frame"
  ],
  "ignore_block_action": false,
  "block_update_interval_ticks": 0
}
```

### キー一覧

| キー | 型 | 初期値 | 説明 |
|---|---|---|---|
| `ignore_entities` | bool | `false` | エンティティ系パケットを記録しない |
| `entity_whitelist` | エンティティ ID の配列 | `["minecraft:player", "minecraft:minecart", "minecraft:hopper_minecart", "minecraft:item_frame", "minecraft:glow_item_frame"]` | `ignore_entities` が true のとき、ここに書いた種類だけ残す。空配列ならエンティティをすべて消す。`ignore_entities` が false なら無視 |
| `ignore_block_action` | bool | `false` | `ClientboundBlockEventPacket` をすべて記録しない |
| `block_update_interval_ticks` | int | `0` | 0 ならブロック変化をまとめない。1 以上なら、その tick 数ごとにまとめて書き出し、ピストンの Block Action を自動で捨てる。上限 6000（Flashback のチャンク長）に丸める |

### 挙動の詳細

- **ファイルを分ける理由**：ServerReplay は `updateConfig` で自分の `config.json` を書き戻すため、独自キーを足すと消えるおそれがある
- **再読み込み**：`ServerReplay.reload()` の末尾に mixin を当て、`/replay reload` で一緒に読み直す
- **反映タイミング**：録画開始時の設定を録画ごとに固定する（ServerReplay の `createSettings()` と同じ考え方）。再読み込みは次の録画から効く
- **読み込み**：MC 同梱の Gson を使う（Kotlin 依存を増やさない）
- **検証**：
  - 型が不正な値 → 警告ログを出して初期値
  - `block_update_interval_ticks` が負 → 0、6000 超 → 6000
  - `entity_whitelist` の未知の ID → 警告ログを出して無視
  - キーが欠けている → 警告ログを出して、そのキーだけ初期値
  - JSON 全体が読めない（構文エラー）→ 警告ログを出して、すべて初期値
  - どの場合も、ファイルは書き換えない（手で書いた内容を消さないため）
- **Block Action の自動適用**：`block_update_interval_ticks >= 1` かつ `ignore_block_action == false` のとき、録画開始時に「ピストンの Block Action を自動で無視します」と info ログを出す

| `ignore_block_action` | `block_update_interval_ticks` | ピストンの Block Action | それ以外の Block Action（チェストの開閉、音符ブロック、ベル等） |
|---|---|---|---|
| false | 0 | 記録する | 記録する |
| false | 1 以上 | 捨てる（自動適用） | 記録する |
| true | どちらでも | 捨てる | 捨てる |

ピストンだけを自動で捨てる理由：ピストンの Block Action はクライアント側でもブロックを動かすため、まとめて書き出したブロックの状態と食い違う。それ以外の Block Action は見た目の演出だけで、ブロックの状態には影響しない。

---

## 3. フック箇所

ServerReplay 3.3.1（`1.21.11` ブランチ）と、それが使う Arcade 0.8.1-beta.39 のソースで存在を確認済み。Arcade の最新 beta.48 でも、下記のメソッドは変わっていない。

| 対象 | 注入位置 | 用途 |
|---|---|---|
| `net.casual.arcade.replay.recorder.ReplayRecorder.<init>` | TAIL | 録画ごとの設定の固定、状態の初期化（Flashback 形式のチャンク録画でなければ無効化） |
| `ReplayRecorder.canRecordPacket(Packet)` | HEAD, cancellable | エンティティ選別、Block Action 破棄、ブロック変化の取り込み |
| `ReplayRecorder.tick()` | HEAD | n tick ごとの書き出し |
| `net.casual.arcade.replay.io.writer.flashback.FlashbackWriter.startNewReplayChunk()`（private） | HEAD | チャンクの区切り（スナップショット）直前の書き出し |
| `ReplayRecorder.pause(boolean)` | HEAD | 一時停止に入る直前の書き出し |
| `ReplayRecorder.stop(boolean)` | HEAD | 録画終了時の書き出し |
| `me.senseiwells.replay.ServerReplay.reload()` | TAIL | 設定の再読み込み |

### 方針

- Arcade / ServerReplay への mixin は `remap = false`、メソッドは原則として名前だけで指定する（記述子は書かない）
  - 例外：`stop` と `pause` は `@JvmOverloads` で引数なしと `boolean` 引数の 2 つがあるため、記述子付きで `stop(Z)`、`pause(Z)` に絞る
- `canRecordPacket` を選ぶ理由：`ReplayRecorder.record()` の中で
  1. `ReplayOptimizerUtils.shouldIgnorePacket()`
  2. バンドルパケットの分解（サブパケットごとに `record()` を再帰呼び出し）
  3. `writer.canRecordPacket()` && `this.canRecordPacket()`
  4. `writer.writePacket()`

  の順に処理されるため、**バンドル分解後・書き込み前**に 1 件ずつ判定できる。`ReplayChunkRecorder.canRecordPacket` のオーバーライドは `super` を呼ぶので、親クラスへの注入で効く
- 録画ごとの状態は `@Unique` フィールドとして `ReplayRecorder` に持たせる
- `ReplayChunkRecorder` 以外（プレイヤー録画）では、すべてのフックを素通りにする
- `ReplayChunkRecorder` でも、形式（`getFormat()`）が `ReplayFormat.Flashback` でなければ、すべてのフックを素通りにする。ReplayMod 形式にはチャンクの区切りがなく、テストもしないため
- フック内で例外が出たときは、ログを 1 回だけ出してパケットを素通しにする（録画を止めない）

### 呼び出し元とスレッド（ソースで確認済み）

| 経路 | 呼び出し元 | スレッド |
|---|---|---|
| ブロック変化・ライト更新 | `ChunkHolder.broadcast`（`ChunkHolder.broadcastChanges` から） | メイン |
| Block Action | `PlayerList.broadcast`（`ServerLevel.runBlockEvents` から） | メイン |
| エンティティ系 | `ChunkMap.TrackedEntity.sendToTrackingPlayers` / `addRecorder` / `removeRecorder` | メイン |
| `ReplayRecorder.tick()` | `ServerTickEvent` | メイン |
| `startNewReplayChunk()` | `FlashbackWriter.tick()`（`ReplayRecorder.tick()` の中）、`FlashbackWriter.resume()` | メイン |
| `ReplayRecorder.pause(boolean)` | `ReplayChunkRecorder.tryPauseAndBroadcast`（録画方式に応じた一時停止）、Arcade の `MinecraftServerMixin`（サーバーが無人のときの一時停止） | メイン |

- パケットのエンコードは Writer のスレッドで非同期に行われるが、記録するパケットは生成時に状態をコピーするので、書き出しで作るパケットにも問題はない

### ServerReplay / Arcade のバージョン違いへの対応

- `fabric.mod.json` では `server-replay` を `>=3.3.1` の範囲で指定する（完全一致で固定しない）
- mixin 設定は `"required": false`、`"defaultRequire": 0` にして、フック先が見つからなくても起動は止めない
- `IMixinConfigPlugin` で上記 7 箇所のフックがすべて当たったかを確認する。1 つでも当たらなければ警告ログを出し、**すべての機能を無効化**する（素通し）
  - 一部だけ当たった状態（例：取り込みはできるのに書き出しのフックがない）でブロック変化が失われるのを防ぐため、全部か無しかにする
- 確認するタイミング：mixin はクラスが初めて読み込まれるときに適用されるので、起動時には結果が分からない。`ServerReplay` は起動時に、`ReplayRecorder` と `FlashbackWriter` は最初の録画開始時に読み込まれる（`FlashbackWriter` は `ReplayRecorder` のコンストラクタの中で作られる）
  - そのため、すべての機能は既定で無効にしておく。Flashback 形式のチャンク録画の `<init>` TAIL で、7 箇所すべてが当たっていると確認できたときだけ、その録画で機能を有効にする
  - 警告ログが出るのは、起動時ではなく最初の録画開始時になる
  - `ReplayRecorder` 自体が見つからない場合は `<init>` のフックも動かないので、警告は出ずに無効のままになる。初版ではこれを許容する
  - 当たったかどうかをプラグインのどこで何を見て判断するかは、下調べ（8 章）で実際の jar に当てて決める

---

## 4. 機能 1・2：`ignore_entities` / `entity_whitelist`

### 判定フロー（`canRecordPacket` の先頭）

1. 書き出し中の自分のパケット（再入防止フラグが立っている）→ 通す
2. `ignore_entities` が false → 通す
3. パケットの種類がエンティティ系セットに含まれない → 通す
4. 対象がチャンク録画のダミープレイヤー（`ReplayChunkRecorder.getDummyPlayer().getId()`）→ 通す
5. `entity_whitelist` が空 → **記録しない**（種類の判定のみ。ID は見ない）
6. ホワイトリストあり：
   - 出現（`ClientboundAddEntityPacket`）：種類がリストにあれば、残す ID セットに追加して記録。なければ記録しない
   - 消滅（`ClientboundRemoveEntitiesPacket`）：ID が残す ID セットにあれば、セットから外して記録する。なければ記録しない（出現を記録していないエンティティの消滅は不要なため）。Arcade はこのパケットを ID 1 つずつで作るが、複数 ID の場合は、どれか 1 つでも該当すれば記録する
   - その他：ID を取り出し、残す ID セットにあれば記録。複数 ID を持つパケット（乗り物・紐・拾得）は、どれか 1 つでも該当すれば記録

#### ダミープレイヤーを常に通す理由

チャンク録画のダミープレイヤー（`-ChunkRecorder-`）は、`ClientboundAddEntityPacket` ではなく Flashback の `CreatePlayer` アクションで作られる（`FlashbackWriter.writePlayer` が出現パケットを取り除く）。そのため残す ID セットに入らず、そのままでは透明化の効果（`ClientboundUpdateMobEffectPacket`）やエンティティデータが捨てられてしまう。

#### 存在しないエンティティを参照するパケット

「どれか 1 つでも該当すれば記録」だと、除外したエンティティの ID を含むパケット（例：除外したボートに乗るプレイヤーの `SetPassengers`、除外したアイテムを拾う `TakeItemEntity`）も記録される。vanilla クライアントは未知の ID を無視するので、壊れることはない。見た目への影響は次の程度で、許容する。

- 紐の相手（`minecraft:leash_knot`）を除外すると、紐が表示されない
- 乗り物を除外すると、乗っているエンティティは乗り物なしで表示される

### エンティティ系パケットのセット

下記のクラス名は、MC 1.21.11 の jar（mojmap）にすべてあることを確認済み。ID のフィールド名・アクセサ名は未確認なので、実装時にコンパイルで確かめる。

- `ClientboundAddEntityPacket`
- `ClientboundRemoveEntitiesPacket`
- `ClientboundMoveEntityPacket.Pos` / `.Rot` / `.PosRot`
- `ClientboundEntityPositionSyncPacket`
- `ClientboundTeleportEntityPacket`
- `ClientboundSetEntityMotionPacket`
- `ClientboundSetEntityDataPacket`
- `ClientboundRotateHeadPacket`
- `ClientboundSetEquipmentPacket`
- `ClientboundUpdateAttributesPacket`
- `ClientboundEntityEventPacket`
- `ClientboundTakeItemEntityPacket`
- `ClientboundSetPassengersPacket`
- `ClientboundSetEntityLinkPacket`
- `ClientboundAnimatePacket`
- `ClientboundHurtAnimationPacket`
- `ClientboundDamageEventPacket`
- `ClientboundUpdateMobEffectPacket`
- `ClientboundRemoveMobEffectPacket`
- `ClientboundProjectilePowerPacket`

次のパケットはエンティティ ID を持つが、セットに入れない。

| パケット | 理由 |
|---|---|
| `ClientboundMoveMinecartPacket` | Arcade の `FlashbackWriter.canRecordPacket` が先に捨てる |
| `ClientboundSetCameraPacket` | Arcade の `ReplayOptimizerUtils` が先に捨てる |
| `ClientboundSoundEntityPacket` | 音として扱う。ServerReplay の `ignore_sound_packets` で消す。残っていても、クライアントは未知の ID を無視する |
| `ClientboundBlockDestructionPacket` | 掘削のひび割れ表示。常に記録する（10 章） |

セットから漏れた種類があっても、除外したエンティティのパケットが記録されるだけで、壊れることはない。

### 実装メモ

- ID のフィールドが private のもの（`ClientboundRotateHeadPacket`、`ClientboundEntityEventPacket` など）は `@Accessor` mixin で取り出す
- 残す ID セットは fastutil の `IntOpenHashSet`（ロックなし）
  - 「隠す ID」ではなく「残す ID」を持つ：額縁・トロッコ等は数十〜数百件でほぼ増減しないため、小さくキャッシュに乗る
  - エンティティ ID はサーバー起動中は増え続ける連番で再利用されないため、消滅の取りこぼしがあっても誤判定にはならない
- `record()` がメインスレッド以外から呼ばれた場合は判定せず通す（エンティティ系の経路はすべてメインスレッドなので、実際にはほぼ起きない）
- 種類の判定は `getClass()` の完全一致（入れ子クラスは個別に登録）
- 想定負荷：約 65 件/tick × 約 10〜20ns ≈ 約 1µs/tick

### 補足

- スナップショット（5 分ごと）でのエンティティ出現も `record()` 経由で `canRecordPacket` を通ることをソースで確認済み（`ReplayChunkRecorder.takeSnapshot` → `TrackedEntity.resendPackets` → `ClientboundBundlePacket` を `record()`）。そのため除外したエンティティは現れず、録画開始時からいるホワイトリスト対象も残す ID セットに入る
- Arcade の移動まとめ（`move_entities` アクション）は、`canRecordPacket` を通過した `ClientboundMoveEntityPacket` を `FlashbackWriter.writePacket` がまとめたもの。そのため除外したエンティティの移動は、まとめに入らない

---

## 5. 機能 3：`ignore_block_action`

`canRecordPacket` で `ClientboundBlockEventPacket` を次のように扱う。

| 条件 | 処理 |
|---|---|
| `ignore_block_action == true` | すべて記録しない |
| `block_update_interval_ticks >= 1` | `getBlock()` が `PistonBaseBlock`（ピストン・粘着ピストン）のものだけ記録しない |
| 上記以外 | 記録する |

---

## 6. 機能 4：`block_update_interval_ticks`

ブロック変化パケットは「座標 → 新しい状態」の絶対値の上書きなので、途中経過を捨てて最終状態だけを書けばよい（差分の導出は不要）。

### 取り込み（`canRecordPacket`、メインスレッドかつ interval >= 1 のとき）

| パケット | 処理 |
|---|---|
| `ClientboundBlockUpdatePacket` | 座標を「変化あり」として記録し、パケットは記録しない |
| `ClientboundSectionBlocksUpdatePacket` | `runUpdates` で全座標を「変化あり」として記録し、パケットは記録しない |
| `ClientboundBlockEntityDataPacket` | 座標を「ブロックエンティティ変化あり」として記録し、パケットは記録しない |
| `ClientboundLevelChunkWithLightPacket` | そのチャンク列の溜まっている分を捨てる（チャンクデータの方が新しい）。パケット自体は通す |

- メインスレッド以外から来たブロック変化は、取り込まずにそのまま通す（ブロック変化の経路はメインスレッドなので、実際にはほぼ起きない）
- `ClientboundForgetLevelChunkPacket` は Arcade の `FlashbackWriter.canRecordPacket` が先に捨てるため、このフックには届かない。チャンクの解放時は、Arcade がその時点のチャンクデータ（`ClientboundLevelChunkWithLightPacket`）を記録するので、上の行の処理で足りる
- `ClientboundLightUpdatePacket` はまとめずにそのまま通す。そのため、明るさだけがブロック変化より最大 n tick 早く変わる（20 tick なら最大 1 秒）。初版ではこれを許容し、見た目と容量を計測してから見直す

### データ構造（resize とアロケーションを避ける）

- `Long2ObjectOpenHashMap<ShortOpenHashSet>`：セクション座標（`SectionPos.asLong`）→ セクション内の変化位置
- `LongOpenHashSet`：ブロックエンティティが変化した座標
- `ShortOpenHashSet` はプールして使い回し、`clear()` して返却（配列は保持される）
- いずれも録画開始時に十分な容量で確保し、書き出しのたびに `clear()` で再利用する（毎回 `new` しない）
- ブロックの状態値は持たない（書き出し時にワールドから読む）

### 書き出し

**タイミング**

- `ReplayRecorder.tick()` の先頭で、録画内の tick カウンタが `interval` の倍数のとき
  - tick カウンタは録画ごとに持ち、一時停止中は進めない
- `FlashbackWriter.startNewReplayChunk()` の先頭（チャンクの区切りの直前）
- `ReplayRecorder.pause(boolean)` の先頭（一時停止に入る直前）
- `ReplayRecorder.stop(boolean)` の先頭（録画終了時）
- 一時停止中（`ReplayRecorder.getPaused()`）は書き出さない（Writer に捨てられるため）。すでに一時停止中に `pause` や `stop` が呼ばれたときも同じ
- メインスレッド以外から呼ばれたときは書き出さない（ワールドを読むため）

**手順**（vanilla の `ChunkHolder.broadcastChanges` と同じ考え方）

1. 再入防止フラグを立てる
2. 変化のあった各セクションについて：
   - `level.getChunkSource().getChunkNow(x, z)` で読み込み済みのチャンクだけ対象にする（未読み込みならスキップ）
   - 現在の状態が `minecraft:moving_piston` の座標を除く（下の「移動中のピストン」）。残りが 0 箇所なら、そのセクションは何も書かない
   - 変化 1 箇所 → `new ClientboundBlockUpdatePacket(level, pos)`
   - 変化 2 箇所以上 → `new ClientboundSectionBlocksUpdatePacket(sectionPos, shorts, section)`（状態はセクションから読まれる）
   - `recorder.record(packet)` で通常の経路に流す
3. ブロックの後に、ブロックエンティティが変化した各座標について `blockEntity.getUpdatePacket()` を `record()` する。チャンクが未読み込み、ブロックエンティティがもう無い、または `getUpdatePacket()` が null のときはスキップする
4. 集合を `clear()` してプールへ戻し、フラグを下ろす

### 移動中のピストン

書き出しの時点で `minecraft:moving_piston`（ピストンに押されている途中のブロック）になっている座標は、書き出さずに捨てる。次回へ持ち越すこともしない。

- 書き出さない理由：ピストンの Block Action を捨てているので、クライアントには移動中のブロックエンティティができない。`moving_piston` は単体では何も描画されないため、状態だけを書くと、その座標は次の書き出しまで消えて見える
- 持ち越さなくてよい理由：移動が終わると、vanilla がその座標にブロックを置き直してブロック変化を送り直す。これが取り込まれて、次回の書き出しに乗る
- 見た目への影響：移動中だった座標には、移動が終わった後の最初の書き出しまで、前回書き出した状態が残る。ピストン本体とヘッドの表示が、その間だけずれることがある。これは許容する

### 順序について

- `tick()` の先頭で書き出すので、そのtickの `next_tick` より前に入る
- `startNewReplayChunk()` の先頭で書き出すので、`endChunk` より前に入る（`endChunk` は Writer のスレッドに後から積まれる）
- 区切りの後のスナップショットでは、読み込み済みのチャンクはワールドから作り直した `ClientboundLevelChunkWithLightPacket` を `record()` する。未読み込みのチャンクは、解放時に記録したチャンクデータのキャッシュを参照する。どちらも最新の状態なので整合する
- 一時停止に入る直前（`pause(boolean)` の先頭）で書き出すので、一時停止中に溜まったままの分は無い。一時停止中は Arcade の `FlashbackWriter.canRecordPacket` が先に弾くため、新しく溜まることもない。そのため、一時停止中に録画を止めても変化は失われない
- 再開時は `ReplayRecorder.resume()` が一時停止を解除してから `startNewReplayChunk()` を呼ぶ。その先頭の書き出しは Writer に弾かれず、続くスナップショットでワールドから地形が送り直される

### 将来の検討（初版では入れない）

- 「最後に書き出した状態と同じ座標は省く」処理：ピストンの往復などで元に戻った座標を省ける。効果を計測してから判断する。入れる場合は `Long2IntOpenHashMap` を容量指定して使い回す
- セクションごとに書き出しタイミングをずらして負荷を平らにする

### 想定負荷

- 取り込み：約 355 件/tick × 約 10ns ＋ 書き出し ≈ 約 5〜15µs/tick
- 記録処理（エンコード＋書き込みスレッドへの受け渡し：約 400〜500ns/件）の回数が約 1/15 になるため、全体では軽くなる見込み

---

## 7. プロジェクト構成

```
optimize-server-replay/
├─ build.gradle                 Loom 1.18 / Java 21 / MC 1.21.11 / mojmap（ビルドの実行には JDK 25 が必要）
├─ src/main/java/com/panyaaa256/optimizeserverreplay/
│  ├─ OptimizeServerReplay.java         初期化・設定読み込み
│  ├─ config/FilterConfig.java          Gson・初期値・検証・録画ごとの固定コピー
│  ├─ entity/EntityPackets.java         エンティティ系パケットのセット・ID 取り出し
│  ├─ entity/EntityFilter.java          録画ごとの状態（残す ID セット）
│  ├─ block/BlockUpdateBuffer.java      録画ごとの状態（変化セクション・プール・書き出し）
│  └─ mixin/
│     ├─ OptimizeServerReplayMixinPlugin.java  フックがすべて当たったかの確認
│     ├─ ReplayRecorderMixin.java       <init> / canRecordPacket / tick / pause / stop
│     ├─ FlashbackWriterMixin.java      startNewReplayChunk
│     ├─ ServerReplayMixin.java         reload
│     └─ accessor/*Accessor.java        private なエンティティ ID の取り出し
└─ src/main/resources/
   ├─ fabric.mod.json                   environment: server / depends: fabricloader, minecraft 1.21.11, java, server-replay >=3.3.1（fabric-api は外す）
   └─ optimize-server-replay.mixins.json  required: false / defaultRequire: 0 / plugin を指定
```

雛形（fabric-example-mod の 1.21.11 ブランチ）から作成済み。現時点であるのは `OptimizeServerReplay.java`、`fabric.mod.json`、`optimize-server-replay.mixins.json` と、テンプレートの見本の `mixin/ExampleMixin.java`。`ExampleMixin.java` は最初の mixin を追加するときに削除する。

### 依存関係

- ServerReplay：`modCompileOnly`（Modrinth maven）
- Arcade（arcade-replay）：`modCompileOnly`（`maven.supersanta.me`、`net.casualchampionships:arcade-replay:0.8.1-beta.39+1.21.11`）。実行時は ServerReplay に同梱されたものを使う
- fastutil：MC 同梱のものを使う
- Fabric API：使わないので依存から外す。雛形に入っている `build.gradle` の `modImplementation`、`gradle.properties` の `fabric_api_version`、`fabric.mod.json` の `depends` を削除する。ServerReplay が Fabric API を必須にしているため、サーバーに入れる必要があること自体は変わらない

---

## 8. 実装手順

1. **下調べ**：ソースでの確認は済んでいる（3 章・11 章）。残りは、実際に使う jar を `javap` で確認すること
   - `ReplayRecorder.canRecordPacket` / `tick` / `pause(Z)` / `stop(Z)` の記述子
   - 引数なしの `stop()` / `pause()` の呼び出し（Kotlin 側の `stop$default` を含む）が、`stop(Z)` / `pause(Z)` を通ること
   - Kotlin のコンパイル結果として、`FlashbackWriter.startNewReplayChunk` が private のまま残っているか
   - 各パケットの ID フィールド名・アクセサ名（MC 1.21.11 の jar。クラス名は確認済み）
   - mixin プラグインで、フックが当たったかどうかを何で判断できるか
2. **依存の整理**：Fabric API を外し、ServerReplay と Arcade を `modCompileOnly` で足す（7 章）
3. **設定と再読み込み**：ファイル生成・検証・`/replay reload` 連動、mixin プラグインによるフックの確認
4. **エンティティ選別**：全消し → ホワイトリストの順
5. **Block Action**
6. **ブロック変化のまとめ**：取り込み → 書き出し → チャンク区切り・停止・一時停止への対応
7. **テスト**（下記）
8. **README**：`README.md` と `README.ja.md` に、キー一覧の表、ServerReplay 側の推奨設定、対象になる録画とプレイヤーの扱い（10 章）を追記し、「フィルターは未実装」の注記を外す。「必要なもの」の Fabric API は、Server Replay が必要とするもの、という説明に直す

---

## 9. テスト

自動テスト（JUnit など）は書かない。すべて、実際にサーバーで録画して手動で確認する。

### テスト用の場所

ピストンのクロック回路、アイテムを出し続けるドロッパー、TNT、額縁、ホッパー付きトロッコを含む小さな範囲をチャンク録画する。

### 正しさ

- ホワイトリストのエンティティだけが出ること（空なら一切出ないこと）
- 録画開始時からいるホワイトリスト対象（額縁など）が、出現するだけでなく動きや表示の変化も記録されること
- `ignore_entities` 有効時も、チャンク録画のダミープレイヤーが透明のままであること
- `ignore_block_action: true` で Block Action が記録されないこと
- まとめ有効時（`ignore_block_action: false`）は、ピストンの Block Action だけが記録されず、チェストの開閉や音符ブロックは記録されること
- 除外したエンティティの消滅パケットが記録されないこと
- まとめ有効時、動いているピストンの周りのブロックが消えて見えないこと
- 録画終了時のブロックが実際のワールドと一致すること（一時停止中に録画を止めた場合も含む）
- チャンクの区切りをまたいだシーク後も表示が壊れないこと
- チャンクの区切り後のスナップショットに、除外したエンティティが現れないこと
- プレイヤー録画に一切影響しないこと
- ReplayMod 形式のチャンク録画に一切影響しないこと

### エッジケース

- 録画の停止・再開・一時停止
- 範囲端のチャンクの読み込み・解放
- `block_update_interval_ticks` が 1 / 20 / 6000 のとき
- `/replay reload` 後、録画中の設定は変わらず、次の録画から反映されること
- 設定ファイルの構文エラー・キーの欠け・型の不正で、警告ログが出て初期値で動き、ファイルが書き換わらないこと
- サーバーが無人になって一時停止し、プレイヤーが戻って再開したあとも、ブロックがワールドと一致すること
- フック先が見つからない ServerReplay / Arcade の組み合わせで、警告ログが出て、すべての機能が無効になり、起動と録画は止まらないこと

### 計測

- 同じファームで各設定のオン／オフのファイルサイズを比較する
- spark で MSPT と記録処理のプロファイルを比較する
- Flashback での書き出し時間を比較する

---

## 10. README に載せる内容

### ServerReplay 側の推奨設定

- `ignore_sound_packets: true`：ピストン音（録画データの約 31%）を消す
- `ignore_entities` を使うなら `optimize_entity_packets: false`：エンティティ検索が無駄になるため
- `default_encoding: "flashback"`
- 補足：`optimize_explosion_packets` は 1.21.11 版の Arcade では参照されておらず、実質効果なし

### 対象になる録画

- Flashback 形式のチャンク録画だけが対象。ReplayMod 形式のチャンク録画と、プレイヤー録画（`/replay start players`）には一切影響しない

### プレイヤーの扱い

- 1.20.2 以降、プレイヤーも他のエンティティと同じ出現パケット（`ClientboundAddEntityPacket`、種類 `minecraft:player`）で出現するため、`ignore_entities` の対象になる
- 初期値の `entity_whitelist` には `minecraft:player` が入っているので、そのままならプレイヤーは映る。リストから外すとワールド上から姿が消える
- タブリスト（`ClientboundPlayerInfoUpdatePacket` / `ClientboundPlayerInfoRemovePacket`）は対象外で、常に記録される。そのため、プレイヤーを外してもタブリストには名前が残る
- 掘削中のひび割れ表示（`ClientboundBlockDestructionPacket`）も対象外で、常に記録される

---

## 11. 参考：調査で分かったこと

- 2026-08-16 の録画（`month6_BlockFarmNE`、約 13.9 時間）：展開後 21.5GB（録画データ 8.6GB ＋ 地形キャッシュ 12.9GB）、zip 6.4GB
- 音パケットの約 98% がピストンの伸縮音。爆発音は爆発パケット内にあり `ignore_sound_packets` では消えない
- エンティティ系の内訳：アイテム 27〜35%、ホッパー付きトロッコ 16〜21%、ピグリン・ピグリンブルート・ストライダー計約 4 割、TNT 5〜8%
- `optimize_entity_packets` は TNT の `ClientboundEntityPositionSyncPacket` を止めておらず、TNT の容量の大半がこれ
- ブロック変化のまとめの試算（3 チャンク平均）：1 tick −9%、5 tick −54%、20 tick −81%、100 tick −93%、600 tick −96%
- 地形キャッシュの 9 割以上は 5 分ごとのスナップショット由来（zip では約 15% に圧縮される）

### ServerReplay / Arcade のソースで確認したこと

ServerReplay 3.3.1（`1.21.11` ブランチ）と Arcade 0.8.1-beta.39（ServerReplay 3.3.1 が依存する版）を読んで確認した。

- Flashback のチャンク長は `FlashbackIO.CHUNK_LENGTH = 5 * 60 * 20`（6000 tick）の定数で、設定では変えられない
- `ReplayRecorder.record()` は `writer.canRecordPacket(p) && this.canRecordPacket(p)` の順に判定する。Writer が先に捨てるパケット（一時停止中のすべて、`ClientboundForgetLevelChunkPacket`、`ClientboundMoveMinecartPacket` など）は、こちらのフックに届かない
- `ReplayChunkRecorder.canRecordPacket` は `ClientboundSetChunkCacheRadiusPacket` 以外で `super` を呼ぶので、親クラスへの注入で全パケットを判定できる
- 一時停止・再開：`pause()` 中は `FlashbackWriter.tick()` が `next_tick` もチャンクの区切りも進めない。`resume()` は `startNewReplayChunk()` を呼んで、スナップショットを取り直す
- スナップショット（`ReplayChunkRecorder.takeSnapshot`）は、読み込み済みのチャンクをワールドから作り直して `record()` し、未読み込みのチャンクは解放時に記録したキャッシュを参照する。エンティティは `TrackedEntity.resendPackets` で、出現パケットを含むバンドルとして `record()` される
- ServerReplay の `reload()` は設定を読み直すだけで、実行中の録画には触れない。`restart()` で作られる次の録画は、その時点の設定で作られる
- Arcade の最新 beta.48 でも、3 章でフックするメソッドは変わっていない。beta.39 からの `ReplayRecorder` / `FlashbackWriter` の差分は、`isInitialized()` の追加と、エンコード失敗時のログ出力の変更だけ
- `ReplayRecorder.resume()` は、一時停止の解除（`lastPausedTimestamp = null`）を `writer.resume()` より先に行う
- サーバーが無人のまま `pause-when-empty-seconds` が過ぎると、Arcade の `MinecraftServerMixin` がすべてのチャンク録画を `pause(true)` で一時停止し、プレイヤーが戻ると `resume()` する
- サーバー停止時は、`ServerStopEvent` で各チャンク録画の `stop()`（引数なし）が呼ばれる
- `ReplayFormat` は enum（`ReplayMod` / `Flashback`）。`ReplayModWriter` は `canRecordPacket` / `pause` / `resume` / `tick` を上書きしておらず、チャンクの区切りにあたる処理もない
- `FlashbackWriter` は `ReplayRecorder` のコンストラクタの中（`writer` フィールドの初期化）で作られる
- エンティティの消滅は、`TrackedEntityMixin` の `removeRecorder` / `removeAllRecorders` が ID 1 つの `ClientboundRemoveEntitiesPacket` を作って `record()` する
- `ServerReplay` は Kotlin の `object` で、`reload()` に `@JvmStatic` は付いていない（インスタンスメソッド）
- vanilla クライアントは、未知のエンティティ ID を参照するパケット（乗り物、紐、拾得など）を無視する

### MC 1.21.11 の jar で確認したこと

Loom のキャッシュにある mojmap の jar を `javap` で確認した。

- 4 章・5 章・6 章に出てくるパケットのクラス名は、すべて存在する
- `ClientboundSectionBlocksUpdatePacket` には、`(SectionPos, ShortSet, LevelChunkSection)` のコンストラクタと `runUpdates(BiConsumer<BlockPos, BlockState>)` がある
- `ClientboundBlockEventPacket` には `getBlock()` と `getPos()` がある
- `PistonMovingBlockEntity` は `getUpdatePacket()` を上書きしていない（`BlockEntity` の既定のまま）
- `MovingPistonBlock.newBlockEntity` は null を返し、`getRenderShape` は `RenderShape.INVISIBLE` を返す
- `PistonMovingBlockEntity` の `tick` / `finalTick` は、移動が終わるとクライアントへ送るフラグ付きで `Level.setBlock` を呼ぶ（フラグ 67 または 3。フラグ 340 の経路では、直後に `Block.updateOrDestroy` をフラグ 3 で呼ぶ）

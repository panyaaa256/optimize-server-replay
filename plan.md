# Optimize Server Replay 実装計画

ServerReplay のチャンク録画（Flashback 形式）で、タイムラプス用途に不要なパケットを削減するアドオン mod。

## 1. 概要

| 項目 | 内容 |
|---|---|
| 形態 | ServerReplay のアドオン。サーバー専用の Fabric mod（Java） |
| 対象バージョン | MC 1.21.11 / ServerReplay 3.3.1+1.21.11（同梱 Arcade 0.8.1-beta.39+1.21.11） |
| 適用範囲 | **チャンク録画（`ReplayChunkRecorder`）のみ**。プレイヤー録画には一切適用しない |
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

`config/optimize-server-replay/config.json`（初回起動時に初期値で自動生成）

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
| `net.casual.arcade.replay.recorder.ReplayRecorder.<init>` | TAIL | 録画ごとの設定の固定、状態の初期化（チャンク録画でなければ無効化） |
| `ReplayRecorder.canRecordPacket(Packet)` | HEAD, cancellable | エンティティ選別、Block Action 破棄、ブロック変化の取り込み |
| `ReplayRecorder.tick()` | HEAD | n tick ごとの書き出し |
| `net.casual.arcade.replay.io.writer.flashback.FlashbackWriter.startNewReplayChunk()`（private） | HEAD | チャンクの区切り（スナップショット）直前の書き出し |
| `ReplayRecorder.stop(boolean)` | HEAD | 録画終了時の書き出し |
| `me.senseiwells.replay.ServerReplay.reload()` | TAIL | 設定の再読み込み |

### 方針

- Arcade / ServerReplay への mixin は `remap = false`、メソッドは原則として名前だけで指定する（記述子は書かない）
  - 例外：`stop` は `@JvmOverloads` で `stop()` と `stop(boolean)` の 2 つがあるため、記述子付きで `stop(Z)` に絞る
- `canRecordPacket` を選ぶ理由：`ReplayRecorder.record()` の中で
  1. `ReplayOptimizerUtils.shouldIgnorePacket()`
  2. バンドルパケットの分解（サブパケットごとに `record()` を再帰呼び出し）
  3. `writer.canRecordPacket()` && `this.canRecordPacket()`
  4. `writer.writePacket()`

  の順に処理されるため、**バンドル分解後・書き込み前**に 1 件ずつ判定できる。`ReplayChunkRecorder.canRecordPacket` のオーバーライドは `super` を呼ぶので、親クラスへの注入で効く
- 録画ごとの状態は `@Unique` フィールドとして `ReplayRecorder` に持たせる
- `ReplayChunkRecorder` 以外（プレイヤー録画）では、すべてのフックを素通りにする
- フック内で例外が出たときは、ログを 1 回だけ出してパケットを素通しにする（録画を止めない）

### 呼び出し元とスレッド（ソースで確認済み）

| 経路 | 呼び出し元 | スレッド |
|---|---|---|
| ブロック変化・ライト更新 | `ChunkHolder.broadcast`（`ChunkHolder.broadcastChanges` から） | メイン |
| Block Action | `PlayerList.broadcast`（`ServerLevel.runBlockEvents` から） | メイン |
| エンティティ系 | `ChunkMap.TrackedEntity.sendToTrackingPlayers` / `addRecorder` / `removeRecorder` | メイン |
| `ReplayRecorder.tick()` | `ServerTickEvent` | メイン |
| `startNewReplayChunk()` | `FlashbackWriter.tick()`（`ReplayRecorder.tick()` の中）、`FlashbackWriter.resume()` | メイン |

- パケットのエンコードは Writer のスレッドで非同期に行われるが、記録するパケットは生成時に状態をコピーするので、書き出しで作るパケットにも問題はない

### ServerReplay / Arcade のバージョン違いへの対応

- `fabric.mod.json` では `server-replay` を `>=3.3.1` の範囲で指定する（完全一致で固定しない）
- mixin 設定は `"required": false`、`"defaultRequire": 0` にして、フック先が見つからなくても起動は止めない
- `IMixinConfigPlugin` で上記 6 箇所のフックがすべて当たったかを確認する。1 つでも当たらなければ警告ログを出し、**すべての機能を無効化**する（素通し）
  - 一部だけ当たった状態（例：取り込みはできるのに書き出しのフックがない）でブロック変化が失われるのを防ぐため、全部か無しかにする

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
   - 消滅（`ClientboundRemoveEntitiesPacket`）：残す ID セットから外し、記録する
   - その他：ID を取り出し、残す ID セットにあれば記録。複数 ID を持つパケット（乗り物・紐・拾得）は、どれか 1 つでも該当すれば記録

#### ダミープレイヤーを常に通す理由

チャンク録画のダミープレイヤー（`-ChunkRecorder-`）は、`ClientboundAddEntityPacket` ではなく Flashback の `CreatePlayer` アクションで作られる（`FlashbackWriter.writePlayer` が出現パケットを取り除く）。そのため残す ID セットに入らず、そのままでは透明化の効果（`ClientboundUpdateMobEffectPacket`）やエンティティデータが捨てられてしまう。

#### 存在しないエンティティを参照するパケット

「どれか 1 つでも該当すれば記録」だと、除外したエンティティの ID を含むパケット（例：除外したボートに乗るプレイヤーの `SetPassengers`、除外したアイテムを拾う `TakeItemEntity`）も記録される。vanilla クライアントは未知の ID を無視するので、壊れることはない。見た目への影響は次の程度で、許容する。

- 紐の相手（`minecraft:leash_knot`）を除外すると、紐が表示されない
- 乗り物を除外すると、乗っているエンティティは乗り物なしで表示される

### エンティティ系パケットのセット

1.21.11 の mojmap 名は実装時に要確認（MC 本体の jar での確認がまだ。コンパイルで確かめる）。

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
- `ReplayRecorder.stop(boolean)` の先頭（録画終了時）
- 一時停止中（`ReplayRecorder.getPaused()`）は書き出さない（Writer に捨てられるため）

**手順**（vanilla の `ChunkHolder.broadcastChanges` と同じ考え方）

1. 再入防止フラグを立てる
2. 変化のあった各セクションについて：
   - `level.getChunkSource().getChunkNow(x, z)` で読み込み済みのチャンクだけ対象にする（未読み込みならスキップ）
   - 変化 1 箇所 → `new ClientboundBlockUpdatePacket(level, pos)`
   - 変化 2 箇所以上 → `new ClientboundSectionBlocksUpdatePacket(sectionPos, shorts, section)`（状態はセクションから読まれる）
   - `recorder.record(packet)` で通常の経路に流す
3. ブロックの後に、ブロックエンティティが変化した各座標について `blockEntity.getUpdatePacket()` を `record()` する。チャンクが未読み込み、ブロックエンティティがもう無い、または `getUpdatePacket()` が null のときはスキップする
4. 集合を `clear()` してプールへ戻し、フラグを下ろす

### 順序について

- `tick()` の先頭で書き出すので、そのtickの `next_tick` より前に入る
- `startNewReplayChunk()` の先頭で書き出すので、`endChunk` より前に入る（`endChunk` は Writer のスレッドに後から積まれる）
- 区切りの後のスナップショットでは、読み込み済みのチャンクはワールドから作り直した `ClientboundLevelChunkWithLightPacket` を `record()` する。未読み込みのチャンクは、解放時に記録したチャンクデータのキャッシュを参照する。どちらも最新の状態なので整合する
- 一時停止中は Arcade の `FlashbackWriter.canRecordPacket` が先に弾くため溜まらない。一時停止の直前に溜まっていた分は書き出さずに残るが、再開時の `startNewReplayChunk()` 先頭で書き出され、続くスナップショットでワールドから地形が送り直されるので、変化は失われない

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
│     ├─ ReplayRecorderMixin.java       <init> / canRecordPacket / tick / stop
│     ├─ FlashbackWriterMixin.java      startNewReplayChunk
│     ├─ ServerReplayMixin.java         reload
│     └─ accessor/*Accessor.java        private なエンティティ ID の取り出し
└─ src/main/resources/
   ├─ fabric.mod.json                   environment: server / depends: minecraft 1.21.11, server-replay >=3.3.1
   └─ optimize-server-replay.mixins.json  required: false / defaultRequire: 0 / plugin を指定
```

雛形（fabric-example-mod の 1.21.11 ブランチ）から作成済み。現時点であるのは `OptimizeServerReplay.java`、`fabric.mod.json`、`optimize-server-replay.mixins.json` と、テンプレートの見本の `mixin/ExampleMixin.java`。`ExampleMixin.java` は最初の mixin を追加するときに削除する。

### 依存関係

- ServerReplay：`modCompileOnly`（Modrinth maven）
- Arcade（arcade-replay）：`modCompileOnly`（`maven.supersanta.me`、`net.casualchampionships:arcade-replay:0.8.1-beta.39+1.21.11`）。実行時は ServerReplay に同梱されたものを使う
- fastutil：MC 同梱のものを使う

---

## 8. 実装手順

1. **下調べ**：ソースでの確認は済んでいる（3 章・11 章）。残りは、実際に使う jar を `javap` で確認すること
   - `ReplayRecorder.canRecordPacket` / `tick` / `stop(Z)` の記述子
   - Kotlin のコンパイル結果として、`FlashbackWriter.startNewReplayChunk` が private のまま残っているか
   - 各パケットの mojmap 名・ID フィールド名（MC 1.21.11 の jar）
2. **設定と再読み込み**：ファイル生成・検証・`/replay reload` 連動、mixin プラグインによるフックの確認
3. **エンティティ選別**：全消し → ホワイトリストの順
4. **Block Action**
5. **ブロック変化のまとめ**：取り込み → 書き出し → チャンク区切り・停止・一時停止への対応
6. **テスト**（下記）
7. **README**：`README.md` と `README.ja.md` に、キー一覧の表、ServerReplay 側の推奨設定、プレイヤーの扱い（10 章）を追記し、「フィルターは未実装」の注記を外す

---

## 9. テスト

### テスト用の場所

ピストンのクロック回路、アイテムを出し続けるドロッパー、TNT、額縁、ホッパー付きトロッコを含む小さな範囲をチャンク録画する。

### 正しさ

- ホワイトリストのエンティティだけが出ること（空なら一切出ないこと）
- 録画開始時からいるホワイトリスト対象（額縁など）が、出現するだけでなく動きや表示の変化も記録されること
- `ignore_entities` 有効時も、チャンク録画のダミープレイヤーが透明のままであること
- `ignore_block_action: true` で Block Action が記録されないこと
- まとめ有効時（`ignore_block_action: false`）は、ピストンの Block Action だけが記録されず、チェストの開閉や音符ブロックは記録されること
- 録画終了時のブロックが実際のワールドと一致すること
- チャンクの区切りをまたいだシーク後も表示が壊れないこと
- チャンクの区切り後のスナップショットに、除外したエンティティが現れないこと
- プレイヤー録画に一切影響しないこと

### エッジケース

- 録画の停止・再開・一時停止
- 範囲端のチャンクの読み込み・解放
- `block_update_interval_ticks` が 1 / 20 / 6000 のとき
- `/replay reload` 後、録画中の設定は変わらず、次の録画から反映されること
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

### プレイヤーの扱い

- 1.20.2 以降、プレイヤーも他のエンティティと同じ出現パケット（`ClientboundAddEntityPacket`、種類 `minecraft:player`）で出現するため、`ignore_entities` の対象になる
- 初期値の `entity_whitelist` には `minecraft:player` が入っているので、そのままならプレイヤーは映る。リストから外すとワールド上から姿が消える
- タブリスト（`ClientboundPlayerInfoUpdatePacket` / `ClientboundPlayerInfoRemovePacket`）は対象外で、常に記録される。そのため、プレイヤーを外してもタブリストには名前が残る
- 掘削中のひび割れ表示（`ClientboundBlockDestructionPacket`）も対象外で、常に記録される
- プレイヤー録画（`/replay start players`）には、このmodは一切影響しない

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
- Arcade の最新 beta.48 でも、3 章でフックするメソッドは変わっていない
- vanilla クライアントは、未知のエンティティ ID を参照するパケット（乗り物、紐、拾得など）を無視する

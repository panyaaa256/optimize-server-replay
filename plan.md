# server-replay-filter 実装計画

ServerReplay のチャンク録画（Flashback 形式）で、タイムラプス用途に不要なパケットを削減するアドオン mod。

## 1. 概要

| 項目 | 内容 |
|---|---|
| 形態 | ServerReplay のアドオン。サーバー専用の Fabric mod（Java） |
| 対象バージョン | MC 1.21.11 / ServerReplay 3.3.1+1.21.11（同梱 Arcade 0.8.1-beta.39+1.21.11） |
| 適用範囲 | **チャンク録画（`ReplayChunkRecorder`）のみ**。プレイヤー録画には一切適用しない |
| mod ID（仮） | `server-replay-filter` |

### 提供する機能

1. `ignore_entities`：エンティティ系パケットを記録しない
2. `entity_whitelist`：`ignore_entities` 有効時に、指定した種類のエンティティだけ残す
3. `ignore_block_action`：Block Action（ピストンの伸縮アニメーション等）を記録しない
4. `block_update_interval_ticks`：ブロック変化を n tick ごとにまとめる（0 で無効）

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

`config/server-replay-filter/config.json`（初回起動時に初期値で自動生成）

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
| `ignore_block_action` | bool | `false` | `ClientboundBlockEventPacket` を記録しない。`block_update_interval_ticks` が 1 以上なら、この値に関係なく自動で有効 |
| `block_update_interval_ticks` | int | `0` | 0 ならブロック変化をまとめない。1 以上なら、その tick 数ごとにまとめて書き出す。上限 6000（Flashback のチャンク長）に丸める |

### 挙動の詳細

- **ファイルを分ける理由**：ServerReplay は `updateConfig` で自分の `config.json` を書き戻すため、独自キーを足すと消えるおそれがある
- **再読み込み**：`ServerReplay.reload()` の末尾に mixin を当て、`/replay reload` で一緒に読み直す
- **反映タイミング**：録画開始時の設定を録画ごとに固定する（ServerReplay の `createSettings()` と同じ考え方）。再読み込みは次の録画から効く
- **読み込み**：MC 同梱の Gson を使う（Kotlin 依存を増やさない）
- **検証**：
  - 型が不正な値 → 警告ログを出して初期値
  - `block_update_interval_ticks` が負 → 0、6000 超 → 6000
  - `entity_whitelist` の未知の ID → 警告ログを出して無視
- **Block Action の自動適用**：`block_update_interval_ticks >= 1` かつ `ignore_block_action == false` のとき、録画開始時に「Block Action を自動で無視します」と info ログを出す

| `ignore_block_action` | `block_update_interval_ticks` | Block Action |
|---|---|---|
| false | 0 | 記録する |
| true | 0 | 捨てる |
| どちらでも | 1 以上 | 捨てる（自動適用） |

---

## 3. フック箇所

1.21.11 ブランチ（ServerReplay `1.21.11`、Arcade `1.21.11`）のソースで存在を確認済み。

| 対象 | 注入位置 | 用途 |
|---|---|---|
| `net.casual.arcade.replay.recorder.ReplayRecorder.<init>` | TAIL | 録画ごとの設定の固定、状態の初期化（チャンク録画でなければ無効化） |
| `ReplayRecorder.canRecordPacket(Packet)` | HEAD, cancellable | エンティティ選別、Block Action 破棄、ブロック変化の取り込み |
| `ReplayRecorder.tick()` | HEAD | n tick ごとの書き出し |
| `net.casual.arcade.replay.io.writer.flashback.FlashbackWriter.startNewReplayChunk()`（private） | HEAD | チャンクの区切り（スナップショット）直前の書き出し |
| `ReplayRecorder.stop(...)` | HEAD | 録画終了時の書き出し |
| `me.senseiwells.replay.ServerReplay.reload()` | TAIL | 設定の再読み込み |

### 方針

- Arcade / ServerReplay への mixin は `remap = false`、メソッドは名前だけで指定する（記述子は書かない）
- `canRecordPacket` を選ぶ理由：`ReplayRecorder.record()` の中で
  1. `ReplayOptimizerUtils.shouldIgnorePacket()`
  2. バンドルパケットの分解（サブパケットごとに `record()` を再帰呼び出し）
  3. `writer.canRecordPacket()` && `this.canRecordPacket()`
  4. `writer.writePacket()`

  の順に処理されるため、**バンドル分解後・書き込み前**に 1 件ずつ判定できる。`ReplayChunkRecorder.canRecordPacket` のオーバーライドは `super` を呼ぶので、親クラスへの注入で効く
- 録画ごとの状態は `@Unique` フィールドとして `ReplayRecorder` に持たせる
- `ReplayChunkRecorder` 以外（プレイヤー録画）では、すべてのフックを素通りにする

---

## 4. 機能 1・2：`ignore_entities` / `entity_whitelist`

### 判定フロー（`canRecordPacket` の先頭）

1. 書き出し中の自分のパケット（再入防止フラグが立っている）→ 通す
2. `ignore_entities` が false → 通す
3. パケットの種類がエンティティ系セットに含まれない → 通す
4. `entity_whitelist` が空 → **記録しない**（種類の判定のみ。ID は見ない）
5. ホワイトリストあり：
   - 出現（`ClientboundAddEntityPacket`）：種類がリストにあれば、残す ID セットに追加して記録。なければ記録しない
   - 消滅（`ClientboundRemoveEntitiesPacket`）：残す ID セットから外し、記録する
   - その他：ID を取り出し、残す ID セットにあれば記録。複数 ID を持つパケット（乗り物・紐）は、どれか 1 つでも該当すれば記録

### エンティティ系パケットのセット

1.21.11 の mojmap 名は実装時に要確認。

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

### 実装メモ

- ID のフィールドが private のもの（`ClientboundRotateHeadPacket`、`ClientboundEntityEventPacket` など）は `@Accessor` mixin で取り出す
- 残す ID セットは fastutil の `IntOpenHashSet`（ロックなし）
  - 「隠す ID」ではなく「残す ID」を持つ：額縁・トロッコ等は数十〜数百件でほぼ増減しないため、小さくキャッシュに乗る
  - エンティティ ID はサーバー起動中は増え続ける連番で再利用されないため、消滅の取りこぼしがあっても誤判定にはならない
- `record()` がメインスレッド以外から呼ばれた場合は判定せず通す
- 種類の判定は `getClass()` の完全一致（入れ子クラスは個別に登録）
- 想定負荷：約 65 件/tick × 約 10〜20ns ≈ 約 1µs/tick

### 補足

- スナップショット（5 分ごと）でのエンティティ出現も `record()` 経由のため、同じく除外される見込み。テストで確認する
- 出現パケットが Writer に届かないため、Arcade の移動まとめ（`move_entities` アクション）も対象がいなくなり、自然に出なくなる

---

## 5. 機能 3：`ignore_block_action`

- `canRecordPacket` で `ClientboundBlockEventPacket` なら記録しない
- 有効条件：`ignore_block_action == true` または `block_update_interval_ticks >= 1`

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
| `ClientboundForgetLevelChunkPacket` | そのチャンク列の溜まっている分を捨てる。パケット自体は通す |

メインスレッド以外から来たブロック変化は、取り込まずにそのまま通す。

### データ構造（resize とアロケーションを避ける）

- `Long2ObjectOpenHashMap<ShortOpenHashSet>`：セクション座標（`SectionPos.asLong`）→ セクション内の変化位置
- `LongOpenHashSet`：ブロックエンティティが変化した座標
- `ShortOpenHashSet` はプールして使い回し、`clear()` して返却（配列は保持される）
- いずれも録画開始時に十分な容量で確保し、書き出しのたびに `clear()` で再利用する（毎回 `new` しない）
- ブロックの状態値は持たない（書き出し時にワールドから読む）

### 書き出し

**タイミング**

- `ReplayRecorder.tick()` の先頭で、録画内の tick カウンタが `interval` の倍数のとき
- `FlashbackWriter.startNewReplayChunk()` の先頭（チャンクの区切りの直前）
- `ReplayRecorder.stop()` の先頭（録画終了時）

**手順**（vanilla の `ChunkHolder.broadcastChanges` と同じ考え方）

1. 再入防止フラグを立てる
2. 変化のあった各セクションについて：
   - `level.getChunkSource().getChunkNow(x, z)` で読み込み済みのチャンクだけ対象にする（未読み込みならスキップ）
   - 変化 1 箇所 → `new ClientboundBlockUpdatePacket(level, pos)`
   - 変化 2 箇所以上 → `new ClientboundSectionBlocksUpdatePacket(sectionPos, shorts, section)`（状態はセクションから読まれる）
   - `recorder.record(packet)` で通常の経路に流す
3. ブロックの後に、ブロックエンティティが変化した各座標について `blockEntity.getUpdatePacket()` を `record()` する
4. 集合を `clear()` してプールへ戻し、フラグを下ろす

### 順序について

- `tick()` の先頭で書き出すので、そのtickの `next_tick` より前に入る
- `startNewReplayChunk()` の先頭で書き出すので、`endChunk` より前に入る。区切りの後のスナップショットはワールドから地形を送り直すので整合する
- 一時停止中は Arcade の `FlashbackWriter.canRecordPacket` が先に弾くため溜まらない。再開時は `startNewReplayChunk()` 経由で書き出される

### 将来の検討（初版では入れない）

- 「最後に書き出した状態と同じ座標は省く」処理：ピストンの往復などで元に戻った座標を省ける。効果を計測してから判断する。入れる場合は `Long2IntOpenHashMap` を容量指定して使い回す
- セクションごとに書き出しタイミングをずらして負荷を平らにする

### 想定負荷

- 取り込み：約 355 件/tick × 約 10ns ＋ 書き出し ≈ 約 5〜15µs/tick
- 記録処理（エンコード＋書き込みスレッドへの受け渡し：約 400〜500ns/件）の回数が約 1/15 になるため、全体では軽くなる見込み

---

## 7. プロジェクト構成

```
server-replay-filter/
├─ build.gradle                 Loom / Java 21 / MC 1.21.11 / mojmap
├─ src/main/java/<package>/
│  ├─ ReplayFilterMod.java              初期化・設定読み込み
│  ├─ config/FilterConfig.java          Gson・初期値・検証・録画ごとの固定コピー
│  ├─ entity/EntityPackets.java         エンティティ系パケットのセット・ID 取り出し
│  ├─ entity/EntityFilter.java          録画ごとの状態（残す ID セット）
│  ├─ block/BlockUpdateBuffer.java      録画ごとの状態（変化セクション・プール・書き出し）
│  └─ mixin/
│     ├─ ReplayRecorderMixin.java       <init> / canRecordPacket / tick / stop
│     ├─ FlashbackWriterMixin.java      startNewReplayChunk
│     ├─ ServerReplayMixin.java         reload
│     └─ accessor/*Accessor.java        private なエンティティ ID の取り出し
└─ src/main/resources/
   ├─ fabric.mod.json                   environment: server / depends: minecraft 1.21.11, server-replay >=3.3.1
   └─ server-replay-filter.mixins.json
```

### 依存関係

- ServerReplay：`modCompileOnly`（Modrinth maven）
- Arcade（arcade-replay）：`modCompileOnly`（`maven.supersanta.me`、`net.casualchampionships:arcade-replay:0.8.1-beta.39+1.21.11`）。実行時は ServerReplay に同梱されたものを使う
- fastutil：MC 同梱のものを使う

---

## 8. 実装手順

1. **下調べ**：実際に使う jar を `javap` で確認し、以下が想定どおりか確かめる
   - `ReplayRecorder.canRecordPacket` / `tick` / `stop` の名前と記述子
   - `FlashbackWriter.startNewReplayChunk` の存在
   - `ServerReplay.reload` の存在（Kotlin `object` のインスタンスメソッド）
   - 各パケットの mojmap 名・ID フィールド名
2. **設定と再読み込み**：ファイル生成・検証・`/replay reload` 連動
3. **エンティティ選別**：全消し → ホワイトリストの順
4. **Block Action**
5. **ブロック変化のまとめ**：取り込み → 書き出し → チャンク区切り・停止・一時停止への対応
6. **テスト**（下記）
7. **README**：キー一覧の表、ServerReplay 側の推奨設定、プレイヤーの扱い（10 章）

---

## 9. テスト

### テスト用の場所

ピストンのクロック回路、アイテムを出し続けるドロッパー、TNT、額縁、ホッパー付きトロッコを含む小さな範囲をチャンク録画する。

### 正しさ

- ホワイトリストのエンティティだけが出ること（空なら一切出ないこと）
- Block Action が記録されないこと（まとめ有効時は `ignore_block_action: false` でも）
- 録画終了時のブロックが実際のワールドと一致すること
- チャンクの区切りをまたいだシーク後も表示が壊れないこと
- チャンクの区切り後のスナップショットに、除外したエンティティが現れないこと
- プレイヤー録画に一切影響しないこと

### エッジケース

- 録画の停止・再開・一時停止
- 範囲端のチャンクの読み込み・解放
- `block_update_interval_ticks` が 1 / 20 / 6000 のとき
- `/replay reload` 後、録画中の設定は変わらず、次の録画から反映されること

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

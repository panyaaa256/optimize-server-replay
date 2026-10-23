# 更新履歴

Optimize Server Replay の変更点の日本語版です。リリースノート（GitHub Releases）は英語だけで、その内容は [CHANGELOG.md](CHANGELOG.md) にあります。

## 未リリース

## 1.4.1 (2026-10-23)

`pause_when_idle_seconds` の時間何も起きていない録画を、Server Replay が再開しようとしても止めたままにして、無駄なスナップショットを取らないようにしました。

- Server Replay が録画を一時停止しているあいだ（範囲のプレイヤーが全員スペクテイターのときなど）も、`pause_when_idle_seconds` の時間を数えるようにしました。その時間に達していれば、Server Replay が再開しようとしても、Block Action か腕振りがあるまで止めたままにします。これまでは、いったん再開してスナップショットを取り直し、その時間が経ってからまた一時停止していました（[#10](https://github.com/panyaaa256/optimize-server-replay/pull/10)）

## 1.4.0 (2026-10-23)

`pause_when_idle_seconds` で録画を続けさせるものを、Block Action とプレイヤーの腕振りだけにしました。`idle_ignore_entities` と `idle_entity_whitelist` は使われなくなったので、`config.json` から消して構いません。

### 追加
- `pause_when_idle_seconds` で一時停止・再開したときに、Server Replay と同じ文言で全プレイヤーに知らせるようにしました。Server Replay の `pause_notify_players` が `true` のときだけです（[#9](https://github.com/panyaaa256/optimize-server-replay/pull/9)）

### 変更
- `pause_when_idle_seconds` で変化として数えるものを、Block Action（ピストン、音符ブロック、チェスト、ベルなど。スポナーとエンドゲートウェイを除く）とプレイヤーの腕振りだけにしました。ブロックの変化、エンティティの動き、Level Event は数えません。羊が草を食べる、作物が育つ、プレイヤーが歩き回るだけ、といったことで録画が続かなくなります。サーバーが 3 秒ごとに位置を送るため、立ち止まっているプレイヤーも録画を続けさせていましたが、これもなくなります（[#9](https://github.com/panyaaa256/optimize-server-replay/pull/9)）
- `ignore_block_action`、`block_update_interval_ticks`、`ignore_entities` で録画から省いた Block Action と腕振りも数えるようにしました（[#9](https://github.com/panyaaa256/optimize-server-replay/pull/9)）

### 削除
- `idle_ignore_entities` と `idle_entity_whitelist` を削除（[#9](https://github.com/panyaaa256/optimize-server-replay/pull/9)）

## 1.3.1 (2026-10-23)

- `pause_when_idle_seconds` を使っているとき、`idle_entity_whitelist` に無いコウモリなどのせいで、録画が一時停止しなかったり再開したりする問題を修正。バニラでエンティティだけが出す Level Event（コウモリが飛び立つときなど）を、変化として数えないようにしました。一覧は [docs/packets.ja.md](docs/packets.ja.md#エンティティだけが出す-level_event) にあります（[#8](https://github.com/panyaaa256/optimize-server-replay/pull/8)）

## 1.3.0 (2026-10-23)

Flashback 形式のチャンク録画を、範囲で何も変化しない時間が続いたら一時停止できるようにしました。今の `config.json` には新しいキーがないため、キーを足すまでは、キーごとに警告をログに出して初期値（無効）を使います。

- `pause_when_idle_seconds` を追加（初期値 `0` で無効）。録画範囲のブロックとエンティティが指定の秒数変化しなかったら録画を一時停止し、何かが変化したら再開します。範囲内でプレイヤーが放置されていても、何も起きていない時間を録画しないようになります。変化として数えるエンティティは `idle_ignore_entities` と `idle_entity_whitelist` で選べます。初期値ではプレイヤーだけで、スペクテイターのプレイヤーは数えません（[#7](https://github.com/panyaaa256/optimize-server-replay/pull/7)）

## 1.2.0 (2026-10-22)

Flashback 形式のチャンク録画で、エンティティが多いときに、録画の開始時とスナップショットでサーバーが止まる時間を短くしました。今の `config.json` には新しいキーがないため、キーを足すまでは、警告をログに出して初期値を使います。

- `optimize_snapshot` を追加（初期値 `true`）。Server Replay は、録画の開始時とスナップショットで送るチャンク 1 つごとに、ワールドの全エンティティを調べていました。これを、一度だけエンティティをチャンクごとに分けるようにしました。記録されるパケットは変わりません（[#5](https://github.com/panyaaa256/optimize-server-replay/pull/5)）

## 1.1.0 (2026-10-21)

チャンク録画の保存を速くし、クラッシュで途中になった録画を復旧するようにしました。今の `config.json` には新しいキーがないため、キーを足すまでは、キーごとに警告をログに出して初期値を使います。

### 追加
- 録画中にサーバーが止まった Flashback 形式のチャンク録画を、次の起動時に復旧。最後に区切られたリプレイチャンクまでが残ります（[#1](https://github.com/panyaaa256/optimize-server-replay/pull/1)）
- `optimize_saving` を追加（初期値 `true`）。Flashback 形式のチャンク録画の保存を速くします。zip を展開した中身は、`metadata.json.old` が無いことを除いて、Server Replay だけのときと同じです（[#2](https://github.com/panyaaa256/optimize-server-replay/pull/2)）
- `zip_compression_level` を追加。録画を保存するときの zip の圧縮レベル（0〜9）です。初期値の `1` は Server Replay と同じです（[#2](https://github.com/panyaaa256/optimize-server-replay/pull/2)）
- `zip_while_recording` を追加（初期値 `false`）。録画しながら zip にまとめます。保存がすぐに終わり、録画中のディスク使用量がほぼ zip の分だけになります。この場合、クラッシュで途中になった録画の復旧には、この mod が必要です（[#3](https://github.com/panyaaa256/optimize-server-replay/pull/3)）

## 1.0.0 (2026-10-07)

最初のリリースです。[Server Replay](https://github.com/senseiwells/ServerReplay) のチャンク録画から、選んだパケットを除きます。

### 追加
- `ignore_entities`：エンティティのパケットを記録しません。`entity_whitelist` の種類は記録を続けます
- `ignore_block_action`：ピストンの伸び縮みやチェストの開閉などの Block Action を記録しません
- `block_update_interval_ticks`：ブロックの変化を、指定した tick 数ごとにまとめて記録します

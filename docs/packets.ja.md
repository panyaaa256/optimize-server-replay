# 設定ごとの対象パケット

[English](packets.md)

Optimize Server Replay の各設定が、録画のどのパケットをどう扱うかの一覧です。設定そのものの説明は、README の[設定](../README.ja.md#設定)を参照してください。

- パケットは Minecraft 1.21.11 での ID で書いています。`minecraft:` の名前空間は省いています。どれも play 状態の clientbound パケットです。
- [Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_protocol/Packets) では、同じ ID が「Official name」の列に載っています。wiki は最新バージョンの内容なので、数値の ID は 1.21.11 と異なる場合があります。
- 対象は Flashback 形式のチャンク録画だけです。
- Server Replay は `bundle` を中のパケットに分けてから記録します。そのため、bundle の中のパケットも 1 つずつ判定されます。

## `ignore_entities`

`true` のとき、下のパケットは「残すエンティティ」のものだけが記録されます。

- 種類が `entity_whitelist` にあるエンティティを残します。残すかどうかは、そのエンティティの `add_entity` が来たときに決まります。
- チャンク録画のダミープレイヤーは、常に残します。

| パケット | 記録される条件 |
|---|---|
| `add_entity` | エンティティの種類が `entity_whitelist` にある |
| `remove_entities` | どれか 1 つでも残すエンティティである |
| `move_entity_pos` | 残すエンティティである |
| `move_entity_rot` | 残すエンティティである |
| `move_entity_pos_rot` | 残すエンティティである |
| `entity_position_sync` | 残すエンティティである |
| `teleport_entity` | 残すエンティティである |
| `set_entity_motion` | 残すエンティティである |
| `set_entity_data` | 残すエンティティである |
| `rotate_head` | 残すエンティティである |
| `set_equipment` | 残すエンティティである |
| `update_attributes` | 残すエンティティである |
| `entity_event` | 残すエンティティである |
| `animate` | 残すエンティティである |
| `hurt_animation` | 残すエンティティである |
| `damage_event` | ダメージを受けた側が残すエンティティである |
| `update_mob_effect` | 残すエンティティである |
| `remove_mob_effect` | 残すエンティティである |
| `projectile_power` | 残すエンティティである |
| `set_passengers` | 乗り物か、乗っているどれかが残すエンティティである |
| `set_entity_link` | 2 つのエンティティのどちらかが残すエンティティである |
| `take_item_entity` | アイテムか、拾った側が残すエンティティである |

複数のエンティティを含むパケットは丸ごと記録されるので、省いたエンティティの ID が残ることがあります。vanilla のクライアントは、知らない ID を無視します。

### 対象にしていないエンティティ関連のパケット

| パケット | 理由 |
|---|---|
| `move_minecart_along_track` | Flashback 形式では、Server Replay がもともと記録しません |
| `set_camera` | Server Replay がもともと記録しません |
| `sound_entity` | 音として扱います。Server Replay の `ignore_sound_packets` で省けます |
| `block_destruction` | ブロックを掘っている途中のひび割れです。常に記録されます |
| `player_info_update` | タブリストです。常に記録されます |
| `player_info_remove` | タブリストです。常に記録されます |

## `ignore_block_action`

対象のパケットは `block_event` だけです。どれを省くかは、`block_update_interval_ticks` にもよります。

| `ignore_block_action` | `block_update_interval_ticks` | 省かれる `block_event` |
|---|---|---|
| `true` | どの値でも | すべて |
| `false` | 1 以上 | `minecraft:piston` と `minecraft:sticky_piston` のもの |
| `false` | `0` | なし |

## `block_update_interval_ticks`

1 以上のとき、下のパケットを次のように扱います。

| パケット | 扱い |
|---|---|
| `block_update` | 記録せず、座標だけを記録します |
| `section_blocks_update` | 記録せず、座標だけを記録します |
| `block_entity_data` | 記録せず、座標だけを記録します |
| `level_chunk_with_light` | そのまま記録します。チャンクのデータのほうが新しいので、そのチャンクについて記録していた座標は捨てます |
| `light_update` | そのまま記録します |

記録した座標は、次のタイミングで録画に書き出します。

- `block_update_interval_ticks` の tick 数ごと
- 新しいリプレイチャンクが始まる前（6000 tick ごとと、録画の再開時）
- 録画が一時停止に入る直前
- 録画の停止時

それぞれの座標は、書き出す時点のワールドの状態で記録します。

| 覚えていたもの | 記録するパケット |
|---|---|
| 1 つのチャンクセクションに 1 か所 | `block_update` |
| 1 つのチャンクセクションに 2 か所以上 | `section_blocks_update` |
| ブロックエンティティ | そのブロックエンティティの更新パケット（通常は `block_entity_data`） |

次の座標は書き出しません。

- 読み込まれていないチャンクの座標
- その時点のブロックが `minecraft:moving_piston` である座標
- すでに無くなったブロックエンティティ

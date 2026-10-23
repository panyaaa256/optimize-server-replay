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

## `pause_when_idle_seconds`

1 以上のとき、下のパケットを「変化」として数えます。数えたパケットが指定の秒数来なければ録画を一時停止し、一時停止中に来れば録画を再開します。このパケットの記録のされ方は変わりません。

| パケット | 数える条件 |
|---|---|
| `block_update` | 常に |
| `section_blocks_update` | 常に |
| `block_entity_data` | 常に |
| `block_event` | `ignore_block_action` で省かれないもの |
| `block_destruction` | 常に |
| `level_event` | [エンティティだけが出す種類](#エンティティだけが出す-level_event)ではない |
| `explode` | 常に |
| [`ignore_entities` の表](#ignore_entities)にあるエンティティのパケット | 下の条件をすべて満たすエンティティが、1 つでも含まれる |

エンティティを数える条件は次のとおりです。

- `ignore_entities` で省かれない
- `idle_ignore_entities` が `false` か、種類が `idle_entity_whitelist` にある
- スペクテイターのプレイヤーではない
- チャンク録画のダミープレイヤーではない
- まだワールドにいる（`add_entity` を除き、ワールドからいなくなったエンティティは数えません）

ほかのパケットは数えません。時刻（`set_time`）、天気（`game_event`）、タブリスト、チャット、スコアボード、ボスバー、音、パーティクル、チャンクの読み込み（`level_chunk_with_light`）、明るさの更新（`light_update`）などです。

`block_update_interval_ticks` でまとめているブロックの変化は、まとめて書き出したときではなく、変化したときに数えます。

### エンティティだけが出す `level_event`

バニラでエンティティ（プレイヤーを含む）だけが出す `level_event` は数えません。数えると、コウモリが飛び立つだけで、`idle_entity_whitelist` に無いエンティティの動きで録画が再開してしまうためです。これらは、エンティティ自身のパケットかブロックの変化と一緒に来るので、そちらで数えます。

| 出すもの | `level_event` の種類（ID） |
|---|---|
| ガスト | `SOUND_GHAST_WARNING`（1015）、`SOUND_GHAST_FIREBALL`（1016） |
| エンダードラゴン | `SOUND_DRAGON_FIREBALL`（1017）、`SOUND_DRAGON_DEATH`（1028）、`PARTICLES_DRAGON_BLOCK_BREAK`（2008） |
| ドアを壊すゾンビなど | `SOUND_ZOMBIE_WOODEN_DOOR`（1019）、`SOUND_ZOMBIE_DOOR_CRASH`（1021） |
| ウィザー | `SOUND_WITHER_BLOCK_BREAK`（1022）、`SOUND_WITHER_BOSS_SPAWN`（1023）、`SOUND_WITHER_BOSS_SHOOT`（1024） |
| コウモリ | `SOUND_BAT_LIFTOFF`（1025） |
| モブの変化 | `SOUND_ZOMBIE_INFECTED`（1026）、`SOUND_ZOMBIE_CONVERTED`（1027）、`SOUND_ZOMBIE_TO_DROWNED`（1040）、`SOUND_HUSK_TO_ZOMBIE`（1041）、`SOUND_SKELETON_TO_STRAY`（1048） |
| ファントム | `SOUND_PHANTOM_BITE`（1039） |
| ミツバチ | `PARTICLES_BEE_GROWTH`（2011） |
| メイスの攻撃 | `PARTICLES_SMASH_ATTACK`（2013） |
| 斧を使うプレイヤー、`minecraft:copper_golem` | `PARTICLES_WAX_OFF`（3004）、`PARTICLES_SCRAPE`（3005） |
| `minecraft:weaving` の効果を持ったまま倒れたエンティティ | `ANIMATION_SPAWN_COBWEB`（3018） |
| 投げたポーション、経験値瓶 | `PARTICLES_SPELL_POTION_SPLASH`（2002）、`PARTICLES_INSTANT_POTION_SPLASH`（2007） |
| エンダーアイ | `PARTICLES_EYE_OF_ENDER_DEATH`（2003） |
| ドラゴンの火の玉 | `PARTICLES_DRAGON_FIREBALL_SPLASH`（2006） |
| `minecraft:ominous_item_spawner` | `PARTICLES_TRIAL_SPAWNER_SPAWN_ITEM`（3021） |
| 金床、砥石、鍛冶台を使うプレイヤー | `SOUND_ANVIL_USED`（1030）、`SOUND_GRINDSTONE_USED`（1042）、`SOUND_SMITHING_TABLE_USED`（1044） |
| エンドポータルの枠にエンダーアイをはめるプレイヤー | `END_PORTAL_FRAME_FILL`（1503）、`SOUND_END_PORTAL_SPAWN`（1038） |
| ポータルを通ったプレイヤー | `SOUND_PORTAL_TRAVEL`（1032） |

ブロックや発射装置も出す種類は、エンティティが出したものでも数えます。たとえば、ブロックが壊れるときの `PARTICLES_DESTROY_BLOCK`（2001）、骨粉の `PARTICLES_AND_SOUND_PLANT_GROWTH`（1505）、ファイヤーチャージの `SOUND_BLAZE_FIREBALL`（1018）、雷の `PARTICLES_ELECTRIC_SPARK`（3002）です。

Fixes entities that `idle_entity_whitelist` leaves out keeping a recording from pausing with `pause_when_idle_seconds`.

- Fix bats and some other entities keeping a recording from pausing, or resuming it, even when they are not in `idle_entity_whitelist`. The level events that only entities send in vanilla, such as a bat taking off, no longer count as a change. See [docs/packets.md](docs/packets.md#level_event-that-only-entities-send) for the list ([#8](https://github.com/panyaaa256/optimize-server-replay/pull/8))

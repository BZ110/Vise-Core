# Vise Core

Vise Core is a Minecraft PvP plugin for **Paper 1.21.3** and **Java 21**. It manages arenas, kits, queues, ranked matches, parties, and events. It also handles the lobby, player profiles, cosmetics, chat, and travel between server instances.

Scale a single instance with schematic slots or Polar world copies, or run a fleet that shares **regional matchmaking**, profiles, leaderboards, and chat. Redis coordinates queues and transfers, and MySQL stores player profiles and match history. [Small-server setup](#skip-the-scaling-can-i-use-vise-core-on-a-small-minecraft-server) · [Global matchmaking](#global-matchmaking) · [Arena backends](#choosing-an-arena-backend) · [Scaling guide](#scaling-and-deployment-architecture)

<a name="vise-core-vs-strikepractice"></a>

## How Vise Core compares

| Feature | **Vise Core** | StrikePractice | Versus | UltimateDuels | Duels by Realized | EpicDuels |
| --- | :---: | :---: | :---: | :---: | :---: | :---: |
| Direct 1v1 challenges | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Arena kits and spectator mode | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Ranked play or Elo ratings | ✅ | ✅ | ❌ | ✅ | ✅ | ❌ |
| Party fights and public FFA arenas | ✅ | ✅ | ❌ | ✅ | ❌ | Party fights |
| Personal kit editor and saved layouts | ✅ | ✅ | ❌ | ✅ | ❌ | Layouts only |
| Automatic arena restoration | ✅ | ✅ | ❌ | ✅ | Paid extension | ✅ |
| PvP practice bots | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| PolarPaper arena generation from saved origins | ✅¹ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Built-in HTML telemetry export with movement, health, and ping charts | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Redis chat bridge and regional travel menu | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Redis matchmaking with ranked and casual queues, ordered regional pools, and automatic duel transfers | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Built-in worldwide pool priorities and latency-based host selection | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Cross-server `/spectate` with host approval and return to the source lobby | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Built-in ping-aware knockback and ghost-arrow recovery | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Lava Rising survival events | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Challonge tournament brackets | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| PvPTiers, MCTiers, and Subtiers in one profile with independent switches | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Reloadable YAML for messages, GUI layouts, and state-specific hotbar commands | ✅ | ❌ | ❌ | ❌ | ❌ | Messages |
| Configurable world rules, lobby protection, combat, and restart warnings | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Configurable Elo placements, divisions, and widening queue windows | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| XenoWorldReset destination picker | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MySQL leaderboards with a Redis index and cached outage fallback | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Standalone ranked queues after Redis loss | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Experimental MySQL key-value fallback for kit sharing and travel discovery | ✅² | ❌ | ❌ | ❌ | ❌ | ❌ |
| Disk recovery for player data and ranked matches when both databases are unreachable | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Peer-assisted database recovery through both Redis and MySQL | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Completely Free** | **✅** | **❌** | **✅** | **✅** | **✅** | **✅** |

✅ denotes a documented feature of the base plugin. On feature rows, ❌ denotes a capability unlisted in the cited materials. The pricing row covers the base download. ¹ See the [PolarPaper version notice](#polarpaper-arena-generation-support). ² Experimental and not recommended. See [Redis and outage options](#redis-and-outage-options).

- **StrikePractice** is a paid practice suite with bot fights, replay playback, per-kit knockback configuration, and built-in events. The author's separate stats site can display saved fight data. [Product listing and pricing](https://builtbybit.com/resources/strikepractice-practice-pvp-plugin.74743/) · [v4.0.0 release notes](https://builtbybit.com/resources/strikepractice-practice-pvp-plugin.74743/updates) · [Stats site](https://github.com/toppev/strikepractice-stats)
- **Versus** focuses on casual 1v1 duels for semi-vanilla servers. It documents arena setup, waiting when arenas are full, public and private matches, and in-game configuration editing. [SpigotMC listing](https://www.spigotmc.org/resources/versus-1-16-1-21-%E2%9A%94%EF%B8%8F-duels-simplified.118883/) · [Source and commands](https://github.com/robomonkey-mc/Versus) · [Downloads](https://www.spigotmc.org/resources/versus-1-16-1-21-%E2%9A%94%EF%B8%8F-duels-simplified.118883/history)
- **UltimateDuels** documents multi-round 1v1 and party play, party queues, per-player kit variants, FFA worlds, built-in scoreboards, and WorldEdit or FAWE schematic restoration. [Modrinth listing](https://modrinth.com/plugin/ultimateduels)
- **Duels by Realized** offers item and money wagers, sign-based queues, kit ratings, inventory inspection after matches, and an extension system. Its automatic arena-reset extension is listed separately from the base download. [Source](https://github.com/Realizedd/Duels) · [SpigotMC listing](https://www.spigotmc.org/resources/duels.20171/) · [Author's command reference](https://github.com/Realizedd/Duels/wiki/Commands)
- **EpicDuels** by nuroso000 offers kit queues, team duels, in-game party brackets, rematches, and live spectating. It creates and removes an isolated world copy for each duel. The base plugin is free to download and can optionally sync statistics through Supabase or Firebase. [Modrinth listing](https://modrinth.com/plugin/epicduels) · [Author's wiki](https://github.com/nuroso000/EpicDuels/wiki)

## Contents

- [Skip the scaling. Can I use Vise Core on a small Minecraft server?](#skip-the-scaling-can-i-use-vise-core-on-a-small-minecraft-server)
- [Installation and module setup](#installation-and-module-setup)
- [Duels, arenas, and matchmaking](#duels-arenas-and-matchmaking)
  - [Global matchmaking](#global-matchmaking)
  - [PolarPaper arena generation support](#polarpaper-arena-generation-support)
- [Ranked play and match reports](#ranked-play-and-match-reports)
- [Experimental NPC practice](#experimental-npc-practice)
- [Kits and inventory layouts](#kits-and-inventory-layouts)
- [Parties, FFA, Lava Rising, and tournaments](#groups-and-events)
- [Lobby, teleportation, and server travel](#lobby-teleportation-and-server-travel)
- [Scaling and deployment architecture](#scaling-and-deployment-architecture)
- [Profiles, statistics, and cosmetics](#profiles-statistics-and-cosmetics)
- [Friends, chat, and Discord](#friends-chat-and-discord)
- [Combat fixes and server operations](#combat-fixes-and-server-operations)
- [Configuration, data, and upgrades](#configuration-data-and-upgrades)
  - [Translating messages with lang.yml](#translating-messages-with-langyml)
  - [Building menus with gui.yml](#building-menus-with-guiyml)
  - [Help Me Please storage failsafes](#help-me-please)
- [Codebase map](#codebase-map)
- [PlaceholderAPI reference](#placeholderapi-reference)
- [Command and permission reference](#command-and-permission-reference)
- [License](#license)

## Skip the scaling. Can I use Vise Core on a small Minecraft server?

**Yes. One Paper server, one lobby, one kit, and one arena are enough to get your first fight running.** Players connect directly to Paper. The same instance runs the lobby, pairs opponents, reserves the arena, and returns players to spawn when the fight ends. Regional pools, proxy transfers, and cross-server queues are optional. [Installation and module setup](#installation-and-module-setup)

### What works on one server

- **A fight between friends.** Use `/duel <player>` for a direct challenge, `/queue` for a specific kit, or `/quickqueue` when players are open to any eligible casual kit. Everyone already connected is in the same local matchmaking pool. Friends can watch with `/spectate <player>` when the fighters allow it. [Duels and matchmaking](#duels-arenas-and-matchmaking)
- **A small ranked ladder.** `/rankedqueue` still uses per-kit Elo and widens its rating range as players wait. A wider range helps two eligible players match, but the queue still needs another player in the same kit. Start with one popular ruleset so a handful of players are looking for the same fight. Ranked history and report exports work on the same instance. [Ranked play and match reports](#ranked-play-and-match-reports)
- **Room to add other ways to play.** Personal kit layouts and player profiles are useful from the first duel. Enable Party when your group wants team fights, or configure an FFA world when everyone wants to play together without waiting for an opponent. Each mode can use the same Paper instance. [Kits and inventory layouts](#kits-and-inventory-layouts) · [Groups and events](#groups-and-events)

### What the host needs

Run **Paper 1.21.3 with Java 21** and install **WorldEdit or FastAsyncWorldEdit** for Duels. Put the lobby and arena world on that instance. Start with the schematic backend, which is the default and works on the documented Paper version. A schematic arena can reuse physical slots in one loaded world as matches finish. [Schematic arena setup](#schematic-arenas)

Plan for **one MySQL database**. A single Paper server can run duels, ranked queues, local spectating, parties, and FFA without Redis. MySQL normally stores profiles, stats, and ranked history and serves leaderboards. Local recovery keeps gameplay available during an outage. Redis is **strongly recommended when expanding to multiple servers** and powers the fast leaderboard indexes and shared network features. [Help Me Please failsafes](#help-me-please)

If you add Core to an existing survival server, test the join flow on a copy first. Core resets player state, clears inventory, and sends players to its configured lobby on join. That behavior fits a practice server and matters when the same Paper instance also hosts an SMP. [Join listener](src/main/java/org/albi/core/events/OnJoinListener.java) · [Player reset code](src/main/java/org/albi/core/Core.java)

### Start with one map and a few slots

Merge these settings into `plugins/Core/config.yml` alongside your MySQL connection settings. Set `lobby_world` to the actual lobby world name.

```yaml
lobby_world: world
redis:
  enabled: false
network:
  global_chat: false
  matchmaking:
    enabled: false
use_polar_worlds: false
modules:
  Duels: true
  Party: false
  FFA: false
  LavaRising: false
  Tournaments: false
duels:
  bots:
    enabled: false
```

1. Set the lobby spawn with `/setspawn`, save one public kit, and create a compact schematic arena. A kit with building disabled is a good first test because there are fewer block changes to reset between rounds. [Kit setup](#shared-rulesets) · [Arena setup](#schematic-arenas)
2. Size `maxarenas` for **simultaneous fights**. For example, two slots in one map let four players fight in two separate 1v1s while other players wait or spectate. Set `perline` and the offsets so the two copies and their playable bounds are separate. If the standalone queue finds a pair while all slots on the selected map are occupied, the duel can fail to start and that pair can requeue when a slot opens.
3. Test one complete duel and then two at once. Check that the next match gets a clean arena, players return to the lobby, and tick times stay steady when the two copies paste. Map size, reset frequency, and other loaded worlds matter more for this test than the total number of people on the whitelist. Keep the setup at the size your players actually use. [Capacity and load testing](#load-test-the-work-that-actually-spikes)

## Installation and module setup

1. Download `ViseCore-*.jar` from GitHub Releases and put it in Paper's `plugins/` directory. To build from source, use `./gradlew build` on macOS or Linux or `gradlew.bat build` on Windows. The JAR is written to `build/libs/`. [Latest release](../../releases/latest) · [Build workflow](../../actions/workflows/build.yml)
2. Install FastAsyncWorldEdit or WorldEdit if you plan to use Duels or modules that share its arenas and kits.
3. Start Paper once to generate `plugins/Core/config.yml`. Create a MySQL database and configure its connection. If MySQL is unreachable, startup uses local recovery until the connection returns and the configured user can create tables. For Redis features, set `redis.enabled: true` and configure its endpoint. Restart Paper after editing the configuration.
4. Set `lobby_world` to the name of your lobby. Stand at the arrival point and run `/setspawn`. If you use multiple instances, give each one its own `region` and `id_number` combination.
5. Grant the command and feature permissions players need. Create a test kit and arena before opening matchmaking. Set up FFA worlds and event arenas independently of the duel map list.

The Paper plugin ID is `Core`, so its data directory is `plugins/Core/`. Most settings take effect after a Paper restart. Use the shipped defaults as a reference when editing the generated files. [Main configuration](src/main/resources/config.yml)

### Modules and requirements

Set each entry under `modules` to `true` or `false`. The defaults below apply to a fresh install. A module flagged as on can still be skipped if a dependency is missing.

| Module | Default | Purpose and requirements |
| --- | --- | --- |
| `Duels` | On | Direct challenges, queues, ranked matchmaking, spectating, shared kit and arena storage, and optional NPC practice. Requires FastAsyncWorldEdit or WorldEdit at startup. |
| `Party` | On | Parties, challenges between parties, and matches within a party. Uses Duels. |
| `FFA` | On | Persistent free-for-all worlds, teleport spots, and personal FFA layouts. Uses Duels for the base kits. |
| `CustomKit` | On | Personal kit creation, selection, load commands, and sharing codes. |
| `Friends` | On | Friend requests, friend lists, and friend-aware invites and messaging. |
| `LavaRising` | Off | Survival events with a rising lava floor. Uses Duels for schematic loading and needs a configured event world and arena. |
| `CriticalDiscord` | Off | Discord invites and account-linking commands. Linking needs a companion Discord integration that handles Core's codes. |
| `Tournaments` | Off | Challonge brackets played in Minecraft. Requires Party, Duels, a Challonge username, and an API key. |
| `KnockbackSynchronization` | On | Ping-aware adjustments to combat knockback. Also has its own `enabled` setting. |
| `AntiGhostArrow` | On | Arrow hit recovery when players and arrows move between server ticks. Also has its own `enabled` setting. |

The optional PolarPaper backend has its own [version requirements](#polarpaper-arena-generation-support). PlaceholderAPI adds values for compatible tab lists, scoreboards, and chat plugins. FeatherServerAPI loads the optional `plugins/Core/server-background.png` as a server-list background. **XenoWorldReset** powers the `/rtp` random-teleport menu. [XenoWorldReset documentation](https://wiki.bitaspire.com/en/docs/xenoworldreset)

## Duels, arenas, and matchmaking

### Direct challenges and spectating

Run `/duel <player>` to challenge an online player. The setup menus let the challenger pick a ruleset, an arena, and the number of rounds needed to win. The recipient can click **Accept** in chat or run `/acceptduel <challenger>`. A standard ruleset supplies the same base equipment and combat rules to both sides. The **Per Custom Kit** option lets each fighter use their own selected personal kit. Challenge preferences and block lists affect who can send requests. Friends can bypass the invite toggle, while a block still applies.

During the match, Core reserves an arena, equips the fighters, starts a countdown, and tracks round wins. Team matches use the same arena and kit system. Kits can define whether players can build, regenerate health naturally, lose hunger, or expose health indicators. Duel listeners handle damage during countdowns, prevent friendly hits, and resolve lethal hits as round outcomes while allowing totems to trigger. At the end, Core returns participants to the lobby and releases the arena for reuse.

Run `/spectate <player>` to watch a fight through the countdown and rounds. Every fighter's spectator preference and block list apply, so choosing a different fighter in the same match still respects those settings. Viewers stay outside combat, follow the match when a Polar round moves to a new copy, and leave with `/spawn`. The [PlaceholderAPI expansion](#placeholderapi-reference) supplies team colors, health indicators, score, elapsed time, and spectator information to compatible displays.

With [global matchmaking](#global-matchmaking) enabled, run `/spectate <exact player name>` from the lobby to watch a duel on another participating server. Leave any party or tournament before making a remote request. The duel host approves the request before the proxy transfers the viewer and checks the same match and permissions again on arrival. `/spawn`, the end of the match, or revoked access sends the viewer back to their original lobby. Redis outages leave local spectating available. Direct invitations and party and tournament sessions use their current Paper instance.

#### How a duel runs

| Stage | Server behavior |
| --- | --- |
| Allocation | Core binds participants to a duel and reserves either a position in a schematic world or a Polar world copy. The two team spawn points are resolved in that reservation's world. |
| Countdown | Fighters get their kit, round state, and start feedback. Combat listeners protect the countdown, restrict teammates from damaging one another, and apply kit rules for building, regeneration, and food. |
| Live round | Core checks the X/Z arena bounds each tick. Fighters outside the active bounds are treated as eliminated. Spectators outside the bounds are sent inward. Moving between two Polar copies of the same arena is a supported round transition. |
| Elimination | The lethal-hit listener lets a held totem use vanilla handling. Otherwise it resolves the killer, switches the defeated player to spectator, and informs the duel. The duel updates the team score and can announce the surviving players' health. |
| Reset or finish | A continuing match reapplies kit inventory and effects and resets player positions. On a cross-world round reset, the inventory is applied after the teleport completes. The end-of-match cleanup sends players and spectators back to the lobby and releases arena reservations. |

[Duel state and transitions](src/main/java/org/albi/core/modules/Duels/DuelInstance.java) · [Bounds checks](src/main/java/org/albi/core/modules/Duels/events/MovementOutside.java) · [Lethal-hit handling](src/main/java/org/albi/core/modules/Duels/events/OnSimulatedDeath.java)

### Casual queues

`/queue` opens a kit browser with queue counts. Choose a standard kit or **Per Custom Kit**. `/quickqueue` looks for a match in an active casual queue without making the player choose a specific ruleset. The action bar shows queue status while the player waits. Run `/queue leave` or `/quickqueue leave` to leave the casual queue.

In standalone mode, the casual matcher checks every two ticks and tries the quick queue first. Two quick-queue players can receive a random standard kit. A quick-queue player can take a match from another eligible casual queue, including Per Custom Kit when both players have usable selections. Regular kit queues pair eligible players waiting for the same kit. Casual matches start first-to-one and pick from active arenas. Menu counts reflect players queued on the current Paper instance. [Casual matchmaking code](src/main/java/org/albi/core/modules/Duels/queue/DuelQueueModule.java)

Staff can organize the duel and casual queue browsers into ruleset pages. Hold the item to use as the category icon, then run `/setpageduelkitcategory <page> <guiSlot>`. Use `/deletepageduelkitcategory <guiSlot>` to remove that entry from the category menu.

### Global matchmaking

With `network.matchmaking.enabled`, `/queue`, `/quickqueue`, and `/rankedqueue` look for opponents across participating servers. Ranked uses the same kit and rating rules as standalone play. Casual quick queue can fill a standard queue or join a Per Custom Kit match if both players queued with a valid kit. Core carries the selected personal-kit snapshots to the destination. Players can hold one network queue ticket at a time.

#### How Core chooses a match

1. **Start in each player's home pool.** A player joining on an `eu-east` server searches for an `eu-east` host first. The defaults open alternate destinations in geographic tiers, so several nearby pools can become eligible together. Each tier adds eight seconds by default. Each player's cross-region routes stay closed for at least 45 seconds. For two players in the EU, an eligible EU host ranks above a host outside it. An explicit priority list opens one destination per step in the listed order.
2. **Widen the latency and rating windows.** Core takes Paper's current ping sample at the source and adds the configured estimate for reaching the candidate pool. Both players must meet their individual ping limit, and their ping difference must fit the limit earned by the player who has waited less. Defaults start at 110 ms per player and a 45 ms difference. Both limits grow by 15 ms every five seconds, up to 260 ms and a 130 ms difference. Ranked starts at ±50 Elo and adds 25 every three seconds. The longer-waiting player's Elo window can admit the pair.
3. **Pick a host that both players can use.** Among eligible servers, Core prefers the region and pool priorities that work for both players. It then minimizes the worse estimated ping and the ping difference. Available player slots, an open arena for the ruleset, and current host load also affect selection. An eligible `eu-central` host can take a duel between players on `eu-east` and `eu-west`, even if neither player is already connected there. Source ping and directional adjustments are estimates. The proxy topology affects the actual in-match latency.
4. **Prepare the arena, then transfer.** Redis claims the two queue tickets atomically. The chosen host reserves a warm Polar world or completes a schematic paste, and both source servers confirm the players are still eligible. Core flushes dirty profiles before remote transfer and requests a proxy connection only for players who need to move. As soon as both players connect, their profiles load, and the lobby arrival completes, Core starts the normal duel countdown on the reserved arena.

#### Set up the network

1. Put participating backends on a BungeeCord-compatible proxy network, with the destination server IDs registered on each participating proxy. Set `bungee-plugin-message-channel = true` in Velocity. Install the same Vise Core version and enable Duels on each participating backend. [PaperMC proxy messaging](https://docs.papermc.io/paper/dev/plugin-messaging/) · [Velocity configuration](https://docs.papermc.io/velocity/configuration/)
2. Set `redis.enabled: true` on every participant and point them at the same Redis primary and MySQL database. The Redis connection needs permission to run Lua scripts alongside the existing data commands. Enable `network.matchmaking.enabled` and use the same `namespace`. Each backend needs a unique proxy registration. Set `server_id` to match it exactly, or leave it blank if the proxy already uses the lowercase `region-id_number` name. Leave `pool` and `pools` at their defaults to use the built-in geography. Redis 7 is used in CI. [Default configuration](src/main/resources/config.yml)
3. Configure the lobby spawn on each backend. Install public kits using the same names and rules on each hosting backend. Set up its local schematic slots or compatible Polar origins and ranked arena lists. Set `host_matches: false` on queue-only backends. A queue-only backend with local arenas can still host local matches during an outage.

| `network.matchmaking` key | Purpose |
| --- | --- |
| `enabled`, `namespace` | Enable shared queues and select a Redis namespace. Existing servers have standalone queues until explicitly enabled. |
| `server_id`, `pool`, `host_matches` | Set the exact proxy destination, the home pool of players queued here, and whether this server advertises as a network host. |
| `return_after_match` | Defaults to `true`. Returns fighters in a network match to the server they queued on so their next queue starts in the same home pool. Set it to `false` to leave fighters in the duel host's lobby. |
| `use_default_pools`, `pools.<pool>.region`, `priority`, `latency_ms` | Use the built-in catalog or define a custom route table. Override a pool's region, its alternate destinations in order, or a specific directional latency estimate in milliseconds. The home pool is implicit. |
| `priority_step_seconds`, `cross_region_after_seconds`, `unlisted_after_seconds` | Control when each tier or explicit alternate opens, the earliest cross-region attempt, and when unlisted pools become eligible. The default `-1` requires a listed route. |
| `ping.start_ms`, `max_ms`, `gap_start_ms`, `gap_max_ms` | Set the starting and maximum limits for estimated individual ping and the difference between players. |
| `ping.relax_every_seconds`, `relax_by_ms`, `same_region_add_ms`, `cross_region_add_ms` | Tune the widening interval and increment, and set defaults for unconfigured route estimates. |
| `poll_ticks`, `scan_size`, `offers_per_poll`, `pending_per_host` | Tune the exchange interval, queue sample size, offers attempted per scan, and outstanding handoffs on each host. Each bounded scan samples oldest, rotating, and random tickets. |

#### Built-in pools and automatic selection

Vise Core ships with **54 pools**. With `pool: ""`, it looks for a specific built-in or custom pool in `region`. Otherwise it checks for a recognized location prefix in the proxy ID, then uses the broad region in `region`. If both labels are unfamiliar, the backend joins the neutral `global` pool. Transfers between unlabeled backends use the cross-region wait and general latency estimate. For example, a proxy backend called `sydney-01` selects `oc-east` and one called `na-west-2` selects `na-west`. Set `pool` explicitly to choose a different home pool. [Bundled pool catalog and aliases](src/main/resources/matchmaking/pools.yml)

| Area | Pool coverage |
| --- | --- |
| Europe | `eu` and east, central, west, north, south, southeast, and southwest pools |
| North America | `na` and east, central, west, northwest, north, south, Mexico, Central America, and Caribbean pools |
| South America | `sa` and east, north, central, west, south, and southwest pools |
| Africa | `af` and north, west, central, east, and south pools |
| Asia and Middle East | `asia` and central, east, northeast, north, south, southeast, China, and Hong Kong pools, plus `middle-east` and `me-east`, `me-central`, and `me-west` |
| Oceania | `oc` and eastern, southern, and western Australia, New Zealand, Melanesia, Micronesia, and Polynesia pools |
| Antarctica and unknown locations | `antarctica` for a labeled backend and `global` for unfamiliar locations |

The catalog uses approximate geographic reference points and includes regional divisions such as Central America and the Pacific island groups. Core combines the route estimate with each player's observed source ping and considers only eligible hosts registered by the same matchmaking network. [UN geographical regions](https://unstats.un.org/unsd/methodology/m49/)

Upgrades pick up the catalog automatically, including servers with the original unedited six-pool examples. Custom pool settings take precedence. Setting `priority` replaces the built-in destinations for that pool. Set a single `latency_ms` value to tune its ping estimate while keeping the default order. To use only your own pool table, set `use_default_pools: false`.

#### Redis outages and interrupted transfers

When Redis is unavailable, each Paper instance can match players already connected there. Fresh local tickets can match right away. Recently published tickets wait for their possible network claims to expire, up to 31 seconds after the last publication attempt finishes. That prevents an old transfer and a new local duel from claiming the same player. Local fallback uses the same kit and ranked rating rules and a local arena. Global queues resume automatically when Redis responds.

Unfinished network handoffs expire after 25 seconds. If a transfer fails or an opponent never arrives, players who already moved can queue again from the destination lobby. Queued players who stayed in place can resume the search. Duels already started continue on their host. The handoff uses short-lived keys under `vise:mm:v1:{<namespace>}:` and needs no MySQL schema change. [Matchmaking code](src/main/java/org/albi/core/modules/Duels/queue/global) · [Redis state transitions](src/main/resources/matchmaking/exchange.lua)

```mermaid
flowchart TD
    Queue["Players join the same kit queue"] --> Store{"Can the queue server reach MySQL and Redis?"}
    Store -->|Yes| Regions["Search priority pools and widen ping and Elo limits"]
    Regions --> Host["Claim both tickets and reserve an arena on the chosen host"]
    Host --> Save{"Are both players eligible and their profiles saved?"}
    Save -->|Yes| Transfer["Transfer both fighters and start when they arrive"]
    Save -->|No| Retry["Release the arena and resolve the pending assignment"]
    Store -->|No| Fence["Wait for any published network claims to expire"]
    Fence --> Local["Match eligible players on this Paper server"]
```

The same namespace also shares short-lived routes for watchable duels. A spectator transfer names a specific duel and can be used once. Core clears spectator state on exit or disconnect. If a return transfer cannot complete, the player can use the current server's lobby. [Spectating code](src/main/java/org/albi/core/modules/Duels/spectating)

### Choosing an arena backend

| | Schematic arenas | PolarPaper arena generation |
| --- | --- | --- |
| **Hardware fit** | **Lower RAM, stronger CPU.** Start here when memory is scarce and the server can keep up with repeated pastes. | **Higher RAM, less CPU headroom.** Use ready-to-play world copies to reduce repeated block pastes during matches. |
| Unit of capacity | A reserved grid slot inside the loaded `<arena>-duel` world. | A separate loaded copy of an arena's saved Polar origin. |
| How the map is reset | Paste blocks and air back into the slot through WorldEdit or FAWE. | Use a fresh world copy and retire the used world after players leave. |
| Main capacity control | `setmax <name> maxarenas <count>` sets the number of slots for that map. `perline` and the X and Z offsets lay them out. | `polar_preload` sets the minimum ready-pool target per map. The target also grows with reservations. |
| Resource costs | Clipboard cache, loaded chunks, block edits, and a shared paste queue. | Cached origin bytes, decoded and loaded world copies, startup and refill work, and cleanup of retired worlds. |

This is a hardware rule of thumb. A big clipboard still takes memory, and generating a Polar copy still takes CPU. Arena size, how often build kits need resets, and how many matches run at once determine which backend fits. [Capacity and load testing](#scaling-and-deployment-architecture) · [PolarPaper's memory tradeoff](https://github.com/MinehubMC/PolarPaper#large-worlds)

### Schematic arenas

The schematic backend gives multiple matches separate physical slots inside one loaded world per arena name. Core lays out slots from `(0, 100, 0)` in that world. For a zero-based slot index, the row is `index / perline` and the column is `index % perline`. The row advances along X and the column advances along Z using the configured offsets. Team spawn coordinates are added to the slot's paste origin, so record them relative to the schematic origin in your build area. The X/Z arena bounds move with the copy.

1. Prepare a clean template in `plugins/Core/duel-placeholder/` and put the arena schematic in `plugins/Core/schems/duels/`. Export the schematic with a known origin and record spawn and boundary offsets relative to that point.
2. Run `/dueladmin arena create <name>` and then `/dueladmin arena arenaready <name>`. The second command copies the template to `plugins/Core/<name>-duel/` and replaces an existing copy at that path. Make the arena world available through your world setup and verify that Paper has loaded it with the exact name `<name>-duel`. The match allocator looks up that loaded world by name.
3. Hold a map icon and run `/dueladmin arena setdisplayitem <name>`. Stand at each team spawn in the origin-aligned build area and use `/dueladmin arena setspawn <name> 1` and `/dueladmin arena setspawn <name> 2`. Core saves the position and facing direction.
4. Record the relative horizontal boundaries with `/dueladmin arena setcorner <name> <x1|z1|x2|z2> <coordinate>`. Link the map with `/dueladmin arena setschematic <name> <file>`.
5. Set a positive `perline` and `maxarenas` with `/dueladmin arena setmax <name> <perline|maxarenas> <count>`. Set X and Z spacing with `/dueladmin arena setoffset <name> <x|z> <blocks>`. Leave space between copies for the full playable footprint and the arena buffers. Use `/dueladmin arena info <name>` to inspect the settings and `/dueladmin arena deactivate <name>` to toggle selection in the direct duel and casual arena pickers.
6. Test two simultaneous matches on the same map before adding capacity. Check that their spawn points and bounds are separate. Play a build round, break and place blocks, and check that the following round and the next match get the expected layout.

Core caches decoded clipboards by filename and runs one queued schematic paste at a time. FastAsyncWorldEdit handles the paste on a worker thread. Standard WorldEdit handles it on Paper's tick thread. The queue waits five ticks between pastes. Global matchmaking waits for the paste to finish before transferring players, and a freed slot becomes eligible for reuse. Large maps and multiple reset requests can delay other work in the queue. Keep distinct filenames for different maps and restart the instance after replacing a schematic in place so Core reloads the cached clipboard. [Schematic queue](src/main/java/org/albi/core/modules/Duels/SchematicManager.java) · [Duel allocation and lifecycle](src/main/java/org/albi/core/modules/Duels/DuelInstance.java) · [Arena setup commands](src/main/java/org/albi/core/modules/Duels/commands/DuelAdminCommand.java)

<a name="polar-arenas"></a>

### PolarPaper arena generation support

Core can generate isolated match worlds from a saved **PolarPaper** origin. Map builders work in one editable origin and publish it with a save command. At runtime, Core creates uniquely named match worlds from that saved version. Every copy starts from the same map coordinates.

> [!IMPORTANT]
> Core's build targets Paper 1.21.3 and compiles the Polar integration against `live.minehub:polarpaper:1.21.11.17`. That dependency JAR declares Paper API 1.21.11. PolarPaper's historical 1.1.3 release for Paper 1.21.3 uses older load and save signatures and lacks the `FilePolarSource` class Core uses. Use schematic arenas on the documented Paper 1.21.3 setup. Validate the full Core, Paper, and PolarPaper combination on staging before turning on the optional backend. [Build dependency](build.gradle.kts) · [PolarPaper versions](https://hangar.papermc.io/MinehubMC/Polar/versions) · [Historical Paper 1.21.3 release](https://hangar.papermc.io/MinehubMC/Polar/versions/1.1.3)
>
> The Vise Core maintainer reports that the PolarPaper backend has been **thoroughly tested** and run **in production** on a compatible setup with virtually no issues.

On a compatible setup, install PolarPaper, enable Duels, and restart Paper with the following settings.

```yaml
use_polar_worlds: true
polar_preload: 2
```

| Step | Action |
| --- | --- |
| Create the origin | Run `/dueladmin polar_arena create <name>`. When the origin file is absent, Core creates a temporary flat world without structures, converts its blocks to Polar, writes the origin, and cleans up the bootstrap world. The command opens the origin for the builder. |
| Edit the map | Run `/dueladmin polar_arena edit <name>` to reopen the loaded origin or load it from disk. Build or paste your intended map into that world. |
| Record teams | Stand at each spawn and use `/dueladmin polar_arena spawn1 <name>` and `spawn2 <name>`. The equivalent command is `/dueladmin polar_arena setspawn <name> <1\|2>`. The editor records exact X/Y/Z and yaw/pitch. |
| Record bounds and icon | At the two opposing corners run `/dueladmin polar_arena set_limit <name> min` and `set_limit <name> max`. These commands record X/Z limits. Hold an item for `/dueladmin polar_arena setdisplayitem <name>`. |
| Publish it to the ready pool | Run `/dueladmin polar_arena save <name>` or `/dueladmin polar_arena arenaready <name>` with the origin loaded. Run `/dueladmin polar_arena info <name>` to review the setup. |

#### What happens during a match

1. **Origin storage.** Arena definitions, team positions, and the Polar flag are in Core's local data. The actual map is `worlds/<lowercase-arena>-p-orig.polar` inside the PolarPaper plugin's data directory. Back up both when moving or restoring a server.
2. **World creation and reservation.** The arena manager caches origin bytes, decodes a copy for each new match world, and tracks ready worlds and in-use reservations separately by arena. Generated names follow `<arena>-p-<sequence>-<suffix>-duel`. Copies enable keep-inventory and immediate respawn. The arena lookup uses the recorded positions in the selected copy.
3. **Consumers.** Casual and ranked duels, party team fights, and tournament duels can use the normal duel allocator. NPC practice and private Party FFA sessions also reserve Polar copies when their chosen arena uses Polar. Public FFA and Lava Rising have their own world workflows.
4. **Round resets.** Build and per-custom duel paths can reserve a replacement copy for the next round. When a replacement is ready, the duel switches worlds and reapplies the players' kit after teleporting. A replacement reservation contributes to the active reservation count. [Duel lifecycle](src/main/java/org/albi/core/modules/Duels/DuelInstance.java) · [NPC session](src/main/java/org/albi/core/modules/Duels/AutomatedCombat/BotSession.java) · [Party FFA allocation](src/main/java/org/albi/core/modules/Party/play/PartyFFAInstance.java)
5. **Retirement and edits.** At release, Core schedules temporary-world unload and attempts to remove the generated world folder. If players are still there, it schedules a retry after 100 ticks. Saving a new origin refreshes the byte cache and retires unused copies from the older origin. Active fights keep the worlds they already reserved.
6. **Maintenance.** The instance warms saved arenas at startup. Join, quit, and world-change events schedule maintenance that looks for missing reserved worlds and queues replenishment. Background Polar operations go through a serialized queue. Its current delay between completed tasks is 250 ticks. The immediate startup and reservation paths can also generate worlds synchronously from Core's perspective by waiting on the world creation future.

The effective ready target is `max(max(1, polar_preload), active reservations + 1)` **per arena**. With the default preload and three in-use reservations on one map, Core aims for four ready copies in addition to those three. That means seven loaded match worlds at the target, before counting an open origin or a world waiting for cleanup. `polar_preload` is a floor for ready capacity. Budget RAM for the active and ready worlds together, and profile both a cold launch and the first reservation burst. [Polar arena manager](src/main/java/org/albi/core/modules/Duels/polar/PolarArenaManager.java) · [World operation queue](src/main/java/org/albi/core/modules/Duels/polar/PolarQueue.java) · [Maintenance listener](src/main/java/org/albi/core/modules/Duels/events/PolarArenaMaintenanceListener.java)

## Ranked play and match reports

`/rankedqueue` opens solo, single-round matchmaking by standard kit. The menu shows the player's Elo, placement or tier, and the number of players waiting for each kit. The queue widens its Elo search range as players wait. Leaving a party and ending any active fight or spectating session makes a player eligible to queue. Use `/rankedqueue leave` to cancel.

- Each kit has its own rating, win and loss counts, and placements. Core also tracks an overall rating. Defaults start ratings at **1,000 Elo**, count the first **five matches** as placements, and use adjustment factors of 64 during placements and 32 afterward. Set the starting rating and formula under `ranked`.
- The default search window starts at ±50 Elo and grows by 25 every three seconds. Set the starting window, increment, interval, and optional cap under `ranked.matchmaking`. A pair is eligible if the gap fits either player's current window. Wins against higher-rated opponents are worth more, and the match countdown previews the stakes.
- Tiers run from Bronze through Silver, Gold, Platinum, Diamond, Master, Grandmaster, and Champion. Defaults use five 100-point divisions per tier. Set division size and count under `ranked.tiers`. Players see placement progress until they earn a displayed tier.
- If local matchmaking cannot prepare an arena, eligible players keep their queue position and elapsed wait. Core starts the duel once the arena is ready and both players are eligible.
- `/rankedlb` opens the top 27 by overall ranked Elo. The kit picker and match calculations use the selected kit's ladder. For an arena pool specific to a kit, use `/rankedadmin add <kit> <arena>`, `remove <kit> <arena>`, and `list <kit>`. If the pool is empty, the queue draws from the shared arena list. Staff can access the pool commands with both `core.rankedadmin` and `core.admin.ranked`.

[Ranked matcher](src/main/java/org/albi/core/modules/Duels/queue/RankedQueueModule.java) · [Elo calculation](src/main/java/org/albi/core/modules/Duels/ranked/EloCalculator.java) · [Leaderboard reader](src/main/java/org/albi/core/modules/Duels/ranked/RankedCommands.java)

### Match telemetry, history, and export

Ranked duels record telemetry. In a direct challenge, the challenger can also enable **Match Telemetry** for a first-to-one, first-to-two, or first-to-three match. For eligible multi-round duels, Core starts a fresh record each round. Each record gets an eight-character ID.

- Hit events capture attacker, distance, aim angles, and vertical separation. The distance calculation uses the player's eye ray against the target hitbox, with nearest-box distance as a fallback. Core also counts arm swings, total damage, and longest combos.
- A repeating task samples both players' positions, health, and ping every 20 ticks. A second sampler collects ping every 100 ticks. The HTML report combines the event stream and periodic snapshots into charts for movement, health, reach, hit frequency, and network conditions.
- The current record can flag unusual measured reach for review. It is a telemetry signal. Consult the recorded context before treating a flag as evidence of a rule violation.
- The final ranked record includes the winner and both Elo changes. Intermediate rounds record their winner when telemetry is enabled for a multi-round challenge.

[Match tracker](src/main/java/org/albi/core/modules/Duels/ranked/MatchTracker.java) · [Match record](src/main/java/org/albi/core/modules/Duels/ranked/RankedMatchRecord.java) · [HTML report generator](src/main/java/org/albi/core/modules/Duels/ranked/RankedReportGenerator.java)

| Command or setting | What it does |
| --- | --- |
| `/seegames [player]` | Queries shared MySQL for up to ten recent records. Use an exact Minecraft name to look up a player on another server or an offline player with retained match history. |
| `/matchinfo <match_id>` | Reads a stored match and displays the kit, participants, average measured reach, and any recorded review flag. |
| `/rankedreport <match_id>` | Generates an HTML report under `plugins/Core/reports/`. It includes charts and a match timeline for reviewing health, movement, reach measurements, combos, and network conditions. Charts use a browser-loaded library. |
| `ranked.history_expiry_days` | Sets the Redis report cache lifetime and the age at which older SQL matches are pruned on startup. Defaults to seven days. |
| `ranked.webhook_url` | Sends match summaries and permits HTML report export to a configured webhook. `VISE_RANKED_WEBHOOK_URL` overrides the YAML value. |
| `ranked.report_url_template` | Adds a clickable report URL to the match ID message. Set a URL on your own report host with `{match_id}` in the appropriate place. Core saves reports to disk and the external host serves the link. |

## Experimental NPC practice

> [!WARNING]
> NPC practice uses version-specific Paper server internals. Updates can break NPC spawning, combat, or cleanup. Test a full practice duel and arena reuse on a staging server before enabling this feature or upgrading Paper.

Enable `duels.bots.enabled` and grant `core.automatecombat` to players who should use `/automatecombat`. An active duel arena and a playable kit are required. The NPC shares the duel arena system but has its own practice session, round score, and health display.

- The practice menu lets players choose a map, kit, and available preset. Built-in preset families cover **Beast**, **Pot**, **SMP**, and **Netherite OP**. The bundled public-kit allowlist contains `beast` and `pot`. Adjust `duels.bots.allowed_kits` to match the public kit IDs on your server.
- Advanced settings cover sprint-hit frequency, W-taps, attack range, strafing, movement and retreat speeds, reaction delay, chase distance, shield use and shield breaking, and resynchronization distance. Players can change the round target. Invulnerability lets players practice hits and knockback while both sides keep their health.
- `duels.bots.max_sessions` caps simultaneous practice duels, `default_rounds` sets the initial round target, and `debug_logging` enables bot diagnostics. The command, server log, and practice UI identify the feature as experimental.

Core creates a server-side fake player and equips it from the selected kit. Its tick loop manages aim, spacing, sprint resets, shields, and supported potions based on session state and inventory. The practice listener connects damage, projectiles, and departures to the correct session. Eligible rounds can reserve a fresh Polar copy. On normal close, Core cancels the session tasks, removes the NPC, and releases the arena. The bot's own round scoring stays inside the practice session. [NPC lifecycle](src/main/java/org/albi/core/modules/Duels/AutomatedCombat/FakePlayerNPC.java) · [Practice session](src/main/java/org/albi/core/modules/Duels/AutomatedCombat/BotSession.java)

## Kits and inventory layouts

### Shared rulesets

Use `/dueladmin kit create <name>` to register a public kit. Set up your inventory and run `saveinv <name>` under `/dueladmin kit`. Use `setdisplayitem <name>` while holding the GUI icon and `setslot <name> <slot>` to place it in the kit browser. `info <name>` shows the rules, `list` shows the available kits, `give <name>` equips the kit for inspection, and `delete <name>` removes it.

The `setbuild`, `setnaturalregen`, `sethealthindics`, and `setlosehunger` subcommands each take a kit name and a boolean. `/kiteditor` opens a chooser for duel layouts, FFA layouts, and the personal-kit browser. A saved layout changes the equipment arrangement for that player. Public kit names, arena configuration, and a player's saved layout are separate pieces of data.

`/setkitdata <kit> <type> <key> <value>` edits typed kit metadata. The type can be `STRING`, `INT`, `DOUBLE`, `BOOLEAN`, `LONG`, or `EFFECTMETA`. The keys below affect gameplay.

| Kit metadata | Behavior |
| --- | --- |
| `dmg_inc_%` | Positive integer percentage increase on qualifying player hits. A match in the legacy `33_percent_boost` configuration list sets the increase to 33%. |
| `mending_rng` | A positive integer overrides the amount an individual Mending event repairs. This is a repair amount. |
| `tnt_ignite` | Boolean that makes placed TNT ignite automatically in supported duels. The fuse uses `duels.tnt_seconds`. |
| `effects` | Potion effects applied when the kit is equipped in a match. `EFFECTMETA` appends an effect using `EFFECT:AMPLIFIER:DURATION:AMBIENT`, such as `SPEED:1:60:false`. Duration is in seconds. |

### Personal kits

`/customkit` opens a grid of **36** personal kit slots. Create a kit in an empty slot, select an existing kit for Per Custom Kit matches, or click the selected slot to edit it. `/k1` through `/k36` equip the corresponding saved kit directly. Each load command resets the current inventory and equips the saved gear.

- **Equipment and item editing.** Edit armor, offhand, hotbar, and inventory slots. Choose items from staff-curated categories. Item actions include changing names, enchantments, and stack sizes, removing items, and editing items inside shulker boxes and a kit's ender chest.
- **Direct Kit Room.** Edit the inventory directly and explicitly save it back to the kit. The room provides a clear-inventory action and a trash slot for unwanted items.
- **Kit settings.** Change build, hunger, regeneration, and health-indicator rules where offered by the menu. The kit editor also supports potion effects, a Mending repair override, a damage increase setting, and automatic TNT ignition.
- **Sharing.** Use **Share Kit** to generate a code through Redis or the experimental MySQL shared state. Codes are valid for 30 days from the latest share. Another player can run `/copykit <code>` and pick an empty slot for a copy. [Redis and outage options](#redis-and-outage-options)
- **Category administration.** Hold a display item and run `/createcustomkitcategory <name> <guiSlot>`. Use `/setitemincustomkitcategory <name> <guiSlot>` to place the held item inside the category. Use `/changecategorylogo <name>` to replace the display icon, `/categorylist` to inspect categories, and `/deletecustomkitcategory <name>` to remove one.

## Groups and events

### Parties

A party is a leader-managed group for inviting players and starting team matches. Create one with `/party create`, invite an online player with `/party invite <player>`, and accept with `/party accept`. Use `/party list` to check the roster, `/party leave` to leave, and `/party disband` to close the group as its leader. The default capacity is four. Grant a leader a larger `core.party.size.<number>` to raise the cap when their party is created.

- **Public parties.** `/party open` toggles whether other players can join without an invite. Opening requires `core.openparty`. Players use `/party join <leader>` or click the broadcast. Reopening a recently closed party has a one-minute cooldown that `core.openparty_cooldownbypass` can bypass.
- **Party settings.** `/party settings` lets the leader configure shuffle and auto-balance for drafting teams. Parties have their own lobby shortcuts for managing the group and opening play.
- **Party challenges.** `/party duel <leader>` opens a challenge to another party. The setup picks the ruleset and arena. The other leader can accept with the clickable invite or `/partyduelaccept <leader>`.
- **Intra-party matches.** `/party play` lets a party draft teams for a fight within the group. Choose balanced or asymmetric sizes, including custom sizes such as `2v5`. Select a shared ruleset, a selected custom kit, or per-player custom kits where available.
- **Party FFA.** The party play menu also offers a private free-for-all in a duel arena for two or more online party members. It tracks kills, remaining players, and elapsed time. This mode uses the party's match session. The public FFA worlds have their own entry and respawn flow.

### Public FFA worlds

`/ffa` opens an arena selector organized by kit and then by the world's saved entry spots. FFA worlds use a public kit as their base ruleset and can apply a player's saved FFA layout when they enter or rekit. On death, the player returns to the lobby and can use a **Teleport Back** feather to return to their last FFA location. FFA kill messages go to the arena and the lobby. FFA placeholders expose saved per-kit kills, deaths, streak, and kill-to-death ratio. The arena death handler increments the death counter and reapplies the surviving player’s kit when applicable.

Set up each FFA world with these steps.

1. Create the base kit under `/dueladmin kit` and load the FFA world in Paper. Include the exact world name in `ffa_worlds` so FFA listeners and placeholders recognize it.
2. Run `/ffaadmin kit create <kit>` to create an FFA profile and copy the base kit's flags, then `/ffaadmin setupworld <kit> <world>` to associate the profile with the world.
3. Stand at an entry point in the linked world, hold the icon, and run `/ffaadmin world teleport <slot>` to save that location in the selector. Use slots `0` through `8` for the spot menu.
4. Use `/ffakiteditor` or the FFA option in `/kiteditor` to rearrange a personal layout for that kit. The base profile controls building, regeneration, hunger, and health indicators.

### Lava Rising

Lava Rising is a survival event in a configured world and a bounded schematic arena. Players enter with `/lavaspawn` or `/event`. The lobby counts entrants, announces that players can join, and starts a countdown once the configured minimum is reached. If players leave and the count drops below the minimum, the countdown cancels. A late arrival during an active match enters as a spectator above the arena.

Players begin with the configured starter items. The match initially keeps PvP off and enables it later. Lava rises one level at a time inside the arena, the game checks who is still alive, and the last survivor can win before the time limit. An optional webhook announces the approaching start and match start.

| Setting or command | Purpose |
| --- | --- |
| `lava_rising.lava_rising_world` | Loaded Paper world used for the event lobby and match. |
| `/lavasetspawn` | Saves the waiting area players arrive at. Stand in the event world when setting it. |
| `/lavaarena create <name>` | Creates a record for an event arena. Use `list` to inspect recorded arenas or `delete <name>` to remove one. |
| `/lavaarena setspawn <name>` | Records the in-arena starting position. |
| `/lavaarena setschematic <name> <file>` | Selects a schematic from `plugins/Core/schems/lavarising/`. |
| `/lavaarena setpos1 <name>`, `/lavaarena setpos2 <name>` | Record the two arena corners used for bounds and the lava fill. |
| `/lavaforcestart` | Starts an event manually after the world and arena are ready. |
| `pregame.required_players`, `lobby_wait_seconds`, `reminder_interval_seconds` | Control the population threshold and timing for the pregame countdown. These keys are under `lava_rising.pregame`. |
| `pregame.spectate_height` | Vertical offset for late-join spectators, relative to the arena spawn. |
| `time_lava_start`, `time_lava_rise`, `time_pvp`, `time_finish` | Seconds until lava starts, seconds between rises, seconds until PvP, and the event time limit. |
| `starter_items` | Inventory at match start. Entries can use `MATERIAL` or `MATERIAL:COUNT`. |
| `webhook_url`, `webhook_warn_seconds` | Optional delivery endpoint and the countdown point at which the warning is sent. |

### Challonge tournaments

With `Tournaments` enabled and valid Challonge credentials, Core runs **single-elimination** brackets. A staff member with `core.tournament` and `core.tournament.admin` uses `/tournament create` to select a kit and choose **1v1**, **2v2**, or **3v3**. `/tournament join` signs up an individual or the party of a leader, subject to the event's team size. `/tournament leave` withdraws an entrant. During registration, a leader can withdraw their whole party.

`/tournament start` forms the teams and starts the bracket. Core checks Challonge for open matches, runs them in duel arenas, and sends results back to advance the bracket. Participating players get tournament-specific lobby shortcuts. `/next` moves the player to spectate another active tournament match when one is available. Keep a usable kit and duel arena available for the bracket.

## Lobby, teleportation, and server travel

### Lobby spawn and protection

- **Player arrival and shortcuts.** `/setspawn` stores a location. `/spawn` returns players there. Lobby hotbar items expose travel, the kit editor, queues, party actions, statistics, and settings as applicable. Active queues and tournaments can replace normal shortcut items with controls for the current activity.
- **Command items and destinations.** Hold an item and run `/setspawnitem <slot> <command…>` to store a command-bound spawn item. Hold a display item at a destination and run `/setgoto <slot>` to save a location in the `/goto` menu. `/store` displays a clickable store link when `network.store_url` is set.
- **Protected lobby.** Core restricts block placement and breaking, item pickup and drops, and interactions in the configured lobby. The Duels module also protects lobby players from combat damage. `/bypass` toggles bypass state for supported restrictions, subject to permissions. A vertical move above `lobby_max_height` returns the player to spawn.
- **Visibility and movement.** Use `/lobbyplayers on` to show other lobby players and `/lobbyplayers off` to hide them. Players with the relevant permissions can use lobby speed, jump boost, or elytra. Lobby elytra can include wind charges for movement. Supported inventory menus play different sounds for actions such as selecting, editing, or backing out.
- **Staff controls.** `/fly` toggles flight. `/gmc`, `/gms`, `/gma`, and `/gmsp` set the game mode for the sender or a named online player. `/vanish` hides a player from viewers without `core.vanish.see`. `/rules` shows the text in `lang.rules`.

### Teleport requests and RTP worlds

`/tpa <player>` asks to go to that player. `/tpahere <player>` asks them to come to you. The recipient has **30 seconds** to run `/tpaccept <requester>` or use the clickable request.

`/rtp` opens a selector for **Plains**, **Snowy**, **Desert**, **Badlands**, and **Mushroom**. Vise Core passes the selected world and player to **XenoWorldReset** by running `xwr rtp <world> <player>` as console. Use a XenoWorldReset build that accepts this command and configure the destination worlds in its `worlds.yml`. [Vise Core menu source](src/main/java/org/albi/core/commands/RTPCommand.java) · [XenoWorldReset world configuration](https://wiki.bitaspire.com/en/docs/xenoworldreset/configuration-files/worlds)

Use Vise Core's `c_worlds` list for world-local death announcements, return to the lobby on respawn, and the one-use `/back` teleport to the last death location in those worlds. With Duels enabled, combat listeners also handle behaviors such as explosive beds there.

### Travel and health across instances

Core writes a heartbeat to Redis every five seconds of server ticks. `/travel` uses those heartbeats to display same-region instances with a player count and online or offline status. A heartbeat older than ten seconds is considered offline. Register the matching instance IDs with a BungeeCord-compatible proxy so the menu can send players to those instances.

For direct region transfers, add entries under `network.travel.regions`. Each entry is keyed by the destination's `region`. The menu shows its display name, optional location and head texture, and the online player count known to Redis. The client transfers to the configured host and port.

```yaml
network:
  travel:
    regions:
      NA:
        name: "North America"
        location: "Toronto"
        host: "na.example.net"
        port: 25565
        texture_url: ""
```

`/health <server>` reads the latest heartbeat for an instance such as `eu-1`. It displays status, last heartbeat, player counts, TPS, uptime, memory, and host information. The Redis health stream also records world, JVM, and server metrics for external monitoring. Keep `core.health` with roles that should see server diagnostics.

## Scaling and deployment architecture

Vise Core runs the lobby, arenas, and combat inside each Paper process. You can **scale up** by giving a busy instance more CPU, RAM, or storage throughput and choosing the arena backend that fits. You can **scale out** by putting multiple Vise Core instances behind a proxy and sharing MySQL and Redis. With global matchmaking enabled, each added backend can receive queue entries, host matches, or do both.

### Vertical scaling on one Paper instance

Start with the resource that limits the actual workload.

| Resource | What drives it in Vise Core | What to change or measure |
| --- | --- | --- |
| CPU and tick time | Combat and entity processing, busy chunks, frequent schematic resets, Polar creation on the reservation path, and NPC activity. The ranked matcher compares waiting players in pairs per kit until it finds compatible opponents. | Put CPU headroom where the Paper tick thread and worker tasks need it. Look at tick-time percentiles during match starts and round resets. Keep the map footprint and loaded chunks modest before increasing concurrent capacity. |
| RAM and garbage collection | Loaded world chunks, the schematic clipboard cache, online profile objects, active match telemetry, and Polar origins and copies. One Polar map can hold several ready and reserved worlds at the same time. | For **high RAM and lower CPU headroom**, choose Polar on a compatible setup where ready copies avoid repeated pastes. For **low RAM and stronger CPU**, choose schematic slots. Check loaded worlds and heap after peak sessions drain as well as during warmup. |
| Local storage | Startup world loads, first-time schematic reads, Polar origin saves and temporary world cleanup, `data.dat`, and report exports. | Give world and report files enough space. Profile cold and warm runs separately. When an arena has trouble cleaning up, check which runtime worlds remain loaded before moving any files. |
| MySQL connections | Each Paper instance owns its own HikariCP pool. `mysql.pool_size` defaults to 20. Startup table work, profile misses and saves, and report history queries use it. | Count connection budgets across all processes. Four instances at the default can open up to 80 pooled MySQL connections in total, before other services. Increase the pool only if connection waits are the bottleneck and MySQL has capacity. |
| Redis connections and response time | The Jedis pool is capped at 16 connections per instance. Profile caching, leaderboards, travel discovery, health streams, sharing, linking, and optional global matchmaking use Redis. Global chat holds a subscription open. | Measure Redis latency and pool pressure along with Paper tick time. One worker scans queues per interval across the fleet. Each server publishes its own tickets and host status. |

#### Capacity controls

- **Schematic slots.** `maxarenas` caps the addressable slots for one named map on the current instance. Plan X/Z spacing and set `perline` before raising the cap. More slots let more matches reserve that map, but schematic work still passes through one shared paste queue.
- **Polar ready pool.** Start with a small `polar_preload` on each supported test instance. Both active reservations and ready copies are loaded worlds, and demand can raise the target above the configured minimum. An open origin, a reserved next round, and deferred cleanup all affect memory.
- **NPC practice.** `duels.bots.max_sessions` is an instance-wide cap. A session has its own combat loop and consumes arena capacity. Test NPC practice separately from player-only fights so its effect on tick time is visible.
- **Modules and worlds.** Disable features you do not use on that instance. An FFA-heavy server, a duel-focused server, and an event server can use different module and world configurations while their network features share backends. Enable Duels where another module needs its arena or kit storage. Paper's `view-distance` and `simulation-distance` affect chunk and entity work. [Paper server properties](https://docs.papermc.io/paper/reference/server-properties/)
- **Profile flushes.** `storage.flush_interval_seconds` controls the cadence of asynchronous dirty-profile sweeps. A shorter interval asks the backing services to persist changes more often. Budget that load alongside disconnect saves and other activity.

#### Load test the work that actually spikes

1. Measure a quiet baseline after startup. Record server tick time, used and committed heap, GC activity, loaded worlds and chunks, and backend latency.
2. Start simultaneous matches in one popular arena. Include a build kit that forces resets, spectators, and the next match on the same map. For Polar, test the first wave of reservations and the refill after the burst.
3. Add a separate run with peak NPC usage, FFA, or the event you expect to run alongside duels. Capture both steady-state play and transitions such as mass joins, round changes, world retirement, and profile flushes.
4. Change one setting, compare the measurements, and repeat. Use Paper's bundled spark profiler while the load is occurring. `/spark profiler start --timeout 600` captures a ten-minute profile. Watch MSPT as well as TPS so short stalls are visible. [Paper profiling guide](https://docs.papermc.io/paper/profiling/)

### Horizontal scaling across a network

```mermaid
flowchart LR
    P[Players] --> B[Proxy]
    B --> E1[Paper eu-1]
    B --> E2[Paper eu-2]
    B --> N1[Paper na-1]
    E1 --> R[(Redis)]
    E2 --> R
    N1 --> R
    E1 --> M[(MySQL)]
    E2 --> M
    N1 --> M
    E1 -.-> L1[Local maps and data]
    E2 -.-> L2[Local maps and data]
    N1 -.-> L3[Local maps and data]
```

Each instance has a unique `region` and `id_number`. Configure proxy entries with the lowercase names Core publishes, such as `eu-1` and `eu-2`. Same-region travel sends the proxy a `BungeeCord` channel `Connect` message. A regional button uses the client transfer API with the configured host and port. The menu shows stored heartbeats and player counts. Players or your proxy configuration choose where sessions start. [Travel implementation](src/main/java/org/albi/core/commands/TravelCommand.java)

| Shared by configured instances | Owned by each instance |
| --- | --- |
| Player profiles, statistics, ranked match records, Discord links, leaderboards, kit-share codes, chat, health discovery, and optional global queue tickets and handoffs. | Worlds, map assets, kit and arena definitions, event setup, spawn and menu locations, standalone queues, parties, live duels, tournaments, and arena reservations. |

- **Place players near their home pool.** The backend a player queues on determines their priority list and latency estimates. Route incoming players to their nearest lobby, then let the network queue choose a host. Standalone queues still require players to join the same instance. [Global matchmaking setup](#global-matchmaking)
- **Provision local content on each instance.** Deploy the intended kit and arena definitions plus the matching schematic or Polar files to each match host. For Polar, the origin file and Core's accompanying arena definition belong together. Set lobby and event locations for each local world setup.
- **Share backends deliberately.** MySQL stores durable rows and Redis is the live cache and messaging layer. Player keys and the `chat:global` channel are common to the deployment, so keep unrelated environments such as staging and production on separate services where they need isolation.
- **Allow for persistence timing.** Profiles are cached by UUID in each process. Network matchmaking flushes dirty profiles before a proxy transfer. Plan for one active proxy session per UUID. Travel and other proxy moves still use normal disconnect saves.
- **Plan restarts per instance.** A Paper restart clears its live matches, local reservations, and queue sessions. Its global tickets and unfinished handoffs expire in Redis. Allow matches to finish or move players to another instance when rolling out changes. [Profile and stats storage](src/main/java/org/albi/core/data/StatManagement.java)

### Heartbeats, health streams, and diagnosis

`StatRecorder` takes a snapshot every **100 ticks** by default, about five seconds at 20 TPS. It gathers Paper state on the tick thread and pipelines a Redis hash update and stream append from an asynchronous task. Each instance writes to `server:<id>:health:latest` and `server:<id>:health:stream`. The stream defaults to roughly **17,280 entries**, about one day of samples at the nominal cadence. Use `network.health` to change the cadence, retention, world details, or publishing. Set `network.travel.offline_after_seconds` above your expected heartbeat interval. Slower tick rates change the wall-clock interval.

The latest hash includes the timestamp, player count and cap, TPS over several windows, loaded chunks and players per world, JVM memory, uptime, threads, garbage collection, system architecture, and server version. `/health <server>` prints a subset in-game. For fleet monitoring, get the latest hash or sample the stream directly using your normal Redis connection settings.

```sh
redis-cli HGETALL server:eu-1:health:latest
redis-cli XREVRANGE server:eu-1:health:stream + - COUNT 5
```

The travel refresh uses Redis `SCAN` and rebuilds its menu snapshot every 100 ticks. A last heartbeat at least ten seconds old is treated as offline, so check the Paper tick rate and Redis connection as well as process uptime when a server disappears from the available destinations. Region totals use the online instances in the latest travel snapshot. [Health recorder](src/main/java/org/albi/core/data/StatRecorder.java) · [Travel and discovery](src/main/java/org/albi/core/commands/TravelCommand.java) · [Health command](src/main/java/org/albi/core/commands/HealthCommand.java)

## Profiles, statistics, and cosmetics

### Profiles and progression

`/profile [player]` shows account status, level, XP progress, total playtime, a social summary, and a link to detailed stats. Duels can award profile XP, and time spent on a server is tracked separately from time in a ruleset. The profile can also show ranks by game mode, region, and peak rank where returned by **PvPTiers**, **MCTiers**, and **Subtiers**. These providers are queried by player UUID and can independently return unavailable results.

Each tier provider has its own switch in `plugins/Core/config.yml`. The example below disables MCTiers and PvPTiers while keeping Subtiers. Set `tier_profiles.enabled: false` to turn off all three. Restart Paper to apply the changes. A disabled provider receives no UUID lookups and its button is hidden from `/profile`. Local profiles, stats, and ranked Elo remain available.

```yaml
tier_profiles:
  enabled: true
  providers:
    mctiers: false
    pvptiers: false
    subtiers: true
```

All three integrations are enabled by default, including when upgrading an older config. Set `lobby.join.warm_tier_profiles: false` to look up enabled providers only when someone opens `/profile`.

`/stats [player]` opens paged statistics for applicable kit and team-size combinations. The UI displays stored results such as wins, kills, deaths, streak, time, and other activity information when present. `/leaderboard` gives a separate view by stat, kit, and team size. It also accepts `/leaderboard <field> <game>` for a text result. `/rankedlb` shows competitive Elo standings.

Both leaderboard commands use Redis indexes of the 100 highest scores and can read the full leaderboard data from MySQL when an index is missing or Redis is unavailable. Core rebuilds the indexes from MySQL as boards are opened, so saved scores dropped by older Redis trimming reappear. If both services are unavailable, the instance serves its last successful result for that board when one is cached.

Players can limit their profile and stats visibility in `/settings`. Staff with `core.profile.bypass` can open otherwise private profiles.

### Armor trims, kill effects, and particles

- **Armor trims.** `/armortrim` selects a trim independently for helmet, chestplate, leggings, and boots. There are 18 preset pattern-and-material combinations. The menu shows available and locked options, and lets players clear their selections. The saved choices are applied to supported kit armor. Use `core.armortrim.<combo>` or `core.armortrim.*` for unlocks.
- **Kill effects.** `/deatheffects` selects a particle and sound effect that plays on supported kills. The catalog includes Supernova, Black Hole, Angel Wings, and other effects. Players can reset the selection. Grant `core.deatheffect.<effect>` or `core.deatheffect.*` for unlocks. Viewers can mute the effects through their own settings.
- **Ranked particles.** Ranked kits can show tier-colored hit particles during competitive fights. Each player controls their ranked particle preference in `/settings`.

### Player settings

| Settings tab | Options |
| --- | --- |
| General | Profile visibility, whether others can spectate, lobby elytra, kill effect visibility, ranked hit particles, lobby speed, and lobby jump boost. |
| Social and Chat | Receive global chat, broadcast chat to other servers, accept direct messages, accept friend requests, and play sounds for chat mentions. |
| Matchmaking | Incoming duel, party, and party duel invites. |

## Friends, chat, and Discord

### Friends, direct messages, and blocks

`/friends` opens a menu for friends, pending requests, and online players who can be added. Use `/friends add <player>`, `accept <player>`, `deny <player>`, or `remove <player>` for the same actions from chat. Friends are saved with the player profile. The friend list and tab-list placeholders show locally visible friend presence and pending requests. Pressing sneak in the lobby toggles the friends and global tab modes for compatible displays.

`/msg <player> <message>` sends a private message to an online player, and `/r <message>` replies to the latest conversation. Use `/block <player>` and `/unblock <player>` to manage the block list. Friendship can bypass certain incoming message or invite toggles. Block-list checks still apply to the actions that use them.

### Chat formatting and cross-server chat

- **Formatting.** Configure `chat` with `{player}` and `{chat}`. PlaceholderAPI can supply values for the formatting template, and `core.chat.color` grants player color formatting. Enabled mention feedback highlights names and plays a sound for the mentioned player.
- **Context-based routing.** Core accounts for worlds and active duels when deciding who sees a message. The global-chat player setting lets recipients see messages beyond their current context. `network.global_chat` enables the Redis bridge for cross-server delivery. Player settings control whether a player broadcasts messages through the bridge and whether they receive them.
- **Moderation.** Add prohibited words or phrases to `bad_words`. The filter checks raw and normalized text, including common character substitutions, and uses a fuzzy comparison for some longer phrases. It applies to local messages and messages received through the bridge.

### Discord

Enable `CriticalDiscord` and set `network.discord_url` for a clickable invite from `/discord`. `/discord link` generates a short-lived code to redeem through a companion Discord integration. `/discord check` shows the current link and `/discord unlink` removes it. Pending codes live in Redis for five minutes. Completed links use the MySQL `player_discord_links` table. Ranked and Lava Rising webhooks are separate notification features and can be configured independently.

## Combat fixes and server operations

### Anti-Ghost Arrow

A ghost arrow is a projectile that appears to pass through a target because both the projectile and the player move between collision checks. Core tracks ordinary and spectral arrows, looks at the arrow's traveled segment and the player's swept hitbox, then attempts to apply an eligible missed collision. The service tracks which targets already consumed a hit from an arrow to avoid repeating it.

- `collision.hitbox_expansion` and `segment_expansion` set the margins for the hitbox and projectile path. `sub_steps` controls interpolation within a tick, and `relative_velocity_expansion` caps extra margin for fast-closing targets.
- `minimum_travel_distance` ignores nearly stationary arrows. `owner_grace_ticks` gives newly launched arrows a grace period around their shooters, and `tracking.max_tracked_ticks` bounds the tracking lifetime.
- `targets` controls whether invulnerable, creative, or spectator players are excluded. `remove_arrow_on_forced_hit` removes the arrow after a recovered hit to avoid the visual bounce. `exempt_worlds` disables checks in selected worlds, and `debug` logs diagnostics. [Anti-Ghost Arrow configuration](src/main/resources/anti-ghost-arrow.yml)

### Knockback Synchronization

The knockback service samples the ping of recently hit players and uses it when adjusting eligible velocity events. It considers recent damage ticks and nearby terrain before applying its correction. The goal is to make the timing of knockback more consistent as latency varies.

- `runnable.enabled`, `runnable.interval`, and `runnable.combat_timer` control ongoing ping sampling and how long a combat state is kept. `spike_threshold` controls ping spike smoothing, and `ping_offset` is the baseline latency allowance.
- `enable_offground_synchronization` opts into airborne adjustment. `sync.max_ground_distance`, `sync.max_ray_distance`, and `sync.max_no_damage_ticks` tune terrain checks and damage timing. `sync.tick_rate` supplies the tick rate used in the calculation.
- Add worlds to `exempt_worlds` to leave their knockback without Core's adjustment. Turn on `debug` to inspect decisions on a staging server. [Knockback Synchronization configuration](src/main/resources/knockback-synchronization.yml)

### World and item rules

At server load, Core disables weather cycling, natural mob spawning, and advancement announcements in the lobby. In other worlds loaded at that point, it also sets Hard difficulty, keep-inventory, immediate respawn, and a fixed daylight cycle. These rules apply to every world loaded outside the lobby at that point, including worlds kept on the same Paper server for other gameplay. Core suppresses default join and quit announcements.

Gameplay listeners reset player state at key transitions, use world-local death messages for supported modes, and apply kit-specific building and food rules. The plugin has special handling for TNT and explosive beds in applicable modes, creeper eggs that spawn short-fuse ignited creepers, and a ten-second cooldown for consuming the custom food based on raw gold.

### Announcements and restart controls

`announcements.enabled` toggles rotating tips. Set the content in `announcements.messages` and the spacing in `announcements.interval_seconds`, which has a 30-second floor. The default audience is the lobby. `announcements.worlds` targets named worlds or `"*"` for every world. `shuffle: false` rotates through the list in order. Separate settings control whether active duels or Lava Rising participants receive tips.

`autorestart.enabled` starts a countdown on server startup using `autorestart.interval_hours`. The bundled default is off. Staff can use `/autorestart time` or `status` to see the countdown, `set <duration>` to schedule a shutdown, `cancel` to clear it, or `now` for the delay in `autorestart.now_delay_seconds`. Durations can use units such as `30m` or `10s`. Use `autorestart.warning_seconds` to choose the warning milestones. A server panel, service manager, or other supervisor launches Paper again after Core shuts it down.

## Configuration, data, and upgrades

### Main configuration reference

Existing installations can add individual settings to `plugins/Core/config.yml`. Omitted settings use the bundled defaults. Restart Paper to apply `config.yml` changes. `/coreconfig reload` applies `gui.yml` and `lang.yml` edits. [Bundled main configuration](src/main/resources/config.yml)

| Setting | What to edit |
| --- | --- |
| `name`, `ip` | Display values used by messages and PlaceholderAPI. Set `ip` to the address you want to display. |
| `region`, `id_number` | Parts of the instance ID. For example, `EU` and `1` produce `EU-1`, with lowercase used in Redis keys. |
| `lobby_world`, `lobby_max_height` | Lobby selection and vertical return-to-spawn threshold. |
| `lobby.join`, `lobby.protection` | Join teleport and reset, join and quit broadcasts, external tier lookups on join, block and item restrictions, lobby PvP, player heads, and the height guard. |
| `tier_profiles` | Master switch and individual switches for MCTiers, PvPTiers, and Subtiers lookups and profile buttons. |
| `lobby.hotbar`, `lobby.movement`, `lobby.elytra`, `lobby.interaction`, `lobby.wind_charges` | Managed shortcuts, item cooldown, Speed and Jump Boost levels, elytra gear, click-to-duel, shulker editor, allowed lobby items, physical triggers, and wind charge behavior. |
| `world_rules` | Lobby and other world profiles, per-world exceptions, gamerules, difficulty, and whether to apply settings to worlds loaded after startup. |
| `c_worlds`, `ffa_worlds` | Names of RTP-style gameplay worlds and public FFA worlds. |
| `modules` | Feature switches. Review the dependencies in [module setup](#modules-and-requirements). |
| `mysql` | Host, port, credentials, pool sizing, connect and socket timeouts, and the maximum wait for a connection. A blank `ssl_mode` uses the legacy `ssl` flag. For TCP TLS verification, use a supported Connector/J mode such as `VERIFY_IDENTITY` with a trusted server certificate. |
| `redis` | Enable Redis, opt in to experimental MySQL failover, and set host, port, optional password, pool size, idle connection target, socket and connection-borrow timeouts, and TLS. With TLS enabled, `ca_file` can point to a custom CA. A blank value tries `certs/ca.crt` first, then the JVM trust store. |
| `storage.experimental_sql_shared_state` | **Experimental and not recommended.** Enables kit sharing and current server heartbeats in `vise_shared_keys` when Redis is disabled or the optional failover is active. |
| `storage.flush_interval_seconds` | Frequency of periodic profile and stats flushes. The bundled default is 600 seconds. |
| `storage.shutdown_timeout_seconds` | Maximum wait for pending writes and the final MySQL flush before storage closes. The default is 15 seconds. |
| `storage.recovery_namespace` | Shared name for Redis recovery requests. Leave blank to use `mysql.database`, or give every server in this deployment the same unique value. |
| `network` | Redis chat bridge, global matchmaking and spectating, travel discovery and health sampling, `/store` and `/discord` links, and direct travel destinations. |
| `announcements`, `autorestart` | Lobby tips and the startup restart schedule. |
| `use_polar_worlds`, `polar_preload` | Toggle the PolarPaper backend and set its preload target. |
| `duels` | TNT fuse, selectable rounds, countdowns, queue timings, schematic paste spacing, Polar background operations, spectator privacy checks, direct challenge telemetry, and NPC settings. |
| `ranked` | Starting Elo, rating factors, placements, tier divisions, widening Elo windows, ranked queue and arena wait timings, telemetry sampling, history retention, webhook, and report link. |
| `party` | Default size, shuffle and auto-balance defaults, public reopen cooldown, and FFA countdown and scatter phases. |
| `teleports` | `/tpa` and `/tpahere` expiration and the list of XenoWorldReset targets shown in `/rtp`. |
| `combat`, `cosmetics` | Consumable cooldowns, creeper egg behavior, per-kit damage boosts, extra blocked kit items, and kill effect availability, unlock levels, and sound. |
| `lava_rising`, `tournaments` | Lava Rising world, timing, starting items and webhook, plus Challonge credentials for tournaments. |
| `33_percent_boost` | Legacy list of public kit IDs using `combat.legacy_damage_boost_percent`, which defaults to 33. |
| `mending_rngs` | Legacy kit list. For an effective Mending repair override, set `mending_rng` on the kit metadata. |
| `guis.duel.kitselector.rows` | Logical page size for saved kit positions in direct duel and party challenge pickers. Use `gui.yml` to set the displayed row count and layout. |
| `cooldowns` | Seconds between uses for commands that check a cooldown. Use the registered command name as the key, such as `duel`, `travel`, or `automatecombat`. |
| `permissions` | Permission nodes used by internal lobby and game-mode checks. Bukkit also checks the registered command permissions in `plugin.yml`. |
| `chat`, `chat_options`, `bad_words` | Public chat formatting, the color permission, mention highlighting and sounds, and moderation terms. Player messages are in `lang.yml`. |
| `max_entities`, `lava_rising.auto_interval_ticks` | Legacy config entries. The current listeners use the creeper-egg behavior and the player-count-based Lava Rising countdown described above. |

### Per-world policies

`world_rules.lobby` applies to the configured lobby and `world_rules.other` applies to the remaining worlds at startup. Use `world_rules.worlds.<world>` for exact world names. Set `enabled: false` on `world_rules` to leave all rules to Paper. The same option on one named world skips that world. A named profile can set `inherit: false` to apply just its own difficulty and gamerules. For a single inherited value, `inherit` leaves the world's current setting in place. Set `apply_on_world_load: true` if the policy should also apply to worlds loaded later.

```yaml
world_rules:
  worlds:
    survival:
      enabled: false
    practice:
      inherit: false
      difficulty: NORMAL
      gamerules:
        doDaylightCycle: false
        randomTickSpeed: 0
```

For a server that keeps its own inventory and spawn on join, turn off `lobby.join.reset_player`, `lobby.join.teleport`, and `lobby.hotbar.enabled`. Use `lobby.elytra.enabled: false` when the lobby should also leave equipment alone.

### Hotbar controls

`gui.yml` has distinct nine-slot layouts under `menus.lobby.hotbar`, `hotbar-queued`, `hotbar-party`, and `hotbar-tournament`. Built-in controls follow their IDs when moved, renamed, or restyled. Common item settings such as `material`, lore, `permission`, `glow`, and model data work here too. `custom-buttons` adds shortcuts. A `commands` list replaces the built-in action or supplies a custom action. Core runs each command as the clicking player, with their existing permission checks.

```yaml
menus:
  lobby:
    hotbar:
      buttons:
        ranked-queue:
          slot: 2
          material: EMERALD
          name: "&bRanked Matches"
      custom-buttons:
        rules:
          slot: 6
          material: BOOK
          name: "&eServer Rules"
          commands: [rules]
```

Set `enabled: false` on a hotbar layout to leave the hotbar alone when that layout is selected. Each layout can override its item cooldown with `cooldown-seconds`. Built-in shortcuts use the lobby cooldown by default, and party shortcuts default to zero. Keep each layout within its nine available slots.

### Changing gameplay and destinations

- `duels.countdown` controls the initial and inter-round timers, ready-up, and the post-match return delay. Ranked matches use `ranked.countdown_seconds`. Tournament matches have a separate countdown. `party.ffa` controls its pregame and scatter phases.
- `ranked.queue.chat_ticks: 0` silences the repeating queue chat. The action bar keeps its own update interval. `ranked.matchmaking.max_elo_range: 0` allows the window to continue widening. Set the same rating and matchmaking settings across a Redis-connected fleet.
- `combat.food_cooldowns` assigns a delay in seconds to each consumable material. `combat.kit_damage_boosts` maps kit IDs to percentage overrides. `0` overrides a legacy or kit metadata boost. `combat.additional_blocked_kit_items` adds to the built-in kit restrictions.
- `/rtp` sends players to the XenoWorldReset destination in each entry's `target` field under `teleports.rtp.destinations`. Use the entry ID under `menus.travel.rtp.buttons` in `gui.yml` to move or style the button. An empty list closes off the destination picker.
- `duels.bots.defaults` sets the starting practice bot name, invulnerability, reaction, aim, movement, range, strafe, and shield settings. The in-game bot menu can still adjust settings per session.
- `cosmetics.kill_effects.unlock_levels` changes requirements by lowercase effect ID, such as `electric_storm`. `disabled_effects` hides individual effects and prevents their playback. Player preferences continue to control visibility where effects are enabled.

### Translating messages with lang.yml

Open `plugins/Core/lang.yml` to translate command feedback, invites, queue updates, match titles and action bars, kits, staff tools, and other player messages. Shared keys such as `spawn` and `cooldown` are at the top. Feature-specific text is grouped under `messages` in the [bundled language file](src/main/resources/lang.yml). Clickable invites and links retain their existing actions after you edit their text.

- Quote strings that contain `&`, `#`, or YAML punctuation. Use `&` color and formatting codes, `\n` in double-quoted strings for newlines, and `""` to silence a standalone message. Some titles or action bars have separate lines, so clear both keys if you want both lines empty.
- `{1}`, `{2}`, and later tokens stand for values supplied by the feature. For example, `messages.ranked-queue-module.s-range` receives elapsed time and the current Elo window. You can reorder or repeat the tokens. Installed PlaceholderAPI expansions can resolve `%placeholder%` in templates, using the receiving player when available.
- On upgrades, Core copies string settings from the old `config.yml` `lang` section when it first creates `lang.yml`. It resolves an explicit `lang.yml` key first, then looks in the legacy section, then uses the bundled default. A partial language file works and picks up newly shipped keys automatically.

```yaml
spawn: "&aBack in the lobby."
messages:
  match-hud:
    fight: "Go!"
    skip-countdown: "&eSneak to skip &7({1}/{2} ready)"
  ranked-queue-module:
    s-range: "&aRanked queue &7| &f{1}s elapsed &7| &fElo ±{2}"
```

### Building menus with gui.yml

Open `plugins/Core/gui.yml` to restyle and reorganize the inventory screens for duels, matchmaking, parties, kits, NPC practice, travel, profiles, settings, cosmetics, events, and more. The [bundled menu catalog](src/main/resources/gui.yml) lists menu and control IDs. Edit `menus` to change the layout and icons. Edit `text` to translate shared labels and lore that features build from live data, such as the queued player count, kit editor hints, or a setting's enabled and disabled states.

Run `/coreconfig reload` after editing either file, then reopen any menus you are testing. Both files must parse before Core applies the update. A YAML syntax error keeps the previous configuration active and logs the affected file. Existing servers can keep a partial file, since omitted settings inherit bundled defaults.

| Option | What it controls |
| --- | --- |
| `title`, `format`, `rows` | Inventory title, `legacy` or `minimessage` formatting, and one to six rows. Rows are counted from the top, with nine slots per row and slot `0` at the upper left. |
| `buttons.<id>` | The layout and item options for a built-in control. Modify or move the control by ID while preserving its action. |
| `content-slots`, `entries` | The order of slots for generated content and shared item options for those entries. Omit `content-slots` to use original positions where they fit. |
| `entries-by-id.<id>` | An override tied to the underlying kit or arena name in supported duel and NPC menus, region or server ID in travel, or effect ID in kill effects. IDs are lowercase, with runs of spaces and punctuation replaced by `-`. |
| `slots.<original-slot>` | A per-slot override with the highest priority, applied to the original position before your layout moves the item. |
| `background`, `decorations.<id>` | Cosmetic items behind controls and entries. With no `slots` list the decoration fills available slots. Set `enabled: false` on `background` to clear legacy filler in that menu. |
| `pagination` | Style the overflow previous and next buttons and set their slots. Set `title-suffix` per menu or at the top level. Overflow pages appear when the rendered contents run out of space, including when `content-slots` is deliberately small. Active page navigation reserves its slots, and colliding controls are placed in available slots. |
| `sounds` | Menu-wide `open`, `click`, and `close` sounds. Set a Bukkit sound name, with optional `-volume` and `-pitch` keys. Use `none` to silence that menu sound. A button can override clicks with `sound`, `sound-volume`, and `sound-pitch`. |
| `custom-buttons.<id>` | Add a shortcut with a list of `commands`, an optional `message`, and optional `close-on-click`. Commands run in order as the clicking player with their existing permissions. |

Buttons and entries accept `slot`, `enabled`, `permission`, and `clicks` where applicable. Use Bukkit click names such as `LEFT`, `RIGHT`, and `SHIFT_LEFT`. Item options include `material`, `amount`, `name`, `lore`, `glow`, `hide-tooltip`, and `item-flags`. Resource packs can use `custom-model-data` or `item-model`, such as `my_pack:menus/rules`. For compatible items, `leather-color` accepts an RGB hex color and `head-texture` accepts a `textures.minecraft.net` skin URL.

Titles expose `{viewer}`, `{player}`, `{page}`, and `{pages}`, plus positional tokens where the menu has its own logical pages or a target player. Icon templates also expose `{slot}`, `{original_slot}`, `{name}`, `{material}`, `{amount}`, and individual lore lines such as `{lore_1}`. A line containing only `{lore}` inserts all of the original lore. This keeps values generated by the feature, such as a kit's Elo or queue count, inside your custom design.

This example moves and restyles the profile visibility switch, creates a rules shortcut, and styles overflow navigation in `/settings`.

```yaml
menus:
  settings:
    main:
      rows: 2
      title: "<gradient:#49dcff:#ac61ff>Player Preferences</gradient>"
      format: minimessage
      background:
        material: BLACK_STAINED_GLASS_PANE
        name: " "
      decorations:
        header:
          enabled: false
        divider:
          enabled: false
      buttons:
        profile-visibility:
          slot: 10
          material: ENDER_EYE
          name: "<aqua>Profile and Stats</aqua>"
          lore: ["<gray>Editing settings for {viewer}</gray>", "{lore}"]
          clicks: [LEFT]
      custom-buttons:
        rules:
          slot: 5
          material: BOOK
          name: "<gold>Server Rules</gold>"
          commands: [rules]
          close-on-click: true
      pagination:
        next:
          slot: 17
          material: SPECTRAL_ARROW
          name: "<green>More options</green>"
      sounds:
        open: BLOCK_NOTE_BLOCK_PLING
        open-pitch: 1.2
```

Editable item inventories have dedicated settings. The personal kit storage editor stays at 54 slots and the lobby shulker editor stays at 27 so every saved item remains accessible. The trash editor accepts custom rows and a configurable finish control. The offhand slot of the saved kit layout editor is movable and always contains the actual item being edited.

### Environment variables

Set credentials in the environment of the Paper process. A defined variable takes precedence over YAML. An empty value can disable an optional webhook or clear an optional password. Required connection fields still need valid values.

| Environment variable | YAML key |
| --- | --- |
| `VISE_MYSQL_HOST` | `mysql.host` |
| `VISE_MYSQL_PORT` | `mysql.port` |
| `VISE_MYSQL_DATABASE` | `mysql.database` |
| `VISE_MYSQL_USER` | `mysql.user` |
| `VISE_MYSQL_PASSWORD` | `mysql.password` |
| `VISE_REDIS_HOST` | `redis.host` |
| `VISE_REDIS_PORT` | `redis.port` |
| `VISE_REDIS_PASSWORD` | `redis.password` |
| `VISE_RANKED_WEBHOOK_URL` | `ranked.webhook_url` |
| `VISE_LAVA_WEBHOOK_URL` | `lava_rising.webhook_url` |
| `VISE_CHALLONGE_USERNAME` | `tournaments.challonge_username` |
| `VISE_CHALLONGE_API_KEY` | `tournaments.challonge_api_key` |

### Redis and outage options

Redis is optional on one Paper instance and strongly recommended for any multi-server deployment. New configs start with `redis.enabled: false`. Older configs that omit the key retain Redis access and reconnect behavior. Restart Paper after changing these settings. [Bundled configuration](src/main/resources/config.yml)

| Deployment | Configuration | Behavior |
| --- | --- | --- |
| Standalone | `redis.enabled: false` | Local queues, duels, spectating, and gameplay use Paper. MySQL stores player data, ranked history, and leaderboard scores. Manually configured travel destinations still work. |
| Multi-server, recommended | `redis.enabled: true` | Redis powers global queues, cross-server spectating and chat, Discord link codes, kit sharing, and discovery. During an outage, Core reconnects in the background. Durable data and leaderboards use MySQL, local play continues, and global queues apply their normal lease rules before matching locally. |
| MySQL shared state only | `redis.enabled: false` and `storage.experimental_sql_shared_state: true` | Enables shared kit codes, `/health`, and automatic travel discovery between instances using the same MySQL database. Use only if you do not have Redis. |
| MySQL failover | `redis.enabled: true`, `redis.fallback_to_mysql: true`, and `storage.experimental_sql_shared_state: true` | Kit sharing and current heartbeats switch to MySQL when Redis is unavailable. Redis resumes those writes automatically when it reconnects. |

> **EXPERIMENTAL. Not recommended as a Redis substitute.** If Redis is unavailable, a smaller server with modest traffic can use this for occasional kit sharing and server discovery. The `vise_shared_keys` table stores expiring key-value records and adds work to the same MySQL service that saves profiles. Redis is strongly recommended when expanding to multiple servers.

Redis and MySQL share codes are kept separate. Codes created through MySQL begin with `S` and remain redeemable through their 30-day lifetime while the MySQL shared state setting stays enabled, even after Redis reconnects. Existing Redis codes can be redeemed again when Redis returns. Redis remains required for cross-server matchmaking, spectating, chat, Discord code redemption, and health history streams. [Shared state code](src/main/java/org/albi/core/data/SharedState.java) · [Matchmaking outage behavior](#redis-outages-and-interrupted-transfers)

### Help Me Please

**Help Me Please** lets a Paper server that loses one database ask for help through the other. Each instance checks its own connections. A peer with working access claims the request, so recovery also works when a single Paper server loses connectivity while the databases themselves are healthy.

| What failed | Where writes go | What stays available |
| --- | --- | --- |
| MySQL and Redis are unreachable | The affected server loads local profile snapshots and keeps new profile, stat, and match writes in `plugins/Core/recovery/outbox/`. | Local gameplay and queues continue. Core pauses transfers that require a saved profile. A player with no available snapshot starts with a temporary local profile. |
| Redis is healthy and all servers lose MySQL | Each server writes its local outbox and sends persistent recovery requests to Redis. | Local gameplay continues. The first instance to regain MySQL can replay pending requests. |
| Redis is healthy and only some servers lose MySQL | Affected servers publish their urgent dirty profile, stat, and match saves into the Redis outbox. A peer can commit them to MySQL and acknowledge each write. | Players on affected servers can play locally. Healthy peers can continue hosting network matches. |
| MySQL is healthy and all servers lose Redis | MySQL remains authoritative. Local queues wait out any outstanding network claims. The optional experimental MySQL shared state provides kit codes and current heartbeats. | Local play, saved profiles, ranked history, and MySQL or cached leaderboards stay available. |
| MySQL is healthy and only some servers lose Redis | Affected servers ask via `vise_redis_relay` in MySQL. A healthy peer claims a short lease and republishes the latest server heartbeat or an authoritative profile, stats, or match cache key to Redis. | Healthy peers can see the relayed heartbeats. Affected servers still use local queues and the optional experimental kit-sharing fallback while disconnected. |

```mermaid
flowchart TD
    Check["Check this server's database connections"] --> SQL{"Can it reach MySQL?"}
    SQL -->|Yes| RedisA{"Can it reach Redis?"}
    SQL -->|No| RedisB{"Can it reach Redis?"}
    RedisA -->|Yes| Normal["Save to MySQL and use Redis for network features"]
    RedisA -->|No| SqlMode["Save to MySQL and run local matches"]
    SqlMode --> Relay["Ask through MySQL to publish supported Redis keys"]
    SqlMode --> Fallback["Use experimental kit codes and heartbeats if enabled"]
    RedisB -->|Yes| RedisMode["Keep dirty writes on disk and ask through Redis"]
    RedisB -->|No| Local["Load local snapshots and journal writes on disk"]
    Local --> Restore["Replay pending writes after MySQL returns"]
    RedisMode --> Restore
```

#### How the handoffs work

- **MySQL writes requested through Redis.** The outbox entries have no expiration. A peer commits the SQL write and its receipt together so another peer can retry safely. Core removes acknowledged requests and replays local files after recovery. Put Redis on persistent storage and configure AOF and an eviction policy that preserves recovery keys if you need Redis to survive a process restart. Keep the local outbox through outages and restarts.
- **Redis publication requested through MySQL.** The SQL table carries a fixed set of request types. A peer with both connections claims one, publishes it, and clears the exact request it handled. Heartbeats only move forward in time. Profile, stats, and match cache requests are rebuilt from MySQL when a peer processes them. Redis still handles live chat delivery, matchmaking leases, spectator transfers, Discord linking codes, and health streams directly.
- **Conflicting profiles.** Replay checks the saved version the change was based on before replacing a player record. If another server has already changed the record, or if a temporary profile was created for a player who already has saved data, Core retains the pending file and leaves the saved record intact. Keep the `recovery/` directory and reconcile those histories before removing any retained writes.

```mermaid
flowchart LR
    subgraph Save["Source lost MySQL"]
        Disk["Persist dirty write to the local outbox"] --> AskRedis["Send the recovery request through Redis"]
        AskRedis --> CheckBase{"Can a peer apply it to the saved version?"}
        CheckBase -->|Yes| Commit["Commit write and receipt in MySQL"]
        Commit --> Ack["Acknowledge through Redis and clear queued files"]
        CheckBase -->|Conflict or no peer| Keep["Retain pending data for replay or reconciliation"]
    end
    subgraph Publish["Source lost Redis"]
        AskSql["Insert typed publication request in MySQL"] --> Claim{"Can a peer with Redis claim it?"}
        Claim -->|Yes| Put["Publish latest heartbeat or refresh a cache key"]
        Put --> Finish["Remove the acknowledged SQL request"]
        Claim -->|No| Wait["Retry until the request expires"]
    end
```

#### During an outage

The console logs a recovery warning when the connection state changes and repeats it about every 30 seconds until the service returns. When MySQL is unreachable, dirty data is flushed to the recovery outbox on the shorter recovery cycle, alongside disconnect and shutdown saves. Check available disk space, keep servers on the same recovery namespace, and grant the configured MySQL user permission to create and use the recovery tables. Use a separate Redis service or database and recovery namespace for unrelated deployments. After restoring a service, check that pending-write warnings clear before moving affected players between servers. [Recovery code](src/main/java/org/albi/core/data/StatManagement.java)

### What gets stored

| Where | Data |
| --- | --- |
| `plugins/Core/data.dat` | Local plugin data, including spawn points, menu destinations, arenas, public kit definitions, categories, and event setup. Core writes the file through a temporary file when saving on shutdown. If the file cannot be read during startup, Core disables itself so an operator can recover the file. |
| MySQL | `player_stats` stores player profiles, statistics, and the last known player name. `ranked_matches` stores match history and telemetry. The optional Discord module stores completed account links in `player_discord_links`. |
| MySQL shared state (experimental) | `vise_shared_keys` stores temporary kit codes, their payloads, and the latest server heartbeats. Expired rows are cleaned up in the background. |
| MySQL recovery | `vise_recovery_receipts` records completed recovery writes. `vise_redis_relay` temporarily holds typed Redis publication requests. |
| Redis | Cached profiles, stats and match records, sorted-set leaderboards, kit-sharing codes, Discord link codes, chat messages in transit, matchmaking tickets and handoffs, server heartbeats and health streams, and the shared recovery outbox. |
| `plugins/Core/recovery/` | Local profile snapshots and pending writes. Keep this directory with server backups. |
| Memory | Online profile state and active gameplay state such as queues, parties, pending teleport requests, and arena reservations. A Paper restart clears active sessions. |
| `plugins/Core/reports/` | HTML files produced by explicit report export commands. |

#### Data flow and cache lifetime

The online player wrapper brings together active state, the saved profile, and statistics. On a process-local cache miss, Core checks MySQL for the saved JSON and can fall back to the Redis snapshot. Periodic flushes and disconnect saves commit dirty changes to MySQL, then update Redis. On a graceful stop, Core waits for already queued writes and performs a final MySQL flush of dirty profiles and statistics within the configured timeout.

| Redis key or channel | Purpose |
| --- | --- |
| `main:<uuid>`, `stats:<uuid>` | Cached profile and stats snapshots. Durable copies live in separate JSON columns in `player_stats`. |
| `lb:ranked:elo`, `lb:names`, `lb:<game>:<metric>` | Sorted rating and ruleset metric indexes, with a name lookup hash. |
| `match:<id>` | On-demand telemetry cache. Match history queries read MySQL, and an individual match lookup checks Redis first. |
| `sharedkits:getkit:<code>`, `sharedkits:getcode:<uuid>:<slot>` | Thirty-day kit snapshots and reusable share codes. |
| `link-code:<code>`, `link-wait:<uuid>` | Five-minute Discord linking state. Completed links use MySQL. |
| `chat:global` | Live Redis Pub/Sub channel. Subscribers receive messages as they arrive. |
| `vise:mm:v1:{<namespace>}:*` | Global queue tickets, per-player claims, host availability, pending match handoffs, and spectator routes and requests. |
| `server:<id>:health:latest`, `server:<id>:health:stream` | Current server heartbeat and rolling health history. |

Core attempts to open MySQL and prepare the tables during startup, then retries in the background if setup fails. On upgrade, `ALTER` on `player_stats` lets Core add a column for leaderboard names. On schemas without that column, Core can still display scores using names known to Redis or Paper, with UUIDs for players it cannot identify. With Redis enabled, the client checks for a PING response and retries failed connections. [Storage and connection code](src/main/java/org/albi/core/data/StatManagement.java) · [Player profile model](src/main/java/org/albi/core/data/MainPlayer.java)

### Upgrading and troubleshooting

1. Back up `plugins/Core/`, the arena worlds, MySQL, and any Redis data you rely on. Stop Paper before swapping JARs.
2. Keep the existing `plugins/Core/` directory and merge new keys from the shipped configuration. In particular, recheck `network` and `duels.bots`, and set your own store link, Discord invite, transfer hosts, and hosted report URL where needed.
3. Check startup logs for storage and skipped-module messages. Verify MySQL table permissions, the Redis endpoint if enabled, the configured world names, and FAWE or WorldEdit if Duels should be active.
4. Join the lobby and test one full match from queue or challenge through arena reuse. For FFA, test the exact configured world name and a saved entry spot. For network features, check `/health <server>` on both instances and confirm they use the expected shared backend and distinct instance IDs.

The repository includes the Gradle Wrapper, a pinned GitHub Actions workflow, build-time plugin version metadata, and shaded runtime dependencies for the JAR. Paper and integration plugins run alongside it. [Build script](build.gradle.kts) · [Main configuration](src/main/resources/config.yml)

### Diagnosing common deployment problems

| Symptom | Where to look |
| --- | --- |
| Core disables itself during startup | Check the logged storage error, the configured SQL database, and the user's table permissions. For a `data.dat` read error, use the backup from that instance. |
| Duel commands show no usable arena | Confirm Duels started, a public kit and active arena exist, the schematic world is loaded under its exact name or the compatible Polar origin is readable, and the map has spare reservations. |
| A pasted map looks stale or overlaps another match | Check the schematic filename and the clipboard cache, slot spacing, relative corner coordinates, and the logs for queued paste failures. Verify one reset before increasing concurrency. |
| Polar maps fail to generate or refill slowly | Check the installed Paper and PolarPaper API versions, the origin file path, and the `[PolarArena]` and `[PolarQueue]` logs. Profile immediate generation and check whether retired worlds still contain players. |
| Two waiting players never match | Check the kit and ranked Elo range, player eligibility, and available arenas. For global queues, check the namespace, registered proxy IDs, pool maps, ping limits, and the `[Matchmaking]` Redis status. Standalone queues match within one Paper instance. |
| A server is online but missing in travel | Check `/health <server>`, the latest Redis heartbeat, `region` and `id_number`, and the corresponding proxy registry entry. |
| A report link fails to load | Verify the record still exists, run `/rankedreport <match_id>` to create the file, and check how your external host serves `ranked.report_url_template`. |

## Codebase map

| Area | Where to start | What it owns |
| --- | --- | --- |
| Bootstrap and module gates | [Core](src/main/java/org/albi/core/Core.java) · [ModuleManager](src/main/java/org/albi/core/modules/ModuleManager.java) | Storage startup, local data, optional plugin hooks, commands and events, and module dependency checks. |
| Duels and arena state | [DuelManager](src/main/java/org/albi/core/modules/Duels/DuelManager.java) · [DuelInstance](src/main/java/org/albi/core/modules/Duels/DuelInstance.java) | Shared kit and arena storage, queue registration, participant state, rounds, spectators, and finish handling. |
| Arena provisioning | [SchematicManager](src/main/java/org/albi/core/modules/Duels/SchematicManager.java) · [PolarArenaManager](src/main/java/org/albi/core/modules/Duels/polar/PolarArenaManager.java) | Shared clipboard paste queue versus the Polar origin, reservation, and cleanup workflow. |
| Telemetry and ratings | [Ranked package](src/main/java/org/albi/core/modules/Duels/ranked) · [RankedQueueModule](src/main/java/org/albi/core/modules/Duels/queue/RankedQueueModule.java) | Elo windows, rating changes, match sampling, history, and HTML or webhook export. |
| NPC combat and other modes | [AutomatedCombat](src/main/java/org/albi/core/modules/Duels/AutomatedCombat) · [Party](src/main/java/org/albi/core/modules/Party) · [FFA](src/main/java/org/albi/core/modules/FFA) · [LavaRising](src/main/java/org/albi/core/modules/LavaRising) · [Tournaments](src/main/java/org/albi/core/modules/Tournaments) | Practice sessions, private party matches, public FFA worlds, event matches, and Challonge progression. |
| Persistence and fleet health | [StatManagement](src/main/java/org/albi/core/data/StatManagement.java) · [StatRecorder](src/main/java/org/albi/core/data/StatRecorder.java) | MySQL and Redis clients, profile and stats caching, match history, periodic health metrics, and Redis streams. |
| Presentation and configuration | [Resources](src/main/resources) · [Menu engine](src/main/java/org/albi/core/gui) · [PlaceholderManager](src/main/java/org/albi/core/managers/PlaceholderManager.java) · [LangAPI](src/main/java/org/albi/core/LangAPI.java) | Packaged settings, inventory layouts, command registry, text formatting, and per-player or relational placeholder output. |

## PlaceholderAPI reference

With PlaceholderAPI installed, write normal values as `%core_<suffix>%`. For example, `%core_server%` displays the instance ID and `%core_duel-score-yours%` displays your team's current round score. Suffixes mix hyphens and underscores. Copy them as written. Put match-specific values on displays that show them in the corresponding game mode.

| Suffix or family | Value |
| --- | --- |
| `server`, `ip`, `name` | Instance ID, display address, and server name. |
| `xp`, `level`, `max-up` | Profile XP, level, and the XP target for the current level. |
| `status`, `displaytab` | Context icon for activities such as the lobby or a duel, and the current friends or global tab mode. |
| `duel-prefix`, `my-color`, `indics` | Team prefix, team color, and kit-gated health indicator. |
| `dueling`, `duel-in-world`, `display-duel-world` | Number of players in duels, whether the player's world has the duel suffix, and the displayed duel world name. |
| `display-duel-world_players_red_team`, `display-duel-world_players_blue_team`, `display-duel-world_players_spectator` | Flags for active red and blue fighters and spectators in a displayed duel world. |
| `red_team`, `blue_team`, `spectators_watching` | Team flags, including the watched team for eligible spectators, and a spectator count. |
| `duel-score-yours`, `duel-score-other`, `duel-time-formatted` | Your team's round score, the opposing team's score, and elapsed time in readable form. |
| `rank-w-color-g` | Tier-colored Elo for the kit in the active ranked duel. |
| `ranked_elo`, `ranked_tier`, `ranked_placement_left`, `ranked_tier_icon` | Overall ranked rating, tier, remaining placements, and the tier's formatting prefix. |
| `ranked_in_queue` | Total players queued for ranked matchmaking. |
| `bot-in-match`, `bot-score-player`, `bot-score-bot`, `bot-round`, `bot-player-hp`, `bot-hp` | NPC match indicator, scores, current round, and both health values. |
| `ffa-in-world`, `ffa-indic`, `ffa-kills`, `ffa-deaths`, `ffa-ws`, `ffa-kdr` | FFA world indicator, legacy health indicator, and per-kit FFA statistics. Prefer `indics` for new health displays. |
| `party_in_party`, `party_size`, `party_max`, `party_leader`, `party_shuffle`, `party_autobalance` | Membership, roster size, capacity, leader, and party draft settings. |
| `party_line_1` through `party_line_5` | Up to five formatted party roster lines. |
| `partyffa-in-match`, `partyffa-kills`, `partyffa-remaining`, `partyffa-time` | Private party FFA indicator, kills, players remaining, and elapsed time. |
| `lava_active`, `lava_seconds`, `lava_time`, `lava_y`, `lava_arena`, `lava_players`, `lava_remaining` | Lava Rising activity, elapsed seconds and formatted time, height, arena name, player count, and non-spectators remaining in the event world. Use these with Lava Rising enabled. |
| `friends_online`, `friends_total`, `friends_pending` | Online friends, total saved friends, and incoming requests. |
| `friends_tab_count`, `friends_tab_header` | Count and formatted heading for the friend tab. |
| `friends_tab_line_<n>`, `friends_tab_lineskinuuid_<n>`, `friends_tab_lineping_<n>` | One-based friend row text, skin UUID, and ping. |
| `friend-line_get-real-name_<n>`, `friend-line_get-real-ping_<n>` | One-based friend row name and ping for consumers needing those raw values. |

Relational placeholders need a display that passes both viewer and target to PlaceholderAPI. `%rel_core_friend%` displays the friend marker for that pair. `%rel_core_duel-color%` colors the target's team when both players share a duel. `%rel_core_playerSharesDuelWorld%` gives a boolean for the same-player or shared active duel context. [Expansion source](src/main/java/org/albi/core/managers/PlaceholderManager.java)

## Command and permission reference

Each group lists its registered commands, aliases, and permission nodes. Additional checks for administrative or cosmetic actions are listed below. Angle brackets mark required arguments and square brackets mark optional arguments. Many inventory commands are player-only.

### Duels, ranked play, and kits

| Command | Aliases | Permission | Action |
| --- | --- | --- | --- |
| `/duel <player>` | | `core.duel` | Open challenge setup. |
| `/acceptduel <challenger>` | | `core.duel` | Accept a direct challenge. |
| `/spectate <player>` | | `core.spectate` | Watch an active duel locally or across servers with global matchmaking enabled. |
| `/queue [leave]` | | `core.queue` | Browse casual queues or leave one. |
| `/quickqueue [leave]` | | `core.quickqueue` | Enter or leave quick matchmaking. |
| `/rankedqueue [leave]` | | `core.ranked` | Browse competitive queues or leave one. |
| `/rankedlb` | | `core.ranked` | Browse the top 27 by overall ranked Elo. |
| `/seegames [player]` | | `core.ranked` | Read recent match history. |
| `/matchinfo <match_id>` | | `core.ranked` | Inspect a stored match. |
| `/rankedreport <match_id>` | | `core.ranked` | Export an HTML report. |
| `/rankedadmin <add\|remove\|list> …` | | `core.rankedadmin` plus `core.admin.ranked` | Edit ranked arena pools. |
| `/dueladmin <arena\|polar_arena\|kit> …` | | `core.dueladmin` | Manage arenas and public kits. |
| `/setkitdata <kit> <type> <key> <value>` | | `core.dueladmin` | Edit kit metadata. |
| `/setpageduelkitcategory <page> <guiSlot>` | | `core.setpageduelkitcategory` | Add a held-item icon linking to a kit page. |
| `/deletepageduelkitcategory <guiSlot>` | | `core.deletepageduelkitcategory` | Remove a kit page icon. |
| `/automatecombat` | | `core.automatecombat` | Open the experimental NPC practice menu when enabled. |
| `/kiteditor` | | `core.kiteditor` | Open the duel, FFA, and custom kit editor chooser. |
| `/customkit` | `/ckit`, `/ck`, `/k` | `core.customkit` | Choose or edit a personal kit. |
| `/k1` through `/k36` | `/kit1` through `/kit36` | `core.kitcommand` | Equip a saved personal kit by slot. |
| `/copykit <code>` | | `core.copykit` | Copy a shared kit to an empty slot. |
| `/createcustomkitcategory <name> <guiSlot>`, `/setitemincustomkitcategory <name> <guiSlot>`, `/deletecustomkitcategory <name>` | | `core.customkitcategoryediting` plus `core.customkit.admin` | Create, stock, or delete a personal-kit item category. |
| `/changecategorylogo <name>` | | `core.changecategorylogo` plus `core.customkit.admin` | Replace a category's icon with the held item. |
| `/categorylist` | | `core.listcategories` plus `core.customkit.admin` | List the configured categories. |

### Groups and events

| Command | Aliases | Permission | Action |
| --- | --- | --- | --- |
| `/party <subcommand>` | `/p` | `core.party` | Use `help`, `create`, `invite`, `accept`, `leave`, `list`, `settings`, `open`, `join`, `duel`, `play`, or `disband`. |
| `/partyduelaccept <leader>` | `/pda` | `core.partyduelaccept` | Accept a challenge to your party. |
| `/ffa` | | `core.ffa` | Pick a public FFA world and entry spot. |
| `/ffaadmin <subcommand>` | | `core.ffaadmin` | Use `kit create`, `setupworld`, or `world teleport` to configure a world. |
| `/ffakiteditor` | | `core.ffakiteditor` | Personalize an FFA kit layout. |
| `/lavaspawn` | `/event` | `core.spawn` | Enter the Lava Rising waiting area or spectate. |
| `/lavasetspawn` | | `core.setspawn` | Save the event lobby spawn. |
| `/lavaarena <subcommand>` | | `core.lavaarena` | Use `create`, `list`, `delete`, `setspawn`, `setschematic`, `setpos1`, or `setpos2`. |
| `/lavaforcestart` | | `core.lavaforcestart` | Start a Lava Rising event manually. |
| `/tournament <subcommand>` | `/tourney` | `core.tournament` | Use `join` or `leave`. Staff also need `core.tournament.admin` to `create` or `start`. |
| `/next` | | `core.tournament` | Spectate another tournament match. |

### Lobby, teleportation, and administration

| Command | Aliases | Permission | Action |
| --- | --- | --- | --- |
| `/spawn` | `/exit`, `/leave`, `/ff` | `core.spawn` | Return to the configured lobby spawn. |
| `/setspawn` | | `core.setspawn` | Store the current spot as the lobby spawn. |
| `/setspawnitem <slot> <command…>` | | `core.setspawnitem` | Register a command on a held lobby item. |
| `/goto`, `/setgoto <slot>` | | `core.goto`, `core.setgoto` | Open the destination menu or save an icon and location. |
| `/lobbyplayers [on\|off]` | `/lobbyp` | `core.lobbyplayers` | Toggle or explicitly set lobby player visibility. |
| `/rules` | | `core.rules` | Show configured server rules. |
| `/store` | | `core.store` | Show the configured store link. |
| `/travel` | | `core.travel` | Browse network instances and configured regions. |
| `/health <server>` | | `core.health` | Read the latest diagnostics for an instance. |
| `/rtp` | `/randomtp`, `/randomteleport` | `core.rtp` | Choose a XenoWorldReset random-teleport destination. |
| `/tpa <player>`, `/tpahere <player>`, `/tpaccept <requester>` | | `core.tpa`, `core.tpahere`, `core.tpaccept` | Request and accept player teleports. |
| `/back` | | `core.back` | Use the saved death location from an RTP world. |
| `/bypass`, `/fly` | | `core.bypass`, `core.fly` | Toggle supported lobby bypass or flight. |
| `/gmc [player]`, `/gms [player]`, `/gma [player]`, `/gmsp [player]` | | `core.gmc`, `core.gms`, `core.gma`, `core.gmsp` | Set Creative, Survival, Adventure, or Spectator mode. |
| `/vanish` | | `core.vanish` | Toggle staff visibility to other players. |
| `/autorestart <subcommand>` | | `core.autorestart` | Use `time`, `status`, `set`, `cancel`, or `now`. |
| `/coreconfig reload` | | `core.config.reload` | Apply changes to `lang.yml` and `gui.yml`. |

### Profiles, social features, and cosmetics

| Command | Aliases | Permission | Action |
| --- | --- | --- | --- |
| `/profile [player]` | `/pinfo`, `/user`, `/whois`, `/lookup` | `core.profile` | Open account and tier information. |
| `/stats [player]` | `/statistics`, `/playerstats`, `/ps` | `core.stats` | Inspect per-kit statistics. |
| `/leaderboard [field game]` | `/lb`, `/topstats`, `/topstatistics` | `core.leaderboard` | Open the leaderboard or look up a specific statistic. |
| `/settings` | `/set`, `/s`, `/options` | `core.settings` | Configure general, social, and matchmaking preferences. |
| `/friends [subcommand]` | `/friend`, `/f` | `core.friends` | Browse friends or use `add`, `accept`, `deny`, `remove`, or `delete` with a name. |
| `/msg <player> <message>`, `/r <message>` | `/tell`, `/whisper` for `/msg` | `core.msg` | Send or reply to a direct message. |
| `/block <player>`, `/unblock <player>` | | `core.block` | Change your block list. |
| `/discord [link\|check\|unlink]` | | `core.discord` | Get the Discord link or manage account linking. |
| `/armortrim` | `/trims`, `/armortrims` | `core.armortrim` | Choose an armor trim. |
| `/deatheffects` | `/deatheffect`, `/de` | `core.deatheffects` | Choose or clear a kill effect. |

### Additional permissions

| Permission | Access |
| --- | --- |
| `core.elytra`, `core.elytra.enchanted` | Lobby elytra and its upgraded enchantment. |
| `core.lobby.speed`, `core.lobby.jumpboost` | Permission-gated movement options in the lobby. |
| `core.chat.color` | Player color formatting in chat. |
| `core.vanish.see` | See staff who have enabled vanish. |
| `core.profile.bypass` | View a profile hidden by its owner. |
| `core.openparty`, `core.openparty_cooldownbypass` | Open a party to the public and, separately, bypass the reopen cooldown. |
| `core.party.size.<number>` | Increase the maximum party size above four. |
| `core.armortrim.<combo>`, `core.armortrim.*` | Unlock one trim preset or all presets. |
| `core.deatheffect.<effect>`, `core.deatheffect.*` | Unlock one kill effect or all effects. |
| `core.admin.ranked`, `core.tournament.admin`, `core.customkit.admin` | Additional in-command checks for ranked administration, tournament administration, and custom-kit categories. |

[Command registry](src/main/resources/plugin.yml)

## License

Vise Core's code is licensed under the [BSD 3-Clause License](LICENSE). You can use, modify, and redistribute it, including commercially. Source distributions, including forks, must retain the original copyright notice and license terms. Redistributed JARs must carry the same notice and terms in the documentation or other materials that accompany the download. Vise Core's build includes its license at `META-INF/LICENSE-Vise-Core` inside the JAR.

## Credits

Originally developed by **Critical <3 (CriticalKing)** for Vise Network.

# Vise Core

Vise Core is a Minecraft PvP plugin for **Paper 1.21.3** and **Java 21**. It manages arenas, kits, queues, ranked matches, parties, and events. It also handles the lobby, player profiles, cosmetics, chat, and travel between server instances.

## Vise Core vs. StrikePractice

| Feature | Vise Core | StrikePractice |
| --- | :---: | :---: |
| Direct duels and ranked matchmaking | ✅ | ✅ |
| Party fights and free-for-all arenas | ✅ | ✅ |
| Custom kits and saved layouts | ✅ | ✅ |
| PvP practice bots | ✅ | ✅ |
| Lava Rising survival events | ✅ | ❌ |
| Challonge tournament brackets | ✅ | ❌ |
| PvPTiers, MCTiers, and Subtiers in one profile | ✅ | ❌ |
| Redis chat bridge and regional travel menu | ✅ | ❌ |
| **Completely Free** | **✅** | **❌** |

The comparison covers the base plugins. On feature rows, ❌ means the feature is unlisted in StrikePractice’s published feature list and v4.0.0 release notes. The pricing row refers to the plugin itself. [StrikePractice listing and pricing](https://builtbybit.com/resources/strikepractice-practice-pvp-plugin.74743/) · [v4.0.0 release notes](https://builtbybit.com/resources/strikepractice-practice-pvp-plugin.74743/updates)

## Contents

- [Installation and module setup](#installation-and-module-setup)
- [Duels, arenas, and matchmaking](#duels-arenas-and-matchmaking)
- [Ranked play and match reports](#ranked-play-and-match-reports)
- [Experimental NPC practice](#experimental-npc-practice)
- [Kits and inventory layouts](#kits-and-inventory-layouts)
- [Parties, FFA, Lava Rising, and tournaments](#groups-and-events)
- [Lobby, teleportation, and server travel](#lobby-teleportation-and-server-travel)
- [Profiles, statistics, and cosmetics](#profiles-statistics-and-cosmetics)
- [Friends, chat, and Discord](#friends-chat-and-discord)
- [Combat fixes and server operations](#combat-fixes-and-server-operations)
- [Configuration, data, and upgrades](#configuration-data-and-upgrades)
- [PlaceholderAPI reference](#placeholderapi-reference)
- [Command and permission reference](#command-and-permission-reference)

## Installation and module setup

1. Build with `./gradlew build` on macOS or Linux or `gradlew.bat build` on Windows. Put `build/libs/ViseCore-1.0.0.jar` in Paper's `plugins/` directory. GitHub Actions also publishes the JAR as the **ViseCore** build artifact. [Build workflow](../../actions/workflows/build.yml)
2. Install FastAsyncWorldEdit or WorldEdit if you plan to use Duels or modules that share its arenas and kits.
3. Start Paper once to generate `plugins/Core/config.yml`. Create a MySQL database and configure its connection alongside Redis. The default endpoints point at localhost. Paper may disable Core on the first run until MySQL is reachable and the configured user can create the tables. Restart Paper after configuring both services.
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

PolarPaper adds a second arena backend when `use_polar_worlds` is on. PlaceholderAPI adds values for compatible tab lists, scoreboards, and chat plugins. FeatherServerAPI loads the optional `plugins/Core/server-background.png` as a server-list background. The RTP menu delegates to a separate plugin that provides the `xwr rtp` console command.

## Duels, arenas, and matchmaking

### Direct challenges and spectating

Run `/duel <player>` to challenge an online player. The setup menus let the challenger pick a ruleset, an arena, and the number of rounds needed to win. The recipient can click **Accept** in chat or run `/acceptduel <challenger>`. A standard ruleset supplies the same base equipment and combat rules to both sides. The **Per Custom Kit** option lets each fighter use their own selected personal kit. Challenge preferences and block lists affect who can send requests. Friends can bypass the invite toggle, while a block still applies.

During the match, Core reserves an arena, equips the fighters, starts a countdown, and tracks round wins. Team matches use the same arena and kit system. Kits can define whether players can build, regenerate health naturally, lose hunger, or expose health indicators. Duel listeners handle damage during countdowns, prevent friendly hits, and resolve lethal hits as round outcomes while allowing totems to trigger. At the end, Core returns participants to the lobby and releases the arena for reuse.

Run `/spectate <player>` to watch an active fight. Spectating respects the player's preference and applies restrictions to keep spectators out of combat. Use `/spawn` to leave. The [PlaceholderAPI expansion](#placeholderapi-reference) supplies team colors, health indicators, score, elapsed time, and spectator information to compatible displays.

### Casual queues

`/queue` opens a kit browser with queue counts. Choose a standard kit or **Per Custom Kit**. `/quickqueue` looks for a match in an active casual queue without making the player choose a specific ruleset. The action bar shows queue status while the player waits. Run `/queue leave` or `/quickqueue leave` to leave the casual queue.

Staff can organize the duel and casual queue browsers into ruleset pages. Hold the item to use as the category icon, then run `/setpageduelkitcategory <page> <guiSlot>`. Use `/deletepageduelkitcategory <guiSlot>` to remove that entry from the category menu.

### Schematic arenas

Schematic arenas reserve a slot in an arena world and paste the map into that slot. The configured offsets and grid size control how many copies can fit in the world. Core queues schematic pastes and reuses arena slots after matches.

1. Prepare an empty template world in `plugins/Core/duel-placeholder/` and put the arena schematic in `plugins/Core/schems/duels/`. Keep the schematic origin aligned with the team spawns and arena coordinates you will register.
2. Run `/dueladmin arena create <name>`. Run `/dueladmin arena arenaready <name>` to copy the template into the arena's world directory. Running `arenaready` again replaces that arena's template copy.
3. Hold the map icon and run `/dueladmin arena setdisplayitem <name>`. Stand at each team spawn and run `/dueladmin arena setspawn <name> 1` and `/dueladmin arena setspawn <name> 2` at their respective locations.
4. Record the four horizontal boundaries with `/dueladmin arena setcorner <name> <x1|z1|x2|z2> <coordinate>`. Link the file with `/dueladmin arena setschematic <name> <file>`.
5. Use `setoffset <name> <x|z> <blocks>` and `setmax <name> <perline|maxarenas> <number>` under `/dueladmin arena` to configure spacing and capacity. Use `info <name>` to review the setup or `deactivate <name>` to toggle the arena's availability in the direct challenge picker.

### Polar arenas

Set `use_polar_worlds: true` with a compatible PolarPaper installation. A Polar arena has an editable origin world and temporary match worlds. Core creates match worlds from the saved origin, preloads copies, and releases them when matches end. `polar_preload` sets the preload target.

Use `/dueladmin polar_arena create <name>` and `edit <name>` to set up the origin. Record the team positions with `spawn1 <name>` and `spawn2 <name>`, or `setspawn <name> <1|2>`. Set the two arena boundaries with `set_limit <name> <min|max>` while standing at each corner. Use `setdisplayitem <name>` to set the icon and `save <name>` or `arenaready <name>` to save the origin and prepare it for use. Run `info <name>` to inspect the result. [Arena command source](src/main/java/org/albi/core/modules/Duels/commands/DuelAdminCommand.java)

## Ranked play and match reports

`/rankedqueue` opens solo, single-round matchmaking by standard kit. The menu shows the player's Elo, placement or tier, and the number of players waiting for each kit. The queue widens its Elo search range as players wait. Leaving a party and ending any active fight or spectating session makes a player eligible to queue. Use `/rankedqueue leave` to cancel.

- Each kit has its own rating, win and loss counts, and placements. Ratings start at **1,000 Elo**. By default, the first **five matches** per kit count as placements and use larger rating adjustments.
- Wins against higher-rated opponents are worth more. Losses against lower-rated opponents cost more. The match countdown previews the stakes.
- Tiers run from Bronze through Silver, Gold, Platinum, Diamond, Master, Grandmaster, and Champion. Each tier has five 100-point divisions. Players see placement progress until they earn a displayed tier.
- `/rankedlb` opens the per-kit ranked leaderboard. For an arena pool specific to a kit, use `/rankedadmin add <kit> <arena>`, `remove <kit> <arena>`, and `list <kit>`. If the pool is empty, the queue draws from the shared arena list. Staff can access the pool commands with both `core.rankedadmin` and `core.admin.ranked`.

### Match telemetry, history, and export

Ranked duels record match telemetry. In a direct challenge, the challenger can also enable **Match Telemetry** for a first-to-one, first-to-two, or first-to-three match. Telemetry records a match ID and includes hits, measured distance and aim angles, arm swings, damage, combos, health, movement, and ping. Use measurements in context when reviewing individual hits.

| Command or setting | What it does |
| --- | --- |
| `/seegames [player]` | Lists recent stored matches for yourself or the named online player. |
| `/matchinfo <match_id>` | Reads a stored match, including the players, winner, and key combat measurements. |
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
- **Sharing.** Use **Share Kit** to generate a Redis-backed code. Codes are valid for 30 days from the latest share. Another player can run `/copykit <code>` and pick an empty slot for a copy.
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

`/rtp` opens a selector for **Plains**, **Snowy**, **Desert**, **Badlands**, and **Mushroom**, and sends the chosen destination to the external `xwr rtp` command as console. Use `c_worlds` to designate worlds for Core's RTP-world behavior, including world-local death announcements, return to the lobby on respawn, and the one-use `/back` teleport to the last death location in those worlds. With Duels enabled, combat listeners also handle behaviors such as explosive beds in those worlds.

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

## Profiles, statistics, and cosmetics

### Profiles and progression

`/profile [player]` shows account status, level, XP progress, total playtime, a social summary, and a link to detailed stats. Duels can award profile XP, and time spent on a server is tracked separately from time in a ruleset. The profile can also show ranks by game mode, region, and peak rank where returned by **PvPTiers**, **MCTiers**, and **Subtiers**. These providers are queried by player UUID and can independently return unavailable results.

`/stats [player]` opens paged statistics for applicable kit and team-size combinations. The UI displays stored results such as wins, kills, deaths, streak, time, and other activity information when present. `/leaderboard` gives a separate view by stat, kit, and team size. It also accepts `/leaderboard <field> <game>` for a text result. `/rankedlb` shows competitive Elo standings.

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

`announcements.enabled` toggles rotating lobby tips. Set the content in `announcements.messages` and the spacing in `announcements.interval_seconds`. The interval has a 30-second floor. Tips go to the configured lobby world.

`autorestart.enabled` starts a countdown on server startup using `autorestart.interval_hours`. The bundled default is off. Staff can use `/autorestart time` or `status` to see the countdown, `set <duration>` to schedule a shutdown, `cancel` to clear it, or `now` for a ten-second countdown. Durations can use units such as `30m` or `10s`. Core warns players at built-in countdown milestones and shuts Paper down when time expires. A server panel, service manager, or other supervisor must launch Paper again to complete the restart.

## Configuration, data, and upgrades

### Main configuration reference

| Setting | What to edit |
| --- | --- |
| `name`, `ip` | Display values used by messages and PlaceholderAPI. Set `ip` to the address you want to display. |
| `region`, `id_number` | Parts of the instance ID. For example, `EU` and `1` produce `EU-1`, with lowercase used in Redis keys. |
| `lobby_world`, `lobby_max_height` | Lobby selection and vertical return-to-spawn threshold. |
| `c_worlds`, `ffa_worlds` | Names of RTP-style gameplay worlds and public FFA worlds. |
| `modules` | Feature switches. Review the dependencies in [module setup](#modules-and-requirements). |
| `mysql` | Host, port, database, user, password, and connection pool size. A blank `ssl_mode` uses the legacy `ssl` flag. For TCP TLS verification, set a supported Connector/J mode such as `VERIFY_IDENTITY` with a trusted server certificate. |
| `redis` | Host, port, optional password, and TLS. With TLS enabled, `ca_file` can point to a custom CA, relative to `plugins/Core/` or absolute. A blank value tries `certs/ca.crt` first, then the JVM trust store. |
| `storage.flush_interval_seconds` | Frequency of periodic profile and stats flushes. The bundled default is 600 seconds. |
| `network` | Redis chat bridge, links for `/store` and `/discord`, and direct travel destinations. |
| `announcements`, `autorestart` | Lobby tips and the startup restart schedule. |
| `use_polar_worlds`, `polar_preload` | Toggle the PolarPaper backend and set its preload target. |
| `duels` | TNT fuse, default round target, selectable round targets, and the `bots` settings. |
| `ranked` | Placement count, match retention, webhook, and hosted report link template. |
| `lava_rising`, `tournaments` | Lava Rising world, timing, starting items and webhook, plus Challonge credentials for tournaments. |
| `33_percent_boost` | Public kit IDs that use the fixed 33% hit-damage modifier. |
| `mending_rngs` | Legacy kit list. For an effective Mending repair override, set `mending_rng` on the kit metadata. |
| `guis.duel.kitselector.rows` | Number of rows in the direct duel and party challenge kit pickers. Some legacy GUI names, rows, and offsets in the shipped YAML are inactive in the current menus. |
| `cooldowns` | Seconds between uses for commands that check a cooldown. Use the registered command name as the key, such as `duel`, `travel`, or `automatecombat`. |
| `permissions` | Permission nodes used by internal lobby and game-mode checks. Bukkit also checks the registered command permissions in `plugin.yml`. |
| `chat`, `bad_words`, `lang` | Chat formatting, moderation terms, and configurable messages. Language strings support legacy `&` colors and positional replacements such as `{1}`. Where the plugin supplies a player context, PlaceholderAPI can resolve values in those strings. |
| `max_entities`, `lava_rising.auto_interval_ticks` | Legacy config entries. The current listeners use the creeper-egg behavior and the player-count-based Lava Rising countdown described above. |

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

### What gets stored

| Where | Data |
| --- | --- |
| `plugins/Core/data.dat` | Local plugin data, including spawn points, menu destinations, arenas, public kit definitions, categories, and event setup. Core writes the file through a temporary file when saving on shutdown. If the file cannot be read during startup, Core disables itself so an operator can recover the file. |
| MySQL | `player_stats` stores player profiles and statistics. `ranked_matches` stores match history and telemetry. The optional Discord module stores completed account links in `player_discord_links`. |
| Redis | Cached profiles, stats and match records, sorted-set leaderboards, kit-sharing codes, Discord link codes, chat messages in transit, and server heartbeats and health streams. Network instances that share these features use the same Redis service. |
| Memory | Online profile state and active gameplay state such as queues, parties, pending teleport requests, and arena reservations. A Paper restart clears active sessions. |
| `plugins/Core/reports/` | HTML files produced by explicit report export commands. |

### Upgrading and troubleshooting

1. Back up `plugins/Core/`, the arena worlds, MySQL, and any Redis data you rely on. Stop Paper before swapping JARs.
2. Keep the existing `plugins/Core/` directory and merge new keys from the shipped configuration. In particular, recheck `network` and `duels.bots`, and set your own store link, Discord invite, transfer hosts, and hosted report URL where needed.
3. Check startup logs for storage and skipped-module messages. Verify MySQL table permissions, both service endpoints, the configured world names, and FAWE or WorldEdit if Duels should be active.
4. Join the lobby and test one full match from queue or challenge through arena reuse. For FFA, test the exact configured world name and a saved entry spot. For network features, check `/health <server>` on both instances and confirm they use the expected Redis database and distinct instance IDs.

The repository includes the Gradle Wrapper, a pinned GitHub Actions workflow, build-time plugin version metadata, and shaded runtime dependencies for the JAR. Paper and integration plugins run alongside it. [Build script](build.gradle.kts) · [Main configuration](src/main/resources/config.yml)

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
| `/spectate <player>` | | `core.spectate` | Watch an active match. |
| `/queue [leave]` | | `core.queue` | Browse casual queues or leave one. |
| `/quickqueue [leave]` | | `core.quickqueue` | Enter or leave quick matchmaking. |
| `/rankedqueue [leave]` | | `core.ranked` | Browse competitive queues or leave one. |
| `/rankedlb` | | `core.ranked` | Browse ranked standings by kit. |
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
| `/rtp` | `/randomtp`, `/randomteleport` | `core.rtp` | Choose an RTP destination. |
| `/tpa <player>`, `/tpahere <player>`, `/tpaccept <requester>` | | `core.tpa`, `core.tpahere`, `core.tpaccept` | Request and accept player teleports. |
| `/back` | | `core.back` | Use the saved death location from an RTP world. |
| `/bypass`, `/fly` | | `core.bypass`, `core.fly` | Toggle supported lobby bypass or flight. |
| `/gmc [player]`, `/gms [player]`, `/gma [player]`, `/gmsp [player]` | | `core.gmc`, `core.gms`, `core.gma`, `core.gmsp` | Set Creative, Survival, Adventure, or Spectator mode. |
| `/vanish` | | `core.vanish` | Toggle staff visibility to other players. |
| `/autorestart <subcommand>` | | `core.autorestart` | Use `time`, `status`, `set`, `cancel`, or `now`. |

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

## Credits

Originally developed by **CriticalKing** for Vise Network, with contributions from [sxlectdev](https://github.com/sxlectdev).

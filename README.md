# Vise Core

Vise Core is a Paper plugin for Minecraft PvP servers. It runs casual and ranked duels, FFA, parties, custom kits, and Lava Rising events. It also handles lobby shortcuts, profiles, leaderboards, and travel between servers.

## Install

1. Run **Paper 1.21.3** on **Java 21**. Build the JAR with `./gradlew build` or download the **ViseCore** artifact from a successful [build](../../actions/workflows/build.yml). On Windows, run `gradlew.bat build`.
2. Put `ViseCore-1.0.0.jar` from `build/libs/` into the server's `plugins/` folder. Install FastAsyncWorldEdit or WorldEdit if you plan to use duel arenas.
3. Start Paper once to generate `plugins/Core/config.yml`. Point it at a MySQL database and a Redis instance, then restart. The MySQL user needs permission to create the plugin tables and read and write their rows.
4. In the lobby, run `/setspawn`. Use `/dueladmin` to create an arena and a kit before opening queues. Grant the player and admin permissions you need through your permissions plugin.

## Configuration

Edit [config.yml](src/main/resources/config.yml) in the server's `plugins/Core/` folder and restart Paper.

| Settings | What they control |
| --- | --- |
| `name`, `ip`, `region`, `id_number` | Server branding and the instance ID used in Redis. Give each server a unique region and ID. |
| `lobby_world`, `c_worlds`, `ffa_worlds` | Lobby and gameplay worlds. |
| `modules` | Which features start. Party, FFA, and Lava Rising use Duels. Tournaments also use Party. |
| `mysql`, `redis`, `storage` | Connection details, TLS, pool size, and the stats flush interval. With Redis TLS, set `redis.ca_file` for a custom CA. A blank value checks `certs/ca.crt` before using JVM trust. |
| `network` | Store and Discord links, cross-server chat, and regional destinations. Same-region travel needs a BungeeCord-compatible proxy. Direct regional transfers use the configured host and port. |
| `duels`, `ranked`, `use_polar_worlds` | Rounds, NPC practice, Elo placements, ranked history retention, reports, and the optional PolarPaper arena backend. |
| `lava_rising`, `tournaments` | Lava Rising timings and kits, plus optional Challonge tournament credentials. |
| `guis`, `lang`, `cooldowns`, `bad_words` | Menu settings, player messages, command cooldowns, and the chat filter. |

Set secrets in the environment of the Paper process. For the fields below, a defined environment variable takes precedence over YAML.

| Variable | YAML setting |
| --- | --- |
| `VISE_MYSQL_HOST`, `VISE_MYSQL_PORT` | `mysql.host`, `mysql.port` |
| `VISE_MYSQL_DATABASE`, `VISE_MYSQL_USER`, `VISE_MYSQL_PASSWORD` | `mysql.database`, `mysql.user`, `mysql.password` |
| `VISE_REDIS_HOST`, `VISE_REDIS_PORT`, `VISE_REDIS_PASSWORD` | `redis.host`, `redis.port`, `redis.password` |
| `VISE_RANKED_WEBHOOK_URL`, `VISE_LAVA_WEBHOOK_URL` | `ranked.webhook_url`, `lava_rising.webhook_url` |
| `VISE_CHALLONGE_USERNAME`, `VISE_CHALLONGE_API_KEY` | `tournaments.challonge_username`, `tournaments.challonge_api_key` |

PlaceholderAPI enables the `%core_…%` expansion. FeatherServerAPI loads `plugins/Core/server-background.png` when that file is present. Enable `use_polar_worlds` with a compatible PolarPaper installation. The combat fixes have their own [anti-ghost-arrow](src/main/resources/anti-ghost-arrow.yml) and [knockback synchronization](src/main/resources/knockback-synchronization.yml) configs.

## Experimental NPC practice

> [!WARNING]
> NPC practice uses version-specific Paper server internals. Updates can break NPC spawning, combat, or cleanup. Test practice duels and arena reuse on a staging server before enabling the feature or upgrading Paper.

Set `duels.bots.enabled` to `true`, configure the public kit IDs in `duels.bots.allowed_kits`, and grant `core.automatecombat` to players who should use `/automatecombat`. `duels.bots.max_sessions` limits concurrent practice duels.

## Common commands

| Command | Permission | Use |
| --- | --- | --- |
| `/queue`, `/quickqueue` | `core.queue`, `core.quickqueue` | Join casual matchmaking. |
| `/rankedqueue`, `/rankedlb` | `core.ranked` | Join ranked matchmaking or view the leaderboard. |
| `/duel <player>`, `/spectate <player>` | `core.duel`, `core.spectate` | Challenge a player or watch a duel. |
| `/customkit`, `/party`, `/friends` | `core.customkit`, `core.party`, `core.friends` | Manage kits, parties, and friends. |
| `/ffa`, `/lavaspawn`, `/tournament` | `core.ffa`, `core.spawn`, `core.tournament` | Join enabled game modes and events. |
| `/dueladmin`, `/ffaadmin`, `/lavaarena` | `core.dueladmin`, `core.ffaadmin`, `core.lavaarena` | Set up arenas and kits. |

See [plugin.yml](src/main/resources/plugin.yml) for the full command and permission list.

## Upgrading an existing server

Back up `plugins/Core/`, MySQL, and Redis before replacing the JAR. The Paper plugin ID is still `Core`, so the plugin uses the existing configuration and `data.dat`. Merge the new YAML keys into your current file, especially the `network` and `duels.bots` sections. Add your store URL, Discord invite, and regional hosts if you use them. For hosted ranked reports, set `ranked.report_url_template` to your report URL with `{match_id}` where the match ID belongs.

Originally developed by **CriticalKing** for Vise Network, with contributions from [sxlectdev](https://github.com/sxlectdev).

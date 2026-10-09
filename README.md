# YamTech Sanctions

YamTech Sanctions is a Paper plugin that adds Towny nation trade embargoes and AllyTeam diplomacy. A sanction blocks the targeted nation from completing villager and wandering-trader trades (including selling goods to those shops) and from piglin bartering. Players can still right-click those entities for other interactions; the plugin cancels the actual trade action only.

Sanctions stack by issuer. A nation or AllyTeam can replace its own sanction against the same target, while removing one issuer's sanction leaves other issuers' sanctions in force. Sanctions can target one Towny nation or an entire AllyTeam; team-targeted sanctions apply to all current team members and nations that join later.

## Requirements

- Paper or Purpur 1.19.4 or newer
- Towny 0.103.2.0 or newer
- JDK 21 and Maven to build from source (plugin bytecode targets Java 17)

One jar cannot cover every historical Minecraft release because those releases use different server APIs and Towny versions. This build targets Minecraft 1.19.4 and newer.

## Build and install

From this folder, run:

```text
mvn package
```

Copy `target/YamTech-Sanctions.jar` to the server's `plugins` folder alongside Towny, then restart. The plugin creates a `YamTechSanctions` data folder. When upgrading from the original plugin, it copies `config.yml` and `sanctions.yml` from the old `Sanction` folder when the new files do not exist.

## Nation sanctions

```text
/sanction impose <nation|team:name> --for <duration> [reason]
/sanction lift <nation|team:name>
/sanction status <nation|team:name>
/sanction list [page]
/sanction clear
/sanction reload
```

Durations accept seconds, minutes, hours, days, or permanent: `45s`, `30m`, `12h`, `7d`, `perm`. The `--for` marker separates the duration from the target name, so nation names may contain duration-looking words. For example:

```text
/sanction impose The 5m Republic --for 7d blockade announced by the council
/sanction impose team:North Atlantic --for 12h port embargo
```

Nation kings outside an AllyTeam can issue and lift their own sanctions. Operators and users with `sanction.admin` can manage any target, clear every sanction, and reload settings. `/sanction status` lists every sanction that currently applies to a nation, including sanctions aimed at its AllyTeam.

## AllyTeams and voting

```text
/allyteam create <name>
/allyteam invite <nation>
/allyteam accept <team name>
/allyteam info [team]
/allyteam list
/allyteam proposals
/allyteam vote sanction <nation> --for <duration> [reason]
/allyteam vote lift <nation>
/allyteam vote <proposal-id> <yes|no>
/allyteam leave
/allyteam leader <member nation>
/allyteam disband
```

The creating nation becomes the AllyTeam leader and first member. Its king can invite other nations; each invited nation joins when its king accepts. Team nations cannot issue individual sanctions directly. A nation king proposes an AllyTeam sanction, and every nation that belonged to the team when the vote opened gets one vote. The proposer starts with a yes vote. A strict majority of all member nations is required; ties fail. Votes remain open for 24 hours, survive restarts, and have short IDs shown by `/allyteam proposals`. A majority vote can also lift the team's sanction against a nation.

A nation outside an AllyTeam may sanction an AllyTeam directly with `/sanction impose team:<name> --for <duration>`. Sanctions on an AllyTeam affect all its members.

## Storage

The default backend is YAML. To use SQLite, stop the server and change `storage.type` in `plugins/YamTechSanctions/config.yml` to `SQLITE`, then start the server. SQLite stores one row per sanction, team, or vote in `sanctions.db`; the SQLite driver is bundled in the plugin jar. Existing YAML records are imported the first time SQLite opens each collection. You can switch backends and use `/sanction reload`; the plugin copies active records to the selected backend.

## Permissions and compatibility

- `sanction.admin` (default: operators): administer sanctions.
- `sanction.bypass` (default: operators): exempt a player from trade embargoes.

The Java package is `yt.yamtechs.sanctions`. The plugin compiles against Towny 0.103.2.0 and Paper API 1.19.4; both are provided by the server and are not bundled in the plugin jar.

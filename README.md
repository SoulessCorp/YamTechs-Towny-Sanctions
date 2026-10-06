# Sanction

Sanction is a Paper plugin for Towny nations. A sanction is a political trade embargo: members of the targeted nation cannot open villager or wandering trader shops, complete villager trades, or barter with piglins. The sanction is tied to the Towny nation's UUID, so it continues to apply if the nation changes its name.

## Requirements

- Paper or Purpur 1.19.4 or newer
- Towny 0.103.2.0 or newer
- JDK 21 and Maven to build from source (the plugin bytecode targets Java 17)

Towny must be installed because the plugin uses Towny's nation API. One jar cannot support every historical Minecraft release because older versions have different APIs and require older Towny releases. This build targets the broad current Towny range, starting at Minecraft 1.19.4.

## Build and install

From this folder, run:

```text
mvn package
```

Copy `target/Sanction-1.0.0.jar` into the server's `plugins` folder, then restart the server. The plugin creates `plugins/Sanction/config.yml` and `plugins/Sanction/sanctions.yml` on startup.

## Commands

```text
/sanction
/sanction impose <nation> <duration> [reason]
/sanction lift <nation>
/sanction status <nation>
/sanction list [page]
/sanction clear
/sanction reload
```

Durations accept seconds, minutes, hours, days, or permanent: `45s`, `30m`, `12h`, `7d`, `perm`. Nation names can contain spaces because the command identifies the duration token and treats preceding words as the nation name. For example:

```text
/sanction impose Iron Coast 7d Blockade announced by the council
```

Nation kings can impose sanctions on other nations and lift sanctions issued by their own nation. Operators and users with `sanction.admin` can impose server sanctions, lift every sanction on a target nation, clear all sanctions, and reload settings. Everyone can use `list` and `status`.

Operators have `sanction.bypass` by default. Grant or remove that permission to control which players are exempt from trade embargoes. The trade types can be toggled in `config.yml`.

## Towny compatibility

The project compiles against Towny 0.103.2.0 and Paper 1.19.4 API. Both are provided by the server and are not bundled in the plugin JAR.

# consts-osrs

RuneLite plugins for Old School RuneScape ( OSRS ), by const. One Gradle build, one plugin per folder.

| Plugin | What it does |
|---|---|
| [consts-AFK](consts-afk/) | OSRS AFK alerts with presets for combat, skilling and Sailing: beeps, screen flashes and a status panel. |

![consts-AFK flashing the screen magenta when the inventory fills while woodcutting](consts-afk/screenshots/inventory-full.jpg)

More on the plugin: [constdecimalserrno.dev/consts-afk](https://constdecimalserrno.dev/consts-afk/).

## Run it

The plugins aren't on the Plugin Hub yet, so you run RuneLite from this repo with every plugin loaded.

### Prerequisites

- **JDK 17+** to run Gradle
- **JDK 11** for the RuneLite client (Temurin 11 works)
- **RuneLite** installed from [runelite.net](https://runelite.net)

`run.sh` defaults to the Homebrew OpenJDK and Temurin 11 paths on macOS. Anywhere else, point it at your JDKs:

```bash
export JAVA_HOME=/path/to/jdk-17          # runs Gradle
export RUNELITE_JAVA_HOME=/path/to/jdk-11 # runs the client
```

### Jagex accounts (one time)

A client started from source can't log in through the Jagex Launcher by itself. Let the launcher hand it your session once:

1. Open RuneLite's configure mode (`RuneLite --configure`; on macOS `/Applications/RuneLite.app/Contents/MacOS/RuneLite --configure`).
2. Add the client argument `--insecure-write-credentials` and save.
3. Launch RuneLite from the Jagex Launcher and log in once.

RuneLite writes `~/.runelite/credentials.properties`, and the client started from source logs in with it. Treat that file like a password.

### Launch

```bash
./run.sh all        # tests, compile, launch RuneLite with every plugin
./run.sh runClient  # launch only
./run.sh test       # tests only
```

The plugin shows up in RuneLite's plugin list. Its settings live under the wrench.

## Releases

Each [release](https://github.com/constdecimalserrno/consts-osrs/releases) attaches the compiled plugin jars. Build them yourself with `./gradlew jar`; they land in `<plugin>/build/libs/`.

## Project structure

```
consts-osrs/
├── build.gradle                  # Shared config: Java 11, RuneLite, tests
├── settings.gradle               # One include per plugin
├── run.sh                        # Build and launch
├── runelite-plugin.properties    # Plugin metadata
├── src/test/java/com/osrs/
│   └── PluginLauncher.java       # Loads every plugin into one RuneLite client
└── consts-afk/
    └── src/main/java/com/constsafk/...
```

Each plugin is a Gradle subproject. Shared settings live in the root `build.gradle`; a plugin's own `build.gradle` holds only its overrides.

## Adding a plugin

1. Create `new-plugin/src/main/java/com/newplugin/` and `new-plugin/src/test/java/com/newplugin/`.
2. Add `new-plugin/build.gradle` (a comment is enough).
3. Add `include 'new-plugin'` to `settings.gradle` and `implementation project(':new-plugin')` to the root `build.gradle` dependencies.
4. Write `NewPlugin.java` (extends `Plugin`, has `@PluginDescriptor`) and `NewPluginConfig.java` (extends `Config`, has `@ConfigGroup`).
5. Add `NewPlugin.class` to `ExternalPluginManager.loadBuiltin(...)` in `PluginLauncher.java`, and to `plugins=` in `runelite-plugin.properties`.
6. `./run.sh all`.

## License

[BSD 2-Clause](LICENSE), the license RuneLite and its Plugin Hub use.

Not affiliated with Jagex or RuneLite. Old School RuneScape is a trademark of Jagex Ltd.

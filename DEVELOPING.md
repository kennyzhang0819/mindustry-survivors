# Developing

## Build and play

Run `./play.sh`. It builds the mod, puts it in the game's mods folder and starts the game. The first time, it also downloads the game (needs Java 17 or newer). It uses your real game data.

The Admin tab in Settings (give yourself levels, skip waves, add or remove curses) only shows when the game is started by `./play.sh`. To try a build at a certain point in a run, use **Fresh start at a wave**: it empties your build, gives you every level the earlier waves would have given if you picked up every gem, and lets you pick your build before the wave starts. Players who install the mod normally never see it.

To only build, run `./build.sh`. It creates `build/MindustrySurvivors.jar`.

## Run report

Run `./report.sh` to see every turret level, passive, curse and wave total, with the level you would reach by each wave. It takes a few seconds and doesn't open the game.

## Screenshot check

Set `SURVIVORS_AUTOTEST=arena` and start the game. It opens the menu, starts a run, picks a level-up choice, adds a few weapons, sends two waves, saves, continues the run, saves screenshots to `SURVIVORS_SHOTS` and quits. Point the game's data folder at an empty folder so your own saves aren't touched (`APPDATA` on Windows, `XDG_DATA_HOME` on Linux).

Set `SURVIVORS_AUTOTEST=damage` to measure every turret: each one fires for 3 seconds at a still group of 9 dummies, at level 1 and level 10, and the damage per second is logged. It takes about 4 minutes. To measure only some turrets, list them in `SURVIVORS_DAMAGE_ONLY`, for example `wave,arc`.

Set `SURVIVORS_AUTOTEST=boss` to check both bosses: each one is spawned next to you, hit with slowing and stunning effects (which it should shrug off), and the first is killed so you walk onto its chest. It takes about 25 seconds.

To run several checks at once, each in its own empty data folder, run `tools/autotest.sh arena death fresh damage:duo,hail`. It reports every test that failed: a crash, an error in the log, an error window, a Java crash, or a test that never finished. `look:cyclone:10` fires a turret at that level into open ground and saves three screenshots, and `chase:swarmer:10` runs the stress test with that turret firing. `listing` opens the game's own Mod Browser and Mods screens with this mod in them and saves screenshots of the list and the details pages, so you can see the listing before a release.

Set `SURVIVORS_AUTOTEST=chase` for a stress test: it starts a run, moves your unit in a circle and adds enemies in steps up to 5,000, logging frame times and how many enemies are closing in on you.

## Pictures

To look at random maps without the game, run `tools/MapPreview.java` (see `report.sh` for how to compile it): it writes pictures of maps to a folder.

Curse icons are drawn by `tools/make-icons.py`, and the title and mod icon by `tools/make-title.py` (both need Python with Pillow and numpy). `python tools/make-title.py banner` redraws only the banner in `art/`, which the README and GitHub show.

## Release

1. Raise `version` in `mod.hjson` (for example `0.1.0` to `0.1.1`), commit and push. Players are only offered an update when this number goes up. The first release keeps `0.1.0`.
2. Run `./release.sh`. It checks that everything is committed and pushed, builds the mod and prints the rest: open the link it shows, attach `build/MindustrySurvivors.jar` and publish.

The game installs the mod from the jar attached to the newest release, so a release without the jar can't be installed.

Before the first release, upload `art/banner.png` in the repo's Settings under Social preview. That's the banner mod lists show for the repo.

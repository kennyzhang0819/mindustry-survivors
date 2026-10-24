# Developing

## Build and play

Run `./play.sh`. It builds the mod, puts it in the game's mods folder and starts the game. The first time, it also downloads the game (needs Java 17 or newer). It uses your real game data.

The Admin tab in Settings (give yourself levels, skip waves, add or remove curses) only shows when the game is started by `./play.sh`. Players who install the mod normally never see it.

To only build, run `./build.sh`. It creates `build/MindustrySurvivors.jar`.

## Run report

Run `./report.sh` to see every turret level, passive, curse and wave total, with the level you would reach by each wave. It takes a few seconds and doesn't open the game.

## Screenshot check

Set `SURVIVORS_AUTOTEST=arena` and start the game. It opens the menu, starts a run, picks a level-up choice, adds a few weapons, sends two waves, saves, continues the run, saves screenshots to `SURVIVORS_SHOTS` and quits. Point the game's data folder at an empty folder so your own saves aren't touched (`APPDATA` on Windows, `XDG_DATA_HOME` on Linux).

Set `SURVIVORS_AUTOTEST=damage` to measure every turret: each one fires for 3 seconds at a still group of 9 dummies, at level 1 and level 10, and the damage per second is logged. It takes about 4 minutes. To measure only some turrets, list them in `SURVIVORS_DAMAGE_ONLY`, for example `wave,arc`.

Set `SURVIVORS_AUTOTEST=chase` for a stress test: it starts a run, moves your unit in a circle and adds enemies in steps up to 5,000, logging frame times and how many enemies are closing in on you.

## Pictures

To look at random maps without the game, run `tools/MapPreview.java` (see `report.sh` for how to compile it): it writes pictures of maps to a folder.

Curse icons are drawn by `tools/make-icons.py`, and the title and mod icon by `tools/make-title.py` (both need Python with Pillow and numpy). `python tools/make-title.py banner` redraws only the banner in `art/`, which the README and GitHub show.

## Release

1. Raise `version` in `mod.hjson` (for example `0.1.0` to `0.1.1`), commit and push. Players are only offered an update when this number goes up. The first release keeps `0.1.0`.
2. Run `./release.sh`. It checks that everything is committed and pushed, builds the mod and prints the rest: open the link it shows, attach `build/MindustrySurvivors.jar` and publish.

The game installs the mod from the jar attached to the newest release, so a release without the jar can't be installed.

Before the first release, upload `art/banner.png` in the repo's Settings under Social preview. That's the banner mod lists show for the repo.

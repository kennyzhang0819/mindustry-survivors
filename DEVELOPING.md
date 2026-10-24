# Developing

## Build and play

Run `./play.sh`. It builds the mod, puts it in the game's mods folder and starts the game. The first time, it also downloads the game (needs Java 17 or newer). It uses your real game data.

To only build, run `./build.sh`. It creates `build/MindustrySurvivors.jar`.

## Run report

Run `./report.sh` to see every turret level, passive, curse and wave total, with the level you would reach by each wave. It takes a few seconds and doesn't open the game.

## Screenshot check

Set `SURVIVORS_AUTOTEST=arena` and start the game. It opens the menu, starts a run, picks a level-up choice, adds a few weapons, sends two waves, saves, continues the run, saves screenshots to `SURVIVORS_SHOTS` and quits. Point the game's data folder at an empty folder so your own saves aren't touched (`APPDATA` on Windows, `XDG_DATA_HOME` on Linux).

Set `SURVIVORS_AUTOTEST=damage` to measure every turret: each one fires for 3 seconds at a still group of 9 dummies, at level 1 and level 10, and the damage per second is logged. It takes about 4 minutes. To measure only some turrets, list them in `SURVIVORS_DAMAGE_ONLY`, for example `wave,arc`.

Set `SURVIVORS_AUTOTEST=chase` for a stress test: it starts a run, moves your unit in a circle and adds enemies in steps up to 5,000, logging frame times and how many enemies are closing in on you.

## Pictures

To look at random maps without the game, run `tools/MapPreview.java` (see `report.sh` for how to compile it): it writes pictures of maps to a folder.

Curse icons are drawn by `tools/make-icons.py`, and the title and mod icon by `tools/make-title.py` (both need Python with Pillow and numpy).

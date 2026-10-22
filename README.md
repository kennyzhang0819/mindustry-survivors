# Roguedustry

A roguelike tower-defense mod for Mindustry v8.

- You only place turrets, walls, shields and menders. No units, mining or conveyors.
- Everything you place works on its own. Turrets need no ammo or power.
- Survive 15 hand-made waves. You lose if your core is destroyed.
- Kills give XP. Each level up pauses the game and lets you pick 1 of 3 block packs.
- Each pack gives a number of blocks you can place. The build menu only shows what you have left. Removing a block you placed gives it back.
- Stronger packs appear at levels 3, 6 and 9.

## Install

1. Run `./build.sh` (needs Java 17 or newer). It creates `build/Roguedustry.jar`.
2. Copy that file into your Mindustry mods folder:
   - Standalone: `%APPDATA%\Mindustry\mods\`
   - Steam: `<Steam>\steamapps\common\Mindustry\saves\mods\`
3. Restart the game and press **Roguedustry** in the main menu.

Needs Mindustry v8 (build 154 or newer) on desktop.

## Playing

- Fly your drone to place blocks. It can't attack.
- The top-left panel shows the wave and enemies left. Press ▶ to send the next wave once the current one is cleared.
- Below that: your level, XP, the blocks you have left, and **Auto waves**, which sends each wave automatically.

## Balance numbers

Run `./balance.sh` to see, for each wave, how strong it is and what level you'll likely be. It also shows rough damage per second for each pack. The numbers are estimates. They don't account for which turrets can hit air or ground, and support turrets like Wave and Segment show about 0 damage.

## Screenshot check

Set `ROGUE_AUTOTEST=hud` and start the game. It plays one wave, saves a screenshot to `ROGUE_SHOTS` and quits. Point `APPDATA` at an empty folder so your own saves aren't touched.

## Known limits

- Loading a saved run starts it over.
- Single player only.
- Balance hasn't been tuned yet.

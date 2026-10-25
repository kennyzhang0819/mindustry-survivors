package mindustrysurvivors;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.func.*;
import arc.math.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.scene.style.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.ui.fragments.*;
import mindustry.ui.fragments.MenuFragment.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

import static mindustry.Vars.*;

public class SurvivorsUI{
    public @Nullable BaseDialog menuDialog, pickDialog;
    static final Color curseColor = Color.valueOf("b07ae8");
    static final String adminKey = "mindustry-survivors-admin", heatKey = "mindustry-survivors-heat";
    public Seq<Runnable> offered = new Seq<>();

    public void init(){
        ui.menufrag.addButton(new MenuButton("Mindustry Survivors", Icon.play, this::showMenu));
        buildHud();
        Events.run(Trigger.update, this::update);
    }

    static SurvivorsRun run(){
        return SurvivorsRun.current;
    }

    static java.lang.reflect.Field buildMenu;
    static boolean buildMenuFailed;
    static @Nullable Boolp buildMenuShown;

    //the game rebuilds its build menu from time to time and sets its visibility again, so the menu is hidden every frame
    static void hideBuildMenu(){
        if(buildMenuFailed) return;
        try{
            if(buildMenu == null){
                buildMenu = PlacementFragment.class.getDeclaredField("toggler");
                buildMenu.setAccessible(true);
            }
            Table menu = (Table)buildMenu.get(ui.hudfrag.blockfrag);
            if(menu != null && menu.visibility != buildMenuShown){
                Boolp shown = menu.visibility;
                buildMenuShown = () -> !Arena.active() && (shown == null || shown.get());
                menu.visibility = buildMenuShown;
            }
        }catch(Exception e){
            buildMenuFailed = true;
            Log.err("[Survivors] Could not hide the build menu", e);
        }
    }

    void update(){
        hideBuildMenu();
        if(!SurvivorsRun.active()) return;
        if(state.gameOver || Core.scene.hasDialog() || Time.timeSinceMillis(run().loadedAt) < 1000) return;
        if(run().pendingPicks > 0) showLevelUp();
    }

    public void showMenu(){
        menuDialog = new BaseDialog("", pageStyle(false));
        Table c = menuDialog.cont;
        c.defaults().pad(4f);

        TextureRegion title = Core.atlas.find("mindustry-survivors-title");
        if(Core.atlas.isFound(title)) c.image(title).size(Math.min(title.width, 900f), Math.min(title.width, 900f) * title.height / title.width).padBottom(6f).row();
        else c.add("MINDUSTRY SURVIVORS").fontScale(2.5f).color(Pal.accent).padBottom(4f).row();
        c.add("Made by Zerkka").color(Color.lightGray).padBottom(18f).row();

        String[] picked = {Core.settings.getString(Arena.difficultyKey, Difficulty.eradication.name())};
        int[] heat = {Mathf.clamp(Core.settings.getInt(heatKey, 0), 0, SurvivorsRun.maxHeat)};
        if(SurvivorsRun.exists()){
            c.table(row -> {
                row.button("Continue run", Icon.play, () -> {
                    menuDialog.hide();
                    SurvivorsRun.resume();
                }).size(292f, 64f);
                row.button(Icon.trash, Styles.defaulti, () -> ui.showConfirm("Delete save", "This run will be lost. Are you sure?", () -> {
                    SurvivorsRun.abandon();
                    menuDialog.hide();
                    showMenu();
                })).size(64f, 64f).padLeft(4f).tooltip("Delete this save");
            }).row();
        }
        Runnable start = () -> {
            menuDialog.hide();
            Difficulty difficulty = Structs.find(Arena.difficulties, d -> d.name().equals(picked[0]));
            if(difficulty == null && picked[0].equals(Difficulty.casual.name())) difficulty = Difficulty.easy;
            SurvivorsRun.startNew(difficulty == null ? Difficulty.eradication : difficulty, heat[0]);
        };
        c.button("New Game", Icon.play, () -> {
            if(SurvivorsRun.exists()) ui.showConfirm("New run", "Your current run will be lost. Are you sure?", start);
            else start.run();
        }).size(360f, 64f).row();
        c.button(b -> b.label(() -> "Difficulty: " + coloredDifficulty(picked[0], heat[0])), Styles.defaultb, () -> {
            BaseDialog pick = new BaseDialog("Difficulty", pageStyle(true));
            pick.cont.defaults().size(360f, 56f).pad(3f);
            for(Difficulty difficulty : Arena.difficulties){
                pick.cont.button(coloredDifficulty(difficulty.name(), 0), Styles.togglet, () -> {
                    picked[0] = difficulty.name();
                    heat[0] = 0;
                    Core.settings.put(Arena.difficultyKey, picked[0]);
                    Core.settings.put(heatKey, 0);
                    pick.hide();
                }).checked(b -> heat[0] == 0 && picked[0].equals(difficulty.name())).row();
            }
            for(int h = 1; h <= SurvivorsRun.maxHeat; h++){
                int level = h;
                pick.cont.button(coloredDifficulty(Difficulty.eradication.name(), h), Styles.togglet, () -> {
                    heat[0] = level;
                    Core.settings.put(heatKey, level);
                    pick.hide();
                }).checked(b -> heat[0] == level).row();
            }
            pick.cont.add("[#" + curseColor + "]Extra random curses are added.").wrap().width(360f).height(-1f).padTop(6f).row();
            pick.addCloseButton();
            pick.show();
        }).size(360f, 50f).row();

        c.button("Info & Database", Icon.book, this::showDatabase).size(360f, 50f).row();
        c.button("Settings", Icon.settings, () -> showSettings(0)).size(360f, 50f).row();

        menuDialog.addCloseButton();
        menuDialog.show();
    }

    static final String[] difficultyColors = {"7ce07c", "ffd37f", "ff9a4f", "f25555"};

    //greener is easier, redder is harder, and the cursed levels past Eradication are purple like their curses
    static String coloredDifficulty(String name, int heat){
        if(heat > 0) return "[#" + curseColor + "]" + Difficulty.eradication.localized() + " +" + heat + "[]";
        Difficulty difficulty = Structs.find(Arena.difficulties, d -> d.name().equals(name));
        if(difficulty == null) difficulty = name.equals(Difficulty.casual.name()) ? Difficulty.easy : Difficulty.eradication;
        return "[#" + difficultyColors[Structs.indexOf(Arena.difficulties, difficulty)] + "]" + difficulty.localized() + "[]";
    }

    public void showDatabase(){
        BaseDialog dialog = new BaseDialog("Info & Database", pageStyle(true));
        Seq<BaseTurret> turrets = new Seq<>();
        for(Block block : Weapons.steps.keys()){
            if(block instanceof BaseTurret turret) turrets.add(turret);
        }
        turrets.sort(t -> Weapons.erekir.contains(t) ? 2 : Weapons.helpers.contains(t) ? 1 : 0);
        int[] tab = {0};
        BaseTurret[] shown = {turrets.isEmpty() ? null : turrets.first()};

        Table body = new Table();
        Runnable[] fill = {null};
        fill[0] = () -> {
            body.clear();
            body.top();
            if(tab[0] == 0){
                body.pane(Styles.noBarPane, this::infoPage).width(900f).growY().top();
            }else if(tab[0] == 1){
                body.pane(Styles.noBarPane, side -> {
                    side.top().defaults().size(240f, 50f).padBottom(2f);
                    for(BaseTurret turret : turrets){
                        side.button(b -> {
                            b.left().margin(6f);
                            b.image(turret.uiIcon).size(36f).padRight(8f);
                            b.add(turret.localizedName);
                        }, Styles.togglet, () -> {
                            shown[0] = turret;
                            fill[0].run();
                        }).checked(b -> shown[0] == turret).row();
                    }
                }).width(250f).growY().top().padRight(12f);
                body.pane(Styles.noBarPane, detail -> turretDetail(detail, shown[0])).width(640f).growY().top();
            }else if(tab[0] == 2){
                body.pane(Styles.noBarPane, this::passiveList).width(900f).growY().top();
            }else{
                body.pane(Styles.noBarPane, this::curseList).width(900f).growY().top();
            }
        };

        Table c = dialog.cont;
        c.table(tabs -> {
            String[] names = {"Info", "Turrets", "Passives", "Curses"};
            for(int i = 0; i < names.length; i++){
                int index = i;
                tabs.button(names[i], Styles.togglet, () -> {
                    tab[0] = index;
                    fill[0].run();
                }).checked(b -> tab[0] == index).size(200f, 50f).pad(2f);
            }
        }).padBottom(10f).row();
        c.add(body).grow();
        fill[0].run();

        dialog.addCloseButton();
        pauseWhileShown(dialog);
        dialog.show();
    }

    static @Nullable Dialog.DialogStyle menuStyle, subpageStyle;

    //the Survivors pages cover the whole screen with their own backdrop; pages opened from the menu dim it so their text stays readable
    static Dialog.DialogStyle pageStyle(boolean dim){
        if(menuStyle == null){
            TextureRegion art = Core.atlas.find("mindustry-survivors-menu-bg");
            menuStyle = withBackdrop(Core.atlas.isFound(art) ? cover(art, 0f) : Styles.black);
            subpageStyle = withBackdrop(Core.atlas.isFound(art) ? cover(art, 0.6f) : Styles.black);
        }
        return dim ? subpageStyle : menuStyle;
    }

    static Dialog.DialogStyle withBackdrop(Drawable backdrop){
        Dialog.DialogStyle style = new Dialog.DialogStyle();
        style.stageBackground = backdrop;
        style.background = Styles.fullDialog.background;
        style.titleFont = Styles.fullDialog.titleFont;
        style.titleFontColor = Styles.fullDialog.titleFontColor;
        return style;
    }

    static Drawable cover(TextureRegion art, float dim){
        return new BaseDrawable(){
            @Override
            public void draw(float x, float y, float width, float height){
                float alpha = Draw.getColor().a, scale = Math.max(width / art.width, height / art.height);
                Draw.rect(art, x + width / 2f, y + height / 2f, art.width * scale, art.height * scale);
                if(dim > 0f){
                    Draw.color(0f, 0f, 0f, dim * alpha);
                    Fill.crect(x, y, width, height);
                    Draw.color(1f, 1f, 1f, alpha);
                }
            }
        };
    }

    void infoPage(Table t){
        t.top().left().defaults().left().padBottom(8f);
        infoSection(t, "How to play",
            "Survive " + Arena.lastWave + " waves on a random map. Enemies chase you from every side.",
            "Kill enemies and pick up their gems to level up.",
            "Turrets orbit you and fire with you.",
            "Up to " + Survivor.maxPassives + " passives boost you.");
        infoSection(t, "Changes",
            "All turrets have been reworked and rebalanced. Give all of them a try.",
            "All enemy stats are unchanged.",
            "There is no core and nothing to build.",
            "Every enemy can hit both ground and air.",
            "Boats can cross land at half speed.",
            "Bosses spawn every " + Bosses.bossEvery + " waves. They move faster the longer they stay alive.");
    }

    static void infoSection(Table t, String title, String... lines){
        t.add(title).color(Pal.accent).padTop(t.getChildren().isEmpty() ? 0f : 12f).row();
        for(String line : lines) t.add("[accent]-[] " + line).wrap().width(860f).row();
    }

    static void turretDetail(Table t, @Nullable BaseTurret turret){
        t.top().left();
        if(turret == null) return;
        float dps = Weapons.levelOneDps(turret);
        t.table(head -> {
            head.left();
            head.image(turret.uiIcon).size(64f).padRight(12f);
            head.table(name -> {
                name.left();
                name.add(turret.localizedName).color(Pal.accent).left().row();
                name.add("Weapon" + (dps > 0f ? ", about " + Math.round(dps) + " damage per second at level 1" : "")).color(Color.lightGray).left();
            });
        }).left().padBottom(10f).row();
        t.add(Survivor.intro(turret)).wrap().width(620f).left().padBottom(6f).row();
        t.add("Every level after the first also adds +" + Math.round(Weapons.levelDamage * 100) + "% damage.").color(Color.lightGray).wrap().width(620f).left().padBottom(10f).row();

        t.table(rows -> {
            rows.left().defaults().left().pad(3f);
            UnlockableContent ammo = Weapons.firstAmmo(turret);
            levelRow(rows, 1, ammo, ammo == null ? "Joins your weapons" : "Joins your weapons with " + ammo.localizedName + " ammo");
            for(int level = 2; level <= Weapons.maxLevel; level++){
                Weapons.Step step = Weapons.step(turret, level);
                if(step != null) levelRow(rows, level, step.icon, step.text);
            }
        }).left();
    }

    static void levelRow(Table rows, int level, @Nullable UnlockableContent icon, String text){
        rows.add("Lv " + level).color(level == Weapons.maxLevel ? Pal.accent : Color.lightGray).width(60f);
        if(icon != null) rows.image(icon.uiIcon).size(28f);
        else rows.add().size(28f);
        rows.add(text).wrap().width(520f).padLeft(6f).row();
    }

    void curseList(Table t){
        t.top().left();
        t.add("Curses make the enemy stronger. They only come with " + Difficulty.eradication.localized() + " +1 to +4: when the run starts, a few are picked at random, and higher levels get more and harsher ones, never more than " + Curses.maxRolled + ". Each curse is worth points by how harsh it is. Your curses show under the level bar.").wrap().width(860f).color(Color.lightGray).left().padBottom(10f).row();
        t.table(rows -> {
            rows.left().defaults().left().pad(4f);
            for(Curses.Curse curse : Curses.all){
                rows.image(curse.icon()).size(40f);
                rows.add(curse.name).color(curseColor).width(220f).padLeft(6f);
                rows.add(curse.cost + (curse.cost == 1 ? " point" : " points")).color(Color.lightGray).width(90f);
                rows.add(curse.description).wrap().width(480f).row();
            }
        }).left();
    }

    void passiveList(Table t){
        t.top().left();
        t.add("You can hold up to " + Survivor.maxPassives + " passives.").color(Color.lightGray).left().padBottom(10f).row();
        t.table(rows -> {
            rows.left().defaults().left().pad(4f);
            for(Survivor.Passive passive : Survivor.passives){
                rows.image(passive.icon.uiIcon).size(40f);
                rows.add(passive.name).color(passiveColor).width(300f).padLeft(6f);
                StringBuilder text = new StringBuilder(passive.step(1));
                for(int l = 2; l <= passive.maxLevel && passive.steps.length > 1; l++) text.append("\n[lightgray]").append(l).append(": ").append(passive.step(l));
                rows.add(text.toString()).wrap().width(400f);
                rows.add(passive.maxLevel + (passive.maxLevel == 1 ? " level" : " levels")).color(Color.lightGray).width(100f).row();
            }
        }).left();
    }

    static void curseIcons(Table table, Seq<Curses.Curse> list, float size, int perRow){
        table.clear();
        table.left();
        if(list.isEmpty()){
            table.add("[lightgray]none");
            return;
        }
        int i = 0;
        for(Curses.Curse m : list){
            table.image(m.icon()).size(size).pad(2f).tooltip(m.name + ": " + m.description);
            if(++i % perRow == 0) table.row();
        }
    }

    static void pauseWhileShown(BaseDialog dialog){
        boolean pause = state.isGame() && !state.gameOver;
        dialog.shown(() -> {
            if(pause) state.set(State.paused);
        });
        dialog.hidden(() -> {
            if(pause && state.isGame() && !state.gameOver) state.set(State.playing);
        });
    }

    public static void showScore(boolean end){
        BaseDialog dialog = end ? new BaseDialog("Run over", pageStyle(true)) : new BaseDialog("Score");
        Table c = dialog.cont;
        float total = Score.total();
        Seq<String> keys = new Seq<>();
        for(var e : Score.damage) keys.add(e.key);
        for(var e : Score.kills) if(!keys.contains(e.key)) keys.add(e.key);
        keys.sort(k -> -Score.damage.get(k, 0f));
        float top = keys.isEmpty() ? 1f : Math.max(Score.damage.get(keys.first(), 0f), 1f);
        int allKills = 0;
        for(var e : Score.kills) allKills += e.value;
        if(end && Score.lost){
            c.table(row -> {
                row.add("[scarlet]Killed by").padRight(8f);
                if(Score.killer == null){
                    row.add("[lightgray]unknown");
                }else{
                    row.image(Score.killer.uiIcon).size(40f).scaling(Scaling.fit).padRight(6f);
                    row.add(Score.killer.localizedName + (Score.killerHow.isEmpty() ? "" : " [lightgray](" + Score.killerHow + ")"));
                }
            }).padBottom(10f).row();
        }
        c.add((end ? "Wave " + Arena.shownWave() + "   " : "") + "Total damage [accent]" + mindustry.core.UI.formatAmount((long)total) + "[]   Kills [accent]" + allKills).padBottom(12f).row();
        if(keys.isEmpty()){
            c.add("[lightgray]No damage dealt yet.").pad(20f).row();
        }else{
            c.pane(Styles.noBarPane, rows -> {
                rows.defaults().pad(3f);
                for(int i = 0; i < keys.size; i++){
                    String key = keys.get(i);
                    boolean first = i == 0;
                    float dealt = Score.damage.get(key, 0f), share = total <= 0f ? 0f : dealt / total;
                    Block block = Score.walker.equals(key) ? null : content.block(key);
                    TextureRegion icon = block == null ? Chase.walker.uiIcon : block.uiIcon;
                    String name = block == null ? "Walker gun" : block.localizedName;
                    Survivor.Gun gun = block == null ? null : Survivor.ownedGuns.find(g -> g.turret == block);
                    rows.add((i == 0 ? "[accent]" : "[lightgray]") + "#" + (i + 1)).width(40f).left();
                    rows.image(icon).size(36f);
                    rows.add(name + (gun == null ? "" : " [lightgray]Lv " + Survivor.gunLevel.get(gun, 1))).width(190f).left().padLeft(6f);
                    rows.add(new Bar(() -> mindustry.core.UI.formatAmount((long)dealt), () -> first ? Pal.accent : Pal.lancerLaser, () -> dealt / top)).size(240f, 26f);
                    rows.add(Strings.autoFixed(share * 100f, 1) + "%").width(70f).right();
                    rows.add("[lightgray]" + Score.kills.get(key, 0) + " kills").width(110f).right();
                    rows.row();
                }
            }).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.65f).row();
        }
        dialog.addCloseButton();
        if(!end) pauseWhileShown(dialog);
        dialog.show();
    }

    public void showEndlessChoice(){
        BaseDialog dialog = new BaseDialog("Wave " + Arena.lastWave + " cleared!");
        Table c = dialog.cont;
        c.add("Keep fighting harder and harder enemies?").padBottom(14f).row();
        c.table(row -> {
            row.button("Keep fighting", Icon.refresh, () -> {
                dialog.hide();
                Arena.startEndless();
            }).size(270f, 64f).pad(4f);
            row.button("End run", Icon.ok, () -> {
                dialog.hide();
                Arena.endRun();
            }).size(270f, 64f).pad(4f);
        });
        pauseWhileShown(dialog);
        dialog.show();
    }

    public void showLevelUp(){
        if(Survivor.offer.isEmpty()) Survivor.offer.set(Survivor.roll());
        pickDialog = new BaseDialog("Level up!");
        Table c = pickDialog.cont;
        c.add("Pick one.").padBottom(8f).row();
        offered = Survivor.offer.map(choice -> () -> Survivor.take(choice));
        c.table(row -> {
            for(int i = 0; i < Survivor.offer.size; i++){
                Survivor.Choice choice = Survivor.offer.get(i);
                int index = i;
                int next = choice.nextLevel();
                Color color = choice.gun != null ? Pal.accent : choice.passive != null ? passiveColor : choice.bonus ? Pal.lightOrange : Pal.heal;
                String kind = choice.gun != null ? "WEAPON" : choice.passive != null ? "PASSIVE" : choice.bonus ? "BONUS" : "HEAL";
                row.button(card -> {
                    card.top().margin(0f);
                    card.image(Tex.whiteui).color(color).height(5f).growX().row();
                    card.add(kind).color(color).fontScale(0.85f).padTop(8f).padBottom(6f).row();
                    card.image(choice.icon()).size(64f).padBottom(6f).row();
                    card.add(choice.name()).color(color).wrap().width(210f).padBottom(4f).get().setAlignment(Align.center);
                    card.row();
                    card.add(next == 0 ? "" : next == 1 ? "New!" : "Level " + next).color(next == 1 ? Pal.accent : Color.lightGray).padBottom(8f).row();
                    TextureRegion stepIcon = choice.stepIcon();
                    card.table(line -> {
                        if(stepIcon != null) line.image(stepIcon).size(32f).padRight(8f).top();
                        line.add(choice.description()).wrap().width(stepIcon != null ? 160f : 200f);
                    });
                }, cardStyle(color), () -> choose(index)).size(240f, 290f).pad(8f).top();
            }
        }).row();
        c.table(t -> loadout(t, 48f)).padTop(10f);
        pauseWhileShown(pickDialog);
        pickDialog.show();
    }

    static final Color passiveColor = Color.valueOf("5c9cff");

    //a card or slot fill in a dark shade of its type's color, so weapons, passives and heals read apart at a glance
    static Drawable shade(Color color, float brightness){
        return ((TextureRegionDrawable)Tex.whiteui).tint(Tmp.c1.set(color).mul(brightness).a(1f));
    }

    static Button.ButtonStyle cardStyle(Color color){
        Button.ButtonStyle style = new Button.ButtonStyle(Styles.defaultb);
        style.up = shade(color, 0.18f);
        style.over = shade(color, 0.28f);
        style.down = shade(color, 0.38f);
        return style;
    }

    static void loadout(Table t, float size){
        t.left();
        t.table(row -> {
            for(int i = 0; i < Survivor.maxWeapons; i++){
                Survivor.Gun gun = i < Survivor.ownedGuns.size ? Survivor.ownedGuns.get(i) : null;
                slot(row, gun == null ? null : gun.turret.uiIcon, gun == null ? 0 : Survivor.gunLevel.get(gun, 1), gun == null ? "Empty weapon slot" : gun.turret.localizedName, size, Pal.accent);
            }
        }).left().row();
        t.table(row -> {
            for(int i = 0; i < Survivor.maxPassives; i++){
                Survivor.Passive passive = i < Survivor.ownedPassives.size ? Survivor.ownedPassives.get(i) : null;
                slot(row, passive == null ? null : passive.icon.uiIcon, passive == null ? 0 : Survivor.level(passive), passive == null ? "Empty passive slot" : passive.name + "\n[lightgray]" + passive.step(Survivor.level(passive)), size, passiveColor);
            }
        }).left();
    }

    static void slot(Table row, @Nullable TextureRegion icon, int level, String tip, float size, Color color){
        Table badge = new Table();
        badge.bottom().right();
        if(level > 0) badge.add(String.valueOf(level)).style(Styles.outlineLabel);
        Table back = new Table(shade(color, icon == null ? 0.12f : 0.3f));
        if(icon != null) back.image(icon).size(size * 0.7f);
        row.stack(back, badge).size(size).pad(2f).tooltip(tip);
    }

    public void choose(int index){
        if(pickDialog == null || index >= offered.size) return;
        Runnable pick = offered.get(index);
        //a second click during the close animation must not pick again
        offered = new Seq<>();
        pick.run();
        pickDialog.hide();
    }

    public void showAdmin(){
        showSettings(1);
    }

    public void showSettings(int startTab){
        BaseDialog dialog = new BaseDialog("Settings", pageStyle(true));
        int[] tab = {MindustrySurvivorsMod.admin ? startTab : 0};
        Table body = new Table();
        Runnable[] fill = {null};
        fill[0] = () -> {
            body.clear();
            body.top();
            if(tab[0] == 0) gameSettings(body);
            else adminPage(body, dialog);
        };
        if(MindustrySurvivorsMod.admin) dialog.cont.table(tabs -> {
            String[] names = {"Game", "Admin"};
            for(int i = 0; i < names.length; i++){
                int index = i;
                tabs.button(names[i], Styles.togglet, () -> {
                    tab[0] = index;
                    fill[0].run();
                }).checked(b -> tab[0] == index).size(200f, 50f).pad(2f);
            }
        }).padBottom(10f).row();
        dialog.cont.add(body).grow().top();
        fill[0].run();
        dialog.addCloseButton();
        pauseWhileShown(dialog);
        dialog.show();
    }

    void gameSettings(Table t){
        t.defaults().pad(3f);
        t.add("Turrets").color(Pal.accent).padBottom(4f).row();
        t.add("Which turrets level-ups can offer.").color(Color.lightGray).padBottom(6f).row();
        for(int i = 0; i < Survivor.pools.length; i++){
            int pool = i;
            t.button(Survivor.pools[i], Styles.togglet, () -> Core.settings.put(Survivor.poolKey, pool))
                .checked(b -> Core.settings.getInt(Survivor.poolKey, 0) == pool).size(360f, 54f).row();
        }
        t.add("Enemies").color(Pal.accent).padTop(12f).padBottom(4f).row();
        t.add("Which planet's enemies waves bring.").color(Color.lightGray).padBottom(6f).row();
        for(int i = 0; i < Survivor.pools.length; i++){
            int pool = i;
            t.button(Survivor.pools[i], Styles.togglet, () -> Core.settings.put(Survivor.enemyKey, pool))
                .checked(b -> Core.settings.getInt(Survivor.enemyKey, 0) == pool).size(360f, 54f).row();
        }
        t.add("Changes apply to new runs.").color(Color.lightGray).padTop(6f).row();
    }

    void adminPage(Table c, BaseDialog dialog){
        c.defaults().pad(3f);
        c.check("Show the Admin button during a run", Core.settings.getBool(adminKey, false), v -> Core.settings.put(adminKey, v)).padBottom(8f).row();
        SurvivorsRun run = run();
        if(!SurvivorsRun.active()){
            c.add("Start or continue a run to use the admin tools.").color(Color.lightGray).row();
            return;
        }

        c.table(row -> {
            row.defaults().size(220f, 54f).pad(4f);
            row.button("+1 level-up pick", Icon.add, () -> {
                run.pendingPicks++;
                dialog.hide();
            });
            row.button("Skip to a wave", Icon.right, () -> showWaves(dialog, false));
            row.button("Jump to a wave", Icon.upload, () -> showWaves(dialog, true));
            row.button("+1 level", Icon.up, () -> {
                run.level++;
                run.xp = 0f;
                run.pendingPicks++;
                dialog.hide();
            });
        }).padBottom(8f).row();
        c.add("Tap a curse to add or remove it.").color(Color.lightGray).padBottom(4f).row();

        c.pane(list -> {
            list.left().defaults().left().pad(2f);
            list.add("[#" + curseColor + "]Curses").row();
            list.table(row -> {
                row.left();
                int i = 0;
                for(Curses.Curse curse : Curses.all){
                    adminCell(row, curse.icon(), curse.name, curseColor, () -> run.curses.contains(curse), () -> run.adminToggle(curse));
                    if(++i % adminPerRow == 0) row.row();
                }
            }).row();
        }).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.55f).width(1100f);
    }

    static final int adminPerRow = 7;

    static void adminCell(Table row, TextureRegion icon, String name, Color color, Boolp owned, Runnable toggle){
        row.button(b -> {
            b.image(icon).size(40f).row();
            b.add(name).color(color).wrap().width(136f).padTop(4f).get().setAlignment(Align.center);
        }, Styles.clearTogglei, toggle).checked(b -> owned.get()).size(150f, 100f).pad(2f);
    }

    void showWaves(BaseDialog admin, boolean fresh){
        BaseDialog dialog = new BaseDialog(fresh ? "Jump to a wave" : "Skip to a wave");
        dialog.cont.add(fresh ? "You keep your build and get the levels the skipped waves would have given if you picked up every gem. Each wave shows how many levels you gain." : "Enemies on the map are removed and the wave you pick is sent now.").color(Color.lightGray).wrap().width(860f).padBottom(8f).row();
        dialog.cont.table(grid -> {
            for(int w = 1; w <= Arena.lastWave; w++){
                int wave = w;
                int gain = fresh ? Survivor.jumpGain(wave) : 0;
                grid.button(w + (gain > 0 ? " [accent]+" + gain : ""), Styles.cleart, () -> {
                    dialog.hide();
                    admin.hide();
                    if(fresh) Survivor.jumpTo(wave);
                    Arena.skipTo(wave);
                }).size(fresh ? 116f : 80f, 56f).pad(2f);
                if(w % 10 == 0) grid.row();
            }
        });
        dialog.addCloseButton();
        dialog.show();
    }

    //the game's label shows the next wave, so during a run it is swapped for the one being fought
    static void showCurrentWave(Table waves){
        arc.scene.Group status = waves.find("status");
        if(status == null || !(status.getChildren().find(e -> e instanceof Label) instanceof Label label)) return;
        try{
            var field = arc.scene.Element.class.getDeclaredField("update");
            field.setAccessible(true);
            Runnable game = (Runnable)field.get(label);
            if(game == null) return;
            label.update(() -> {
                game.run();
                if(!SurvivorsRun.active() || state.wave == Arena.shownWave()) return;
                String next = Core.bundle.format("wave", state.wave), text = label.getText().toString();
                if(text.contains(next)) label.setText(text.replace(next, Core.bundle.format("wave", Arena.shownWave())));
            });
        }catch(Exception e){
            Log.err("[Survivors] Could not change the wave label", e);
        }
    }

    void buildHud(){
        Table panel = new Table(Tex.wavepane);
        panel.name = "survivors-hud";
        panel.visible(() -> SurvivorsRun.active() && ui.hudfrag.shown());
        panel.left();
        panel.defaults().growX();
        panel.label(() -> run() == null ? "" : "Difficulty: " + coloredDifficulty(Arena.difficulty().name(), run().heat)).left().padLeft(6f).padTop(2f).row();
        panel.add(new Bar(() -> run() == null ? "" : "Level " + (run().level + 1), () -> Pal.accent, () -> run() == null ? 0f : run().xpProgress())).height(24f).pad(4f).row();
        Table cursed = new Table();
        String[] lastCurses = {""};
        cursed.update(() -> {
            SurvivorsRun run = run();
            String key = run == null ? "" : run.curses.toString();
            if(key.equals(lastCurses[0])) return;
            lastCurses[0] = key;
            cursed.clear();
            cursed.left();
            if(run == null || run.curses.isEmpty()) return;
            cursed.add("[#" + curseColor + "]Curses").padRight(6f);
            for(Curses.Curse curse : run.curses){
                cursed.image(curse.icon()).size(28f).pad(2f).tooltip("[#" + curseColor + "]" + curse.name + "[]\n[lightgray]" + curse.description);
            }
        });
        panel.add(cursed).left().pad(2f);
        panel.row();
        Table admin = new Table();
        boolean[] shown = {false};
        admin.update(() -> {
            boolean on = MindustrySurvivorsMod.admin && Core.settings.getBool(adminKey, false);
            if(on == shown[0] && admin.hasChildren() == on) return;
            shown[0] = on;
            admin.clear();
            if(on) admin.button("Admin", Icon.wrench, Styles.cleart, this::showAdmin).growX().height(40f);
        });
        panel.add(admin).growX();
        panel.row();
        panel.button("Score", Icon.list, Styles.cleart, () -> showScore(false)).growX().height(36f).row();
        Table loadoutTable = new Table();
        String[] last = {""};
        loadoutTable.update(() -> {
            StringBuilder key = new StringBuilder();
            for(Survivor.Gun gun : Survivor.ownedGuns) key.append(gun.turret.name).append(Survivor.gunLevel.get(gun, 1));
            for(Survivor.Passive p : Survivor.ownedPassives) key.append(p.id).append(Survivor.level(p));
            if(key.toString().equals(last[0])) return;
            last[0] = key.toString();
            loadoutTable.clear();
            loadout(loadoutTable, 36f);
        });
        panel.add(loadoutTable).left().pad(2f);

        Table waves = ui.hudGroup.find("waves");
        if(waves != null) showCurrentWave(waves);
        if(waves != null){
            waves.row();
            waves.add(panel).growX().left().padTop(4f);
        }else{
            ui.hudGroup.fill(t -> t.top().left().marginTop(130f).add(panel).width(330f));
        }
    }
}

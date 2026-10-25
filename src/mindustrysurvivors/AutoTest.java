package mindustrysurvivors;

import arc.*;
import arc.files.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;

import static mindustry.Vars.*;

public class AutoTest{
    final String shotDir = OS.env("SURVIVORS_SHOTS") == null ? "survivors-shots" : OS.env("SURVIVORS_SHOTS");
    @Nullable String pendingShot;
    boolean continued;

    public static boolean enabled(){
        return System.getenv("SURVIVORS_AUTOTEST") != null;
    }

    public void begin(){
        Log.info("[Survivors-Test] Autotest started");
        Core.settings.put("backgroundpause", false);
        Core.settings.put("musicvol", 0);
        Core.settings.put("sfxvol", 0);
        Core.settings.put("ambientvol", 0);
        Core.audio.musicBus.setVolume(0f);
        Core.audio.soundBus.setVolume(0f);
        control.sound.uiBus.setVolume(0f);
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> Log.err("[Survivors-Test] CRASH on thread " + thread.getName(), e));
        //the game's error dialogs don't write to the log, so any dialog with the error heading is logged here
        ObjectSet<Element> seen = new ObjectSet<>();
        Events.run(Trigger.update, () -> {
            for(Element e : Core.scene.root.getChildren()){
                if(!(e instanceof Dialog dialog) || !seen.add(dialog)) continue;
                Seq<String> texts = new Seq<>();
                collectText(dialog, texts);
                if(texts.contains(Core.bundle.get("error.title"))) Log.err("[Survivors-Test] ERROR DIALOG: @", texts.toString(" | "));
            }
        });

        Events.run(Trigger.uiDrawEnd, () -> {
            if(pendingShot != null){
                Fi file = Core.files.absolute(shotDir).child(pendingShot + ".png");
                ScreenUtils.saveScreenshot(file);
                Log.info("[Survivors-Test] Screenshot @", file.absolutePath());
                pendingShot = null;
            }
        });

        later(60f * 8f, () -> {
            Log.info("[Survivors-Test] RESULT timeout");
            Core.app.exit();
        });

        String mode = System.getenv("SURVIVORS_AUTOTEST");
        if("fresh".equals(mode)){
            later(2f, () -> SurvivorsRun.startNew(Difficulty.eradication, 0));
            Events.run(Trigger.newGame, () -> later(5f, () -> {
                SurvivorsRun run = SurvivorsRun.current;
                Survivor.give(Survivor.guns.first());
                int before = run.level;
                Survivor.jumpTo(21);
                Arena.skipTo(21);
                Log.info("[Survivors-Test] Jump: level before=@", before);
                Log.info("[Survivors-Test] Fresh start: wave=@ level=@ picks=@ weapons=@ xp=@", state.wave, run.level, run.pendingPicks, Survivor.ownedGuns.size, (int)run.xp);
                later(2f, () -> shot("fresh-start"));
                later(3f, () -> {
                    Log.info("[Survivors-Test] RESULT done");
                    Core.app.exit();
                });
            }));
            return;
        }
        if("boss".equals(mode)){
            later(2f, () -> SurvivorsRun.startNew(Difficulty.eradication, 0));
            Events.run(Trigger.newGame, () -> later(4f, this::boss));
            return;
        }
        if("endless".equals(mode)){
            later(2f, () -> SurvivorsRun.startNew());
            Events.run(Trigger.newGame, () -> later(5f, this::endless));
            return;
        }
        if("damage".equals(mode)){
            later(2f, () -> SurvivorsRun.startNew(Difficulty.eradication, 0));
            Events.run(Trigger.newGame, () -> later(4f, this::damage));
            return;
        }
        if("death".equals(mode)){
            later(2f, () -> SurvivorsRun.startNew(Difficulty.eradication, 0));
            Events.run(Trigger.newGame, () -> later(4f, () -> {
                Unit me = player.unit();
                for(int i = 0; i < 4; i++){
                    Tmp.v1.trns(i * 90f, 40f);
                    UnitTypes.dagger.spawn(state.rules.waveTeam, me.x + Tmp.v1.x, me.y + Tmp.v1.y);
                }
                me.health = 1f;
                later(6f, () -> {
                    Log.info("[Survivors-Test] Death: gameOver=@ lost=@ killer=@ how=@", state.gameOver, Score.lost, Score.killer, Score.killerHow);
                    shot("death-score");
                });
                later(7f, () -> {
                    Log.info("[Survivors-Test] RESULT done");
                    Core.app.exit();
                });
            }));
            return;
        }
        if("chase".equals(mode)){
            Chase.stressTest(this);
            return;
        }
        if("arena".equals(mode)){
            later(1f, () -> {
                Core.settings.put(SurvivorsUI.adminKey, true);
                MindustrySurvivorsMod.ui.showMenu();
            });
            later(2f, () -> shot("01-difficulty"));
            later(2.2f, () -> {
                Element button = Core.scene.find(e -> e instanceof Label l && l.getText().toString().startsWith("Difficulty:"));
                if(button != null && button.parent != null) button.parent.fireClick();
            });
            later(3f, () -> shot("01-difficulty-pick"));
            later(3.2f, () -> Core.scene.getDialog().hide());
            later(3.5f, () -> MindustrySurvivorsMod.ui.showDatabase());
            later(4.3f, () -> shot("01-database-info"));
            later(4.6f, () -> clickTab("Turrets"));
            later(5.3f, () -> shot("01-database-turrets"));
            later(5.6f, () -> clickTab("Passives"));
            later(6.3f, () -> shot("01-database-passives"));
            later(6.6f, () -> Core.scene.getDialog().hide());
            later(6.8f, () -> MindustrySurvivorsMod.ui.showSettings(0));
            later(7.4f, () -> shot("01-settings"));
            later(7.6f, () -> clickTab("Admin"));
            later(8.1f, () -> shot("01-settings-admin"));
            later(8.3f, () -> Core.scene.getDialog().hide());
            later(8.6f, () -> {
                Core.scene.getDialog().hide();
                SurvivorsRun.startNew(Difficulty.eradication, SurvivorsRun.maxHeat);
            });
            Events.run(Trigger.newGame, () -> {
                if(!continued) later(7f, this::arena);
            });
            return;
        }
        if("bench".equals(mode)){
            later(2f, () -> {
                SurvivorsRun.startNew();
            });
            Events.run(Trigger.newGame, () -> later(10f, this::bench));
            return;
        }
        if("curses".equals(mode)){
            later(2f, () -> {
                SurvivorsRun.startNew();
            });
            Events.run(Trigger.newGame, () -> later(10f, this::curses));
            return;
        }
        Log.info("[Survivors-Test] RESULT unknown mode @", mode);
        Core.app.exit();
    }

    static void collectText(Element e, Seq<String> out){
        if(e instanceof Label label) out.add(label.getText().toString());
        if(e instanceof Group group) for(Element child : group.getChildren()) collectText(child, out);
    }

    void boss(){
        state.rules.waveTimer = false;
        for(Unit u : state.rules.waveTeam.data().units.copy()) u.remove();
        Unit me = player.unit();
        Events.run(Trigger.update, () -> {
            Unit p = player.unit();
            if(p != null && !p.dead){
                p.health = p.maxHealth;
                p.shield = 1e9f;
            }
        });
        Bosses.spawn(false);
        later(0.5f, () -> {
            Unit b = Groups.unit.find(Bosses::is);
            b.set(me.x + 90f, me.y);
            b.apply(StatusEffects.freezing, 600f);
            b.apply(StatusEffects.slow, 600f);
            b.apply(StatusEffects.disarmed, 600f);
            b.apply(StatusEffects.wet, 600f);
        });
        later(1.5f, () -> {
            Unit b = Groups.unit.find(Bosses::is);
            Log.info("[Survivors-Test] Ground boss: type=@ health=@/@ hitSize=@ (base @) bossBar=@ frozen=@ slowed=@ disarmed=@ wet=@ speedMul=@",
                b.type.name, (int)b.health, (int)b.maxHealth, b.hitSize, b.type.hitSize, state.boss() != null,
                b.hasEffect(StatusEffects.freezing), b.hasEffect(StatusEffects.slow), b.hasEffect(StatusEffects.disarmed), b.hasEffect(StatusEffects.wet), b.speedMultiplier);
            shot("boss-ground");
            Bosses.alive.put(b.id, 60f * 61f);
        });
        later(2.5f, () -> {
            Unit b = Groups.unit.find(Bosses::is);
            Log.info("[Survivors-Test] Ground boss after 61s alive: haste=@ speedMul=@ damageMul=@ (regular reign health @)", Bosses.haste(b), b.speedMultiplier, b.damageMultiplier, (int)b.type.health);
            Bosses.alive.put(b.id, 0f);
        });
        later(14f, () -> {
            Log.info("[Survivors-Test] Ground boss after 14s: enemies=@ bullets=@", state.rules.waveTeam.data().units.size, Groups.bullet.count(x -> x.team == state.rules.waveTeam));
            shot("boss-ground-fight");
            SurvivorsRun.current.pendingPicks = 0;
            Unit b = Groups.unit.find(Bosses::is);
            b.set(player.unit().x + 150f, player.unit().y);
            b.kill();
        });
        later(15f, () -> {
            int before = SurvivorsRun.current.pendingPicks;
            Log.info("[Survivors-Test] Ground boss dead: chests=@ picks=@", Bosses.chestX.size, before);
            shot("boss-chest");
            SurvivorsRun.current.pendingPicks = 0;
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
            if(Bosses.chestX.size > 0) player.unit().set(Bosses.chestX.first(), Bosses.chestY.first());
        });
        later(15.5f, () -> {
            Log.info("[Survivors-Test] After chest: chests=@ picks=@", Bosses.chestX.size, SurvivorsRun.current.pendingPicks);
            SurvivorsRun.current.pendingPicks = 0;
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
            for(Unit u : state.rules.waveTeam.data().units.copy()) u.remove();
            Bosses.spawn(true);
        });
        later(16f, () -> {
            Unit b = Groups.unit.find(Bosses::is);
            Unit p = player.unit();
            b.set(p.x + 120f, p.y + 40f);
            b.health = b.maxHealth * 0.4f;
        });
        later(20f, () -> {
            Unit b = Groups.unit.find(Bosses::is);
            Log.info("[Survivors-Test] Air boss: type=@ health=@/@ hitSize=@ enraged=@ enemies=@ bullets=@", b.type.name, (int)b.health, (int)b.maxHealth, b.hitSize,
                b.hasEffect(Bosses.enraged), state.rules.waveTeam.data().units.size, Groups.bullet.count(x -> x.team == state.rules.waveTeam));
            shot("boss-air");
        });
        later(21f, () -> {
            Log.info("[Survivors-Test] RESULT done");
            Core.app.exit();
        });
    }

    void arena(){
        SurvivorsRun run = SurvivorsRun.current;
        SurvivorsUI rui = MindustrySurvivorsMod.ui;
        Unit me = player.unit();
        Log.info("[Survivors-Test] Curses rolled at Eradication +@: @", run.heat, run.curses.map(c -> c.name));
        Log.info("[Survivors-Test] Arena: active=@ size=@x@ ground=@ water=@ cores=@ player=@ at @,@ weapons=@ allowed=@",
            SurvivorsRun.active(), world.width(), world.height(), Arena.groundSpawns.size, Arena.waterSpawns.size, state.rules.defaultTeam.cores().size,
            me == null ? "none" : me.type.name, me == null ? -1 : me.tileX(), me == null ? -1 : me.tileY(), Survivor.ownedGuns.map(g -> g.turret.name), state.rules.bannedBlocks.size);
        shot("02-start");
        later(1f, () -> {
            Survivor.Choice a = new Survivor.Choice(), b = new Survivor.Choice(), c = new Survivor.Choice();
            a.passive = Survivor.amount;
            b.passive = Survivor.armor;
            c.gun = Survivor.guns.find(g -> g.turret == Blocks.spectre);
            Survivor.Choice d = new Survivor.Choice();
            d.bonus = true;
            Survivor.offer.set(Seq.with(a, b, c, d));
            run.pendingPicks = 1;
        });
        later(2f, () -> {
            Log.info("[Survivors-Test] Level-up: dialog=@ offer=@", Core.scene.hasDialog(), Survivor.offer.map(Survivor.Choice::name));
            shot("03-levelup");
        });
        later(3f, () -> {
            rui.choose(0);
            for(int i = 0; i < 7; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.lancer));
            for(int i = 0; i < 9; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.hail));
            for(int i = 0; i < 5; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.duo));
            Survivor.give(Survivor.bulwark);
            for(int i = 0; i < 5; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.parallax));
            for(int i = 0; i < 8; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.segment));
            for(int i = 0; i < 3; i++) Survivor.give(Survivor.guns.find(g -> g.turret == Blocks.wave));
            Survivor.give(Survivor.aura);
            Survivor.give(Survivor.lifesteal);
            Survivor.give(Survivor.spark);
            for(int i = 0; i < 3; i++) Survivor.give(Survivor.shield);
            Log.info("[Survivors-Test] Loadout: weapons=@ passives=@ mounts=@", Survivor.ownedGuns.map(g -> g.turret.name + ":" + Survivor.gunLevel.get(g, 0)),
                Survivor.ownedPassives.map(p -> p.name + ":" + Survivor.level(p)), player.unit().mounts.length);
            logic.runWave();
            logic.runWave();
        });
        later(3.6f, () -> renderer.setScale(renderer.maxScale()));
        later(4.3f, () -> {
            int before = Survivor.gemValue.size;
            Survivor.magX.set(0, player.unit().x);
            Survivor.magY.set(0, player.unit().y);
            later(0.3f, () -> {
                int flying = 0;
                for(int i = 0; i < Survivor.gemPulled.size; i++) if(Survivor.gemPulled.get(i)) flying++;
                Log.info("[Survivors-Test] Magnet: gems on map @ before; 0.3s after pickup @ left, @ of them flying to you, dialog=@", before, Survivor.gemValue.size, flying, Core.scene.hasDialog());
            });
        });
        later(5f, () -> {
            Unit u = player.unit();
            float[] before = new float[u.mounts.length];
            for(int i = 0; i < u.mounts.length; i++) before[i] = u.rotation + u.mounts[i].rotation;
            u.rotation += 120f;
            later(0.2f, () -> {
                StringBuilder out = new StringBuilder();
                for(int i = 2; i < u.mounts.length; i++) out.append(Math.round(Angles.angleDist(before[i], u.rotation + u.mounts[i].rotation))).append(u.mounts[i].target == null ? "" : "(aiming)").append(" ");
                Log.info("[Survivors-Test] Turret aim change after turning 120 degrees: @", out);
            });
        });
        later(3.7f, () -> {
            Unit u = player.unit();
            int[] values = {1, 1, 3, 8, 12, 40};
            for(int i = 0; i < values.length; i++){
                Tmp.v1.trns(i * 60f, 60f);
                Survivor.gemX.add(u.x + Tmp.v1.x);
                Survivor.gemY.add(u.y + Tmp.v1.y);
                Survivor.gemValue.add(values[i]);
                Survivor.gemPulled.add(false);
            }
            Survivor.kitX.add(u.x + 70f, u.x - 70f);
            Survivor.kitY.add(u.y + 30f, u.y - 30f);
            Survivor.magX.add(u.x);
            Survivor.magY.add(u.y - 75f);
            for(int i = 0; i < 20; i++){
                Survivor.gemX.add(u.x + Mathf.range(900f));
                Survivor.gemY.add(u.y + Mathf.range(900f));
                Survivor.gemValue.add(1);
                Survivor.gemPulled.add(false);
            }
        });
        later(4f, () -> shot("03b-turrets"));
        later(7f, () -> {
            Unit u = player.unit();
            Log.info("[Survivors-Test] Fighting: wave=@ enemies=@ gems=@ xp=@ level=@ health=@/@ bullets=@", state.wave, state.rules.waveTeam.data().units.size,
                Survivor.gemValue.size, (int)run.xp, run.level, (int)u.health, (int)u.maxHealth, Groups.bullet.size());
            int i = 0;
            for(Unit e : state.rules.waveTeam.data().units){
                if(i >= 8) break;
                Tmp.v1.trns(i++ * 45f, 80f);
                e.set(u.x + Tmp.v1.x, u.y + Tmp.v1.y);
            }
        });
        later(9.5f, () -> {
            SurvivorsRun.current.pendingPicks = 0;
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
            Unit u = player.unit();
            for(int i = 0; i < 12; i++){
                Tmp.v1.trns(i * 30f, 120f);
                (i % 2 == 0 ? UnitTypes.flare : UnitTypes.horizon).spawn(state.rules.waveTeam, u.x + Tmp.v1.x, u.y + Tmp.v1.y).apply(StatusEffects.invincible, 600f);
            }
            u.health = u.maxHealth;
            later(2.4f, () -> Log.info("[Survivors-Test] Air attack: health=@/@ shield=@ abilities=@ 2.4s after 6 flares and 6 horizons arrived, enemy bullets shot down by Segment @", (int)player.unit().health, (int)player.unit().maxHealth, (int)player.unit().shield, player.unit().abilities.length, Survivor.shotDown));
        });
        later(9.5f, () -> renderer.setScale(renderer.maxScale()));
        later(10f, () -> {
            shot("04-fight");
            Log.info("[Survivors-Test] Your bullets in flight while not shooting: @ (loadout @)", Groups.bullet.count(b -> b.owner == player.unit()), Survivor.ownedGuns.map(g -> g.turret.name));
            Log.info("[Survivors-Test] After fight: enemies=@ gems=@ kits=@ xp=@ level=@ pending=@ kills=@ boats=@ boatsOnLand=@", state.rules.waveTeam.data().units.size,
                Survivor.gemValue.size, Survivor.kitX.size, (int)run.xp, run.level, run.pendingPicks, state.stats.enemyUnitsDestroyed,
                state.rules.waveTeam.data().units.count(e -> e.type.naval), state.rules.waveTeam.data().units.count(e -> e.type.naval && !e.floorOn().isLiquid));
        });
        later(10.2f, () -> {
            firing = true;
            Events.run(Trigger.uiDrawEnd, () -> {
                Unit u = player.unit();
                if(!firing || u == null) return;
                Unit target = mindustry.entities.Units.closestEnemy(u.team, u.x, u.y, 400f, e -> true);
                if(target == null) return;
                player.shooting = u.isShooting = true;
                u.aim(target.x, target.y);
            });
        });
        later(11f, () -> {
            firing = false;
            player.shooting = false;
            Log.info("[Survivors-Test] Score: total=@ damage=@ kills=@", (int)Score.total(), Score.damage, Score.kills);
            SurvivorsUI.showScore(false);
        });
        later(11.6f, () -> shot("04b-score"));
        later(12f, () -> {
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
            if(player.unit() != null) player.unit().apply(StatusEffects.invincible, 60f * 60f);
            Arena.skipTo(20);
        });
        later(14f, () -> {
            Log.info("[Survivors-Test] After skip: wave=@ enemies=@", state.wave, state.rules.waveTeam.data().units.size);
            String before = Survivor.ownedGuns.map(g -> g.turret.name + ":" + Survivor.gunLevel.get(g, 0)).toString();
            Arena.save();
            continued = true;
            logic.reset();
            SurvivorsRun.resume();
            later(6f, () -> {
                Unit u = player.unit();
                if(u == null){
                    Log.err("[Survivors-Test] Continued run has no player unit");
                    Core.app.exit();
                    return;
                }
                Log.info("[Survivors-Test] Continued: active=@ wave=@ player=@ weapons=@ (before @) mounts=@",
                    SurvivorsRun.active(), state.wave, u.type.name, Survivor.ownedGuns.map(g -> g.turret.name + ":" + Survivor.gunLevel.get(g, 0)), before, u.mounts.length);
                shot("05-continued");
                Log.info("[Survivors-Test] Score after continuing: total=@", (int)Score.total());
                Log.info("[Survivors-Test] Shake in a run: dagger death @ fortress step @ scepter death @ fortress shot @", UnitTypes.dagger.deathShake, UnitTypes.fortress.stepShake, UnitTypes.scepter.deathShake, UnitTypes.fortress.weapons.first().bullet.hitShake);
                later(1f, () -> {
                    logic.reset();
                    later(0.5f, () -> {
                        Log.info("[Survivors-Test] Shake after the run: dagger death @ fortress step @ scepter death @ fortress shot @", UnitTypes.dagger.deathShake, UnitTypes.fortress.stepShake, UnitTypes.scepter.deathShake, UnitTypes.fortress.weapons.first().bullet.hitShake);
                        MindustrySurvivorsMod.ui.showMenu();
                        later(1f, () -> shot("06-menu"));
                        later(2f, () -> {
                            Log.info("[Survivors-Test] RESULT done");
                            Core.app.exit();
                        });
                    });
                });
            });
        });
    }

    final Seq<Unit> dummies = new Seq<>();
    float dummyX, dummyY;
    boolean firing;

    void damage(){
        state.rules.waveTimer = false;
        Events.run(Trigger.update, () -> {
            for(Unit e : state.rules.waveTeam.data().units.copy()) if(!dummies.contains(e)) e.remove();
            Arena.releasing.clear();
            for(int i = 0; i < dummies.size; i++){
                Unit d = dummies.get(i);
                d.set(dummyX + (i % 3 - 1) * 14f, dummyY + (i / 3 - 1) * 14f);
                d.vel.setZero();
            }
        });
        Events.run(Trigger.uiDrawEnd, () -> {
            if(!firing || player.unit() == null) return;
            player.shooting = true;
            player.mouseX = dummyX;
            player.mouseY = dummyY;
            player.unit().aim(dummyX, dummyY);
            player.unit().isShooting = true;
        });
        Seq<Runnable> steps = new Seq<>();
        String only = System.getenv("SURVIVORS_DAMAGE_ONLY");
        for(Block block : Weapons.steps.keys()){
            if(!(block instanceof Turret turret)) continue;
            if(only != null && !only.isEmpty() && !Seq.with(only.split(",")).contains(turret.name)) continue;
            for(int level : new int[]{1, 10}){
                steps.add(() -> measure(turret, level));
            }
        }
        float t = 0f;
        for(Runnable step : steps){
            later(t, step);
            t += 4.5f;
        }
        later(t + 1f, () -> {
            Log.info("[Survivors-Test] RESULT done");
            Core.app.exit();
        });
    }

    void measure(Turret turret, int level){
        Unit me = player.unit();
        me.apply(StatusEffects.invincible, 60f * 60f);
        me.health = me.maxHealth;
        Survivor.ownedGuns.clear();
        Survivor.gunLevel.clear();
        Survivor.liquidPools.clear();
        Groups.all.copy(new Seq<>()).each(e -> {
            if(e instanceof Firec || e instanceof Puddlec) e.remove();
        });
        Survivor.Gun gun = Survivor.guns.find(g -> g.turret == turret);
        for(int i = 0; i < level; i++) Survivor.give(gun);
        for(Unit d : dummies) d.remove();
        dummies.clear();
        dummyX = me.x + Math.min(80f, Weapons.reach(turret) * 0.7f);
        dummyY = me.y;
        for(int i = 0; i < 9; i++){
            Unit d = UnitTypes.dagger.spawn(state.rules.waveTeam, dummyX, dummyY);
            d.maxHealth = d.health = 1e7f;
            d.armor = 0f;
            d.apply(StatusEffects.disarmed, 60f * 60f);
            dummies.add(d);
        }
        firing = true;
        float seconds = 3f;
        later(0.5f, () -> {
            for(Unit d : dummies) d.health = d.maxHealth;
            Score.reset();
        });
        if(level == 10) later(2f, () -> shot("damage-" + turret.name));
        later(0.5f + seconds, () -> {
            float total = 0f;
            for(Unit d : dummies) total += d.maxHealth - d.health;
            float center = dummies.get(4).maxHealth - dummies.get(4).health;
            firing = false;
            Log.info("[Survivors-Test] Damage @ level @: @ per second to the group, @ per second to the middle dummy, @ tiles away", turret.name, level, (int)(total / seconds), (int)(center / seconds), (int)((dummyX - me.x) / 8f));
            Log.info("[Survivors-Test] Score @ level @: credited @ of @ dealt, all sources @", turret.name, level, (int)Score.damage.get(turret.name, 0f), (int)total, Score.damage);
            Score.reset();
        });
    }

    void endless(){
        Arena.skipTo(Arena.lastWave);
        later(1f, () -> {
            for(Unit unit : state.rules.waveTeam.data().units.copy()) unit.remove();
            Arena.releasing.clear();
        });
        later(2.5f, () -> {
            var dialog = Core.scene.getDialog();
            Log.info("[Survivors-Test] After wave @ cleared: dialog=@ gameOver=@", Arena.lastWave, dialog == null ? "none" : dialog.title.getText(), state.gameOver);
            shot("endless-choice");
        });
        later(3.5f, () -> {
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
            Arena.startEndless();
            for(int wave : new int[]{51, 60, 61}){
                Arena.skipTo(wave);
                var team = state.rules.teams.get(state.rules.waveTeam);
                Log.info("[Survivors-Test] Endless wave @: plays wave @, cycle @, enemy health x@ damage x@", wave, Arena.scriptWave(wave), Arena.cycle(wave), team.unitHealthMultiplier, Strings.fixed(team.unitDamageMultiplier, 2));
            }
        });
        later(5f, () -> {
            Log.info("[Survivors-Test] Endless running: wave=@ enemies=@ gameOver=@ endless=@", state.wave, state.rules.waveTeam.data().units.size, state.gameOver, Arena.endless());
            Log.info("[Survivors-Test] RESULT endless done");
            Core.app.exit();
        });
    }

    void bench(){
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        state.rules.waveTimer = false;
        Tile spawn = spawner.getSpawns().first();
        Building core = state.rules.defaultTeam.core();
        Core.camera.position.set((spawn.worldx() + core.x) / 2f, (spawn.worldy() + core.y) / 2f);
        Timer.schedule(() -> Core.app.post(() -> {
            SurvivorsRun r = SurvivorsRun.current;
            if(r == null) return;
            r.pendingPicks = 0;
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        }), 0f, 0.2f);
        if(OS.env("BENCH_NOPHYS") != null){
            UnitTypes.dagger.physics = false;
            UnitTypes.flare.physics = false;
        }
        boolean offscreen = OS.env("BENCH_OFFSCREEN") != null;
        if(offscreen) Timer.schedule(() -> Core.app.post(() -> Core.camera.position.set(-5000f, -5000f)), 0f, 0.1f);
        int[] steps = {1000, 3000, 6000, 10000, 20000};
        float t = 0f;
        for(int step : steps){
            float at = t;
            later(at, () -> {
                int have = Groups.unit.count(u -> u.team == state.rules.waveTeam);
                for(int i = have; i < step; i++){
                    Tmp.v1.rnd(Mathf.random(30f * tilesize));
                    Unit u = (i % 2 == 0 ? UnitTypes.dagger : UnitTypes.flare).spawn(state.rules.waveTeam, spawn.worldx() + Tmp.v1.x, spawn.worldy() + Tmp.v1.y);
                    u.apply(StatusEffects.disarmed, 60f * 600f);
                }
                Log.info("[Survivors-Test] Bench spawned: group=@ team=@", Groups.unit.size(), state.rules.waveTeam.data().units.size);
                later(1f, () -> Log.info("[Survivors-Test] Bench 1s later: group=@ team=@ daggers=@ flares=@", Groups.unit.size(), state.rules.waveTeam.data().units.size,
                    Groups.unit.count(u -> u.type == UnitTypes.dagger), Groups.unit.count(u -> u.type == UnitTypes.flare)));
            });
            later(at + 4f, () -> {
                long start = Time.nanos();
                int frames = Core.graphics.getFrameId() > 0 ? (int)Core.graphics.getFrameId() : 0;
                later(3f, () -> {
                    float seconds = Time.timeSinceNanos(start) / 1e9f;
                    int count = (int)Core.graphics.getFrameId() - frames;
                    Log.info("[Survivors-Test] Bench: units=@ paused=@ fps=@ avgFrameMs=@ bullets=@", Groups.unit.size(), state.isPaused(), (int)(count / seconds), (int)(seconds * 1000f / Math.max(count, 1)), Groups.bullet.size());
                    if(step == steps[steps.length - 1]){
                        shot("bench");
                        later(1f, () -> {
                            Log.info("[Survivors-Test] RESULT bench done");
                            Core.app.exit();
                        });
                    }
                });
            });
            t += 8f;
        }
    }

    void curses(){
        SurvivorsRun run = SurvivorsRun.current;
        if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        run.curses.set(Seq.with(Curses.hungryMechs, Curses.mechVirus, Curses.overshields, Curses.armoredSwarms, Curses.leadership, Curses.shieldTowers, Curses.ambush, Curses.speedFreaks));
        Team enemy = state.rules.waveTeam;
        Unit me = player.unit();
        me.apply(StatusEffects.invincible, 60f * 60f);
        Survivor.give(Survivor.guns.first());
        float sx = me.x + 15f * tilesize, sy = me.y;

        Unit armored = UnitTypes.dagger.spawn(enemy, sx, sy);
        Unit eater = UnitTypes.mace.spawn(enemy, sx + 16f, sy);
        Unit[] prey = {UnitTypes.dagger.spawn(enemy, sx + 24f, sy), UnitTypes.dagger.spawn(enemy, sx + 24f, sy + 8f)};
        Unit leader = UnitTypes.scepter.spawn(enemy, sx - 40f, sy);
        Unit follower = UnitTypes.dagger.spawn(enemy, sx - 40f, sy + 30f);
        Unit shielded = UnitTypes.flare.spawn(enemy, sx + 200f, sy - 200f);
        Unit slowed = UnitTypes.dagger.spawn(enemy, sx + 40f, sy + 40f);

        later(0.5f, () -> {
            Log.info("[Survivors-Test] Armored: dagger armor=@", armored.armor);
            eater.apply(Curses.hungryEffect);
            for(Unit p : prey) p.set(eater.x + 10f, eater.y);
            shielded.shield = 0f;
            slowed.apply(StatusEffects.slow, 600f);
            Unit carrier = UnitTypes.dagger.spawn(enemy, me.x + 8f * tilesize, me.y);
            Curses.carriers.add(carrier.id);
            later(0.3f, carrier::kill);
        });
        later(1f, () -> shielded.shield += 10f);
        later(1.5f, () -> {
            float before = follower.health;
            follower.damage(100f);
            Curses.capHit(follower);
            Log.info("[Survivors-Test] Leadership: guarded=@ hit100 lost=@ leader alive=@ ; Overshields: +10 became @ ; Speedy: slowed speedMult=@",
                Curses.guarded.containsKey(follower.id), before - follower.health, leader.isAdded(), shielded.shield, slowed.speedMultiplier());
            Log.info("[Survivors-Test] Virus: a turret is jammed=@", Time.time < Survivor.jammed[0]);
            Curses.raiseTower();
        });
        later(4f, () -> {
            Log.info("[Survivors-Test] Hungry: meals=@ maceMaxHealth=@ preyLeft=@", Curses.meals.get(eater.id, 0), (int)eater.maxHealth, prey[0].isAdded() || prey[1].isAdded());
            Log.info("[Survivors-Test] Shield Towers: towers=@", Curses.towers.size);
            Curses.towers.each(p -> {
                var f = (mindustry.world.blocks.defense.ForceProjector.ForceBuild)world.build(p);
                Log.info("[Survivors-Test] Tower at @,@ broken=@ health=@ efficiency=@", f.tileX(), f.tileY(), f.broken, (int)f.maxHealth, f.efficiency);
            });
        });
        later(5f, () -> shot("curses"));
        later(6f, () -> {
            Log.info("[Survivors-Test] RESULT curses done");
            Core.app.exit();
        });
    }

    static void clickTab(String name){
        Element tab = Core.scene.find(e -> e instanceof TextButton b && name.equals(b.getText().toString()));
        if(tab != null) tab.fireClick();
    }

    void shot(String name){
        pendingShot = name;
    }

    static void later(float seconds, Runnable run){
        Timer.schedule(() -> Core.app.post(run), seconds);
    }
}

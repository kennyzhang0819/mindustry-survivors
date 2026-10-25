package mindustrysurvivors;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class Bosses{
    public static final int bossEvery = 25, chestPicks = 3;
    static final float groundHealth = 65000f, airHealth = 400000f, groundScale = 1.8f, airScale = 1.6f, bossDamage = 1.5f, minHealthScale = 2.5f, hasteEvery = 30f * 60f;
    static final float slamPeriod = 5f * 60f, groundSummonPeriod = 12f * 60f, spiralPeriod = 4f * 60f, enragedSpiralPeriod = 2.5f * 60f, airSummonPeriod = 8f * 60f;
    static final Color chestGlow = Color.valueOf("ffd37f");

    public static StatusEffect ground, air, enraged;
    static BulletType slamBullet, spiralBullet;
    static final Seq<StatusEffect> banned = new Seq<>();
    static final IntFloatMap attackTimer = new IntFloatMap(), summonTimer = new IntFloatMap(), spin = new IntFloatMap(), alive = new IntFloatMap();
    static final FloatSeq chestX = new FloatSeq(), chestY = new FloatSeq();

    public static void load(){
        ground = kind("boss-ground");
        air = kind("boss-air");
        enraged = new StatusEffect("boss-enraged"){{
            permanent = true;
            speedMultiplier = 1.5f;
            reloadMultiplier = 1.5f;
            color = Color.scarlet;
        }};
        slamBullet = new BasicBulletType(2f, 45f){{
            width = height = 14f;
            lifetime = 160f;
            shrinkY = 0f;
            frontColor = Color.white;
            backColor = Team.crux.color;
            collidesAir = collidesGround = true;
        }};
        spiralBullet = new BasicBulletType(2.6f, 30f){{
            width = height = 10f;
            lifetime = 170f;
            shrinkY = 0f;
            frontColor = Color.white;
            backColor = Pal.remove;
            collidesAir = collidesGround = true;
        }};
    }

    //the game has no per-unit size, so a boss is drawn stretched by the same trick the Grudge curse uses
    static StatusEffect kind(String name){
        return new StatusEffect(name){{
            permanent = true;
            show = false;
            damageMultiplier = bossDamage;
        }

            @Override
            public void update(Unit unit, StatusEntry entry){
                super.update(unit, entry);
                unit.speedMultiplier *= haste(unit);
            }

            @Override
            public void draw(Unit unit, float time){
                Curses.Grow.begin(unit);
            }
        };
    }

    public static boolean is(Unit unit){
        return unit.hasEffect(ground) || unit.hasEffect(air);
    }

    static boolean isAir(Unit unit){
        return unit.hasEffect(air);
    }

    public static float haste(Unit unit){
        return 1f + (int)(alive.get(unit.id, 0f) / hasteEvery);
    }

    static float maxHealth(Unit unit, boolean flying){
        return Math.max(flying ? airHealth : groundHealth, unit.type.health * minHealthScale);
    }

    static void onWave(int wave){
        if(wave > 0 && wave % bossEvery == 0) spawn(wave / bossEvery % 2 == 0);
    }

    static boolean erekir(){
        return Strings.parseInt(state.rules.tags.get(Survivor.enemyTag, "0"), 0) == 2;
    }

    public static void spawn(boolean flying){
        Tile tile = Arena.spawnTile(false);
        if(tile == null) return;
        UnitType type = flying ? (erekir() ? UnitTypes.disrupt : UnitTypes.eclipse) : (erekir() ? UnitTypes.conquer : UnitTypes.reign);
        Unit unit = type.create(state.rules.waveTeam);
        unit.set(tile.worldx(), tile.worldy());
        unit.apply(StatusEffects.boss);
        unit.apply(flying ? air : ground);
        unit.maxHealth = unit.health = maxHealth(unit, flying);
        unit.hitSize = type.hitSize * (flying ? airScale : groundScale);
        Position goal = Chase.goal();
        if(goal != null) unit.rotation = unit.angleTo(goal);
        unit.add();
        attackTimer.put(unit.id, 0f);
        summonTimer.put(unit.id, 0f);
        Effect.shake(8f, 60f, unit.x, unit.y);
        if(!headless) ui.announce("[scarlet]" + (flying ? "Nightfall" : "Juggernaut") + " is coming!", 4f);
        Log.info("[Survivors] Boss @ (@) spawned with @ health", flying ? "Nightfall" : "Juggernaut", type.name, (int)unit.maxHealth);
    }

    static void collectBanned(){
        if(banned.any()) return;
        for(StatusEffect e : content.statusEffects()){
            if(e == ground || e == air || e == enraged || e == StatusEffects.boss) continue;
            if(e.speedMultiplier < 1f || e.reloadMultiplier < 1f || e.disarm) banned.add(e);
        }
    }

    static void update(){
        if(!SurvivorsRun.active()) return;
        collectBanned();
        boolean paused = state.isPaused() || state.gameOver;
        for(Unit unit : state.rules.waveTeam.data().units){
            if(is(unit)) keep(unit, paused);
        }
        if(!paused) collectChests();
    }

    static void keep(Unit unit, boolean paused){
        boolean flying = isAir(unit);
        float scale = flying ? airScale : groundScale, max = maxHealth(unit, flying);
        Curses.Grow.of(unit).scale = scale;
        unit.hitSize = unit.type.hitSize * scale;
        if(unit.maxHealth != max){
            unit.maxHealth = max;
            unit.health = Math.min(unit.health, max);
        }
        for(StatusEffect e : banned){
            if(unit.hasEffect(e)) unit.unapply(e);
        }
        if(paused || unit.dead) return;
        float before = haste(unit);
        alive.increment(unit.id, 0f, Time.delta);
        if(haste(unit) > before && !headless) ui.announce("[scarlet]" + (flying ? "Nightfall" : "Juggernaut") + " speeds up!", 2f);

        boolean angry = unit.health < max * 0.5f;
        if(flying && angry && !unit.hasEffect(enraged)){
            unit.apply(enraged);
            if(!headless) ui.announce("[scarlet]Nightfall is enraged!", 3f);
        }

        float attack = attackTimer.increment(unit.id, 0f, Time.delta), summon = summonTimer.increment(unit.id, 0f, Time.delta);
        if(!flying){
            if(attack >= slamPeriod){
                attackTimer.put(unit.id, 0f);
                ring(unit, slamBullet, 20, Mathf.random(360f));
                Fx.spawnShockwave.at(unit.x, unit.y, unit.hitSize * 2f);
                Effect.shake(5f, 30f, unit.x, unit.y);
            }
            if(summon >= groundSummonPeriod){
                summonTimer.put(unit.id, 0f);
                summon(unit, 4);
            }
        }else{
            if(attack >= (angry ? enragedSpiralPeriod : spiralPeriod)){
                attackTimer.put(unit.id, 0f);
                float turn = spin.increment(unit.id, 0f, 9f);
                ring(unit, spiralBullet, 24, turn);
                ring(unit, spiralBullet, 24, turn + 7.5f);
            }
            if(angry && summon >= airSummonPeriod){
                summonTimer.put(unit.id, 0f);
                summon(unit, 3);
            }
        }
    }

    static void ring(Unit unit, BulletType bullet, int count, float offset){
        float damage = bullet.damage * state.rules.unitDamage(unit.team) * StatusEffects.boss.damageMultiplier;
        for(int i = 0; i < count; i++){
            bullet.create(unit, unit.team, unit.x, unit.y, offset + 360f / count * i, damage, 1f, 1f, null);
        }
    }

    static void summon(Unit boss, int count){
        Arena.Family family = Arena.families.find(f -> Structs.contains(f.tiers, boss.type));
        UnitType type = family == null ? boss.type : family.tiers[1];
        for(int i = 0; i < count; i++){
            Tmp.v1.rnd(boss.hitSize * 0.8f + tilesize * 2f).add(boss.x, boss.y);
            if(!type.flying && !type.naval && EntityCollisions.solid(World.toTile(Tmp.v1.x), World.toTile(Tmp.v1.y))) continue;
            type.spawn(boss.team, Tmp.v1.x, Tmp.v1.y);
            Fx.spawn.at(Tmp.v1.x, Tmp.v1.y);
        }
    }

    static void died(Unit unit){
        attackTimer.remove(unit.id, 0f);
        summonTimer.remove(unit.id, 0f);
        spin.remove(unit.id, 0f);
        alive.remove(unit.id, 0f);
        chestX.add(unit.x);
        chestY.add(unit.y);
        Fx.reactorExplosion.at(unit.x, unit.y);
        Effect.shake(10f, 60f, unit.x, unit.y);
        if(!headless) ui.announce("[accent]" + (isAir(unit) ? "Nightfall" : "Juggernaut") + " is down! Grab the chest for " + chestPicks + " level-ups.", 4f);
        Log.info("[Survivors] Boss @ died", isAir(unit) ? "Nightfall" : "Juggernaut");
    }

    static void collectChests(){
        Unit me = player.unit();
        SurvivorsRun run = SurvivorsRun.current;
        if(me == null || me.dead || run == null) return;
        for(int i = chestX.size - 1; i >= 0; i--){
            if(!me.within(chestX.get(i), chestY.get(i), me.hitSize + 10f)) continue;
            run.pendingPicks += chestPicks;
            Fx.spawnShockwave.at(chestX.get(i), chestY.get(i), 80f);
            chestX.removeIndex(i);
            chestY.removeIndex(i);
        }
    }

    static void draw(){
        if(!Arena.active() || chestX.isEmpty()) return;
        Draw.z(Layer.groundUnit - 1f);
        float pulse = 0.6f + Mathf.absin(Time.time, 10f, 0.4f);
        for(int i = 0; i < chestX.size; i++){
            float x = chestX.get(i), y = chestY.get(i) + Mathf.absin(Time.time + i * 19f, 9f, 2f);
            Fill.light(x, y, 24, 40f * pulse, Tmp.c1.set(chestGlow).a(0.8f * pulse), Tmp.c2.set(chestGlow).a(0f));
            Draw.color();
            Draw.rect(Blocks.container.fullIcon, x, y, 26f, 26f);
        }
        Draw.reset();
    }

    static void register(){
        Events.on(WorldLoadBeginEvent.class, e -> {
            chestX.clear();
            chestY.clear();
            attackTimer.clear();
            summonTimer.clear();
            spin.clear();
            alive.clear();
        });
        Events.on(UnitDestroyEvent.class, e -> {
            if(Arena.active() && is(e.unit)) died(e.unit);
        });
        Events.run(Trigger.update, Bosses::update);
        Events.run(Trigger.draw, Bosses::draw);
    }
}

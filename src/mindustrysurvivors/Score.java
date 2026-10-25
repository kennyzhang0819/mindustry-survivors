package mindustrysurvivors;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.type.unit.*;

import java.io.*;

import static mindustry.Vars.*;

public class Score{
    public static final String walker = "walker";
    public static final ObjectFloatMap<String> damage = new ObjectFloatMap<>();
    public static final ObjectIntMap<String> kills = new ObjectIntMap<>();
    static final ObjectMap<BulletType, String> source = new ObjectMap<>();
    static final IntFloatMap lastHp = new IntFloatMap();
    static final ObjectSet<BulletType> shared = new ObjectSet<>();
    static BulletType arcLightning;
    static final ObjectMap<String, BulletType> lightnings = new ObjectMap<>();
    static @Nullable UnitType lastHitBy;
    static String lastHitHow = "";
    public static @Nullable UnitType killer;
    public static String killerHow = "";
    public static boolean lost;
    static boolean pending;

    static void claim(@Nullable BulletType type, String key){
        claim(type, key, 0);
    }

    static void claim(@Nullable BulletType type, String key, int depth){
        if(type == null || depth > 3 || shared.contains(type)) return;
        source.put(type, key);
        claim(type.fragBullet, key, depth + 1);
        claim(type.intervalBullet, key, depth + 1);
        claim(type.lightningType, key, depth + 1);
    }

    static BulletType arcLightning(){
        if(arcLightning == null) arcLightning = lightning(Blocks.arc.name);
        return arcLightning;
    }

    static BulletType lightning(String key){
        BulletType type = lightnings.get(key);
        if(type == null){
            type = Bullets.damageLightning.copy();
            source.put(type, key);
            lightnings.put(key, type);
        }
        return type;
    }

    static @Nullable String key(@Nullable Bullet b){
        if(b == null) return null;
        String key = source.get(b.type);
        if(key != null) return key;
        if(b.owner instanceof Unit u && u.type instanceof MissileUnitType && Survivor.missiles.contains(u.type)) return Blocks.scathe.name;
        return null;
    }

    static float hp(Unit u){
        return Math.max(u.health, 0f) + u.shield;
    }

    static void add(String key, float amount, boolean killed){
        if(amount > 0f) damage.increment(key, 0f, amount);
        if(killed) kills.increment(key);
    }

    public static void direct(String key, Unit u, float amount){
        boolean wasDead = u.dead;
        float before = hp(u);
        u.damage(amount);
        settle(key, u, before, wasDead);
    }

    public static void directPierce(String key, Unit u, float amount){
        boolean wasDead = u.dead;
        float before = hp(u);
        u.damageContinuousPierce(amount);
        settle(key, u, before, wasDead);
    }

    static void settle(String key, Unit u, float before, boolean wasDead){
        float now = hp(u);
        lastHp.put(u.id, now);
        add(key, before - now, !wasDead && u.dead);
    }

    public static float total(){
        float sum = 0f;
        for(var e : damage) sum += e.value;
        return sum;
    }

    public static void blameNear(UnitType type, String how, float x, float y, float radius){
        Unit me = player.unit();
        if(me != null && me.within(x, y, radius + me.hitSize / 2f)){
            lastHitBy = type;
            lastHitHow = how;
        }
    }

    static void showPending(){
        if(!pending) return;
        pending = false;
        SurvivorsUI.showScore(true);
    }

    static void reset(){
        lastHitBy = killer = null;
        lastHitHow = killerHow = "";
        lost = false;
        damage.clear();
        kills.clear();
        lastHp.clear();
    }

    static void write(DataOutput out) throws IOException{
        Seq<String> keys = new Seq<>();
        for(var e : damage) keys.add(e.key);
        for(var e : kills) if(!keys.contains(e.key)) keys.add(e.key);
        out.writeInt(keys.size);
        for(String key : keys){
            out.writeUTF(key);
            out.writeFloat(damage.get(key, 0f));
            out.writeInt(kills.get(key, 0));
        }
    }

    static void read(DataInput in) throws IOException{
        reset();
        int count = in.readInt();
        for(int i = 0; i < count; i++){
            String key = in.readUTF();
            damage.put(key, in.readFloat());
            kills.put(key, in.readInt());
        }
    }

    static void register(){
        Events.on(ClientLoadEvent.class, e -> {
            shared.addAll(content.bullets());
            ui.restart.shown(() -> Core.app.post(Score::showPending));
        });
        Events.run(Trigger.update, () -> {
            if(!Arena.active()) return;
            lastHp.clear();
            for(Unit u : state.rules.waveTeam.data().units) lastHp.put(u.id, hp(u));
        });
        Events.on(UnitDamageEvent.class, e -> {
            if(Arena.active() && e.unit == player.unit() && e.bullet != null && e.bullet.team == state.rules.waveTeam && e.bullet.owner instanceof Unit u){
                lastHitBy = u.type;
                lastHitHow = "";
            }
            if(!Arena.active() || e.unit.team != state.rules.waveTeam || e.bullet == null) return;
            float now = hp(e.unit), before = lastHp.get(e.unit.id, now);
            lastHp.put(e.unit.id, now);
            String key = key(e.bullet);
            if(key == null) return;
            BulletType t = e.bullet.type;
            float mult = e.bullet.damageMultiplier();
            float cap = (Math.max(e.bullet.damage, t.damage * mult) + (t.splashDamage + Math.max(t.lightningDamage, 0f)) * mult) * 1.2f + 1f;
            add(key, Math.min(before - now, cap), false);
        });
        Events.on(UnitBulletDestroyEvent.class, e -> {
            if(!Arena.active() || e.unit.team != state.rules.waveTeam) return;
            String key = key(e.bullet);
            if(key != null) kills.increment(key);
        });
        Events.on(GameOverEvent.class, e -> {
            if(!Arena.active()) return;
            lost = e.winner == state.rules.waveTeam;
            killer = lost ? lastHitBy : null;
            killerHow = lost ? lastHitHow : "";
            if(MindustrySurvivorsMod.ui == null) return;
            pending = true;
            Time.run(60f * 4f, Score::showPending);
        });
    }
}

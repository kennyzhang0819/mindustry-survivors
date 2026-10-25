package mindustrysurvivors;

import arc.*;
import arc.func.*;
import arc.math.*;
import arc.scene.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.io.*;
import mindustry.ui.fragments.*;

import java.io.*;

import static mindustry.Vars.*;

public class SurvivorsRun{
    public static final String saveKey = "mindustry-survivors-run";

    public static @Nullable SurvivorsRun current;

    public long id;
    public Difficulty difficulty = Difficulty.eradication;
    //levels past Eradication: each one has a curse budget and a smallest number of curses
    public int heat;
    public static final int maxHeat = 3;
    static final int[] heatBudget = {0, 4, 8, 13}, heatMinCurses = {0, 2, 3, 4};
    public final Seq<Curses.Curse> curses = new Seq<>();
    public float xp;
    public int level, pendingPicks;
    boolean stateLoaded;
    public long loadedAt;

    public static int heatBudget(int heat){
        return heatBudget[heat];
    }

    public static int heatMinCurses(int heat){
        return heatMinCurses[heat];
    }

    public static boolean exists(){
        return Core.settings.has(saveKey) && Arena.saved();
    }

    public static void startNew(){
        startNew(Difficulty.eradication);
    }

    public static void startNew(Difficulty difficulty){
        startNew(difficulty, 0);
    }

    public static void startNew(Difficulty difficulty, int heat){
        Arena.deleteSave();
        SurvivorsRun run = new SurvivorsRun();
        run.id = Math.abs(new Rand().nextLong());
        run.difficulty = heat > 0 ? Difficulty.eradication : difficulty;
        run.heat = Mathf.clamp(heat, 0, maxHeat);
        run.save();
        current = run;
        Arena.start(run.id);
    }

    public static void resume(){
        current = load();
        if(current == null) return;
        Arena.resume();
    }

    public static void finish(boolean won){
        Core.settings.remove(saveKey);
        Log.info("[Survivors] Run @ on wave @", won ? "won" : "lost", state.wave);
    }

    public static void abandon(){
        Arena.deleteSave();
        Core.settings.remove(saveKey);
        current = null;
    }

    public static boolean active(){
        return current != null && Arena.active();
    }

    public static float xpFor(int level){
        float mid = Math.max(level - 10, 0), late = Math.max(level - 50, 0);
        return 5f + 10f * level + 0.25f * mid * mid + 1.5f * late * late;
    }

    public float xpProgress(){
        return Math.min(xp / xpFor(level), 1f);
    }

    void reset(){
        for(Curses.Curse m : curses) Curses.lifted(m);
        curses.clear();
        xp = 0f;
        level = pendingPicks = 0;
        stateLoaded = false;
    }

    void write(DataOutput out) throws IOException{
        out.writeInt(5);
        out.writeFloat(xp);
        out.writeInt(level);
        out.writeInt(pendingPicks);
        out.writeInt(curses.size);
        for(Curses.Curse m : curses) out.writeUTF(m.id);
        Curses.write(out);
    }

    void read(DataInput in) throws IOException{
        reset();
        int version = in.readInt();
        if(version < 5){
            readOld(in, version);
        }else{
            xp = in.readFloat();
            level = in.readInt();
            pendingPicks = in.readInt();
            readCurses(in);
            Curses.read(in);
        }
        stateLoaded = true;
    }

    //runs saved before relics and modifiers were removed still carry their fields, which are read and dropped
    void readOld(DataInput in, int version) throws IOException{
        xp = in.readFloat();
        level = in.readInt();
        in.readInt();
        in.readBoolean();
        pendingPicks = in.readInt();
        in.readInt();
        int count = in.readInt();
        for(int i = 0; i < count; i++){
            in.readUTF();
            in.readInt();
        }
        readCurses(in);
        count = in.readInt();
        for(int i = 0; i < count; i++) in.readUTF();
        for(int i = 0; i < 3; i++) in.readBoolean();
        if(version >= 2) in.readBoolean();
        if(version >= 3) Curses.read(in);
        if(version >= 4){
            count = in.readInt();
            for(int i = 0; i < count; i++){
                in.readUTF();
                in.readInt();
            }
        }
    }

    void readCurses(DataInput in) throws IOException{
        int count = in.readInt();
        for(int i = 0; i < count; i++){
            Curses.Curse m = Curses.get(in.readUTF());
            if(m != null) curses.add(m);
        }
    }

    void onLoaded(){
        loadedAt = Time.millis();
        if(!stateLoaded){
            curses.set(Curses.roll(heatBudget[heat], heatMinCurses[heat], new Rand(id * 7121L)));
            if(curses.any()) Log.info("[Survivors] Curses: @", curses.map(c -> c.name));
        }
        stateLoaded = true;
    }

    static void hideHints(){
        try{
            var field = HintsFragment.class.getDeclaredField("group");
            field.setAccessible(true);
            Element group = (Element)field.get(ui.hints);
            Boolp shown = group.visibility;
            group.visibility = () -> !active() && (shown == null || shown.get());
        }catch(Throwable e){
            Log.err("[Survivors] Could not hide hints", e);
        }
    }

    public void adminToggle(Curses.Curse curse){
        if(curses.remove(curse)){
            Curses.lifted(curse);
            Log.info("[Survivors] Admin lifted '@'", curse.name);
        }else{
            curses.add(curse);
            Log.info("[Survivors] Admin added '@'", curse.name);
        }
        save();
    }

    public void save(){
        StringBuilder out = new StringBuilder();
        out.append("id=").append(id).append('\n');
        out.append("difficulty=").append(difficulty.name()).append('\n');
        out.append("heat=").append(heat).append('\n');
        Core.settings.put(saveKey, out.toString());
    }

    public static @Nullable SurvivorsRun load(){
        String text = Core.settings.getString(saveKey, null);
        if(text == null) return null;
        try{
            SurvivorsRun run = new SurvivorsRun();
            for(String line : text.split("\n")){
                int eq = line.indexOf('=');
                if(eq < 0) continue;
                String k = line.substring(0, eq), v = line.substring(eq + 1);
                switch(k){
                    case "id" -> run.id = Long.parseLong(v);
                    case "difficulty" -> run.difficulty = Structs.find(Difficulty.all, d -> d.name().equals(v));
                    case "heat" -> run.heat = Mathf.clamp(Integer.parseInt(v), 0, maxHeat);
                }
            }
            return run;
        }catch(Throwable e){
            Log.err("[Survivors] Could not read the saved run", e);
            return null;
        }
    }

    static void registerEvents(){
        SaveVersion.addCustomChunk("mindustry-survivors-sector", new SaveFileReader.CustomChunk(){
            @Override
            public boolean shouldWrite(){
                return current != null && Arena.active();
            }

            @Override
            public void write(DataOutput out) throws IOException{
                current.write(out);
            }

            @Override
            public void read(DataInput in) throws IOException{
                if(current != null) current.read(in);
            }
        });

        //custom save chunks are read after this event, so a loaded run's state is not wiped
        Events.on(WorldLoadBeginEvent.class, e -> {
            if(current != null) current.reset();
        });

        Events.on(ClientLoadEvent.class, e -> hideHints());

        Events.on(WorldLoadEvent.class, e -> Core.app.post(() -> {
            if(active()) current.onLoaded();
        }));
    }
}

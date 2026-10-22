package roguedustry;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.GameState.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.maps.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

public class RogueUI{
    public @Nullable BaseDialog levelDialog;
    public Seq<Packs.Pack> offered = new Seq<>();

    public void init(){
        ui.menufrag.addButton("Roguedustry", Icon.play, this::showMapPicker);
        buildHud();

        Events.run(Trigger.update, this::update);
    }

    public static Seq<Map> playableMaps(){
        return maps.all().select(m -> m.spawns > 0 && !m.tags.get("rules", "").contains("\"attackMode\":true"));
    }

    void showMapPicker(){
        BaseDialog dialog = new BaseDialog("Roguedustry");
        Seq<Map> list = playableMaps();

        dialog.cont.add("Survive " + Waves.schedule().size + " waves. Kills give XP. Each level up: pick 1 of 3 packs.").color(Pal.accent).row();
        dialog.cont.button("Random map", Icon.refresh, () -> {
            dialog.hide();
            RogueRun.start(list.random());
        }).size(260f, 60f).pad(8f).row();

        dialog.cont.pane(p -> {
            int i = 0;
            for(Map map : list){
                p.button(map.name(), () -> {
                    dialog.hide();
                    RogueRun.start(map);
                }).size(220f, 50f).pad(4f);
                if(++i % 4 == 0) p.row();
            }
        }).growX();

        dialog.addCloseButton();
        dialog.show();
    }

    void buildHud(){
        Table panel = new Table(Styles.black6);
        panel.name = "rogue-hud";
        panel.visible(() -> RogueRun.active() && ui.hudfrag.shown());
        panel.left().margin(6f);
        panel.defaults().pad(2f).left();

        panel.table(top -> {
            top.left();
            top.label(() -> RogueRun.active() ? "[accent]Level " + run().level : "").padRight(12f);
            top.check("Auto waves", v -> run().autoStart = v).update(cb -> {
                if(RogueRun.active()) cb.setChecked(run().autoStart);
            });
        }).growX().row();

        panel.add(new Bar(
            () -> RogueRun.active() ? "XP " + run().xp + " / " + run().xpToNext() : "",
            () -> Pal.accent,
            () -> RogueRun.active() ? run().xp / (float)run().xpToNext() : 0f
        )).height(18f).growX().row();

        Table inventory = new Table();
        int[] version = {-1};
        inventory.update(() -> {
            if(!RogueRun.active() || version[0] == run().inventoryVersion) return;
            version[0] = run().inventoryVersion;
            rebuildInventory(inventory);
        });
        panel.add(inventory).growX();

        Table waves = ui.hudGroup.find("waves");
        if(waves != null){
            waves.row();
            waves.add(panel).growX().left().padTop(4f);
        }else{
            ui.hudGroup.fill(t -> t.top().left().marginTop(130f).add(panel).width(330f));
        }
    }

    void rebuildInventory(Table table){
        table.clear();
        int i = 0;
        for(var e : run().charges){
            if(e.value <= 0) continue;
            table.image(e.key.uiIcon).size(28f).padLeft(6f).tooltip(e.key.localizedName);
            table.add("x" + e.value).padLeft(2f);
            if(++i % 5 == 0) table.row();
        }
        if(i == 0) table.add("[lightgray]No charges - level up for more").pad(4f);
    }

    void update(){
        if(!RogueRun.active() || state.gameOver) return;

        if(run().pendingLevelUps > 0 && (levelDialog == null || !levelDialog.isShown())){
            showLevelUp();
        }
    }

    void showLevelUp(){
        RogueRun run = run();
        offered = Packs.roll(run.level - run.pendingLevelUps + 1, run.rand);

        levelDialog = new BaseDialog("Level up!");
        levelDialog.cont.add("Choose one pack").color(Pal.accent).padBottom(12f).row();
        levelDialog.cont.table(row -> {
            for(int i = 0; i < offered.size; i++){
                Packs.Pack pack = offered.get(i);
                int index = i;
                row.button(card -> {
                    card.top().margin(12f);
                    card.add(pack.name).color(tierColor(pack.tier)).padBottom(4f).row();
                    card.add("Tier " + (pack.tier + 1)).color(Color.lightGray).padBottom(10f).row();
                    for(Packs.Entry e : pack.entries){
                        card.table(line -> {
                            line.image(e.block.uiIcon).size(40f);
                            line.add("x" + e.amount + "  " + e.block.localizedName).padLeft(8f).left();
                        }).left().pad(3f).row();
                    }
                }, Styles.defaultb, () -> choose(index)).size(260f, 300f).pad(8f);
            }
        });

        levelDialog.shown(() -> state.set(State.paused));
        levelDialog.hidden(() -> {
            if(state.isGame() && !state.gameOver) state.set(State.playing);
        });
        levelDialog.show();
    }

    public void choose(int index){
        if(levelDialog == null || index >= offered.size) return;
        Packs.Pack pack = offered.get(index);
        //a second click during the close animation must not pick again
        offered = new Seq<>();
        run().choosePack(pack);
        levelDialog.hide();
    }

    static Color tierColor(int tier){
        return switch(tier){
            case 0 -> Color.white;
            case 1 -> Pal.accent;
            case 2 -> Pal.lancerLaser;
            default -> Pal.remove;
        };
    }

    static RogueRun run(){
        return RogueRun.current;
    }
}

package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.util.IntervalUtil;

//script is used to add scripts for certain battles
public class armaa_EinhanderHaxPlugin extends BaseEveryFrameCombatPlugin {

    protected CombatEngineAPI engine;
    private final IntervalUtil interval = new IntervalUtil(.025f, .05f);
    private boolean needsComboPlugin = false;
    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return;
        }

        if (!engine.isPaused()) {
            if (engine.getContext().getOtherFleet() != null && engine.getContext().getOtherFleet().getMemoryWithoutUpdate().contains("$inAtmoBattle") && engine.getCustomData().get("armaa_atmoPlugin") == null) {
                engine.addPlugin(new armaa_atmosphericBattlePlugin());
                engine.getCustomData().put("armaa_atmoPlugin", "-");
            } else if (engine.getContext().getOtherFleet() != null && engine.getContext().getOtherFleet().getMemoryWithoutUpdate().contains("$inAtmoBossBattle") && engine.getCustomData().get("armaa_atmoPlugin") == null) {
                engine.addPlugin(new armaa_atmosphericBossBattlePlugin());
                engine.getCustomData().put("armaa_atmoPlugin", "-");
            } else if (engine.getContext().getOtherFleet() != null && engine.getContext().getOtherFleet().getMemoryWithoutUpdate().contains("$inCityBattle") && engine.getCustomData().get("armaa_atmoPlugin") == null) {
                engine.addPlugin(new armaa_cityBattlePlugin());
                engine.getCustomData().put("armaa_atmoPlugin", "-");
            } else if (engine.getContext().getOtherFleet() != null && engine.getContext().getOtherFleet().getMemoryWithoutUpdate().contains("$inShaftBattle") && engine.getCustomData().get("armaa_atmoPlugin") == null) {
                engine.addPlugin(new armaa_shaftBattlePlugin());
                engine.getCustomData().put("armaa_atmoPlugin", "-");
            } else if (engine.getContext().getOtherFleet() != null && engine.getContext().getOtherFleet().getMemoryWithoutUpdate().contains("$inGravionBattle") && engine.getCustomData().get("armaa_atmoPlugin") == null) {
                engine.addPlugin(new armaa_gasGiantBattlePlugin());
                engine.getCustomData().put("armaa_atmoPlugin", "-");
            }
            
            
            
            
            
            interval.advance(amount);
            if (interval.intervalElapsed()) {
                for (MissileAPI missile : engine.getMissiles()) {
                    if (missile.getWeapon() != null) {
                        if (!missile.getWeapon().getId().equals("armaa_sprigganTorso")) {
                            continue;
                        }
                        if (missile.isFizzling() || missile.isFading()) {
                            if (MagicRender.screenCheck(0.25f, missile.getLocation())) {
                                engine.addSmoothParticle(missile.getLocation(), new Vector2f(), 50, 0.5f, 0.15f, Color.blue);
                                engine.addHitParticle(missile.getLocation(), new Vector2f(), 50, 1f, 0.25f, new Color(250, 192, 250, 255));
                            }
                            engine.removeEntity(missile);
                            return;
                        }
                    }
                }
                if(!needsComboPlugin)
                for (ShipAPI ship : engine.getShips()) 
                {
                    if (ship.isFighter()) {
                        continue;
                    }
                    if (ship.getVariant().getHullMods().contains("armaa_comboUnit")) 
                    {
                        needsComboPlugin = true;
                        Global.getLogger(this.getClass()).info("Added Combo unit control plugin");
                        engine.addPlugin(new armaa_comboUnitControlPlugin());
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }
}

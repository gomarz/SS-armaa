package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class armaa_bellatorAegis extends BaseHullMod {

    public static final String MOD_ID = "armaa_bellatorAegis";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) {
            return;
        }
        if (engine.getCustomData().containsKey("armaa_aegis_listener")) {
            return;
        }
        engine.getCustomData().put("armaa_aegis_listener", Boolean.TRUE);
        engine.addPlugin(new armaa_aegisPlugin());
        engine.getListenerManager().addListener(new armaa_aegisDamageListener(engine));
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "" + (int) armaa_aegisConfig.RADIUS;
        }
        if (index == 1) {
            return (int) (armaa_aegisConfig.MAX_REDUCTION * 100f) + "%";
        }
        if (index == 2) {
            return "" + armaa_aegisConfig.FLUX_PER_DAMAGE;
        }
        if (index == 3) {
            return "70%"; // zero-flux threshold at full field strength
        }
        if (index == 4) {
            return (int) (armaa_aegisConfig.FLUX_CUTOFF * 100f) + "%";
        }
        return null;
    }
}

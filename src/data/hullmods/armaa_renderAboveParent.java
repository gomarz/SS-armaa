package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * Draws a module above its parent's weapons but below fighters. A parent with modules draws its
 * weapons in STATION_WEAPONS_LAYER, and CONTRAILS_LAYER is the only layer between that and
 * FIGHTERS_LAYER. The combat renderer files each entity under its layers once, when it is added,
 * so the layer has to be set here, before the module is added to combat; a later setLayer does not
 * move it.
 */
public class armaa_renderAboveParent extends BaseHullMod {

    // Anything that calls setLayer on these modules later must use this too: removal looks the
    // entity up by its current layers, so a mismatch leaves it in the old layer's list
    public static final CombatEngineLayers LAYER = CombatEngineLayers.CONTRAILS_LAYER;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.setLayer(LAYER);
    }
}

//
// Decompiled by Procyon v0.5.36
//
package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.combat.WeaponUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_reloadAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private WeaponAPI weapon;
    private IntervalUtil tracker;

    public armaa_reloadAI() {
        this.tracker = new IntervalUtil(0.5f, 1.0f);
    }

    @Override
    public void init(final ShipAPI ship, final ShipSystemAPI system, final ShipwideAIFlags flags, final CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = ship.getSystem();

    }

    @Override
    public void advance(final float amount, final Vector2f missileDangerDir, final Vector2f collisionDangerDir, final ShipAPI target) {
        this.tracker.advance(amount);
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        for (WeaponAPI wep : this.ship.getAllWeapons()) {
            if (wep.getId().equals("armaa_musha_rightPauldron_frig") && !WeaponUtils.getEnemiesInArc(wep).isEmpty()) {
                if (tracker.intervalElapsed() && !system.isActive()) {
                    ship.useSystem();
                    break;
                }
            }
        }
    }
}

package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class armaa_meleeAttackAI implements ShipSystemAIScript {
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private IntervalUtil tracker = new IntervalUtil(0.5F, 1.0F);
    // AI scalars
    private float aiTimer = 0f;
    private static Map approachComfort = new HashMap();

    static {

        approachComfort.put(ShipAPI.HullSize.FIGHTER, 0f);
        approachComfort.put(ShipAPI.HullSize.FRIGATE, 1.5f);
        approachComfort.put(ShipAPI.HullSize.DESTROYER, 0.75f);
        approachComfort.put(ShipAPI.HullSize.CRUISER, 0.5f);
        approachComfort.put(ShipAPI.HullSize.CAPITAL_SHIP, 0.25f);
    }

    private static final float FLANKING_MULT = 3f; // AI will be this many times more likely to use system when flanking
    private static final float FLANKING_RANGE = 150f; // AI will consider this many degrees of a ship's rear exposed to be a flank
    private float extraMad = 0f;
    public static final float FLUX_TOLERANCE = 1f; // How much it hesitates if it's high flux
	
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = system;
    }
	
    private float normalizeAngle(float f) {
        if (f < 0f)
            return f + 360f;
        if (f > 360f)
            return f - 360f;
        return f;
    }
	
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (!engine.isPaused()) {
            if (!system.isActive()) {
                tracker.advance(amount);
                if (tracker.intervalElapsed()) {
                    if (target == null) {
                        return;
                    }
					
					if (ship.isRetreating()) return;

					if(ship.getFluxLevel() > 0.7f)
						return;

					if(ship.getHullLevel() < 0.3f)
						return;
					// AI trigger procs much more often when flanking enemy
					float flanking = 1f;
					float angle = normalizeAngle(target.getFacing()) - normalizeAngle(ship.getFacing());
					angle = normalizeAngle(angle);
					if (angle <= FLANKING_RANGE * 0.5f || angle >= 360f - (FLANKING_RANGE * 0.5f) || target.getFluxTracker().isOverloaded() || target.getHullSize() == HullSize.FRIGATE) {
						flanking = FLANKING_MULT;
					}					
					// Trigger is random chance based on size of enemy, flux level, and flanking status
					if (Math.random() > ((float) approachComfort.get(target.getHullSize()) + extraMad) * flanking * (1 + (FLUX_TOLERANCE * (target.getFluxLevel() - ship.getFluxLevel()))))
						return;
					float distance = MathUtils.getDistance(ship,target);
					// Will never proc outside of certain range - more strict vs bigger ships
					if (distance < 50f || distance > 700f) return;
					if (distance < 300f && (target.getHullSize() == HullSize.CRUISER || target.getHullSize() == HullSize.CAPITAL_SHIP)) {
						extraMad += 0.2f;
						return;
        }					
                    if (ship.getShipTarget() == null) {
                        ship.setShipTarget(target);
                        return;
                    }

                    if (!target.isAlive()) {
                        return;
                    }

                    if (target.isFighter() && !ship.isFighter() || target.isDrone()) {
                        return;
                    }

                    if (!MathUtils.isWithinRange(ship, target, 1000.0F) && !AIUtils.canUseSystemThisFrame(ship)) {
                        return;
                    }

                    if (ship.getFluxTracker().getFluxLevel() >= 0.5F) {
                        return;
                    }

                    if (flags.hasFlag(AIFlags.MANEUVER_TARGET) || flags.hasFlag(AIFlags.PURSUING) || flags.hasFlag(AIFlags.HARASS_MOVE_IN)) {
                        ship.useSystem();
                    }
                }
            }

        }
		
        WeaponAPI drill = null;
            for (WeaponAPI shipWeapon : ship.getAllWeapons()) {
				if (shipWeapon.getId().contentEquals("armaa_aleste_blade_LeftArm")) {
                    drill = shipWeapon;
                }
            }
            if (drill != null) {
                if (system.isActive()) {
                    WeaponGroupAPI drillGroup = null;
                    if (!drill.isDisabled() && !drill.isPermanentlyDisabled() && !drill.isFiring()) {
                        drillGroup = ship.getWeaponGroupFor(drill);
                    }

                    if (drillGroup != null) {
                        int groupNum = 0;
                        boolean foundGroup = false;
                        for (WeaponGroupAPI group : ship.getWeaponGroupsCopy()) {
                            if (group == drillGroup) {
                                foundGroup = true;
                                break;
                            } else {
                                groupNum++;
                            }
                        }
                        if (foundGroup) {
                            if (!drillGroup.isAutofiring()) {
                                ship.giveCommand(ShipCommand.TOGGLE_AUTOFIRE, null, groupNum);
                            }
                        }
                    }
                }
			}
    }
}

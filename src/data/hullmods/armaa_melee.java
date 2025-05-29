package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.util.vector.Vector2f;

public class armaa_melee extends BaseHullMod 
{


    public static final float RESIST_MULT = 0.2f;
    public static final float FLUX_TOLERANCE = 1f; // How much it hesitates if it's high flux
    public static final float OFFCENTER_DISTANCE_RATIO = 0.025f;

    private float originalAngVel = 0f;


    // AI scalars
    private float aiTimer = 0f;
    private static Map approachComfort = new HashMap();

    static {

        approachComfort.put(ShipAPI.HullSize.FIGHTER, 0f);
        approachComfort.put(ShipAPI.HullSize.FRIGATE, 1f);
        approachComfort.put(ShipAPI.HullSize.DESTROYER, 0.75f);
        approachComfort.put(ShipAPI.HullSize.CRUISER, 0.5f);
        approachComfort.put(ShipAPI.HullSize.CAPITAL_SHIP, 0.25f);
    }

    private static final float FLANKING_MULT = 3f; // AI will be this many times more likely to use system when flanking
    private static final float FLANKING_RANGE = 150f; // AI will consider this many degrees of a ship's rear exposed to be a flank
    private float extraMad = 0f;

    private boolean aiOverrideInit = false;


    public String getDescriptionParam(int index, HullSize hullSize) {
        return null;
    }


    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {

    }


    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return true;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {

        // Init stuff
        super.advanceInCombat(ship, amount);

	WeaponAPI meleeWep = null;
        WeaponAPI leftGuide = null;
        WeaponAPI rightGuide = null;
        for (WeaponAPI w : ship.getAllWeapons()) {
            if (w.getSlot().getId().equals("B_TORSO"))
                leftGuide = w;
            if (w.getSlot().getId().equals("armaa_aleste_blade_RightArm"))
                rightGuide = w;
            if (w.getSpec().getWeaponId().equals("armaa_aleste_blade_LeftArm"))
                meleeWep = w;
        }
        // framerate check
        float frameRatio = amount / 0.016666668f;

        aiTimer += amount;
        if (aiTimer >= 0.5f) {
            aiTimer = 0f;

            // AI process for closing distance w/system, only called if AI pilot is active & at least 1 melee weapon is available
            if (Global.getCombatEngine().getPlayerShip() != ship || !Global.getCombatEngine().isUIAutopilotOn()) {
                if(meleeWep != null || rightGuide != null)
                    aiHandler(ship);
                }
            }
        }


    private float normalizeAngle(float f) {
        if (f < 0f)
            return f + 360f;
        if (f > 360f)
            return f - 360f;
        return f;
    }

    private void activateSystem(ShipAPI ship) {
        try {
            ship.getSystem().setAmmo(1);
            ship.useSystem();
        } catch (Exception e) {
            //Global.getCombatEngine().getCombatUI().addMessage(0, "Exception: " + e);
        }
    }

    // The system uses default plasma burn AI, this appends special melee rules.
    private void aiHandler(ShipAPI ship) 
	{
        // If system isn't ready, abort.
        ShipSystemAPI system = ship.getSystem();
        if (system.getCooldownRemaining() > 0) return;
        if (system.isActive()) return;
      //  if (!system.getId().equals("microburn")) return;
        if (ship.isRetreating()) return;

		if(ship.getFluxLevel() > 0.7f)
			return;

		if(ship.getHullLevel() < 0.3f)
			return;
        // Set viewfinder
        WeaponAPI viewfinder = null;
        WeaponAPI viewfinderAlly = null;
        for (WeaponAPI w : ship.getAllWeapons()) {
            if (w.getSlot().getId().equals("E_HEAD"))
                viewfinder = w;
        }
        if (viewfinder == null) return;

        // Get closest enemy target
        ShipAPI target = null;
        float distance = 10000f;
        int enemy = 0;
        if (ship.getOwner() == 0)
            enemy = 1;
        for (ShipAPI f : Global.getCombatEngine().getShips()) {
            if (f.getOwner() == enemy && !f.isPhased()){
                Vector2f dir = Vector2f.sub(f.getLocation(), ship.getLocation(), new Vector2f());
                float newDistance = dir.length() - ship.getShieldRadiusEvenIfNoShield() - f.getShieldRadiusEvenIfNoShield();
                if (viewfinder.distanceFromArc(f.getLocation()) <= newDistance * OFFCENTER_DISTANCE_RATIO) {
                    if (newDistance < distance || target == null) {
                        distance = newDistance;
                        target = f;
                    }
                }
            }
        }
        if (target == null) return;

        // AI trigger procs much more often when flanking enemy
        float flanking = 1f;
        float angle = normalizeAngle(target.getFacing()) - normalizeAngle(ship.getFacing());
        angle = normalizeAngle(angle);
        if (angle <= FLANKING_RANGE * 0.5f || angle >= 360f - (FLANKING_RANGE * 0.5f) || target.getFluxTracker().isOverloaded()) {
            flanking = FLANKING_MULT;
        }

        // Trigger is random chance based on size of enemy, flux level, and flanking status
        if (Math.random() > ((float) approachComfort.get(target.getHullSize()) + extraMad) * flanking * (1 + (FLUX_TOLERANCE * (target.getFluxLevel() - ship.getFluxLevel()))))
            return;

        // Will never proc outside of certain range - more strict vs bigger ships
        if (distance < 50f || distance > 700f) return;
        if (distance < 300f && (target.getHullSize() == HullSize.CRUISER || target.getHullSize() == HullSize.CAPITAL_SHIP)) {
            extraMad += 0.2f;
            return;
        }


	
        extraMad = 0f;
        ship.useSystem();
	}

}
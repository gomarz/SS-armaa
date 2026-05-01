package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.VectorUtils;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;

public class armaa_assaultPodKiller extends BaseHullMod {

    private boolean spawnedPA = false;

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {

    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        return "";
    }

    //Built-in only
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }

    @Override
    public boolean affectsOPCosts() {
        return true;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
       if(ship.getCollisionClass() != CollisionClass.SHIP)
           ship.setCollisionClass(CollisionClass.SHIP);
        ship.setHitpoints(ship.getHitpoints() - amount * 6);
        for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), 1000f)) {
            if (ship.getOwner() == target.getOwner()) {
                continue;
            }
            Global.getCombatEngine().headInDirectionWithoutTurning(ship, VectorUtils.getAngle(ship.getLocation(), target.getLocation()), ship.getMaxSpeed());          
            break;
        }
        boolean justDie = ship.getHullLevel() <= 0.80f || Global.getCombatEngine().getCustomData().get("armaa_killPod_" + ship.getId()) != null;
        IntervalUtil dieTimer = null;

        Global.getCombatEngine().getFleetManager(ship.getOwner()).setSuppressDeploymentMessages(false);
        if (ship.getSharedFighterReplacementRate() < 0.95f && Global.getCombatEngine().getCustomData().get("armaa_killPod_" + ship.getId()) == null) {
            Global.getCombatEngine().getCustomData().put("armaa_killPod_" + ship.getId(), new IntervalUtil(0.5f, 0.5f));
        } else if (justDie) {
            dieTimer = (IntervalUtil) Global.getCombatEngine().getCustomData().get("armaa_killPod_" + ship.getId());
            if(dieTimer == null)
            {
                Global.getCombatEngine().getCustomData().put("armaa_killPod_" + ship.getId(), new IntervalUtil(0.5f, 0.5f));
                return;
            }
            dieTimer.advance(amount);
            float elapsed = dieTimer.getElapsed();
            float maxInterval = dieTimer.getMaxInterval();
            float dist = elapsed / maxInterval;
            float jitterFraction = dist;
            jitterFraction = Math.max(jitterFraction, dist);
            float jitterMax = 1f + 10f * jitterFraction;
            ship.setJitter(this, new Color(255, 100, 50, (int) (25 + 50 * jitterFraction)), 1f, 10, 1f, jitterMax);
        }

        if (dieTimer != null && dieTimer.intervalElapsed()) {
            DamagingExplosionSpec boom = new DamagingExplosionSpec(
                    1f ,
                    150,
                    75,
                    800f,
                    400f,
                    CollisionClass.MISSILE_NO_FF,
                    CollisionClass.PROJECTILE_FIGHTER,
                    5,
                    10,
                    5,
                    50,
                    Color.yellow,
                    Color.yellow
            );
            boom.setDamageType(DamageType.HIGH_EXPLOSIVE);
            boom.setShowGraphic(false);
            boom.setSoundSetId("mine_explosion");
            Global.getCombatEngine().spawnDamagingExplosion(boom, ship, ship.getLocation(), false);            
            Global.getCombatEngine().applyDamage(ship, ship.getLocation(), 100000f, DamageType.ENERGY, 0f, true, false, ship, false);
        }
        if (Global.getCombatEngine().isCombatOver() || Global.getCombatEngine().getFleetManager(0).getTaskManager(false).isInFullRetreat() || Global.getCombatEngine().isEnemyInFullRetreat()) {
            ship.setHitpoints(0f);
        }
    }

}

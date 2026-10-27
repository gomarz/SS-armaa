package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.ArrayList;
import java.util.List;
import java.awt.*;
import org.magiclib.util.MagicRender;

public class armaa_siegeModeStats extends BaseShipSystemScript {

    public static final float SENSOR_RANGE_PERCENT = 50f;
    public static final float WEAPON_RANGE_PERCENT = 100f;
    public static final float ROF_PERCENT = 10f;
    public static final float SPEED_PENALTY = 0.2f;
    public static final float SHIELD_REDUCTION = 0.1f;
    private float rotation = 0f;
    private IntervalUtil interval = new IntervalUtil(0.05f, 0.05f);
    private List<ShipAPI> targets;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {

        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips" + ship.getId()) instanceof List) {
            targets = (List) Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips" + ship.getId());
        } else {
            targets = new ArrayList<>();
        }
        interval.advance(Global.getCombatEngine().getElapsedInLastFrame());
        if (interval.intervalElapsed()) {

            List<ShipAPI> toRemove = new ArrayList<>();
            for (ShipAPI target : CombatUtils.getShipsWithinRange(ship.getLocation(), 1000f * effectLevel)) {
                if (target == ship) {
                    continue;
                }
                if (ship.getOwner() != target.getOwner()) {
                    continue;
                }
                if (!targets.contains(target)) {
                    Global.getCombatEngine().addFloatingText(target.getLocation(), "Shield++!", 12f, Color.white, target, 1f, 1f);
                    Global.getCombatEngine().spawnEmpArcVisual(ship.getLocation(), ship, target.getLocation(), target, 40f, Color.white, Color.white);
                    targets.add(target);
                }
            }

            for (ShipAPI target : targets) {
                if (!target.isAlive() || MathUtils.getDistance(target, ship) >= 1000f * effectLevel) {
                    target.getMutableStats().getShieldDamageTakenMult().unmodify("armaa_guardianProtocol");
                    target.setCircularJitter(false);
                    target.setJitterShields(false);
                    toRemove.add(target);
                }
            }

            for (ShipAPI remove : toRemove) {
                targets.remove(remove);
            }
            Global.getCombatEngine().getCustomData().put("armaa_guardianProtocolShips" + ship.getId(), targets);
        }

        // Jitter and the status line only last one frame, so they are refreshed every frame, not on the interval
        for (ShipAPI target : targets) {
            target.getMutableStats().getShieldDamageTakenMult().modifyMult("armaa_guardianProtocol", 1f - SHIELD_REDUCTION * effectLevel);
            if (Global.getCombatEngine().getPlayerShip() == target) {
                Global.getCombatEngine().maintainStatusForPlayerShip("armaa_guardianProtocol", "graphics/icons/hullsys/fortress_shield.png", "GRDIAN PRTCL", "Shield damage reduced by " + (int) (SHIELD_REDUCTION * 100 * effectLevel) + "%", false);
            }
            target.setJitterUnder(ship, Color.white, .5f * effectLevel, 3, 15 * effectLevel);
            target.setCircularJitter(true);
            target.setJitterShields(true);
        }
        // apply() runs outside the render pass, so draw through MagicRender in world coords. GL state set
        // here leaks into the frame: an unrestored glViewport skews the cursor under UI scaling.
        final ViewportAPI view = Global.getCombatEngine().getViewport();
        float radius = 1000f * effectLevel;
        final Vector2f loc = ship.getLocation();
        if (view.isNearViewport(loc, radius) && !Global.getCombatEngine().isPaused()) {
            SpriteAPI sprite = Global.getSettings().getSprite("ceylon", "armaa_ceylonrad");
            int alpha = (int) Math.min(255f, Math.max(0f, 255f * 0.3f * effectLevel));
            MagicRender.singleframe(sprite, new Vector2f(loc), new Vector2f(radius * 2f, radius * 2f), rotation,
                    new Color(255, 255, 255, alpha), false, CombatEngineLayers.BELOW_SHIPS_LAYER);

            // Spin it
            rotation += 5f * Global.getCombatEngine().getElapsedInLastFrame();
            if (rotation > 360f) {
                rotation -= 360f;
            }
        }
        float sensorRangePercent = SENSOR_RANGE_PERCENT * effectLevel;
        float weaponRangePercent = WEAPON_RANGE_PERCENT * effectLevel;

        stats.getSightRadiusMod().modifyPercent(id, sensorRangePercent);
        stats.getMaxSpeed().modifyMult(id, 1f - SPEED_PENALTY * effectLevel);
        stats.getMaxTurnRate().modifyMult(id, 1f - SPEED_PENALTY * effectLevel);

        stats.getBallisticWeaponRangeBonus().modifyPercent(id, ship.getMutableStats().getSystemRangeBonus().computeEffective(weaponRangePercent));
        stats.getEnergyWeaponRangeBonus().modifyPercent(id, ship.getMutableStats().getSystemRangeBonus().computeEffective(weaponRangePercent));

        stats.getBallisticRoFMult().modifyPercent(id, ROF_PERCENT * effectLevel);
        stats.getEnergyRoFMult().modifyPercent(id, ROF_PERCENT * effectLevel);

    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        stats.getSightRadiusMod().unmodify(id);

        stats.getBallisticWeaponRangeBonus().unmodify(id);
        stats.getEnergyWeaponRangeBonus().unmodify(id);

        stats.getBallisticRoFMult().unmodify(id);
        stats.getEnergyRoFMult().unmodify(id);

        stats.getMaxSpeed().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);

        List<ShipAPI> targets;
        if (Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips" + ship.getId()) instanceof List) {
            targets = (List) Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips" + ship.getId());
            for (ShipAPI target : targets) {
                target.getMutableStats().getShieldDamageTakenMult().unmodify("armaa_guardianProtocol");
                target.setCircularJitter(false);
                target.setJitterShields(false);
            }
            Global.getCombatEngine().getCustomData().put("armaa_guardianProtocolShips" + ship.getId(), null);
        }

    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        float sensorRangePercent = SENSOR_RANGE_PERCENT * effectLevel;
        float weaponRangePercent = WEAPON_RANGE_PERCENT * effectLevel;
        if (index == 0) {
            return new StatusData("WPN RNG +" + (int) weaponRangePercent + "%", false);
        } else if (index == 1) {
            return new StatusData("SNSR RNG +" + (int) sensorRangePercent + "%", false);
        } else if (index == 2) {
            return new StatusData("WPN ROF +" + (int) (ROF_PERCENT * effectLevel) + "%", false);
        }
        return null;
    }

}

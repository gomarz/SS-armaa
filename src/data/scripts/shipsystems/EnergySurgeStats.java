package data.scripts.shipsystems;

import java.awt.Color;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.combat.ShipAPI;

public class EnergySurgeStats extends BaseShipSystemScript {

    public static final float SPEED_BONUS         = 50f;
    public static final float ACCEL_PERCENT       = 150f;
    public static final float TURN_RATE_FLAT      = 15f;
    public static final float TURN_RATE_PERCENT   = 100f;
    public static final float TURN_ACCEL_FLAT     = 100f;
    public static final float TURN_ACCEL_PERCENT  = 500f;
    public static final float FLUX_DAMAGE_SCALE   = 25f;
    public static final float ROF_MULT            = 0.80f;
    public static final float RECOIL_MULT         = 2f;

    private float bonusPercent = 0f;
    private final Color color = new Color(255, 0, 115, 255);

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();

        bonusPercent = ship.getFluxLevel() * FLUX_DAMAGE_SCALE * effectLevel;

        stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS * effectLevel);
        stats.getAcceleration().modifyPercent(id, ACCEL_PERCENT * effectLevel);
        stats.getDeceleration().modifyPercent(id, ACCEL_PERCENT * effectLevel);
        stats.getMaxTurnRate().modifyFlat(id, TURN_RATE_FLAT * effectLevel);
        stats.getMaxTurnRate().modifyPercent(id, TURN_RATE_PERCENT * effectLevel);
        stats.getTurnAcceleration().modifyFlat(id, TURN_ACCEL_FLAT * effectLevel);
        stats.getTurnAcceleration().modifyPercent(id, TURN_ACCEL_PERCENT * effectLevel);

        // ramp penalties with effectLevel so IN/OUT aren't pure downside
        float rof    = 1f - (1f - ROF_MULT)    * effectLevel;
        float recoil = 1f + (RECOIL_MULT - 1f) * effectLevel;
        stats.getEnergyRoFMult().modifyMult(id, rof);
        stats.getMaxRecoilMult().modifyMult(id, recoil);
        stats.getRecoilPerShotMult().modifyMult(id, recoil);
        stats.getEnergyWeaponDamageMult().modifyPercent(id, bonusPercent);

        ship.getEngineController().fadeToOtherColor(this, color,
                new Color(0, 0, 0, 0), effectLevel, 0.67f);
        ship.getEngineController().extendFlame(this,
                1.35f * effectLevel, 1.35f * effectLevel, 0f);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
        stats.getMaxRecoilMult().unmodify(id);
        stats.getRecoilPerShotMult().unmodify(id);
        stats.getEnergyRoFMult().unmodify(id);
        stats.getEnergyWeaponDamageMult().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("Safety release: +" + (int) bonusPercent + "% energy damage", false);
        }
        if (index == 1) {
            return new StatusData("-50% energy rate of fire, increased recoil", true);
        }
        return null;
    }
}
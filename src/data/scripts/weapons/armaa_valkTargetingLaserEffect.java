package data.scripts.weapons;

import java.util.List;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_valkTargetingLaserEffect implements BeamEffectPlugin {

    public static float EFFECT_DUR = 1f;    // debuff linger duration
    public static float AUTOAIM_DEBUFF = 50f;   // % autoaim reduction
    public static float EMP_PIERCE_CHANCE_MAX = 0.75f; // max EMP pierce chance at 100% flux
    public static float EMP_PIERCE_DAMAGE = 75f;   // EMP damage per proc
    public static float WEAPON_DAMAGE_MULT = 1.15f;  // bonus damage multiplier vs weapons
    public static float ENGINE_DAMAGE_MULT = 1.10f;  // bonus damage multiplier vs engines
    private IntervalUtil pierceInterval = new IntervalUtil(0.15f,0.15f);
    protected boolean wasZero = true;

    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        CombatEntityAPI target = beam.getDamageTarget();
        if (!(target instanceof ShipAPI)) {
            return;
        }
        if (beam.getBrightness() < 1f || beam.getWeapon() == null) {
            return;
        }

        ShipAPI ship = (ShipAPI) target;

        float dur = beam.getDamage().getDpsDuration();
        if (!wasZero) {
            dur = 0;
        }
        wasZero = beam.getDamage().getDpsDuration() <= 0;
        pierceInterval.advance(amount);
        if (pierceInterval.intervalElapsed()) {
            // EMP pierce through shields, scales with target flux level
            boolean hitShield = ship.getShield() != null
                    && ship.getShield().isWithinArc(beam.getTo());
                float fluxLevel = ship.getFluxTracker().getFluxLevel();
                float empChance = fluxLevel * EMP_PIERCE_CHANCE_MAX;
            if (Math.random() < empChance) {
                    engine.spawnEmpArcPierceShields(
                            beam.getWeapon().getShip(), // damageSource
                            beam.getTo(), // point
                            ship, // pointAnchor
                            ship, // empTargetEntity
                            DamageType.ENERGY, // damageType
                            1f, // virtually no raw damage
                            EMP_PIERCE_DAMAGE, // empDamAmount
                            1000f, // maxRange (short, it's already on target)
                            "tachyon_lance_emp_impact", // impactSoundId - swap for whatever fits
                            10f, // thickness
                            beam.getFringeColor(), // fringe
                            beam.getCoreColor() // core
                    );
                }

            // Apply or refresh listener for autoaim, weapon and engine damage debuffs
            if (!ship.hasListenerOfClass(ValkDebuffListener.class)) {
                ship.addListener(new ValkDebuffListener(ship));
            }
            List<ValkDebuffListener> listeners = ship.getListeners(ValkDebuffListener.class);
            if (!listeners.isEmpty()) {
                listeners.get(0).notifyHit();
            }
        }
    }

    public static final String DEBUFF_ID = "armaa_valktargeting_debuff";

    public static class ValkDebuffListener implements AdvanceableListener {

        protected ShipAPI ship;
        protected float timeoutRemaining = 0f;

        public ValkDebuffListener(ShipAPI ship) {
            this.ship = ship;
        }

        public void notifyHit() {
            timeoutRemaining = EFFECT_DUR;
            applyDebuffs();
        }

        public void advance(float amount) {
            if (timeoutRemaining > 0) {
                timeoutRemaining -= amount;
                applyDebuffs();
            } else {
                removeDebuffs();
                ship.removeListener(this);
            }
        }

        private void applyDebuffs() {
            ship.getMutableStats().getAutofireAimAccuracy()
                    .modifyMult(DEBUFF_ID, 1f - AUTOAIM_DEBUFF * 0.01f);
            ship.getMutableStats().getWeaponDamageTakenMult()
                    .modifyMult(DEBUFF_ID, WEAPON_DAMAGE_MULT);
            ship.getMutableStats().getEngineDamageTakenMult()
                    .modifyMult(DEBUFF_ID, ENGINE_DAMAGE_MULT);
        }

        private void removeDebuffs() {
            ship.getMutableStats().getAutofireAimAccuracy().unmodify(DEBUFF_ID);
            ship.getMutableStats().getWeaponDamageTakenMult().unmodify(DEBUFF_ID);
            ship.getMutableStats().getEngineDamageTakenMult().unmodify(DEBUFF_ID);
        }

        public String modifyDamageTaken(Object param, CombatEntityAPI target,
                DamageAPI damage, Vector2f point, boolean shieldHit) {
            return null;
        }
    }
}

package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import data.scripts.util.armaa_utils;
import org.lwjgl.util.vector.Vector2f;

public class armaa_linkedSystems implements EveryFrameWeaponEffectPlugin {

    private final String SHIELD_KEY = "armaa_shieldDebuff";
    private boolean init = false;
    private boolean listenerAdded = false;

    // ── Maneuverability malus
    public static final float MANUEVER_MALUS = 0.25f;

    // ── Shield module regen
    public static final float REGEN_HP_PERCENT_PER_SECOND = 0.005f; // 0.5% max HP/s
    public static final float REGEN_MIN_THRESHOLD = 0.10f;  // must be at least 10% damaged to regen
    public static final float REGEN_ARMOR_PERCENT_PER_SECOND = 0.01f;  // 1% max armor/s (2x HP rate)
    public static final float REGEN_PAUSE_DURATION = 4f;     // seconds after taking damage

    private float lastHullFraction = 1f;
    private float lastArmorFraction = 1f;
    private float regenPauseTimer = REGEN_PAUSE_DURATION;

    private class armaa_ShieldModuleDeathListener implements AdvanceableListener, HullDamageAboutToBeTakenListener {

        private final ShipAPI module;
        private final ShipAPI parent;
        private final String key;

        armaa_ShieldModuleDeathListener(ShipAPI module, ShipAPI parent, String key) {
            this.module = module;
            this.parent = parent;
            this.key = key;
        }

        @Override
        public void advance(float f) {

        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
            float hull = ship.getHitpoints();
            if (damageAmount >= hull) {
                parent.getMutableStats().getMaxSpeed().unmodifyMult(key);
                parent.getMutableStats().getMaxTurnRate().unmodifyMult(key);
                parent.getMutableStats().getWeaponTurnRateBonus().unmodifyMult(key);
                parent.removeListener(this);
                return true;
            }
            return false;
        }
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();

        if (ship.getParentStation() == null) {
            return;
        }

        ShipAPI parent = ship.getParentStation();

        if (!init) {
            parent.getMutableStats().getMaxSpeed().modifyMult(SHIELD_KEY, 1f - MANUEVER_MALUS);
            parent.getMutableStats().getMaxTurnRate().modifyMult(SHIELD_KEY, 1f - MANUEVER_MALUS);
            parent.getMutableStats().getWeaponTurnRateBonus().modifyMult(SHIELD_KEY, 1f - MANUEVER_MALUS);
            lastHullFraction = ship.getHullLevel();
            lastArmorFraction = armaa_utils.getArmorPercent(ship);
            init = true;
        }

        if (!listenerAdded) {
            parent.addListener(new armaa_ShieldModuleDeathListener(ship, parent, SHIELD_KEY));
            listenerAdded = true;
        }

        if (ship.isHulk() || !ship.isAlive() || ship.getHitpoints() <= 0f) {
            parent.getMutableStats().getMaxSpeed().unmodifyMult(SHIELD_KEY);
            parent.getMutableStats().getMaxTurnRate().unmodifyMult(SHIELD_KEY);
            parent.getMutableStats().getWeaponTurnRateBonus().unmodifyMult(SHIELD_KEY);
            return;
        }

        boolean isPhased = parent.getPhaseCloak() != null && parent.getPhaseCloak().isActive();

        float currentHullFraction = ship.getHullLevel();
        float currentArmorFraction = armaa_utils.getArmorPercent(ship);

        if (!isPhased) {
            if (currentHullFraction < lastHullFraction || currentArmorFraction < lastArmorFraction) {
                regenPauseTimer = REGEN_PAUSE_DURATION;
            }
            if (regenPauseTimer > 0f) {
                regenPauseTimer -= amount;
            }
        } else {
            regenPauseTimer = REGEN_PAUSE_DURATION;
        }
        lastHullFraction = currentHullFraction;
        lastArmorFraction = currentArmorFraction;

        boolean isRegenning = regenPauseTimer <= 0f && !isPhased
                && (ship.getHullLevel() < (1f - REGEN_MIN_THRESHOLD)
                || armaa_utils.getArmorPercent(ship) < (1f - REGEN_MIN_THRESHOLD));

        if (isRegenning) {
            float regenHP = ship.getMaxHitpoints() * REGEN_HP_PERCENT_PER_SECOND * amount;
            ship.setHitpoints(Math.min(ship.getHitpoints() + regenHP, ship.getMaxHitpoints()));

            float currArmor = armaa_utils.getArmorPercent(ship);
            if (currArmor < 1f) {
                armaa_utils.setArmorPercentage(ship,
                        Math.min(1f, currArmor + REGEN_ARMOR_PERCENT_PER_SECOND * amount));
            }

            ship.setJitterUnder(this, new java.awt.Color(255, 130, 10, 80), 1f, 5, 0f, 5f);
            ship.setJitter(this, new java.awt.Color(255, 0, 0, 50), 1f, 3, 0f, 5f);
        }

        if (ship.getCollisionClass() != parent.getCollisionClass()) {
            ship.setCollisionClass(parent.getCollisionClass());
        }

        if (ship.getPhaseCloak() != null) {
            if (parent.getPhaseCloak().isActive() && !ship.getPhaseCloak().isActive()) {
                ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0);
            } else if (ship.getPhaseCloak().isActive() && !parent.getPhaseCloak().isActive()) {
                ship.getPhaseCloak().deactivate();
                ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
                ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM);
            } else {
                ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
            }
        }
    }
}

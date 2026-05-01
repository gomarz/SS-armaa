package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import org.lwjgl.input.Keyboard;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.armaa_utils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicLensFlare;
// This currently is hard coded to work only with the panther
// well, I guess it would work elsewhere, it'd just spawn panthers
// we could update strikecraft hullmod to just return based on customdata.. ?

public class armaa_mirageShroudStats extends BaseShipSystemScript {

    private static final int NUM_CLONES = 2;
    private static final String SHROUD_KEY = "armaa_mirageShroudKey_";
    private boolean activated = false;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        boolean hasKey = ship.getCustomData().containsKey(SHROUD_KEY + id);
        if (!hasKey && state == State.ACTIVE && !activated) {
            // for some reason itll bug out and spawn dozens of clones without this
            activated = true;
            Global.getCombatEngine().addPlugin(new armaa_mirageShroudEveryFramePlugin(ship));
            ship.getCustomData().put(SHROUD_KEY + id, true);
        }
        armaa_utils.makeAfterImages(ship, 0.5f, Global.getCombatEngine().getElapsedInLastFrame()/ Global.getCombatEngine().getTimeMult().getModifiedValue(), new Color(155, 75, 155, 75)); // cyan                

    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        activated = false;
        ship.getCustomData().remove(SHROUD_KEY + id);
    }

    private static class armaa_mirageShroudEveryFramePlugin extends BaseEveryFrameCombatPlugin {

        private ShipAPI ship;
        private ShipAPI clone;
        private IntervalUtil cloneLifeTime;
        private ArrayList<ShipAPI> clones = new ArrayList();
        private boolean isSpawned = false;
        Color MUZZLE_FLASH_COLOR = new Color(200, 25, 150, 155);
        Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);

        public armaa_mirageShroudEveryFramePlugin(ShipAPI ship) {
            this.ship = ship;
            cloneLifeTime = new IntervalUtil(ship.getSystem().getChargeActiveDur(), ship.getSystem().getChargeActiveDur());
        }

        private ShipAPI getCloneOnSide(boolean wantLeft) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || clones == null || clones.isEmpty()) {
                return null;
            }

            Vector2f shipLoc = ship.getLocation();

            // Facing vectors
            float rad = (float) Math.toRadians(ship.getFacing());
            Vector2f forward = new Vector2f((float) Math.cos(rad), (float) Math.sin(rad));
            Vector2f right = new Vector2f(forward.y, -forward.x); // rotate forward by -90deg -> right

            ShipAPI bestSide = null;
            float bestSideVal = wantLeft ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;

            ShipAPI bestClosest = null;
            float bestDist2 = Float.POSITIVE_INFINITY;

            for (ShipAPI c : clones) {
                if (c == null) {
                    continue;
                }
                if (!engine.isEntityInPlay(c)) {
                    continue;
                }
                if (c.isHulk() || !c.isAlive()) {
                    continue;
                }

                Vector2f to = Vector2f.sub(c.getLocation(), shipLoc, new Vector2f());

                // side: negative = left, positive = right (relative to ship facing)
                float side = to.x * right.x + to.y * right.y;

                // Optional: avoid swapping to a clone that is way out in front (feels less like a "sidestep")
                // float fwd = to.x * forward.x + to.y * forward.y;
                // if (fwd > 450f) continue; // tune this threshold
                float d2 = to.x * to.x + to.y * to.y;
                if (d2 < bestDist2) {
                    bestDist2 = d2;
                    bestClosest = c;
                }

                if (wantLeft) {
                    if (side < bestSideVal) { // most negative
                        bestSideVal = side;
                        bestSide = c;
                    }
                } else {
                    if (side > bestSideVal) { // most positive
                        bestSideVal = side;
                        bestSide = c;
                    }
                }
            }

            // If we found a "proper side" clone and it's not basically centerline, use it.
            // If it's near centerline (side ~ 0), swapping can feel arbitrary, so fall back to closest.
            if (bestSide != null) {
                // Side threshold: if |side| is very small, clone is roughly in front/behind, not clearly left/right
                float absSide = Math.abs(bestSideVal);
                if (absSide > 30f) {
                    return bestSide; // tune 30f..80f based on  offsets
                }
            }

            return bestClosest; // fallback: still feels responsive
        }

        private void swapWithClone(ShipAPI c) {
            if (c == null) {
                return;
            }
            Vector2f originalLoc = new Vector2f(ship.getLocation());
            // Neural interface does this, so maybe...
            Mouse.setCursorPosition((int) Global.getSettings().getScreenWidthPixels() / 2,
                    (int) Global.getSettings().getScreenHeightPixels() / 2);
            EmpArcEntityAPI.EmpArcParams params = new EmpArcEntityAPI.EmpArcParams();

            params.segmentLengthMult = 8f;
            params.zigZagReductionFactor = 0.15f;
            params.brightSpotFullFraction = 0.5f;
            params.brightSpotFadeFraction = 0.5f;

            float dist = Misc.getDistance(originalLoc, clone.getLocation());
            params.flickerRateMult = 0.6f - dist / 3000f;
            if (params.flickerRateMult < 0.3f) {
                params.flickerRateMult = 0.3f;
            }
            // Sell the swap
            EmpArcEntityAPI arc = Global.getCombatEngine().spawnEmpArcVisual(
                    originalLoc, null,
                    c.getLocation(), null,
                    25f,
                    MUZZLE_FLASH_COLOR,
                    MUZZLE_FLASH_COLOR_ALT,
                    params
            );
            arc.setCoreWidthOverride(30f);

            //arc.setFadedOutAtStart(true);
            //arc.setRenderGlowAtStart(false);
            arc.setSingleFlickerMode(true);
            MagicLensFlare.createSmoothFlare(
                    Global.getCombatEngine(),
                    c,
                    c.getLocation(),
                    c.getCollisionRadius() * 2f,
                    c.getCollisionRadius() * 2f,
                    c.getFacing(),
                    MUZZLE_FLASH_COLOR,
                    MUZZLE_FLASH_COLOR_ALT
            );
            MagicLensFlare.createSmoothFlare(
                    Global.getCombatEngine(),
                    null,
                    originalLoc,
                    c.getCollisionRadius() * 2f,
                    c.getCollisionRadius() * 2f,
                    c.getFacing(),
                    MUZZLE_FLASH_COLOR,
                    MUZZLE_FLASH_COLOR_ALT
            );
            Vector2f newLoc = new Vector2f(c.getLocation());
            ship.getLocation().set(newLoc.x, newLoc.y);

            // Optional but usually feels best:
            ship.getVelocity().set(c.getVelocity());
            ship.setFacing(c.getFacing());
        }

        private void cleanUp() {

            for (ShipAPI clone : clones) {
                spawnParticles(clone);
                clone.getLocation().set(-10000, -10000);
                Global.getCombatEngine().removeEntity(clone);
                for (ShipAPI module : clone.getChildModulesCopy()) {
                    module.getLocation().set(-10000, -10000);
                    Global.getCombatEngine().removeEntity(module);
                }
            }
            Global.getCombatEngine().removePlugin(this);
        }

        private void spawnParticles(ShipAPI ship) {
            Global.getSoundPlayer().playSound("phase_anchor_vanish", 1.1f, 1f, ship.getLocation(), ship.getVelocity());
            MagicLensFlare.createSmoothFlare(
                    Global.getCombatEngine(),
                    ship,
                    new Vector2f(ship.getLocation()),
                    5f,
                    100f,
                    0,
                    Color.red,
                    MUZZLE_FLASH_COLOR_ALT
            );

            for (int x = 0; x < 8; x++) {
                Vector2f vel = MathUtils.getPointOnCircumference(null,
                        MathUtils.getRandomNumberInRange(50f, 150f), // speed
                        (float) Math.random() * 360f // angle
                );
                Global.getCombatEngine().addHitParticle(ship.getLocation(), vel, ship.getCollisionRadius() * 6, 1f, 1.25f, MUZZLE_FLASH_COLOR);

            }
            Global.getCombatEngine().spawnExplosion(ship.getLocation(), ship.getVelocity(), MUZZLE_FLASH_COLOR, ship.getCollisionRadius() * 6, 1.25f);
            org.magiclib.util.MagicRender.battlespace(
                    Global.getSettings().getSprite("misc", "armaa_sfxpulse"),
                    ship.getLocation(),
                    new Vector2f(),
                    new Vector2f(0, 0),
                    new Vector2f(200f, 200f),
                    5f,
                    5f,
                    new Color(25, 0, 50, 100),
                    false,
                    .1f,
                    .5f,
                    .5f
            );
        }

        @Override
        public void advance(float amount, List<InputEventAPI> list) {
            if(Global.getCombatEngine().isPaused())
                return;
            if (!isSpawned) {
                for (int i = 0; i < NUM_CLONES; i++) {
                    ShipVariantAPI var = ship.getVariant().clone();
                    var.setHullSpecAPI(Global.getSettings().getHullSpec("armaa_panther_frig_mirage"));
                    var.addMod("automated");
                    var.addMod("do_not_back_off");
                    var.removePermaMod("strikeCraft");
                    var.removeMod("strikeCraft");
                    var.removeSuppressedMod("strikeCraft");
                    FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, var);
                    member.getStats().getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat("armaa", -99);

                    member.updateStats();
                    if (ship.getCaptain() != null) {
                        member.setCaptain(ship.getCaptain());
                    }
                    float offset = (i == 0 ? -200f : 200f);
                    Vector2f vec = new Vector2f(ship.getLocation().x + offset, ship.getLocation().y);
                    clone = Global.getCombatEngine().getFleetManager(ship.getOwner()).spawnFleetMember(member, vec, ship.getFacing(), 0f);
                    clone.setFacing(ship.getFacing());
                    clone.getVelocity().set(new Vector2f(ship.getVelocity()));
                    Global.getCombatEngine().spawnEmpArcVisual(ship.getLocation(), ship, vec, clone, 25f, ship.getSystem().getSpecAPI().getJitterEffectColor(), Color.white);
                    
                    // spawn particles
                    spawnParticles(clone);
                    //
                    clone.getMutableStats().getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat("armaa", -99);
                    //60% reduction : 1f-0.60f
                    clone.getMutableStats().getBallisticWeaponDamageMult().modifyMult(SHROUD_KEY, 0.40f);
                    clone.getMutableStats().getEnergyWeaponDamageMult().modifyMult(SHROUD_KEY, 0.40f);
                    Global.getCombatEngine().getFleetManager(ship.getOwner()).removeDeployed(clone, false);
                    clone.setHitpoints(ship.getHitpoints() * 0.25f);
                    clone.setHullSize(HullSize.FIGHTER);
                    clone.setCollisionClass(CollisionClass.FIGHTER);
                    clones.add(clone);
                    armaa_utils.setArmorPercentage(clone, 0.25f);
                    for(WeaponAPI sourceWep : ship.getAllWeapons())
                    {
                        for(WeaponAPI wep : clone.getAllWeapons())
                        {

                            if(wep.getId() == sourceWep.getId())
                            {
                                wep.setAmmo(sourceWep.getAmmo());
                            }
                        }
                    }
                    for (ShipAPI module : clone.getChildModulesCopy()) {
                        module.setHitpoints(1f);
                        armaa_utils.setArmorPercentage(module, 0.25f);
                    }
                }
                isSpawned = true;
            }
            if (isSpawned) {
                cloneLifeTime.advance(amount);
            }
            for (ShipAPI clone : clones) {
                clone.setAlphaMult(0.5f);
                clone.setJitter(new Object(), new Color(255, 175, 255, 100), 1f, 2, 8f);
                clone.setJitterUnder(new Object(), new Color(255, 175, 255, 50), 1f, 5, 5f);
                clone.fadeToColor(new Object(), new Color(255, 0, 255, 225), 1f, 0f, 1f);
                armaa_utils.makeAfterImages(clone, 0.5f, Global.getCombatEngine().getElapsedInLastFrame()/ Global.getCombatEngine().getTimeMult().getModifiedValue(), new Color(155, 75, 155, 75)); // cyan                
                if (clone.getFluxTracker().getFluxLevel() > 0.25f && clone.isPhased()) {
                    clone.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
                    clone.getPhaseCloak().forceState(SystemState.COOLDOWN, 10f);
                }
                for (ShipAPI module : clone.getChildModulesCopy()) {
                    if (ship.getChildModulesCopy().size() == 0) {
                        Global.getCombatEngine().removeEntity(module);
                    } else {
                        if (module.getHullSize() != clone.getHullSize()) {
                            module.setHullSize(clone.getHullSize());
                        }
                        module.setJitter(new Object(), new Color(255, 175, 255, 50), 1f, 2, 5f);
                        module.setJitterUnder(new Object(), new Color(255, 175, 255, 50), 1f, 5, 5f);
                        module.fadeToColor(new Object(), new Color(255, 0, 255, 225), 1f, 0f, 1f);
                    }
                }
            }
            boolean isPlayer = Global.getCombatEngine().getPlayerShip() == ship;
            boolean toggledOff = (ship.getSystem().isActive() && isPlayer && Keyboard.isKeyDown(Keyboard.getKeyIndex(Global.getSettings().getControlStringForEnumName("SHIP_USE_SYSTEM"))));
            if (toggledOff) {
                boolean wantsLeft = Keyboard.isKeyDown(Keyboard.getKeyIndex(Global.getSettings().getControlStringForEnumName("SHIP_STRAFE_LEFT_NOTURN"))) ? true : false;
                ShipAPI chosenClone = getCloneOnSide(wantsLeft);
                swapWithClone(chosenClone);
                cleanUp();
            } else if (cloneLifeTime.intervalElapsed()) {
                cleanUp();

            }

        }
    }

}

package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import static com.fs.starfarer.api.util.Misc.ZERO;
import data.scripts.util.armaa_utils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 *
 * @author shoi needs its own script to avoid ConcurrentModificationException
 */
public class armaa_comboUnitControlPlugin extends BaseEveryFrameCombatPlugin {

    private CombatEngineAPI engine = Global.getCombatEngine();
    protected List<ShipAPI> cataphrachtii = new ArrayList<ShipAPI>();
    protected List<ShipAPI> toRemove = new ArrayList<ShipAPI>();
    protected Map<ShipAPI, FleetMemberAPI> modulesToUpdate = new HashMap();
    private final IntervalUtil interval2 = new IntervalUtil(.05f, .05f);
    private final IntervalUtil interval3 = new IntervalUtil(.05f, .1f);
    Map<FleetMemberAPI, ShipAPI> memberToShip = new HashMap<>();

    public void putMapping(ShipAPI ship, FleetMemberAPI member) {
        if (ship == null || member == null) {
            return;
        }

        // If this fleet member is already associated with a different ShipAPI, remove the old key.
        ShipAPI oldShip = memberToShip.put(member, ship);
        if (oldShip != null && oldShip != ship) {
            modulesToUpdate.remove(oldShip);
        }

        // If this ship was previously mapped to some other member, clean reverse mapping too.
        FleetMemberAPI oldMember = modulesToUpdate.put(ship, member);
        if (oldMember != null && oldMember != member) {
            memberToShip.remove(oldMember);
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {

        // this is SUPPOSED to make sure that if the module eject and
        // is intact, they aren't considered destryoed/ need repairs
        // even if base unit dies
        if (engine.isCombatOver() || engine.isEnemyInFullRetreat()) {
            for (ShipAPI ship : engine.getShips()) {
                if (modulesToUpdate.get(ship) != null) {
                    if (ship.getOwner() == 0) {
                        FleetMemberAPI member = modulesToUpdate.get(ship);
                        member.getStatus().setHullFraction(ship.getHullLevel());
                        member.getStatus().repairDisabledABit();
                        member.getStatus().repairFully();
                        member.updateStats();
                    }
                    //Global.getLogger(this.getClass()).info("test");
                    modulesToUpdate.remove(ship);

                }
            }
        }
        interval3.advance(amount);
        if (interval3.intervalElapsed()) {
            for (ShipAPI ship : engine.getShips()) {
                if (ship.isFighter() || ship.isHulk() || !ship.isAlive()) {
                    continue;
                }

                if (ship.getVariant().getHullMods().contains("armaa_comboUnit")) {
                    if (!cataphrachtii.contains(ship) && !ship.controlsLocked()) {
                        cataphrachtii.add(ship);
                    }
                }
            }
        }
        if (cataphrachtii.isEmpty()) {
            return;
        }

        if (!cataphrachtii.isEmpty()) {
            interval2.advance(amount);
            if (interval2.intervalElapsed()) {
                toRemove.clear();
                for (ShipAPI ship : cataphrachtii) {
                    if (!ship.isAlive()) {
                        toRemove.add(ship);
                        continue;
                    }
                    boolean doubletapped = armaa_utils.isKeyDoubleTapped(ship, engine) || engine.getCustomData().containsKey("armaa_autoEject_" + ship.getId());

                    List<ShipAPI> children = ship.getChildModulesCopy();
                    if (children == null || children.size() == 0) {
                        continue;
                    }
                    for (ShipAPI module : children) {
                        if(module.isHulk() || !module.isAlive())
                        {
                            continue;
                        }
                        if (module.getStationSlot() != null && (!module.getStationSlot().getId().equals("MODULE") && !module.getStationSlot().getId().equals("MODULE_COMBAT_POS"))) {
                            continue;
                        }
                            //module.ensureClonedStationSlotSpec();
                        if (ship.getHullSpec().getWeaponSlot("MODULE_COMBAT_POS") != null && module.getStationSlot() != ship.getHullSpec().getWeaponSlot("MODULE_COMBAT_POS")) {
                            module.setStationSlot(ship.getHullSpec().getWeaponSlot("MODULE_COMBAT_POS"));
                            Global.getLogger(this.getClass()).info("Set statuin skit L " + ship.getName() + " " + module.getHullSpec().getHullNameWithDashClass());

                        }
                        if (module.controlsLocked()) {
                            continue;
                        }
                        if (!module.hasListenerOfClass(armaa_comboUnitDeathListener.class)) {
                            if (!Global.getCombatEngine().getCustomData().containsKey("armaa_autoEject_" + module.getParentStation().getId())) {
                                Global.getLogger(this.getClass()).info("Added damage listener to module of " + ship.getName() + " " + module.getHullSpec().getHullNameWithDashClass());
                                module.addListener(new armaa_comboUnitDeathListener(module));
                            }
                        }
                        if (!Global.getCombatEngine().getCustomData().containsKey("armaa_comboUnitListener_" + module.getParentStation().getId())) {
                            Global.getCombatEngine().getCustomData().put("armaa_comboUnitListener_" + module.getParentStation().getId(), true);
                            Global.getLogger(this.getClass()).info("Added listener to module of " + ship.getName() + " " + module.getHullSpec().getHullNameWithDashClass());
                            module.addListener(new armaa_comboUnitModuleListener(module));
                        }

                        module.ensureClonedStationSlotSpec();
                        if (module.getStationSlot() != null) {
                            if (!module.isAlive() || module.isHulk() || module.getLocation().getY() == -1000000f) {
                                toRemove.add(ship);
                                ship.resetDefaultAI();
                                continue;
                            }
                            if (!doubletapped && engine.getPlayerShip() == ship) {
                                continue;
                            }
                            if (doubletapped || engine.getPlayerShip() != ship && (module.getHullLevel() < 0.50f || ship.isDirectRetreat() || ship.getCurrentCR() <= 0.25f || ship.getHullLevel() < 0.10f)) {
                                ShipAPI s = createShipFromModule(ship, module, engine);
                                engine.addPlugin(new armaa_CoreEjectEffect(s));
                                if (engine.getPlayerShip() != ship && ship.getOwner() == 0) {
                                    float variance = MathUtils.getRandomNumberInRange(-0.2f, 0.2f);
                                    Global.getSoundPlayer().playUISound("cr_allied_warning", 1f, 1f);

                                    Global.getCombatEngine().getCombatUI().addMessage(0, Misc.getPositiveHighlightColor(), s.getName() + "(" + s.getHullSpec().getHullNameWithDashClass() + ")", Color.white, " has ejected from " + ship.getName());
                                }
                                if (!engine.isSimulation() && ship.getOwner() == 0 && !ship.isAlly()) {
                                    putMapping(s, module.getFleetMember());
                                }
                                engine.getCustomData().remove("armaa_autoEject_" + ship.getId());
                            }
                        }
                    }
                }
                if (!toRemove.isEmpty()) {
                    cataphrachtii.removeAll(toRemove);
                }
            }
        }
    }

    public static ShipAPI createShipFromModule(ShipAPI ship, ShipAPI module, CombatEngineAPI engine) {
        for (int x = 0; x < 30; x++) {
            engine.addNebulaSmokeParticle(module.getLocation(),
                    MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(50f, 100f), (float) Math.random() * 360f),
                    45f, 1.5f, .1f, 1f, MathUtils.getRandomNumberInRange(1f, 1.5f), Color.white);
        }
        // play some sound here
        Global.getSoundPlayer().playSound("disabled_large_crit", 1f, 1f, ship.getLocation(), ZERO);
        //WeaponslotAPI slot = module.
        module.getFluxTracker().showOverloadFloatyIfNeeded("Emergency Purge!", Color.blue, 4f, true);
        ShipVariantAPI var = module.getVariant().clone();
        var.removePermaMod("armaa_dpReduction");
        FleetMemberAPI f = Global.getFactory().createFleetMember(FleetMemberType.SHIP, var);

        PersonAPI commander = ship.getCaptain();
        if (ship.getFleetMember().getFleetData() != null) {
            commander = ship.getOwner() == 0 && !ship.isAlly() ? Global.getSector().getPlayerPerson() : ship.getFleetMember().getFleetData().getCommander();
        }
        f.setFleetCommanderForStats(commander, ship.getFleetMember().getFleetDataForStats());
        f.setCaptain(ship.getCaptain());
        f.setOwner(ship.getOwner());
        ship.getFleetMember().getCrewComposition().transfer(2, f.getCrewComposition());
        f.getRepairTracker().setCR(Math.min(1f, ship.getCRAtDeployment()));
        f.updateStats();
        if (ship.getOwner() == 0 && !ship.isAlly()) {
            engine.getFleetManager(ship.getOwner()).addToReserves(f);
            if (module.getFleetMember() != null && ship.getFleetMember().getFleetData() != null) {
                ship.getFleetMember().getFleetData().removeFleetMember(module.getFleetMember());
            }
        }
        ShipAPI s = null;
        engine.getFleetManager(ship.getOwner()).setSuppressDeploymentMessages(true);
        s = engine.getFleetManager(ship.getOwner()).spawnFleetMember(f, module.getLocation(), module.getFacing(), 0f);
        f = null;
        engine.getFleetManager(ship.getOwner()).setSuppressDeploymentMessages(false);
        s.setCurrentCR(ship.getCRAtDeployment());
        s.addListener(new armaa_comboUnitListener(s, module));
        if (ship.isAlly()) {
            s.setAlly(true);
        }
        s.setHitpoints(Math.min(module.getHitpoints(), s.getMaxHitpoints()));
        if (engine.getPlayerShip() == ship) {
            engine.setShipPlayerLastTransferredCommandTo(s);
            engine.setPlayerShipExternal(s);
        }

        s.setControlsLocked(false);

        module.setControlsLocked(true);
        module.getMutableStats().getHullDamageTakenMult().modifyMult("armaa_invincible", 0f);
        module.getMutableStats().getArmorDamageTakenMult().modifyMult("armaa_invincible", 0f);
        if (s.getCaptain() != ship.getCaptain()) {
            s.setCaptain(ship.getCaptain());
        }
        ship.setCaptain(null);
        return s;
    }

    protected static class armaa_comboUnitListener implements AdvanceableListener {

        ShipAPI ship;
        ShipAPI trueShip;

        // CombatEngineAPI engine;
        public armaa_comboUnitListener(ShipAPI ship, ShipAPI trueShip) {
            this.ship = ship;
            this.trueShip = trueShip;
        }

        @Override
        public void advance(float amount) {
            if (ship.isHulk() || !ship.isAlive()) {
                trueShip.getMutableStats().getHullDamageTakenMult().unmodify("armaa_invincible");
                trueShip.getMutableStats().getArmorDamageTakenMult().unmodify("armaa_invincible");
                // the fact we need to do it twice means something is wrong
                trueShip.removeListenerOfClass(armaa_comboUnitDeathListener.class);
                trueShip.setStationSlot(null);            
                trueShip.getLocation().set(-10000,-10000);
                armaa_utils.destroy(trueShip);
                ship.removeListener(this);
                return;

            }
            if (Global.getCombatEngine().isEntityInPlay(ship)) {
                trueShip.setAnimatedLaunch();
                if (trueShip.getAllWings().size() > 0) {
                    for (ShipAPI fighter : trueShip.getAllWings().get(0).getWingMembers()) {
                        trueShip.getAllWings().get(0).orderReturn(fighter);
                    }
                }
            }
            //redock logic
            if (ship.getShipTarget() != null && ship.getShipTarget().getOwner() == ship.getOwner()) {
                ShipAPI target = ship.getShipTarget();
                if (target.getVariant().hasHullMod("armaa_comboUnit")) {
                    if (MathUtils.getDistance(ship, target) < 20) {
                        if (armaa_utils.isKeyDoubleTapped(ship, Global.getCombatEngine())) {

                            CombatEngineAPI engine = Global.getCombatEngine();
                            if (engine.getPlayerShip() == ship) {
                                engine.setShipPlayerLastTransferredCommandTo(target);
                                engine.setPlayerShipExternal(target);
                                target.setControlsLocked(false);
                                target.setCaptain(ship.getCaptain());
                                List<ShipAPI> children = target.getChildModulesCopy();

                                if (children != null && children.size() > 0) {
                                    for (ShipAPI module : children) {
                                        module.ensureClonedStationSlotSpec();
                                        module.setControlsLocked(false);
                                        module.setCaptain(ship.getCaptain());
                                        module.getMutableStats().getHullDamageTakenMult().unmodify("armaa_invincible");
                                        module.getMutableStats().getArmorDamageTakenMult().unmodify("armaa_invincible");
                                    }
                                }
                                for (ShipAPI modules : ship.getChildModulesCopy()) {
                                    engine.removeEntity(modules);
                                }
                                ship.removeListener(this);
                                Global.getCombatEngine().getFleetManager(ship.getOwner()).removeDeployed(ship, false);
                                ship.setHitpoints(0f);
                            }
                        }
                    }
                }
            }

        }
    }

    private class armaa_comboUnitDeathListener implements AdvanceableListener, HullDamageAboutToBeTakenListener {

        private ShipAPI ship;

        public armaa_comboUnitDeathListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float f) {
        }

        @Override
        public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
            float hull = ship.getHitpoints();
            if (damageAmount >= hull && ship.getParentStation() != null) {
                Global.getCombatEngine().getCustomData().put("armaa_autoEject_" + ship.getParentStation().getId(), true);
                ship.setHitpoints(damageAmount + 1f);
                // need to do this so we dont die in the 2-3 frames before listener is applied
                ship.getMutableStats().getHullDamageTakenMult().modifyMult("armaa_invincible", 0f);
                ship.getMutableStats().getArmorDamageTakenMult().modifyMult("armaa_invincible", 0f);
                ship.removeListener(this);
                return true;
            }
            return false;
        }
    }

    private class armaa_comboUnitModuleListener implements AdvanceableListener, DamageTakenModifier {

        private ShipAPI ship;
        private ShipAPI parent;

        armaa_comboUnitModuleListener(ShipAPI ship) {
            this.ship = ship;
            this.parent = ship.getParentStation();
        }

        @Override
        public void advance(float f) {
            if (!ship.isAlive() || ship.getLocation().getY() == -1000000f) {
                if (parent != null) {
                    boolean independent = parent.getVariant().hasHullMod("automated") || parent.getHullSpec().getMinCrew() <= 0 ||parent.getVariant().hasTag("armaa_core_independent") || parent.getHullSpec().hasTag("armaa_core_independent");
                    if (!independent) {
                        parent.setControlsLocked(true);
                        parent.getFluxTracker().showOverloadFloatyIfNeeded("Core unit lost! Controls locked!", Color.red, 10f, true);

                    }
                }
                ship.getMutableStats().getHullDamageTakenMult().unmodify("armaa_invincible");
                ship.getMutableStats().getArmorDamageTakenMult().unmodify("armaa_invincible");
                ship.setHitpoints(0f);
                ship.removeListener(this);
            }

        }

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            if (parent == null || ship.controlsLocked()) {
                return "";
            }
            if (parent.getShield() != null && parent.getShield().isOn()) {
                damage.setDamage(0f);
                return "armaa_shieldprotection";

            }
            return "";
        }
    }
}

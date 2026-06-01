package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import java.awt.Color;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

public class armaa_valkyrieEffect implements EveryFrameWeaponEffectPlugin {

    private IntervalUtil interval = new IntervalUtil(0.08f, 5f);

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ShipAPI ship = weapon.getShip();

        List<ShipAPI> children = ship.getChildModulesCopy();
        interval.advance(amount);
        if (children != null) {
            for (ShipAPI m : children) {
                if (m.getStationSlot() == null || m.getShipAI() == null) {
                    continue;
                }
                ShipVariantAPI var = m.getVariant().clone();
                if (!m.getVariant().hasHullMod("armaa_noSupplies")) {
                    var.addPermaMod("armaa_noSupplies");
                    var.addPermaMod("armaa_dpReduction");
                    m.getParentStation().getVariant().setModuleVariant(m.getStationSlot().getId(), var);
                }
                if (m.controlsLocked() == false) {
                    m.setControlsLocked(true);

                } else {
                    for (WeaponAPI wep : m.getAllWeapons()) {
                        if (wep.getSlot().getId().equals("TRUE_GUN2")) {
                            wep.setCurrAngle(m.getFacing() + 90f);
                        }
                    }
                }
                if (ship.areAnyEnemiesInRange() && interval.intervalElapsed() && Math.random() > 0.50f) {
                    float variance = MathUtils.getRandomNumberInRange(-0.2f, 0.2f);
                    Global.getSoundPlayer().playSound("disabled_small_crit", 1f + variance, 1f, m.getLocation(), m.getVelocity());
                    engine.addNebulaSmokeParticle(
                            m.getLocation(),
                            m.getVelocity(),
                            40f * (0.75f + (float) Math.random() * 0.5f),
                            3f + 1f * (float) Math.random() * 2f,
                            0f,
                            0f,
                            1f,
                            Color.white);
                    /*
                    if (m.isRetreating()) {
                        m.setRetreating(false, false);
                    }
                     */
                    //m.setControlsLocked(false);
                    m.beginLandingAnimation(m);
                    m.fadeToColor(m, new Color(0, 0, 0, 0), 0.05f, 999999f, 1f);
                    //engine.getFleetManager(ship.getOriginalOwner()).getRetreatedCopy().add(m);
                    m.getMutableStats().getHullDamageTakenMult().modifyMult(ship.getId(), 0f);
                    m.getMutableStats().getArmorDamageTakenMult().modifyMult(ship.getId(), 0f);
                    FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, var);
                    member.getStats().getDynamic().getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat("armaa", -99);
                    member.setOwner(ship.getOriginalOwner());
                    member.getCrewComposition().addCrew(m.getHullSpec().getMinCrew()+m.getMutableStats().getMinCrewMod().computeEffective(1f));
                    member.updateStats();
                    member.getRepairTracker().setCR(m.getCurrentCR());
                    if (ship.getCaptain() != null) {
                        member.setCaptain(ship.getCaptain());
                    }
                    // float offset = (i == 0 ? -200f : 200f);
                    Vector2f vec = new Vector2f(m.getLocation().x, m.getLocation().y);
                    ShipAPI module = Global.getCombatEngine().getFleetManager(ship.getOwner()).spawnFleetMember(member, vec, ship.getFacing(), 0f);
                    module.setFacing(m.getFacing());
                    module.getVelocity().set(new Vector2f(ship.getVelocity()));
                    module.setControlsLocked(false);
                    module.setCurrentCR(m.getCurrentCR());
                    m.setRetreating(true, true);
                    m.setShipAI(null);

                }

                m.ensureClonedStationSlotSpec();
                if (m.getAllWings() == null || m.getAllWings().size() == 0) {
                    continue;
                }
                for (ShipAPI fighter : m.getAllWings().get(0).getWingMembers()) {
                    m.getAllWings().get(0).orderReturn(fighter);
                }
            }
        }

    }
}

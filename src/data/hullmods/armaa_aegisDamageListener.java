package data.hullmods;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.List;


public class armaa_aegisDamageListener implements DamageTakenModifier {

    private final CombatEngineAPI engine;
private static final String ARC_KEY = "armaa_aegis_lastArc";
private static final float ARC_COOLDOWN = 0.75f; // seconds between arcs per victim
    public armaa_aegisDamageListener(CombatEngineAPI engine) {
        this.engine = engine;
    }

    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage,
                                    Vector2f point, boolean shieldHit) {
        // cheapest possible early-out: no Bellators fielded => nothing to do
        List<ShipAPI> hosts = armaa_aegisPlugin.AEGIS_HOSTS;
        if (hosts.isEmpty()) return null;

        if (shieldHit) return null; // only eat what gets through the mech's own shield
        if (!(target instanceof ShipAPI)) return null;
        ShipAPI victim = (ShipAPI) target;
        if (victim.isHulk() || !victim.isAlive()) return null;

        // pick the coolest in-range covering Bellator (currentReduction already
        // rules out dead/venting/overloaded/over-cutoff hosts)
        ShipAPI best = armaa_aegisConfig.bestCoveringHost(hosts, victim);

        if (best == null) return null;
        float bestReduction = armaa_aegisConfig.currentReduction(best);
        

        float mitigated = damage.getDamage() * bestReduction;

        // mech eats the rest...
        damage.getModifier().modifyMult("armaa_aegis", 1f - bestReduction);
        // ...and the Bellator pays for it in hard flux.
        best.getFluxTracker().increaseFlux(mitigated * armaa_aegisConfig.FLUX_PER_DAMAGE, true);
        float now = engine.getTotalElapsedTime(false);
        Float last = (Float) victim.getCustomData().get(ARC_KEY);
        if (last == null || now - last >= ARC_COOLDOWN) {
            victim.getCustomData().put(ARC_KEY, now);
            engine.spawnEmpArcVisual(point, target, best.getLocation(), best,
                    10f, Color.yellow, Color.black);
        }
        return "armaa_aegis";
    }
}

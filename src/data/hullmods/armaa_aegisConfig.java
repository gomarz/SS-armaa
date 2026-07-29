package data.hullmods;

import com.fs.starfarer.api.combat.ShipAPI;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;

public final class armaa_aegisConfig {

    private armaa_aegisConfig() {}

    // ---- balance levers -------------------------------------------------
    public static final float RADIUS          = 4000f; // aura radius (su)
    public static final float MAX_REDUCTION   = 0.40f; // damage share absorbed at 0 hard flux
    public static final float FLUX_CUTOFF     = 0.65f; // host hard-flux level where reduction hits 0
    public static final float FLUX_PER_DAMAGE = 0.75f;  // hard flux charged per point of absorbed dmg
    // ---------------------------------------------------------------------

    /** Host's current hard-flux level, 0..1. */
    public static float hardFluxLevel(ShipAPI host) {
        if (host == null || host.getFluxTracker() == null
                || host.getFluxTracker().getMaxFlux() <= 0f) {
            return 0f;
        }
        return host.getFluxTracker().getHardFlux() / host.getFluxTracker().getMaxFlux();
    }

    public static float currentReduction(ShipAPI host) {
        if (host == null || host.isHulk() || !host.isAlive()) return 0f;
        if (host.getFluxTracker() == null) return 0f;
        if (host.getFluxTracker().isOverloadedOrVenting()) return 0f;

        float hf = hardFluxLevel(host);
        if (hf >= FLUX_CUTOFF) return 0f;
        float t = 1f - (hf / FLUX_CUTOFF); // 1 at 0 flux, 0 at cutoff
        return MAX_REDUCTION * t;
    }

public static ShipAPI bestCoveringHost(List<ShipAPI> hosts, ShipAPI ship) {
    ShipAPI best = null;
    float bestRed = 0f;
    for (int i = 0, n = hosts.size(); i < n; i++) {
        ShipAPI host = hosts.get(i);
        if (host.getOwner() != ship.getOwner()) continue;
        if (!isProtected(host, ship)) continue;
        if (MathUtils.getDistance(host.getLocation(), ship.getLocation()) > RADIUS) continue;
        float r = currentReduction(host);
        if (r > bestRed) { bestRed = r; best = host; }
    }
    return best;
}
    
    
    public static boolean isProtected(ShipAPI host, ShipAPI other) {
        if (other == null || other == host) return false;
        if (other.isHulk() || !other.isAlive()) return false;
        if (other.getOwner() != host.getOwner()) return false;
        // ArmaA mechs are frigate-sized fake-fighters; this also covers real
        // allied frigates. Swap to a tag check if you want strictly the swarm.
        return other.getVariant().getHullMods().contains("strikeCraft");
    }

    public static boolean isAegisHost(ShipAPI ship) {
        return ship != null && ship.isAlive() && !ship.isHulk()
                && ship.getVariant() != null
                && ship.getVariant().hasHullMod(armaa_bellatorAegis.MOD_ID);
    }
}

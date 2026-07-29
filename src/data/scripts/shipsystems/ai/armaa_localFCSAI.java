package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.lazywizard.lazylib.combat.WeaponUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.ArrayList;
import java.util.List;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

public class armaa_localFCSAI implements ShipSystemAIScript {

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private WeaponAPI weapon;
    private IntervalUtil tracker;

    public armaa_localFCSAI() {
        this.tracker = new IntervalUtil(0.5f, 1.0f);
    }

    @Override
    public void init(final ShipAPI ship, final ShipSystemAPI system, final ShipwideAIFlags flags, final CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = ship.getSystem();

    }

    @Override
    public void advance(final float amount, final Vector2f missileDangerDir, final Vector2f collisionDangerDir, final ShipAPI target) {
        tracker.advance(amount);
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        if(!AIUtils.canUseSystemThisFrame(ship))
            return;
        if(!tracker.intervalElapsed())
            return;
        // we need to check ships within system range and find the one that could use our help
        // Im thinking we check by AI Flags and then drill down based on status, like hard flux level & hull %
        // we also shouldnt do anything if they arent in combat (tho this probbaly will be ccovered by the ai flag)
        List<ShipAPI> friendlies = new ArrayList<ShipAPI>();
        List<ShipAPI> friendliesUnderFire = new ArrayList<ShipAPI>();
        for(ShipAPI tgt: CombatUtils.getShipsWithinRange(ship.getLocation(), 1000f))
        {
            if(tgt.getOwner() != ship.getOwner())
                continue;
            if(tgt.isFighter())
                continue;
            if(tgt == ship)
                continue;
            if(!tgt.areSignificantEnemiesInRange())
                continue;
            if(tgt.getAIFlags().hasFlag(AIFlags.HAS_INCOMING_DAMAGE))
            {
                friendliesUnderFire.add(tgt);
            }
            else
                friendlies.add(tgt);
        }
        ShipAPI lowest = null;  
        if(friendliesUnderFire.size() > 0)
        {
            for(ShipAPI friendly: friendliesUnderFire)
            {
                if(lowest == null)
                {
                    lowest = friendly;
                    continue;
                }
                float status = (1f-friendly.getHullLevel()) + friendly.getHardFluxLevel() + AIUtils.getNearbyEnemies(friendly, 1000f).size();
                float lstatus = (1f-lowest.getHullLevel()) + lowest.getHardFluxLevel()+ AIUtils.getNearbyEnemies(lowest, 1000f).size();;
                if(status > lstatus)
                    lowest = friendly;
            }
        }
        else
        {
            for(ShipAPI friendly: friendlies)
            {
                if(lowest == null)
                {
                    lowest = friendly;
                    continue;
                }
                if(friendly.getAIFlags().hasFlag(AIFlags.PURSUING))
                {
                    float status = (1f-friendly.getHullLevel()) + friendly.getHardFluxLevel() + AIUtils.getNearbyEnemies(friendly, 1000f).size();
                    float lstatus = (1f-lowest.getHullLevel()) + lowest.getHardFluxLevel()+ AIUtils.getNearbyEnemies(lowest, 1000f).size();;
                    if(status > lstatus)
                        lowest = friendly;
                }
            }
        }
        if(lowest ==  null)
            return;
        ship.setShipTarget(lowest);
        ship.useSystem();
    }
}

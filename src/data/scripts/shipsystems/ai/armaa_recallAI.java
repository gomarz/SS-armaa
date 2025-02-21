// 
// Decompiled by Procyon v0.5.36
// 

package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.FluxTrackerAPI;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipCommand;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.util.ArrayList;
import com.fs.starfarer.api.Global;
import java.util.List;
import data.scripts.shipsystems.armaa_RecallDeviceStats;

public class armaa_recallAI implements ShipSystemAIScript
{
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private IntervalUtil tracker;
    
    public armaa_recallAI() {
        this.tracker = new IntervalUtil(0.1f, 0.5f);
    }
    
    public void init(final ShipAPI ship, final ShipSystemAPI system, final ShipwideAIFlags flags, final CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = ship.getSystem();
    }
	
    public void advance(final float amount, final Vector2f missileDangerDir, final Vector2f collisionDangerDir, final ShipAPI target) {
        this.tracker.advance(amount);
		float dicision  = 0f;
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        if (tracker.intervalElapsed()) 
		{

				
            final FluxTrackerAPI fluxTracker = ship.getFluxTracker();
			if(fluxTracker.getFluxLevel() <= .50f && armaa_RecallDeviceStats.getFighters(ship).size() > 0)
				dicision+=9999f;
            if (dicision >= 2f && !system.isActive()) 
			{
				ship.useSystem();
            }
        }
    }
}
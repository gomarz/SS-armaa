package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.FluxTrackerAPI;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipCommand;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.VectorUtils;
import java.util.ArrayList;
import com.fs.starfarer.api.Global;
import java.util.List;
import java.awt.*;
import data.scripts.util.armaa_utils;

public class armaa_morganaAI implements ShipSystemAIScript
{
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private IntervalUtil tracker;
	private float beamBonus;
    
    public armaa_morganaAI() {
        this.tracker = new IntervalUtil(0.5f, 0.5f);
    }
    
    public void init(final ShipAPI ship, final ShipSystemAPI system, final ShipwideAIFlags flags, final CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = ship.getSystem();
		this.beamBonus = 0f;
    }
    
    public void advance(final float amount, final Vector2f missileDangerDir, final Vector2f collisionDangerDir, final ShipAPI target) {
        this.tracker.advance(amount);
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
		if(ship.getSystem().isActive())
		{
			if(ship.isFighter())
			{ 
				float closest = 999999f;
			    DamagingProjectileAPI closestProj = null;
				for(DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(ship.getLocation(),1000f))
				{
					if(proj.getOwner() == ship.getOwner())
						continue;
					if(proj.isFading())
						continue;
					if(MathUtils.getDistance(proj,ship.getWing().getSourceShip()) < closest)
					{
						closest = MathUtils.getDistance(proj,ship.getWing().getSourceShip());
						closestProj = proj;
					}
				}
				if(closestProj != null)
				engine.headInDirectionWithoutTurning(ship,VectorUtils.getAngle(ship.getLocation(),closestProj.getLocation()),ship.getMaxSpeed());
				
			}
		}
        if (tracker.intervalElapsed()) {
            float dicision = 0.0f;
			boolean abort = false;
			
            if (!ship.areAnyEnemiesInRange()) 
			{
                dicision -= 1f;
				
            }
            if(flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE) || ship.getWing() != null && ship.getWing().getSourceShip() != null && ship.getWing().getSourceShip().getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) 
			{			
				float guardBar = 1f; 
				if(Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId()) instanceof Float)
					guardBar = 1f-((Float)Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId()))/100f;
				
				if(guardBar > 0f)
				{
					float he = 0f;
					for(DamagingProjectileAPI proj:CombatUtils.getProjectilesWithinRange(ship.getLocation(), 800f))
					{
						if(proj.getOwner() == ship.getOwner())
							continue;
						he++;
					}
					
					if(he > 0f)
					{
						if(!ship.getSystem().isActive())
							ship.useSystem();
					}
				}
            }
		}
    }
}
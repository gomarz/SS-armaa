// 
// Decompiled by Procyon v0.5.36
// 

package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.FluxTrackerAPI;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipCommand;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.combat.WeaponUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import java.util.ArrayList;
import com.fs.starfarer.api.Global;
import java.util.List;
import java.awt.*;
import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import org.magiclib.util.MagicRender;

public class armaa_siegeAI implements ShipSystemAIScript
{
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private IntervalUtil tracker;
    
    public armaa_siegeAI() {
        this.tracker = new IntervalUtil(0.5f, 1.0f);
    }
    
    public void init(final ShipAPI ship, final ShipSystemAPI system, final ShipwideAIFlags flags, final CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = ship.getSystem();
    }
    
    public void advance(final float amount, final Vector2f missileDangerDir, final Vector2f collisionDangerDir, final ShipAPI target) {
        this.tracker.advance(amount);
        if (engine == null) {
            return;
        }
        if (engine.isPaused()) {
            return;
        }
        if (tracker.intervalElapsed()) {
            float dicision = 0.0f;
			boolean abort = false;
            if(flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) 
			{			
				if((armaa_utils.estimateIncomingDamage(ship) > 100f || armaa_utils.estimateIncomingBeamDamage(ship,5f) >= 10f))
				{
					dicision -= 9999f;
					flags.setFlag(ShipwideAIFlags.AIFlags.BACK_OFF,10f);					
					abort = true;				
				}
            }
		
			float bonus = 1.8f;
			List<ShipAPI> allies = new ArrayList<ShipAPI>();
			List<ShipAPI> targets = new ArrayList<ShipAPI>();			
			if(!system.isActive())
			{
				for(WeaponAPI weapon: ship.getAllWeapons())
				{
					if(weapon.isDecorative())
						continue;
					if(weapon.getSize() != WeaponSize.LARGE)
						continue;
					allies = WeaponUtils.getAlliesInArc(weapon);			
				}
					
				for(WeaponAPI weapon: ship.getAllWeapons())
				{
					if(weapon.isDecorative())
						continue;
					if(weapon.getSize() != WeaponSize.LARGE)
						continue;

					if(system.isActive())
						bonus = 1f;
					targets = CombatUtils.getShipsWithinRange(ship.getLocation(), ship.getMutableStats().getSystemRangeBonus().computeEffective(weapon.getRange()*bonus));
				
					for(ShipAPI neartarget: targets)
					{
						if(neartarget.getOwner() == ship.getOwner())
							continue;
						
						if(neartarget.isHulk() || neartarget.isFighter() || !neartarget.isAlive() || neartarget.getOwner() == ship.getOwner())
							continue;
						
						boolean close = MathUtils.getDistance(neartarget,ship) <= 500f;
						
						if(!close)
						{
							for(ShipAPI ally:allies)
							{
								if(!CollisionUtils.getCollides(weapon.getLocation(),neartarget.getLocation(),ally.getLocation(),ally.getCollisionRadius()) && weapon.distanceFromArc(neartarget.getLocation()) == 0)							
									dicision+=2;
							}
						}
						else
							dicision+=1;
					}
				}
					if(targets.isEmpty())
						return;				

				if (dicision >= 1f && !system.isActive()) 
				{
					ship.useSystem();
				}
				else if(system.isActive() && abort)
				{
					system.deactivate();
				}
			}
		}
    }
}
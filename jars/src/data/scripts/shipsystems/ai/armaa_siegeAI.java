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
            if (!ship.areAnyEnemiesInRange() || flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) 
			{
                dicision -= 9999f;
				
            }
            if(flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) 
			{			
				if(armaa_utils.estimateIncomingDamage(ship) > 200f)
				{
					dicision -= 9999f;
					abort = true;				
				}
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)) {
                dicision -= 0.5f;
            }
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY)) {
                dicision -= 1f;
            }
		
            if (flags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP)) {
                dicision -= 1f;
            }
			float bonus = 1.8f;
			
			for(WeaponAPI weapon: ship.getAllWeapons())
			{
				if(weapon.isDecorative())
					continue;
				if(weapon.getSize() != WeaponSize.LARGE)
					continue;
				if(system.isActive())
					bonus = 1f;
				List<ShipAPI> targets = CombatUtils.getShipsWithinRange(ship.getLocation(), ship.getMutableStats().getSystemRangeBonus().computeEffective(weapon.getRange()*bonus*0.85f));
				for(ShipAPI neartarget: targets)
				{
					if(neartarget.getOwner() == ship.getOwner())
						continue;
					
					if(neartarget.isHulk() || neartarget.isFighter() || !neartarget.isAlive() || neartarget.getOwner() == ship.getOwner())
						continue;
					
					boolean close = MathUtils.getDistance(neartarget,ship) <= 1000f;
					
					if(!close)
					{
						dicision+=2;
					}
					else
					{
						flags.setFlag(ShipwideAIFlags.AIFlags.BACK_OFF,1f);
						dicision-=1f;
					}
				}
				if(targets.isEmpty())
					dicision-= 0.5f;				
			}
            if (dicision >= 2f && !system.isActive()) 
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
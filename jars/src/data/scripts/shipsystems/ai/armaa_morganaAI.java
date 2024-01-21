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
		Object bonus = Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId());
		if(bonus instanceof Float)
			beamBonus = (float)bonus;
		else
			return;
		
        if (tracker.intervalElapsed()) {
            float dicision = 0.0f;
			boolean abort = false;
			
            if (!ship.areAnyEnemiesInRange()) 
			{
                dicision -= 1f;
				
            }
            if(flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) 
			{			
				if(armaa_utils.estimateIncomingDamage(ship) > 200f || ship.getHullLevel() < 0.5f)
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
			for(WeaponAPI weapon: ship.getAllWeapons())
			{
				if(!weapon.getId().equals ("armaa_morgana_beam"))
					continue;
				List<ShipAPI> targets = CombatUtils.getShipsWithinRange(ship.getLocation(), ship.getMutableStats().getSystemRangeBonus().computeEffective(weapon.getRange()));
				for(ShipAPI neartarget: targets)
				{
					if(neartarget.getOwner() == ship.getOwner())
						continue;
					
					if(neartarget.isHulk() || neartarget.isFighter() || !neartarget.isAlive() || neartarget.isAlly())
						continue;
					
					boolean close = MathUtils.getDistance(neartarget,ship) <= 1000f;					
					if(close)
					{
						dicision+=0.5f;
					}		
				}
				if(targets.isEmpty())
					dicision-= 1;
			}
			dicision+= (beamBonus/100f);
			ShipAPI beamTarget = (ShipAPI)flags.getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET);
			if(!(beamTarget instanceof ShipAPI) || beamTarget.isHulk())	
				dicision -= 99999f;
            if(dicision >= 1f && !system.isActive() && (beamBonus >= 80 * beamTarget.getHullLevel() || beamTarget.getFluxTracker().isOverloaded())) 
			{
				ship.useSystem();
            }
			else if((system.isActive() && abort) || beamBonus <= 25)
			{
				system.deactivate();
			}
		}
    }
}
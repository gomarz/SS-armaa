package data.scripts.shipsystems;

import java.util.HashMap;
import java.util.Map;
import org.magiclib.util.MagicRender;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.ArrayList;
import java.util.List;
import java.awt.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import org.lwjgl.opengl.Display;

public class armaa_siegeModeStats extends BaseShipSystemScript {

	public static final float SENSOR_RANGE_PERCENT = 25f;
	public static final float WEAPON_RANGE_PERCENT = 100f;
	private float rotation = 0f;
	private IntervalUtil interval = new IntervalUtil(0.05f,0.05f);	
	private List<ShipAPI> targets;	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		
		ShipAPI ship = (ShipAPI)stats.getEntity();
		interval.advance(Global.getCombatEngine().getElapsedInLastFrame());
		if(interval.intervalElapsed())
		{
			
			if(Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips"+ship.getId()) instanceof List)
				targets = (List)Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips"+ship.getId());
			
			else
				targets = new ArrayList<>();
			List<ShipAPI> toRemove = new ArrayList<>();	
			for(ShipAPI target:CombatUtils.getShipsWithinRange(ship.getLocation(), 1000f*effectLevel))
			{
				if(target == ship)
					continue;
				if(ship.getOwner() != target.getOwner())
					continue;
				if(!targets.contains(target))
				{
					Global.getCombatEngine().addFloatingText(target.getLocation(), "Shield++!", 12f, Color.white, target, 1f, 1f); 
					Global.getCombatEngine().spawnEmpArcVisual(ship.getLocation(), ship, target.getLocation(), target, 40f, Color.white, Color.white); 
					targets.add(target);
				}
			}
			
			for(ShipAPI target:targets)
			{
				if(MathUtils.getDistance(target, ship) < 1000f*effectLevel)
				{
					target.getMutableStats().getShieldDamageTakenMult().modifyMult("armaa_guardianProtocol", (1f-0.1f)*effectLevel);
					if(Global.getCombatEngine().getPlayerShip() == target)
						Global.getCombatEngine().maintainStatusForPlayerShip(target.getId(), "graphics/icons/hullsys/fortress_shield.png","GRDIAN PRTCL","Shield damage reduced by " + (int)((1f-.9f)*100*effectLevel), false);
					target.setJitterUnder(ship, Color.white, .5f*effectLevel, 3, 15*effectLevel);
					target.setCircularJitter(true);
					target.setJitterShields(true);
				}
				else
				{
					target.getMutableStats().getShieldDamageTakenMult().unmodify("armaa_guardianProtocol");
					toRemove.add(target);
				}					
			}
	
			for(ShipAPI remove:toRemove)
			{
				targets.remove(remove);
			}
			Global.getCombatEngine().getCustomData().put("armaa_guardianProtocolShips"+ship.getId(),targets);
		}
        final ViewportAPI view = Global.getCombatEngine().getViewport();

		float scale = Global.getSettings().getScreenScaleMult();
		float radius = 1000f*effectLevel*scale;
		final Vector2f loc = ship.getLocation();
        if (view.isNearViewport(loc, radius)) {			
		SpriteAPI sprite = Global.getSettings().getSprite("ceylon", "armaa_ceylonrad");	
		glPushAttrib(GL_ENABLE_BIT);
		glMatrixMode(GL_PROJECTION);
		glPushMatrix();
		glViewport(0, 0, Display.getWidth(), Display.getHeight());
		glOrtho(0.0, Display.getWidth(), 0.0, Display.getHeight(), -1.0, 1.0);
		glEnable(GL_TEXTURE_2D);
		glEnable(GL_BLEND);
		radius = (radius * 2f) / view.getViewMult();
		sprite.setSize(radius, radius);
		sprite.setColor(Color.white);
		sprite.setAlphaMult(0.3f*effectLevel);
		sprite.renderAtCenter(view.convertWorldXtoScreenX(loc.x), view.convertWorldYtoScreenY(loc.y));
		sprite.setAngle(rotation);
		glPopMatrix();
		glPopAttrib();
		

		// Spin it
		rotation += 5f;
		if (rotation > 360f){
			rotation -= 360f;
		}		

		}		
		float sensorRangePercent = SENSOR_RANGE_PERCENT * effectLevel;
		float weaponRangePercent = WEAPON_RANGE_PERCENT * effectLevel;
		
		stats.getSightRadiusMod().modifyPercent(id, sensorRangePercent);
		stats.getMaxSpeed().modifyMult(id,0.80f*effectLevel);
		stats.getMaxTurnRate().modifyMult(id,0.80f*effectLevel);
	
		stats.getBallisticWeaponRangeBonus().modifyPercent(id, ship.getMutableStats().getSystemRangeBonus().computeEffective(weaponRangePercent)*effectLevel);
		stats.getEnergyWeaponRangeBonus().modifyPercent(id, ship.getMutableStats().getSystemRangeBonus().computeEffective(weaponRangePercent)*effectLevel);
		
		stats.getBallisticRoFMult().modifyPercent(id, 1.1f*effectLevel);
		stats.getEnergyRoFMult().modifyPercent(id, 1.1f*effectLevel);
		stats.getEnergyProjectileSpeedMult().modifyPercent(id, weaponRangePercent*effectLevel);
		stats.getBallisticProjectileSpeedMult().modifyPercent(id, weaponRangePercent*effectLevel);	

	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		ShipAPI ship = (ShipAPI)stats.getEntity();
		stats.getSightRadiusMod().unmodify(id);
		
		stats.getBallisticWeaponRangeBonus().unmodify(id);
		stats.getEnergyWeaponRangeBonus().unmodify(id);
		
		stats.getBallisticRoFMult().unmodify(id);
		stats.getEnergyRoFMult().unmodify(id);	
		
		stats.getBallisticProjectileSpeedMult().unmodify(id);
		stats.getEnergyProjectileSpeedMult().unmodify(id);		

		stats.getMaxSpeed().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		
		List<ShipAPI> targets; 
		if(Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips"+ship.getId()) instanceof List)
		{
			targets = (List)Global.getCombatEngine().getCustomData().get("armaa_guardianProtocolShips"+ship.getId());		
			for(ShipAPI target:targets)
			{
				target.getMutableStats().getShieldDamageTakenMult().unmodify("armaa_guardianProtocol");
				target.setJitterShields(false);				
			}
			Global.getCombatEngine().getCustomData().put("armaa_guardianProtocolShips"+ship.getId(),null);	
		}
		
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		float sensorRangePercent = SENSOR_RANGE_PERCENT * effectLevel;
		float weaponRangePercent = WEAPON_RANGE_PERCENT * effectLevel;
		if (index == 0) {
			return new StatusData("WPN PROJ SPEED +" + (int) weaponRangePercent + "%", false);
		} else if (index == 1) {
			//return new StatusData("increased energy weapon range", false);
			return null;
		} else if (index == 2) {
			return new StatusData("WPN/SNSR RNG+" + (int) weaponRangePercent + "%", false);
		}
		return null;
	}
	
}

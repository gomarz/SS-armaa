package data.scripts.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import java.util.Random;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.combat.WeaponGroupAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class armaa_takeOffScript extends BaseEveryFrameCombatPlugin
{
	private ShipAPI ship;
	private ShipAPI carrier;
	private WeaponSlotAPI w;
	private IntervalUtil interval = new IntervalUtil(10f, 10f);
	private boolean stayInPlace = true;
	private boolean runOnce = false;
	private boolean hasLaunched = false;

    public armaa_takeOffScript(ShipAPI ship, ShipAPI carrier) 
	{
        this.ship = ship;
		this.carrier = carrier;
    }

    @Override
	public void advance(float amount, List<InputEventAPI> events)
	{
		Vector2f takeoffLoc = carrier.getLocation();
		if(!runOnce)
		for(FighterLaunchBayAPI wep:carrier.getLaunchBaysCopy())
		{
			if(wep.getWeaponSlot() != null)
			{
				w = wep.getWeaponSlot();
				if(Math.random() <= .25f)
					break;
			}
		}
		if(!runOnce)
			runOnce = true;
		
		if(w != null)
			takeoffLoc = new Vector2f(carrier.getLocation().x+w.getLocation().y, carrier.getLocation().y+w.getLocation().x);
			VectorUtils.rotate(takeoffLoc,carrier.getFacing());
			armaa_utils.setLocation(ship,takeoffLoc);
		if(stayInPlace)
		{
			ship.getTravelDrive().deactivate();
		}				
		if(ship.isFinishedLanding())
		{
			for(WeaponAPI w: ship.getAllWeapons())
			{
				if(w.getSprite() != null)
					w.getSprite().setColor(new Color(255,255,255,255));
				
				if(w.getBarrelSpriteAPI() != null)
					w.getBarrelSpriteAPI().setColor(new Color(255,255,255,255));
									
				if(w.getUnderSpriteAPI() != null)
					w.getUnderSpriteAPI().setColor(new Color(255,255,255,255));
				
				if(w.getMissileRenderData() != null)
				{
					for(MissileRenderDataAPI m: w.getMissileRenderData())
						if(m.getSprite() != null)
							m.getSprite().setColor(new Color(255,255,255,255));
				}
			}		

			//armaa_utils.setLocation(ship,takeoffLoc);
			ship.getSpriteAPI().setColor(new Color(255,255,255,255));
			Global.getSoundPlayer().playSound("fighter_takeoff", 1f, 1f, ship.getLocation(),new Vector2f());
			ship.setAnimatedLaunch();
			stayInPlace = false;
			//ship.setControlsLocked(false); 
			if(w != null)
				ship.setFacing(carrier.getFacing()+w.getAngle());			
			
			else
				ship.setFacing(carrier.getFacing());				
			if(Global.getCombatEngine().getPlayerShip() == ship)
			{
				carrier.getFluxTracker().showOverloadFloatyIfNeeded("Good luck out there!", Misc.getBasePlayerColor(), 2f, true);
				Global.getSoundPlayer().playSound("ui_noise_static", 1f+MathUtils.getRandomNumberInRange(-0.3f, .3f), 1f, carrier.getLocation(),new Vector2f());
			}
			Vector2f.add(carrier.getVelocity(),ship.getVelocity(),ship.getVelocity());
			for(WeaponGroupAPI w:ship.getWeaponGroupsCopy())
			{
				if(!w.getActiveWeapon().usesAmmo() || w.getActiveWeapon().getAmmoPerSecond() != 0)
					w.toggleOn();
			}
			if(ship.getVariant().hasHullMod("armaa_strikeCraftFrig"))
			{
				ship.setHullSize(HullSize.FRIGATE);
				//ship.resetDefaultAI();
			}
			
			hasLaunched = true;
		}
			if(ship.getCombinedAlphaMult() >= 0.5f && hasLaunched)
			{
				ship.getFluxTracker().setCurrFlux(0f);
				ship.getTravelDrive().deactivate();
				ship.getTravelDrive().setCooldown(10f); 
				Global.getCombatEngine().removePlugin(this);
				
			}
			//Global.getCombatEngine().maintainStatusForPlayerShip("debug", "graphics/ui/icons/icon_repair_refit.png",String.valueOf(ship.getCombinedAlphaMult()),"" ,true);
	}
}
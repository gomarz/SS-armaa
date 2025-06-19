package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;

import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.awt.Color;
//import org.lazywizard.lazylib.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import org.magiclib.util.MagicRender;


public class armaa_legAnimLarge implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private WeaponAPI torso, rShould, lShould,lTurret,rTurret;
	public int frame = 7;
    private IntervalUtil interval = new IntervalUtil(0.08f, 0.08f);
	private IntervalUtil interval2;
	private Color ogColor;  
    public void init(WeaponAPI weapon, float amount)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()) {
                case "WS0002":
                    if(torso==null) {
                        torso = w;
                    }
                    break;

                case "LSHOULDER":
                    if(lShould==null) {
                        lShould = w;
                    }
                    break;

                case "RSHOULDER":
                    if(rShould==null) {
                        rShould = w;
                    }
                    break;
                case "WS0005":
                    if(lTurret==null) {
                        lTurret = w;
                    }
                    break;

                case "WS0004":
                    if(rTurret==null) {
                        rTurret = w;
                    }
                    break;
            }
			
        }
		ogColor = new Color(weapon.getSprite().getColor().getRed()/255f,weapon.getSprite().getColor().getGreen()/255f,weapon.getSprite().getColor().getBlue()/255f);
		//interval to prevent legs being drawn constantly due to time dilation
		interval2 = new IntervalUtil(amount, amount);
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{		
        ShipAPI ship = weapon.getShip();		
		if(!Global.getCombatEngine().isEntityInPlay(weapon.getShip()))
			return;
		
		if(!MagicRender.screenCheck(0.1f, weapon.getLocation()) || Global.getCombatEngine().isPaused())
		{
			return;
		}
				
		if(!runOnce)
        init(weapon,amount);
		Color defColor = ship.getSpriteAPI().getAverageColor();

		interval.advance(amount);
		if(ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) 
		{	
			//interval.advance(amount);
			if(interval.intervalElapsed())
			if(frame!= 0)
				frame--;
			
			if(frame < 0)
				frame = 0;
		}

		else if (ship.getEngineController().isAccelerating()) 
		{	
			//interval.advance(amount);
			if(interval.intervalElapsed())
			if(frame!= 11)
				frame++;
			
			if(frame > 11)
				frame = 11;
		}
		
		else
		{
			if(interval.intervalElapsed())		
			{
				
				if(frame > 6)
					frame--;
				
				else if(frame != 6)
					frame++;
			}
		}
		String frameStr = "0"+frame;
		if(frame >= 10)
			frameStr = frame+"";		
		SpriteAPI spr = Global.getSettings().getSprite("graphics/armaa/ships/legs/destroyer/armaa_dd_legs"+frameStr+".png");
		//String frameStr = frame;
		if(ship.getHullSpec().getHullId().contains("armaa_kshatriya"))
			spr = Global.getSettings().getSprite("graphics/armaa/ships/legs/destroyer/kshatriya/armaa_dd_legs"+frameStr+".png");
		else if(ship.getHullSpec().getHullId().equals("armaa_zanac_grey") || (ship.getHullSpec().getDParentHullId() != null && ship.getHullSpec().getDParentHullId().equals("armaa_zanac_grey")))
			spr = Global.getSettings().getSprite("graphics/armaa/ships/legs/destroyer/grey/armaa_dd_legs"+frameStr+".png");	
		Color color = new Color(defColor.getRed()*2,defColor.getGreen()*2,defColor.getBlue()*2,defColor.getAlpha());		
		color = new Color(255,255,255);

		
		color = new Color(color.getRed()/255f,color.getGreen()/255f,color.getBlue()/255f,(color.getAlpha()/255f)*ship.getCombinedAlphaMult());

		MagicRender.singleframe(
					spr,
					new Vector2f(weapon.getLocation().getX(),weapon.getLocation().getY()),
					new Vector2f(spr.getWidth(),spr.getHeight()),
					ship.getFacing()-90f,
					color,
					false,
					CombatEngineLayers.BELOW_SHIPS_LAYER
			);
    }
}

package data.scripts.weapons;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.Global;
import org.magiclib.util.MagicRender;


public class armaa_guarDualLegAnim implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private WeaponAPI torso;
	public int frame = 7;
    private IntervalUtil interval = new IntervalUtil(0.07f, 0.07f);
	private IntervalUtil interval2;
	private Color ogColor;
    public void init(WeaponAPI weapon, float amount)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()) {
                case "B_TORSO":
                    if(torso==null) {
                        torso = w;
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
		
		if(ship.getOwner() == -1 || !ship.isAlive())
		{
			weapon.getSprite().setColor(new Color(0,0,0,0));		
			return;
		}
		
		if(!Global.getCombatEngine().isEntityInPlay(weapon.getShip()))
			return;
				
		if(!MagicRender.screenCheck(0.1f, weapon.getLocation()) || Global.getCombatEngine().isPaused())
		{
			return;
		}
		
		if(!runOnce)
        init(weapon,amount);
		Color defColor = ship.getSpriteAPI().getAverageColor();
		if(torso != null)
			defColor = torso.getSprite().getAverageColor(); 
		boolean isRobot = false;
		float alphaMod = isRobot == false ? 1 : 0;
		if(engine.getCustomData().containsKey("armaa_tranformState_"+ship.getId()))
			isRobot = (boolean)engine.getCustomData().get("armaa_tranformState_"+ship.getId());
		if(engine.getCustomData().containsKey("armaa_tranformLevel_"+ship.getId()))
			alphaMod = (Float)engine.getCustomData().get("armaa_tranformLevel_"+ship.getId());
		if(isRobot)
		{
			interval.advance(amount);			
			if(ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) 
			{	
				if(interval.intervalElapsed())
				if(frame!= 1)
					frame--;
				
				if(frame < 1)
					frame = 1;
			}

			else if (ship.getEngineController().isAccelerating()) 
			{	
				//interval.advance(amount);
				if(interval.intervalElapsed())
				if(frame!= 13)
					frame++;
				
				if(frame > 13)
					frame = 13;
			}
			
			else
			{
				if(interval.intervalElapsed())		
				{
					
					if(frame > 7)
						frame--;
					
					else if(frame != 7)
						frame++;
				}
			}
		}
		else
		{
			interval.advance(amount*4);
			if(interval.intervalElapsed())		
			{
				
				if(frame > 7)
					frame--;
				
				else if(frame != 7)
					frame++;
				else
					return;
			}					
		}
		SpriteAPI spr = Global.getSettings().getSprite("graphics/armaa/ships/legs/armaa_ht_legs0"+frame+".png");			
		if(frame >= 10)
		spr = Global.getSettings().getSprite("graphics/armaa/ships/legs/armaa_ht_legs"+frame+".png");				
		weapon.getAnimation().setFrame(frame);
		Color color = new Color(defColor.getRed()+60,defColor.getGreen()+60,defColor.getBlue()+60);
		color = new Color(color.getRed()/255f,color.getGreen()/255f,color.getBlue()/255f,(color.getAlpha()/255f)*ship.getCombinedAlphaMult()*(1f-alphaMod));
		MagicRender.singleframe(
					spr,
					new Vector2f(weapon.getLocation().getX(),weapon.getLocation().getY()),
					new Vector2f(spr.getWidth()*color.getAlpha()/255f,spr.getHeight()*color.getAlpha()/255f),
					ship.getFacing()-90f,
					color,
					false,
					CombatEngineLayers.FRIGATES_LAYER
			);
		
		weapon.getSprite().setColor(new Color(0f,0f,0f,0f));
    }
}

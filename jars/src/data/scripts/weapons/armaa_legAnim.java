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


public class armaa_legAnim implements EveryFrameWeaponEffectPlugin {

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
		if(!Global.getCombatEngine().isEntityInPlay(weapon.getShip()))
			return;
				
		if(!MagicRender.screenCheck(0.1f, weapon.getLocation()) || Global.getCombatEngine().isPaused())
		{
			return;
		}
		
        ShipAPI ship = weapon.getShip();
		
		if(!ship.isAlive())
			return;
		
		if(ship.getHullSpec().getHullId().equals("armaa_valkazard"))
			return;
		if(ship.getOwner() == -1)
		{
			weapon.getAnimation().setFrame(7);	
			return;
		}
		
		if(!runOnce)
        init(weapon,amount);
		if(torso == null)
			return;
		interval.advance(amount);
		if(ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) 
		{	
			//interval.advance(amount);
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
			if(frame!= 16)
				frame++;
			
			if(frame > 16)
				frame = 16;
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
			
		SpriteAPI spr = Global.getSettings().getSprite("graphics/armaa/ships/legs/armaa_ht_legs0"+frame+".png");		
		Color defColor = torso.getSprite().getAverageColor();
		
		Color color = new Color(defColor.getRed()+60,defColor.getGreen()+60,defColor.getBlue()+60);

		if(frame >= 10)
		spr = Global.getSettings().getSprite("graphics/armaa/ships/legs/armaa_ht_legs"+frame+".png");				
		weapon.getAnimation().setFrame(frame);
		
		if(ship.getHullSpec().getBaseHullId().equals("armaa_leynos_frig") || ship.getHullSpec().getBaseHullId().equals("armaa_leynos_frig_lt"))
		{
			spr = Global.getSettings().getSprite("graphics/armaa/ships/legs/armaa_ml_legs0"+frame+".png");
			if(ship.getHullSpec().getBaseHullId().equals("armaa_leynos_frig"))
				color = new Color(Color.white.getRed()-50,Color.white.getGreen()-50,Color.white.getBlue()-50,255);
			else
				color = new Color(defColor.getRed()+50,defColor.getGreen()+20,defColor.getBlue()+20);
			if(frame >= 10)
				spr = Global.getSettings().getSprite("graphics/armaa/ships/legs/armaa_ml_legs"+frame+".png");
		}
			
		if(ship.getVariant().getWeaponSpec("F_LEGS").getWeaponId().equals("armaa_valkazard_legs"))
		{
			color = new Color((ogColor.getRed()/255f)*.85f,ogColor.getGreen()/255f,ogColor.getBlue()/255f,1f*ship.getCombinedAlphaMult());
			spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs0"+frame+".png");	
			if(frame >= 10)
			spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs"+frame+".png");				

		}
		color = new Color(color.getRed()/255f,color.getGreen()/255f,color.getBlue()/255f,(color.getAlpha()/255f)*ship.getCombinedAlphaMult());
		//interval2.advance(1f*amount);
		//if(interval2.intervalElapsed())
		MagicRender.singleframe(
					spr,
					new Vector2f(weapon.getLocation().getX(),weapon.getLocation().getY()),
					new Vector2f(spr.getWidth(),spr.getHeight()),
					ship.getFacing()-90f,
					color,
					false,
					CombatEngineLayers.BELOW_SHIPS_LAYER
			);
		
		weapon.getSprite().setColor(new Color(0f,0f,0f,0f));
    }
}

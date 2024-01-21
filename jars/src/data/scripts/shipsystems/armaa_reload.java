package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicRender;
import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;
import data.scripts.util.armaa_reloadEveryFrame;    
import org.lwjgl.input.Keyboard;

public class armaa_reload extends BaseShipSystemScript {

	public static final float ROF_BONUS = 1f;
	public static final float FLUX_REDUCTION = 50f;
	public static final float TIME_MULT = 1.50f;
	public static final Object KEY_JITTER = new Object();
	private IntervalUtil interval = new IntervalUtil(1.5f,1.5f);
	private Color startColor = Color.RED;
	private Color endColor = Color.YELLOW;
	private final float baseAccuracy = 0.7f;
	private final float maxAccuracy = 1f;
	float rotation = 0f;	

	public static final float DAMAGE_INCREASE_PERCENT = 50;
	
	public static final Color JITTER_UNDER_COLOR = new Color(255,50,0,125);
	public static final Color JITTER_COLOR = new Color(255,50,0,75);

	public static float lerp(float a, float b, float t) 
	{
		return Math.max(0,(1 - t) * a + t * b);
	}	
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) 
	{
			float amount = Global.getCombatEngine().getElapsedInLastFrame();
			ShipAPI ship = (ShipAPI)stats.getEntity();
			ShipAPI enemy = ship.getShipTarget();
			ShipAPI target = enemy == null ? ship:enemy;
			interval.advance(amount);
			SpriteAPI waveSprite = Global.getSettings().getSprite("ceylon", "armaa_ceylontarget");
			SpriteAPI waveSprite2 = Global.getSettings().getSprite("misc", "armaa_monitorrad");
			SpriteAPI waveSprite3 = Global.getSettings().getSprite("ceylon", "armaa_ceylonrad");
			float elapsedTime = interval.getElapsed()/interval.getIntervalDuration();// calculate elapsed time since transition started

			float sizeRatio = elapsedTime; // a value between 0 and 1 indicating how far along the transition is

			Color currentColor = new Color(
				(int) Math.round(lerp(startColor.getRed(), endColor.getRed(), sizeRatio)),
				(int) Math.min(255,Math.round(lerp(startColor.getGreen(), endColor.getGreen(), sizeRatio))),
				(int) Math.round(lerp(startColor.getBlue(), endColor.getBlue(), sizeRatio)),
				(int) Math.round(lerp(startColor.getAlpha()*effectLevel, endColor.getAlpha()*effectLevel, sizeRatio)));
			
				rotation+=1f;
			float radius = ship.getCollisionRadius()*2; //radius - radius*radius
			float newRadius = (radius*2)-radius*elapsedTime;
			if(Math.abs(radius-newRadius) <= 15)
				currentColor = Color.GREEN;
			boolean abort = Keyboard.isKeyDown(Keyboard.KEY_F);
			boolean isPlayer = ship == Global.getCombatEngine().getPlayerShip() && Global.getCombatEngine().isUIAutopilotOn();	
			if (waveSprite != null && isPlayer)
			{
				//if(interval.intervalElapsed())
				MagicRender.objectspace(
							waveSprite,
							target,
							new Vector2f(),
							new Vector2f(),
							new Vector2f(newRadius,newRadius),
							new Vector2f(0f,0f),
							rotation,
							25f,
							true,
							currentColor, 
							true,
							0f,
							amount,
							0f,
							true
					);
				MagicRender.objectspace(
							waveSprite2,
							target,
							new Vector2f(),
							new Vector2f(),
							new Vector2f(radius,radius),
							new Vector2f(0f,0f),
							0f,
							25f,
							true,
							Color.white, 
							true,
							0f,
							amount,
							0f,
							true
					);
				MagicRender.objectspace(
							waveSprite3,
							target,
							new Vector2f(),
							new Vector2f(),
							new Vector2f(radius+15,radius+15),
							new Vector2f(0f,0f),
							rotation*-1,
							25f,
							true,
							Color.blue, 
							true,
							0f,
							amount,
							0f,
							true
					);
			}
			float aiAccuracy = -1;
			if(aiAccuracy <= 0)
			{
				aiAccuracy = Math.max(baseAccuracy,baseAccuracy + (maxAccuracy - baseAccuracy) * (ship.getCaptain().getStats().getLevel() - 1) / 4f); // scale accuracy from 0 to 1 based on level
			}
			float aiAccNeeded = (float)Math.random();
			aiAccNeeded*= 0.7f;		
			if(!isPlayer && aiAccuracy >= aiAccNeeded && Math.abs(radius-newRadius) >= 15)
			{
				return;
			}
			boolean success = false;
			String outcome = "Failure!";
			if( (isPlayer && abort || !isPlayer) && state == State.ACTIVE)
			{
				if((isPlayer || (!isPlayer && aiAccuracy >= aiAccNeeded)) && Math.abs(radius-newRadius) < 15)
				{
					WeaponAPI weapon = null;
					success = true;
					for(WeaponAPI wep: ship.getAllWeapons())
					{
						if(wep.getId().equals("armaa_barretta"))
						{
							weapon = wep;
							break;
						}
					}
					outcome = "perfect!";
					Global.getSoundPlayer().playSound("ui_char_spent_story_point_combat", 1.3f, 1f, ship.getLocation(),new Vector2f());
					Global.getCombatEngine().addPlugin(new armaa_reloadEveryFrame(ship));	
				}

				if(isPlayer)
				{
					Global.getCombatEngine().addFloatingText(ship.getLocation(),outcome,24,Color.WHITE,ship,0f,0f);		
					MagicRender.objectspace(
								waveSprite,
								target,
								new Vector2f(),
								new Vector2f(),
								new Vector2f(newRadius,newRadius),
								new Vector2f(100f,100f),
								rotation,
								25f,
								true,
								currentColor, 
								true,
								0f,
								.5f,
								.5f,
								true
						);
					MagicRender.objectspace(
								waveSprite2,
								target,
								new Vector2f(),
								new Vector2f(),
								new Vector2f(radius,radius),
								new Vector2f(0f,0f),
								0f,
								25f,
								true,
								Color.white, 
								true,
								0f,
								.5f,
								.5f,
								true
						);			
				}
				if(success == false)
				{
					Global.getSoundPlayer().playSound("cr_playership_malfunction", 1.3f, 1f, ship.getLocation(),new Vector2f());						
				}
				ship.getSystem().deactivate();				
			}			
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) 
	{
		interval = new IntervalUtil(1.5f,1.5f);
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		float mult = 1f + ROF_BONUS * effectLevel;
		float bonusPercent = (int) ((mult - 1f) * 100f);
		if (index == 0) {
			return new StatusData("ballistic rate of fire +" + (int) bonusPercent + "%", false);
		}
		if (index == 1) {
			return new StatusData("ballistic flux use -" + (int) FLUX_REDUCTION + "%", false);
		}
		return null;
	}
}

package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicFakeBeam;
import java.awt.Color;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lwjgl.input.Keyboard;
import org.magiclib.util.MagicRender.*;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;

import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;


public class armaa_gasGiantBattlePlugin extends BaseEveryFrameCombatPlugin
{
	// for atmo battle
	boolean warning = true;
    protected CombatEngineAPI engine;
	private IntervalUtil interval = new IntervalUtil(.025f, .05f);
	private IntervalUtil interval2 = new IntervalUtil(.05f, .05f);
	private IntervalUtil interval3 = new IntervalUtil(2f, 2f);
	private IntervalUtil attackInterval = new IntervalUtil(2f, 2f);	
	private float spin = 0f, ratioMod = 0f;
	// 1 - 100% HP
	private boolean playedMusic = false;
	private float bossStage = 1f;
	private float battleStr = -99f;
	private boolean bossLine1, bossLine2, bossLine3, firstContact = false, stationAlive = true;
	private boolean spawnedBoss = false, spawnedCorpse = false;
    public static Map MANUVER_MALUS = new HashMap();
    static {
        /*mass_mult.put(HullSize.FRIGATE, 3f);
        mass_mult.put(HullSize.DESTROYER, 3f);
        mass_mult.put(HullSize.CRUISER, 2f);
        mass_mult.put(HullSize.CAPITAL_SHIP, 2f); massy*/
        MANUVER_MALUS.put(HullSize.FIGHTER, 0.90f);
        MANUVER_MALUS.put(HullSize.FRIGATE, 0.80f);
        MANUVER_MALUS.put(HullSize.DESTROYER, 0.70f);
        MANUVER_MALUS.put(HullSize.CRUISER, 0.60f);
        MANUVER_MALUS.put(HullSize.CAPITAL_SHIP, 0.50f);
    }
	
	public Color shiftColor(Color start, Color end, float ratio)
	{
		Color intermediateColor = Color.WHITE;
        int steps = 100; // Number of steps in the transition
        long duration = 1500; // Duration of the transition in milliseconds		
		if(ratio >= 1)
			return end;
		
		int red = (int) (start.getRed() * (1 - ratio) + end.getRed() * ratio);
		int green = (int) (start.getGreen() * (1 - ratio) + end.getGreen() * ratio);
		int blue = (int) (start.getBlue() * (1 - ratio) + end.getBlue() * ratio);
		int alpha = (int) (start.getAlpha() * (1 - ratio) + end.getAlpha() * ratio);
		intermediateColor = new Color(red, green, blue, alpha);	
		
		return intermediateColor;
		
		
	}

    @Override
    public void advance(float amount, List<InputEventAPI> events)
    {
		if(!playedMusic)
		{
			Global.getSoundPlayer().playCustomMusic(1,1,"music_armaa_ax_bounty",true);
			playedMusic = true;
			engine.setDoNotEndCombat(true); 
		}
		if(engine == null)
			return;
        Color startColor = new Color(75, 30, 0, 255); // Starting color: (200, 0, 0)
        Color endColor = new Color(15, 30, 75, 100);   // Ending color: (0, 0, 200)
        Color startColorBG = new Color(150, 150, 150, 255); // Starting color: (200, 0, 0)
        Color endColorBG = new Color(175, 175, 175, 255);   // Ending color: (0, 0, 200)
        Color endColorBG2 = new Color(175, 175, 175, 0);   // Ending color: (0, 0, 200)		
		engine.setBackgroundColor(new Color(150,125,0,255));
		float ratio = 0f;
		float bgStage = 0f;
		if(!engine.isPaused())
		{
		
			ratio = (float) (engine.getElapsedInContactWithEnemy() / 100);
			
			if (!stationAlive) {
				if (ratioMod >= 0f) {
					ratioMod = -ratio; // Set `ratioMod` initially when station is no longer alive
				}
				ratio += ratioMod; // Decrease `ratio` by `ratioMod` when `stationAlive` is false
			} else {
				ratio = 0f;       // Reset `ratio` if the station is alive
				ratioMod = 0f;    // Reset `ratioMod` to prevent locking when station is alive
			}
			bgStage = ratio;
			engine.setBackgroundGlowColor(shiftColor(startColor,endColor,ratio));			
			interval3.advance(amount);
			float mult = engine.getViewport().getViewMult();
			//Global.getCombatEngine().maintainStatusForPlayerShip("AceSystem3", "graphics/ui/icons/icon_repair_refit.png", "mult",""+ ratio,false);		
			float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
			float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport	
			float minY = engine.getViewport().getLLY(); // Leftmost X-coordinate of the viewport
			float maxY = engine.getViewport().getLLY() + engine.getViewport().getVisibleHeight(); // Rightmost X-coordinate of the viewport	
			float size = Math.max(1f,1f*(float)(engine.getTotalElapsedTime(false) / 100));
			float mapMult = size/2f;
				MagicRender.screenspace(
					Global.getSettings().getSprite("misc", "armaa_gravion"),
					MagicRender.positioning.CENTER, 
					new Vector2f(0,0), 
					new Vector2f(0,0), 
					new Vector2f(Global.getSettings().getScreenWidth()*(1.2f+mapMult),Global.getSettings().getScreenWidth()*(1.2f+mapMult)), 
					new Vector2f(0,0),
					spin, 
					0f, //spin 
					startColorBG, 
					false, 
					0f, 
					0f, 
					0f, 
					0f, 
					0f, 
					0f, 
					1, 
					0f, 
					CombatEngineLayers.CLOUD_LAYER
				);
				spin+= amount/2;	
				SpriteAPI spr = Global.getSettings().getSprite("misc", "armaa_atmo_storm");
				MagicRender.screenspace(
				spr,
				MagicRender.positioning.CENTER,				
				new Vector2f(0,0),
				new Vector2f(0,0),
				new Vector2f(spr.getWidth()*size,spr.getHeight()*size),
				new Vector2f(0,0),		
				-spin*3,
				0f,
				new Color(Math.min(1f,0.7f*size),Math.min(1f,0.7f*size),Math.min(0.60f,0.60f*size),0.80f),
				false,
				0f,
				0f,
				0f,
				0f,
				0f,
				0f,
				amount,
				0f,
				CombatEngineLayers.CLOUD_LAYER
				);				
			if(bgStage > 0.50f)
			{
				size = Math.min(1f,(bgStage)-0.50f);
				spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("station_derelict_survey_mothership").getSpriteName());
				MagicRender.screenspace(
				spr,
				MagicRender.positioning.CENTER,				
				new Vector2f(500-(500*size),0),
				new Vector2f(0,0),
				new Vector2f(spr.getWidth()*size,spr.getHeight()*size),
				new Vector2f(0,0),		
				-spin*3,
				0f,
				new Color(0.7f*size,0.7f*size,0.7f*size,1f),
				false,
				0f,
				0f,
				0f,
				0f,
				0f,
				0f,
				amount,
				0f,
				CombatEngineLayers.CLOUD_LAYER
				);
				float elapsed = attackInterval.getElapsed();
				float maxinterval = attackInterval.getMaxInterval();
				float rate = Math.min(1f,elapsed/maxinterval);
				SpriteAPI enemySpr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("warden").getSpriteName());
				if(bgStage < 0.60f)
					MagicRender.screenspace(
					enemySpr,
					MagicRender.positioning.CENTER,				
					new Vector2f(500-(500*size)-(1000*rate),0),
					new Vector2f(0,0),
					new Vector2f((spr.getWidth()/12),(spr.getHeight()/12)),
					new Vector2f(0,0),		
					0+(45f*size),
					0f,
					new Color(0.6f*size,0.6f*size,0.6f*size,1f),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0f,
					amount,
					0f,
					CombatEngineLayers.CLOUD_LAYER
					);
			}
				ShipAPI bossEne = null;			
				if(engine.getCustomData().containsKey("armaa_atmo_boss"))
					bossEne = (ShipAPI)engine.getCustomData().get("armaa_atmo_boss");
			if(bossEne != null && (bgStage > 0.60f && battleStr >= 0 && battleStr/2 > engine.getFleetManager(0).getCurrStrength() || (!bossEne.isAlive() && (bossEne.getCurrentCR() < 0.20f) && !bossEne.isRetreating())))
			{
				if(bossEne.getFluxTracker().isOverloaded())
					return;
				battleStr = -999;
				engine.getFleetManager(1).getTaskManager(false).orderFullRetreat();		
				for(ShipAPI ship : engine.getAllShips())
				{
					if(ship.getOwner() == 1)
						ship.setRetreating(true,true);
					engine.getFleetManager(1).getTaskManager(false).orderRetreat(engine.getFleetManager(1).getDeployedFleetMember(ship),true,false);						
				}
				if(bossEne != null && bossEne.isAlive())
				{				
					if(bossEne.getHullLevel() > 0.50f || bossEne.getCurrentCR() > 0.20f)
					{
						engine.getCombatUI().addMessage(1,bossEne,Color.cyan,bossEne.getName(),Color.white,":",Color.cyan,"More inbound. We've done enough here, pull back for now.");
						Global.getSector().getMemoryWithoutUpdate().set("$armaa_imeldaSavedPlayer",true);						
					}
					else
					{
						if(bossEne.isAlive())
							engine.getCombatUI().addMessage(1,bossEne,Color.cyan,bossEne.getName(),Color.white,":",Color.cyan,"These ones aren't half bad. Pull back. We'll relay this development to IK.");
						Global.getSector().getMemoryWithoutUpdate().set("$armaa_imeldaSavedPlayerDidSignificantDamage",true);
					}
					engine.getFleetManager(0).spawnShipOrWing("brawler_pather_Raider",new Vector2f(-100,-10000),0f,10f);	
					engine.getFleetManager(0).spawnShipOrWing("brawler_pather_Raider",new Vector2f(0,-10000),0f,10f);	
					engine.getFleetManager(0).spawnShipOrWing("brawler_pather_Raider",new Vector2f(100,-10000),0f,10f);		
				}				
			}
			if((bgStage >= 0.50f || engine.getFleetManager(1).getCurrStrength() <= 0f) && !spawnedBoss && !stationAlive)
			{
				// Apparently this can be the case
				//if (Misc.getAICoreOfficerPlugin(ITEM) != null) {
				//	return;
				//}
				engine.setDoNotEndCombat(false); 
				//Global.getSoundPlayer().pauseCustomMusic();	
				battleStr = engine.getFleetManager(0).getCurrStrength();
				PersonAPI pilot = Misc.getAICoreOfficerPlugin(Commodities.ALPHA_CORE).createPerson(Commodities.ALPHA_CORE,"remnant",new Random());	
				String bossStr = "armaa_valkenx_ss_boss";
				String chaffStr = "armaa_valkenx_ss_IK";
				boolean haveNex = Global.getSettings().getModManager().isModEnabled("nexerelin");			
				if(haveNex && Global.getSector().getPlayerMemoryWithoutUpdate().get("$nex_startingFactionId").equals("armaarmatura_pirates"))
				{
					bossStr = "armaa_leynos_cc_standard";
					chaffStr = "armaa_valkenx_frig_assault";	
				}						
				ShipAPI boss = engine.getFleetManager(1).spawnShipOrWing(bossStr,new Vector2f(0,10000),270f,0f);
				engine.getFleetManager(1).spawnShipOrWing(chaffStr,new Vector2f(-100,10000),270f,5f);
				engine.getFleetManager(1).spawnShipOrWing(chaffStr,new Vector2f(100,10000),270f,5f);					
				engine.getFleetManager(1).spawnShipOrWing("brawler_tritachyon_Standard",new Vector2f(-100,-10000),270f,5f);	
				engine.getFleetManager(1).spawnShipOrWing("brawler_tritachyon_Standard",new Vector2f(100,-10000),270f,5f);
				engine.getFleetManager(1).spawnShipOrWing("brawler_tritachyon_Standard",new Vector2f(100,-10000),270f,5f);					
				boss.setCaptain(pilot);
				Global.getSoundPlayer().playUISound("cr_playership_critical", 0.67f, 10f);					
				engine.getCombatUI().addMessage(1,boss,Color.red,boss.getName(),Color.white,":",Color.cyan,"Unknown IFF detected; offworld origin. You've entered restricted space. Power down your weapons and surrender immediately, or face swift destruction. This is your only warning.");					
				spawnedBoss = true;
				engine.getCustomData().put("armaa_atmo_boss",boss);					
			}
			ShipAPI boss = null;
			if(engine.getCustomData().containsKey("armaa_atmo_boss"))
				boss = (ShipAPI)engine.getCustomData().get("armaa_atmo_boss");
			if(boss != null && boss.isAlive() && (!bossLine1 || !bossLine2 || !bossLine3 || !firstContact))
			{
				if(!firstContact && boss.areAnyEnemiesInRange())
				{
					//Global.getSoundPlayer().playCustomMusic(0,0,"music_armaa_pirate_encounter_hostile",true);						
					engine.getCombatUI().addMessage(1,boss,Color.red,boss.getName(),Color.white,":",Color.white,"Engaging intercept protocols. Let's see if you spacers have the guts to die for your arrogance.");
					firstContact = true;
				}						
				bossStage = boss.getHullLevel();
				if(bossStage <= 0.25f && !bossLine3)
				{
					engine.getCombatUI().addMessage(1,boss,Color.red,boss.getName(),Color.white,":",Color.white,"Impossible...!");
					bossLine3 = true;
					bossLine2 = true;
					bossLine1 = true;
				}
				else if(bossStage <= 0.50f && !bossLine2)
				{
					engine.getCombatUI().addMessage(1,boss,Color.red,boss.getName(),Color.white,":",Color.white,"You're not walking away from this. Let's end it.");
					bossLine2 = true;
					bossLine1 = true;						
				}
				else if(bossStage <= 0.75f && !bossLine1)
				{
					engine.getCombatUI().addMessage(1,boss,Color.red,boss.getName(),Color.white,":",Color.white,"I'll give you credit-you're lasting longer than expected. But pushing your luck won't change the outcome.");	
					bossLine1 = true;
				}						
				
			}				

			if(Math.random() < 0.33)
			{
					float xVel = (float)(MathUtils.getRandomNumberInRange(-800,800));
					float cloudSize = (float)(MathUtils.getRandomNumberInRange(1500,2000)*(Math.random()*2));
					if(size > 1)
					MagicRender.battlespace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),engine.getViewport().getCenter().y+engine.getViewport().getVisibleHeight()),
					new Vector2f(xVel,-MathUtils.getRandomNumberInRange(800,1000)*mult),
					new Vector2f(MathUtils.getRandomNumberInRange(cloudSize*0.7f,cloudSize*1.25f),MathUtils.getRandomNumberInRange(cloudSize*0.70f,cloudSize*1.25f)),
					new Vector2f(0f,0f),		
					0f,
					0f,
					shiftColor(new Color(75,60,50,200),new Color(175,175,150,200),0.8f),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					(0.2f)*mult,
					(0.5f+0.80f-ratio)*mult,
					(0.5f+0.80f-ratio)*mult,
					CombatEngineLayers.BELOW_SHIPS_LAYER
					);				
					xVel = (float)(MathUtils.getRandomNumberInRange(-1000,1000));
					cloudSize = (float)(MathUtils.getRandomNumberInRange(500,1000));

					if(Math.random() < 0.05)
					MagicRender.battlespace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),engine.getViewport().getCenter().y+engine.getViewport().getVisibleHeight()),
					new Vector2f(xVel,-MathUtils.getRandomNumberInRange(500,800)*mult),
					new Vector2f(MathUtils.getRandomNumberInRange(cloudSize/2f,cloudSize),MathUtils.getRandomNumberInRange(cloudSize/2f,cloudSize)),
					new Vector2f(0f,0f),		
					0f,
					0f,
					new Color(225,225,200,80),
					true,
					0f,
					0f,
					0f,
					0f,
					0f,
					(1f)*mult,
					(0.5f+0.80f)*mult,
					(0.7f+0.80f)*mult,
					CombatEngineLayers.ABOVE_SHIPS_LAYER
					);

				
				if(Math.random() <= 0.15)
				MagicRender.battlespace(
				Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
				new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),engine.getViewport().getCenter().y+engine.getViewport().getVisibleHeight()),
				new Vector2f(xVel,-MathUtils.getRandomNumberInRange(200,400)*mult),
				new Vector2f(MathUtils.getRandomNumberInRange(cloudSize,cloudSize*2f),MathUtils.getRandomNumberInRange(cloudSize,cloudSize*2f)),
				new Vector2f(200f,200f),		
				0f,
				0f,
				getRandomBottomCloudColor(ratio),
				true,
				0f,
				0f,
				0f,
				0f,
				0f,
				(1f)*mult,
				(0.5f+0.80f)*mult,
				(1f+0.80f)*mult,
				CombatEngineLayers.BELOW_SHIPS_LAYER
				);			
			}				
		}
		if(!engine.isPaused())
		{		
			interval.advance(amount);
			attackInterval.advance(amount);
			Vector2f vec = (Vector2f)engine.getCustomData().get("armaa_atmoWarningLoc"+0);

			if(attackInterval.intervalElapsed())
			{
				float minX = engine.getViewport().getLLX(); // Leftmost X-coordinate of the viewport
				float maxX = engine.getViewport().getLLX() + engine.getViewport().getVisibleWidth(); // Rightmost X-coordinate of the viewport	
				float minY = engine.getViewport().getLLY(); // Leftmost X-coordinate of the viewport
				float maxY = engine.getViewport().getLLY() + engine.getViewport().getVisibleHeight(); // Rightmost X-coordinate of the viewport
				Random rand = new Random();
				ShipAPI nearbyShip = engine.getShips().get(rand.nextInt(engine.getShips().size()));
				Vector2f altVector = new Vector2f(0,-10000);
				for(int i = 0; i < 1; i++)
				{
					if(warning)
					{
						vec =  nearbyShip != null ? new Vector2f(MathUtils.getRandomPointInCircle(nearbyShip.getLocation(),2000f)) : altVector;
						MagicRender.battlespace(
						Global.getSettings().getSprite("ceylon", "armaa_ceylontarget"),
						vec,
						new Vector2f(0,0),
						new Vector2f(1050,1050),
						new Vector2f(-250f,-250f),		
						0f,
						10f,
						new Color(255,75,0,150),
						true,
						0f,
						0f,
						0f,
						0f,
						0f,
						0.2f,
						2f,
						0.5f,
						CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
						);
						MagicRender.battlespace(
						Global.getSettings().getSprite("ceylon", "armaa_ceylontarget"),
						vec,
						new Vector2f(0,0),
						new Vector2f(1150,1150),
						new Vector2f(-250f,-250f),		
						0f,
						-10f,
						new Color(255,150,0,150),
						true,
						0f,
						0f,
						0f,
						0f,
						0f,
						0.2f,
						2f,
						0.5f,
						CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
						);					
						warning = false;
						engine.getCustomData().put("armaa_atmoWarningLoc"+i,vec);
						float size = (ratio)-0.50f;
						SpriteAPI spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("warden").getSpriteName());
						MagicRender.battlespace(
						spr,			
						engine.getViewport().getCenter(),
						new Vector2f(-500,0),
						new Vector2f(spr.getWidth()*0.25f,spr.getHeight()*0.25f),
						new Vector2f(0,0),		
						45f,
						0f,
						new Color(0.5f,0.5f,0.5f,1f),
						true,
						0f,
						0f,
						0f,
						0f,
						0f,
						0f,
						amount,
						0f,
						CombatEngineLayers.BELOW_SHIPS_LAYER
						);	
					}
					else if(!warning)
					{
						for (ShipAPI ship : engine.getShips())
						{
							if(ship.getOwner() != 1)
								continue;
							if(!engine.getCustomData().containsKey("armaa_atmoWarningLoc"+i))
								continue;
							vec = (Vector2f)engine.getCustomData().get("armaa_atmoWarningLoc"+i);
							DamagingExplosionSpec spec = new DamagingExplosionSpec(1f, 500f, 500f, 500f, 50f, CollisionClass.HITS_SHIPS_AND_ASTEROIDS , CollisionClass.HITS_SHIPS_AND_ASTEROIDS, 5f, 10f, 1f, 10, Color.blue, Color.white);
							engine.spawnDamagingExplosion(spec, ship, vec, true); 										
							break;
						}
						warning = true;
					}
				}

			}
			for(CombatEntityAPI asteroid: engine.getAsteroids())
			{
				if(ratio > 0.3)
					engine.removeEntity(asteroid);
			}
			for (ShipAPI ship : engine.getShips())
			{
				float velX = ship.getVelocity().getX();
				float velY = ship.getVelocity().getY();			
				if(attackInterval.intervalElapsed())
				{
					if(Math.random() < 0.10f)
					{
					 engine.spawnEmpArcVisual(new Vector2f(10000,-10000*ship.getOwner()), null, ship.getLocation(), null, (float)Math.random()*100f, generateLightningColor(), Color.white);
					 if(Math.random() > 0.50f)
						 break;
					 
					}
				}				
				if(stationAlive && ship.getName() != null && ship.getName().equals("GR340959F") && ship.isCapital() && ship.getHullLevel() > 0.50f)
				{
					ship.setHitpoints(ship.getHitpoints()*0.65f);
					ship.setCurrentCR(0.50f);
				}
				
				if(stationAlive && ship.getName() != null && ship.getName().equals("GR340959F"))
				{
					if(!ship.isAlive() || ship.isHulk() || !engine.isEntityInPlay(ship) || Global.getSector().getMemoryWithoutUpdate().get("$armaa_killedJeniusGuardian") != null)
					{
						stationAlive = false;
						Global.getSector().getMemoryWithoutUpdate().set("$armaa_killedJeniusGuardian",true);
						Global.getSoundPlayer().playUISound("cr_allied_critical", 0.77f, 10f);	
						engine.getCombatUI().addMessage(1,ship,Color.white,"Obstacle eliminated. Proceeding into lower orbit...");							
						engine.removeEntity(ship);
						engine.getFleetManager(1).spawnShipOrWing("warden_Defense",new Vector2f(0,-7000),270f,0f);	
						engine.getFleetManager(1).spawnShipOrWing("warden_Defense",new Vector2f(0,-7000),270f,0f);							
					}
				}

				if(!ship.isAlive() || ship.isHulk() || !engine.isEntityInPlay(ship))
				{
					engine.removeEntity(ship);					
				}
				ship.getMutableStats().getMaxSpeed().modifyMult(ship.getId()+"_atmo",(float)MANUVER_MALUS.get(ship.getHullSize()));
				ship.getMutableStats().getMaxTurnRate().modifyMult(ship.getId()+"_atmo",(float)MANUVER_MALUS.get(ship.getHullSize()));
				ship.getMutableStats().getAcceleration().modifyMult(ship.getId()+"_atmo",(float)MANUVER_MALUS.get(ship.getHullSize()));
				ship.getMutableStats().getTurnAcceleration().modifyMult(ship.getId()+"_atmo",(float)MANUVER_MALUS.get(ship.getHullSize()));	
				Global.getCombatEngine().maintainStatusForPlayerShip("atmo", "graphics/ui/icons/icon_repair_refit.png","In Atmoshpere", "Manuverability reduced",true);	
			}										
		}	
	}

    // Method to generate a random color for the top (lighter) cloud layer
    private Color getRandomTopCloudColor(float ratio) {
		Random rand = new Random();
        // Define the range for the top layer colors (bright yellow-orange shades)
        float r = rand.nextFloat() * 0.30f+0.30f; // Random red value from 0 to 1
        float g = rand.nextFloat() * 0.1f+0.10f; // Random green value from 0.35 to 1.0 for yellow-orange range
        float b = rand.nextFloat() * 0.1f; // Random blue value from 0 to 0.5 for warmer, less cool tones

        // Return the random color
        return new Color(r, g, b,0.7f-ratio);
    }

    // Method to generate a random color for the bottom (darker) cloud layer
    private Color getRandomBottomCloudColor(float ratio) {
		Random rand = new Random();
        float r = rand.nextFloat() * 0.85f+0.15f; // Random red value from 0 to 1
        float g = rand.nextFloat() * 0.7f+0.1f; // Random green value from 0.35 to 1.0 for yellow-orange range
        float b = rand.nextFloat() * 0.05f+0.05f; // Random blue value from 0 to 0.5 for warmer, less cool tones

        // Return the random color
        return new Color(r, g, b,0.5f);
    }
	
	// Method to generate a random color for a brighter, more vibrant reentry effect
	private Color generateLightningColor() 
	{
        float hue;
        float saturation = 1.0f;
        float brightness = 1.0f;
		Random random = new Random();
        int type = random.nextInt(1); // Pick a random lightning type

        switch (type) {
            case 0: // Yellow-White Lightning
                hue = randomInRange(50, 60) / 360f; // Pale yellow hue
                break;
            case 1: // Orange or Deep Amber
                hue = randomInRange(30, 40) / 360f;
                break;
            default:
                hue = 0; // Default to white
        }

        return Color.getHSBColor(hue, saturation, brightness);
    }

    private int randomInRange(int min, int max) 
	{
		Random random = new Random();
        return min + random.nextInt(max - min + 1);
    }
	

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}

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
import lunalib.lunaSettings.LunaSettings;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;


public class armaa_atmosphericBattlePlugin extends BaseEveryFrameCombatPlugin
{
	// for atmo battle
	boolean warning = true;
    protected CombatEngineAPI engine;
	private IntervalUtil interval = new IntervalUtil(.025f, .05f);
	private IntervalUtil interval2 = new IntervalUtil(.05f, .05f);
	private IntervalUtil interval3 = new IntervalUtil(2f, 2f);
	private IntervalUtil bgInterval = new IntervalUtil(0.05f,0.5f);	
	private IntervalUtil attackInterval = new IntervalUtil(2f, 2f);	
	private float spin = 0f, ratioMod = 0f;
	private boolean playedMusic = false;
	private boolean perfMode = false;
	private float bossStage = 1f, ratio = 0f, bgStage = 0f;
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
			if (Global.getSettings().getModManager().isModEnabled("lunalib"))
			{
				perfMode = LunaSettings.getBoolean("armaa", "armaa_performanceMode");
			}			
			Global.getSoundPlayer().playCustomMusic(1,1,"music_armaa_ax_bounty",true);
			playedMusic = true;
			// give player some extra help
			// depending how things went in the previous stage
			// killing the guardian even at 50% might be too difficult
			if(engine.getFleetManager(0).getCurrStrength() < 100 && Global.getSector().getMemoryWithoutUpdate().get("$armaa_killedJeniusGuardian") == null)
			{
				ShipAPI ally = engine.getFleetManager(0).spawnShipOrWing("hyperion_Attack",new Vector2f(0,-5000),0,5f);
				ally = engine.getFleetManager(0).spawnShipOrWing("afflictor_Strike",new Vector2f(0,-5000),0,5f);
				ally.setName("ISS Rogue");
				engine.getCombatUI().addMessage(1,ally,Color.green,ally.getName(),Color.green,":",Color.white,"Commander, this is the ISS Rogue. We've been retasked to assist. Following your lead.");		
			}		
			engine.setDoNotEndCombat(true); 
		}
		if(engine == null)
			return;
        Color startColor = new Color(75, 0, 0, 255); // Starting color: (200, 0, 0)
        Color endColor = new Color(15, 30, 75, 100);   // Ending color: (0, 0, 200)
        Color startColorBG = new Color(100, 25, 0, 255); // Starting color: (200, 0, 0)
        Color endColorBG = new Color(100, 100, 100, 255);   // Ending color: (0, 0, 200)
        Color endColorBG2 = new Color(175, 175, 175, 0);   // Ending color: (0, 0, 200)		
		engine.setBackgroundColor(new Color(150,100,100,255));
				if(ratio >= 0.1f)
				MagicRender.screenspace(
					Global.getSettings().getSprite("misc", "armaa_atmo2"),
					MagicRender.positioning.CENTER, 
					new Vector2f(0,0), 
					new Vector2f(0,0), 
					new Vector2f(Global.getSettings().getScreenWidth()*(1.2f),Global.getSettings().getScreenWidth()*(1.2f)), 
					new Vector2f(0,0),
					spin, 
					0f, //spin 
					shiftColor(new Color(50,50,50,255),endColorBG,bgStage), 
					false, 
					0f, 
					0f, 
					0f, 
					0f, 
					0f, 
					0f, 
					-1, 
					0f, 
					CombatEngineLayers.CLOUD_LAYER
				);
				Color bg2Color  = new Color(200,155,155,0);
				if(ratio < 0.45f)
				MagicRender.screenspace(
					Global.getSettings().getSprite("misc", "armaa_atmo"),
					MagicRender.positioning.CENTER,
					new Vector2f(0,0), 
					new Vector2f(0,0), 
					new Vector2f(Global.getSettings().getScreenWidth()*(1.2f+3f*ratio),Global.getSettings().getScreenWidth()*(1.2f+3f*ratio)), 
					new Vector2f(50,50), 
					spin,
					0f, //spin 
					shiftColor(new Color(155,100,100,200),bg2Color,bgStage), 
					false, 
					0f, 
					0, 
					0f, 
					0f, 
					0f, 
					0f, 
					-1, 
					0f, 
					CombatEngineLayers.CLOUD_LAYER  
				);		
			if(bgStage > 0.50f)
			{
				float size = Math.min(1f,(bgStage)-0.50f);
				SpriteAPI spr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("station_derelict_survey_mothership").getSpriteName());
				MagicRender.screenspace(
				spr,
				MagicRender.positioning.CENTER,				
				new Vector2f(500-(500*size),0),
				new Vector2f(0,0),
				new Vector2f(spr.getWidth()*size,spr.getHeight()*size),
				new Vector2f(0,0),		
				-spin*3,
				0f,
				new Color(0.6f*size,0.6f*size,0.6f*size,1f),
				false,
				0f,
				0f,
				0f,
				0f,
				0f,
				0f,
				-1,
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
					-1,
					0f,
					CombatEngineLayers.CLOUD_LAYER
					);
			}				
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
			spin+= amount/2;			

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
			if((bgStage >= 0.20f || engine.getFleetManager(1).getCurrStrength() <= 0f) && !spawnedBoss && !stationAlive)
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
				if(haveNex && Global.getSector().getPlayerMemoryWithoutUpdate().get("$nex_startingFactionId") != null && Global.getSector().getPlayerMemoryWithoutUpdate().get("$nex_startingFactionId").equals("armaarmatura_pirates"))
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
			if(((!perfMode && interval3.intervalElapsed()) || perfMode && bgInterval.intervalElapsed()) && ratio < 0.55f)
			{					
				for(int i = 0; i < Math.random()*4*mult; i++)
				{
					float xVel = (float)(MathUtils.getRandomNumberInRange(-100,100));
					float cloudSize = (float)(MathUtils.getRandomNumberInRange(1200,1800)*(Math.random()*2));
					MagicRender.battlespace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),MathUtils.getRandomNumberInRange(minY,maxY)),
					new Vector2f(xVel,-MathUtils.getRandomNumberInRange(600,1800)),
					new Vector2f(cloudSize,cloudSize),
					new Vector2f(50f,50f),		
					90f,
					0f,
					shiftColor(new Color(75,50,50,100),endColor,bgStage),
					false,
					0f,
					0f,
					0f,
					0f,
					0f,
					0.4f*mult,
					2f*mult,
					1f*mult,
					CombatEngineLayers.BELOW_SHIPS_LAYER
					);
				}
				
			}		
			else if(interval3.intervalElapsed() && ratio > 0.60f )
			{				
				for(int i = 0; i < Math.random()*2*mult; i++)
				{
					float xVel = (float)(MathUtils.getRandomNumberInRange(-250,250));
					float cloudSize = (float)(MathUtils.getRandomNumberInRange(1000,1500)*(Math.random()*2));
					MagicRender.battlespace(
					Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
					new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),engine.getViewport().getCenter().y+engine.getViewport().getVisibleHeight()),
					new Vector2f(xVel,-MathUtils.getRandomNumberInRange(100,300)*mult),
					new Vector2f(MathUtils.getRandomNumberInRange(cloudSize*0.75f,cloudSize*1.25f),MathUtils.getRandomNumberInRange(cloudSize*0.75f,cloudSize*1.25f)),
					new Vector2f(50f,50f),		
					0f,
					0f,
					new Color(150,150,150,225),
					true,
					0f,
					0f,
					0f,
					0f,
					0f,
					0.4f*mult,
					2f*mult,
					1f*mult,
					CombatEngineLayers.BELOW_SHIPS_LAYER
					);
				}
				
			}
			if(ratio <= 1f && Math.random() < 0.33)
			{
				
				float xVel = (float)(MathUtils.getRandomNumberInRange(-1000,1000));
				float cloudSize = (float)(MathUtils.getRandomNumberInRange(1000,2000)*(Math.random()*2));
				
				if(Math.random() <= 0.40 && ratio >= 0.20f && ratio < 0.45f)
				MagicRender.battlespace(
				Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
				new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),engine.getViewport().getCenter().y+engine.getViewport().getVisibleHeight()),
				new Vector2f(xVel,-(float)Math.random()*MathUtils.getRandomNumberInRange(1500,2000)*mult),
				new Vector2f(MathUtils.getRandomNumberInRange(cloudSize*0.75f,cloudSize*1.25f),MathUtils.getRandomNumberInRange(cloudSize*0.75f,cloudSize*1.25f)),
				new Vector2f(10f,10f),		
				0f,
				0f,
				shiftColor(getRandomTopCloudColor(ratio),new Color(0.4f,0.3f,0.3f,1f-ratio),bgStage),
				true,
				0f,
				0f,
				0f,
				0f,
				0f,
				(0.2f)*mult,
				(0.5f+0.80f-ratio)*mult,
				(0.5f+0.80f-ratio)*mult,
				CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
				);
				int random = MathUtils.getRandomNumberInRange(0,3);
				if(random == 1)
					random = 0;
				
				if(Math.random() <= 0.30 && ratio < 0.40f)
				MagicRender.battlespace(
				Global.getSettings().getSprite("backgrounds", "star2"),
				new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),engine.getViewport().getCenter().y+engine.getViewport().getVisibleHeight()),
				new Vector2f(xVel,-MathUtils.getRandomNumberInRange(100,1000)*mult),
				new Vector2f(30,30),
				new Vector2f(2f,2f),		
				0f,
				0f,
				shiftColor(new Color(255, 133, 6,255),new Color(255, 133, 6,0),bgStage),
				true,
				0f,
				0f,
				0f,
				0f,
				0f,
				0.5f*mult,
				2f*mult,
				1f*mult,
				CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
				);
				if(Math.random() <= 0.50 && ratio > 0.10f && ratio < 0.80f)
				MagicRender.battlespace(
				Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
				new Vector2f(MathUtils.getRandomNumberInRange(minX,maxX),engine.getViewport().getCenter().y+engine.getViewport().getVisibleHeight()),
				new Vector2f(xVel,-MathUtils.getRandomNumberInRange(800,1000)*mult),
				new Vector2f(MathUtils.getRandomNumberInRange(cloudSize*1.25f,cloudSize*3f),MathUtils.getRandomNumberInRange(cloudSize*1.25f,cloudSize*3f)),
				new Vector2f(50f,50f),		
				0f,
				0f,
				shiftColor(getRandomBottomCloudColor(ratio),new Color(0.5f,0.5f,0.5f,1f-ratio),bgStage),
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
			}				
		}
		Vector2f vec = (Vector2f)engine.getCustomData().get("armaa_atmoWarningLoc"+0);

		if(vec != null && !warning && bgStage < 0.60f)
		{
			
			float elapsed = attackInterval.getElapsed();
			float maxinterval = attackInterval.getMaxInterval();
			float rate = Math.min(1f,elapsed/maxinterval);
			SpriteAPI enemySpr = Global.getSettings().getSprite(Global.getSettings().getHullSpec("warden").getSpriteName());
			MagicRender.battlespace(
			enemySpr,
			vec,
			new Vector2f(0,0),
			new Vector2f(enemySpr.getWidth()*rate,enemySpr.getWidth()*rate),
			new Vector2f(0,0),		
			-10f,
			0f,
			new Color(0.5f*rate,0.5f*rate,0.5f*rate,1f),
			false,
			0f,
			0f,
			0f,
			0f,
			0f,
			0f,
			-1,
			0f,
			CombatEngineLayers.FRIGATES_LAYER
			);		
		}		
		if(!engine.isPaused())
		{		
			interval.advance(amount);
			attackInterval.advance(amount);

			if(attackInterval.intervalElapsed() && bgStage > 0.10f)
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
					if(warning && bgStage < 0.60f)
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
						false,
						0f,
						0f,
						0f,
						0f,
						0f,
						0f,
						-1,
						0f,
						CombatEngineLayers.BELOW_SHIPS_LAYER
						);	
					}
					else if(!warning && bgStage < 0.60f)
					{
						
						for (ShipAPI ship : engine.getShips())
						{
							if(ship.getOwner() != 1 || stationAlive)
								continue;
							if(!engine.getCustomData().containsKey("armaa_atmoWarningLoc"+i))
								continue;
							vec = (Vector2f)engine.getCustomData().get("armaa_atmoWarningLoc"+i);					
							engine.getFleetManager(1).spawnShipOrWing("warden_Defense",vec,270f,0f);
							engine.spawnExplosion(vec, new Vector2f(),shiftColor(new Color(0.29f,.1f,.1f,0.50f),new Color(1f,1f,1f,0.50f),bgStage), 250f, 1f);					
							//engine.spawnProjectile(ship,ship.getAllWeapons().get(0),"armaa_curvyLaser",vec,ship.getFacing(),ship.getVelocity());
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
				if(MagicRender.screenCheck(0.05f, ship.getLocation()) && interval.intervalElapsed() && !ship.isFighter())
				{				
					if(Math.random() <= 1f-ratio  && ratio >= 0.20f && ratio <= 0.60f)
						MagicRender.battlespace(
						Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
						MathUtils.getRandomPointInCircle(ship.getLocation(),ship.getCollisionRadius()),
						new Vector2f(MathUtils.getRandomNumberInRange(-ship.getCollisionRadius()*5,ship.getCollisionRadius()*5)+velX,(-MathUtils.getRandomNumberInRange(600,800)+velY)*(1f-ratio)),
						new Vector2f(MathUtils.getRandomNumberInRange(ship.getCollisionRadius()*2,ship.getCollisionRadius()*3),MathUtils.getRandomNumberInRange(ship.getCollisionRadius()*3f,ship.getCollisionRadius()*4f)),
						new Vector2f(ship.getCollisionRadius(),ship.getCollisionRadius()*3f),		
						0f,
						0f,
						shiftColor(getBrighterReentryColor(0.3f),getBrighterReentryColor(1f),bgStage+0.40f),
						true,
						0f,
						0f,
						0f,
						0f,
						0f,
						0.1f,
						Math.max(0.1f,(1f-ratio)-0.5f),
						0.1f,
						CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
						);					
					if(Math.random() <= 1f-ratio  && ratio >= 0.15f && ratio <= 0.70f)
						MagicRender.battlespace(
						Global.getSettings().getSprite("misc", "armaa_atmo_cloud"),
						MathUtils.getRandomPointInCircle(ship.getLocation(),ship.getCollisionRadius()),
						new Vector2f(MathUtils.getRandomNumberInRange(-ship.getCollisionRadius()*5,ship.getCollisionRadius()*5)+velX,(-MathUtils.getRandomNumberInRange(600,800)+velY)*(1f-ratio)),
						new Vector2f(MathUtils.getRandomNumberInRange(ship.getCollisionRadius(),ship.getCollisionRadius()*3),MathUtils.getRandomNumberInRange(ship.getCollisionRadius()*2f,ship.getCollisionRadius()*4f)),
						new Vector2f(ship.getCollisionRadius(),ship.getCollisionRadius()*3f),		
						0f,
						0f,
						shiftColor(getRandomBottomCloudColor(0.1f),new Color(0.5f,0.5f,0.5f,0f),bgStage+0.20f),
						false,
						0f,
						0f,
						0f,
						0f,
						0f,
						0.1f,
						Math.max(0.1f,(1f-ratio)-0.5f),
						0.1f,
						CombatEngineLayers.BELOW_SHIPS_LAYER
						);			
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
						if(!ship.isAlive())
							engine.removeEntity(ship);
						engine.getFleetManager(1).spawnShipOrWing("warden_Defense",new Vector2f(0,-7000),270f,0f);	
						engine.getFleetManager(1).spawnShipOrWing("warden_Defense",new Vector2f(0,-7000),270f,0f);		
						engine.getFleetManager(1).spawnShipOrWing("warden_Defense",new Vector2f(7000,-7000),270f,0f);	
						engine.getFleetManager(1).spawnShipOrWing("warden_Defense",new Vector2f(-7000,-7000),270f,0f);
						engine.getFleetManager(1).spawnShipOrWing("warden_Defense",new Vector2f(-7000,-6000),270f,0f);	
						engine.getFleetManager(1).spawnShipOrWing("warden_Defense",new Vector2f(-7000,-5000),270f,0f);	
						engine.getFleetManager(1).spawnShipOrWing("warden_Defense",new Vector2f(-7000,-4000),270f,0f);	
						
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
        float r = rand.nextFloat() * 0.15f+0.1f; // Random red value from 0 to 1
        float g = rand.nextFloat() * 0.05f; // Random green value from 0.35 to 1.0 for yellow-orange range
        float b = rand.nextFloat() * 0.05f; // Random blue value from 0 to 0.5 for warmer, less cool tones

        // Return the random color
        return new Color(r*ratio, g*ratio, b*ratio,0.9f-ratio);
    }
	
	// Method to generate a random color for a brighter, more vibrant reentry effect
	private Color getBrighterReentryColor(float ratio) {
		Random rand = new Random();
		// Define the range for fiery red-orange shades
		float r = 0.85f + rand.nextFloat() * 0.15f; // Bright red, close to the upper range
		float g = 0.3f + rand.nextFloat() * 0.3f;  // Moderate green for orange hues
		float b = 0.0f + rand.nextFloat() * 0.1f;  // Minimal blue to keep it warm

		// Return the color with adjusted alpha based on the ratio
		return new Color(r, g, b, 1.0f - ratio); 
	}	

    @Override
    public void init(CombatEngineAPI engine)
    {
        this.engine = engine;
    }
}

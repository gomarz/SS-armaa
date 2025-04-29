package data.hullmods;

import data.scripts.util.armaa_utils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import java.util.*;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import java.awt.Color;
import org.lwjgl.input.Keyboard;
import org.magiclib.util.MagicUI;
import org.magiclib.util.MagicRender;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.input.Keyboard;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.entities.AnchoredEntity;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;
import lunalib.lunaSettings.LunaSettings;
// Made by Timid
// Adulterated by Mayu
// Adulterated x2 by shoi
public class armaa_himac extends BaseHullMod {

    private static final String DATA_KEY = "armaa_booster_data";
	public static final float BOOST_COST = 5f;
	private static final Vector2f ZERO = new Vector2f();
    public static final float OVERLOAD_ENEMY_DURATION = 5f; //overload duration
    private static final float SUBSYSTEMCD = 20f; // cooldown after use
    public static final Color JITTER_COLOR = new Color(255,155,255,75);
	private static float A_2 = 45 / 2;
	// constant that effects the lower end of the particle velocity
	private static float VEL_MIN = 0.5f;
	// constant that effects the upper end of the particle velocity
	private static float VEL_MAX = 1f;	
    public static final Color JITTER_UNDER_COLOR = new Color(255,155,255,155);
    public static final boolean QUANTUMSETTINGS = false; //boolean for the HUD/status engine
    public boolean cooldownActive = false;
    public final String ID;
    private static final float TICK_TIME = 0.015f;
    private static final Map<HullSize, Float> SIZE_MULT = new HashMap<>();

    static {
		SIZE_MULT.put(HullSize.FIGHTER, 1f);
        SIZE_MULT.put(HullSize.FRIGATE, 1f);
		SIZE_MULT.put(HullSize.DESTROYER, 0.85f);
		SIZE_MULT.put(HullSize.CRUISER, 0.50f);
		SIZE_MULT.put(HullSize.CAPITAL_SHIP, 0.50f);
	}
    private float armaa_himacsubsys(final ShipAPI ship) {
        return SUBSYSTEMCD;
    }
    
    private static class armaa_himacdata {
        String subsysID = ""; // empty like my soul
        boolean runOnce = false;
        boolean holdButtonBefore = false;
        float activeTime = 0f;
        float maxActiveTime = 0f;
        float cooldown = 25f;
        float maxcooldown = 0f;
		boolean keyPressed = false;
		boolean window = false;
		long startTime = System.currentTimeMillis();
		boolean moveKeyPressed = false;
		public String lastKeyPressed = null;
		public long lastKeyTime = 0;		
		boolean boostEnabled = false;		
		boolean assaultBoostEnabled = false;
		boolean assaultBoostCharging = false;
		boolean assaultBoostCharged = false;		
		boolean burnedOut = false;
		IntervalUtil chargeInterval = new IntervalUtil(1.5f, 1.5f);
        IntervalUtil tracker = new IntervalUtil(5f, 5f);
		IntervalUtil aiTracker = new IntervalUtil(1f, 1f);
		IntervalUtil smokeTracker = new IntervalUtil(0.25f, 0.25f);			
		
    }
    
    public armaa_himac() {
        this.ID = "ArmaaHIMACThrusters";
    }
	
    private static float getSystemEngineScale(ShipEngineAPI engine, float direction) {
        float engAngle = engine.getEngineSlot().getAngle();
        if (Math.abs(MathUtils.getShortestRotation(engAngle, direction)) > 100f) {
            return 1f;
        } else {
            return 0f;
        }
    }

	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) 
	{
		stats.getEngineDamageTakenMult().modifyMult(id,1f+0.50f); 
	}

	public void assaultBoostCharge(ShipAPI ship, ShipEngineAPI eng, float chargeLevel)
	{
        String key = DATA_KEY + "_" + ship.getId();
        armaa_himacdata data = (armaa_himacdata) Global.getCombatEngine().getCustomData().get(key);
		if(chargeLevel < 1)
		{
			armaa_utils.createChargeParticle(chargeLevel, eng, ship);
		}
		else
		{
			data.assaultBoostCharged = true;
			data.assaultBoostCharging = false;
			Color ENGINE_COLOR = ship.getEngineController().getShipEngines().get(0).getEngineColor();
			Color CONTRAIL_COLOR = new Color(100, 100, 100, 25);
			Color BOOST_COLOR = new Color(255, 175, 175, 200);		
			Color bigBoostColor = new Color(
					armaa_utils.clamp255(Math.round(0.1f * ENGINE_COLOR.getRed())),
					armaa_utils.clamp255(Math.round(0.1f * ENGINE_COLOR.getGreen())),
					armaa_utils.clamp255(Math.round(0.1f * ENGINE_COLOR.getBlue())),
					armaa_utils.clamp255(Math.round(0.3f * ENGINE_COLOR.getAlpha() * chargeLevel)));
				float VEL_MIN = 0.2f;
				// constant that effects the upper end of the particle velocity
				float CONE_ANGLE = 200f;
				float A_2 = CONE_ANGLE / 2;
				float VEL_MAX = 0.4f;		
				float speed = 450f;
				float facing = ship.getFacing();
				float angle = MathUtils.getRandomNumberInRange(facing - A_2,
						facing + A_2);
				float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
						speed * -VEL_MAX);
				Vector2f vector = MathUtils.getPointOnCircumference(null,
						vel,
						angle);		
			if(MagicRender.screenCheck(0.1f, ship.getLocation()))
			{								
				Global.getCombatEngine().spawnExplosion(eng.getLocation(), vector, bigBoostColor,
							20f * eng.getEngineSlot().getWidth(), 1.25f);
				Global.getCombatEngine().spawnExplosion(eng.getLocation(), vector, Color.black,
							25f * eng.getEngineSlot().getWidth(), 2f);
				Global.getCombatEngine().addNebulaSmoothParticle(eng.getLocation(), vector, 15f* eng.getEngineSlot().getWidth(), 1f, 0.5f, 0.25f, 1f, new Color(0.7f,0.7f,0.7f,0.8f), true);
				
				Global.getCombatEngine().spawnExplosion(eng.getLocation(), new Vector2f(), Color.black,
							25f * eng.getEngineSlot().getWidth(), 2f);
				Global.getCombatEngine().addHitParticle(
						eng.getLocation(),
						new Vector2f(),
						MathUtils.getRandomNumberInRange(50f, 100f),
						1f, //1f*effectLevel
						MathUtils.getRandomNumberInRange(0.1f, 0.2f),
						Color.white);
				Global.getCombatEngine().spawnExplosion(eng.getLocation(), new Vector2f(), bigBoostColor,
							30f * eng.getEngineSlot().getWidth(), 1.5f);
				Global.getCombatEngine().addNebulaSmoothParticle(eng.getLocation(), vector, 25f* eng.getEngineSlot().getWidth(), 1f, 0.5f, 0.25f, 1f, bigBoostColor, true);
			}			
			Global.getSoundPlayer().playSound("system_orion_device_explosion", 1.1f, 1f, ship.getLocation(), new Vector2f());
						
			boost(ship.getFacing()+0f,ship,true);
		}
		
	}
	
	public void createBoostParticles(ShipAPI ship, boolean assaultBoost)
	{
		float INSTANT_BOOST_FLAT = 300f;
		float INSTANT_BOOST_MULT = 5f;
        float boostVisualDir = 0f;		
        float shipRadius = armaa_utils.effectiveRadius(ship);		
		float duration = (float) Math.sqrt(shipRadius) / 25f;		
		Vector2f direction = new Vector2f();
		float boostScale = 0.75f;
		
		if (ship.getEngineController().isAccelerating()) {
			direction.y += 0.75f - 0.35f;
			boostScale -= 0.35f;
		} else if (ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) {
			direction.y -=  0.75f - 0.45f;
			boostScale -= 0.45f;
		}
		if (ship.getEngineController().isStrafingLeft()) {
			direction.x -= 1f;
			boostScale += 1f - 0.75f;
		} else if (ship.getEngineController().isStrafingRight()) {
			direction.x += 1f;
			boostScale += 1f - 0.75f;
		}
		if (direction.length() <= 0f) {
			direction.y = 0.75f - 0.35f;
			boostScale -= 0.35f;
		}
		Misc.normalise(direction);
		VectorUtils.rotate(direction, ship.getFacing() - 90f, direction);
		direction.scale(((ship.getMaxSpeedWithoutBoost() * INSTANT_BOOST_MULT) + INSTANT_BOOST_FLAT) * boostScale);

		boostVisualDir = MathUtils.clampAngle(VectorUtils.getFacing(direction) - 90f);
		Color ENGINE_COLOR = ship.getEngineController().getFlameColorShifter().getCurr().getAlpha() == 0 ? ship.getEngineController().getShipEngines().get(0).getEngineColor() : ship.getEngineController().getFlameColorShifter().getCurr();		
		//Color ENGINE_COLOR = ship.getEngineController().getShipEngines().get(0).getEngineColor();
        Color CONTRAIL_COLOR = new Color(100, 100, 100, 25);
        Color BOOST_COLOR = new Color(255, 175, 175, 200);		
		for (ShipEngineAPI eng : ship.getEngineController().getShipEngines()) 
		{
			float level = 1f;
			if (eng.isSystemActivated()) {
				level = getSystemEngineScale(eng, boostVisualDir);
			}
			if ((eng.isActive() || eng.isSystemActivated()) && (level > 0f)) {
				Color bigBoostColor = new Color(
						armaa_utils.clamp255(Math.round(0.1f * ENGINE_COLOR.getRed())),
						armaa_utils.clamp255(Math.round(0.1f * ENGINE_COLOR.getGreen())),
						armaa_utils.clamp255(Math.round(0.1f * ENGINE_COLOR.getBlue())),
						armaa_utils.clamp255(Math.round(0.3f * ENGINE_COLOR.getAlpha() * level)));
				Color boostColor = new Color(BOOST_COLOR.getRed(), BOOST_COLOR.getGreen(), BOOST_COLOR.getBlue(),
						armaa_utils.clamp255(Math.round(BOOST_COLOR.getAlpha() * level)));
				Global.getCombatEngine().spawnExplosion(eng.getLocation(), ZERO, bigBoostColor,
						2f * 4f * boostScale * eng.getEngineSlot().getWidth(), duration*1.5f);
				Global.getCombatEngine().spawnExplosion(eng.getLocation(), ZERO, boostColor,
						1f * 2f * boostScale * eng.getEngineSlot().getWidth(), 0.15f);
						
				float VEL_MIN = 0.2f;
				// constant that effects the upper end of the particle velocity
				float CONE_ANGLE = 200f;
				float A_2 = CONE_ANGLE / 2;
				float VEL_MAX = 0.4f;		
				float speed = 450f;
				float facing = ship.getFacing();
				float angle = MathUtils.getRandomNumberInRange(facing - A_2,
						facing + A_2);
				float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
						speed * -VEL_MAX);
				Vector2f vector = MathUtils.getPointOnCircumference(null,
						vel,
						angle);						
				Vector2f origin = new Vector2f(eng.getLocation());
				Vector2f offset = new Vector2f(-5f, -0f);
				VectorUtils.rotate(offset, ship.getFacing(), offset);
				Vector2f.add(offset, origin, origin);								

			   Global.getCombatEngine().addHitParticle(
						origin,
						vector,
						MathUtils.getRandomNumberInRange(2, 5),
						1f, //1f*effectLevel
						MathUtils.getRandomNumberInRange(0.4f, 0.8f),
						BOOST_COLOR);
			}
		}

		float soundScale = (float) Math.sqrt(boostScale);
		switch (ship.getHullSize()) {
			case FIGHTER:
				Global.getSoundPlayer().playSound("mechmove", 1.1f, 0.6f * soundScale, ship.getLocation(), ZERO);
				break;
			case FRIGATE:
				Global.getSoundPlayer().playSound("mechmove", .8f, 1.1f * soundScale, ship.getLocation(), ZERO);
				break;
			default:
			case DESTROYER:
				Global.getSoundPlayer().playSound("mechmove", 0.9f, 1.1f * soundScale, ship.getLocation(), ZERO);
				break;
			case CRUISER:
				Global.getSoundPlayer().playSound("mechmove", 0.8f, 1.2f * soundScale, ship.getLocation(), ZERO);
				break;
			case CAPITAL_SHIP:
				Global.getSoundPlayer().playSound("mechmove", 0.7f, 1.3f * soundScale, ship.getLocation(), ZERO);
				break;
		}		
	}
	
    public void boost(float angleDegrees, ShipAPI ship, boolean assaultBoost) 
	{
		String id = "armaa_assaultBoost_"+ship.getId();
		float bonus = assaultBoost ? 1f:2f;
		float modifier = assaultBoost ? 0.15f:1f;
        String key = DATA_KEY + "_" + ship.getId();
        armaa_himacdata data = (armaa_himacdata) Global.getCombatEngine().getCustomData().get(key);
		if(assaultBoost)
			data.cooldown-= (BOOST_COST/Math.max(1,ship.getEngineController().getShipEngines().size()))*modifier;
		
		else data.cooldown-=BOOST_COST*modifier;
		if(MagicRender.screenCheck(0.1f, ship.getLocation()))
		{
			createBoostParticles(ship,assaultBoost);
		}
		data.boostEnabled = false;
		data.assaultBoostCharged = false;		
		if(assaultBoost)
		{
			data.assaultBoostEnabled = true;
		}

		if(data.assaultBoostEnabled && !assaultBoost)
		{
			ship.getMutableStats().getMaxTurnRate().modifyMult(id, 1.25f);
			ship.getMutableStats().getTurnAcceleration().modifyMult(id, 1.25f);		
		}
		if(data.cooldown < 0)
		{
			ship.getEngineController().forceFlameout();
			data.burnedOut = true;
			Global.getSoundPlayer().playSound("disabled_small_crit",1f,1f,ship.getLocation(),ship.getVelocity());
			data.cooldown = 0;
			return;
		}
		CombatUtils.applyForce(ship, angleDegrees, (ship.getMaxSpeed()+(ship.getMass()*SIZE_MULT.get(ship.getHullSize()))*bonus));
    }	
 
	public void isKeyPressed(ShipAPI ship) 
	{
		boolean wPressed = Keyboard.isKeyDown(Keyboard.getKeyIndex("W"));
		boolean aPressed = Keyboard.isKeyDown(Keyboard.getKeyIndex("A"));
		boolean sPressed = Keyboard.isKeyDown(Keyboard.getKeyIndex("S"));
		boolean dPressed = Keyboard.isKeyDown(Keyboard.getKeyIndex("D"));

		String key = DATA_KEY + "_" + ship.getId();
		armaa_himacdata data = (armaa_himacdata) Global.getCombatEngine().getCustomData().get(key);

		// Check if *any* movement key is pressed
		data.keyPressed = wPressed || aPressed || sPressed || dPressed;

		// Disable assault boost if moving backward
		if (sPressed && data.assaultBoostEnabled) {
			data.assaultBoostEnabled = false;
		}

		// Handle W blocking when charging
		if (wPressed && data.assaultBoostCharging && !data.assaultBoostCharged) {
			// ignore W in pressed key logic
		} else if (!data.boostEnabled) {
			// If a movement key was just pressed
			if (!data.moveKeyPressed && data.keyPressed) {
				long now = System.currentTimeMillis();
				int inputTime = 300;

				if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
					inputTime = LunaSettings.getInt("armaa", "armaa_inputTime");
				}

				String currentKey = null;
				if (wPressed) currentKey = "W";
				else if (aPressed) currentKey = "A";
				else if (sPressed) currentKey = "S";
				else if (dPressed) currentKey = "D";

				if (currentKey != null) {
					// Double tap check: same key pressed again within time
					if (currentKey.equals(data.lastKeyPressed) && now - data.lastKeyTime < inputTime) {
						if ("W".equals(currentKey)) {
							if (!data.assaultBoostCharging && !data.assaultBoostCharged && !data.assaultBoostEnabled) {
								data.assaultBoostCharging = true;
								data.boostEnabled = false;
							}
						} else if ("A".equals(currentKey)) {
							boost(ship.getFacing() + 90f, ship, false);
						} else if ("S".equals(currentKey)) {
							boost(ship.getFacing() - 180f, ship, false);
						} else if ("D".equals(currentKey)) {
							boost(ship.getFacing() - 90f, ship, false);
						}
					} else {
						// Record new key and time
						data.lastKeyPressed = currentKey;
						data.lastKeyTime = now;
					}
				}
				data.moveKeyPressed = true;
			}

			if (!data.keyPressed) {
				data.moveKeyPressed = false;
			}
		}

		Global.getCombatEngine().getCustomData().put(key, data);
	}

 
    @Override
    public void advanceInCombat(ShipAPI ship, final float amount) {
        final CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || !engine.isEntityInPlay(ship) || !ship.isAlive()) {
            return;
        }
        String key = DATA_KEY + "_" + ship.getId();
		String id = "armaa_assaultBoost_"+ship.getId();		
        armaa_himacdata data = (armaa_himacdata) engine.getCustomData().get(key);
        if (data == null) {
            data = new armaa_himacdata();
            engine.getCustomData().put(key, data);
        }
        if (!data.runOnce) {
            data.runOnce = true;
            data.subsysID = this.getClass().getName() + "_" + ship.getId();
            data.maxcooldown = armaa_himacsubsys(ship);
            data.maxActiveTime = OVERLOAD_ENEMY_DURATION; // the duration of enemy overload
        }
        if (!data.assaultBoostEnabled && data.cooldown < data.maxcooldown && data.activeTime <= 0f && ship.getCurrentCR() > 0f && !ship.getFluxTracker().isOverloadedOrVenting()) {
			float bonus = 1f;
			if(data.burnedOut)
				bonus = 0.5f;
            data.cooldown += amount*bonus;
        }
		
		if(!data.burnedOut && !ship.getFluxTracker().isOverloaded() && !ship.getEngineController().isFlamedOut())
		{
			if(Global.getCombatEngine().getPlayerShip() == ship)		
				isKeyPressed(ship);
				if(data.assaultBoostCharging && !data.assaultBoostCharged)
				{
					float level = data.chargeInterval.getElapsed()/data.chargeInterval.getIntervalDuration();
					if(Global.getCombatEngine().getPlayerShip() == ship)	
						Global.getCombatEngine().maintainStatusForPlayerShip(
								"dasdsadsad",
								"graphics/icons/hullsys/burn_drive.png",
								"ASSAULT BOOST",
								"Charging.. " + level*100,
								true
						);		
					for (ShipEngineAPI eng : ship.getEngineController().getShipEngines()) 
					{
						if(eng.isActive())
							assaultBoostCharge(ship,eng,level);				
					}
					data.chargeInterval.advance(amount);				
				}
		}
		
		else if(data.burnedOut)
		{
			data.tracker.advance(amount);
			if(data.assaultBoostEnabled)
				data.assaultBoostEnabled = false;
			
			if(Global.getCombatEngine().getPlayerShip() == ship)	
				engine.maintainStatusForPlayerShip(
						"dasdsadsad",
						"graphics/icons/hullsys/quantum_disruptor.png",
						"IVR-VI BOOSTER OVERHEAT",
						"Recovering.. " + (data.tracker.getElapsed()/data.tracker.getIntervalDuration())*100,
						true
				);
				data.smokeTracker.advance(amount);
				if(MagicRender.screenCheck(0.1f, ship.getLocation()))
					for (ShipEngineAPI eng : ship.getEngineController().getShipEngines()) 
					{
						float CHARGEUP_PARTICLE_ANGLE_SPREAD = 90f;
						float CHARGEUP_PARTICLE_BRIGHTNESS = 1f;
						float CHARGEUP_PARTICLE_DISTANCE_MAX = 200f;
						float CHARGEUP_PARTICLE_DISTANCE_MIN = 100f;
						float CHARGEUP_PARTICLE_DURATION = 0.5f;
						float CHARGEUP_PARTICLE_SIZE_MAX = 5f;
						float CHARGEUP_PARTICLE_SIZE_MIN = 1f;
						float charge = Math.max(0f,1-data.tracker.getElapsed()/data.tracker.getIntervalDuration());								
						float facing = eng.getEngineSlot().getAngle()+ship.getFacing();
						float distance = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_DISTANCE_MIN+1f,
						CHARGEUP_PARTICLE_DISTANCE_MAX+1f)
						* charge;
						//private WeaponAPI weapon;							
						float speed = 1f * distance / CHARGEUP_PARTICLE_DURATION*charge;

						float angle = MathUtils.getRandomNumberInRange(facing - A_2,
						facing + A_2);
						float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
						speed * -VEL_MAX);
						Vector2f particleVelocity = MathUtils.getPointOnCircumference(ship.getVelocity(), vel, angle);
						Vector2f vector = MathUtils.getPointOnCircumference(ship.getVelocity(),
						vel,
						angle+180f);

						float size = MathUtils.getRandomNumberInRange(CHARGEUP_PARTICLE_SIZE_MIN+1f, CHARGEUP_PARTICLE_SIZE_MAX+3f)*charge;
						// float angle = MathUtils.getRandomNumberInRange(-0.5f * CHARGEUP_PARTICLE_ANGLE_SPREAD, 0.5f
						//       * CHARGEUP_PARTICLE_ANGLE_SPREAD);

						Global.getCombatEngine().addNebulaSmoothParticle(eng.getLocation(), vector, size*eng.getEngineSlot().getWidth()/2, 1f, 0.5f, 1f, 0.5f, new Color(0.6f*charge,0.6f*charge,0.6f*charge,0.9f*charge), true);
			
					}				
			if(data.tracker.intervalElapsed())
				data.burnedOut = false;
		}
		if(data.assaultBoostEnabled)
		{
			if(ship.getFluxTracker().isOverloaded() || ship.getEngineController().isFlamedOut())
				data.assaultBoostEnabled = false;
			
			if(ship.getShield() != null)
				ship.getShield().toggleOff();
			Global.getSoundPlayer().playLoop("system_plasma_jets_loop",ship,1f,1f,ship.getLocation(),new Vector2f());
			ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
			//ship.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT);
			//ship.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT);
			ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
			ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
			ship.getEngineController().extendFlame(new Object(), 2f, 2f, 1.5f);	

			ship.getMutableStats().getMaxTurnRate().modifyMult(id, 0.35f);
			ship.getMutableStats().getTurnAcceleration().modifyMult(id, 0.35f);
			ship.getMutableStats().getMaxSpeed().modifyFlat(id, 100f);
			ship.getMutableStats().getAcceleration().modifyMult(id, 0.5f);
			
			ship.getMutableStats().getBallisticWeaponDamageMult().modifyMult(id,1.15f);
			ship.getMutableStats().getEnergyWeaponDamageMult().modifyMult(id,1.15f);
			
			ship.getMutableStats().getHullDamageTakenMult().modifyMult(id, 1f - (1f - .5f));
			ship.getMutableStats().getArmorDamageTakenMult().modifyMult(id, 1f - (1f - .5f));
			ship.getMutableStats().getEmpDamageTakenMult().modifyMult(id, 1f - (1f - .5f));		
			data.cooldown-=amount*2;			
			if((float) Math.random() >= 0.7f)
				for(int i=0; i<ship.getEngineController().getShipEngines().size(); i++)
				{
					Color ENGINE_COLOR = ship.getEngineController().getFlameColorShifter().getCurr().getAlpha() == 0 ? ship.getEngineController().getShipEngines().get(0).getEngineColor() : ship.getEngineController().getFlameColorShifter().getCurr();
					float speed = 200f;
					float facing = ship.getEngineController().getShipEngines().get(i).getEngineSlot().getAngle()+ship.getFacing();
					float angle = MathUtils.getRandomNumberInRange(facing - A_2,
							facing + A_2);
					float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
							speed * -VEL_MAX);
					Vector2f vector = MathUtils.getPointOnCircumference(null,
							vel,
							angle+180f);
							
					Vector2f origin = new Vector2f(ship.getEngineController().getShipEngines().get(i).getLocation());
					Vector2f offset = new Vector2f(-5f, -0f);
					VectorUtils.rotate(offset, ship.getFacing(), offset);
					Vector2f.add(offset, origin, origin);								

				   Global.getCombatEngine().addHitParticle(
							origin,
							vector,
							MathUtils.getRandomNumberInRange(2, 8),
							1f,
							MathUtils.getRandomNumberInRange(0.4f, 0.8f),
							ENGINE_COLOR);

				}		
			if(data.cooldown<= 0 || ship.isLanding())
			{
				data.assaultBoostEnabled = false;
			}						
		}

		if(data.assaultBoostEnabled == false)
		{
			ship.getMutableStats().getMaxTurnRate().unmodify(id);
			ship.getMutableStats().getTurnAcceleration().unmodify(id);
			ship.getMutableStats().getMaxSpeed().unmodify(id);
			ship.getMutableStats().getAcceleration().unmodify(id);	
			
			ship.getMutableStats().getBallisticWeaponDamageMult().unmodify(id);
			ship.getMutableStats().getEnergyWeaponDamageMult().unmodify(id);	

			ship.getMutableStats().getHullDamageTakenMult().unmodify(id);
			ship.getMutableStats().getArmorDamageTakenMult().unmodify(id);
			ship.getMutableStats().getEmpDamageTakenMult().unmodify(id);
			
		}			
        if (engine.getPlayerShip() == ship) {
            if (data.burnedOut  || data.cooldown <= 10f) {
                // custom magic UI/Status goes here below
                if (QUANTUMSETTINGS) {

                } else {
                    MagicUI.drawHUDStatusBar(
                            ship,
                            data.cooldown/data.maxcooldown,
                            Misc.getNegativeHighlightColor(),
                            Misc.getNegativeHighlightColor(),
                            0,
                            "Charge",
                            "",
                            false
                    );
                }
            } else {
                if (QUANTUMSETTINGS) {

                } else {
                    MagicUI.drawHUDStatusBar(
                            ship,
                            data.cooldown/data.maxcooldown,
                            Misc.getPositiveHighlightColor(),
                            Misc.getPositiveHighlightColor(),
                            0,
                            "Charge",
                            "",
                            false
                    );
                }
            }
        }
        // AI Conditions goes here, oh god I hate it already
//        List<ShipAPI> enemyShip = AIUtils.getNearbyEnemies(ship, 500f); // range to scan nearby enemies
        boolean player = false;
        player = ship == Global.getCombatEngine().getPlayerShip();
        if (ship.getAI() != null ) {
            final ShipwideAIFlags flags = ship.getAIFlags();
            data.aiTracker.advance(amount);
            if (data.aiTracker.intervalElapsed() && data.cooldown >= 5f && !ship.getFluxTracker().isOverloaded()) {
				if(!player && ship.getHullLevel() >= 0.5f)
				{
					if((flags.hasFlag(AIFlags.MOVEMENT_DEST) && !ship.areAnyEnemiesInRange() || flags.hasFlag(AIFlags.PURSUING))   && !data.assaultBoostEnabled && data.cooldown >= 5f)
					{
						data.assaultBoostCharging = true;
					}
					
					if(data.assaultBoostEnabled && flags.hasFlag(AIFlags.HAS_INCOMING_DAMAGE))
						data.assaultBoostEnabled = false;
					if(ship.getEngineController().isDecelerating() && data.assaultBoostEnabled)
						data.assaultBoostEnabled = false;
					
					List<DamagingProjectileAPI> possibleTargets = new ArrayList<>(100);
					possibleTargets.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(), 500f));
					possibleTargets.addAll(CombatUtils.getProjectilesWithinRange(ship.getLocation(), 500f));
					if(armaa_utils.estimateIncomingDamage(ship) < 100)
						return;						
					for(DamagingProjectileAPI proj : possibleTargets)
					{
						if(proj == null)
							continue;
						if(armaa_utils.getHitChance(proj,ship) > 0.8f)
						{
							if (proj.getLocation().getX() < ship.getLocation().getX()) {
								boost(ship.getFacing()+90f,ship,false);
								break;
							} else if (proj.getLocation().getX() > ship.getLocation().getX()) {
								boost(ship.getFacing()-90f,ship,false);
								break;
							}
							else
							{
								boost(ship.getFacing()-180f,ship,false);
								break;
							}							
						}
					}
				}
            }
        }
		Global.getCombatEngine().getCustomData().put(key,data);
    }

    @Override
    public String getDescriptionParam(final int index, final HullSize hullSize) {
        return null;
    }

    @Override
    public void addPostDescriptionSection(final TooltipMakerAPI tooltip, final ShipAPI.HullSize hullSize, final ShipAPI ship, final float width, final boolean isForModSpec) {
        if (isForModSpec || ship == null) {
            return;
        }   
        tooltip.addSectionHeading("Subsystem", Alignment.MID, 10f);
        final TooltipMakerAPI text2 = tooltip.beginImageWithText("graphics/icons/hullsys/maneuvering_jets.png", 40f);
        text2.addPara("RAM-VI Velocity Thrusters", 0f, Global.getSettings().getColor("tooltipTitleAndLightHighlightColor"), "RAM-VI Velocity Thrusters");
        text2.addPara("Rapidly boost in any lateral direction using the [%s] keys. If the system is used with no charge remaining, the craft will flame out, preventing it from using this system again for a short period of time.",
                0f, Misc.getHighlightColor(),
                new String[] {
					"ASD",
                    Misc.getRoundedValue(5.0f),
                    Misc.getRoundedValue(30.0f)
                });
		text2.addPara("%s " + "Engine health reduced by %s",10f, Misc.getNegativeHighlightColor(),"\u2022","50%");	
        tooltip.addImageWithText(10f);
        TooltipMakerAPI text3 = tooltip.beginImageWithText("graphics/icons/hullsys/burn_drive.png", 40f);
		tooltip.addSectionHeading("Assault Boost", Alignment.MID, 10f);
        text3.addPara("Initiate an 'Assault Boost' for increased forward speed and weapon damage with the [%s] button.",
                0f, Misc.getHighlightColor(),
                new String[] {
					"W",					
					"20%",
                    "100",
                    Misc.getRoundedValue(75.0f),
					"50%"					
                });
		text3.addPara("%s " + "Increases non-missile damage by %s",10f, Misc.getPositiveHighlightColor(),"\u2022","15%");					
		text3.addPara("%s " + "Increases speed by a flat %s SU.",10f, Misc.getPositiveHighlightColor(),"\u2022","100");	
		text3.addPara("%s " + "Damage resistance is increased by %s while active.",10f, Misc.getPositiveHighlightColor(),"\u2022","50%");		
		text3.addPara("%s " + "Reduces turning speed by %s",10f, Misc.getNegativeHighlightColor(),"\u2022","65%");	
		text3.addPara("%s " + "Disables shields.",10f, Misc.getNegativeHighlightColor(),"\u2022","shields","strafing","deceleration");		
		tooltip.addImageWithText(10f);
        tooltip.addPara("%s", 6f, Misc.getGrayColor(), new String[] { "\"I won't stop! I'll chase the clouds from over Mazalot. Only I can fly high enough!\"" }).italicize();
        tooltip.addPara("%s", 1f, Misc.getGrayColor(), new String[] { "         \u2014 ???" });    
    }

	@Override	
	public boolean isApplicableToShip(ShipAPI ship) 
	{
		return true;
	}

	public String getUnapplicableReason(ShipAPI ship) 
	{
        if (ship == null) 
			return "";
		
		return "Only installable on destroyer-sized ships or smaller.";
	}	
}
package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.util.armaa_utils;
import java.awt.Color;
import org.lazywizard.lazylib.FastTrig;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicRender;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.entities.AnchoredEntity;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.combat.CollisionClass;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import java.util.concurrent.ThreadLocalRandom;


/* Majority of this code by DarkRevenant - all credits to him for allowing me to use it*/
public class armaa_SilverSysStats extends BaseShipSystemScript {

    public static final float GAUGE_DRAIN_TIME = 10f;
    public static final float GAUGE_REGEN_TIME = 35f;
    public static final float COOLDOWN_MIN = 0.25f;
    public static final float COOLDOWN_MAX = 1f;
    public static final float ROF_BONUS = 40f;
	public static final float DISSIPATION_MULT = 2f;
	public static final float CAPACITY_MULT = 1.25f;
    public static final float SPEED_BONUS = 40f;
    public static final Map<HullSize, Float> SPEED_FLAT_BONUS = new HashMap<>();
	public static final float MAX_TIME_MULT = 1.1f;
	public static final float MIN_TIME_MULT = 0.1f;
	public static final float ACCEL_BONUS = 50f;
    public static final float ACCEL_FLAT_BONUS = 30f;
    public static final float DECEL_BONUS = 40f;
    public static final float DECEL_FLAT_BONUS = 20f;
    public static final float TURN_ACCEL_BONUS = 200f;
    public static final float MAX_TURN_BONUS = 40f;
    public static final Map<HullSize, Float> MAX_TURN_FLAT_BONUS = new HashMap<>();
    public static final float AUTOAIM_BONUS = 1f / 3f;
    public static final float RECOIL_MULT = 2f / 3f;
    public static final float OVERLOAD_DUR = GAUGE_DRAIN_TIME * 0.8f;
    public static final float CR_LOSS_MULT = 3f;
    public static final float OVER_GAUGE_LEVEL = 1f / 3f;
	private static final float RANGE_THRESHOLD = 450f;
	private static final float RANGE_MULT = 0.25f;
    public static final float MAX_OVERLEVEL = 3f;
	private static final float AFTERIMAGE_THRESHOLD = 0.2f;
	//
	private final Color PARTICLE_COLOR = new Color(255, 166, 155);
	private final float PARTICLE_SIZE = 10f;
	private final float PARTICLE_BRIGHTNESS = 150f;
	private final float PARTICLE_DURATION = 1f;
	private final float EXPLOSION_SIZE_OUTER = 200f;
	private  final float EXPLOSION_SIZE_INNER = 100f;
	private final float EXPLOSION_DAMAGE_MAX = 500f;
	private final float EXPLOSION_DAMAGE_MIN = 250f;
	private final float EXPLOSION_DURATION = 0.05f;
	private final float PARTICLE_DURATION2 = 0.2f;
	private final Color VFX_COLOR = new Color(149, 206, 240, 200);
	private final int PARTICLE_COUNT = 50;
	private final int PARTICLE_SIZE_MIN = 8;
	private final int PARTICLE_SIZE_RANGE = 3;
	//
	//Internal timer
	IntervalUtil tracker = new IntervalUtil(4f,10f);
    //private float tracker = 0f;
	private static final float SMOKE_PARTICLES_PER_SECOND = 25f;
    private static final float SMOKE_SPEED = 15f;
    private static final float SMOKE_ANGLE = 0f;
    private static final float SMOKE_SCATTER = 16f;
    private static final float SMOKE_OPACITY = 0.4f;
    private static final Color SMOKE_COLOR = new Color(255, 255, 255);
    private static final float SMOKE_MAX_DURATION = 0.5f;
    private static final float SMOKE_MIN_DURATION = 0.3f;
    private static final float SMOKE_MAX_SIZE = 10f;
    private static final float SMOKE_MIN_SIZE = 5f;
	//


    static {
		SPEED_FLAT_BONUS.put(HullSize.FIGHTER, 60f);
        SPEED_FLAT_BONUS.put(HullSize.FRIGATE, 50f);
        SPEED_FLAT_BONUS.put(HullSize.DESTROYER, 40f);
        SPEED_FLAT_BONUS.put(HullSize.CRUISER, 35f);
        SPEED_FLAT_BONUS.put(HullSize.CAPITAL_SHIP, 30f);

		MAX_TURN_FLAT_BONUS.put(HullSize.FIGHTER, 60f);
        MAX_TURN_FLAT_BONUS.put(HullSize.FRIGATE, 50f);
        MAX_TURN_FLAT_BONUS.put(HullSize.DESTROYER, 40f);
        MAX_TURN_FLAT_BONUS.put(HullSize.CRUISER, 30f);
        MAX_TURN_FLAT_BONUS.put(HullSize.CAPITAL_SHIP, 20f);
    }

    private static final float TICK_TIME = 0.015f;
    private static final Map<HullSize, Float> EXTEND_TIME = new HashMap<>();
    private static final Map<HullSize, Float> BASE_SPARK_CHANCE_PER_TICK = new HashMap<>();
    private static final Map<HullSize, Integer> SPARKS_ON_OVERLOAD = new HashMap<>();

    static {
		EXTEND_TIME.put(HullSize.FIGHTER, 0.75f);
        EXTEND_TIME.put(HullSize.FRIGATE, 0.1f);
        EXTEND_TIME.put(HullSize.DESTROYER, 0.125f);
        EXTEND_TIME.put(HullSize.CRUISER, 0.15f);
        EXTEND_TIME.put(HullSize.CAPITAL_SHIP, 0.175f);

		BASE_SPARK_CHANCE_PER_TICK.put(HullSize.FIGHTER, TICK_TIME * 2.5f);
        BASE_SPARK_CHANCE_PER_TICK.put(HullSize.FRIGATE, TICK_TIME * 3f);
        BASE_SPARK_CHANCE_PER_TICK.put(HullSize.DESTROYER, TICK_TIME * 3.5f);
        BASE_SPARK_CHANCE_PER_TICK.put(HullSize.CRUISER, TICK_TIME * 4f);
        BASE_SPARK_CHANCE_PER_TICK.put(HullSize.CAPITAL_SHIP, TICK_TIME * 4.5f);

		SPARKS_ON_OVERLOAD.put(HullSize.FIGHTER, 2);
        SPARKS_ON_OVERLOAD.put(HullSize.FRIGATE, 4);
        SPARKS_ON_OVERLOAD.put(HullSize.DESTROYER, 6);
        SPARKS_ON_OVERLOAD.put(HullSize.CRUISER, 8);
        SPARKS_ON_OVERLOAD.put(HullSize.CAPITAL_SHIP, 10);
    }

    private static final Color ENGINE_COLOR_STANDARD = new Color(255, 50, 50, 220);
    private static final Color CONTRAIL_COLOR_STANDARD = new Color(255, 50, 50, 100);
    private static final Color OVERLOAD_COLOR_STANDARD = new Color(255,50,150,150);
    private static final Color ENGINE_COLOR_ARMOR = new Color(205, 150, 100, 200);
    private static final Color CONTRAIL_COLOR_ARMOR = new Color(80, 75, 40, 90);
    private static final Color OVERLOAD_COLOR_ARMOR = new Color(205, 150, 50);
    private static final Color ENGINE_COLOR_TARGETING = new Color(50, 220, 255, 220);
    private static final Color CONTRAIL_COLOR_TARGETING = new Color(40, 70, 80, 100);
    private static final Color OVERLOAD_COLOR_TARGETING = new Color(10, 110, 205);
    private static final Color ENGINE_COLOR_ELITE = new Color(145, 50, 255, 220);
    private static final Color CONTRAIL_COLOR_ELITE = new Color(60, 40, 80, 100);
    private static final Color OVERLOAD_COLOR_ELITE = new Color(100, 10, 205);

    private static final Vector2f ZERO = new Vector2f();
    private static final String DATA_KEY_ID = "armaa_SilverSysStats";
    private final Object STATEKEY = new Object();
    private final Object ENGINEKEY1 = new Object();
    private final Object ENGINEKEY2 = new Object();

    private final Map<Integer, Float> engState = new HashMap<>();

    private boolean activated = false;
    private boolean deactivated = false;
    private boolean shutdown = false;
	//private boolean systemOff;

    private float totalPeakTimeLoss = 0f;
    private float tempGauge = 0f;
    private HullSize tempSize = HullSize.FRIGATE;
    private boolean unbugify = false;
    private final IntervalUtil interval = new IntervalUtil(TICK_TIME, TICK_TIME);

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        final ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) {
            return;
        }
		boolean player = false;
		player = ship == Global.getCombatEngine().getPlayerShip();

        Object data = Global.getCombatEngine().getCustomData().get(DATA_KEY_ID + "_" + ship.getId());
        OverdriveData odData = null;
        if (data instanceof OverdriveData) {
            odData = (OverdriveData) data;
        }
        if ((odData == null) || (STATEKEY != odData.stateKey)) {
            odData = new OverdriveData(STATEKEY);
            Global.getCombatEngine().getCustomData().put(DATA_KEY_ID + "_" + ship.getId(), odData);
            odData.gauge = 1f;
            totalPeakTimeLoss = 0f;
        }
		
        float shipRadius = armaa_utils.effectiveRadius(ship);
        float amount = Global.getCombatEngine().getElapsedInLastFrame();
        if (Global.getCombatEngine().isPaused()) {
            amount = 0f;
        }

        Color ENGINE_COLOR = ENGINE_COLOR_STANDARD;
        Color CONTRAIL_COLOR = CONTRAIL_COLOR_STANDARD;
        Color OVERLOAD_COLOR = OVERLOAD_COLOR_STANDARD;
        float gaugeDrainTime = GAUGE_DRAIN_TIME;
        float gaugeRegenTime = GAUGE_REGEN_TIME;
        float overGaugeLevel = OVER_GAUGE_LEVEL;
        float maxOverlevel = MAX_OVERLEVEL;
        float afterimageIntensity = 1f;
        float sparkIntensity = 1f;
        float pitchShift = 1.2f;
        float volumeShift = 0.8f;
        tempSize = ship.getHullSize();


        float effectLevelSquared = effectLevel * effectLevel;
        float effectOverlevel = 1f;
		float effect2 = 1f;
        if ((odData.gauge < overGaugeLevel)) {
            effectOverlevel = 1f / armaa_utils.lerp(1f / maxOverlevel, 1f, Math.max(0f, odData.gauge) / overGaugeLevel);
        }
        float effectOverlevelSquared = effectOverlevel * effectOverlevel;

        if (!ship.getFluxTracker().isOverloaded()) {
            shutdown = false;
        }

        ship.getSystem().setCooldown(Math.max(0f, armaa_utils.lerp(COOLDOWN_MAX * (float) Math.sqrt(effectOverlevel), COOLDOWN_MIN, odData.gauge)));

        if (state == State.IN) {
            deactivated = false;
            if (!activated) {
                Global.getSoundPlayer().playSound("armaa_silversword_activate", .8f, 1f * effectOverlevel * volumeShift, ship.getLocation(), ZERO);
                activated = true;
            }
        } else {
            activated = false;
        }

        if (state == State.OUT) {
            if (!deactivated) {
                Global.getSoundPlayer().playSound("system_entropy_off", 1f * effectOverlevel * pitchShift, 1f * effectOverlevel * volumeShift, ship.getLocation(), ZERO);
                deactivated = true;
            }
        }

        if ((state == State.COOLDOWN) || (state == State.IDLE) || (odData.gauge < 0f) || shutdown) {
			boolean systemOff = true;
			if ((odData.gauge < 0f) && ((state == State.IN) || (state == State.ACTIVE) || (state == State.OUT)) && !ship.getFluxTracker().isOverloaded()) 
			{
				

				shutdown = true;
				deactivated = true;

				ship.setOverloadColor(OVERLOAD_COLOR);
				ship.getFluxTracker().beginOverloadWithTotalBaseDuration(OVERLOAD_DUR);
				int randomNum = ThreadLocalRandom.current().nextInt(0, ship.getAllWeapons().size());
				ship.getAllWeapons().get(randomNum).disable();
				

				if (ship.getFluxTracker().showFloaty() || (ship == Global.getCombatEngine().getPlayerShip())) {
					ship.getFluxTracker().showOverloadFloatyIfNeeded("Emergency Shutdown!", OVERLOAD_COLOR, 4f, true);
				}
				Global.getSoundPlayer().playSound("disabled_large_crit", 1f, 1f * effectLevel * volumeShift, ship.getLocation(), ZERO);
					float explosionMult = (ship.getFluxTracker().getCurrFlux()/4f)*ship.getFluxTracker().getFluxLevel();
					float time = .5f;
				//	RippleDistortion ripple = new RippleDistortion(ship.getLocation(), ship.getVelocity());
				//	ripple.setSize(200f+(float)Math.sqrt(explosionMult));
				//	ripple.setIntensity((100f+(float)Math.sqrt(explosionMult)) * 1.25f);
				//	ripple.setFrameRate(60f / (time));
				//	ripple.fadeInSize(time);
				//	ripple.fadeOutIntensity(time);
				//	DistortionShader.addDistortion(ripple);
					
					DamagingExplosionSpec boom2 = new DamagingExplosionSpec(
							EXPLOSION_DURATION,
							EXPLOSION_SIZE_OUTER+explosionMult,
							EXPLOSION_SIZE_INNER,
							EXPLOSION_DAMAGE_MAX+explosionMult,
							EXPLOSION_DAMAGE_MIN,
							CollisionClass.PROJECTILE_FF,
							CollisionClass.PROJECTILE_FIGHTER,
							PARTICLE_SIZE_MIN,
							PARTICLE_SIZE_RANGE,
							PARTICLE_DURATION2,
							PARTICLE_COUNT,
							OVERLOAD_COLOR,
							OVERLOAD_COLOR
						);
				    Global.getCombatEngine().spawnExplosion(ship.getLocation(), new Vector2f(0,0), Color.BLACK, explosionMult+EXPLOSION_SIZE_OUTER, 3);
					Global.getCombatEngine().spawnExplosion(ship.getLocation(),new Vector2f(0,0), OVERLOAD_COLOR,explosionMult+EXPLOSION_SIZE_OUTER, 3f);
					boom2.setDamageType(DamageType.ENERGY);
					boom2.setShowGraphic(true);
					boom2.setSoundSetId("explosion_ship");
				    Global.getSoundPlayer().playSound("armaa_silversword_warning", 1f, 1f * effectLevel * volumeShift, ship.getLocation(), ZERO);
					
					
					Global.getCombatEngine().spawnDamagingExplosion(boom2, ship, ship.getLocation(), false);	
				ship.setWeaponGlow(0f, new Color(255, 125, 50, 220), EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.MISSILE, WeaponAPI.WeaponType.ENERGY));

				List<ShipEngineAPI> engList = ship.getEngineController().getShipEngines();
				for (int i = 0; i < engList.size(); i++) {
					ShipEngineAPI eng = engList.get(i);
					if (eng.isSystemActivated()) {
						engState.put(i, 0f);
						ship.getEngineController().setFlameLevel(eng.getEngineSlot(), 0f);
					}
				}

				for (int i = 0; i < SPARKS_ON_OVERLOAD.get(ship.getHullSize()); i++) {
					Vector2f targetPoint = MathUtils.getRandomPointInCircle(ship.getLocation(), (shipRadius * 0.75f + 15f) * effectOverlevel * sparkIntensity);
					Vector2f anchorPoint = MathUtils.getRandomPointInCircle(ship.getLocation(), shipRadius);
					AnchoredEntity anchor = new AnchoredEntity(ship, anchorPoint);
					float thickness = (float) Math.sqrt(((shipRadius * 0.025f + 5f) * effectOverlevel * sparkIntensity) * MathUtils.getRandomNumberInRange(0.75f, 1.25f)) * 3f;
					Color coreColor = new Color(ENGINE_COLOR.getRed(), ENGINE_COLOR.getGreen(), ENGINE_COLOR.getBlue(), 255);
					EmpArcEntityAPI arc = Global.getCombatEngine().spawnEmpArcPierceShields(ship, targetPoint, anchor, anchor, DamageType.ENERGY,
							0f, 0f, shipRadius, null, thickness, OVERLOAD_COLOR, coreColor);
				}

				Global.getCombatEngine().addPlugin(new BaseEveryFrameCombatPlugin() {
					@Override
					public void advance(float amount, List<InputEventAPI> events) {
						if (!ship.getFluxTracker().isOverloadedOrVenting()) {
							ship.resetOverloadColor();
							Global.getCombatEngine().removePlugin(this);
						}
					}
				});
				                odData.gauge = 0f;
			}

            

            if (systemOff) {
                stats.getMaxSpeed().unmodify(id);
				stats.getFluxDissipation().unmodify(id);
				stats.getFluxCapacity().unmodify(id);
                stats.getMaxTurnRate().unmodify(id);
                stats.getBallisticRoFMult().unmodify(id);
				stats.getBallisticWeaponFluxCostMod().unmodify(id);
				stats.getEnergyWeaponFluxCostMod().unmodify(id);
                stats.getEnergyRoFMult().unmodify(id);
                stats.getAcceleration().unmodify(id);
                stats.getDeceleration().unmodify(id);
                stats.getTurnAcceleration().unmodify(id);
                stats.getProjectileSpeedMult().unmodify(id);
                stats.getWeaponTurnRateBonus().unmodify(id);
                stats.getAutofireAimAccuracy().unmodify(id);
                stats.getMaxRecoilMult().unmodify(id);
                stats.getRecoilPerShotMult().unmodify(id);
                stats.getCRLossPerSecondPercent().unmodify(id);
				stats.getWeaponRangeThreshold().unmodify(id);
				stats.getWeaponRangeMultPastThreshold().unmodify(id);
				Global.getCombatEngine().getTimeMult().unmodify(id);
				stats.getTimeMult().unmodify(id);

                /* Ham-fisted attempt to get rid of that FUCKING glow */
                ship.setWeaponGlow(0f, new Color(255, 125, 50, 220), EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.MISSILE, WeaponAPI.WeaponType.ENERGY));
				
				if(ship.getFluxTracker().getCurrFlux() > ship.getFluxTracker().getMaxFlux())
				{
					ship.getFluxTracker().setCurrFlux(ship.getFluxTracker().getMaxFlux());
				}

                if (ship.controlsLocked()) {
                    odData.gauge = 0f;
                } else {
                    odData.gauge += (amount / gaugeRegenTime) * effectOverlevel;
                    if (odData.gauge > 1f) {
                        odData.gauge = 1f;
                    }
                }

                tempGauge = odData.gauge;
                return;
            }
		}
        

        /* WTF? */
        if (effectLevel <= 0f) {
            tempGauge = odData.gauge;
            if (unbugify) {
                ship.getSystem().deactivate();
            } else {
                unbugify = true;
            }
            return;
        } else {
            unbugify = false;
        }
		
		//float shipTimeMult = 1f + (MAX_TIME_MULT - 1f) * (effectOverlevel*effectOverlevel) * effectLevel;

        if (state == State.OUT) {
            stats.getMaxSpeed().unmodify(id);
            stats.getMaxTurnRate().unmodify(id);

            /* No gauge draining during out phase */
        } else 
		{
			stats.getMaxSpeed().modifyPercent(id, SPEED_BONUS * effectOverlevel);
			stats.getMaxSpeed().modifyFlat(id, SPEED_FLAT_BONUS.get(ship.getHullSize()) * effectOverlevel);
			stats.getMaxTurnRate().modifyPercent(id, MAX_TURN_BONUS * effectOverlevel);
			stats.getMaxTurnRate().modifyFlat(id, MAX_TURN_FLAT_BONUS.get(ship.getHullSize()) * effectOverlevel);
			//stats.getTimeMult().modifyMult(id, shipTimeMult);
		
            odData.gauge -= amount / (gaugeDrainTime);
        }
		
        stats.getFluxCapacity().modifyMult(id, armaa_utils.lerp(1f, CAPACITY_MULT, effectLevel * effectOverlevel));
		stats.getFluxDissipation().modifyMult(id, armaa_utils.lerp(1f, DISSIPATION_MULT, effectLevel * effectOverlevel));
		stats.getBallisticRoFMult().modifyPercent(id, ROF_BONUS * effectLevel * effectOverlevel);
		stats.getEnergyRoFMult().modifyPercent(id, ROF_BONUS * effectLevel * effectOverlevel);
		stats.getAcceleration().modifyPercent(id, ACCEL_BONUS * effectLevelSquared * effectOverlevel);
		stats.getAcceleration().modifyFlat(id, ACCEL_FLAT_BONUS * effectLevelSquared * effectOverlevel);
		stats.getDeceleration().modifyPercent(id, DECEL_BONUS * effectLevelSquared * effectOverlevel);
		stats.getDeceleration().modifyFlat(id, DECEL_FLAT_BONUS * effectLevelSquared * effectOverlevel);
		stats.getTurnAcceleration().modifyPercent(id, TURN_ACCEL_BONUS * effectLevelSquared * effectOverlevel);
		stats.getWeaponRangeThreshold().modifyFlat(id, RANGE_THRESHOLD);
		stats.getWeaponRangeMultPastThreshold().modifyMult(id, RANGE_MULT);
		/*
		if (player)
		{
			Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
		} 
		else 
		{
			Global.getCombatEngine().getTimeMult().unmodify(id);
		}	
		*/
        
        stats.getAutofireAimAccuracy().modifyFlat(id, AUTOAIM_BONUS * effectLevel * effectOverlevel);
        stats.getMaxRecoilMult().modifyMult(id, armaa_utils.lerp(1f, RECOIL_MULT, effectLevel * effectOverlevel));
        stats.getRecoilPerShotMult().modifyMult(id, armaa_utils.lerp(1f, RECOIL_MULT, effectLevel * effectOverlevel));

        totalPeakTimeLoss += (CR_LOSS_MULT - 1f) * effectLevel * effectOverlevelSquared * amount;
		stats.getCRLossPerSecondPercent().modifyMult(id, armaa_utils.lerp(1f, CR_LOSS_MULT, effectLevel * effectOverlevel));
		stats.getPeakCRDuration().modifyFlat(id, -totalPeakTimeLoss / ship.getMutableStats().getPeakCRDuration().getMult());
		ship.getEngineController().fadeToOtherColor(ENGINEKEY1, ENGINE_COLOR, CONTRAIL_COLOR, effectLevel * (float) Math.sqrt(effectOverlevel), 1f);
		ship.getEngineController().extendFlame(ENGINEKEY2, 0.25f * effectLevel * (float) Math.sqrt(effectOverlevel),
                    0.25f * effectLevel * (float) Math.sqrt(effectOverlevel), 0.25f * effectLevel * (float) Math.sqrt(effectOverlevel));
        
        /* Unweighted direction calculation for visual purposes - 0 degrees is forward */
        Vector2f direction = new Vector2f();
        float visualDir = 0f;
        boolean maneuvering = true;
        boolean cwTurn = false;
        boolean ccwTurn = false;
        if (ship.getEngineController().isAccelerating()) {
            direction.y += 1f;
        } else if (ship.getEngineController().isAcceleratingBackwards()) {
            direction.y -= 1f;
        }
        if (ship.getEngineController().isStrafingLeft()) {
            direction.x -= 1f;
        } else if (ship.getEngineController().isStrafingRight()) {
            direction.x += 1f;
        }
        if (direction.length() > 0f) {
            visualDir = MathUtils.clampAngle(VectorUtils.getFacing(direction) - 90f);
        } else if (ship.getEngineController().isDecelerating() && (ship.getVelocity().length() > 0f)) {
            visualDir = MathUtils.clampAngle(VectorUtils.getFacing(ship.getVelocity()) + 180f - ship.getFacing());
        } else {
            maneuvering = false;
        }
        if (ship.getEngineController().isTurningRight()) {
            cwTurn = true;
        }
        if (ship.getEngineController().isTurningLeft()) {
            ccwTurn = true;
        }

        List<ShipEngineAPI> engList = ship.getEngineController().getShipEngines();
        Map<Integer, Float> engineScaleMap = new HashMap<>();
        for (int i = 0; i < engList.size(); i++) {
            ShipEngineAPI eng = engList.get(i);
            if (eng.isSystemActivated()) {
                engineScaleMap.put(i, getSystemEngineScale(ship, eng, visualDir, maneuvering, cwTurn, ccwTurn, null));
            }
        }
        for (int i = 0; i < engList.size(); i++) {
            ShipEngineAPI eng = engList.get(i);
            if (eng.isSystemActivated()) {
                float targetLevel = getSystemEngineScale(ship, eng, visualDir, maneuvering, cwTurn, ccwTurn, engineScaleMap);
                if (state == State.OUT) {
                    targetLevel *= effectLevel;
                }
                Float currLevel = engState.get(i);
                if (currLevel == null) {
                    currLevel = 0f;
                }
                if (currLevel > targetLevel) {
                    currLevel = Math.max(targetLevel, currLevel - (amount / EXTEND_TIME.get(ship.getHullSize())));
                } else {
                    currLevel = Math.min(targetLevel, currLevel + (amount / EXTEND_TIME.get(ship.getHullSize())));
                }
                if (ship.getEngineController().isFlamedOut()) {
                    currLevel = 0f;
                }

                engState.put(i, currLevel);
                ship.getEngineController().setFlameLevel(eng.getEngineSlot(), currLevel);
            }
        }
		Color g = new Color(230, 73, 73);
        Color glowColor = new Color(armaa_utils.clamp255(Math.round((3f * g.getRed() + 255f) / 4f)),
                armaa_utils.clamp255(Math.round((3f * g.getGreen() + 255f) / 4f)),
                armaa_utils.clamp255(Math.round((3f * g.getBlue() + 255f) / 4f)),
                255);
        ship.setWeaponGlow(effectLevel, g, EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.MISSILE, WeaponAPI.WeaponType.ENERGY));
/*
        if (!Global.getCombatEngine().isPaused()) {
            Global.getSoundPlayer().playLoop("ui_interdict_loop", ship, 1f * effectOverlevel * pitchShift, 1f * effectOverlevel * effectLevel * volumeShift, ship.getLocation(), ZERO);
        }
*/
        if (!Global.getCombatEngine().isPaused()) 
		{
			Global.getSoundPlayer().playLoop("system_high_energy_focus_loop", ship, 1f * pitchShift*effectLevel, 1f * volumeShift*effectLevel, ship.getLocation(), ZERO);
			
			if ((odData.gauge < overGaugeLevel)) 
			{
            Global.getSoundPlayer().playLoop("armaa_silversword_warning", ship, 1f * effectOverlevel * pitchShift, 1f * effectOverlevel * effectLevel * volumeShift, ship.getLocation(), ZERO);
			}
        }


        interval.advance(amount);
        if (interval.intervalElapsed()) {
			float randRange = (float) shipRadius * 0.2f * afterimageIntensity;
			float randSpeed = (float) shipRadius * 0.5f * afterimageIntensity;
			float randAngle = MathUtils.getRandomNumberInRange(0f, 360f);
			float randRadiusFrac = (float) (Math.random() + Math.random());
			randRadiusFrac = (randRadiusFrac > 1f ? 2f - randRadiusFrac : randRadiusFrac);
			Vector2f randLoc = MathUtils.getPointOnCircumference(ZERO, randRange * randRadiusFrac, randAngle);
			Vector2f randVel = MathUtils.getRandomPointInCircle(ZERO, randSpeed * MathUtils.getRandomNumberInRange(0.5f, 1f));
			Color afterimageColor = new Color(OVERLOAD_COLOR.getRed(), OVERLOAD_COLOR.getGreen(), OVERLOAD_COLOR.getBlue(),
					armaa_utils.clamp255(Math.round(0.1f * afterimageIntensity * effectLevel * (float) Math.sqrt(effectOverlevel) * CONTRAIL_COLOR.getAlpha())));
			ship.addAfterimage(afterimageColor, randLoc.x, randLoc.y, randVel.x, randVel.y,
					randRange, 0f, 0.1f, 0.3f * effectLevel, true, false, false);

			randRange = (float) Math.sqrt(shipRadius) * 0.75f * afterimageIntensity;
			randLoc = MathUtils.getRandomPointInCircle(ZERO, randRange);
			Color afterimageColor3 = new Color(OVERLOAD_COLOR.getRed(), OVERLOAD_COLOR.getGreen(), OVERLOAD_COLOR.getBlue(),
					armaa_utils.clamp255(Math.round(0.15f * afterimageIntensity * effectLevel * (float) Math.sqrt(effectOverlevel) * CONTRAIL_COLOR.getAlpha())));
			//ship.addAfterimage(afterimageColor3, randLoc.x, randLoc.y, -ship.getVelocity().x, -ship.getVelocity().y,
			//		randRange, 0f, 0.1f, 0.7f * effectLevel, true, false, false);
					
			ship.addAfterimage(afterimageColor3, randLoc.x, randLoc.y, 0f, 0f,
					randRange, 0f, 1f, 0.1f * effectLevel, true, true, false);
					
				ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerNullerID", -1);
				ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerID",
                ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() + amount);
				
				if (ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHOLD) 
				{

					// Sprite offset fuckery - Don't you love trigonometry?
					SpriteAPI sprite = ship.getSpriteAPI();
					float offsetX = sprite.getWidth()/2 - sprite.getCenterX();
					float offsetY = sprite.getHeight()/2 - sprite.getCenterY();

					float trueOffsetX = (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetX - (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetY;
					float trueOffsetY = (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetX + (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetY;
					if(!ship.isHulk())
					{
					
						MagicRender.battlespace(
								Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
								new Vector2f(ship.getLocation().getX()+trueOffsetX,ship.getLocation().getY()+trueOffsetY),
								new Vector2f(0, 0),
								new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
								new Vector2f(0, 0),
								ship.getFacing()-90f,
								0f,
								new Color(255,120,150,200),
								true,
								0.1f,
								0.1f,
								0.5f,
								0f,
								0f,
								0f,
								0f,
								0f,
								CombatEngineLayers.BELOW_SHIPS_LAYER);
								
						for(WeaponAPI w : ship.getAllWeapons())
						{
							//armaa_valkazardEffect;
							if(w.getSpec().getType() == WeaponAPI.WeaponType.MISSILE && w.getAmmo() < 0)
								continue;
							if(!w.getSlot().getId().equals("F_LEGS"))
							{
								MagicRender.battlespace(
										Global.getSettings().getSprite(w.getSpec().getTurretSpriteName()),
										new Vector2f(w.getLocation().getX()+trueOffsetX,w.getLocation().getY()+trueOffsetY),
										new Vector2f(0, 0),
										new Vector2f(w.getSprite().getWidth(), w.getSprite().getHeight()),
										new Vector2f(0, 0),
										ship.getFacing()-90f,
										0f,
										new Color(255,200,200,155),
										true,
										0.1f,
										0.15f,
										0.2f,
										0f,
										0f,
										0f,
										0f,
										0f,
										CombatEngineLayers.BELOW_SHIPS_LAYER);
							}
							
							else
							{
								int frame = w.getAnimation().getFrame();
								SpriteAPI spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs0"+frame+".png");
							
								if(frame >= 10)
								spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs"+frame+".png");				
								
								MagicRender.battlespace(
										spr,
										new Vector2f(w.getLocation().getX()+trueOffsetX,w.getLocation().getY()+trueOffsetY),
										new Vector2f(0, 0),
										new Vector2f(w.getSprite().getWidth(), w.getSprite().getHeight()),
										new Vector2f(0, 0),
										ship.getFacing()-90f,
										0f,
										new Color(255,200,200,155),
										true,
										0.1f,
										0.15f,
										0.2f,
										0,
										0,
										0,
										0,
										0,
										CombatEngineLayers.BELOW_SHIPS_LAYER);								
							}							
							
							if(w.getBarrelSpriteAPI() != null)
							{								
								//Explicitly for Valkazard compat, since apparently can't access barrelsprite id through api and using the api itself also modifies the color of the original
								SpriteAPI spr = Global.getSettings().getSprite(w.getSpec().getHardpointSpriteName());
								
								MagicRender.battlespace(
										spr,
										new Vector2f(w.getLocation().getX()+trueOffsetX,w.getLocation().getY()+trueOffsetY),
										new Vector2f(0, 0),
										new Vector2f(w.getBarrelSpriteAPI().getWidth(), w.getBarrelSpriteAPI().getHeight()),
										new Vector2f(0, 0),
										w.getCurrAngle()-90f,
										0f,
										new Color(255,200,200,155),
										true,
										0.1f,
										0.15f,
										0.2f,
										0,
										0,
										0,
										0,
										0,
										CombatEngineLayers.BELOW_SHIPS_LAYER);
							}
						}
					}
							ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerID",
							ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() - AFTERIMAGE_THRESHOLD);
				}					
			//ship.setJitter(ship, afterimageColor3,afterimageIntensity * effectLevel, 5, randRange);

            if (effectOverlevel > 1f) {
                randRange = (float) Math.sqrt(shipRadius) * (float) Math.sqrt(effectOverlevel);
                randLoc = MathUtils.getRandomPointOnCircumference(ZERO, randRange);
                afterimageColor = new Color(ENGINE_COLOR.getRed(), ENGINE_COLOR.getGreen(), ENGINE_COLOR.getBlue(),
                        armaa_utils.clamp255(Math.round(0.75f * effectLevel * (effectOverlevel - 1f) * CONTRAIL_COLOR.getAlpha())));
                ship.addAfterimage(afterimageColor, randLoc.x, randLoc.y, 0f, 0f,
                        randRange, 0f, 0f, 0.15f, true, false, true);
				//if(effectOverlevel > 1.5f)
				//ship.setJitter(ship, afterimageColor3,afterimageIntensity * effectLevel, 10, randRange);
            }
			/*
            if (Math.random() < (BASE_SPARK_CHANCE_PER_TICK.get(ship.getHullSize())) * effectLevelSquared * effectOverlevelSquared * sparkIntensity) {
                float targetAngle = (float) Math.random() * 360f;
                Vector2f targetPointPre = MathUtils.getPointOnCircumference(ship.getLocation(), shipRadius * 2f, targetAngle);
                Vector2f anchorPoint = CollisionUtils.getCollisionPoint(targetPointPre, ship.getLocation(), ship);
                if (anchorPoint != null) {
                    float sparkLen = (shipRadius * 0.05f + 10f) * MathUtils.getRandomNumberInRange(0.75f, 1.25f) * sparkIntensity * (float) Math.sqrt(effectOverlevel);
                    Vector2f targetPoint = MathUtils.getPointOnCircumference(ship.getLocation(),
                            MathUtils.getDistance(ship.getLocation(), anchorPoint) + sparkLen, targetAngle);
                    AnchoredEntity anchor = new AnchoredEntity(ship, anchorPoint);
                    float thickness = (float) Math.sqrt(sparkLen) * 3f;
                    Color coreColor = new Color(ENGINE_COLOR.getRed(), ENGINE_COLOR.getGreen(), ENGINE_COLOR.getBlue(), 255);
                    Global.getCombatEngine().spawnEmpArcPierceShields(ship, targetPoint, anchor, anchor, DamageType.ENERGY,
                            0f, 0f, sparkLen * 2f, null, thickness, OVERLOAD_COLOR, coreColor);
                }
            }
			*/
			        /* - Visuals - */
        //Checks whether we should draw visuals at all (only draw when we are close enough to the viewport, )
        //if (ship == null) { return; }
      //  ViewportAPI viewport = Global.getCombatEngine().getViewport();
      //  if (viewport.isNearViewport(ship.getLocation(), ship.getCollisionRadius() * 1.5f)) 
		//{

			//Main part handling smoke: keeps a counter which depends on how many smoke particles we want per second
			//tracker += Global.getCombatEngine().getElapsedInLastFrame();
			tracker.advance(1f*effectLevel*(float) Math.sqrt(effectOverlevel));
			if (tracker.intervalElapsed() && ship.getHullSize() != HullSize.FIGHTER) 
			{
				//Handles smoke emission from all system weapon mounts
				for (WeaponSlotAPI testSlot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
					if (!testSlot.isSystemSlot()) {
						continue;
					}

					Vector2f positionOfSmoke = MathUtils.getRandomPointInCircle(testSlot.computePosition(ship), 1f*randRange);
					Vector2f speedOfSmoke = MathUtils.getRandomPointInCone(null, SMOKE_SPEED* (float) Math.sqrt(effectOverlevel), testSlot.getAngle() - SMOKE_ANGLE + ship.getFacing(), testSlot.getAngle() + SMOKE_ANGLE + ship.getFacing());
					speedOfSmoke = Vector2f.add(speedOfSmoke, MathUtils.getRandomPointInCircle(null, SMOKE_SCATTER), null);

					Global.getCombatEngine().addSmokeParticle(positionOfSmoke,
							speedOfSmoke, MathUtils.getRandomNumberInRange(SMOKE_MIN_SIZE*effectLevel * (float) Math.sqrt(effectOverlevel)*(float)Math.sqrt(effectOverlevel), SMOKE_MAX_SIZE*effectLevel* (float) Math.sqrt(effectOverlevel)*(float)Math.sqrt(effectOverlevel)), SMOKE_OPACITY,
							MathUtils.getRandomNumberInRange(SMOKE_MIN_DURATION, SMOKE_MAX_DURATION), SMOKE_COLOR);
				}
				//Updates our smoke tracker
				//tracker -= (1f / SMOKE_PARTICLES_PER_SECOND);
			}
		//}
        
		}
        tempGauge = odData.gauge;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (shutdown) {
            return false;
        }
        return isUsable(ship, system);
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        float overGaugeLevel = OVER_GAUGE_LEVEL;
        float gauge = getGauge(ship);

        int displayGauge = Math.round(100f * Math.max(0f, gauge));
        if (shutdown) {
            return "SHUTDOWN";
        }
        if ((gauge < overGaugeLevel) && system.isOn()) {
            long count200ms = (long) Math.floor(Global.getCombatEngine().getTotalElapsedTime(true) / 0.2f);
            if (count200ms % 2L == 0L) {
                return "" + displayGauge + "% - WARNING!";
            } else {
                return "" + displayGauge + "% - ";
            }
        }
        if (((gauge < overGaugeLevel) && !system.isOn()) || system.isCoolingDown()) {
            return "" + displayGauge + "%";
        }
        if (system.isActive()) {
            return "" + displayGauge + "% - ENGAGED";
        }
        return "" + displayGauge + "% - ALL OK";
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        float overGaugeLevel = OVER_GAUGE_LEVEL;
        float maxOverlevel = MAX_OVERLEVEL;


        float effectOverlevel = 1f;
        if ((tempGauge < overGaugeLevel)) {
            effectOverlevel = 1f / armaa_utils.lerp(1f / maxOverlevel, 1f, Math.max(0f, tempGauge) / overGaugeLevel);
        }
        float effectOverlevelSquared = effectOverlevel * effectOverlevel;
		float mult = 1f + ROF_BONUS * effectLevel* effectOverlevel;
		float bonusPercent = (int) ((mult - 1f) * 100f);
		//float bonusDilation = ((MAX_TIME_MULT-1)*100)*effectLevel* effectOverlevel;
        switch (index) {
            case 0:
                    if ((state == State.IN) || (state == State.ACTIVE) || (state == State.OUT)) {
                        return new StatusData("rate of fire +" + Math.round(ROF_BONUS * effectLevel * effectOverlevel) + "%", false);
                    }
                
                break;
            case 1:
                    if ((state == State.IN) || (state == State.ACTIVE) || (state == State.OUT)) 
					{
                        return new StatusData("improved engine performance", false);
                    }
                break;
            case 2:
                if ((state == State.IN) || (state == State.ACTIVE) || (state == State.OUT)) 
				{
                    return new StatusData("weapon accuracy +" + Math.round((1f - armaa_utils.lerp(1f, RECOIL_MULT, effectLevel * effectOverlevel)) * 100f) + "%", false);
                }
                break;
            case 3:
                    if ((state == State.IN) || (state == State.ACTIVE) || (state == State.OUT)) {
                        return new StatusData("CR degradation +" + Math.round((armaa_utils.lerp(1f, CR_LOSS_MULT, effectLevel * effectOverlevelSquared) - 1f) * 100f) + "%", true);
                    }
                
                break;
            default:
                break;
        }
        return null;
    }

    private static float getSystemEngineScale(ShipAPI ship, ShipEngineAPI engine, float direction, boolean maneuvering, boolean cwTurn, boolean ccwTurn, Map<Integer, Float> engineScaleMap) {
        float target = 0f;

        Vector2f engineRelLocation = new Vector2f(engine.getLocation());
        Vector2f.sub(engineRelLocation, ship.getLocation(), engineRelLocation); // Example -- (20, 20) ship facing forwards, engine on upper right quadrant
        engineRelLocation.normalise(engineRelLocation); // (0.7071, 0.7071)
        VectorUtils.rotate(engineRelLocation, -ship.getFacing(), engineRelLocation); // (0.7071, -0.7071) - engine past centerline (x) on right side (y)
        Vector2f engineAngleVector = VectorUtils.rotate(new Vector2f(1f, 0f), engine.getEngineSlot().getAngle()); // 270 degrees into (0, -1)
        float torque = VectorUtils.getCrossProduct(engineRelLocation, engineAngleVector); // 0.7071*-1 - -0.7071*0 = -0.7071 (70.71% strength CCW torque)

        if ((Math.abs(MathUtils.getShortestRotation(engine.getEngineSlot().getAngle(), direction)) > 100f) && maneuvering) {
            target = 1f;
        } else {
            if ((torque <= -0.4f) && ccwTurn) {
                target = 1f;
            } else if ((torque >= 0.4f) && cwTurn) {
                target = 1f;
            }
        }

        /* Engines that are firing directly against each other should shut off */
        if (engineScaleMap != null) {
            List<ShipEngineAPI> engineList = ship.getEngineController().getShipEngines();
            for (int i = 0; i < engineList.size(); i++) {
                ShipEngineAPI otherEngine = engineList.get(i);
                if (otherEngine.isSystemActivated() && (engineScaleMap.get(i) >= 0.5f)) {
                    Vector2f otherEngineRelLocation = new Vector2f(otherEngine.getLocation());
                    Vector2f.sub(otherEngineRelLocation, ship.getLocation(), otherEngineRelLocation); // Example -- (20, 20) ship facing forwards, engine on upper right quadrant
                    otherEngineRelLocation.normalise(otherEngineRelLocation); // (0.7071, 0.7071)
                    VectorUtils.rotate(otherEngineRelLocation, -ship.getFacing(), otherEngineRelLocation); // (0.7071, -0.7071) - engine past centerline (x) on right side (y)
                    Vector2f otherEngineAngleVector = VectorUtils.rotate(new Vector2f(1f, 0f), otherEngine.getEngineSlot().getAngle()); // 270 degrees into (0, -1)

                    float otherTorque = VectorUtils.getCrossProduct(otherEngineRelLocation, otherEngineAngleVector); // 0.7071*-1 - -0.7071*0 = -0.7071 (70.71% strength CCW torque)
                    if ((Math.abs(MathUtils.getShortestRotation(engine.getEngineSlot().getAngle(), otherEngine.getEngineSlot().getAngle())) > 155f)
                            && (Math.abs(torque + otherTorque) <= 0.2f)) {
                        target = 0f;
                        break;
                    }
                }
            }
        }

        return target;
    }

    public static float getGauge(ShipAPI ship) {
        if ((ship == null) || (ship.getSystem() == null)) {
            return 0f;
        }

        Object data = Global.getCombatEngine().getCustomData().get(DATA_KEY_ID + "_" + ship.getId());
        if (data instanceof OverdriveData) {
            OverdriveData odData = (OverdriveData) data;

            return odData.gauge;
        } else {
            return 0f;
        }
    }

    /* Returns the same regardless of whether the system is on or not */
    public static float getOverlevel(ShipAPI ship) {
        if ((ship == null) || (ship.getSystem() == null)) {
            return 0f;
        }

        float overGaugeLevel = OVER_GAUGE_LEVEL;
        float maxOverlevel = MAX_OVERLEVEL;

        float gauge = getGauge(ship);

        float effectOverlevel = 1f;
		
			if ((gauge < overGaugeLevel) && (gauge >= 0f)) {
            effectOverlevel = 1f / armaa_utils.lerp(1f / maxOverlevel, 1f, gauge / overGaugeLevel);
        }

        return effectOverlevel;
    }

    public static boolean isUsable(ShipAPI ship, ShipSystemAPI system) {
        if ((ship == null) || (system == null)) {
            return false;
        }

        float overGaugeLevel = OVER_GAUGE_LEVEL;

        float gauge = getGauge(ship);

        return !((gauge < overGaugeLevel) && !system.isActive());
    }

    private static class OverdriveData {

        final Object stateKey;
        float gauge;

        OverdriveData(Object stateKey) {
            this.stateKey = stateKey;
        }
    }
}

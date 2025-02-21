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

/**
 *Base script by
 * Tartiflette
 * additional modifications by shoi
 */
public class armaa_valkazardEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private ShipSystemAPI system;
    private ShipAPI ship;
    private SpriteAPI sprite;
    private AnimationAPI anim, aGlow, aGlow2;
    private WeaponAPI head, armL, armR, pauldronL,torso,headGlow,gun,rwep;

    private float delay = 0.1f;
    private int numFrames = 8;
    private float minDelay=1f/30f;
	public int frame = 7;
    private float SPINUP = 0.02f;
    private float SPINDOWN = 10f;
    //private int maxFrame, frame;

    //sliver cannon charging fx
    private boolean charging = false;
    private boolean cooling = false;
    private boolean firing = false;
    private final IntervalUtil interval = new IntervalUtil(0.06f, 0.06f);
    private float level = 0f;
    private float glowLevel = 1f;
    private int glowEffect = 0;
    float eyeG = 0.0f;
    private final Vector2f ZERO = new Vector2f();
    public float TURRET_OFFSET = 30f;
    private int limbInit = 0;
	private float timer = 0f;
	private int anime = 0;
	private boolean windingUp = false;
	private boolean swinging = false;
	private boolean cooldown = false;
	private float swingLevel = 0f;	
	
	private IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);

    private float overlap = 0, heat = 0, originalRArmPos = 0f, originalArmPos = 0f, originalShoulderPos = 0f;
    private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -60, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10, LPAULDRONOFFSET = -5;

    public void init()
    {
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "B_TORSO":
                    if(torso==null) {
                        torso = w;
                        limbInit++;
                    }
                    break;
                case "C_ARML":
                    if(armL==null) {
                        armL = w;
						originalArmPos = armL.getSprite().getCenterY();
                        limbInit++;
                    }
                    break;
                case "C_ARMR":
                    if(armR==null) {
                        armR = w;
                        limbInit++;
                    }
                    break;
                case "D_PAULDRONL":
                    if(pauldronL == null) {
                        pauldronL = w;
						originalShoulderPos = pauldronL.getSprite().getCenterY();
                        limbInit++;
                    }
                    break;
                case "A_GUN":
                    if(gun == null) {
                        gun = w;
						originalRArmPos = w.getBarrelSpriteAPI().getCenterY();
                        limbInit++;
                    }
                    break;
                case "E_HEAD":
                    if(head == null) {
                        head = w;
                        limbInit++;
                    }
                    break;
				case "H_GLOW":
                   		headGlow = w;
                    break;
				case "F_LEGS":
                   		rwep = w;
						rwep.ensureClonedSpec(); 
						sprite = rwep.getSprite();
                    break;
            }
        }

    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{

        ship = weapon.getShip();
        system = ship.getSystem();
        anim = weapon.getAnimation();

        init();
		
		if (ship.getEngineController().isAccelerating()) {
			if (overlap > (MAX_OVERLAP - 0.1f)) {
				overlap = MAX_OVERLAP;
			} else {
				overlap = Math.min(MAX_OVERLAP, overlap + ((MAX_OVERLAP - overlap) * amount * 5));
			}
		} else if (ship.getEngineController().isDecelerating() || ship.getEngineController().isAcceleratingBackwards()) {
			if (overlap < -(MAX_OVERLAP - 0.1f)) {
				overlap = -MAX_OVERLAP;
			} else {
				overlap = Math.max(-MAX_OVERLAP, overlap + ((-MAX_OVERLAP + overlap) * amount * 5));
			}
		} else {
			if (Math.abs(overlap) < 0.1f) {
				overlap = 0;
			} else {
				overlap -= (overlap / 2) * amount * 3;
			}
		}

		float sineA = 0, sinceB = 0, sineC = 0;
		boolean useanim = false;
		if(gun == null)
			return;
		float global = ship.getFacing();
		float aim = MathUtils.getShortestRotation(global,gun.getCurrAngle());
		
		if(!ship.isLanding())
		for (WeaponAPI w : ship.getAllWeapons()) 
		{
			switch (w.getSlot().getId()) 
			{
				case "WS0001":
				case "WS0002":
				if(w.getAmmo() <1 && w.getAmmoPerSecond() == 0)
					w.getSprite().setColor(new Color(0,0,0,0));
				else
					w.getSprite().setColor(new Color(255,255,255,255));
				break;
				
			}
		}		
		if(!engine.isPaused())
		{
			
			if(armL!= null)
			{				
				if(armL.getSpec().getWeaponId().equals("armaa_valkazard_harpoon"))
					useanim = true;
			
				if(useanim ||armL.getId().equals("armaa_valkazard_blade"))
				{
					if(armL.getChargeLevel()<1)
					{
						sineA=MagicAnim.smoothNormalizeRange(armL.getChargeLevel(),0.3f,0.9f);
						sinceB=MagicAnim.smoothNormalizeRange(armL.getChargeLevel(),0.3f,1f);
					} 
					else 
					{                
						sineA =1;
						sinceB =1;
					}
					armL.getSprite().setCenterY(originalArmPos-(16*sinceB));
				}
				float aim2 = MathUtils.getShortestRotation(global, armL.getCurrAngle());
			}
		if(armL != null && armL.getId().equals("armaa_valkazard_blade"))
		{
			
			armL.getSprite().setCenterY(originalArmPos-(16*sinceB));
			
			if(torso != null)
			{

				if(armL.getCooldownRemaining() <= 0f && !armL.isFiring())
					cooldown = false;
				
				if(!swinging && !cooldown && armL.getChargeLevel() > 0f)
				{
					armL.setCurrAngle(armL.getCurrAngle() + (sineA * TORSO_OFFSET*0.30f) *armL.getChargeLevel());
				}
				if(armL.getChargeLevel() >= 1f)
				{
					swinging = true;
				}
				
				if(swinging && armL.getCurrAngle() != armL.getShip().getFacing()+45f)
				{
					animInterval.advance(amount);
					armL.setCurrAngle((float)Math.min(armL.getCurrAngle()+swingLevel,armL.getCurrAngle()+armL.getArc()/2));
				}
				
				if(swinging == true && (armL.getChargeLevel() <= 0f))
				{
					swinging = false;
					swingLevel = 0f;
					cooldown = true;
				}
				
				if(animInterval.intervalElapsed())
				{
					swingLevel+=0.5;
				}

				if(swingLevel > 9)
					swingLevel = 9;
				if(swinging == false)
				{
					swingLevel = 0f;
				}
			}
				
		}

			if (torso != null)
			{
				/*
				if(torso.getSpec().getWeaponId().equals("armaa_valkazard_torso_chaosburst"))
					if(torso.getChargeLevel() > 0)
					{
						sineC=MagicAnim.smoothNormalizeRange(torso.getChargeLevel(),0.0f,1f);
						armL.setCurrAngle(armL.getCurrAngle()+(sineA*(TORSO_OFFSET/7)*.7f)+(sineC*(TORSO_OFFSET)));

					}
				*/
				if(torso.getChargeLevel() == 0 && (ship.getSelectedGroupAPI() != null && ship.getSelectedGroupAPI().getActiveWeapon() != torso ))
				{
					if(useanim)
						torso.setCurrAngle((global + (sineA * (TORSO_OFFSET) +((sineC*(TORSO_OFFSET))) + aim * 0.3f)*armL.getChargeLevel()));
					else
					torso.setCurrAngle((global + (sineA * (TORSO_OFFSET-(TORSO_OFFSET*(swingLevel/9f))) + aim * 0.3f)));
				}
			}

			if(head != null)
			{

				head.setCurrAngle(gun.getCurrAngle());

			}
			
			if (armR != null)
			{
				armR.setCurrAngle(gun.getCurrAngle() + RIGHT_ARM_OFFSET + ((sineC*(TORSO_OFFSET))));
			}

			if (weapon != null)
			{
				weapon.setCurrAngle(global + sineA * (TORSO_OFFSET-(TORSO_OFFSET*(swingLevel/9f))) * 0.5f + ((sineC*(TORSO_OFFSET))) + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f);
				weapon.getSprite().setCenterY(gun.getBarrelSpriteAPI().getCenterY()-23f);
			}
		
			if (pauldronL != null)
			{
				pauldronL.setCurrAngle(torso.getCurrAngle()+LPAULDRONOFFSET*sineA + ((sineC*(TORSO_OFFSET*.5f))) + MathUtils.getShortestRotation(torso.getCurrAngle(), armL.getCurrAngle()) * 0.70f);
				pauldronL.getSprite().setCenterY(originalShoulderPos-(8*sinceB));
			}
			
			if(headGlow != null)
			{
				headGlow.setCurrAngle(head.getCurrAngle());
				headGlow.getSprite().setColor(new Color(55,5,10, 255));
			}
				
				interval.advance(amount);
			if(ship.getEngineController().isAcceleratingBackwards()) 
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
			
			SpriteAPI spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs0"+frame+".png");
		
			if(frame >= 10)
			spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs"+frame+".png");				
			rwep.getAnimation().setFrame(frame);
			MagicRender.singleframe(
						spr,
						new Vector2f(rwep.getLocation().getX(),rwep.getLocation().getY()),
						new Vector2f(spr.getWidth(),spr.getHeight()),
						ship.getFacing()-90f,
						pauldronL.getSprite().getColor(),
						false,
						CombatEngineLayers.BELOW_SHIPS_LAYER
				);
			
			rwep.getSprite().setColor(new Color(0f,0f,0f,0f));
		}

    }
}

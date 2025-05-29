package data.scripts.weapons;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 *Base script by
 * Tartiflette
 * additional modifications by shoi
 */
public class armaa_alesteEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private ShipSystemAPI system;
    private ShipAPI ship;
    private SpriteAPI sprite;
    private AnimationAPI anim;
    private WeaponAPI head, armL, armL2, armR, pauldronL, pauldronR, torso,gun, shoulderwep, headGlow, trueWeapon;	
    private final Vector2f ZERO = new Vector2f();
    private int limbInit = 0;
	
	private float swingLevel = 0f;
	private boolean swinging = false;
	private boolean cooldown = false;
	
	private float swingLevelR = 0f;
	private boolean swingingR = false;
	private boolean cooldownR = false;
	private IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);

    private float overlap = 0, heat = 0, originalRArmPos = 0f, originalArmPos = 0f, originalArmPos2 = 0f, originalShoulderPos = 0f, originalRShoulderPos = 0f;
    private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -60, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10, LPAULDRONOFFSET = -5;

    public void init()
    {
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) {
            switch (w.getSlot().getId()) {
                case "A_GUN":
                    if(gun==null) {
                        gun = w;
						originalRArmPos =  w.getBarrelSpriteAPI() != null ? w.getBarrelSpriteAPI().getCenterY() : w.getSprite().getCenterY();
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
                case "C_ARML2":
                    if(armL2==null) {
                        armL2 = w;
						originalArmPos2 = armL2.getSprite().getCenterY();
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
                case "D_PAULDRONR":
                    if(pauldronR == null) {
                        pauldronR = w;
						originalRShoulderPos = pauldronR.getSprite().getCenterY();
                        limbInit++;
                    }
                    break;
                case "E_HEAD":
                    if(head == null) {
                        head = w;
                        limbInit++;
                    }
                    break;
				case "WS001":
                   		shoulderwep = w;
                    break;
				case "H_GLOW":
                   		headGlow = w;
                    break;
                case "TRUE_GUN":
                    if(trueWeapon==null) 
					{
                        trueWeapon = w;
                    }
                    break;
            }
        }
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{

        ship = weapon.getShip();
        sprite = ship.getSpriteAPI();
        system = ship.getSystem();
		
		if(!runOnce)
        init();
		if(gun == null || armL == null) return;
		if(engine.isPaused())
			return;
        anim = gun.getAnimation();
		ship.syncWeaponDecalsWithArmorDamage();
		
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

		float sineA = 0, sinceB = 0, sineC=0, sinceD=0;	
		float global = ship.getFacing();
		float aim = MathUtils.getShortestRotation(global, gun.getCurrAngle());
		float aim2 = MathUtils.getShortestRotation(global, armL.getCurrAngle());
		boolean noanim = false;
		if(armL2 == null && (!ship.getHullSpec().getBaseHullId().contains("aleste") ||armL.getSpec().getWeaponId().equals("armaa_aleste_rifle_left") || armL.getSpec().getWeaponId().equals("armaa_aleste_grenade_left")))
			noanim  = true;
		if(armL != null && !noanim)
		{
			WeaponAPI bladeArm = armL2 == null ? armL : armL2;
			float pos = bladeArm == armL ? originalArmPos : originalArmPos2;

			if(bladeArm.getChargeLevel()<1)
			{
				sineA=MagicAnim.smoothNormalizeRange(bladeArm.getChargeLevel(),0.3f,0.9f);
				sinceB=MagicAnim.smoothNormalizeRange(bladeArm.getChargeLevel(),0.3f,1f);
			} 
			else 
			{                
				sineA =1;
				sinceB =1;
			}
			//armL.getSprite().setCenterY(originalArmPos-(16*sinceB));
			//if(armL != bladeArm)
			//{
				bladeArm.getSprite().setCenterY(pos-(16*sinceB));
			//}
		}

		if (weapon != null)
		{
			if(armL != null)
			{
				WeaponAPI bladeArm = armL2 == null ? armL : armL2;
				if(noanim)
					weapon.setCurrAngle(global + (sineA * TORSO_OFFSET + aim * 0.3f));
			
				else
				{
					weapon.setCurrAngle(global + (sineA * (TORSO_OFFSET-(TORSO_OFFSET*(swingLevel/9f))) + aim * 0.3f)*bladeArm.getChargeLevel());
				}
				
				if(bladeArm.getSpec().getWeaponId().contains("armaa_aleste_blade_LeftArm"))
				{
					if(bladeArm.getCooldownRemaining() <= 0f && !bladeArm.isFiring())
						cooldown = false;
					
					if(!swinging && !cooldown && bladeArm.getChargeLevel() > 0f)
					{
						armL.setCurrAngle(armL.getCurrAngle() + (sineA * TORSO_OFFSET*0.30f) *bladeArm.getChargeLevel());
					}
					if(bladeArm.getChargeLevel() >= 1f)
					{
						swinging = true;
					}
					
					if(swinging && armL.getCurrAngle() != armL.getShip().getFacing()+45f)
					{
						animInterval.advance(amount);
						armL.setCurrAngle((float)Math.min(armL.getCurrAngle()+swingLevel,armL.getCurrAngle()+armL.getArc()/2));
					}
					
					if(swinging == true && (bladeArm.getChargeLevel() <= 0f))
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
		}
		
		if(armL2 != null)
		{
			armL2.setCurrAngle(armL.getCurrAngle());
		}

		if (armR != null)
		{
			armR.setCurrAngle(gun.getCurrAngle() + RIGHT_ARM_OFFSET);
//
			if(gun.getChargeLevel()<1)
			{
				sineC=MagicAnim.smoothNormalizeRange(gun.getChargeLevel(),0.3f,0.9f);
				sinceD=MagicAnim.smoothNormalizeRange(gun.getChargeLevel(),0.3f,1f);
			} 
			else 
			{                
				sineC =1;
				sinceD =1;
			}

				if(gun.getSpec().getWeaponId().equals("armaa_aleste_blade_RightArm"))
				{
					if(gun.getCooldownRemaining() <= 0f && !gun.isFiring())
						cooldownR = false;
					
					if(!swingingR && !cooldownR && gun.getChargeLevel() > 0f)
					{
						gun.setCurrAngle(gun.getCurrAngle() + (sineC * -(TORSO_OFFSET)*0.30f) *gun.getChargeLevel());
					}
					if(gun.getChargeLevel() >= 1f)
					{
						swingingR = true;
					}
					
					if(swingingR && gun.getCurrAngle() != gun.getShip().getFacing()-45f)
					{
						animInterval.advance(amount);
						gun.setCurrAngle((float)Math.max(gun.getCurrAngle()-swingLevelR,gun.getCurrAngle()-gun.getArc()/2));
					}
					
					if(swingingR == true && (gun.getChargeLevel() <= 0f))
					{
						swingingR = false;
						swingLevelR = 0f;
						cooldownR = true;
					}
					
					if(animInterval.intervalElapsed())
					{
						swingLevelR+=0.5;
					}

					if(swingLevelR > 9)
						swingLevelR = 9;
					if(swingingR == false)
					{
						swingLevelR = 0f;
					}
				}
			}			
//
		if (pauldronR != null)
		{
			pauldronR.setCurrAngle(global + sineA * (TORSO_OFFSET-(TORSO_OFFSET*(swingLevel/9f))) * 0.5f + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f);
			if(gun.getBarrelSpriteAPI() != null)
			pauldronR.getSprite().setCenterY(gun.getBarrelSpriteAPI().getCenterY()-40f);
		
			if(trueWeapon != null && trueWeapon.getCooldown() > 0)
			{
				gun.getSprite().setCenterY(originalRArmPos+(2*trueWeapon.getCooldownRemaining()/trueWeapon.getCooldown()));
				pauldronR.getSprite().setCenterY(originalRShoulderPos+(2*trueWeapon.getCooldownRemaining()/trueWeapon.getCooldown()));			
			}			
		}
				
		if (pauldronL != null)
		{
			if(armL != null)
			pauldronL.setCurrAngle(global + sineA * TORSO_OFFSET * 0.5f + aim2 * 0.75f - RIGHT_ARM_OFFSET * 0.5f);
			pauldronL.getSprite().setCenterY(originalShoulderPos-(8*sinceB));
		}
		
		if(headGlow != null)
		{
			headGlow.setCurrAngle(head.getCurrAngle());
		}
    }
}

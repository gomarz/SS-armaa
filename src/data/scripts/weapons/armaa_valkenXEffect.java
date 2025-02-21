package data.scripts.weapons;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.magiclib.util.MagicAnim;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShipwideAIFlags;

/**
 *Base script by
 * Tartiflette
 * additional modifications by shoi
 */
public class armaa_valkenXEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private ShipSystemAPI system;
    private ShipAPI ship;
    private SpriteAPI sprite;
    private AnimationAPI anim, aGlow, aGlow2;
    private WeaponAPI head, armL, armR, pauldronL, pauldronR, torso,bunker;

    private float delay = 0.1f;
    private float timer = 0;
    private float SPINUP = 0.02f;
    private float SPINDOWN = 10f;
    //private int maxFrame, frame;

    //sliver cannon charging fx
    private boolean charging = false;
    private boolean cooling = false;
    private boolean firing = false;
    private IntervalUtil interval = new IntervalUtil(0.6f, 1f);
    private float level = 0f;
    private float glowLevel = 1f;
    private int glowEffect = 0;
    float eyeG = 0.0f;
    private final Vector2f ZERO = new Vector2f();
    public float TURRET_OFFSET = 30f;
    private int limbInit = 0;
	private float swingLevel = 0f;
	private boolean windingUp = false;
	private boolean swinging = false;
	private boolean cooldown = false;
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
                case "D_PAULDRONR":
                    if(pauldronR == null) {
                        pauldronR = w;
                        limbInit++;
                    }
                    break;
                case "E_HEAD":
                    if(head == null) {
                        head = w;
                        limbInit++;
                    }
                    break;
                case "A_GUN":
                   		//originalRArmPos = w.getBarrelSpriteAPI().getCenterY();
                    break;
				case "bunker":
					bunker = w;
					break;
            }
        }

    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ship = weapon.getShip();
        sprite = ship.getSpriteAPI();
        system = ship.getSystem();
        anim = weapon.getAnimation();
		ShipwideAIFlags flags = ship.getAIFlags();

        init();
		
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

            float sineA = 0, sinceB = 0;
			
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

            float global = ship.getFacing();
            float aim = MathUtils.getShortestRotation(global, weapon.getCurrAngle());
			float aim2 = MathUtils.getShortestRotation(global, armL.getCurrAngle());
		if(armL != null)
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
                torso.setCurrAngle((global + (sineA * (TORSO_OFFSET-(TORSO_OFFSET*(swingLevel/9f))) + aim * 0.3f)*armL.getChargeLevel()));

			}

            if (armR != null)
			{
                armR.setCurrAngle(weapon.getCurrAngle() + RIGHT_ARM_OFFSET);
				//weapon.getBarrelSpriteAPI().setCenterY(originalRArmPos+(4*sinceB));
			}

            if (pauldronR != null)
			{
				pauldronR.setCurrAngle(global + sineA * (TORSO_OFFSET-(TORSO_OFFSET*(swingLevel/9f))) * 0.5f + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f);
				//pauldronR.getSprite().setCenterY(weapon.getBarrelSpriteAPI().getCenterY()-23f);
			}
	    
            Vector2f origin = new Vector2f(weapon.getLocation());
            Vector2f offset = new Vector2f(TURRET_OFFSET, -15f);
            VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
            Vector2f.add(offset, origin, origin);
            float shipFacing = weapon.getCurrAngle();
            Vector2f shipVelocity = ship.getVelocity();


            if (pauldronL != null)
			{
                pauldronL.setCurrAngle(torso.getCurrAngle()+LPAULDRONOFFSET*sineA + MathUtils.getShortestRotation(torso.getCurrAngle(), armL.getCurrAngle()) * 0.70f);
				pauldronL.getSprite().setCenterY(originalShoulderPos-(8*sinceB));
			}

    }
}

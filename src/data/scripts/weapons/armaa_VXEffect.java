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
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 *Base script by
 * Tartiflette
 * additional modifications by shoi
 */
public class armaa_VXEffect implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false, lockNloaded = false;
    private ShipSystemAPI system;
    private ShipAPI ship;
    private SpriteAPI sprite;
    private AnimationAPI anim, aGlow, aGlow2;
    private WeaponAPI head, armL, armR, pauldronL, pauldronR, torso,vernier,dummyGun,realGun,torsoWep,lArmWep, headGlow;	
    private final Vector2f ZERO = new Vector2f();
	
	private float swingLevel = 0f;
	private boolean swinging = false;
	private boolean cooldown = false;
	private boolean deathRoll = false;
	private float swingLevelR = 0f;
	private boolean swingingR = false;
	private boolean cooldownR = false;
	private IntervalUtil animInterval = new IntervalUtil(0.012f, 0.012f);

    private float overlap = 0, heat = 0, originalRArmPos = 0f, originalArmPos = 0f, originalShoulderPos = 0f, originalRShoulderPos = 0f;
    private final float TORSO_OFFSET = -45, LEFT_ARM_OFFSET = -60, RIGHT_ARM_OFFSET = -25, MAX_OVERLAP = 10, LPAULDRONOFFSET = -5;

    public void init()
    {
        runOnce = true;
        for (WeaponAPI w : ship.getAllWeapons()) 
		{
            switch (w.getSlot().getId()) {
                case "A_GUN":
                    if(dummyGun==null){
                        dummyGun = w;
						//if(w.getBarrelSpriteAPI() != null)
						originalRArmPos = w.getSprite().getCenterY();           
                    }
                    break;
                case "TRUE_GUN":
                    if(realGun==null) {
                        realGun = w;
						//if(w.getBarrelSpriteAPI() != null)
						//originalRArmPos = w.getBarrelSpriteAPI().getCenterY();                       
                    }
                    break;
                case "WS0004":
                    if(lArmWep==null) {
                        lArmWep = w;
						//if(w.getBarrelSpriteAPI() != null)
						//originalLArmPos = w.getBarrelSpriteAPI().getCenterY();       
                    }
                    break;
                case "C_ARML":
                    if(armL==null) {
                        armL = w;
						originalArmPos = armL.getSprite().getCenterY();                        
                    }
                    break;
                case "C_ARMR":
                    if(armR==null) {
                        armR = w;                        
                    }
                    break;
                case "D_PAULDRONL":
                    if(pauldronL == null) {
                        pauldronL = w;
						originalShoulderPos = pauldronL.getSprite().getCenterY();                        
                    }
                    break;
                case "D_PAULDRONR":
                    if(pauldronR == null) {
                        pauldronR = w;
						originalRShoulderPos = pauldronL.getSprite().getCenterY();    						
                    }
                    break;
                case "E_HEAD":
                    if(head == null) {
                        head = w;                 
                    }
                    break;
				case "WS0001":
                   		torsoWep = w;
                    break;
				case "H_GLOW":
                   		headGlow = w;
                    break;
            }
			if(w.getAnimation() != null && w != dummyGun)
			{
				int skin = ship.getVariant().hasHullMod("armaa_vxSkin_AASV") ? 1:0;
				if(ship.getVariant().hasHullMod("armaa_vxSkin_midline"))
					skin = 2;	
				if(w.getAnimation().getNumFrames() > 1)
				{
					w.getAnimation().setFrame(skin);
				}
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
		if(dummyGun == null) return;
		if(engine.isPaused())return;
		if(ship.getHitpoints() <= 0 && ship.getOwner() > 0 && !deathRoll)
		{
			head.getSprite().setColor(new Color(0,0,0,0));
			dummyGun.getSprite().setColor(new Color(0,0,0,0));
			if(Math.random() > 0.50f)
			{
				pauldronL.getSprite().setColor(new Color(0,0,0,0));
				armL.getSprite().setColor(new Color(0,0,0,0));
			}
			if(Math.random() > 0.50f)
			{
				pauldronR.getSprite().setColor(new Color(0,0,0,0));
				armR.getSprite().setColor(new Color(0,0,0,0));
			}
			deathRoll = true;
			return;
		}
        anim = dummyGun.getAnimation();
		//if(ship.getHullLevel() < .5f)
			ship.syncWeaponDecalsWithArmorDamage();
		
		
		float sineA = 0, sinceB = 0, sineC=0, sinceD=0;	
		float global = ship.getFacing();
		float aim = MathUtils.getShortestRotation(global, dummyGun.getCurrAngle());
		float aim2 = MathUtils.getShortestRotation(global, armL.getCurrAngle());
		boolean noanim = false;
		if(armL.getSpec().getWeaponId().equals("armaa_aleste_rifle_left") || armL.getSpec().getWeaponId().equals("armaa_aleste_grenade_left"))
			noanim  = true;
		else if(armL.getSpec().hasTag("armaa_noAnim"))
				noanim = true;
		if(armL != null && !noanim)
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
		
		if (armR != null)
		{
			armR.setCurrAngle(dummyGun.getCurrAngle() + RIGHT_ARM_OFFSET);
//
			if(dummyGun.getChargeLevel()<1)
			{
				sineC=MagicAnim.smoothNormalizeRange(dummyGun.getChargeLevel(),0.3f,0.9f);
				sinceD=MagicAnim.smoothNormalizeRange(dummyGun.getChargeLevel(),0.3f,1f);
			} 
			else 
			{                
				sineC =1;
				sinceD =1;
			}
		}			
		
		if(torsoWep == null || torsoWep.getChargeLevel() == 0 && (ship.getSelectedGroupAPI() != null && ship.getSelectedGroupAPI().getActiveWeapon() != torsoWep ))
		{
			weapon.setCurrAngle((global + (sineA * (TORSO_OFFSET-(TORSO_OFFSET*(swingLevel/9f))) + aim * 0.3f)));
		}

		else
			weapon.setCurrAngle(torsoWep.getCurrAngle());
		
		if(realGun != null && realGun.getCooldown() > 0)
		{
			dummyGun.getSprite().setCenterY(originalRArmPos+(2*realGun.getCooldownRemaining()/realGun.getCooldown()));
			pauldronR.getSprite().setCenterY(originalRShoulderPos+(2*realGun.getCooldownRemaining()/realGun.getCooldown()));
			
		}
		
		if(lArmWep != null && lArmWep.getCooldown() > 0)
		{
			armL.getSprite().setCenterY(originalArmPos+(2*lArmWep.getCooldownRemaining()/lArmWep.getCooldown()));
			pauldronL.getSprite().setCenterY(originalShoulderPos+(2*lArmWep.getCooldownRemaining()/lArmWep.getCooldown()));
			
		}		
		
		if (pauldronR != null)
		{
			pauldronR.setCurrAngle(global + sineA * (TORSO_OFFSET-(TORSO_OFFSET*(swingLevel/9f))) * 0.5f + aim * 0.75f + RIGHT_ARM_OFFSET * 0.5f);
			//if(dummyGun.getBarrelSpriteAPI() != null)
			//pauldronR.getSprite().setCenterY(originalRShoulderPos+(2*realGun.getCooldownRemaining()/realGun.getCooldown()));
		}
	
		if (pauldronL != null)
		{
			if(armL != null)
			pauldronL.setCurrAngle(global + sineA * TORSO_OFFSET * 0.5f + aim2 * 0.75f - RIGHT_ARM_OFFSET * 0.5f);
			//pauldronL.getSprite().setCenterY(originalShoulderPos+(2*lArmWep.getCooldownRemaining()/lArmWep.getCooldown()));
		}
		
		if(headGlow != null)
		{
			headGlow.setCurrAngle(head.getCurrAngle());
			int skin = ship.getVariant().hasHullMod("armaa_vxSkin_AASV") ? 1:0;
				if(ship.getVariant().hasHullMod("armaa_vxSkin_midline"))
					skin = 1;	
			if(headGlow.getAnimation().getNumFrames() > 1)
			{
				headGlow.getAnimation().setFrame(skin);
			}
			if(skin == 0 || skin == 1)
			{
				headGlow.getSprite().setColor(new Color(155,0,00,75));
			}
			else
			{
				headGlow.getSprite().setColor(new Color(155,50,125));
			}
		}
    }
}

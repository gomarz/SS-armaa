package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.awt.Color;


public class armaa_zanacDummyWeapon2 implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
	private WeaponAPI falseWeapon,trueWeapon;
	private float originalArmPos = 0f;
    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()) 
			{
                case "A_GUN2":
                    if(falseWeapon==null) 
					{
                        falseWeapon = w;
						originalArmPos = w.getSprite().getCenterY();						
                    }
                    break;

                case "TRUE_GUN2":
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
		if(!runOnce)
			init(weapon);
		if(trueWeapon == null || falseWeapon == null)
		{
			falseWeapon = weapon;
			originalArmPos = weapon.getSprite().getCenterY();
			return;
		}
		Color invis = new Color(0f,0f,0f,0f);
		trueWeapon.ensureClonedSpec();
		int size = trueWeapon.getSpec().getTurretFireOffsets().size();
		trueWeapon.getSpec().getTurretFireOffsets().clear();
		trueWeapon.getSpec().getTurretAngleOffsets().clear();
		boolean odd = false;
		for(int i = 0; i < size; i++)
		{
			if(size <= 1)
			{
				trueWeapon.getSpec().getTurretFireOffsets().add(falseWeapon.getSpec().getTurretFireOffsets().get(2));
				trueWeapon.getSpec().getTurretAngleOffsets().add(falseWeapon.getSpec().getTurretAngleOffsets().get(2));			
			}
			else
			{
				if(!odd)
				{
					trueWeapon.getSpec().getTurretFireOffsets().add(falseWeapon.getSpec().getTurretFireOffsets().get(0));
					trueWeapon.getSpec().getTurretAngleOffsets().add(falseWeapon.getSpec().getTurretAngleOffsets().get(0));	
					odd = true;
				}
				
				else
				{
					trueWeapon.getSpec().getTurretFireOffsets().add(falseWeapon.getSpec().getTurretFireOffsets().get(1));
					trueWeapon.getSpec().getTurretAngleOffsets().add(falseWeapon.getSpec().getTurretAngleOffsets().get(1));						
				}
			}
		
		}
		
		if(trueWeapon.getSprite() != null)
			trueWeapon.getSprite().setColor(invis);
		
		if(trueWeapon.getUnderSpriteAPI() != null)
			trueWeapon.getUnderSpriteAPI().setColor(invis);		

		if(trueWeapon.getBarrelSpriteAPI() != null)
			trueWeapon.getBarrelSpriteAPI().setColor(invis);
		
		if(trueWeapon.getGlowSpriteAPI() != null)
			trueWeapon.getGlowSpriteAPI().setColor(invis);
		
		falseWeapon.setCurrAngle(trueWeapon.getCurrAngle());
		if(trueWeapon.getCooldown() > 0 && falseWeapon.getSlot().getId().equals("A_GUN2"))
			falseWeapon.getSprite().setCenterY(originalArmPos+(2*trueWeapon.getCooldownRemaining()/trueWeapon.getCooldown()));
	
    }
}

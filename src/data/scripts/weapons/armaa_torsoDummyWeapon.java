package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.awt.Color;


public class armaa_torsoDummyWeapon implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
	private WeaponAPI falseWeapon,trueWeapon;

    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()) 
			{
                case "B_TORSO":
                    if(falseWeapon==null) 
					{
                        falseWeapon = w;
                    }
                    break;

                case "WS0001":
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
			return;
		
		//if(weapon.getShip().getVariant().hasHullMod("armaa_vxSkin_midline"))
		//	return;
		
		Color invis = new Color(0f,0f,0f,0f);
		trueWeapon.ensureClonedSpec();
		/*
		int size = trueWeapon.getSpec().getTurretFireOffsets().size();

		trueWeapon.getSpec().getTurretFireOffsets().clear();
		trueWeapon.getSpec().getTurretAngleOffsets().clear();
		boolean odd = false;
		for(int i = 0; i < size; i++)
		{
	

			trueWeapon.getSpec().getTurretFireOffsets().add(falseWeapon.getSpec().getTurretFireOffsets().get(0));
			trueWeapon.getSpec().getTurretAngleOffsets().add(falseWeapon.getSpec().getTurretAngleOffsets().get(0));	
			odd = true;
				

		
		}
		*/
		if(trueWeapon.getSprite() != null)
			trueWeapon.getSprite().setColor(invis);
		
		if(trueWeapon.getUnderSpriteAPI() != null)
			trueWeapon.getUnderSpriteAPI().setColor(invis);		

		if(trueWeapon.getBarrelSpriteAPI() != null)
			trueWeapon.getBarrelSpriteAPI().setColor(invis);
		
		if(trueWeapon.getGlowSpriteAPI() != null)
			trueWeapon.getGlowSpriteAPI().setColor(invis);
		
		//falseWeapon.setCurrAngle(trueWeapon.getCurrAngle());
		
		
    }
}

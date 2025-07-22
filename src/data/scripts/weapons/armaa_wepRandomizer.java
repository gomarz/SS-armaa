package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import java.util.*; 

public class armaa_wepRandomizer implements EveryFrameWeaponEffectPlugin {
	
	private boolean runOnce = false;
	
	private final ArrayList<String> weaponsL = new ArrayList();
	{
		weaponsL.add("armaa_aleste_blade_LeftArm");
		weaponsL.add("armaa_aleste_grenade_left");
		weaponsL.add("armaa_aleste_rifle_left");
		weaponsL.add("armaa_koutoBazooka");
		weaponsL.add("armaa_leynos_ppc");
		weaponsL.add("armaa_leynosBazooka");
	}
	private final ArrayList<String> weaponsR = new ArrayList();
	{
		weaponsR.add("armaa_aleste_blade_RightArm");
		weaponsR.add("armaa_aleste_heavyrifle_right");
		weaponsR.add("armaa_leynos_minigun_right");
		weaponsR.add("armaa_kouto_minigun_right");
		weaponsR.add("armaa_aleste_rightArm");
		weaponsR.add("armaa_leynos_shotgun_right");
		weaponsR.add("armaa_leynos_crusher_right");		
	}
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
		{
			weapon.getShip().getMutableStats().getBallisticRoFMult().modifyMult("armaa_wepRandomizer",0.70f);
			weapon.getShip().getMutableStats().getMissileRoFMult().modifyMult("armaa_wepRandomizer",0.70f);
			weapon.getShip().getMutableStats().getEnergyRoFMult().modifyMult("armaa_wepRandomizer",0.70f);
			if(!runOnce)
			{
				Collections.shuffle(weaponsR);
				Collections.shuffle(weaponsL);
				Random rand = new Random();
				int size = rand.nextInt(weaponsL.size());
				weapon.getShip().getVariant().clearSlot("C_ARML");
				weapon.getShip().getVariant().addWeapon("C_ARML",weaponsL.get(size));
				rand = new Random();
				weapon.getShip().getVariant().clearSlot("A_GUN");
				weapon.getShip().getVariant().addWeapon("A_GUN",weaponsR.get(size));
				runOnce = true;
				if(Math.random() <= .25f)
				{
					weapon.getShip().getVariant().autoGenerateWeaponGroups();
					weapon.getShip().getWing().orderReturn(weapon.getShip());
				}

			}
		}
}


package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;

import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;

public class armaa_javelin implements EveryFrameWeaponEffectPlugin{ 

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
		if(weapon.getChargeLevel()==1)
		{
		                    Vector2f.add(MathUtils.getPoint(new Vector2f(), 180, weapon.getCurrAngle()+180), weapon.getShip().getVelocity(), weapon.getShip().getVelocity());

                      Vector2f drift = MathUtils.getPoint(new Vector2f(), MathUtils.getRandomNumberInRange(0, 150), weapon.getCurrAngle());
                        Vector2f.add(drift, new Vector2f(weapon.getShip().getVelocity()), drift);

		}
	}
}

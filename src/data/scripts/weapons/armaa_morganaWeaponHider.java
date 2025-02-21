package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.Global;
import java.awt.Color;


public class armaa_morganaWeaponHider implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
	private WeaponAPI falseWeapon,trueWeapon;
	private float originalArmPos = 0f;
    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            if(w.getType() != WeaponType.DECORATIVE)
			{
				w.getSprite().setColor(new Color(0,0,0,0));
			}
        }
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		if(!runOnce)
			init(weapon);
    }
}

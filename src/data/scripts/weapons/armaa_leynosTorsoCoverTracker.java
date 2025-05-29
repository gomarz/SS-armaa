package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.*;


public class armaa_leynosTorsoCoverTracker implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
	private WeaponAPI chestWeapon, torso, head;
	private float originalPos, originalHeadPos, originalX, originalY;
	private List<Vector2f> originalOffset;
	private float retractLevel = 10f;
	private float currRetract = 0f;

    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()) 
			{
                case "WS002":
                    if(chestWeapon==null) 
					{
                        chestWeapon = w;
                    }
                    break;
				case "B_TORSO_T":
					originalPos = w.getSprite().getCenterY();
					break;
                case "B_TORSO":
                    if(torso==null) 
					{
                        torso = w;
                    }
                    break;
                case "E_HEAD":
                    if(head==null) 
					{
						originalOffset = w.getSpec().getTurretFireOffsets();
						originalX = originalOffset.get(0).getX();
						originalY = originalOffset.get(0).getY();
						originalHeadPos = w.getSprite().getCenterY();					
                        head = w;
                    }
                    break;
            }
        }
    }
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		if(weapon.getShip().getOwner() == -1 || !weapon.getShip().isAlive())
			return;
		if(engine.isPaused())
			return;
		if(!runOnce)
		{
			init(weapon);
			head.ensureClonedSpec();
		}
		if(chestWeapon == null)
			return;
		
		weapon.setCurrAngle(torso.getCurrAngle());
		if(chestWeapon.isFiring() || engine.getShipPlayerIsTransferringCommandTo() == weapon.getShip())
		{
			if(currRetract < retractLevel)
			{
				currRetract+=0.5f;
			}
			else if(currRetract > retractLevel)
			{
				currRetract = retractLevel;
			}
		}
		else
		{
			if(currRetract > 0)
				currRetract-=0.5f;
		}
		weapon.getSprite().setCenterY(originalPos+(currRetract));

		if(head != null && originalOffset != null)
		{
			//head.getSprite().setCenterY(originalHeadPos+currRetract);
			//head.getSprite().renderAtCenter(head.getSprite().getCenterX(),originalHeadPos+(currRetract)); 
			//head.getSpec().getTurretFireOffsets().clear();
			//float x = originalX;
			//float y = originalY*-1;
			//head.getSpec().getTurretFireOffsets().add(new Vector2f(x,y+currRetract));
			//x = originalX;
			//y = originalY;
			//head.getSpec().getTurretFireOffsets().add(new Vector2f(x,y+currRetract));			
		}		
		head.getSprite().setColor(new Color(1f,1f,1f,MathUtils.clamp(1f - (currRetract / retractLevel), 0, 1)));
		
    }
}

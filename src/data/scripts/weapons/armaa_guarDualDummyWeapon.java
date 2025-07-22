package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.*;


public class armaa_guarDualDummyWeapon implements EveryFrameWeaponEffectPlugin {

    private boolean runOnce = false;
	private WeaponAPI falseWeapon,trueWeapon,gunF, head, headWeapon;
	private List<Vector2f> ogOffset;
	private List<Float> ogAngleOffsets;

    public void init(WeaponAPI weapon)
    {
        runOnce = true;
		//need to grab another weapon so some effects are properly rendered, like lighting from glib
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) 
		{
            switch (w.getSlot().getId()) 
			{
                case "E_HEAD":
                    if(head==null) 
					{
                        head = w;
                    }
                    break;
                case "WS0001":
                    if(headWeapon==null) 
					{
                        headWeapon = w;
                    }
                    break;
                case "A_GUN":
                    if(falseWeapon==null) 
					{
                        falseWeapon = w;
                    }
                    break;

                case "F_GUN":
                    if(gunF==null) 
					{
                          gunF = w;
                    }
                    break;					

                case "TRUE_GUN":
                    if(trueWeapon==null) 
					{
                        trueWeapon = w;
						ogOffset = trueWeapon.getSpec().getTurretFireOffsets();
						ogAngleOffsets = trueWeapon.getSpec().getTurretAngleOffsets(); 
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
        ShipAPI ship = weapon.getShip();
        boolean isRobot = false;
        if(engine.getCustomData().containsKey("armaa_tranformState_"+ship.getId()))
                isRobot = (boolean)engine.getCustomData().get("armaa_tranformState_"+ship.getId());		
        Color invis = new Color(0f,0f,0f,0f);
        if(trueWeapon  != null && falseWeapon != null)
        {
            trueWeapon.ensureClonedSpec();
            int size = trueWeapon.getSpec().getTurretFireOffsets().size();

            if(size > 1 && falseWeapon.getAnimation() != null &&falseWeapon.getAnimation().getNumFrames() > 0)
                falseWeapon.getAnimation().setFrame(1);
            if(isRobot)
            {
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
            }
            else
            {
                trueWeapon.getSpec().getTurretFireOffsets().clear();
                trueWeapon.getSpec().getTurretAngleOffsets().clear();
                size = ogOffset.size();
                for(int i = 0; i < size; i++)
                {
                        trueWeapon.getSpec().getTurretFireOffsets().add(ogOffset.get(i));

                }
                size = ogAngleOffsets.size();
                for(int i = 0; i< size; i++)
                {
                        trueWeapon.getSpec().getTurretAngleOffsets().add(ogAngleOffsets.get(i));	
                }				
            }
        }
        if(trueWeapon != null)
        {
			trueWeapon.setWeaponGlowHeightMult(0.5f); 
			trueWeapon.setWeaponGlowWidthMult(0.5f); 			
            if(trueWeapon.getSprite() != null)
                    trueWeapon.getSprite().setColor(invis);

            if(trueWeapon.getUnderSpriteAPI() != null)
                    trueWeapon.getUnderSpriteAPI().setColor(invis);		

            if(trueWeapon.getBarrelSpriteAPI() != null)
                    trueWeapon.getBarrelSpriteAPI().setColor(invis);

            if(trueWeapon.getGlowSpriteAPI() != null)
                    trueWeapon.getGlowSpriteAPI().setColor(invis);

            falseWeapon.setCurrAngle(trueWeapon.getCurrAngle());
        }
        if(headWeapon != null && head != null)
        {
            headWeapon.ensureClonedSpec();
            int size = headWeapon.getSpec().getTurretFireOffsets().size();

            if(size > 1 && head.getAnimation() != null &&head.getAnimation().getNumFrames() > 0)
                head.getAnimation().setFrame(1);
			headWeapon.getSpec().getTurretFireOffsets().clear();
			headWeapon.getSpec().getTurretAngleOffsets().clear();
			boolean odd = false;
			for(int i = 0; i < size; i++)
			{
				if(size <= 1)
				{
					headWeapon.getSpec().getTurretFireOffsets().add(head.getSpec().getTurretFireOffsets().get(2));
					headWeapon.getSpec().getTurretAngleOffsets().add(head.getSpec().getTurretAngleOffsets().get(2));			
				}
				else
				{
					if(!odd)
					{
							headWeapon.getSpec().getTurretFireOffsets().add(head.getSpec().getTurretFireOffsets().get(0));
							headWeapon.getSpec().getTurretAngleOffsets().add(head.getSpec().getTurretAngleOffsets().get(0));	
							odd = true;
					}

					else
					{
							headWeapon.getSpec().getTurretFireOffsets().add(head.getSpec().getTurretFireOffsets().get(1));
							headWeapon.getSpec().getTurretAngleOffsets().add(head.getSpec().getTurretAngleOffsets().get(1));						
					}
				}
			}
        }
        if(headWeapon != null)
        {
			headWeapon.setWeaponGlowHeightMult(0.5f); 
			headWeapon.setWeaponGlowWidthMult(0.5f); 			
            if(headWeapon.getSprite() != null)
                    headWeapon.getSprite().setColor(invis);

            if(headWeapon.getUnderSpriteAPI() != null)
                    headWeapon.getUnderSpriteAPI().setColor(invis);		

            if(headWeapon.getBarrelSpriteAPI() != null)
                    headWeapon.getBarrelSpriteAPI().setColor(invis);

            if(headWeapon.getGlowSpriteAPI() != null)
                    headWeapon.getGlowSpriteAPI().setColor(invis);

            head.setCurrAngle(headWeapon.getCurrAngle());
            if(!isRobot)
                    headWeapon.setForceNoFireOneFrame(true); 
            else
            {
                    if(headWeapon.isDisabled())
                            headWeapon.repair();
            }
        }
    }
}

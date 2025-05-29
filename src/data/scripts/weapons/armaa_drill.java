package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;

public class armaa_drill implements EveryFrameWeaponEffectPlugin {

	private float atkAngle = 0f;
	private boolean runOnce = false;
	private float ogSpikePos = 0f;
	private float wepRecoilMax = 30f;
	private float recoil = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{
		if(!runOnce)
		{
			ogSpikePos = weapon.getBarrelSpriteAPI().getCenterY();
			runOnce = true;
		}
		if(engine.isPaused())
			return;
		ShipAPI ship = weapon.getShip();
		if(engine.getCustomData().get("armaa_drillHit_"+ship.getId()) != null)
		{	
			engine.getCustomData().remove("armaa_drillHit_"+ship.getId());
			recoil=Math.max(0,recoil-10);
		}
		else if(recoil < wepRecoilMax)
				recoil++;

		weapon.getBarrelSpriteAPI().setCenterY(ogSpikePos-(recoil*weapon.getCooldownRemaining()/weapon.getCooldown()));
		
		if(ship.getShipTarget() != null && MathUtils.getDistance(ship,ship.getShipTarget()) < 500 && !weapon.isDisabled())
		{
			if(ship.getAI() != null )
			{
				ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
				Global.getCombatEngine().headInDirectionWithoutTurning(ship, weapon.getCurrAngle(), ship.getMaxSpeed());
			}					
		}			
		if(weapon.isFiring() && ship.getAI() != null && !ship.isFighter())
		{
			ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT); 
			ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);
			ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);

			float angle = ship.getFacing();
			atkAngle = weapon.getCurrAngle() - 90;
			if(angle != atkAngle)
			{
				float angleDifference = MathUtils.getShortestRotation(angle, atkAngle);
				float turnDirection = (angleDifference > 0) ? 1 : -1;				
				if(ship.getShipTarget() != null)
				{
					angleDifference=MathUtils.getShortestRotation(
                        angle,
                        VectorUtils.getAngle(ship.getLocation(), ship.getShipTarget().getLocation())+90f*turnDirection

					);			
				}
				angleDifference = (angleDifference + 180) % 360 - 180;				
				float bonus = (angleDifference > 0) ? 0.8f : -0.8f;
				// Set the angle to 0 degrees
				if (Math.abs(angleDifference) > 0.2f) 
				{
					float newFacing = MathUtils.clampAngle(angle + (turnDirection+bonus));
					ship.setFacing(newFacing);
				}			
			}
		}
	
    }
}

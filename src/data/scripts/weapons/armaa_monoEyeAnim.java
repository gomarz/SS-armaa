package data.scripts.weapons;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;

public class armaa_monoEyeAnim implements EveryFrameWeaponEffectPlugin {

    private boolean isAnimating = false;
	private IntervalUtil interval = new IntervalUtil(1f,20f);
	private IntervalUtil animInterval = new IntervalUtil(0.06f,0.08f);
	private int target = 0;
	private int currFrame = 0;

	public int getAngle(int a, int b)
	{
		int angle = a + b;
		if(angle > 360)
			angle%= 360;
		
		return angle;
	}

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) 
	{			
		if(interval.intervalElapsed() && !isAnimating)
		{

			isAnimating = true;
			
			currFrame = weapon.getAnimation().getFrame();
			target = currFrame <= 4 ? 8 : 0;
			animInterval = new IntervalUtil(0.055f,0.08f);
			if(weapon.getShip().getShipTarget() != null)
			{
				float facing = weapon.getCurrAngle();
				float targetAngle = VectorUtils.getAngle(weapon.getShip().getLocation(),weapon.getShip().getShipTarget().getLocation());
				
				float difference = Math.abs(facing - targetAngle);

				float increments = 4;
				if (facing > targetAngle) 
				{
					target = (int)Math.floor(Math.min(5f+ (difference / 25f),8f));			
				} 
				else if (facing < targetAngle) 
				{
					target = (int)Math.floor(Math.max(3f- (difference / 25f),0f));
				}
			}
			else if(Math.random() <= 0.25f)
			{
				if(target == 8)
					target = MathUtils.getRandomNumberInRange(4, 6);
				
				else
					target = MathUtils.getRandomNumberInRange(0,4);
			}
			if(Math.random() <= 0.25f)
			{
				Global.getSoundPlayer().playSound("armaa_monoeye_move", 0.8f, 0.8f, weapon.getLocation(), weapon.getShip().getVelocity());				
			}
		}
		
		if(!isAnimating)
		{
			interval.advance(amount);
		}
		
		else
		{
			animInterval.advance(amount);
			if(animInterval.intervalElapsed())
			{
				currFrame = weapon.getAnimation().getFrame();
				
				if(currFrame == target)
				{
					interval = new IntervalUtil(5f,15f);
					isAnimating = false;
				}
				
				else
				{
					if(currFrame < target)
						weapon.getAnimation().setFrame(Math.min(currFrame+1,8));
					
					else
						weapon.getAnimation().setFrame(Math.max(0,currFrame-1));
				}
				
			}
		}
				
    }
}

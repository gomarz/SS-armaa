package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.combat.CombatUtils;
import java.util.ArrayList;
import java.util.List;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;


public class armaa_shotgunEveryFrameEffectCrimson implements EveryFrameWeaponEffectPlugin {

    public static final int SHOTS = 20;
    public static final float SPREAD = 20f;
    public static final float PUSH_CONSTANT = 12800f;
    private int lastAmmo = 0;
    private boolean init = false;
    private boolean reloading = true;
    private IntervalUtil cockSound = new IntervalUtil(0.33f,0.38f);
    private IntervalUtil reloadTime = new IntervalUtil(2.8f,2.85f);
	private DamagingProjectileAPI  dummy;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (!init){
            init = true;
            lastAmmo = weapon.getAmmo();
        }
		
		ShipwideAIFlags flags = weapon.getShip().getAIFlags();
		/*Attempted fix to make altagrave AI les suicidal. Probably should be in a different script, but not sure if this even has any kind of effect*/
        if (weapon.getShip().getFluxTracker().getFluxLevel() >= 0.60f) {
            flags.setFlag(AIFlags.BACK_OFF, amount);
	    flags.setFlag(AIFlags.RUN_QUICKLY,amount);
		flags.setFlag(AIFlags.BACKING_OFF,amount);
        }

       // if (weapon.getAmmo() == 0){
            // Removes dummy shot

                List<CombatEntityAPI> targets = new ArrayList<>(Global.getCombatEngine().getProjectiles().size() / 4);
                targets.addAll(CombatUtils.getProjectilesWithinRange(weapon.getShip().getLocation(), 100f));
                targets.addAll(CombatUtils.getMissilesWithinRange(weapon.getShip().getLocation(), 100f));
            for (CombatEntityAPI p : targets)
			{
				DamagingProjectileAPI q = (DamagingProjectileAPI)p;
                if (q.getWeapon() == weapon)
				{
					dummy = q;
					engine.removeEntity(q);
				}
            }

            // makes kinetic and HE shots
			if(dummy != null)
			{
				for (int f = 0; f < SHOTS; f++){
					float offset = ((float)Math.random() * SPREAD) - (SPREAD * 0.5f);
					float angle = weapon.getCurrAngle() + offset;
					String weaponId = "armaa_altagrave_shotgun_pellet";
					Vector2f muzzleLocation = calculateMuzzle(weapon);
					DamagingProjectileAPI proj = (DamagingProjectileAPI)engine.spawnProjectile(weapon.getShip(),weapon,weaponId,dummy.getLocation(),angle,weapon.getShip().getVelocity());
					float randomSpeed = (float)Math.random() * 0.4f + 0.8f;
					proj.getVelocity().scale(randomSpeed);
				}
				dummy = null;
			}

            // for sound fx
            if (weapon.getAmmo() > 0)
                reloading = true;
            else
                reloadTime.setElapsed(0f);
        //}

        // cock sound plays when first shell is loaded after reaching 0 ammo
        if (lastAmmo == 0 && weapon.getAmmo() > 0)
		{
            Global.getSoundPlayer().playSound("armaa_shotgun_cock", 0.5f, 2.8f, weapon.getLocation(), weapon.getShip().getVelocity());
            weapon.setRemainingCooldownTo(1f);
        }

        lastAmmo = weapon.getAmmo();

        // cock sound plays about 0.35 sec after firing if there is at least 1 ammo
        if (reloading){
            cockSound.advance(amount);
            if (cockSound.intervalElapsed()) {
                reloading = false;
              //  Global.getSoundPlayer().playSound("KT_boomstick_cock", 0.5f, 2.8f, weapon.getLocation(), weapon.getShip().getVelocity());
            }
        }
    }

    private Vector2f calculateMuzzle(WeaponAPI weapon){
        float muzzle;
        if (weapon.getSlot().isHardpoint()) {
            muzzle = weapon.getSpec().getHardpointFireOffsets().get(0).getX();
        } else {
            muzzle = weapon.getSpec().getTurretFireOffsets().get(0).getX();
        }
        double angle = Math.toRadians(weapon.getCurrAngle());
        Vector2f dir = new Vector2f((float)Math.cos(angle),(float)Math.sin(angle));
        if (dir.lengthSquared() > 0f) dir.normalise();
        dir.scale(muzzle);
        Vector2f loc = new Vector2f(weapon.getLocation().getX(),weapon.getLocation().getY()-20);
        return Vector2f.add(loc,dir,new Vector2f());

    }
}
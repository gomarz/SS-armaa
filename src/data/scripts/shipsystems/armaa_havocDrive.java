package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.VectorUtils;

public class armaa_havocDrive extends BaseShipSystemScript {
	    private static final float VEL_MIN = 0.2f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.4f;

    // one half of the angle. used internally, don't mess with thos
	    private static final float CONE_ANGLE = 200f;
    private static final float A_2 = CONE_ANGLE / 2;
	public static final float MAX_TIME_MULT = 2f;
	public static final float MIN_TIME_MULT = 0.1f;
	public static final float MASS_MULT = 3f;
	private Float mass = null;
	
	public static float getMaxTimeMult(MutableShipStatsAPI stats) 
	{
		return 1f + (MAX_TIME_MULT - 1f);
	}		
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) 
	{
		
		ShipAPI ship = (ShipAPI)stats.getEntity();
        if (mass == null) {
            mass = ship.getMass();
        }

		Color color = Color.white;
		if(ship.getSystem() == null || !ship.getSystem().getId().equals("armaa_havocDrive"))
		{
			color = ship.getPhaseCloak().getSpecAPI().getEngineGlowColor();
		}

		else
			color = ship.getSystem().getSpecAPI().getEngineGlowColor();
		
		Color coreColor = new Color(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, 1f*effectLevel);
		if((float) Math.random() <= 0.7f)
			for(int i=0; i<ship.getEngineController().getShipEngines().size(); i++)
			{
				float speed = 450f;
				float facing = ship.getFacing();
				float angle = MathUtils.getRandomNumberInRange(facing - A_2,
						facing + A_2);
				float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN,
						speed * -VEL_MAX);
				Vector2f vector = MathUtils.getPointOnCircumference(null,
						vel,
						angle);
						
				Vector2f origin = new Vector2f(ship.getEngineController().getShipEngines().get(i).getLocation());
				Vector2f offset = new Vector2f(-5f, -0f);
				VectorUtils.rotate(offset, ship.getFacing(), offset);
				Vector2f.add(offset, origin, origin);								

			   Global.getCombatEngine().addHitParticle(
						origin,
						vector,
						MathUtils.getRandomNumberInRange(2, 5),
						1f*effectLevel,
						MathUtils.getRandomNumberInRange(0.4f, 0.8f),
						coreColor);

			}
		
		if(state == ShipSystemStatsScript.State.IN)
		{
			float shipTimeMult = 1f + (getMaxTimeMult(stats) - 1f) * (1f-effectLevel);
			if(ship.isFighter())
				stats.getTimeMult().modifyMult(id, shipTimeMult);
			boolean player = false;
			player = ship == Global.getCombatEngine().getPlayerShip();
            if (ship.getMass() == mass) 
			{
                ship.setMass(mass * MASS_MULT);
            }			
			stats.getMaxSpeed().modifyFlat(id, 300f * effectLevel);
			stats.getAcceleration().modifyFlat(id, 300f * effectLevel);
			stats.getDeceleration().modifyFlat(id, 300f * effectLevel);
			
			/*
			if (player) 
			{
				Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);

			} 
			else 
			{
				Global.getCombatEngine().getTimeMult().unmodify(id);
			}
			*/			
		}
		
		else if (state == ShipSystemStatsScript.State.OUT) {
			stats.getMaxSpeed().modifyFlat(id, 300f * (1-effectLevel));
			stats.getAcceleration().modifyFlat(id, 300f * (1-effectLevel));
			stats.getDeceleration().modifyFlat(id, 300f * (1-effectLevel));
			Global.getCombatEngine().getTimeMult().unmodify(id);
			ship.setMass(mass);
		} else {
			stats.getMaxSpeed().modifyFlat(id, 300f * effectLevel);
			stats.getAcceleration().modifyFlat(id, 300f * effectLevel);
			stats.getDeceleration().modifyFlat(id, 300f * effectLevel);


			if(ship != null)
			{
				if (ship instanceof ShipAPI && false) {
					String key = ship.getId() + "_" + id;
					Object test = Global.getCombatEngine().getCustomData().get(key);
					if (state == State.IN) {
						if (test == null && effectLevel > 0.2f) {
							Global.getCombatEngine().getCustomData().put(key, new Object());
							ship.getEngineController().getExtendLengthFraction().advance(1f);
							for (ShipEngineAPI engine : ship.getEngineController().getShipEngines()) {
								if (engine.isSystemActivated()) {
									ship.getEngineController().setFlameLevel(engine.getEngineSlot(), 1f);
								}
							}
						}
					} else {
						Global.getCombatEngine().getCustomData().remove(key);
					}
				}
			}
		}
	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		stats.getMaxSpeed().unmodify(id);
		stats.getAcceleration().unmodify(id);
		Global.getCombatEngine().getTimeMult().unmodify(id);
		stats.getTimeMult().unmodify(id);
	}
	
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index == 0) {
			return new StatusData("increased engine power", false);
		}
		return null;
	}
	
	
	public float getActiveOverride(ShipAPI ship) {

		return -1;
	}
	public float getInOverride(ShipAPI ship) {
		return -1;
	}
	public float getOutOverride(ShipAPI ship) {
		return -1;
	}
	
	public float getRegenOverride(ShipAPI ship) {
		return -1;
	}

}



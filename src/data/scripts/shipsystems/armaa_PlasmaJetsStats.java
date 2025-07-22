package data.scripts.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.VectorUtils;

public class armaa_PlasmaJetsStats extends BaseShipSystemScript {
	    private static final float VEL_MIN = 0.2f;
    // constant that effects the upper end of the particle velocity
    private static final float VEL_MAX = 0.4f;

    // one half of the angle. used internally, don't mess with thos
	    private static final float CONE_ANGLE = 200f;
    private static final float A_2 = CONE_ANGLE / 2;
	public static final float MAX_TIME_MULT = 1.15f;
	public static final float MIN_TIME_MULT = 0.1f;
	public static final float DEF_BEAM_MULT = 0.45f;
	
	public static float getMaxTimeMult(MutableShipStatsAPI stats) {
		return 1f + (MAX_TIME_MULT - 1f);
	}		
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		if (state == ShipSystemStatsScript.State.OUT) {
			stats.getMaxSpeed().unmodify(id); // to slow down ship to its regular top speed while powering drive down
			Global.getCombatEngine().getTimeMult().unmodify(id);
			stats.getTimeMult().unmodify(id);
		} else {
			stats.getMaxSpeed().modifyFlat(id, 300f * effectLevel);
			stats.getAcceleration().modifyFlat(id, 300f * effectLevel);
			stats.getDeceleration().modifyFlat(id, 300f * effectLevel);
			float shipTimeMult = 1f + (getMaxTimeMult(stats) - 1f) * effectLevel;
			stats.getTimeMult().modifyMult(id, shipTimeMult);
			ShipAPI ship = (ShipAPI)stats.getEntity();
			if(ship != null)
			{

				boolean player = false;
				player = ship == Global.getCombatEngine().getPlayerShip();
				if (player) 
				{
					Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);

				} 
			else 
			{
				Global.getCombatEngine().getTimeMult().unmodify(id);
			}
			Color color = Color.white;
			if(!ship.getSystem().getId().equals("armaa_plasmaJets"))
			{
				color = ship.getPhaseCloak().getSpecAPI().getEngineGlowColor();
			}

			else
				color = ship.getSystem().getSpecAPI().getEngineGlowColor();
			
			Color coreColor = new Color(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, 1f*effectLevel);
			if((float) Math.random() <= 0.5f)
				for(int i=0; i<ship.getEngineController().getShipEngines().size(); i++)
				{
					float speed = 500f;
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
			}
		}
	}
	public void unapply(MutableShipStatsAPI stats, String id) {
		stats.getMaxSpeed().unmodify(id);
		stats.getAcceleration().unmodify(id);
		Global.getCombatEngine().getTimeMult().unmodify(id);
		stats.getTimeMult().unmodify(id);
		stats.getBeamDamageTakenMult().unmodify(id);
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



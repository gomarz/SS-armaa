package data.scripts.ai;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * Activation AI for the aura taser.
 *
 * The vanilla "EMP" aiType cannot be used here: its AI class casts the system
 * object to the concrete EMP system class, and a "STAT_MOD" system is a
 * different class, so it throws ClassCastException at BasicShipAI construction.
 * Hence a hand-rolled script.
 *
 * There is no positioning decision to make - the aura is omnidirectional - so
 * this only has to answer "is there enough inside the radius to be worth the
 * flux". Everything else is the ship AI's problem.
 */
public class armaa_TaserAI implements ShipSystemAIScript {

	// weight per target class; system fires once these sum past ON_THRESHOLD
	private static final float SHIP_WEIGHT = 1.2f;
	private static final float FIGHTER_WEIGHT = 0.35f;
	private static final float MISSILE_WEIGHT = 0.2f;

	private static final float ON_THRESHOLD = 1f;
	private static final float MAX_FLUX_TO_FIRE = 0.75f;

	private ShipAPI ship;
	private ShipSystemAPI system;
	private CombatEngineAPI engine;

	private final IntervalUtil tracker = new IntervalUtil(0.2f, 0.35f);

	public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
		this.ship = ship;
		this.system = system;
		this.engine = engine;
	}

	public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
		if (engine == null || engine.isPaused()) return;
		if (ship == null || system == null) return;
		if (!ship.isAlive()) return;

		tracker.advance(amount);
		if (!tracker.intervalElapsed()) return;

		// fixed-duration system: nothing to do until it is off cooldown again
		if (system.isActive() || system.isOutOfAmmo()) return;
		if (system.getCooldownRemaining() > 0f) return;
		if (ship.getFluxLevel() > MAX_FLUX_TO_FIRE) return;

		if (score() >= ON_THRESHOLD) {
			ship.useSystem();
		}
	}

	/**
	 * Range is read off the spec rather than hardcoded, so hullmods and skills
	 * that touch getSystemRangeBonus keep the AI and the effect script in
	 * agreement about how big the aura actually is.
	 */
	private float score() {
		float range = system.getSpecAPI().getRange(ship.getMutableStats());
		float rangeSq = range * range;
		Vector2f center = ship.getLocation();
		float total = 0f;

		for (ShipAPI other : engine.getShips()) {
			if (other == ship) continue;
			if (other.getOwner() == ship.getOwner()) continue;
			if (!other.isAlive() || other.isHulk() || other.isPiece()) continue;
			if (other.isPhased()) continue; // arcs cannot touch it anyway
			if (Misc.getDistanceSq(center, other.getLocation()) > rangeSq) continue;
			total += other.isFighter() ? FIGHTER_WEIGHT : SHIP_WEIGHT;
			if (total >= ON_THRESHOLD) return total; // early out
		}

		for (MissileAPI m : engine.getMissiles()) {
			if (m.getOwner() == ship.getOwner()) continue;
			if (m.isFizzling() || m.isFlare()) continue;
			if (Misc.getDistanceSq(center, m.getLocation()) > rangeSq) continue;
			total += MISSILE_WEIGHT;
			if (total >= ON_THRESHOLD) return total;
		}

		return total;
	}
}

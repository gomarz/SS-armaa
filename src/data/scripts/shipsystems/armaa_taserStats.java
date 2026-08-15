package data.scripts.shipsystems;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * Aura taser.
 *
 * Every SYSTEM/SMALL slot is an emitter. With nothing in reach they discharge
 * into empty space at random bearings ahead of the ship, the way the vanilla
 * EMP emitter idles. Once something enters the aura, the emitters lash it
 * directly and the idle discharge stops.
 *
 * All tuning lives in the .system file. Nothing here is hardcoded except the
 * defaults in readConfig(), which exist only so a missing key can't NPE you.
 *
 * One instance per ship system, so instance fields are safe for per-ship state
 * (same pattern vanilla's MoteControlScript uses).
 */
public class armaa_taserStats extends BaseShipSystemScript {

	private List<WeaponSlotAPI> emitters = null;
	private boolean configLoaded = false;

	private float rimIntervalMin, rimIntervalMax;
	private float zapIntervalMin, zapIntervalMax;
	private int   maxTargetsPerTick;
	private float fighterEmpMult, missileEmpMult;
	private float strandSkipChance, rimThickness, zapThickness;
	private float idleArcMinRange, idleArcMaxRange, idleArcSpread, idleArcSpeed;
	private int   idleArcsPerTick;
	private float rimSegmentLengthMult, rimZigZagReduction, rimMaxZigZag, rimGlowSize;
	private boolean pierceShields;

	private IntervalUtil rimTick;
	private IntervalUtil zapTick;

	// reused every frame; do not allocate these in the hot path
	private final List<Vector2f> emitterPos = new ArrayList<Vector2f>();
	private final List<Vector2f> origins = new ArrayList<Vector2f>();
	private final List<CombatEntityAPI> targets = new ArrayList<CombatEntityAPI>();
	private final Vector2f forward = new Vector2f();
	private EmpArcParams zapParams;
	private EmpArcParams rimParams;

	@Override
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		if (!(stats.getEntity() instanceof ShipAPI)) return;
		ShipAPI ship = (ShipAPI) stats.getEntity();
		CombatEngineAPI engine = Global.getCombatEngine();
		if (engine == null || engine.isPaused()) return;

		ShipSystemAPI system = ship.getSystem();
		if (system == null) return;
		ShipSystemSpecAPI spec = system.getSpecAPI();

		readConfig(spec);
		resolveEmitters(ship);
		if (emitters.isEmpty()) return;

		float amount = engine.getElapsedInLastFrame();

		// Live emitter positions. computePosition() handles facing and sprite
		// center offset, so this stays glued to the antennas as the ship turns.
		emitterPos.clear();
		for (WeaponSlotAPI slot : emitters) {
			emitterPos.add(slot.computePosition(ship));
		}

		forward.set(Misc.getUnitVectorAtDegreeAngle(ship.getFacing()));

		// Zap origins are the prongs themselves - nothing else.
		origins.clear();
		origins.addAll(emitterPos);

		if (state == State.COOLDOWN) return;

		if (state == State.IDLE) {
			// Needs "runScriptWhileIdle":true. Faint crackle at the antennas
			// only - no discharge arcs, no damage pass.
			rimTick.advance(amount * 0.35f);
			if (rimTick.intervalElapsed()) idleFlicker(engine, ship, spec);
			return;
		}

		// ---- active: IN / ACTIVE / OUT ----
		float range = spec.getRange(stats);

		zapTick.advance(amount);
		if (zapTick.intervalElapsed()) {
			gatherTargets(engine, ship, range);
			int count = Math.min(targets.size(), maxTargetsPerTick);
			for (int i = 0; i < count; i++) {
				zap(engine, ship, spec, targets.get(i), range, effectLevel);
			}
		}

		// Idle discharge: when nothing is in reach the emitters stab out at
		// nothing in particular, the way the vanilla emitter does. Once there
		// are targets the zaps carry the visual on their own.
		rimTick.advance(amount);
		if (rimTick.intervalElapsed() && targets.isEmpty()) {
			drawIdleArcs(engine, ship, spec, range, effectLevel);
		}
	}

	// ------------------------------------------------------------------
	// idle discharge - cosmetic only, never touches damage
	// ------------------------------------------------------------------
	private void drawIdleArcs(CombatEngineAPI engine, ShipAPI ship, ShipSystemSpecAPI spec,
							  float range, float level) {
		if (emitterPos.isEmpty()) return;
		Color fringe = spec.getEffectColor1();
		Color core = spec.getEffectColor2();
		float facing = ship.getFacing();

		for (int n = 0; n < idleArcsPerTick; n++) {
			if ((float) Math.random() < strandSkipChance) continue;

			Vector2f from = emitterPos.get((int) (Math.random() * emitterPos.size()));

			// random bearing within idleArcSpread degrees of the bow, random
			// reach inside the aura - so the discharge stays in the volume the
			// system actually covers instead of pointing anywhere
			float angle = facing + (float) (Math.random() - 0.5) * idleArcSpread;
			float dist = range * (idleArcMinRange
					+ (float) Math.random() * (idleArcMaxRange - idleArcMinRange));
			Vector2f dir = Misc.getUnitVectorAtDegreeAngle(angle);
			Vector2f to = new Vector2f(from.x + dir.x * dist, from.y + dir.y * dist);

			float thickness = rimThickness * (0.7f + (float) Math.random() * 0.6f) * level;

			// bright spot travels the length of the arc, flicker eases off
			// with distance
			rimParams.flickerRateMult = Math.max(0.3f, 0.6f - dist / 3000f);
			// how long the arc takes to reach full extension - this is what
			// makes it stab outward instead of appearing whole
			rimParams.movementDurOverride = Math.max(0.05f, dist / idleArcSpeed);

			EmpArcEntityAPI arc = engine.spawnEmpArcVisual(
					from, ship, to, null, thickness, fringe, core, rimParams);
			// MUST be the boolean overload when toAnchor is null. The no-arg
			// version leaves movement mode off, and that render path resolves
			// the endpoint through the (null) anchor - instant NPE in
			// EmpArcEntity.render. Vanilla's RiftLightningEffect and
			// ShroudedThunderheadHullmod both do it this way.
			arc.setSingleFlickerMode(true);
			// NOT setUpdateFromOffsetEveryFrame - the arc should strike and
			// stay put. Re-resolving the offsets every frame drags the whole
			// arc along with the ship, which reads as rubber-banding.
			arc.setRenderGlowAtStart(true);
			arc.setRenderGlowAtEnd(false); // nothing is being hit out there
			arc.setFadedOutAtStart(true);  // tapers into empty space
		}
	}

	private void idleFlicker(CombatEngineAPI engine, ShipAPI ship, ShipSystemSpecAPI spec) {
		if (emitterPos.isEmpty()) return;
		int i = (int) (Math.random() * emitterPos.size());
		engine.addSmoothParticle(new Vector2f(emitterPos.get(i)), ship.getVelocity(),
				6f + (float) Math.random() * 4f, 0.5f, 0.2f, spec.getEffectColor2());
	}

	// ------------------------------------------------------------------
	// target gathering - nearest first, tiered by what it is
	// ------------------------------------------------------------------
	private void gatherTargets(CombatEngineAPI engine, ShipAPI ship, float range) {
		targets.clear();
		float rangeSq = range * range;
		final Vector2f center = ship.getLocation();

		for (ShipAPI other : engine.getShips()) {
			if (other == ship) continue;
			if (other.getOwner() == ship.getOwner()) continue;
			if (!other.isAlive() || other.isHulk() || other.isPiece()) continue;
			if (other.isPhased()) continue;
			if (Misc.getDistanceSq(center, other.getLocation()) > rangeSq) continue;
			targets.add(other);
		}

		for (MissileAPI m : engine.getMissiles()) {
			if (m.getOwner() == ship.getOwner()) continue;
			if (m.isFizzling() || m.isFlare()) continue; // don't waste arcs on flares
			if (Misc.getDistanceSq(center, m.getLocation()) > rangeSq) continue;
			targets.add(m);
		}

		Collections.sort(targets, new Comparator<CombatEntityAPI>() {
			public int compare(CombatEntityAPI a, CombatEntityAPI b) {
				return Float.compare(
						Misc.getDistanceSq(center, a.getLocation()),
						Misc.getDistanceSq(center, b.getLocation()));
			}
		});
	}

	// ------------------------------------------------------------------
	// the actual zap, from whichever emitter is nearest the target
	// ------------------------------------------------------------------
	private void zap(CombatEngineAPI engine, ShipAPI ship, ShipSystemSpecAPI spec,
					 CombatEntityAPI target, float range, float level) {

		Vector2f from = origins.get(0);
		float best = Float.MAX_VALUE;
		for (Vector2f p : origins) {
			float d = Misc.getDistanceSq(p, target.getLocation());
			if (d < best) { best = d; from = p; }
		}

		float emp = spec.getEmpDamage() * level;
		float dam = spec.getDamage() * level;

		if (target instanceof MissileAPI) {
			// EmpArcParams.flamesOutMissiles defaults true, so this kills them
			// regardless - no reason to pay full damage for a Swarmer.
			emp *= missileEmpMult;
			dam *= missileEmpMult;
		} else if (target instanceof ShipAPI && ((ShipAPI) target).isFighter()) {
			emp *= fighterEmpMult;
			dam *= fighterEmpMult;
		}

		// closer targets crackle harder
		float depth = 1f - (float) Math.sqrt(best) / Math.max(1f, range);
		zapParams.flickerRateMult = 0.3f + 0.5f * Math.max(0f, Math.min(1f, depth));

		EmpArcEntityAPI arc;
		if (pierceShields) {
			arc = engine.spawnEmpArcPierceShields(ship, from, ship, target,
					DamageType.ENERGY, dam, emp, range,
					spec.getImpactSound(), zapThickness,
					spec.getEffectColor1(), spec.getEffectColor2(), zapParams);
		} else {
			arc = engine.spawnEmpArc(ship, from, ship, target,
					DamageType.ENERGY, dam, emp, range,
					spec.getImpactSound(), zapThickness,
					spec.getEffectColor1(), spec.getEffectColor2(), zapParams);
		}

		arc.setSingleFlickerMode();
		// NOT setUpdateFromOffsetEveryFrame here either. With it on, the target
		// end tracks the target as it moves and the arc visibly stretches over
		// its lifetime instead of being a strike that lands and fades.
		// terminate at hull center on big targets instead of the hitbox edge
		if (target instanceof ShipAPI && !((ShipAPI) target).isFighter()) {
			arc.setTargetToShipCenter(from, (ShipAPI) target);
		}
	}

	// ------------------------------------------------------------------
	// setup
	// ------------------------------------------------------------------
	private void resolveEmitters(ShipAPI ship) {
		if (emitters != null) return;
		emitters = new ArrayList<WeaponSlotAPI>();
		for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
			// SMALL system slots are the antennas. Order no longer matters -
			// each strand is independent now, so add or remove them freely.
			if (slot.isSystemSlot() && slot.getSlotSize() == WeaponSize.SMALL) {
				emitters.add(slot);
			}
		}
	}

	private void readConfig(ShipSystemSpecAPI spec) {
		if (configLoaded) return;
		configLoaded = true;

		JSONObject json = spec.getSpecJson();
		rimIntervalMin    = optFloat(json, "taser_rimIntervalMin", 0.05f);
		rimIntervalMax    = optFloat(json, "taser_rimIntervalMax", 0.10f);
		zapIntervalMin    = optFloat(json, "taser_zapIntervalMin", 0.14f);
		zapIntervalMax    = optFloat(json, "taser_zapIntervalMax", 0.22f);
		maxTargetsPerTick = (int) optFloat(json, "taser_maxTargetsPerTick", 5f);
		fighterEmpMult    = optFloat(json, "taser_fighterEmpMult", 0.4f);
		missileEmpMult    = optFloat(json, "taser_missileEmpMult", 0.1f);
		strandSkipChance  = optFloat(json, "taser_strandSkipChance", 0.2f);
		rimThickness      = optFloat(json, "taser_rimThickness", 17f);
		zapThickness      = optFloat(json, "taser_zapThickness", 16f);
		idleArcsPerTick   = (int) optFloat(json, "taser_idleArcsPerTick", 2f);
		idleArcMinRange   = optFloat(json, "taser_idleArcMinRange", 0.25f);
		idleArcMaxRange   = optFloat(json, "taser_idleArcMaxRange", 0.75f);
		idleArcSpread     = optFloat(json, "taser_idleArcSpread", 220f);
		idleArcSpeed      = optFloat(json, "taser_idleArcSpeed", 2200f);
		rimSegmentLengthMult = optFloat(json, "taser_rimSegmentLengthMult", 8f);
		rimZigZagReduction   = optFloat(json, "taser_rimZigZagReduction", 0.15f);
		rimMaxZigZag         = optFloat(json, "taser_rimMaxZigZag", 1f);
		rimGlowSize          = optFloat(json, "taser_rimGlowSize", 1.2f);
		pierceShields     = optFloat(json, "taser_pierceShields", 0f) > 0.5f;

		rimTick = new IntervalUtil(rimIntervalMin, rimIntervalMax);
		zapTick = new IntervalUtil(zapIntervalMin, zapIntervalMax);

		// zaps: inherit the emp_* keys from the .system
		zapParams = new EmpArcParams();
		zapParams.loadFromSystemJson(json);
		zapParams.brightSpotFullFraction = 0.5f;
		zapParams.brightSpotFadeFraction = 0.5f;

		// idle arcs: mote-attractor styling. NOTE zigZagReductionFactor is
		// INVERSE - higher means straighter. 0.15 is what vanilla uses.
		rimParams = new EmpArcParams();
		rimParams.segmentLengthMult = rimSegmentLengthMult;
		rimParams.zigZagReductionFactor = rimZigZagReduction;
		rimParams.maxZigZagMult = rimMaxZigZag;
		rimParams.glowSizeMult = rimGlowSize;
		rimParams.brightSpotFullFraction = 0.5f;
		rimParams.brightSpotFadeFraction = 0.5f;
		rimParams.flamesOutMissiles = false; // cosmetic arcs must not eat ordnance
	}

	private static float optFloat(JSONObject json, String key, float fallback) {
		if (json == null) return fallback;
		return (float) json.optDouble(key, fallback);
	}

	// ------------------------------------------------------------------
	// HUD
	// ------------------------------------------------------------------
	@Override
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index != 0) return null;
		if (state == State.COOLDOWN || state == State.IDLE) return null;
		return new StatusData("emitters live", false);
	}
}
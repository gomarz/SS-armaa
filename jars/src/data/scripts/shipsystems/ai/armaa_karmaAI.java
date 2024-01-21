package data.scripts.shipsystems.ai;

import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.combat.CombatUtils;
import java.util.ArrayList;
import java.util.List;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.impl.campaign.ids.Skills;

public class armaa_karmaAI implements ShipSystemAIScript {

    private CombatEngineAPI engine = null;
    private ShipwideAIFlags flags;
    private ShipAPI ship;

    private final IntervalUtil tracker = new IntervalUtil(0.25f, 1f);
	private final float BASE_RANGE = 600f;

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine == null) {
            return;
        }

        if (engine.isPaused()) {
            return;
        }		
		tracker.advance(amount);

        if (ship.getFluxTracker().getFluxLevel() >= 0.60f) {
            flags.setFlag(AIFlags.BACK_OFF, 1f);
			flags.setFlag(AIFlags.DO_NOT_USE_FLUX,1f);
        }

        if (tracker.intervalElapsed()) {
            if (!AIUtils.canUseSystemThisFrame(ship)) {
                return;
            }
			
			float range = BASE_RANGE;
			boolean hasSysExpertise = ship.getCaptain().getStats().getSkillLevel(Skills.SYSTEMS_EXPERTISE) > 0 ? true : false;

			if(hasSysExpertise)
			{
				range = range*1.50f;
			}				
            List<DamagingProjectileAPI> possibleTargets = new ArrayList<>(100);
            possibleTargets.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(), range));
            possibleTargets.addAll(CombatUtils.getProjectilesWithinRange(ship.getLocation(), range));
            float decisionLevel = 0f;

            for (DamagingProjectileAPI possibleTarget : possibleTargets) {
                if (possibleTarget.getOwner() == ship.getOwner() || possibleTarget.isFading() || possibleTarget.getCollisionClass() == CollisionClass.NONE) {
                    continue;
                }
                if (possibleTarget.getDamageType() == DamageType.FRAGMENTATION) {
                    decisionLevel += (float) Math.sqrt(0.25f * possibleTarget.getDamageAmount() + possibleTarget.getEmpAmount() * 0.25f);
                } else {
                    decisionLevel += (float) Math.sqrt(possibleTarget.getDamageAmount() + possibleTarget.getEmpAmount() * 0.25f);
                }
            }

            if (decisionLevel > 50f) {
                ship.useSystem();
            }
        }
    }

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
    }
}

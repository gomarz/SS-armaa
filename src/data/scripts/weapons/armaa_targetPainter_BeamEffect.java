package data.scripts.weapons;

import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.lazywizard.lazylib.MathUtils;
import java.awt.Color;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;

public class armaa_targetPainter_BeamEffect implements BeamEffectPlugin
{
    private static final float EXPLOSION_MULTIPLIER = 2.0f;
    private static final float FOCUS_TIME = 1.0f;
    private float timer = 0.0f;
    private boolean wasZero = true;
    private static final int TEXTURE_LENGTH = 128;
	//borrowed prv skrip
    public void advance(final float amount, final CombatEngineAPI engine, final BeamAPI beam) {
        final CombatEntityAPI target = beam.getDamageTarget();
        float dur = beam.getDamage().getDpsDuration();
        if (target != null) {
            if (!wasZero) {
                dur = 0.0f;
            }
            wasZero = (beam.getDamage().getDpsDuration() <= 0.0f);
            if (beam.getSource() != null) {
                dur *= beam.getSource().getMutableStats().getEnergyRoFMult().getModifiedValue();
            }
            timer += dur;
            if (timer > 1.0f) {
                final float rng = Misc.random.nextFloat() * 0.1f + 0.95f;
                timer -= 1.0f * rng;
                spawnExplosion(engine, beam, target);
            }
        }
        setInterruptedBeamLength(beam, target, 156.0f);
    }
	
	private void setInterruptedBeamLength(BeamAPI beam, CombatEntityAPI target, final float textureLength) {
        if (beam == null) {
            return;
        }
        float length = MathUtils.getDistance(beam.getTo(), beam.getFrom()) + 4.0f;
        if (target == null) {
            length += beam.getLength() * 0.5f;
        }
        beam.setPixelsPerTexel(length / textureLength);
    }
    
    private void spawnExplosion(final CombatEngineAPI engine, final BeamAPI beam, final CombatEntityAPI target) {
        final float damageAmount = beam.getDamage().getDamage() * 2.0f;
        final Color fringeColor = beam.getFringeColor();
        engine.spawnExplosion(beam.getTo(), target.getVelocity(), fringeColor, damageAmount * 0.5f, 0.75f);
       // addWaveDistorsion(beam.getTo(), damageAmount);
		if((float) Math.random() <= 0.5f)
		{
			engine.addNebulaParticle(beam.getTo(),
				target.getVelocity(),
				 damageAmount*0.5f * (0.75f + (float) Math.random() * 0.5f),
				MathUtils.getRandomNumberInRange(1.0f,3f),
				0f,
				0f,
				1f,
				new Color(beam.getFringeColor().getRed(),beam.getFringeColor().getGreen(),beam.getFringeColor().getBlue(),100),
				true);
		}
        engine.spawnDamagingExplosion(getExplosion(damageAmount, beam.getFringeColor(), beam.getCoreColor()), beam.getSource(), beam.getTo(), true);
    }
    /*
    private static void addWaveDistorsion(final Vector2f location, final float damageAmount) {
        final WaveDistortion wave = new WaveDistortion(location, new Vector2f());
        wave.setIntensity(damageAmount * 0.1f);
        wave.fadeInSize(0.75f);
        wave.fadeOutIntensity(0.33f);
        wave.setSize(damageAmount * 0.3f);
        wave.setLifetime(1.0f);
        wave.setAutoFadeIntensityTime(0.67f);
        wave.flip(true);
        DistortionShader.addDistortion((DistortionAPI)wave);
    }
	*/
    
    private DamagingExplosionSpec getExplosion(final float damage, final Color explosionColor, final Color particleColor) {
        final float radius = damage * 0.5f;
        final DamagingExplosionSpec boom = new DamagingExplosionSpec(0.1875f, radius, radius * 0.5f, damage, damage * 0.5f, CollisionClass.PROJECTILE_FF, CollisionClass.PROJECTILE_FIGHTER, 3.0f, 5.0f, 1.5f, Math.round(damage * 0.2f), particleColor, explosionColor);
        boom.setDamageType(DamageType.FRAGMENTATION);
        boom.setShowGraphic(false);
        return boom;
    }
}
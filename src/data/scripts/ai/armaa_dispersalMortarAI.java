package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.GuidedMissileAI;
import com.fs.starfarer.api.combat.MissileAIPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.*;
import org.lwjgl.util.vector.Vector2f;


public class armaa_dispersalMortarAI implements MissileAIPlugin, GuidedMissileAI {
    
    private CombatEngineAPI engine;
    private final MissileAPI missile;
    private CombatEntityAPI target;
    private IntervalUtil blink = new IntervalUtil(0.4f,0.4f);

    public armaa_dispersalMortarAI(MissileAPI missile, ShipAPI launchingShip) {	        
        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }
        this.missile = missile;  
        missile.setArmingTime(missile.getArmingTime()-(float)(Math.random()/4));
    }

    @Override
    public void advance(float amount) {        
        //skip the AI if the game is paused, the missile is engineless or fading
        if (engine.isPaused() || missile.isFading()) {return;}
        
        if(missile.getVelocity().lengthSquared()>1600){
            missile.giveCommand(ShipCommand.DECELERATE);
        }
        
        if(missile.isArmed() || missile.getAngularVelocity() > 5){

            DamagingExplosionSpec boom = new DamagingExplosionSpec(
                    1f,
                    200,
                    100,
                    200,
                    50,
                    CollisionClass.PROJECTILE_FF,
                    CollisionClass.PROJECTILE_FIGHTER,
                    2,
                    5,
                    5,
                    25,
                    new Color(44,245,44),
                    new Color(200,255,200)
            );
            boom.setDamageType(DamageType.HIGH_EXPLOSIVE);
            boom.setShowGraphic(false);
            boom.setSoundSetId("mine_explosion");
            engine.spawnDamagingExplosion(boom, missile.getSource(), missile.getLocation(),false);
            
            //visual effect
            engine.addSmoothParticle(missile.getLocation(), new Vector2f(0,0), 250, 2, 0.1f, Color.white);
            engine.addHitParticle(missile.getLocation(), new Vector2f(0,0), 200, 1, 0.4f, new Color(255,180,25));
            engine.spawnExplosion(missile.getLocation(), new Vector2f(0,0), new Color(50,100,50), 93, 1.2f);
            engine.spawnExplosion(missile.getLocation(), new Vector2f(0,0), Color.BLACK, 225, 3);
            engine.spawnExplosion(missile.getLocation(),new Vector2f(0,0), new Color (255,50,0), 187, 1.0f);
            
            for(int i=0; i<10; i++){
                engine.addHitParticle(
                        missile.getLocation(),
                        MathUtils.getRandomPointInCircle(new Vector2f(), 300),
                        MathUtils.getRandomNumberInRange(5, 8),
                        1,
                        MathUtils.getRandomNumberInRange(0.4f, 0.8f),
                        new Color(255,155,155));
            }
            
            //destroy grenade
//            engine.applyDamage(missile, missile.getLocation(), 1000, DamageType.FRAGMENTATION, 0, true, false, missile.getSource());
            engine.removeEntity(missile);
        }
    }

    @Override
    public CombatEntityAPI getTarget() {
        return target;
    }

    @Override
    public void setTarget(CombatEntityAPI target) {
        this.target = target;
    }
}
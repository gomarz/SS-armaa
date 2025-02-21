package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicTargeting;
import org.magiclib.util.MagicLensFlare;
import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import data.scripts.weapons.armaa_curveLaserProjectileScript;
import org.lazywizard.lazylib.combat.CombatUtils;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.input.InputEventAPI;

public class armaa_kineticGrenadeEffect extends BaseEveryFrameCombatPlugin
{
	//This script combines the cyroflamer script with the Projectile Tracking script developed by Nicke535
	//Modified by shoi
    //////////////////////
    //     SETTINGS     //
    //////////////////////
    
    //Damping of the turn speed when closing on the desired aim. The smaller the snappier.
    private final float DAMPING=0.1f;    
    
    //max speed of the missile after modifiers.
    //private final float MAX_SPEED;
    private CombatEngineAPI engine;
    private final DamagingProjectileAPI proj;
    private CombatEntityAPI target;
    private Vector2f lead = new Vector2f();
	private Vector2f ZERO = new Vector2f();
    private boolean launch=true;
    private IntervalUtil trailtimer= new IntervalUtil(0.15f,0.15f);
	private IntervalUtil timer= new IntervalUtil(0.2f,0.3f);
	private IntervalUtil interval = new IntervalUtil(0.015f,0.025f);
	private IntervalUtil interval2 = new IntervalUtil(.039f,.039f);
	protected List<List> trailOfTrails;
	protected List<armaa_curvyLaserAI> trails;
    private static final Color MUZZLE_FLASH_COLOR = new Color(200,100,255, 50);
    private static final Color MUZZLE_FLASH_COLOR_ALT = new Color(255, 255, 255, 100);
    private static final Color MUZZLE_FLASH_COLOR_GLOW = new Color(255, 75, 255, 50);
    private static final float MUZZLE_FLASH_DURATION = 0.10f;
    private static final float MUZZLE_FLASH_SIZE = 3.0f;
	
	private boolean inRange = false;
	private int count = 0; // number of proj's that have been created;
	private int total = 0;
	private int beamLength = 4; //The number of projectiles making up the stream;
	private int beamNo = 0;
	private float angle = 0f;
	private float angleIncrease = 0f;
	private int side = 1;
	private String targetPointKey;
    private static final float CONE_ANGLE = 180f;
    // one half of the angle. used internally, don't mess with thos
    private static final float A_2 = CONE_ANGLE / 2;
    private List<DamagingProjectileAPI> alreadyRegisteredProjectiles = new ArrayList<DamagingProjectileAPI>();

    public armaa_kineticGrenadeEffect(DamagingProjectileAPI proj, ShipAPI launchingShip) 
	{	
        this.proj = proj;
		WeaponAPI weapon = proj.getWeapon();
		engine = Global.getCombatEngine();
    }

    @Override
	public void advance(float amount, List<InputEventAPI> events)
	{
		if(!engine.isPaused())
		{

			
			if (Math.random() > 0.75) {
				engine.spawnExplosion(proj.getLocation(), proj.getVelocity(), MUZZLE_FLASH_COLOR_ALT, MUZZLE_FLASH_SIZE * 0.20f, MUZZLE_FLASH_DURATION);
			} else {
				engine.spawnExplosion(proj.getLocation(), proj.getVelocity(), MUZZLE_FLASH_COLOR, MUZZLE_FLASH_SIZE*2f, MUZZLE_FLASH_DURATION);
			}
			engine.addSmoothParticle(proj.getLocation(), proj.getVelocity(), MUZZLE_FLASH_SIZE * 6f, 1f, MUZZLE_FLASH_DURATION * 2f, MUZZLE_FLASH_COLOR_GLOW);
		}
		
        if (proj == null || proj.didDamage() || proj.isFading() || !Global.getCombatEngine().isEntityInPlay(proj)) {
            Global.getCombatEngine().removePlugin(this);
            return;
        }
					
	}
	

}
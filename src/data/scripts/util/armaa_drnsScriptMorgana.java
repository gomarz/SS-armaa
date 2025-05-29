package data.scripts.util;

import java.awt.Color;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.combat.CombatUtils;

public class armaa_drnsScriptMorgana extends BaseEveryFrameCombatPlugin
{
	private final IntervalUtil interval = new IntervalUtil(0.08f, 0.08f);
	private ShipAPI ship;
	private float gauge;
	private float MAX_GAUGE;
	private boolean isFiring = false;
	private CombatEngineAPI engine;
	private WeaponAPI weapon;
	private boolean even = false;
	private float maxDamage = 1500f;
	private float maxProjs = 99f;
	private float guardBar = 0f;
    private SpriteAPI waveSprite;
    public armaa_drnsScriptMorgana(ShipAPI ship, float gauge) 
	{
		this.ship = ship;
		this.gauge = gauge;
		if(Global.getCombatEngine().getCustomData().containsKey("armaa_morgana_bonus_"+ship.getId()))
			guardBar = 1f-((Float)Global.getCombatEngine().getCustomData().get("armaa_morgana_bonus_"+ship.getId())/100f);
		this.MAX_GAUGE = gauge;
		float damageAmt = Global.getCombatEngine().getCustomData().get("armaa_morgana_absorbed_"+ship.getId()) != null ? (float)Global.getCombatEngine().getCustomData().get("armaa_morgana_absorbed_"+ship.getId()) : 0;
		maxDamage = damageAmt;
		engine = Global.getCombatEngine();
		weapon = ship.getAllWeapons().get(0);
		waveSprite = Global.getSettings().getSprite("misc", "armaa_sfxpulse");	
    }
	
    @Override
	public void advance(float amount, List<InputEventAPI> events)
	{
		if(gauge < 1f)
		{
			Global.getCombatEngine().getCustomData().put("armaa_morgana_absorbed_"+ship.getId(),0f);
			Global.getCombatEngine().getCustomData().remove("armaa_morgana_absorbed_"+ship.getId()+"_countering");			
			engine.removePlugin(this);
		}
		else if(gauge > 0f)
		{
			if(gauge > 0f)
			{
				isFiring = true;
			}
		}
		float shipRadius = armaa_utils.effectiveRadius(ship);	
		float randRange = (float) shipRadius * 0.5f * 1f;
		randRange = (float) Math.sqrt(shipRadius) * 0.75f * 1f;
		ship.setJitter(ship, new Color(199f/255f,146f/255f,0,Math.max((gauge/MAX_GAUGE),0f)), 1f * (gauge/MAX_GAUGE), 4, 25);

		if(isFiring == true)
			interval.advance(amount);		
			
		if(interval.intervalElapsed() && gauge >= 1f && isFiring)
		{			
			//String weaponId = "armaa_guardual_drns_he";
			//String weaponId2 = "armaa_einhander_hig";
			Vector2f muzzleLocation = weapon.getSpec().getTurretFireOffsets().get(0); 			
			int level = 1;
			if(guardBar < 0.50f)
			{
				level = 2;
			}
			for(int i = 0; i < level; i++)
			{

				ShipAPI target1 = null;
				if(ship.getWeaponGroupFor(weapon)!=null )
				{
					//WEAPON IN AUTOFIRE
					if(ship.getWeaponGroupFor(weapon).isAutofiring()  //weapon group is autofiring
							&& ship.getSelectedGroupAPI()!=ship.getWeaponGroupFor(weapon)){ //weapon group is not the selected group
						target1 = ship.getWeaponGroupFor(weapon).getAutofirePlugin(weapon).getTargetShip();
					}
					else 
					{
						target1 = ship.getShipTarget();
						
					}
				}
				
				if(target1 == null)
				{
					for(ShipAPI target: CombatUtils.getShipsWithinRange(ship.getLocation(),1000f))
					{
						if(target.getOwner() == ship.getOwner())
							continue;
						
						if(!target.isAlive() || target.isHulk())
							continue;
						
						target1 = target;
					}
				}

				float angle = even ? 180f+ship.getFacing()+(float)Math.random()*90f : 180f+ship.getFacing()-(float)Math.random()*90f;
			engine.addNebulaSmokeParticle(ship.getLocation(),
				new Vector2f(ship.getVelocity().getX()/2,ship.getVelocity().getY()/2),
				 40f * (0.75f + (float) Math.random() * 0.5f),
				3f + 1f * (float)Math.random()*2f,
				0f,
				0f,
				1f,
				new Color(255,255,255,155));
			if (waveSprite != null)
			{
				MagicRender.objectspace(
							waveSprite,
							ship,
							new Vector2f(),
							new Vector2f(),
							new Vector2f((float)Math.random()*ship.getCollisionRadius(),(float)Math.random()*ship.getCollisionRadius()),
							new Vector2f(100f,100f),
							5f,
							5f,
							true,
							new Color(150,155,155,100), 
							true,
							.1f,
							.2f,
							.2f,
							true
					);
			}
				float damage = (float)(maxDamage*Math.random());
				float variance = MathUtils.getRandomNumberInRange(-0.6f,0f);
				Global.getSoundPlayer().playSound("vajra_fire", 1f+variance, 1f+variance, ship.getLocation(), ship.getVelocity());

				if(MagicRender.screenCheck(0.25f, ship.getLocation())){
					engine.addSmoothParticle(ship.getLocation(), new Vector2f(), 60, 0.5f, 0.25f, new Color(199f/255f, 146f/255f,0f,Math.max(0f,gauge)/MAX_GAUGE));
					//engine.addHitParticle(proj.getLocation(), new Vector2f(), 50, 1f, 0.1f, Color.white);
				}
				if(target1 == null)
					return;
				engine.spawnEmpArc(ship, ship.getLocation(), ship, target1, DamageType.ENERGY, damage, damage, 10000f, "tachyon_lance_emp_impact", Math.min(150f,damage), Color.red, Color.white);		
				gauge -= damage;
				maxDamage-= damage;
				maxProjs--;
				Global.getCombatEngine().getCustomData().put("armaa_morgana_absorbed_"+ship.getId(),maxDamage);				
				Global.getCombatEngine().getCustomData().put("armaa_morgana_absorbed_"+ship.getId(),gauge);				
				if( gauge <= 0)
				{
					ship.getSystem().deactivate();
					isFiring = false;
				}
				even = even == true ? false : true;
			}					
		}		
		
	}

}
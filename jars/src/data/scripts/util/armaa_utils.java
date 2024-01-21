package data.scripts.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.FastTrig;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.ReadableVector2f;

//those methods were copied from source files of other modders like Sundog and Dark.Revenant

public class armaa_utils {
    static Map<String, Float> shieldUpkeeps;
    static Map<String, String> SystemIDs;
    static final float SAFE_DISTANCE = 600f;
    static final float DEFAULT_DAMAGE_WINDOW = 3f;
	static IntervalUtil particle = new IntervalUtil(0.015f, 0.015f);		
    static final Map<HullSize, Float> baseOverloadTimes = new HashMap<HullSize, Float>();
    static {
        baseOverloadTimes.put(HullSize.FIGHTER, 10f);
        baseOverloadTimes.put(HullSize.FRIGATE,  4f);
        baseOverloadTimes.put(HullSize.DESTROYER, 6f);
        baseOverloadTimes.put(HullSize.CRUISER, 8f);
        baseOverloadTimes.put(HullSize.CAPITAL_SHIP, 10f);
        baseOverloadTimes.put(HullSize.DEFAULT, 6f);
    }

    
    public static float getEngineFractionDisabled(ShipAPI ship) {
        float maxThrust = 0;
        float onlineThrust = 0;
        
        for(ShipEngineAPI engine : ship.getEngineController().getShipEngines()) {
            if(engine.isSystemActivated()) continue;
            else if(!engine.isDisabled()) onlineThrust += engine.getContribution();
            
            maxThrust += engine.getContribution();
        }
        
        return onlineThrust / maxThrust;
    }
    public static void showHealText(ShipAPI anchor, Vector2f at, float repairAmount) {
//        Global.getCombatEngine().addFloatingDamageText(at, repairAmount,
//            ICEModPlugin.HEAL_TEXT_COLOR, anchor, anchor);
    }
    public static List<CombatEntityAPI> getCollideablesInRange(Vector2f at, float range) {
        List<CombatEntityAPI> retVal = new LinkedList();
        
        retVal.addAll(CombatUtils.getAsteroidsWithinRange(at, range));
        
        for(ShipAPI ship : CombatUtils.getShipsWithinRange(at, range)) {
            if(!ship.isFighter()) retVal.add(ship);
        }
        
        return retVal;
    }
    public static List<DamagingProjectileAPI> getProjectilesDamagedBy(ShipAPI ship) {
        List retVal = new LinkedList();
        
        for(DamagingProjectileAPI p : CombatUtils.getProjectilesWithinRange(ship.getLocation(), ship.getCollisionRadius())) {
            if(p.didDamage() && p.getDamageTarget() == ship) retVal.add(p);
        }
        
        return retVal;
    }
    
    
    public static float estimateOptimalRange(ShipAPI ship) {
        float acc = 0, opAcc = 0;
//        Map<WeaponType, Float> rangeBunuses = new HashMap();
//        rangeBunuses.put(WeaponType.BALLISTIC, ship.getMutableStats().getBallisticWeaponRangeBonus().getBonusMult());
//        rangeBunuses.put(WeaponType.ENERGY, ship.getMutableStats().getEnergyWeaponRangeBonus().getBonusMult());
//        rangeBunuses.put(WeaponType.MISSILE, ship.getMutableStats().getMissileWeaponRangeBonus().getBonusMult());
//        rangeBunuses.put(WeaponType., ship.getMutableStats().getBeamWeaponRangeBonus().getBonusMult());
//        rangeBunuses.put(WeaponType.BALLISTIC, ship.getMutableStats().getBallisticWeaponRangeBonus().getBonusMult());
        
        for(WeaponAPI w :ship.getAllWeapons()) {
            float op = w.getSpec().getOrdnancePointCost(null);
             if(w.getDamageType() == DamageType.FRAGMENTATION) op *= 0.2f;
            opAcc += op;
            acc += op * w.getRange();
                    
        }
        
        return acc / opAcc;
    }
    public static Vector2f getDirectionalVector(float degrees) {
        double radians = Math.toRadians(degrees);
        return new Vector2f((float)Math.cos(radians), (float)Math.sin(radians));
    }
    public static Vector2f getMidpoint(Vector2f from, Vector2f to, float d) {
        d *= 2;
        
        return new Vector2f(
                (from.x * (2 - d) + to.x * d) / 2,
                (from.y * (2 - d) + to.y * d) / 2
        );
    }
    public static Vector2f getMidpoint(Vector2f from, Vector2f to) {
        return getMidpoint(from, to, 0.5f);
    }
    public static Vector2f toRelative(CombatEntityAPI entity, Vector2f point) {
        Vector2f retVal = new Vector2f(point);
        Vector2f.sub(retVal, entity.getLocation(), retVal);
        VectorUtils.rotate(retVal, -entity.getFacing(), retVal);
        return retVal;
    }
    public static Vector2f toAbsolute(CombatEntityAPI entity, Vector2f point) {
        Vector2f retVal = new Vector2f(point);
        VectorUtils.rotate(retVal, entity.getFacing(), retVal);
        Vector2f.add(retVal, entity.getLocation(), retVal);
        return retVal;
    }
    public static void blink(Vector2f at) {
        Global.getCombatEngine().addHitParticle(at, new Vector2f(), 30, 1, 0.1f, Color.RED);
    }
    public static List<ShipAPI> getShipsOnSegment(Vector2f from, Vector2f to) {
        float distance = MathUtils.getDistance(from, to);
        Vector2f center = new Vector2f();
        center.x = (from.x + to.x) / 2;
        center.y = (from.y + to.y) / 2;
        
        List<ShipAPI> list = new ArrayList<ShipAPI>();
        
        for(ShipAPI s : CombatUtils.getShipsWithinRange(center, distance / 2)) {
            if(CollisionUtils.getCollisionPoint(from, to, s) != null) list.add(s);
        }
        
        return list;
    }
    public static ShipAPI getFirstShipOnSegment(Vector2f from, Vector2f to, CombatEntityAPI exception) {
        ShipAPI winner = null;
        float record = Float.MAX_VALUE;
        
        for(ShipAPI s : getShipsOnSegment(from, to)) {
            if(s == exception) continue;
            
            float dist2 = MathUtils.getDistanceSquared(s, from);
            
            if(dist2 < record) {
                record = dist2;
                winner = s;
            }
        }
        
        return winner;
    }
    public static ShipAPI getFirstNonFighterOnSegment(Vector2f from, Vector2f to, CombatEntityAPI exception) {
        ShipAPI winner = null;
        float record = Float.MAX_VALUE;
        
        for(ShipAPI s : getShipsOnSegment(from, to)) {
            if(s == exception) continue;
            if(s.isFighter()) continue;
            float dist2 = MathUtils.getDistanceSquared(s, from);
            
            if(dist2 < record) {
                record = dist2;
                winner = s;
            }
        }
        
        return winner;
    }
    public static ShipAPI getFirstShipOnSegment(Vector2f from, Vector2f to) {
        return getFirstShipOnSegment(from, to, null);
    }
    public static ShipAPI getShipInLineOfFire(WeaponAPI weapon) {
        Vector2f endPoint = weapon.getLocation();
        endPoint.x += Math.cos(Math.toRadians(weapon.getCurrAngle())) * weapon.getRange();
        endPoint.y += Math.sin(Math.toRadians(weapon.getCurrAngle())) * weapon.getRange();
        
        return getFirstShipOnSegment(weapon.getLocation(), endPoint, weapon.getShip());
    }    
    public static float getArmorPercent(ShipAPI ship) {
        float acc = 0;
        int width = ship.getArmorGrid().getGrid().length;
        int height = ship.getArmorGrid().getGrid()[0].length;
        
        for(int x = 0; x < width; ++x) {
            for(int y = 0; y < height; ++y) {
                acc += ship.getArmorGrid().getArmorFraction(x, y);
            }
        }
        
        return acc / (width * height);
    }
    public static void setArmorPercentage(ShipAPI ship, float armorPercent) {
        ArmorGridAPI armorGrid = ship.getArmorGrid();

        armorPercent = Math.min(1, Math.max(0, armorPercent));
        
        for(int x = 0; x < armorGrid.getGrid().length; ++x) {
            for(int y = 0; y < armorGrid.getGrid()[0].length; ++y) {
                armorGrid.setArmorValue(x, y, armorGrid.getMaxArmorInCell() * armorPercent);
            }           
        }
    }
    public static List getCellLocations(ShipAPI ship) {
        int width = ship.getArmorGrid().getGrid().length;
        int height = ship.getArmorGrid().getGrid()[0].length;
        //List cellLocations = new ArrayList(width * height);
        // Not sure if above works the way I think it does.
        List cellLocations = new ArrayList();
        
        for(int x = 0; x < width; ++x) {
            for(int y = 0; y < height; ++y) {
                cellLocations.add(getCellLocation(ship, x, y));
            }           
        }
        
        return cellLocations;
    }
    public static void setLocation(CombatEntityAPI entity, Vector2f location) {
        Vector2f dif = new Vector2f(location);
        Vector2f.sub(location, entity.getLocation(), dif);
        Vector2f.add(entity.getLocation(), dif, entity.getLocation());
    }
    public static Vector2f getCellLocation(ShipAPI ship, float x, float y) {
        x -= ship.getArmorGrid().getGrid().length / 2f;
        y -= ship.getArmorGrid().getGrid()[0].length / 2f;
        float cellSize = ship.getArmorGrid().getCellSize();
        Vector2f cellLoc = new Vector2f();
        float theta = (float)(((ship.getFacing() - 90) / 360f) * (Math.PI * 2));
        cellLoc.x = (float)(x * Math.cos(theta) - y * Math.sin(theta)) * cellSize + ship.getLocation().x;
        cellLoc.y = (float)(x * Math.sin(theta) + y * Math.cos(theta)) * cellSize + ship.getLocation().y;

        return cellLoc;
    }
    public static void print(String str) {
        print(Global.getCombatEngine().getPlayerShip(), str);
    }
    public static void print(ShipAPI at, String str) {
        if(at == null) return;

        Global.getCombatEngine().addFloatingText(at.getLocation(), str, 40, Color.green, at, 1, 5);
    }
    public static void print(Vector2f at, String str) {
        if(at == null) return;

        Global.getCombatEngine().addFloatingText(at, str, 40, Color.green, null, 1, 5);
    }
    public static void destroy(CombatEntityAPI entity) {
        Global.getCombatEngine().applyDamage(entity, entity.getLocation(),
                entity.getMaxHitpoints() * 10f, DamageType.HIGH_EXPLOSIVE, 0,
                true, true, entity);
    }
    public static float estimateIncomingDamage(ShipAPI ship) {
        return estimateIncomingDamage(ship, DEFAULT_DAMAGE_WINDOW);
    }
    public static float estimateIncomingDamage(ShipAPI ship, float damageWindowSeconds) {
        float accumulator = 0f;

        accumulator += estimateIncomingBeamDamage(ship, damageWindowSeconds);

        for (DamagingProjectileAPI proj : Global.getCombatEngine().getProjectiles()) {

            if(proj.getOwner() == ship.getOwner()) continue; // Ignore friendly projectiles

            Vector2f endPoint = new Vector2f(proj.getVelocity());
            endPoint.scale(damageWindowSeconds);
            Vector2f.add(endPoint, proj.getLocation(), endPoint);

            if(( ship.getShield() != null && ship.getShield().isOn() && ship.getShield().isWithinArc(proj.getLocation()))
                    || !CollisionUtils.getCollides(proj.getLocation(), endPoint,
                        new Vector2f(ship.getLocation()), ship.getCollisionRadius()))
                continue;

            accumulator += proj.getDamageAmount();// * Math.max(0, Math.min(1, Math.pow(1 - MathUtils.getDistance(proj, ship) / safeDistance, 2)));
        }

        return accumulator;
    }
    public static float estimateAllIncomingDamage(ShipAPI ship) {
        float accumulator = 0f;

        accumulator += estimateIncomingBeamDamage(ship, DEFAULT_DAMAGE_WINDOW);

        for (DamagingProjectileAPI proj : Global.getCombatEngine().getProjectiles()) {
            
            Vector2f endPoint = new Vector2f(proj.getVelocity());
            endPoint.scale(DEFAULT_DAMAGE_WINDOW);
            Vector2f.add(endPoint, proj.getLocation(), endPoint);

            if((ship.getShield() != null && ship.getShield().isWithinArc(proj.getLocation()))
                    || !CollisionUtils.getCollides(proj.getLocation(), endPoint,
                        new Vector2f(ship.getLocation()), ship.getCollisionRadius()))
                continue;

            accumulator += proj.getDamageAmount() + proj.getEmpAmount();// * Math.max(0, Math.min(1, Math.pow(1 - MathUtils.getDistance(proj, ship) / safeDistance, 2)));
        }

        return accumulator;
    }
    public static float estimateIncomingBeamDamage(ShipAPI ship, float damageWindowSeconds) {
        float accumulator = 0f;

        for (Iterator iter = Global.getCombatEngine().getBeams().iterator(); iter.hasNext();) {
            BeamAPI beam = (BeamAPI)iter.next();
            
            if(beam.getDamageTarget() != ship) continue;
            
            float dps = beam.getWeapon().getDerivedStats().getDamageOver30Sec() / 30;
            float emp = beam.getWeapon().getDerivedStats().getEmpPerSecond();

            accumulator += (dps + emp) * damageWindowSeconds;
        }

        return accumulator;
    }
    public static float estimateIncomingMissileDamage(ShipAPI ship) {
        float accumulator = 0f;
        DamagingProjectileAPI missile;

        for (Iterator iter = Global.getCombatEngine().getMissiles().iterator(); iter.hasNext();) {
            missile = (DamagingProjectileAPI) iter.next();

            if(missile.getOwner() == ship.getOwner()) continue; // Ignore friendly missiles

            float safeDistance = SAFE_DISTANCE + ship.getCollisionRadius();
            float threat = missile.getDamageAmount() + missile.getEmpAmount();

            if(ship.getShield() != null && ship.getShield().isWithinArc(missile.getLocation()))
                continue;

            accumulator += threat * Math.max(0, Math.min(1, Math.pow(1 - MathUtils.getDistance(missile, ship) / safeDistance, 2)));
        }

        return accumulator;
    }
    public static float getHitChance(DamagingProjectileAPI proj, CombatEntityAPI target) 
	{
		if(proj.getOwner() == target.getOwner())
			return 0;
		
        float estTimeTilHit = MathUtils.getDistance(target, proj.getLocation())
                / (float)Math.max(1, proj.getMoveSpeed());

        Vector2f estTargetPosChange = new Vector2f(
                target.getVelocity().x * estTimeTilHit,
                target.getVelocity().y * estTimeTilHit);

        float estFacingChange = target.getAngularVelocity() * estTimeTilHit;

        Vector2f projVelocity = new Vector2f(proj.getVelocity());

        target.setFacing(target.getFacing() + estFacingChange);
        Vector2f.add(target.getLocation(), estTargetPosChange, target.getLocation());

        projVelocity.scale(estTimeTilHit * 3);
        Vector2f.add(projVelocity, proj.getLocation(), projVelocity);
        Vector2f estHitLoc = CollisionUtils.getCollisionPoint(proj.getLocation(),
                projVelocity, target);
        
        target.setFacing(target.getFacing() - estFacingChange);
        Vector2f.add(target.getLocation(), (Vector2f)estTargetPosChange.scale(-1),target.getLocation());

        if(estHitLoc == null) return 0;

        return 1;        
    }
    public static float getHitChance(WeaponAPI weapon, CombatEntityAPI target) {
        float estTimeTilHit = MathUtils.getDistance(target, weapon.getLocation())
                / (float)Math.max(1, weapon.getProjectileSpeed());

        Vector2f estTargetPosChange = new Vector2f(
                target.getVelocity().x * estTimeTilHit,
                target.getVelocity().y * estTimeTilHit);

        float estFacingChange = target.getAngularVelocity() * estTimeTilHit;

        double theta = weapon.getCurrAngle() * (Math.PI / 180);
        Vector2f projVelocity = new Vector2f(
                (float)Math.cos(theta) * weapon.getProjectileSpeed() + weapon.getShip().getVelocity().x,
                (float)Math.sin(theta) * weapon.getProjectileSpeed() + weapon.getShip().getVelocity().y);

        target.setFacing(target.getFacing() + estFacingChange);
        Vector2f.add(target.getLocation(), estTargetPosChange, target.getLocation());

        projVelocity.scale(estTimeTilHit * 3);
        Vector2f.add(projVelocity, weapon.getLocation(), projVelocity);
        Vector2f estHitLoc = CollisionUtils.getCollisionPoint(weapon.getLocation(),
                projVelocity, target);
        
        target.setFacing(target.getFacing() - estFacingChange);
        Vector2f.add(target.getLocation(), (Vector2f)estTargetPosChange.scale(-1),target.getLocation());

        if(estHitLoc == null) return 0;

        return 1;
    }
	
	public static void createChargeParticle(float chargeLevel, Vector2f loc, CombatEntityAPI ship)
	{
		float charge = chargeLevel;
		float size = 1;
		ShipAPI source = (ShipAPI) ship;
		IntervalUtil particle = new IntervalUtil(0.025f, 0.05f);		
		//if(weapon.getSize() ==  WeaponAPI.WeaponSize.LARGE)
		//	size = 2;
		
		if (charge != 1) {		
			Global.getSoundPlayer().playLoop("beamchargeM", (Object)source, 1.0f, 1.0f, source.getLocation(), source.getVelocity());
			particle.advance(Global.getCombatEngine().getElapsedInLastFrame());
			if (particle.intervalElapsed()) 
			{
				Vector2f origin = new Vector2f(loc);
				//Vector2f offset = new Vector2f(TURRET_OFFSET,OFFSET_Y);
				//VectorUtils.rotate(offset, weapon.getCurrAngle(), offset);
				//Vector2f.add(offset, origin, origin);
				Vector2f location = MathUtils.getPoint(loc, (float)18.5f, (float)source.getFacing());
				Vector2f vel = source.getVelocity();
				Global.getCombatEngine().addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(charge / 2.0f, charge / 1.5f, charge));
				Global.getCombatEngine().addSwirlyNebulaParticle(origin, new Vector2f(0.0f, 0.0f),MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), 1.2f, 0.15f, 0.0f, 0.35f*charge, new Color(charge / 2.0f, charge / 4.0f, charge),false);
				Vector2f particleVel = MathUtils.getRandomPointInCircle((Vector2f)new Vector2f(), (float)(35.0f * charge));
				Vector2f particleLoc = new Vector2f();
				Vector2f.sub((Vector2f)origin, (Vector2f)new Vector2f((ReadableVector2f)particleVel), (Vector2f)particleLoc);
				Vector2f.add((Vector2f)vel, (Vector2f)particleVel, (Vector2f)particleVel);
				for (int i = 0; i < 5; ++i) 
				{
					 Global.getCombatEngine().addHitParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float)2.0f, (float)(charge * 2.0f + 2.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.75f, (float)(0.75f + charge / 4.0f)), new Color(charge / 4.0f, charge / 2.0f, charge));
				}
			}
		}
	}
	public static void createChargeParticle(float chargeLevel, Vector2f loc, CombatEntityAPI ship, Color color, float particleSize)
	{
		float charge = chargeLevel;
		float size = particleSize;
		ShipAPI source = (ShipAPI) ship;
		//if(weapon.getSize() ==  WeaponAPI.WeaponSize.LARGE)
		//	size = 2;
		Color ENGINE_COLOR = color;
			float r = (ENGINE_COLOR.getRed()/255f);
			float g = (ENGINE_COLOR.getGreen()/255f);
			float b = (ENGINE_COLOR.getBlue()/255f);			
		if (charge != 1) {		
			Global.getSoundPlayer().playLoop("beamchargeM", (Object)source, 1.0f, 1.0f, source.getLocation(), source.getVelocity());
			particle.advance(Global.getCombatEngine().getElapsedInLastFrame());
			if (particle.intervalElapsed()) 
			{
				Vector2f origin = new Vector2f(loc);
				Vector2f offset = new Vector2f(-5f, -0f);
				VectorUtils.rotate(offset, ship.getFacing(), offset);
				Vector2f.add(offset, origin, origin);	
				Vector2f vel = source.getVelocity();
				Global.getCombatEngine().addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)10.0f*size, (float)(charge*(size) * 20.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(r*charge, g*charge, b*charge,charge));
				//Global.getCombatEngine().addSwirlyNebulaParticle(origin, new Vector2f(0.0f, 0.0f),MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), 1.2f, 0.15f, 0.0f, 0.35f*charge, new Color(charge / 2.0f, charge / 4.0f, charge),false);
				Vector2f particleVel = MathUtils.getRandomPointInCircle((Vector2f)new Vector2f(), (float)(35.0f * charge));
				Vector2f particleLoc = new Vector2f();
				Vector2f.sub((Vector2f)origin, (Vector2f)new Vector2f((ReadableVector2f)particleVel), (Vector2f)particleLoc);
				Vector2f.add((Vector2f)vel, (Vector2f)particleVel, (Vector2f)particleVel);
				
				for (int i = 0; i < 5 + (int) (chargeLevel * 5); ++i) 
				{
					 Global.getCombatEngine().addHitParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float)2.0f, (float)(charge * 2.0f + 2.0f)), MathUtils.getRandomNumberInRange((float)0.25f, (float)(0.25f + charge)), MathUtils.getRandomNumberInRange((float)0.75f, (float)(0.75f + charge / 4.0f)), new Color(r*charge, g*charge, b*charge,charge));
				}
			}
		}
	}
	public static void makeAfterImages(ShipAPI ship, float AFTERIMAGE_THRESHOLD, float amount)
	{
		//afterimage shit from tahlan
		ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerNullerID", -1);
		ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerID",
		ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() + amount);
			
		if(ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHOLD) 
		{
			// Sprite offset fuckery - Don't you love trigonometry?
			SpriteAPI sprite = ship.getSpriteAPI();
			float offsetX = sprite.getWidth()/2 - sprite.getCenterX();
			float offsetY = sprite.getHeight()/2 - sprite.getCenterY();

			float trueOffsetX = (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetX - (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetY;
			float trueOffsetY = (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetX + (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetY;
			if(!ship.isHulk())
			{
				for(WeaponAPI w : ship.getAllWeapons())
				{
					if(!w.getSlot().isBuiltIn() && !w.getSlot().isDecorative())
						continue;
					//armaa_valkazardEffect;
					if(w.getSpec().getType() == WeaponAPI.WeaponType.MISSILE && w.getAmmo() < 0)
						continue;
					
					if(w.getSprite() == null)
						continue;
					
					if(!w.getSlot().getId().equals("F_LEGS"))
					{
						MagicRender.battlespace(
								Global.getSettings().getSprite(w.getSpec().getTurretSpriteName()),
								new Vector2f(w.getLocation().getX()+trueOffsetX,w.getLocation().getY()+trueOffsetY),
								new Vector2f(0, 0),
								new Vector2f(w.getSprite().getWidth(), w.getSprite().getHeight()),
								new Vector2f(0, 0),
								ship.getFacing()-90f,
								0f,
								new Color(100,255,150,185),
								true,
								0f,
								0f,
								0f,
								0f,
								0f,
								0.1f,
								0.1f,
								0.3f,
								CombatEngineLayers.BELOW_SHIPS_LAYER);
					}
					
					else
					{
						int frame = w.getAnimation().getFrame();
						SpriteAPI spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs0"+frame+".png");
					
						if(frame >= 10)
						spr = Global.getSettings().getSprite("graphics/armaa/ships/valkazard/armaa_valkazard_legs"+frame+".png");				
						
						MagicRender.battlespace(
								spr,
								new Vector2f(w.getLocation().getX()+trueOffsetX,w.getLocation().getY()+trueOffsetY),
								new Vector2f(0, 0),
								new Vector2f(w.getSprite().getWidth(), w.getSprite().getHeight()),
								new Vector2f(0, 0),
								ship.getFacing()-90f,
								0f,
								new Color(100,255,150,185),
								true,
								0f,
								0f,
								0f,
								0f,
								0f,
								0.1f,
								0.1f,
								0.3f,
								CombatEngineLayers.BELOW_SHIPS_LAYER);								
					}							
					
					if(w.getBarrelSpriteAPI() != null)
					{
						if(!w.getSlot().getId().equals("C_ARML") && (!w.getSlot().getId().equals("A_GUN")))
							continue;
						
						//Explicitly for Valkazard compat, since apparently can't access barrelsprite id through api and using the api itself also modifies the color of the original
						SpriteAPI spr = Global.getSettings().getSprite(w.getSpec().getHardpointSpriteName());
						
						MagicRender.battlespace(
								spr,
								new Vector2f(w.getLocation().getX()+trueOffsetX,w.getLocation().getY()+trueOffsetY),
								new Vector2f(0, 0),
								new Vector2f(w.getBarrelSpriteAPI().getWidth(), w.getBarrelSpriteAPI().getHeight()),
								new Vector2f(0, 0),
								w.getCurrAngle()-90f,
								0f,
								new Color(100,200,150,185),
								true,
								0f,
								0f,
								0f,
								0f,
								0f,
								0.1f,
								0.1f,
								0.3f,
								CombatEngineLayers.BELOW_SHIPS_LAYER);
					}
				}
			}
						ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").modifyFlat("armaa_NNAfterimageTrackerID",
						ship.getMutableStats().getDynamic().getStat("armaa_NNAfterimageTracker").getModifiedValue() - AFTERIMAGE_THRESHOLD);
		}
	}
	public static void createChargeParticle(float chargeLevel, ShipEngineAPI eng, CombatEntityAPI ship)
	{
		float charge = chargeLevel;
		float size = 1;
		ShipAPI source = (ShipAPI) ship;
		//if(weapon.getSize() ==  WeaponAPI.WeaponSize.LARGE)
		//	size = 2;
		Color ENGINE_COLOR = source.getEngineController().getFlameColorShifter().getCurr().getAlpha() == 0 ? source.getEngineController().getShipEngines().get(0).getEngineColor() : source.getEngineController().getFlameColorShifter().getCurr();
			float r = (ENGINE_COLOR.getRed()/255f);
			float g = (ENGINE_COLOR.getGreen()/255f);
			float b = (ENGINE_COLOR.getBlue()/255f);			
		if (charge != 1) {		
			Global.getSoundPlayer().playLoop("beamchargeM", (Object)source, 1.0f, 1.0f, source.getLocation(), source.getVelocity());
			particle.advance(Global.getCombatEngine().getElapsedInLastFrame());
			if (particle.intervalElapsed()) 
			{
				Vector2f origin = new Vector2f(eng.getLocation());
				Vector2f offset = new Vector2f(-5f, -0f);
				VectorUtils.rotate(offset, ship.getFacing(), offset);
				Vector2f.add(offset, origin, origin);	
				Vector2f vel = source.getVelocity();
				Global.getCombatEngine().addHitParticle(origin, vel, MathUtils.getRandomNumberInRange((float)10.0f*size, (float)(charge*size * 40.0f + 20.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.1f, (float)(0.1f + charge / 10.0f)), new Color(r*charge, g*charge, b*charge));
				//Global.getCombatEngine().addSwirlyNebulaParticle(origin, new Vector2f(0.0f, 0.0f),MathUtils.getRandomNumberInRange((float)20.0f*size, (float)(charge*size * 60.0f + 20.0f)), 1.2f, 0.15f, 0.0f, 0.35f*charge, new Color(charge / 2.0f, charge / 4.0f, charge),false);
				Vector2f particleVel = MathUtils.getRandomPointInCircle((Vector2f)new Vector2f(), (float)(35.0f * charge));
				Vector2f particleLoc = new Vector2f();
				Vector2f.sub((Vector2f)origin, (Vector2f)new Vector2f((ReadableVector2f)particleVel), (Vector2f)particleLoc);
				Vector2f.add((Vector2f)vel, (Vector2f)particleVel, (Vector2f)particleVel);
				for (int i = 0; i < 5; ++i) 
				{
					 Global.getCombatEngine().addHitParticle(particleLoc, particleVel, MathUtils.getRandomNumberInRange((float)2.0f, (float)(charge * 2.0f + 2.0f)), MathUtils.getRandomNumberInRange((float)0.5f, (float)(0.5f + charge)), MathUtils.getRandomNumberInRange((float)0.75f, (float)(0.75f + charge / 4.0f)), new Color(r*charge, g*charge, b*charge));
				}
			}
		}
	}	
    public static float getFPWorthOfSupport(ShipAPI ship, float range) {
        float retVal = 0;
        
        for(Iterator iter = AIUtils.getNearbyAllies(ship, range).iterator(); iter.hasNext();) {
            ShipAPI ally = (ShipAPI)iter.next();
            if(ally == ship) continue;
            float colDist = ship.getCollisionRadius() + ally.getCollisionRadius();
            float distance = Math.max(0, MathUtils.getDistance(ship, ally) - colDist);
            float maxRange = Math.max(1, range - colDist);
            
            retVal += getFPStrength(ally) * (1 - distance / maxRange); 
        }
        
        return retVal;
    }
    public static float getFPWorthOfHostility(ShipAPI ship, float range) {
        float retVal = 0;

        for(Iterator iter = AIUtils.getNearbyEnemies(ship, range).iterator(); iter.hasNext();) {
            ShipAPI enemy = (ShipAPI)iter.next();
            float colDist = ship.getCollisionRadius() + enemy.getCollisionRadius();
            float distance = Math.max(0, MathUtils.getDistance(ship, enemy) - colDist);
            float maxRange = Math.max(1, range - colDist);

            retVal += getFPStrength(enemy) * (1 - distance / maxRange);
        }

        return retVal;
    }
    public static float getStrengthInArea(Vector2f at, float range) {
        float retVal = 0;

        for(ShipAPI ship : CombatUtils.getShipsWithinRange(at, range)) {
            retVal += getFPStrength(ship);
        }

        return retVal;
    }
    public static float getStrengthInArea(Vector2f at, float range, int owner) {
        float retVal = 0;

        for(ShipAPI ship : CombatUtils.getShipsWithinRange(at, range)) {
            if(ship.getOwner() == owner) retVal += getFPStrength(ship);
        }

        return retVal;
    }
    public static float getFPStrength(ShipAPI ship) {
        DeployedFleetMemberAPI member = Global.getCombatEngine().getFleetManager(ship.getOwner()).getDeployedFleetMember(ship);
        return (member == null || member.getMember() == null)
                ? 0
                : member.getMember().getMemberStrength();
    }
    public static float getFP(ShipAPI ship) {
        DeployedFleetMemberAPI member = Global.getCombatEngine().getFleetManager(ship.getOwner()).getDeployedFleetMember(ship);
        return (member == null || member.getMember() == null)
                ? 0
                : member.getMember().getFleetPointCost();
    }
    public static float getBaseOverloadDuration(ShipAPI ship) {
        return (Float)baseOverloadTimes.get(ship.getHullSize());
    }
    public static float estimateOverloadDurationOnHit(ShipAPI ship, float damage, DamageType type) {
        if(ship.getShield() == null) return 0;

        float fluxDamage = damage * type.getShieldMult()
                * ship.getMutableStats().getShieldAbsorptionMult().getModifiedValue();
        fluxDamage += ship.getFluxTracker().getCurrFlux()
                - ship.getFluxTracker().getMaxFlux();
        
        if(fluxDamage <= 0) return 0;

        return Math.min(15, getBaseOverloadDuration(ship) + fluxDamage / 25);
    }
    public static float getLifeExpectancy(ShipAPI ship) {
        float damage = estimateIncomingDamage(ship);
        return (damage <= 0) ? 3600 : ship.getHitpoints() / damage;
    }
	
	    public static float lerp(float x, float y, float alpha) {
        return (1f - alpha) * x + alpha * y;
    }
    public static int clamp255(int x) {
        return Math.max(0, Math.min(255, x));
    }

    public static float effectiveRadius(ShipAPI ship) {
        if (ship.getSpriteAPI() == null || ship.isPiece()) {
            return ship.getCollisionRadius();
        } else {
            float fudgeFactor = 1.5f;
            return ((ship.getSpriteAPI().getWidth() / 2f) + (ship.getSpriteAPI().getHeight() / 2f)) * 0.5f * fudgeFactor;
        }
    }
	
	   public static String getMoreAggressivePersonality(FleetMemberAPI member, ShipAPI ship) {
        if (ship == null) {
            return Personalities.AGGRESSIVE;
        }

        boolean player = false;
        if ((member != null) && (member.getFleetData() != null) && (member.getFleetData().getFleet() != null)
                && member.getFleetData().getFleet().isPlayerFleet()) {
            player = true;
        }

        String personality = null;
        if (member != null) {
            if (member.getCaptain() != null) {
                /* Skip the player's ship or any player officer ships */
                if (player && (!member.getCaptain().isDefault() || member.getCaptain().isPlayer())) {
                    return null;
                }

                personality = member.getCaptain().getPersonalityAPI().getId();
            }
        } else {
            if (ship.getCaptain() != null) {
                personality = ship.getCaptain().getPersonalityAPI().getId();
            }
        }

        if ((ship.getShipAI() != null) && (ship.getShipAI().getConfig() != null)) {
            if (ship.getShipAI().getConfig().personalityOverride != null) {
                personality = ship.getShipAI().getConfig().personalityOverride;
            }
        }

        String newPersonality;
        if (personality == null) {
            newPersonality = Personalities.AGGRESSIVE;
        } else {
            switch (personality) {
                case Personalities.TIMID:
                    newPersonality = Personalities.CAUTIOUS;
                    break;
                case Personalities.CAUTIOUS:
                    newPersonality = Personalities.STEADY;
                    break;
                default:
                case Personalities.STEADY:
                    newPersonality = Personalities.AGGRESSIVE;
                    break;
                case Personalities.AGGRESSIVE:
                case Personalities.RECKLESS:
                    newPersonality = Personalities.RECKLESS;
                    break;
            }
        }

        return newPersonality;
    }

    public static String getLessAggressivePersonality(FleetMemberAPI member, ShipAPI ship) {
        if (ship == null) {
            return Personalities.CAUTIOUS;
        }

        boolean player = false;
        if ((member != null) && (member.getFleetData() != null) && (member.getFleetData().getFleet() != null)
                && member.getFleetData().getFleet().isPlayerFleet()) {
            player = true;
        }

        String personality = null;
        if (member != null) {
            if (member.getCaptain() != null) {
                /* Skip the player's ship or any player officer ships */
                if (player && (!member.getCaptain().isDefault() || member.getCaptain().isPlayer())) {
                    return null;
                }

                personality = member.getCaptain().getPersonalityAPI().getId();
            }
        } else {
            if (ship.getCaptain() != null) {
                personality = ship.getCaptain().getPersonalityAPI().getId();
            }
        }

        if ((ship.getShipAI() != null) && (ship.getShipAI().getConfig() != null)) {
            if (ship.getShipAI().getConfig().personalityOverride != null) {
                personality = ship.getShipAI().getConfig().personalityOverride;
            }
        }

        String newPersonality;
        if (personality == null) {
            newPersonality = Personalities.CAUTIOUS;
        } else {
            switch (personality) {
                case Personalities.TIMID:
                case Personalities.CAUTIOUS:
                    newPersonality = Personalities.TIMID;
                    break;
                default:
                case Personalities.STEADY:
                    newPersonality = Personalities.CAUTIOUS;
                    break;
                case Personalities.AGGRESSIVE:
                    newPersonality = Personalities.STEADY;
                    break;
                case Personalities.RECKLESS:
                    newPersonality = Personalities.AGGRESSIVE;
                    break;
            }
        }

        return newPersonality;
    }
	
	
}
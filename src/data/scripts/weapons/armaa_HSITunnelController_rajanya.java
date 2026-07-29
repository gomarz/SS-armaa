package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.AmmoTrackerAPI;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatAsteroidAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponGroupType;
import com.fs.starfarer.api.util.Misc;
import org.magiclib.util.MagicRender;

/**
 * 核心代码来自光环的中帮手
 * 新版本：4次攻击，第一次攻击后使用传送切换位置
 */
public class armaa_HSITunnelController_rajanya implements EveryFrameWeaponEffectPlugin {

    // 传送特效相关参数
    private static final float TELEPORT_DELAY = 0.10f; // 传送延迟时间
    
    // 发射间隔计时器参数
    private static final float LAUNCH_INTERVAL = 0.15f; // 每个浮游炮发射间隔（秒）
    
    // 残影效果开关和参数
    private static final boolean ENABLE_AFTERIMAGE = false;   // 残影效果开关（true=开启，false=关闭）
    private static final float AFTERIMAGE_FADE_IN = 0f;      // 残影淡入时间
    private static final float AFTERIMAGE_FADE_OUT = 0.75f;  // 残影淡出时间
    private static final float AFTERIMAGE_ALPHA_START = 0.95f; // 初始透明度
    private static final float AFTERIMAGE_ALPHA_END = 0f;    // 结束透明度
    
    // 击杀奖励参数
    private static final boolean ENABLE_KILL_BONUS = true;   // 击杀奖励开关（true=开启，false=关闭）
    private static final int BONUS_ATTACKS_PER_KILL = 1;     // 每次击杀增加的攻击次数

    // 无人机AI状态枚举
    public enum AIStage {
        WAITING_ORDER, // 等待指令
        MOVE, // 移动向目标
        ATTACK, // 攻击目标
        TELEPORTING, // 传送中
        MANUVER_TO_NEXT_ATTACK_POSITION, // 机动到下一个攻击位置
        END_ATTACK, // 结束攻击
        IN_CD, GO_TO_LAND // 返回母舰
    }

    private List<HSITunnelScript> drones = new ArrayList<>();

    protected static final int MAX_AMMO = 12;
    private static final Map<Integer, Float> facingMap = new HashMap<>();
    private static final Map<Integer, Vector2f> offsetMap = new HashMap<>();
    private final ammoTracker ammoTracker = new ammoTracker(0.1f, 6, 1);
    
    // 发射计时器相关
    private float launchTimer = 0f;
    private int nextDroneToLaunch = -1;
    
    // 追踪上一个浮游炮的曲线方向，用于交替曲线
    private boolean lastDroneCurveRight = false;

    public enum WeaponSide {
        LEFT, RIGHT, NONE
    }

    static WeaponSide weaponSide = WeaponSide.NONE;

    static {
        facingMap.put(0, 180f);
        facingMap.put(1, 180f);
        facingMap.put(2, 180f);
        facingMap.put(3, 180f);
        facingMap.put(4, 180f);
        facingMap.put(5, 180f);
        offsetMap.put(0, new Vector2f(-56f, -46f));
        offsetMap.put(1, new Vector2f(-58f, -26f));
        offsetMap.put(2, new Vector2f(-44f, -10f));
        offsetMap.put(3, new Vector2f(-56f, 46f));
        offsetMap.put(4, new Vector2f(-58f, 26f));
        offsetMap.put(5, new Vector2f(-44f, 10f));
    }

    private static Vector2f findTarget(WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        float range = weapon.getRange();
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
        ShipAPI target = ship.getShipTarget();
        Vector2f targetLoc = null;
        if (ship.getShipAI() != null && ship.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.TARGET_FOR_SHIP_SYSTEM)) {
            target = (ShipAPI) ship.getAIFlags().getCustom(ShipwideAIFlags.AIFlags.TARGET_FOR_SHIP_SYSTEM);
        }
        if (target != null && target.getOwner() != ship.getOwner()) {
            float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
            float radSum = ship.getCollisionRadius() + target.getCollisionRadius();
            if (dist > range + radSum)
                target = null;
        } else {
            if (player) {
                targetLoc = ship.getMouseTarget();
            } else {
                Object test = ship.getAIFlags().getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET);
                if (test instanceof ShipAPI) {
                    target = (ShipAPI) test;
                    float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
                    float radSum = ship.getCollisionRadius() + target.getCollisionRadius();
                    if (dist > range + radSum)
                        target = null;
                }
            }
        }
        if (target == null && targetLoc == null) {
            target = Misc.findClosestShipEnemyOf(ship, ship.getLocation(), ShipAPI.HullSize.FIGHTER, range, true);
        }
        if (target != null && targetLoc == null) {
            targetLoc = target.getLocation();
        }
        return targetLoc;
    }

    Vector2f targetLocation = null;

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        String slotId = weapon.getSlot().getId();
        if (slotId.endsWith("_L")) {
            weaponSide = WeaponSide.LEFT;
        } else if (slotId.endsWith("_R")) {
            weaponSide = WeaponSide.RIGHT;
        }

        //weapon.getAnimation().pause();

        int idleDrones = 0;
        for (HSITunnelScript drone : drones) {
            if (drone.getStage() == AIStage.WAITING_ORDER || drone.getStage() == AIStage.IN_CD) {
                idleDrones++;
            }
        }
        
        // 处理发射计时器
        if (nextDroneToLaunch >= 0) {
            launchTimer += amount;
            if (launchTimer >= LAUNCH_INTERVAL) {
                launchTimer = 0f;
                nextDroneToLaunch = -1;
            }
        }
        
        // 根据待机浮游炮数量设置武器帧（0帧=6个待机，6帧=0个待机）
        //weapon.getAnimation().setFrame(6 - idleDrones);
        //weapon.getAnimation().pause();

        if (idleDrones == 6) {
            boolean inCD = false;
            for (HSITunnelScript drone : drones) {
                drone.mainWeapon.setRemainingCooldownTo(0f);
                if (drone.getStage() == AIStage.IN_CD) {
                    inCD = true;
                }
            }
            if (!inCD) {
                weapon.setRemainingCooldownTo(0f);
                weapon.setAmmo(weapon.getMaxAmmo());
            }
        }

        boolean end = weapon.getShip() != null && !weapon.getShip().isAlive();
        if (end) {
            for (HSITunnelScript drone : drones) {
                if (drone.getDrone().isAlive()) {
                    drone.getDrone().getMutableStats().getHullDamageTakenMult().unmodify("RX_93_BIT");
                    engine.applyDamage(drone.getDrone(), drone.getDrone().getLocation(),
                            100000f, DamageType.ENERGY, 0f, true, false, weapon.getShip());
                }
            }
        }

        int droneNum = 0;
        for (int i = 0; i < 6; i++) {
            if (drones.size() < i + 1) {
                if (ammoTracker.deductOneAmmo()) {
                    spawnAndBindScript(engine, weapon, i);
                    droneNum++;
                } else {
                    break;
                }
            } else {
                if (!drones.get(i).drone.isAlive()) {
                    drones.remove(i);
                    i--;
                } else {
                    drones.get(i).resetPoint(i);
                    droneNum++;
                }
            }
        }

        ammoTracker.advance(amount, droneNum);
        if (!weapon.isFiring()) {
            targetLocation = null;
        } else {
            if (targetLocation == null) {
                targetLocation = findTarget(weapon);
            }
        }
        boolean hasTarget = targetLocation != null;

        checkAsteroidHitAndStop(weapon);

        for (HSITunnelScript drone : drones) {
            drone.advance(amount);
            if (end) {
                drone.notifyEnd();
                continue;
            }
            if (hasTarget) {
                if (drone.getStage() == AIStage.WAITING_ORDER) {
                    if (nextDroneToLaunch < 0) {
                        nextDroneToLaunch = drone.point;
                        launchTimer = 0f;
                        
                        // 确定曲线方向：
                        // - 左侧武器：首个向右曲线（远离母舰），后续交替
                        // - 右侧武器：首个向左曲线（远离母舰），后续交替
                        boolean useCurveRight;
                        if (weaponSide == WeaponSide.LEFT) {
                            // 左侧武器：首个向右，然后交替
                            useCurveRight = lastDroneCurveRight;
                        } else {
                            // 右侧武器：首个向左，然后交替
                            useCurveRight = !lastDroneCurveRight;
                        }
                        
                        // 切换下一个浮游炮的曲线方向
                        lastDroneCurveRight = !lastDroneCurveRight;
                        
                        drone.setStage(AIStage.MOVE);
                        drone.setTarget(targetLocation, useCurveRight);
                    }
                } else {
                    if (drone.getStage() != AIStage.IN_CD) {
                        drone.setStage(AIStage.MOVE);
                        // 已经在移动的浮游炮保持原有曲线方向
                        drone.updateTarget(targetLocation);
                    }
                }
            }
        }
    }

    private static Vector2f getLocation(WeaponAPI weapon, int i) {
        Vector2f offset = offsetMap.get(i);
        if (offset == null) {
            return weapon.getLocation();
        } else {
            if (weaponSide == WeaponSide.LEFT) {
                offset = new Vector2f(offset.x, -offset.y);
            }
            return OffSetToLocation(weapon.getLocation(), weapon.getCurrAngle(), offset);
        }
    }

    private static float getFacing(WeaponAPI weapon, int i) {
        if (facingMap.containsKey(i)) {
            return weapon.getCurrAngle() + facingMap.get(i);
        }
        return weapon.getCurrAngle();
    }

    public void spawnAndBindScript(CombatEngineAPI engine, WeaponAPI weapon, int point) {
        ShipHullSpecAPI spec = Global.getSettings().getHullSpec("armaa_bit_kshatriya");
        ShipVariantAPI v = Global.getSettings().createEmptyVariant("armaa_bit_kshatriya_ShortTrail", spec);
        v.addWeapon("WS0001", "armaa_bit_kshatriya_beam");
        WeaponGroupSpec g = new WeaponGroupSpec(WeaponGroupType.LINKED);
        g.addSlot("WS0001");
        g.setAutofireOnByDefault(false);
        v.addWeaponGroup(g);
        ShipAPI w = engine.createFXDrone(v);
        w.setLayer(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
        w.setOwner(weapon.getShip().getOwner());
        if (weapon.getShip().isAlly()) {
            w.setAlly(true);
        }
        w.setForceHideFFOverlay(true);
        w.setDrone(true);
        w.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f, weapon.getShip());

        w.getSpriteAPI().setTexWidth(0f);
        w.getSpriteAPI().setTexHeight(0f);

        MutableShipStatsAPI self = w.getMutableStats();
        MutableShipStatsAPI source = weapon.getShip().getMutableStats();
        self.getEnergyWeaponDamageMult().applyMods(source.getEnergyWeaponDamageMult());
        self.getEnergyWeaponRangeBonus().applyMods(source.getEnergyWeaponRangeBonus());
        self.getDamageToFighters().applyMods(source.getDamageToFighters());
        self.getDamageToFrigates().applyMods(source.getDamageToFrigates());
        self.getDamageToDestroyers().applyMods(source.getDamageToDestroyers());
        self.getDamageToCruisers().applyMods(source.getDamageToCruisers());
        self.getDamageToCapital().applyMods(source.getDamageToCapital());
        self.getEnergyWeaponFluxCostMod().modifyMult("RX_93_BIT", 0f);
        self.getHardFluxDissipationFraction().modifyFlat("RX_93_BIT", 1f);
        self.getHullDamageTakenMult().modifyMult("RX_93_BIT", 0f);
        self.getTimeMult().modifyMult("RX_93_BIT", 1f);
        self.getEnergyRoFMult().modifyMult("RX_93_BIT", 0.5f);
        self.getCRLossPerSecondPercent().modifyMult("RX_93_BIT", 0f);
        self.getEnergyWeaponDamageMult().modifyMult("RX_93_BIT", 0.5f);
        w.setDoNotFlareEnginesWhenStrafingOrDecelerating(true);
        w.setCollisionClass(CollisionClass.NONE);
        w.setFacing(weapon.getCurrAngle());
        Global.getCombatEngine().addEntity(w);
        w.getLocation().set(getLocation(weapon, point));
        w.setShipAI(null);

        drones.add(new HSITunnelScript(w, weapon, point));
    }

    private void checkAsteroidHitAndStop(WeaponAPI weapon) {
        if (weapon == null || weapon.getBeams().isEmpty()) {
            return;
        }

        for (Object beam : weapon.getBeams()) {
            CombatEntityAPI hitTarget = ((com.fs.starfarer.api.combat.BeamAPI) beam).getDamageTarget();

            if (hitTarget != null && isAsteroid(hitTarget)) {
                hitTarget.getVelocity().set(0f, 0f);
                hitTarget.setAngularVelocity(0f);
            }
        }
    }

    private boolean isAsteroid(CombatEntityAPI entity) {
        if (entity instanceof CombatAsteroidAPI) {
            return true;
        }
        return entity.getCollisionClass() == CollisionClass.ASTEROID;
    }

    public static class HSITunnelScript {
        private ShipAPI drone;
        private AIStage stage;
        private WeaponAPI source;
        private WeaponAPI mainWeapon;
        private Vector2f target = null;
        private Vector2f expectedLocation = new Vector2f();
        private final float turnRate, maxSpeed;
        private boolean ended = false;
        private int point = 0;
        private boolean fired = false;
        
        // 曲线方向（true=向右曲线，false=向左曲线）
        private boolean curveRight = true;
        
        // 攻击次数
        private int attackCount = 0;
        private final int maxAttackCount = 5;
        private float attackAngleOffset = 0f;
        
        // 传送相关变量
        private float teleportTimer = 0f;
        private Vector2f teleportTarget = null;
        private float teleportFacing = 0f;
        private boolean teleportDone = false;

        private Vector2f controlPoint1 = null;
        private Vector2f controlPoint2 = null;
        private float bezierProgress = 0f;
        private Vector2f bezierStartPoint = null;
        private float totalBezierDistance = 0f;
        private float accumulatedDistance = 0f;

        public void resetPoint(int point) {
            this.point = point;
        }

        public HSITunnelScript(ShipAPI drone, WeaponAPI weapon, int point) {
            this.drone = drone;
            stage = AIStage.WAITING_ORDER;
            source = weapon;
            this.point = point;
            this.attackAngleOffset = (float) (Math.random() * 120f - 60f);
            turnRate = 720f;
            maxSpeed = 600f;
            this.point = point;

            for (WeaponAPI w : drone.getAllWeapons()) {
                if (!w.isDecorative()) {
                    this.mainWeapon = w;
                    break;
                }
            }
        }

        ShipAPI shipTarget = null;
        float CD = 0;

        public void advance(float amount) {
            if (ended)
                return;

            float timeMult = 1f;

            if (drone != null && drone.isAlive() && source != null && source.getShip().isAlive()) {
                MutableShipStatsAPI self = drone.getMutableStats();
                MutableShipStatsAPI mothership = source.getShip().getMutableStats();

                timeMult = mothership.getTimeMult().getMult();
                self.getTimeMult().modifyMult("RX_93_BIT", timeMult);

                if (timeMult != 1f) {
                    float flameAdjust = Math.max(-0.1f, Math.min(0.1f, (timeMult - 1f) * -0.05f));
                    drone.getEngineController().extendFlame(this, flameAdjust, flameAdjust, flameAdjust);
                }

                self.getEnergyWeaponDamageMult().modifyMult("RX_93_BIT",
                        mothership.getEnergyWeaponDamageMult().getMult());
                self.getEnergyWeaponRangeBonus().modifyMult("RX_93_BIT",
                        mothership.getEnergyWeaponRangeBonus().getMult());
                self.getDamageToFighters().modifyMult("RX_93_BIT", mothership.getDamageToFighters().getMult());
                self.getDamageToFrigates().modifyMult("RX_93_BIT", mothership.getDamageToFrigates().getMult());
                self.getDamageToDestroyers().modifyMult("RX_93_BIT", mothership.getDamageToDestroyers().getMult());
                self.getDamageToCruisers().modifyMult("RX_93_BIT", mothership.getDamageToCruisers().getMult());
                self.getDamageToCapital().modifyMult("RX_93_BIT", mothership.getDamageToCapital().getMult());
            }

            if (stage == AIStage.WAITING_ORDER || stage == AIStage.IN_CD) {
                drone.setExtraAlphaMult2(0f);
                drone.setLayer(CombatEngineLayers.BELOW_SHIPS_LAYER);
                drone.setRenderEngines(false);
                mainWeapon.setRemainingCooldownTo(0f);
            } else {
                drone.setExtraAlphaMult2(1f);
                drone.setLayer(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
                drone.setRenderEngines(true);
            }

            float toFace = drone.getFacing();
            float mult = 1f;
            
            switch (stage) {
                case IN_CD:
                    if (CD > 0) {
                        CD -= amount * timeMult;
                    } else {
                        setStage(AIStage.WAITING_ORDER);
                    }
                case WAITING_ORDER:
                    expectedLocation = getLocation(source, point);
                    break;
                    
                case MOVE:
                    if (controlPoint1 == null || controlPoint2 == null) {
                        Vector2f startPos = new Vector2f(drone.getLocation());
                        bezierStartPoint = new Vector2f(startPos);
                        Vector2f endPos = target;
                        Vector2f direction = Vector2f.sub(endPos, startPos, null);
                        float distance = direction.length();

                        if (distance == 0f || Float.isNaN(distance) || distance < 0.001f) {
                            setStage(AIStage.ATTACK);
                            controlPoint1 = null;
                            controlPoint2 = null;
                            bezierStartPoint = null;
                            totalBezierDistance = 0f;
                            accumulatedDistance = 0f;
                            break;
                        }

                        direction.normalise();
                        Vector2f perpendicular = new Vector2f(-direction.y, direction.x);

                        // 根据curveRight字段决定曲线方向
                        if (curveRight) {
                            // 向右曲线
                            controlPoint1 = new Vector2f(
                                    startPos.x - direction.x * distance * 0.5f + perpendicular.x * distance * 0.8f,
                                    startPos.y - direction.y * distance * 0.5f + perpendicular.y * distance * 0.8f);
                            controlPoint2 = new Vector2f(
                                    endPos.x - direction.x * distance * 0.3f + perpendicular.x * distance * 0.6f,
                                    endPos.y - direction.y * distance * 0.3f + perpendicular.y * distance * 0.6f);
                        } else {
                            // 向左曲线
                            controlPoint1 = new Vector2f(
                                    startPos.x - direction.x * distance * 0.5f - perpendicular.x * distance * 0.8f,
                                    startPos.y - direction.y * distance * 0.5f - perpendicular.y * distance * 0.8f);
                            controlPoint2 = new Vector2f(
                                    endPos.x - direction.x * distance * 0.3f - perpendicular.x * distance * 0.6f,
                                    endPos.y - direction.y * distance * 0.3f - perpendicular.y * distance * 0.6f);
                        }

                        totalBezierDistance = calculateBezierLength(bezierStartPoint, controlPoint1, controlPoint2,
                                target);
                        bezierProgress = 0f;
                        accumulatedDistance = 0f;
                    }

                    mult = 2.5f;
                    float moveDistance = maxSpeed * mult * amount * timeMult;
                    accumulatedDistance += moveDistance;
                    bezierProgress = Math.min(1f, accumulatedDistance / totalBezierDistance);

                    expectedLocation = calculateBezierPoint(
                            bezierStartPoint,
                            controlPoint1,
                            controlPoint2,
                            target,
                            bezierProgress);

                    if (bezierProgress >= 1f) {
                        setStage(AIStage.ATTACK);
                        controlPoint1 = null;
                        controlPoint2 = null;
                        bezierStartPoint = null;
                        totalBezierDistance = 0f;
                        accumulatedDistance = 0f;
                    }
                    break;

                case TELEPORTING:
                    // 传送状态：参考CA_SlashMissileAI的传送特效
                    drone.setJitter(drone, Color.cyan, 2f, 5, 2f, 3f);
                    teleportTimer += amount * timeMult;
                    
                    if (teleportTimer > TELEPORT_DELAY) {
                        if (!teleportDone) {
                            // 在传送前创建残影（留在原地）
                            if (ENABLE_AFTERIMAGE) {
                                createAfterimage(drone);
                            }
                            
                            // 执行传送
                            teleportDone = true;
                            drone.getLocation().set(teleportTarget);
                            drone.setFacing(teleportFacing);
                            
                            // 播放传送音效
                            /*
                            Global.getSoundPlayer().playSound("Moci_bit_blink", 1f, 0.7f,
                                    drone.getLocation(), drone.getVelocity());

                             */
                        } else {
                            // 传送完成，直接进入攻击状态
                            teleportTimer = 0f;
                            teleportDone = false;
                            setStage(AIStage.ATTACK);
                        }
                    }
                    break;

                case ATTACK:
                    // 转火逻辑：如果目标死亡或不存在，寻找新目标
                    if (shipTarget == null || !shipTarget.isAlive()) {
                        // 击杀奖励：如果击杀了目标且开启了击杀奖励，增加攻击次数
                        if (shipTarget != null && !shipTarget.isAlive() && ENABLE_KILL_BONUS && attackCount > 0) {
                            attackCount = Math.max(0, attackCount - BONUS_ATTACKS_PER_KILL);
                        }
                        
                        // 寻找新目标
                        shipTarget = AIUtils.getNearestEnemy(drone);
                        if (shipTarget != null && Misc.getDistance(drone.getLocation(),
                                shipTarget.getLocation()) > mainWeapon.getRange()) {
                            shipTarget = null;
                        }
                    }
                    
                    if (shipTarget == null) {
                        attackCount = 0;
                        attackAngleOffset = 0f;
                        setStage(AIStage.GO_TO_LAND);
                        target = null;
                        fired = false;
                        return;
                    }
                    
                    // 攻击距离在40%-60%之间随机浮动
                    float rangeMultiplier = 0.3f + (float) Math.random() * 0.25f; // 0.4 到 0.6
                    float dynamicAttackRange = mainWeapon != null ? mainWeapon.getRange() * rangeMultiplier : 240f;
                    expectedLocation = Vector2f.add(shipTarget.getLocation(),
                            (Vector2f) Misc.getUnitVectorAtDegreeAngle(
                                    MathUtils.clampAngle(shipTarget.getFacing() + point * 60f + attackAngleOffset)
                                            + 120f)
                                    .scale(shipTarget.getCollisionRadius() + dynamicAttackRange),
                            null);

                    float attackRange = mainWeapon != null ? mainWeapon.getRange() : 300f;
                    float rotationDiff = Math.abs(MathUtils.getShortestRotation(drone.getFacing(),
                            VectorUtils.getAngle(drone.getLocation(), shipTarget.getLocation())));
                    float distanceToTarget = MathUtils.getDistance(drone.getLocation(), shipTarget.getLocation());

                    if (rotationDiff <= 1f && distanceToTarget <= attackRange) {
                        drone.giveCommand(ShipCommand.FIRE, shipTarget.getLocation(), 0);
                        fired = true;
                    }
                    if (fired && mainWeapon != null && mainWeapon.getCooldownRemaining() > 0 &&
                            mainWeapon.getBeams().isEmpty()) {

                        attackCount++;
                        
                        // 关键修改：第一次攻击后使用传送，之后的攻击也使用传送
                        if (attackCount < maxAttackCount) {
                            // 重置武器冷却
                            mainWeapon.setRemainingCooldownTo(0f);
                            
                            // 计算下一个攻击位置的角度偏移
                            attackAngleOffset += 180f + (Math.random() > 0.5 ? 45f : -45f);
                            
                            // 使用原有的攻击位置计算逻辑，距离在40%-60%之间随机浮动
                            float rangeMultiplier2 = 0.4f + (float) Math.random() * 0.2f; // 0.4 到 0.6
                            float dynamicAttackRange2 = mainWeapon != null ? mainWeapon.getRange() * rangeMultiplier2 : 240f;
                            teleportTarget = Vector2f.add(shipTarget.getLocation(),
                                    (Vector2f) Misc.getUnitVectorAtDegreeAngle(
                                            MathUtils.clampAngle(shipTarget.getFacing() + point * 60f + attackAngleOffset)
                                                    + 120f)
                                            .scale(shipTarget.getCollisionRadius() + dynamicAttackRange2),
                                    null);
                            
                            // 计算传送后的朝向（直接朝向目标）
                            teleportFacing = VectorUtils.getAngle(teleportTarget, shipTarget.getLocation());
                            
                            // 进入传送状态
                            teleportTimer = 0f;
                            teleportDone = false;
                            setStage(AIStage.TELEPORTING);
                            
                            fired = false;
                        } else {
                            // 攻击次数达到上限，结束攻击
                            CD = attackCount * ((float) 5 / maxAttackCount);
                            attackCount = 0;
                            shipTarget = null;
                            attackAngleOffset = 0f;
                            setStage(AIStage.GO_TO_LAND);
                            target = null;
                            fired = false;
                        }
                    }
                    break;

                case MANUVER_TO_NEXT_ATTACK_POSITION:
                    setStage(AIStage.ATTACK);
                    break;
                    
                case END_ATTACK:
                    setStage(AIStage.GO_TO_LAND);
                    break;
                    
                case GO_TO_LAND:
                    mult = 1.5f * timeMult;
                    expectedLocation = getLocation(source, point);
                    if (MathUtils.getDistanceSquared(expectedLocation, drone.getLocation()) <= 900f) {
                        setStage(AIStage.IN_CD);
                    }
                    break;
            }

            float expectedFacing = VectorUtils.getAngle(drone.getLocation(), expectedLocation);
            if (stage.equals(AIStage.ATTACK)) {
                if (shipTarget != null) {
                    expectedFacing = VectorUtils.getAngle(drone.getLocation(), shipTarget.getLocation());
                } else {
                    expectedFacing = VectorUtils.getAngle(drone.getLocation(), target);
                }
            }
            if (stage.equals(AIStage.WAITING_ORDER) || stage.equals(AIStage.IN_CD)) {
                expectedFacing = getFacing(source, point);
            }
            
            // 传送状态下不进行转向和移动
            if (!stage.equals(AIStage.TELEPORTING)) {
                float rotation = MathUtils.getShortestRotation(drone.getFacing(), expectedFacing);
                float effectiveTurnAmount = turnRate * amount * timeMult;
                if (Math.abs(rotation) > effectiveTurnAmount) {
                    toFace = drone.getFacing() + Math.signum(rotation) * effectiveTurnAmount;
                } else {
                    toFace = drone.getFacing() + rotation;
                }
                drone.setFacing(toFace);

                Vector2f diff = Vector2f.sub(expectedLocation, drone.getLocation(), null);

                if (diff.length() != 0) {
                    if (!stage.equals(AIStage.WAITING_ORDER) && !stage.equals(AIStage.IN_CD)) {
                        float effectiveAmount = amount * timeMult;
                        if (diff.length() > maxSpeed * effectiveAmount) {
                            diff.scale(maxSpeed * mult * effectiveAmount / diff.length());
                        }
                        drone.getEngineController().forceShowAccelerating();
                        drone.giveCommand(ShipCommand.ACCELERATE, drone.getMouseTarget(), 0);
                        for (ShipEngineControllerAPI.ShipEngineAPI e : drone.getEngineController().getShipEngines()) {
                            drone.getEngineController().setFlameLevel(e.getEngineSlot(), 2f);
                        }
                    }
                }
                Vector2f shouldMove = Vector2f.add(drone.getLocation(), diff, null);
                drone.getLocation().set(shouldMove);
            }
        }

        private Vector2f calculateBezierPoint(Vector2f p0, Vector2f p1, Vector2f p2, Vector2f p3, float t) {
            float u = 1 - t;
            float tt = t * t;
            float uu = u * u;
            float uuu = uu * u;
            float ttt = tt * t;

            Vector2f point = new Vector2f();
            point.x = uuu * p0.x + 3 * uu * t * p1.x + 3 * u * tt * p2.x + ttt * p3.x;
            point.y = uuu * p0.y + 3 * uu * t * p1.y + 3 * u * tt * p2.y + ttt * p3.y;

            return point;
        }

        private float calculateBezierLength(Vector2f p0, Vector2f p1, Vector2f p2, Vector2f p3) {
            float length = 0f;
            int segments = 20;
            Vector2f prevPoint = new Vector2f(p0);

            for (int i = 1; i <= segments; i++) {
                float t = (float) i / segments;
                Vector2f currentPoint = calculateBezierPoint(p0, p1, p2, p3, t);
                length += MathUtils.getDistance(prevPoint, currentPoint);
                prevPoint = currentPoint;
            }

            return length;
        }

        public void notifyEnd() {
            ended = true;
        }

        public void beginLand() {
        }

        public void setStage(AIStage stage) {
            this.stage = stage;
        }

        public AIStage getStage() {
            return stage;
        }

        public ShipAPI getDrone() {
            return drone;
        }
        
        /**
         * 为无人机创建残影效果
         * 只为船体和装饰武器创建残影，跳过主武器
         */
        private void createAfterimage(ShipAPI ship) {
            // 获取船体精灵的偏移量
            SpriteAPI sprite = ship.getSpriteAPI();
            float offsetX = sprite.getWidth() / 2 - sprite.getCenterX();
            float offsetY = sprite.getHeight() / 2 - sprite.getCenterY();

            // 计算旋转后的偏移量
            float trueOffsetX = (float) Math.cos(Math.toRadians(ship.getFacing() - 90f)) * offsetX -
                    (float) Math.sin(Math.toRadians(ship.getFacing() - 90f)) * offsetY;
            float trueOffsetY = (float) Math.sin(Math.toRadians(ship.getFacing() - 90f)) * offsetX +
                    (float) Math.cos(Math.toRadians(ship.getFacing() - 90f)) * offsetY;

            // 只为装饰武器创建残影
            for (WeaponAPI weapon : ship.getAllWeapons()) {
                // 跳过非装饰武器（主武器）
                if (!weapon.isDecorative()) continue;
                
                if (weapon.getSprite() == null) continue;

                // 检查武器是否被设置为完全透明
                boolean isHidden = false;
                if (weapon.getSprite() != null && weapon.getSprite().getColor().getAlpha() == 0) {
                    isHidden = true;
                }
                if (weapon.getBarrelSpriteAPI() != null && weapon.getBarrelSpriteAPI().getColor().getAlpha() == 0) {
                    isHidden = true;
                }
                if (weapon.getUnderSpriteAPI() != null && weapon.getUnderSpriteAPI().getColor().getAlpha() == 0) {
                    isHidden = true;
                }
                if (weapon.getGlowSpriteAPI() != null && weapon.getGlowSpriteAPI().getColor().getAlpha() == 0) {
                    isHidden = true;
                }

                if (isHidden) continue;

                // 获取武器贴图路径
                String spritePath;
                if (weapon.getAnimation() != null) {
                    // 对于动画武器，获取当前帧的贴图路径
                    int frame = weapon.getAnimation().getFrame();
                    String basePath = weapon.getSlot().isTurret() ?
                            weapon.getSpec().getTurretSpriteName() :
                            weapon.getSpec().getHardpointSpriteName();
                    // 移除_XX.png后缀（下划线+两位数字+.png = 7个字符）
                    if (basePath.endsWith(".png")) {
                        // 检查是否已经包含帧号
                        if (basePath.matches(".*_\\d{2}\\.png$")) {
                            basePath = basePath.substring(0, basePath.length() - 7);
                        } else {
                            basePath = basePath.substring(0, basePath.length() - 4);
                        }
                    }
                    // 添加帧号和后缀
                    spritePath = String.format("%s_%02d.png", basePath, frame);
                } else {
                    // 无动画的武器使用默认贴图
                    spritePath = weapon.getSlot().isTurret() ?
                            weapon.getSpec().getTurretSpriteName() :
                            weapon.getSpec().getHardpointSpriteName();
                }

                // 获取新的Sprite实例
                SpriteAPI weaponSprite = Global.getSettings().getSprite(spritePath);
                weaponSprite.setSize(weapon.getSprite().getWidth(), weapon.getSprite().getHeight());

                // 创建残影 - 使用白色(255,255,255)即为原色半透明
                MagicRender.battlespace(
                        weaponSprite,
                        new Vector2f(weapon.getLocation().x + trueOffsetX, weapon.getLocation().y + trueOffsetY),
                        new Vector2f(0, 0),
                        new Vector2f(weapon.getSprite().getWidth(), weapon.getSprite().getHeight()),
                        new Vector2f(0, 0),
                        weapon.getCurrAngle() - 90f,
                        0f,
                        new Color(255, 255, 255, (int) (AFTERIMAGE_ALPHA_START * 255)),
                        true,
                        0f,  // jitterRange
                        0f,  // jitterTilt
                        0f,  // flickerRange
                        0f,  // flickerMedian
                        0f,  // maxDelay
                        AFTERIMAGE_FADE_IN,   // fadein
                        0f,  // full - 完全显示时间
                        AFTERIMAGE_FADE_OUT,  // fadeout
                        CombatEngineLayers.BELOW_SHIPS_LAYER
                );
            }
        }

        /**
         * 设置目标位置并指定曲线方向（用于首次发射）
         * @param target 目标位置
         * @param useCurveRight true=向右曲线，false=向左曲线
         */
        public void setTarget(Vector2f target, boolean useCurveRight) {
            if (target == null) {
                return;
            }

            // 保存曲线方向
            this.curveRight = useCurveRight;

            // 根据曲线方向计算目标位置的偏移
            int facing = useCurveRight ? 1 : -1;

            float attackRange = mainWeapon != null ? mainWeapon.getRange() : 300f;
            Vector2f calculatedTarget = MathUtils.getPointOnCircumference(target, facing * 60 * point, attackRange);

            if (drone != null && MathUtils.getDistance(drone.getLocation(), calculatedTarget) < 10f) {
                calculatedTarget = MathUtils.getPointOnCircumference(target, facing * 60 * point, attackRange + 100f);
            }

            this.target = calculatedTarget;
        }
        
        /**
         * 更新目标位置（保持原有曲线方向）
         * @param target 新的目标位置
         */
        public void updateTarget(Vector2f target) {
            if (target == null) {
                return;
            }

            // 使用已保存的曲线方向
            int facing = curveRight ? 1 : -1;

            float attackRange = mainWeapon != null ? mainWeapon.getRange() : 300f;
            Vector2f calculatedTarget = MathUtils.getPointOnCircumference(target, facing * 60 * point, attackRange);

            if (drone != null && MathUtils.getDistance(drone.getLocation(), calculatedTarget) < 10f) {
                calculatedTarget = MathUtils.getPointOnCircumference(target, facing * 60 * point, attackRange + 100f);
            }

            this.target = calculatedTarget;
        }
    }

    private static class ammoTracker implements AmmoTrackerAPI {

        private float ammoPerSecond;

        private ammoTracker(float ammoPerSecond, int maxAmmo, float reloadSize) {
            this.ammoPerSecond = ammoPerSecond;
            this.maxAmmo = maxAmmo;
            this.ammo = maxAmmo;
            this.reloadSize = reloadSize;
        }

        public void advance(float amount, int droneNum) {
            int max = maxAmmo - droneNum;
            if (ammo < max) {
                reloadProgress += ammoPerSecond * amount;
                while (reloadProgress >= reloadSize) {
                    reloadProgress -= (int) reloadSize;
                    ammo += (int) reloadSize;
                }
                if (ammo > max) {
                    ammo = max;
                }
            } else {
                reloadProgress = 0;
            }
        }

        @Override
        public void setAmmoPerSecond(float ammoPerSecond) {
            this.ammoPerSecond = ammoPerSecond;
        }

        float reloadProgress = 0;

        @Override
        public float getReloadProgress() {
            return reloadProgress / reloadSize;
        }

        int ammo = 0;

        @Override
        public void setAmmo(int ammo) {
            this.ammo = ammo;
        }

        @Override
        public boolean usesAmmo() {
            return true;
        }

        @Override
        public void addOneAmmo() {
            if (ammo < maxAmmo) {
                ammo += 1;
            }
        }

        @Override
        public boolean deductOneAmmo() {
            if (ammo >= 1) {
                ammo -= 1;
                return true;
            }
            return false;
        }

        @Override
        public int getAmmo() {
            return ammo;
        }

        @Override
        public float getAmmoPerSecond() {
            return ammoPerSecond;
        }

        private int maxAmmo;

        @Override
        public int getMaxAmmo() {
            return maxAmmo;
        }

        @Override
        public void resetAmmo() {
            this.ammo = maxAmmo;
        }

        @Override
        public void setMaxAmmo(int maxAmmo) {
            this.maxAmmo = maxAmmo;
        }

        private float reloadSize;

        @Override
        public float getReloadSize() {
            return reloadSize;
        }

        @Override
        public void setReloadSize(float reloadSize) {
            this.reloadSize = reloadSize;
        }

        @Override
        public void setReloadProgress(float progress) {
            this.reloadProgress = progress * reloadSize;
        }
    }

    public static Vector2f OffSetToLocation(Vector2f sLoc, float sFac, Vector2f offset) {
        Vector2f loc = new Vector2f(offset);
        VectorUtils.rotate(loc, sFac, loc);
        Vector2f.add(loc, sLoc, loc);
        return loc;
    }
}
package data.hullmods.selector;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

public class armaa_selector_head_command extends BaseHullMod {

    public static final float SIGHT_BONUS = 25f;         // % sensor range increase
    public static final float RECOVERY_BONUS = 25f;       // flat EW rating added
    public static final float FIGHTER_REPLACE_BONUS = 25f; // % faster fighter replacement
    private IntervalUtil timer = new IntervalUtil(0.5f,0.5f);
    private static final float AURA_RANGE = 1000f;     // tune this to taste
    private static final int MAX_BUFFED = 10;
    private final List<ShipAPI> previouslyBuffed = new ArrayList<>();
    private static final String MOD_ID = "armaa_head_command";
    private boolean frame = false;
    @Override
    public int getDisplaySortOrder() {
        return 2000;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 3;
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSightRadiusMod().modifyPercent(id, SIGHT_BONUS);
        stats.getSensorStrength().modifyPercent(id, SIGHT_BONUS);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {

    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) {
            return "Commander Head";
        }
        if (index == 1) {
            return "Remove this hullmod to cycle between heads.";
        }
        return null;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) {
            return;
        }
        if(engine.isPaused())
            return;
        ship.getMutableStats().getDynamic().getMod(Stats.COMMAND_POINT_RATE_FLAT).modifyFlat(MOD_ID, RECOVERY_BONUS * 0.01f);
       
        // Throttle updates - no need to run every frame
        timer.advance(amount);
        if(!timer.intervalElapsed())
            return;
        SpriteAPI waveSprite = Global.getSettings().getSprite("misc", "wormhole_ring");
        if(frame && Math.random() < 0.75f)
            MagicRender.objectspace(
                    waveSprite,
                    ship,
                    new Vector2f(),
                    new Vector2f(),
                    new Vector2f(0f, 0f),
                    new Vector2f(AURA_RANGE*2f, AURA_RANGE*2f),
                    0f,
                    25f,
                    true,
                    new Color(0,150,55,200),
                    true,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0.1f,
                    0.5f,
                    0.6f,
                    true,
                    CombatEngineLayers.BELOW_SHIPS_LAYER
            );         
        frame = !frame;
        // Clear previous buffs
        
        for (ShipAPI buffed : previouslyBuffed) {
            buffed.getMutableStats().getTimeMult().unmodify(MOD_ID);
        }
        previouslyBuffed.clear();

        // Gather candidates in range
        List<ShipAPI> pilotedStrikecraftFrigates = new ArrayList<>();
        List<ShipAPI> strikecraftFrigates = new ArrayList<>();
        List<ShipAPI> pilotedFighters = new ArrayList<>();
        List<ShipAPI> fighters = new ArrayList<>();

        for (ShipAPI other : engine.getShips()) {
            if (other == ship) {
                continue;
            }
            if (!other.isAlive()) {
                continue;
            }
            if (other.getOwner() != ship.getOwner()) {
                continue;
            }

            float dist = Misc.getDistance(ship.getLocation(), other.getLocation());
            if (dist > AURA_RANGE) {
                continue;
            }

            boolean isPiloted = other.getCaptain() != null && !other.getCaptain().isDefault();
            boolean isFighter = other.getHullSize() == ShipAPI.HullSize.FIGHTER;
            boolean isStrikecraftOrFrigate = other.getHullSize() == ShipAPI.HullSize.FRIGATE;


            if (isPiloted && isStrikecraftOrFrigate) {
                pilotedStrikecraftFrigates.add(other);
            } else if (isStrikecraftOrFrigate) {
                strikecraftFrigates.add(other);
            } else if (isPiloted && isFighter) {
                pilotedFighters.add(other);
            } else if (isFighter) {
                fighters.add(other);
            }
        }

        // Build final list respecting priority order up to MAX_BUFFED
        List<ShipAPI> candidates = new ArrayList<>();
        for (List<ShipAPI> group : Arrays.asList(
                pilotedStrikecraftFrigates,
                strikecraftFrigates,
                pilotedFighters,
                fighters)) {
            for (ShipAPI candidate : group) {
                if (candidates.size() >= MAX_BUFFED) {
                    break;
                }
                candidates.add(candidate);
            }
            if (candidates.size() >= MAX_BUFFED) {
                break;
            }
        }

        // Apply buff to final candidates
        float dilationMult = 1f + getDilationAmount(ship);
        for (ShipAPI target : candidates) {
            target.getMutableStats().getTimeMult().modifyMult(MOD_ID, dilationMult);
            previouslyBuffed.add(target);
        }
    }

    private float getDilationAmount(ShipAPI ship) {
        // TODO: hook into your level/range system
        // Returning LV4 range 1 value for now
        return 0.10f;
    }
    private final Color HL = Global.getSettings().getColor("hColor");
    private final Color F = Global.getSettings().getColor("textFriendColor");
    private final Color E = Global.getSettings().getColor("textEnemyColor");

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
            ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float padS = 2f;
        Color[] pos = {Misc.getHighlightColor(), F};

        tooltip.addSectionHeading("Tactical Overview", Alignment.MID, pad);
        tooltip.addPara(
                "Replaces the standard head with an advanced command-and-control suite, "
                + "turning this unit into a force multiplier for nearby allied assets.",
                padS
        );

        tooltip.addSectionHeading("Bonuses", Alignment.MID, pad);
        tooltip.addPara("%s Sensor range increased by %s.",
                padS, pos, "-", (int) SIGHT_BONUS + "%");
        tooltip.addPara("%s Command point regen increased by %s.",
                padS, pos, "-", String.valueOf(RECOVERY_BONUS) + "%");
        tooltip.addPara("%s Increases the performance of up to %s Strikecraft, Frigates, and Fighters within %s su by %s. This effect prioritizes %s.",
                padS, pos, "-",""+MAX_BUFFED,AURA_RANGE+"", (int) 10 + "%","units with officers assigned");

        tooltip.addSectionHeading("Notes", Alignment.MID, pad);
        tooltip.addPara(
                "Best suited for fleet engagements where coordination and battlefield awareness "
                + "outweigh raw firepower. Pairs well wolfpack compositions.",
                padS
        );
    }
}

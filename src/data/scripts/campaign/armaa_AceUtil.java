package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.HashSet;
import java.util.Set;

/**
 * Both acquisition paths (latent talent awakening in
 * armaa_wingmanPromotion and the story-point button in armaa_squadManagerIntel)
 * route through grantAce() so the fleet-wide hard cap is enforced.
 */
public class armaa_AceUtil {

    // The .skill spec id
    public static final String ACE_SKILL_ID = "armaa_VeteranPilot";

    // Default fleet-wide hard cap; overridden by LunaSettings if present. TODO
    public static final int DEFAULT_ACE_CAP = 3;

    public static int getAceCap() {
        if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
            try {
                return lunalib.lunaSettings.LunaSettings.getInt("armaa", "armaa_wingcomAceCap");
            } catch (Exception e) {
                // setting not defined; fall through to default
            }
        }
        return DEFAULT_ACE_CAP;
    }

    public static boolean hasAce(PersonAPI p) {
        if (p == null) return false;
        return p.getStats().getSkillLevel(ACE_SKILL_ID) > 0f;
    }

    /**
     * Counts every Ace-skilled pilot the player effectively owns:
     *  - commissioned officers in the fleet
     *  - WINGCOM squad wingmen stored in persistent data
     *
     * Uses a Set of person ids so a pilot can't be double-counted.
     */
    // Hand-authored Aces (e.g. Dawn) are exempt from the player's cap
    // story characters, not player-created Aces, so they don't consume a slot.
    public static final Set<String> CAP_EXEMPT_IDS = new HashSet<String>(
            java.util.Arrays.asList("armaa_dawn"));

    public static int countPlayerAces() {
        Set<String> seen = new HashSet<String>();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return 0;
        FleetDataAPI data = fleet.getFleetData();

        // 1) Commissioned officers via the officer roster, so benched/unassigned
        //    officers are still counted (not just those currently captaining a ship).
        for (OfficerDataAPI od : data.getOfficersCopy()) {
            PersonAPI p = od.getPerson();
            if (p != null && !CAP_EXEMPT_IDS.contains(p.getId()) && hasAce(p)) {
                seen.add(p.getId());
            }
        }

        // The player commander (not in the officer roster).
        PersonAPI commander = data.getCommander();
        if (commander != null && !CAP_EXEMPT_IDS.contains(commander.getId()) && hasAce(commander)) {
            seen.add(commander.getId());
        }

        // WINGCOM squad wingmen
        for (FleetMemberAPI m : data.getMembersListCopy()) {
            PersonAPI cap = m.getCaptain();
            if (cap == null || cap.isDefault()) continue;
            String capId = cap.getId();
            Object sizeObj = Global.getSector().getPersistentData()
                    .get("armaa_wingCommander_squadSize_" + capId);
            if (!(sizeObj instanceof Integer)) continue;
            int count = (Integer) sizeObj;
            for (int i = 0; i < count; i++) {
                Object po = Global.getSector().getPersistentData()
                        .get("armaa_wingCommander_wingman_" + i + "_" + capId);
                if (po instanceof PersonAPI) {
                    PersonAPI wp = (PersonAPI) po;
                    if (!CAP_EXEMPT_IDS.contains(wp.getId()) && hasAce(wp)) {
                        seen.add(wp.getId());
                    }
                }
            }
        }
        return seen.size();
    }

    /** True if granting another Ace would stay within the hard cap. */
    public static boolean canGrantAce() {
        return countPlayerAces() < getAceCap();
    }

    /**
     * Grants the veteran skill (additive - does not consume an existing slot).
     * Returns false if the pilot already has it or the cap is reached.
     *
     * NOTE: this does not persist the PersonAPI back into persistent data
     * the caller should re-put the pilot object afterward, matching the
     * existing pattern in the WINGCOM code.
     */
    public static boolean grantAce(PersonAPI pilot) {
        if (pilot == null) return false;
        if (hasAce(pilot)) return false;
        if (!canGrantAce()) return false;

        MutableCharacterStatsAPI stats = pilot.getStats();
        stats.setSkillLevel(ACE_SKILL_ID, 1f);
        return true;
    }
}
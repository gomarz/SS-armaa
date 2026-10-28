package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import java.util.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.HullModItemManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.campaign.CargoAPI;
import org.apache.log4j.Logger;

/**
 * Legacy AI core bookkeeping for the OVERLORD SUITE hullmods.
 *
 * The suites used to take their AI core out of the cargo themselves and this
 * script handed it back when the suite was removed. They now declare the core
 * through getRequiredItem(), so vanilla's HullModItemManager takes it on install
 * and gives it back on removal. Refunding here as well returned two cores per
 * removal, which is how add/remove cycles were minting cores.
 *
 * What's left is migration for saves from before that change. A ship whose core
 * vanilla is holding gets its legacy key dropped, since vanilla will refund it.
 * A ship vanilla knows nothing about had its core taken by the old code, so this
 * script still refunds that one when the suite comes off. Kept as a hullmod
 * because existing variants carry it as a perma-mod.
 */
public class armaa_aicoreutilityscript extends BaseHullMod {

	public static final String ID = "armaa_aicoreutilityscript";
	private static final Logger Log = Logger.getLogger(armaa_aicoreutilityscript.class);

	private static final String[][] SUITES = {
		{"alpha_core_skymind_check_", "armaa_skyMindAlpha", Commodities.ALPHA_CORE},
		{"beta_core_skymind_check_", "armaa_skyMindBeta", Commodities.BETA_CORE},
		{"gamma_core_skymind_check_", "armaa_skyMindGamma", Commodities.GAMMA_CORE},
	};

	@Override
	public void advanceInCampaign(FleetMemberAPI member, float amount) {
		checkMember(member);
	}

	/** Also run on game load, so a refit straight after loading can't slip past the migration. */
	public static void checkPlayerFleet() {
		CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
		if (fleet == null || fleet.getFleetData() == null) {
			return;
		}
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			if (member.getVariant() != null && member.getVariant().hasHullMod(ID)) {
				checkMember(member);
			}
		}
	}

	public static void checkMember(FleetMemberAPI member) {
		if (member.getVariant() == null) {
			return;
		}
		final Map<String, Object> data = Global.getSector().getPersistentData();
		boolean anyLegacy = false;
		for (String[] suite : SUITES) {
			String key = suite[0] + member.getId();
			if (!data.containsKey(key)) {
				continue;
			}
			String modId = suite[1];
			String item = suite[2];
			if (member.getVariant().hasHullMod(modId)) {
				if (vanillaHoldsItem(member, item)) {
					data.remove(key);
				} else {
					anyLegacy = true;
				}
			} else {
				Log.info("Refunding legacy " + item + " for " + member.getId());
				data.remove(key);
				addPlayerCommodityItem(item, 1);
			}
		}
		if (!anyLegacy) {
			member.getVariant().removePermaMod(ID);
		}
	}

	private static boolean vanillaHoldsItem(FleetMemberAPI member, String item) {
		CargoAPI inUse = HullModItemManager.getInstance().getItemsInUseBy(member);
		return inUse.getCommodityQuantity(item) > 0;
	}

	public static void addPlayerCommodityItem(final String id, final int amount) {
		final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		if (playerFleet == null) {
			return;
		}
		playerFleet.getCargo().addCommodity(id, amount);
	}

}

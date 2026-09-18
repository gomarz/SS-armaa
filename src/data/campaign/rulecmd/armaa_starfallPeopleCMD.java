package data.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

/**
 * Creates the Starfall Order cast as persistent named people.
 *
 * armaa_starfallPeopleCMD ensure
 *
 * Registers them with Global.getSector().getImportantPeople(), which is what
 * BeginConversation looks up when it is given a bare id. After this runs once,
 * any node anywhere can do:
 *
 * BeginConversation armaa_sfo_roland ShowPersonVisual
 *
 * Idempotent - safe to call from the top of every scene, and safe on a save
 * where it has already run.
 *
 * Registration is the right call here rather than stashing a PersonAPI in a
 * memory key. BeginConversation does accept an object token, so
 * "BeginConversation $armaa_sfo_roland" would work too, but that needs the key
 * to be live in the right scope at every call site. ImportantPeople is global
 * and persists, so the id is usable from the hangar, the intake fleet and the
 * debrief without threading anything through.
 */
public class armaa_starfallPeopleCMD extends BaseCommandPlugin {

    public static final String ROLAND = "armaa_sfo_roland";
    public static final String VOIT = "armaa_sfo_voit";
    public static final String EHREN = "armaa_sfo_ehrenmark";
    public static final String ALARD = "armaa_sfo_alard";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
            List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.isEmpty()) {
            return false;
        }
        String command = params.get(0).getString(memoryMap);

        if ("ensure".equals(command)) {
            ensurePeople();
            return true;
        }
        return false;
    }

    public static void ensurePeople() {
        // Ser Roland - the instructor. Higher rank so the portrait card reads
        // as somebody with standing, not another recruit.
        make(ROLAND, "Roland", "Aumary", Gender.MALE,
                "graphics/portraits/portrait25.png",
                Ranks.KNIGHT_CAPTAIN, Ranks.POST_FLEET_COMMANDER, Personalities.STEADY, 6);

        make(VOIT, "Sella", "Voit", Gender.FEMALE,
                "graphics/armaa/portraits/armaa_sella.png",
                "armaa_aspirant", Ranks.POST_OFFICER, Personalities.CAUTIOUS, 3);

        make(EHREN, "Corvin", "Ehrenmark", Gender.MALE,
                "graphics/armaa/portraits/armaa_ehrenmark.png",
                "armaa_aspirant", Ranks.POST_OFFICER, Personalities.STEADY, 2);

        make(ALARD, "Vivi", "Alard", Gender.FEMALE,
                "graphics/armaa/portraits/armaa_vivi.png",
                "armaa_aspirant", Ranks.POST_OFFICER, Personalities.AGGRESSIVE, 2);
    }

    // NOTE on ranks: there is no cadet rank or trainee post in vanilla Ranks.
    // KNIGHT_CAPTAIN exists and suits Roland exactly. The recruits use
    // SPACE_CAPTAIN/POST_PATROL_COMMANDER as the closest fit - if you want
    // "Recruit" or "Squire" on the portrait card, add them to your faction's
    // ranks/posts in the .faction file and use those ids here instead.
    private static void make(String id, String first, String last, Gender gender,
            String portrait, String rank, String post,
            String personality, int level) {
        // already registered - leave the existing instance alone so anything
        // holding a reference to it stays valid
        if (Global.getSector().getImportantPeople().getData(id) != null) {
            return;
        }

        PersonAPI p = Global.getSettings().createPerson();
        p.setId(id);
        p.getName().setFirst(first);
        p.getName().setLast(last);
        p.getName().setGender(gender);
        p.setPortraitSprite(portrait);
        p.setFaction(Factions.PERSEAN);
        p.setRankId(rank);
        p.setPostId(post);
        p.setPersonality(personality);
        p.getStats().setLevel(level);

        Global.getSector().getImportantPeople().addPerson(p);
    }

    public static PersonAPI get(String id) {
        return Global.getSector().getImportantPeople().getPerson(id);
    }
}

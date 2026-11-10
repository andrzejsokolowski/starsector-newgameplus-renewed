package ngprenewed.feats;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import ngprenewed.Career;
import ngprenewed.SaveState;
import ngprenewed.Settings;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Feats: story flags that pay career points once per profile, ever (Andrzej, 2026-11-10). Read once at
 * game start from data/campaign/ngp_feats.csv, merged across mods so another mod can add lines. A feat
 * whose required mod is missing is left out. Which feats were paid is kept in the career file, outside
 * every save, so neither another save nor a reload pays the same feat again.
 */
public final class Feats {

    private static final Logger log = Global.getLogger(Feats.class);
    private static final String PATH = "data/campaign/ngp_feats.csv";

    private static final List<Feat> feats = new ArrayList<>();

    private Feats() {
    }

    public static void load() {
        feats.clear();
        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", PATH, Settings.MOD_ID);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = text(row, "id");
                if (id.isEmpty() || id.startsWith("#")) continue;
                String requiresMod = text(row, "requiresMod");
                if (!requiresMod.isEmpty() && !Global.getSettings().getModManager().isModEnabled(requiresMod)) continue;
                String flag = text(row, "flag");
                if (flag.isEmpty()) continue;
                int points;
                try {
                    points = Integer.parseInt(text(row, "points"));
                } catch (NumberFormatException e) {
                    log.warn("New Game Plus - Renewed: feat " + id + " has no whole number of points; skipped.");
                    continue;
                }
                String name = text(row, "name");
                String check = text(row, "check").toLowerCase();
                if (check.isEmpty()) check = "flag";
                if (!check.equals("flag") && !check.equals("equals") && !check.equals("skill")) {
                    log.warn("New Game Plus - Renewed: feat " + id + " has unknown check " + check + "; skipped.");
                    continue;
                }
                feats.add(new Feat(id, name.isEmpty() ? id : name, Math.max(0, points), text(row, "memory"), flag,
                        check, text(row, "value")));
            }
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not read " + PATH, e);
        }
        log.info("New Game Plus - Renewed: " + feats.size() + " feats.");
    }

    /** org.json returns the string "null" for a JSON null, so empty cells are checked by hand. */
    private static String text(JSONObject row, String key) {
        if (!row.has(key) || row.isNull(key)) return "";
        return row.optString(key, "").trim();
    }

    public static List<Feat> all() {
        return Collections.unmodifiableList(feats);
    }

    /** Pays every feat this game has achieved that the profile has never been paid for. */
    public static void check(SaveState state) {
        for (Feat feat : feats) {
            if (Career.featPaid(feat.id) || !achieved(feat)) continue;
            int paid = Career.payFeat(state.saveId(), feat.id, feat.points);
            log.info("New Game Plus - Renewed: feat " + feat.id + " paid " + paid + " career points.");
            if (Global.getSector().getCampaignUI() != null) {
                Global.getSector().getCampaignUI().addMessage(
                        "The Ledger records a feat, " + feat.name + ": +" + paid + " career points",
                        Misc.getPositiveHighlightColor());
            }
        }
    }

    /**
     * Whether this game has done the feat. getBoolean also reads the text "true", which is how
     * rules.csv stores a flag, so flags set by dialogs and by code both work.
     */
    static boolean achieved(Feat feat) {
        if ("skill".equals(feat.check)) {
            return Global.getSector().getPlayerStats().getSkillLevel(feat.flag) > 0f;
        }
        MemoryAPI memory = "player".equalsIgnoreCase(feat.memory)
                ? Global.getSector().getCharacterData().getMemoryWithoutUpdate()
                : Global.getSector().getMemoryWithoutUpdate();
        if (memory == null) return false;
        if ("equals".equals(feat.check)) {
            return memory.contains(feat.flag) && sameValue(memory.getString(feat.flag), feat.value);
        }
        if (feat.flag.contains("|")) {
            for (String key : feat.flag.split("\\|")) {
                if (!key.trim().isEmpty() && memory.getBoolean(key.trim())) return true;
            }
            return false;
        }
        for (String key : feat.flag.split("&")) {
            if (!key.trim().isEmpty() && !memory.getBoolean(key.trim())) return false;
        }
        return true;
    }

    /** Text compared without case; numbers compared as numbers, so 10 matches "10" and 10.0. */
    private static boolean sameValue(String actual, String wanted) {
        if (actual == null) return false;
        String a = actual.trim();
        String w = wanted.trim();
        if (a.equalsIgnoreCase(w)) return true;
        try {
            return Double.parseDouble(a) == Double.parseDouble(w);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

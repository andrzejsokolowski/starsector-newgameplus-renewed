package ngprenewed.feats;

import achievementsrenewed.api.AchievementSpec;
import achievementsrenewed.api.Achievements;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import ngprenewed.Career;
import ngprenewed.SaveState;
import ngprenewed.Settings;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Career points for achievements (Andrzej, 2026-11-10): every achievement in Achievements - Renewed pays
 * once per profile, except Rule Book - Renewed's challenges, which only unlock Rule Book perks.
 *
 * Points come from the feat that pays on the achievement (data/campaign/ngp_feats.csv), else from
 * data/campaign/ngp_achievement_points.csv, else {@link #DEFAULT_POINTS}. A price of 0 pays nothing.
 */
public final class Rewards {

    /** For achievements nobody priced, such as those of a mod added later. */
    public static final int DEFAULT_POINTS = 5;

    private static final Logger log = Global.getLogger(Rewards.class);
    private static final String PATH = "data/campaign/ngp_achievement_points.csv";
    private static final String RULE_BOOK = "rulebook_renewed";

    private static final Map<String, Integer> prices = new HashMap<>();

    private Rewards() {
    }

    public static void load() {
        prices.clear();
        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", PATH, Settings.MOD_ID);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!row.has("id") || row.isNull("id")) continue;
                String id = row.optString("id", "").trim();
                if (id.isEmpty() || id.startsWith("#") || !row.has("points") || row.isNull("points")) continue;
                try {
                    prices.put(id, Math.max(0, Integer.parseInt(row.optString("points", "").trim())));
                } catch (NumberFormatException e) {
                    log.warn("New Game Plus - Renewed: achievement " + id + " has no whole number of points; default used.");
                }
            }
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not read " + PATH, e);
        }
        log.info("New Game Plus - Renewed: " + prices.size() + " achievement prices.");
    }

    /** Rule Book's challenges unlock its perks and never pay career points. */
    static boolean isChallenge(AchievementSpec spec) {
        return spec.id.startsWith("rbr_challenge_")
                || (RULE_BOOK.equals(spec.modId) && "Challenges".equalsIgnoreCase(spec.category));
    }

    /** Career points this achievement pays, before the Rule Book multiplier. */
    public static int points(AchievementSpec spec) {
        if (spec == null || isChallenge(spec)) return 0;
        Feat feat = Feats.forAchievement(spec.id);
        if (feat != null) return feat.points;
        Integer price = prices.get(spec.id);
        return price != null ? price : DEFAULT_POINTS;
    }

    private static String legacyFeatId(AchievementSpec spec) {
        Feat feat = Feats.forAchievement(spec.id);
        return feat == null ? null : feat.id;
    }

    /** Pays one achievement if it pays anything and the profile has not been paid for it. */
    static int pay(SaveState state, AchievementSpec spec) {
        if (state == null || spec == null) return 0;
        int points = points(spec);
        if (points <= 0 || Career.achievementPaid(spec.id, legacyFeatId(spec))) return 0;
        int paid = Career.payAchievement(state.saveId(), spec.id, points);
        log.info("New Game Plus - Renewed: achievement " + spec.id + " paid " + paid + " career points.");
        return paid;
    }

    /** Pays one achievement the moment it is done, with a message. */
    static void payNow(SaveState state, AchievementSpec spec) {
        int paid = pay(state, spec);
        if (paid > 0) message("Your legacy grows: +" + paid + " career points");
    }

    /**
     * Pays every done achievement the profile has not been paid for: done in another save, done before
     * this mod was installed, or done while no save state existed yet. Many at once (the first load with
     * this mod) give one message for all.
     */
    public static void check(SaveState state) {
        if (state == null) return;
        Feats.markLegacyDone();
        int count = 0;
        int total = 0;
        for (AchievementSpec spec : Achievements.all()) {
            if (!Achievements.isDone(spec.id)) continue;
            int paid = pay(state, spec);
            if (paid > 0) {
                count++;
                total += paid;
            }
        }
        if (count == 1) message("Your legacy grows: +" + total + " career points");
        else if (count > 1) message("Your legacy grows by " + count + " deeds: +" + total + " career points");
    }

    private static void message(String text) {
        if (Global.getSector() != null && Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().addMessage(text, Misc.getPositiveHighlightColor());
        }
    }
}

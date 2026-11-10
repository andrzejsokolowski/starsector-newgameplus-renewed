package ngprenewed.feats;

import achievementsrenewed.api.AchievementSpec;
import achievementsrenewed.api.Achievements;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feats: story flags that pay career points once per profile, ever (Andrzej, 2026-11-10). Read once at
 * game start from data/campaign/ngp_feats.csv, merged across mods so another mod can add lines. A feat
 * whose required mod is missing is left out.
 *
 * Every feat is an achievement in Achievements - Renewed (Andrzej, 2026-11-10): that mod checks it,
 * records it across saves and shows it in its list. When another mod already has an achievement with
 * the same condition (the "shared" column, for example Rule Book - Renewed's rbr_atg), the feat pays on
 * that one and adds none of its own, so the list never shows the same thing twice. Which feats were paid
 * is still kept in the career file, outside every save, so the points are paid once even if the
 * achievement it pays on changes.
 */
public final class Feats {

    public static final String ACHIEVEMENT_PREFIX = "ngp_";

    private static final Logger log = Global.getLogger(Feats.class);
    private static final String PATH = "data/campaign/ngp_feats.csv";
    private static final String BASE_GAME = "Base game";

    private static final List<Feat> feats = new ArrayList<>();
    /** Achievement id the feat pays on, by feat id: its own, or the shared one. */
    private static final Map<String, String> achievementOf = new HashMap<>();
    private static final Map<String, Feat> byAchievement = new HashMap<>();

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
                feats.add(new Feat(id, name.isEmpty() ? id : name, Math.max(0, points), requiresMod,
                        text(row, "memory"), flag, check, text(row, "value"), text(row, "shared"),
                        text(row, "hint"), text(row, "howTo")));
            }
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not read " + PATH, e);
        }
        log.info("New Game Plus - Renewed: " + feats.size() + " feats.");
        registerAchievements();
    }

    /** Adds an achievement for every feat that has no shared one. Runs in onApplicationLoad. */
    private static void registerAchievements() {
        achievementOf.clear();
        byAchievement.clear();
        int own = 0;
        for (int i = 0; i < feats.size(); i++) {
            Feat feat = feats.get(i);
            String achievement;
            try {
                if (!feat.shared.isEmpty() && Achievements.get(feat.shared) != null) {
                    achievement = feat.shared;
                } else {
                    achievement = feat.ownAchievementId();
                    Achievements.register(AchievementSpec.builder(achievement, Settings.MOD_ID, feat.name)
                            .hint(feat.hint)
                            .howTo(feat.howTo)
                            .category(category(feat))
                            .reward("+" + feat.points + " career points")
                            .script(new FeatAchievement(feat))
                            .order(100 + i)
                            .build());
                    own++;
                }
            } catch (Throwable t) {
                log.error("New Game Plus - Renewed: could not add the achievement of feat " + feat.id, t);
                continue;
            }
            achievementOf.put(feat.id, achievement);
            byAchievement.put(achievement, feat);
        }
        log.info("New Game Plus - Renewed: " + own + " feat achievements added, "
                + (achievementOf.size() - own) + " shared with other mods.");
    }

    private static String category(Feat feat) {
        if (feat.requiresMod.isEmpty()) return BASE_GAME;
        ModSpecAPI mod = Global.getSettings().getModManager().getModSpec(feat.requiresMod);
        return mod != null && mod.getName() != null ? mod.getName() : feat.requiresMod;
    }

    /** org.json returns the string "null" for a JSON null, so empty cells are checked by hand. */
    private static String text(JSONObject row, String key) {
        if (!row.has(key) || row.isNull(key)) return "";
        return row.optString(key, "").trim();
    }

    public static List<Feat> all() {
        return Collections.unmodifiableList(feats);
    }

    /** The feat that pays on this achievement, or null. */
    static Feat forAchievement(String achievementId) {
        return byAchievement.get(achievementId);
    }

    /**
     * Pays every feat whose achievement is done and that the profile has never been paid for: done in
     * another save, done before this mod was installed, or done while no save state existed yet. A feat
     * paid before it was an achievement marks its own achievement done instead (which pays nothing).
     */
    public static void check(SaveState state) {
        for (Feat feat : feats) {
            String achievement = achievementOf.get(feat.id);
            if (achievement == null) continue;
            boolean paid = Career.featPaid(feat.id);
            if (Achievements.isDone(achievement)) {
                if (!paid) pay(state, feat);
            } else if (paid && achievement.equals(feat.ownAchievementId())) {
                Achievements.complete(achievement);
            }
        }
    }

    /** Pays one feat if the profile has not been paid for it yet. */
    static void pay(SaveState state, Feat feat) {
        if (state == null || Career.featPaid(feat.id)) return;
        int paid = Career.payFeat(state.saveId(), feat.id, feat.points);
        log.info("New Game Plus - Renewed: feat " + feat.id + " paid " + paid + " career points.");
        if (Global.getSector().getCampaignUI() != null) {
            Global.getSector().getCampaignUI().addMessage(
                    "The Ledger records your deed: +" + paid + " career points",
                    Misc.getPositiveHighlightColor());
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

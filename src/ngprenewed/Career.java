package ngprenewed;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * The career total: points earned in every save, kept in saves/common so it outlives any save.
 *
 * <p>File layout ({@code saves/common/ngp_renewed/career.json}):
 * {@code {"version":1, "earned":N, "achievements":{"<achievementId>":true}, "saves":{"<saveId>":{"levelsPaid":L, "earned":E, "carry":C}}}}.
 * Achievements are paid once per profile, so they sit outside the per-save records. Before 0.7.0 paid
 * feats were kept as {@code "feats":{"<featId>":true}}; that map is still read.
 * {@code earned} is the lifetime total. Each save keeps the highest level it has been paid for, so a
 * level is paid once per save even after reloading an older save file.</p>
 *
 * <p>Points are never spent from here. A game's store budget is a snapshot of this total taken when
 * the game starts (see {@link SaveState}).</p>
 */
public final class Career {

    private static final Logger log = Global.getLogger(Career.class);
    private static final String FILE = "ngp_renewed/career.json";

    /** Sector memory key written by Rule Book - Renewed: multiplier on points earned. */
    public static final String RULE_BOOK_MULT_KEY = "$rbr_ngpMultiplier";

    private static JSONObject data;

    private Career() {
    }

    private static JSONObject data() {
        if (data == null) load();
        return data;
    }

    private static void load() {
        data = new JSONObject();
        try {
            if (Global.getSettings().fileExistsInCommon(FILE)) {
                String text = Global.getSettings().readTextFileFromCommon(FILE);
                if (text != null && !text.trim().isEmpty()) data = new JSONObject(text);
            }
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not read " + FILE + ", starting from zero.", e);
            data = new JSONObject();
        }
    }

    /** Forget the cached copy, so the next read sees the file again (another save may have written it). */
    public static void reload() {
        data = null;
    }

    private static void save() {
        try {
            data().put("version", 1);
            Global.getSettings().writeTextFileToCommon(FILE, data().toString(2));
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not write " + FILE + "; points earned now are kept for this session only.", e);
        }
    }

    /** Lifetime points earned, without the LunaLib adjustment. */
    public static int earned() {
        return data().optInt("earned", 0);
    }

    /** Lifetime points as the player sees them: earned plus the LunaLib adjustment. */
    public static int total() {
        return earned() + Settings.bonusPoints();
    }

    private static JSONObject record(String saveId) {
        try {
            JSONObject saves = data().optJSONObject("saves");
            if (saves == null) {
                saves = new JSONObject();
                data().put("saves", saves);
            }
            JSONObject rec = saves.optJSONObject(saveId);
            if (rec == null) {
                rec = new JSONObject();
                saves.put(saveId, rec);
            }
            return rec;
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: career file is damaged; using a blank record.", e);
            return new JSONObject();
        }
    }

    /** Highest level this save has been paid for. A new character starts at level 1, which pays nothing. */
    public static int levelsPaid(String saveId) {
        return record(saveId).optInt("levelsPaid", 1);
    }

    public static void setLevelsPaid(String saveId, int level) {
        try {
            JSONObject rec = record(saveId);
            if (level > rec.optInt("levelsPaid", 1)) {
                rec.put("levelsPaid", level);
                save();
            }
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not record paid levels.", e);
        }
    }

    /** Points this save has added to the career. */
    public static int earnedBy(String saveId) {
        return record(saveId).optInt("earned", 0);
    }

    /**
     * True once the profile has been paid for this achievement, in any save. Feats paid before 0.7.0
     * were recorded by feat id; {@code legacyFeatId} (may be null) is checked too.
     */
    public static boolean achievementPaid(String achievementId, String legacyFeatId) {
        JSONObject paid = data().optJSONObject("achievements");
        if (paid != null && paid.optBoolean(achievementId, false)) return true;
        if (legacyFeatId == null) return false;
        JSONObject feats = data().optJSONObject("feats");
        return feats != null && feats.optBoolean(legacyFeatId, false);
    }

    /**
     * Pays an achievement once for the whole profile. It is marked paid in the same file write that adds
     * the points, so the two never get out of step.
     *
     * @return whole points added
     */
    public static int payAchievement(String saveId, String achievementId, float points) {
        try {
            JSONObject paid = data().optJSONObject("achievements");
            if (paid == null) {
                paid = new JSONObject();
                data().put("achievements", paid);
            }
            paid.put(achievementId, true);
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not record achievement " + achievementId, e);
            return 0;
        }
        return pay(saveId, points);
    }

    /** Rule Book - Renewed's multiplier, or 1 when it is not installed or has no rules active. */
    public static float multiplier() {
        try {
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            if (!mem.contains(RULE_BOOK_MULT_KEY)) return 1f;
            Object value = mem.get(RULE_BOOK_MULT_KEY);
            if (value instanceof Number) return Math.max(0f, ((Number) value).floatValue());
        } catch (Exception ignored) {
        }
        return 1f;
    }

    /**
     * Adds points to the career for this save. The Rule Book multiplier applies, and fractions carry
     * over to the next payment so a 1.3x multiplier is honoured exactly over time.
     *
     * @return whole points added
     */
    public static int pay(String saveId, float basePoints) {
        try {
            JSONObject rec = record(saveId);
            double amount = basePoints * multiplier() + rec.optDouble("carry", 0.0);
            int whole = (int) Math.floor(amount + 1e-6);
            rec.put("carry", Math.max(0.0, amount - whole));
            rec.put("earned", rec.optInt("earned", 0) + whole);
            data().put("earned", earned() + whole);
            save();
            return whole;
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not pay career points.", e);
            return 0;
        }
    }
}

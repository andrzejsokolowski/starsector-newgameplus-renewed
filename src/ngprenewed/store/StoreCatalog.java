package ngprenewed.store;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import ngprenewed.Settings;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Every store item, read once at game start from data/campaign/ngp_store_items.csv (merged across
 * mods, so another mod can add lines). Items that need a missing mod, or name a commodity or special
 * item that does not exist, are left out. The Armoury tab is built from the loaded weapons instead:
 * one pack per design type, so modded design types appear by themselves.
 */
public final class StoreCatalog {

    private static final Logger log = Global.getLogger(StoreCatalog.class);
    private static final String PATH = "data/campaign/ngp_store_items.csv";
    private static final String DEFAULT_ICON = "graphics/ngp/ngp_icon.png";

    private static final List<StoreItem> items = new ArrayList<>();
    private static final List<String> tabs = new ArrayList<>();
    private static final Map<String, List<String>> packWeapons = new HashMap<>();

    public static final String ARMOURY = "Armoury";
    /** Copies of every weapon in a pack. */
    public static final int PACK_COPIES = 2;
    /** A design type needs this many weapons to get a pack. */
    private static final int PACK_MIN_WEAPONS = 5;
    /** Weapon value per career point, matching the credit packs (1000000 credits for 25 points). */
    private static final float CREDITS_PER_POINT = 40000f;
    /** Weapons the game keeps out of shops and loot. */
    private static final Set<String> EXCLUDED_TAGS = new HashSet<>(Arrays.asList(
            "restricted", "no_drop", "no_sell", "no_dealer", "omega", "threat", "dweller", "monster", "fragment"));

    private StoreCatalog() {
    }

    public static void load() {
        items.clear();
        tabs.clear();
        packWeapons.clear();
        Set<String> tabOrder = new LinkedHashSet<>();
        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", PATH, Settings.MOD_ID);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = text(row, "id");
                if (id.isEmpty() || id.startsWith("#")) continue;
                try {
                    StoreItem item = parse(row, id);
                    if (item == null) continue;
                    items.add(item);
                    tabOrder.add(item.tab);
                } catch (Exception e) {
                    log.warn("New Game Plus - Renewed: skipping store item " + id + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not read " + PATH, e);
        }
        try {
            addWeaponPacks(tabOrder);
        } catch (Exception e) {
            log.error("New Game Plus - Renewed: could not build the weapon packs.", e);
        }
        tabs.addAll(tabOrder);
        log.info("New Game Plus - Renewed: " + items.size() + " store items in " + tabs.size() + " tabs.");
    }

    /** org.json returns the string "null" for a JSON null, so empty cells are checked by hand. */
    private static String text(JSONObject row, String key) {
        if (!row.has(key) || row.isNull(key)) return "";
        return row.optString(key, "").trim();
    }

    private static int number(JSONObject row, String key, int fallback) {
        String value = text(row, key);
        if (value.isEmpty()) return fallback;
        return Integer.parseInt(value);
    }

    private static StoreItem parse(JSONObject row, String id) {
        String requiresMod = text(row, "requiresMod");
        if (!requiresMod.isEmpty() && !Global.getSettings().getModManager().isModEnabled(requiresMod)) return null;

        String kind = text(row, "kind");
        String target = text(row, "target");
        String icon = text(row, "icon");

        if ("commodity".equals(kind)) {
            CommoditySpecAPI spec = commoditySpec(target);
            if (spec == null) {
                log.warn("New Game Plus - Renewed: store item " + id + " names unknown commodity " + target);
                return null;
            }
            if (icon.isEmpty()) icon = spec.getIconName();
        } else if ("special_item".equals(kind)) {
            SpecialItemSpecAPI spec = specialSpec(target);
            if (spec == null) {
                log.warn("New Game Plus - Renewed: store item " + id + " names unknown special item " + target);
                return null;
            }
            if (icon.isEmpty()) icon = spec.getIconName();
        } else if ("perk".equals(kind)) {
            SkillSpecAPI spec = skillSpec(target);
            if (spec == null) {
                log.warn("New Game Plus - Renewed: store item " + id + " names unknown perk " + target);
                return null;
            }
            if (icon.isEmpty()) icon = spec.getSpriteName();
        }
        if (icon.isEmpty()) icon = DEFAULT_ICON;
        // Art that no game file names is never loaded by the game itself.
        try {
            Global.getSettings().loadTexture(icon);
        } catch (Exception e) {
            log.warn("New Game Plus - Renewed: icon " + icon + " for " + id + " did not load; using the default.");
            icon = DEFAULT_ICON;
        }

        String name = text(row, "name");
        return new StoreItem(id, text(row, "tab"), name.isEmpty() ? id : name, text(row, "description"),
                Math.max(0, number(row, "cost", 0)), number(row, "stock", -1), kind, target,
                Math.max(1, number(row, "amount", 1)), icon);
    }

    /**
     * One pack per design type with at least {@link #PACK_MIN_WEAPONS} weapons, leaving out weapons the
     * game never sells or drops, built-in and system weapons. Packs are sorted by design type.
     */
    private static void addWeaponPacks(Set<String> tabOrder) {
        Map<String, List<WeaponSpecAPI>> byDesign = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
            if (!packable(spec)) continue;
            byDesign.computeIfAbsent(design(spec), k -> new ArrayList<>()).add(spec);
        }
        for (Map.Entry<String, List<WeaponSpecAPI>> entry : byDesign.entrySet()) {
            List<WeaponSpecAPI> specs = entry.getValue();
            if (specs.size() < PACK_MIN_WEAPONS) continue;
            String design = entry.getKey();
            List<String> ids = new ArrayList<>();
            float value = 0f;
            for (WeaponSpecAPI spec : specs) {
                ids.add(spec.getWeaponId());
                value += Math.max(0f, spec.getBaseValue()) * PACK_COPIES;
            }
            // Weapons are cheap in credits and rare in shops, so a big pack also costs by its size.
            int cost = Math.max(2, Math.max(Math.round(value / CREDITS_PER_POINT), Math.round(specs.size() / 4f)));
            String id = "weapons_" + design.toLowerCase().replaceAll("[^a-z0-9]+", "_");
            packWeapons.put(design, ids);
            items.add(new StoreItem(id, ARMOURY, design + " weapons (" + specs.size() + ")",
                    "Two crates of every " + design + " design the Ledger could find.", cost, -1,
                    "weapon_pack", design, PACK_COPIES, packIcon(design)));
            tabOrder.add(ARMOURY);
        }
    }

    /**
     * The weapon's design type. The base game leaves it empty on its own weapons, so those are grouped
     * by the blueprint package they belong to, and the rest go under Common.
     */
    private static String design(WeaponSpecAPI spec) {
        String design = spec.getManufacturer();
        if (design != null && !design.trim().isEmpty()) return design.trim();
        if (spec.hasTag("hightech_bp")) return "High Tech";
        if (spec.hasTag("midline_bp")) return "Midline";
        if (spec.hasTag("lowtech_bp")) return "Low Tech";
        if (spec.hasTag("missile_bp")) return "Missile";
        return "Common";
    }

    private static boolean packable(WeaponSpecAPI spec) {
        if ("Unknown".equalsIgnoreCase(design(spec))) return false;
        WeaponType type = spec.getType();
        if (type != WeaponType.BALLISTIC && type != WeaponType.ENERGY && type != WeaponType.MISSILE
                && type != WeaponType.HYBRID && type != WeaponType.COMPOSITE && type != WeaponType.SYNERGY
                && type != WeaponType.UNIVERSAL) return false;
        if (spec.getAIHints().contains(AIHints.SYSTEM)) return false;
        for (String tag : spec.getTags()) {
            if (EXCLUDED_TAGS.contains(tag)) return false;
        }
        return spec.getWeaponName() != null && !spec.getWeaponName().trim().isEmpty();
    }

    private static String packIcon(String design) {
        switch (design.toLowerCase()) {
            case "high tech":
                return "graphics/icons/cargo/package_hightech.png";
            case "midline":
                return "graphics/icons/cargo/package_midline.png";
            case "low tech":
                return "graphics/icons/cargo/package_lowtech.png";
            case "missile":
                return "graphics/icons/cargo/bp_missiles.png";
            default:
                return "graphics/icons/cargo/starship_weapons.png";
        }
    }

    /** The weapons in one design type's pack. */
    public static List<String> packWeapons(String design) {
        List<String> ids = packWeapons.get(design);
        return ids == null ? Collections.emptyList() : Collections.unmodifiableList(ids);
    }

    private static SkillSpecAPI skillSpec(String id) {
        try {
            return Global.getSettings().getSkillSpec(id);
        } catch (Exception e) {
            return null;
        }
    }

    private static CommoditySpecAPI commoditySpec(String id) {
        try {
            return Global.getSettings().getCommoditySpec(id);
        } catch (Exception e) {
            return null;
        }
    }

    private static SpecialItemSpecAPI specialSpec(String id) {
        try {
            return Global.getSettings().getSpecialItemSpec(id);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> tabs() {
        return Collections.unmodifiableList(tabs);
    }

    public static List<StoreItem> itemsIn(String tab) {
        List<StoreItem> result = new ArrayList<>();
        for (StoreItem item : items) {
            if (item.tab.equals(tab)) result.add(item);
        }
        return result;
    }

    /** Price after the LunaLib multiplier. */
    public static int cost(StoreItem item) {
        return (int) Math.round(item.baseCost * Settings.costMult());
    }
}

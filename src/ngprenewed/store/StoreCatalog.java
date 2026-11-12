package ngprenewed.store;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
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
 * item that does not exist, are left out. The Armory tab is built from the loaded factions instead:
 * one weapon pack per faction, so modded factions appear by themselves.
 */
public final class StoreCatalog {

    private static final Logger log = Global.getLogger(StoreCatalog.class);
    private static final String PATH = "data/campaign/ngp_store_items.csv";
    private static final String DEFAULT_ICON = "graphics/ngp/ngp_icon.png";

    private static final List<StoreItem> items = new ArrayList<>();
    private static final List<String> tabs = new ArrayList<>();
    private static final Map<String, List<String>> packWeapons = new HashMap<>();

    public static final String ARMORY = "Armory";
    public static final String RARE = "Rare";
    /** Store item ids of the colony items made in code start with this. */
    private static final String COLONY_PREFIX = "colony_";
    /** Copies of every weapon in a pack. */
    public static final int PACK_COPIES = 2;
    /** A faction needs this many weapons to get a pack. */
    private static final int PACK_MIN_WEAPONS = 5;
    /** Price of one faction's weapon pack (Andrzej, 2026-11-09). */
    private static final int PACK_COST = 20;
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

    /**
     * Every colony item the game knows goes to the Rare tab, one per game each (Andrzej, 2026-11-11: "all
     * colony items (auroran ones, tenebrian ones, normal ones)"). A colony item is a special item with an
     * entry in ItemEffectsRepo.ITEM_EFFECTS (where vanilla, AoTD's Tenebrian items and UAF's Auroran items
     * register what they do) or the colony_item tag. Runs on every game load, because mods fill that map
     * in their own plugins. Items a CSV row already sells are skipped. Price from the item's base value.
     */
    public static void addColonyItems() {
        Set<String> sold = new HashSet<>();
        for (java.util.Iterator<StoreItem> it = items.iterator(); it.hasNext(); ) {
            StoreItem item = it.next();
            if (item.id.startsWith(COLONY_PREFIX)) it.remove();
            else if ("special_item".equals(item.kind)) sold.add(item.target);
        }
        Set<String> effects;
        try {
            effects = new HashSet<>(ItemEffectsRepo.ITEM_EFFECTS.keySet());
        } catch (Throwable t) {
            effects = new HashSet<>();
        }
        List<StoreItem> found = new ArrayList<>();
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            if (spec == null || sold.contains(spec.getId())) continue;
            if (!effects.contains(spec.getId()) && !spec.hasTag("colony_item")) continue;
            if (scripted(spec)) continue;
            String icon = spec.getIconName();
            try {
                Global.getSettings().loadTexture(icon);
            } catch (Exception e) {
                icon = DEFAULT_ICON;
            }
            found.add(new StoreItem(COLONY_PREFIX + spec.getId(), RARE, spec.getName(), installsIn(spec),
                    colonyItemCost(spec), 1, "special_item", spec.getId(), 1, icon));
        }
        found.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        items.addAll(found);
        if (!tabs.contains(RARE) && !found.isEmpty()) tabs.add(0, RARE);
        log.info("New Game Plus - Renewed: " + found.size() + " colony items in the Rare tab.");
    }

    /** 10 points plus 1 per 25000 credits of base value, 10 to 60: a Pristine Nanoforge is 20, a Tenebrian one 50. */
    static int colonyItemCost(SpecialItemSpecAPI spec) {
        int cost = Math.round(10f + spec.getBasePrice() / 25000f);
        return Math.max(10, Math.min(60, cost));
    }

    /**
     * Items the game hands out through a script, one per campaign: buying one breaks that script (Andrzej,
     * 2026-11-12: a Wormhole Anchor "will crash the game"). Mission items such as the Planetkiller too.
     */
    private static final Set<String> SCRIPTED = new HashSet<>(java.util.Arrays.asList("wormhole_anchor"));

    static boolean scripted(SpecialItemSpecAPI spec) {
        return SCRIPTED.contains(spec.getId()) || spec.hasTag("mission_item");
    }

    /** Industries named in a colony item's line; more than this become "and N more". */
    private static final int NAMES_SHOWN = 3;

    /** "Colony item: Heavy Industry, Orbital Works." from the item's industry list, when it has one. */
    private static String installsIn(SpecialItemSpecAPI spec) {
        String params = spec.getParams();
        if (params == null || params.trim().isEmpty()) return "Colony item.";
        List<String> names = new ArrayList<>();
        for (String id : params.split(",")) {
            String industryId = id.trim();
            if (industryId.isEmpty()) continue;
            try {
                IndustrySpecAPI industry = Global.getSettings().getIndustrySpec(industryId);
                if (industry != null && !names.contains(industry.getName())) names.add(industry.getName());
            } catch (Exception e) {
                // not an industry id
            }
        }
        if (names.isEmpty()) return "Colony item.";
        if (names.size() <= NAMES_SHOWN) return "Colony item: " + String.join(", ", names) + ".";
        // 0.9.0: some mods list hundreds of buildings for one item, and the whole list ran over the store.
        return "Colony item: " + String.join(", ", names.subList(0, NAMES_SHOWN)) + " and "
                + (names.size() - NAMES_SHOWN) + " more.";
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
     * One pack per faction that knows at least {@link #PACK_MIN_WEAPONS} weapons, built from every
     * faction the game and its mods load, so modded factions get packs by themselves. Weapons the game
     * never sells or drops, built-in and system weapons are left out. Sorted by faction name.
     */
    private static void addWeaponPacks(Set<String> tabOrder) {
        Map<String, FactionSpecAPI> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FactionSpecAPI faction : Global.getSettings().getAllFactionSpecs()) {
            if (faction == null || "player".equals(faction.getId()) || !faction.isShowInIntelTab()) continue;
            byName.put(faction.getDisplayName() + " " + faction.getId(), faction);
        }
        for (FactionSpecAPI faction : byName.values()) {
            List<String> ids = packableWeapons(faction.getKnownWeapons());
            if (ids.size() < PACK_MIN_WEAPONS) continue;
            packWeapons.put(faction.getId(), ids);
            items.add(new StoreItem("weapons_" + faction.getId(), ARMORY,
                    faction.getDisplayName() + " weapons (" + ids.size() + ")",
                    "Two of every weapon " + faction.getDisplayName() + " uses.",
                    PACK_COST, -1, "weapon_pack", faction.getId(), PACK_COPIES, packIcon(faction)));
            tabOrder.add(ARMORY);
        }
    }

    private static List<String> packableWeapons(Set<String> weaponIds) {
        List<String> result = new ArrayList<>();
        if (weaponIds == null) return result;
        for (String id : weaponIds) {
            WeaponSpecAPI spec = weaponSpec(id);
            if (spec != null && packable(spec)) result.add(id);
        }
        Collections.sort(result);
        return result;
    }

    private static boolean packable(WeaponSpecAPI spec) {
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

    /** The faction's crest, or a weapons crate if it has none or it does not load. */
    private static String packIcon(FactionSpecAPI faction) {
        String crest = faction.getCrest();
        if (crest != null && !crest.isEmpty()) {
            try {
                Global.getSettings().loadTexture(crest);
                return crest;
            } catch (Exception e) {
                // fall through to the crate
            }
        }
        return "graphics/icons/cargo/starship_weapons.png";
    }

    private static WeaponSpecAPI weaponSpec(String id) {
        try {
            return Global.getSettings().getWeaponSpec(id);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The weapons in one faction's pack. In a campaign this is what the faction knows right now, so
     * blueprints it learned since the game started are included; otherwise the list from game start.
     */
    public static List<String> packWeapons(String factionId) {
        if (Global.getSector() != null) {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            if (faction != null) {
                List<String> live = packableWeapons(faction.getKnownWeapons());
                if (!live.isEmpty()) return live;
            }
        }
        List<String> ids = packWeapons.get(factionId);
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

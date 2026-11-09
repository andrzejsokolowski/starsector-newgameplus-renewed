package ngprenewed.store;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import ngprenewed.Settings;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every store item, read once at game start from data/campaign/ngp_store_items.csv (merged across
 * mods, so another mod can add lines). Items that need a missing mod, or name a commodity or special
 * item that does not exist, are left out.
 */
public final class StoreCatalog {

    private static final Logger log = Global.getLogger(StoreCatalog.class);
    private static final String PATH = "data/campaign/ngp_store_items.csv";
    private static final String DEFAULT_ICON = "graphics/ngp/ngp_icon.png";

    private static final List<StoreItem> items = new ArrayList<>();
    private static final List<String> tabs = new ArrayList<>();

    private StoreCatalog() {
    }

    public static void load() {
        items.clear();
        tabs.clear();
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

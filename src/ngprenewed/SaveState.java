package ngprenewed;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;

import java.util.HashMap;
import java.util.Map;

/**
 * What one save remembers: its id, the store budget it started with, what it bought, the
 * delivery bay and the Ledger station.
 *
 * <p>Kept as a plain map of numbers, strings and game objects, so the save format does not depend on
 * this class's fields and can grow without breaking older saves.</p>
 */
public final class SaveState {

    private static final String KEY = "ngp_renewed_state";

    private final Map<String, Object> map;

    private SaveState(Map<String, Object> map) {
        this.map = map;
    }

    /** This save's state, made on first use. Null outside a campaign. */
    @SuppressWarnings("unchecked")
    public static SaveState get() {
        if (Global.getSector() == null) return null;
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object existing = data.get(KEY);
        if (existing instanceof Map) return new SaveState((Map<String, Object>) existing);
        return null;
    }

    /** Creates the state for a save that has none: a fresh game, or a save that predates the mod. */
    public static SaveState create() {
        Map<String, Object> map = new HashMap<>();
        map.put("saveId", Misc.genUID());
        // Only points earned in other saves count: this save has earned nothing yet.
        map.put("budgetBase", Career.earned());
        map.put("spent", 0);
        map.put("bought", new HashMap<String, Integer>());
        map.put("bay", Global.getFactory().createCargo(true));
        Global.getSector().getPersistentData().put(KEY, map);
        return new SaveState(map);
    }

    public String saveId() {
        return (String) map.get("saveId");
    }

    private int intValue(String key) {
        Object value = map.get(key);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /** Points the store can still spend in this game. */
    public int budget() {
        return intValue("budgetBase") + Settings.bonusPoints() - intValue("spent");
    }

    /** The budget this game started with, LunaLib adjustment included. */
    public int startingBudget() {
        return intValue("budgetBase") + Settings.bonusPoints();
    }

    public int spent() {
        return intValue("spent");
    }

    public void spend(int points) {
        map.put("spent", intValue("spent") + points);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> bought() {
        Object value = map.get("bought");
        if (value instanceof Map) return (Map<String, Integer>) value;
        Map<String, Integer> fresh = new HashMap<>();
        map.put("bought", fresh);
        return fresh;
    }

    public int boughtCount(String itemId) {
        Integer count = bought().get(itemId);
        return count == null ? 0 : count;
    }

    public void recordPurchase(String itemId) {
        bought().put(itemId, boughtCount(itemId) + 1);
    }

    /** Goods bought and not yet collected. The player takes them through a loot screen. */
    public CargoAPI bay() {
        Object value = map.get("bay");
        if (value instanceof CargoAPI) return (CargoAPI) value;
        CargoAPI fresh = Global.getFactory().createCargo(true);
        map.put("bay", fresh);
        return fresh;
    }

    public SectorEntityToken ledger() {
        Object value = map.get("ledger");
        return value instanceof SectorEntityToken ? (SectorEntityToken) value : null;
    }

    public void setLedger(SectorEntityToken ledger) {
        map.put("ledger", ledger);
    }
}

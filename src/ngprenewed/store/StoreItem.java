package ngprenewed.store;

/** One line of data/campaign/ngp_store_items.csv. */
public final class StoreItem {

    public final String id;
    public final String tab;
    public final String name;
    public final String description;
    /** Price before the LunaLib multiplier. */
    public final int baseCost;
    /** How many one game can buy; -1 means no limit. */
    public final int stock;
    public final String kind;
    public final String target;
    public final int amount;
    public final String icon;

    public StoreItem(String id, String tab, String name, String description, int baseCost, int stock,
                     String kind, String target, int amount, String icon) {
        this.id = id;
        this.tab = tab;
        this.name = name;
        this.description = description;
        this.baseCost = baseCost;
        this.stock = stock;
        this.kind = kind;
        this.target = target;
        this.amount = amount;
        this.icon = icon;
    }

    public boolean limited() {
        return stock >= 0;
    }
}

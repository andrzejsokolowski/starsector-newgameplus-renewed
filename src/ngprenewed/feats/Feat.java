package ngprenewed.feats;

/** One line of data/campaign/ngp_feats.csv. */
public final class Feat {

    public final String id;
    public final String name;
    public final int points;
    /** "sector" for sector memory, "player" for the player character's memory. */
    public final String memory;
    /**
     * For check "flag": one key, several joined by "&amp;" (all must be true) or by "|" (any one).
     * For check "equals": the key whose value is compared. For check "skill": the skill id.
     */
    public final String flag;
    /** "flag" (the default), "equals" or "skill". */
    public final String check;
    /** For check "equals": the value the key must hold. */
    public final String value;

    public Feat(String id, String name, int points, String memory, String flag, String check, String value) {
        this.id = id;
        this.name = name;
        this.points = points;
        this.memory = memory;
        this.flag = flag;
        this.check = check;
        this.value = value;
    }
}

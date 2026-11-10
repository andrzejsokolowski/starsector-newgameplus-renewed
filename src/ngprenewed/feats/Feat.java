package ngprenewed.feats;

/** One line of data/campaign/ngp_feats.csv. */
public final class Feat {

    public final String id;
    public final String name;
    public final int points;
    /** "sector" for sector memory, "player" for the player character's memory. */
    public final String memory;
    public final String flag;

    public Feat(String id, String name, int points, String memory, String flag) {
        this.id = id;
        this.name = name;
        this.points = points;
        this.memory = memory;
        this.flag = flag;
    }
}

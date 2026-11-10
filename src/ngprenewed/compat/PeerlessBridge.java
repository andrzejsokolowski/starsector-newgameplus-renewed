package ngprenewed.compat;

import peerless.api.PeerlessSkills;

/**
 * Talks to Peerless Skills. Only loaded when that mod is enabled (the store item requires it), so the
 * game never looks for its classes otherwise.
 */
public final class PeerlessBridge {

    public static final String MOD_ID = "peerless_skills";

    private PeerlessBridge() {
    }

    /**
     * Grants one Peerless point under a key that is unique to this save and purchase.
     *
     * @return false if Peerless Skills had already paid this key
     */
    public static boolean awardPoint(String key) {
        return PeerlessSkills.awardOnce(key);
    }
}

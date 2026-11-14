package ngprenewed.compat;

import peerlessrenewed.PeerlessData;

/**
 * Talks to Peerless Skills - Renewed (0.9.2). Only loaded when that mod is enabled, so the game never looks
 * for its classes otherwise.
 */
public final class PeerlessRenewedBridge {

    public static final String MOD_ID = "peerless_skills_renewed";

    private PeerlessRenewedBridge() {
    }

    /** Adds one unspent Peerless point in this campaign. */
    public static void addPoint() {
        if (!PeerlessData.hasCampaign()) throw new IllegalStateException("Peerless Skills - Renewed has no campaign data");
        PeerlessData.addPoints(1);
    }
}

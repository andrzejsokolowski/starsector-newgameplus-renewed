package peerless.api;

/**
 * Compile-only stand-in for the original Peerless Skills mod's public API (mod id peerless_skills), whose
 * jar is no longer installed here. Never packaged: at run time the real class comes from that mod.
 * Keep the signature exactly as that mod has it.
 */
public final class PeerlessSkills {

    private PeerlessSkills() {
    }

    public static boolean awardOnce(String key) {
        throw new UnsupportedOperationException("compile-only stub");
    }
}

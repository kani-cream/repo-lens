package samples.unused;

/**
 * Triggers RL-U001 Unused Candidate: a public type and method nothing references.
 * The cycle samples (Alpha/Beta) reference each other, so they must NOT appear.
 */
public class OrphanSample {
    public void neverCalled() {
    }
}

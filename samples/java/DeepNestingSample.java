package samples.java;

/** Triggers RL-M003 Deep Nesting only: control flow 6 levels deep against the default of 5. */
public class DeepNestingSample {

    void deep(int[][] matrix) {
        if (matrix != null) {                        // 1
            for (int[] row : matrix) {               // 2
                if (row != null) {                   // 3
                    for (int cell : row) {           // 4
                        if (cell > 0) {              // 5
                            try {                    // 6
                                System.out.println(cell);
                            } catch (RuntimeException e) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }
    }

    /** Depth 5, exactly at the threshold; must NOT be reported. */
    void atLimit(int[][] matrix) {
        if (matrix != null) {                        // 1
            for (int[] row : matrix) {               // 2
                if (row != null) {                   // 3
                    for (int cell : row) {           // 4
                        if (cell > 0) {              // 5
                            System.out.println(cell);
                        }
                    }
                }
            }
        }
    }
}

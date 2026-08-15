package samples.java;

/** Triggers RL-M002 Too Many Parameters only: 9 parameters against the default of 7. */
public class WideParametersSample {

    void configure(
            String host,
            int port,
            String user,
            String password,
            boolean useTls,
            int timeoutMillis,
            int retryCount,
            String proxyHost,
            int proxyPort) {
        // intentionally empty
    }

    /** Within the default threshold; must NOT be reported. */
    void narrow(String host, int port, String user) {
        // intentionally empty
    }
}

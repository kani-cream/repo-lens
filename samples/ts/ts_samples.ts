// TODO TS marker: picked up by RL-T001 like in any other language.

/** Triggers RL-M002 Too Many Parameters: 9 parameters against the default of 7. */
export function configure(
    host: string,
    port: number,
    user: string,
    password: string,
    useTls: boolean,
    timeoutMillis: number,
    retryCount: number,
    proxyHost: string,
    proxyPort: number,
): void {
    // intentionally empty
}

/** Within the default threshold; must NOT be reported. */
export function narrow(host: string, port: number): void {
    // intentionally empty
}

/** Triggers RL-M003 Deep Nesting: 6 levels against the default of 5. */
export function deep(groups: number[][]): void {
    if (groups) {                         // 1
        groups.forEach(group => {         // 2
            for (const v of group) {      // 3
                if (v > 0) {              // 4
                    try {                 // 5
                        setTimeout(() => { // 6
                            console.log(v);
                        }, 0);
                    } catch (e) {
                        // ignore
                    }
                }
            }
        });
    }
}

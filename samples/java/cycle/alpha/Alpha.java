package samples.cycle.alpha;

import samples.cycle.beta.Beta;

/** Half of an intentional package cycle: alpha depends on beta. */
public class Alpha {
    Beta partner;
}

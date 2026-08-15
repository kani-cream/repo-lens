package samples

// TODO Go marker: picked up by RL-T001 like in any other language.

// Triggers RL-M002 Too Many Parameters: 9 parameters against the default of 7.
func Configure(host string, port int, user string, password string,
	useTLS bool, timeoutMillis int, retryCount int, proxyHost string, proxyPort int) {
	// intentionally empty
}

// Within the default threshold; must NOT be reported.
func Narrow(host string, port int) {
	// intentionally empty
}

// Triggers RL-M003 Deep Nesting: 6 levels against the default of 5.
func Deep(groups [][]int) {
	if groups != nil { // 1
		for _, group := range groups { // 2
			switch len(group) { // 3
			case 0:
			default:
				for _, v := range group { // 4
					if v > 0 { // 5
						func() { // 6
							println(v)
						}()
					}
				}
			}
		}
	}
}

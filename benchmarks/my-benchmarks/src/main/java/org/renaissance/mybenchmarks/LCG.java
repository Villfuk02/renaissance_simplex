package org.renaissance.mybenchmarks;

public class LCG {
    // Linear congruential generator, MODULO 2^64
    // MULTIPLIER taken from:
    // TABLES OF LINEAR CONGRUENTIAL GENERATORS OF DIFFERENT SIZES AND GOOD LATTICE
    // STRUCTURE by PIERRE L'ECUYER, Table 4
    // https://citeseerx.ist.psu.edu/doc/10.1.1.34.1024
    static final long MULTIPLIER = 3935559000370003845l;
    // INCREMENT can be any odd number
    static final long INCREMENT = 0xFACED;

    long currentState;

    public LCG(long seed) {
        currentState = seed;
    }

    // Advance to the next state.
    public long step() {
        currentState = MULTIPLIER * currentState + INCREMENT;
        return currentState;
    }

    // Get random number in [0, 1).
    public double nextDouble() {
        return ((double) (step() & Long.MAX_VALUE)) / Long.MAX_VALUE;
    }
}

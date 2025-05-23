package org.renaissance.mybenchmarks;

import javax.xml.validation.Validator;

import org.renaissance.Benchmark;
import org.renaissance.Benchmark.Group;
import org.renaissance.Benchmark.Licenses;
import org.renaissance.Benchmark.Name;
import org.renaissance.Benchmark.Parameter;
import org.renaissance.Benchmark.Summary;
import org.renaissance.BenchmarkContext;
import org.renaissance.BenchmarkResult;
import org.renaissance.BenchmarkResult.Validators;
import org.renaissance.License;
import org.renaissance.mybenchmarks.SimplexSolver.Constraint;
import org.renaissance.mybenchmarks.SimplexSolver.LPResult;
import org.renaissance.mybenchmarks.SimplexSolver.LinearProgram;

@Name("simplex")
@Group("my-benchmarks")
@Summary("Solves random linear programs using the simplex method using rational numbers.")
// I don't know how to make a usable compile-time constatnt in Java
@Parameter(name = "program_count", defaultValue = "10", summary = "Number of linear programs to solve.")
@Parameter(name = "seed", defaultValue = "42", summary = "Seed for the random number generator.")
@Licenses(License.MIT)
public final class Simplex implements Benchmark {

    public static final int DEFAULT_PROGRAM_COUNT = 10;
    public static final long DEFAULT_SEED = 42;
    private static final int VARIABLES = 50;
    private static final int CONSTRAINTS = 50;
    private static final int MAX_STEPS = 200;
    private static final double NONZERO_COEFFICIENT_CHANCE = 0.2;
    private static final double EQ_CHANCE = 0.05;
    private static final double GE_CHANCE = 0.1;

    // The expected values hold only for the default parameters.
    private static final Rational EXPECTED_SUM = Rational.parse(
            "2890528279780327546890920560296572053017582970462229169962737102270355639494297269989090972103025110388363/2775065187046933750458072200920143470609500555385545496730279581456018896727786579265533127822441171840");
    private static final long EXPECTED_FEASIBLE = 3;
    private static final long EXPECTED_INFEASIBLE = 7;
    private static final long EXPECTED_UNBOUNDED = 0;
    private static final long EXPECTED_TIMED_OUT = 0;

    private LCG lcg;
    private long seed;
    private int program_count;

    public void setUpBeforeEach(BenchmarkContext ctx) {
        seed = ctx.parameter("seed").toInteger();
        lcg = new LCG(seed);

        program_count = ctx.parameter("program_count").toPositiveInteger();
    }

    @Override
    public BenchmarkResult run(BenchmarkContext ctx) {
        Rational sum = Rational.ZERO;
        long feasibleCount = 0;
        long unboundedCount = 0;
        long infeasibleCount = 0;
        long timeoutCount = 0;

        for (int i = 0; i < program_count; i++) {
            LinearProgram lp = generateRandomLP();
            LPResult result = new SimplexSolver(false, MAX_STEPS).solve(lp);
            if (result == LPResult.INFEASIBLE) {
                infeasibleCount++;
            } else if (result == LPResult.UNBOUNDED) {
                unboundedCount++;
            } else if (result == LPResult.TIMEOUT) {
                timeoutCount++;
            } else {
                feasibleCount++;
                sum = sum.add(result.objectiveValue);
            }
        }

        if (program_count == DEFAULT_PROGRAM_COUNT && seed == DEFAULT_SEED) {
            return Validators.compound(
                    Validators.simple("real sum compared to expected sum", sum.compareTo(EXPECTED_SUM), 0),
                    Validators.simple("expected feasible", EXPECTED_FEASIBLE, feasibleCount),
                    Validators.simple("expected infeasible", EXPECTED_INFEASIBLE, infeasibleCount),
                    Validators.simple("expected unbounded", EXPECTED_UNBOUNDED, unboundedCount),
                    Validators.simple("expected timed out", EXPECTED_TIMED_OUT, timeoutCount));
        }
        return Validators.simple("programs run", program_count,
                feasibleCount + infeasibleCount + unboundedCount + timeoutCount);
    }

    private LinearProgram generateRandomLP() {
        Rational[] objective = new Rational[VARIABLES];
        for (int i = 0; i < VARIABLES; i++) {
            objective[i] = randomCoefficient(0);
        }

        LinearProgram lp = new LinearProgram(objective, true);

        for (int i = 0; i < CONSTRAINTS; i++) {
            Rational[] coefficients = new Rational[VARIABLES];
            for (int j = 0; j < VARIABLES; j++) {
                if (lcg.nextDouble() < NONZERO_COEFFICIENT_CHANCE)
                    coefficients[j] = randomCoefficient(64);
                else
                    coefficients[j] = Rational.ZERO;
            }
            Constraint.Type type = randomConstraintType();
            Rational rhs = randomCoefficient(128);
            lp.addConstraint(new Constraint(coefficients, type, rhs));
        }

        return lp;
    }

    private Constraint.Type randomConstraintType() {
        double rand = lcg.nextDouble();
        if (rand < EQ_CHANCE) {
            return Constraint.Type.EQ;
        } else if (rand < EQ_CHANCE + GE_CHANCE) {
            return Constraint.Type.GE;
        } else {
            return Constraint.Type.LE;
        }
    }

    // Generates a random integer in the range [x-128, x+127].
    private Rational randomCoefficient(int x) {
        return new Rational((lcg.step() >> 56) + x);
    }
}
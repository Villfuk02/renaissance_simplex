package org.renaissance.mybenchmarks;

import java.util.*;

public class SimplexSolver {

    public static class Constraint {
        public enum Type {
            LE, GE, EQ
        }

        public Rational[] coefficients;
        public Rational rhs;
        public Type type;

        public Constraint(Rational[] coefficients, Type type, Rational rhs) {
            this.coefficients = coefficients;
            this.rhs = rhs;
            this.type = type;
        }
    }

    public static class LinearProgram {
        public List<Constraint> constraints = new ArrayList<>();
        public Rational[] objective; // always maximize
        public boolean maximize; // if false, negate the result

        public LinearProgram(Rational[] objective, boolean maximize) {
            this.objective = objective;
            this.maximize = maximize;
            if (!maximize) {
                for (int i = 0; i < objective.length; i++) {
                    objective[i] = objective[i].negate();
                }
            }
        }

        public void addConstraint(Constraint c) {
            constraints.add(c);
        }
    }

    public static class LPResult {
        public static final LPResult INFEASIBLE = new LPResult(null, null);
        public static final LPResult UNBOUNDED = new LPResult(null, null);
        public static final LPResult TIMEOUT = new LPResult(null, null);
        public Rational[] solution;
        public Rational objectiveValue;

        public LPResult(Rational[] solution, Rational objectiveValue) {
            this.solution = solution;
            this.objectiveValue = objectiveValue;
        }

        public boolean isFinite() {
            return solution != null && objectiveValue != null;
        }
    }

    private Rational[][] tableau;
    private int rows, cols;
    private int numVarsOriginal;
    private int[] basis;
    private boolean debug;
    private int steps = 0;
    private int maxSteps;

    public SimplexSolver(boolean debug, int maxSteps) {
        this.debug = debug;
        this.maxSteps = maxSteps;
    }

    public LPResult solve(LinearProgram lp) {
        int artificialVars = preprocess(lp);

        if (debug)
            printTableau("Initial Tableau");

        // Phase I
        if (artificialVars > 0) {
            boolean finished = optimize();

            if (debug)
                printTableau("After Phase I");

            if (!finished)
                return steps >= maxSteps ? LPResult.TIMEOUT : LPResult.UNBOUNDED;
            if (!isFeasible())
                return LPResult.INFEASIBLE;
            removeArtificialVariables(artificialVars);
        }

        // Phase II
        resetObjective(lp);

        if (debug)
            printTableau("Before Phase II");

        boolean finished = optimize();

        if (debug)
            printTableau("After Phase II");

        if (!finished)
            return steps >= maxSteps ? LPResult.TIMEOUT : LPResult.UNBOUNDED;
        return extractSolution(lp);
    }

    private int preprocess(LinearProgram lp) {
        numVarsOriginal = lp.objective.length;

        // 1. Count slack/surplus and artificial variables
        int slackVars = 0, artificialVars = 0;
        for (Constraint c : lp.constraints) {
            if (c.type == Constraint.Type.LE) {
                slackVars++;
            } else if (c.type == Constraint.Type.GE) {
                slackVars++;
                artificialVars++;
            } else if (c.type == Constraint.Type.EQ) {
                artificialVars++;
            }
        }

        rows = lp.constraints.size() + 1;
        cols = numVarsOriginal + slackVars + artificialVars + 1;
        tableau = new Rational[rows][cols];
        basis = new int[rows - 1];
        for (int i = 0; i < rows; i++) {
            Arrays.fill(tableau[i], Rational.ZERO);
        }

        // 2. Fill tableau
        int slackIndex = numVarsOriginal;
        int artificialIndex = numVarsOriginal + slackVars;
        for (int i = 0; i < lp.constraints.size(); i++) {
            Rational[] row = lp.constraints.get(i).coefficients;
            for (int j = 0; j < row.length; j++) {
                tableau[i][j] = row[j];
            }

            Constraint.Type type = lp.constraints.get(i).type;
            if (type == Constraint.Type.LE) {
                tableau[i][slackIndex] = Rational.ONE;
                basis[i] = slackIndex;
                slackIndex++;
            } else if (type == Constraint.Type.GE) {
                tableau[i][slackIndex] = new Rational(-1);
                tableau[i][artificialIndex] = Rational.ONE;
                basis[i] = artificialIndex;
                artificialIndex++;
                slackIndex++;
            } else if (type == Constraint.Type.EQ) {
                tableau[i][artificialIndex] = Rational.ONE;
                basis[i] = artificialIndex;
                artificialIndex++;
            }

            tableau[i][cols - 1] = lp.constraints.get(i).rhs;
        }

        // 3. Build Phase I objective (sum of artificial vars)
        for (int j = 0; j < cols; j++) {
            boolean isArtificial = j >= numVarsOriginal + slackVars && j < cols - 1;
            tableau[rows - 1][j] = isArtificial ? Rational.ONE : Rational.ZERO;
        }
        for (int i = 0; i < lp.constraints.size(); i++) {
            int var = basis[i];
            if (var >= numVarsOriginal + slackVars && var < cols - 1) {
                for (int j = 0; j < cols; j++) {
                    tableau[rows - 1][j] = tableau[rows - 1][j].subtract(tableau[i][j]);
                }
            }
        }

        return artificialVars;
    }

    private void removeArtificialVariables(int count) {
        Rational[][] newTab = new Rational[rows][cols - count];
        for (int i = 0; i < rows; i++) {
            tableau[i][cols - count - 1] = tableau[i][cols - 1];
            newTab[i] = Arrays.copyOf(tableau[i], cols - count);
        }
        tableau = newTab;
        cols -= count;
    }

    private void resetObjective(LinearProgram lp) {
        Arrays.fill(tableau[rows - 1], Rational.ZERO);
        for (int i = 0; i < numVarsOriginal; i++) {
            tableau[rows - 1][i] = lp.objective[i].negate();
        }
        for (int i = 0; i < basis.length; i++) {
            int var = basis[i];
            if (var < numVarsOriginal) {
                for (int j = 0; j < cols; j++) {
                    tableau[rows - 1][j] = tableau[rows - 1][j].add(lp.objective[var].multiply(tableau[i][j]));
                }
            }
        }
    }

    private boolean isFeasible() {
        return tableau[rows - 1][cols - 1].equals(Rational.ZERO);
    }

    // Returns true when successfully optimized
    private boolean optimize() {
        while (true) {
            steps++;
            int pivotCol = findEntering();
            if (pivotCol == -1)
                return true; // Finished
            int pivotRow = findLeaving(pivotCol);
            if (pivotRow == -1)
                return false; // Unbounded
            pivot(pivotRow, pivotCol);

            if (steps >= maxSteps)
                return false; // Timeout
        }
    }

    private int findEntering() {
        Rational min = Rational.ZERO;
        int pivotCol = -1;
        for (int j = 0; j < cols - 1; j++) {
            if (tableau[rows - 1][j].compareTo(min) < 0) {
                pivotCol = j;
                min = tableau[rows - 1][j];
            }
        }
        return pivotCol;
    }

    private int findLeaving(int pivotCol) {
        Rational min = null;
        int pivotRow = -1;
        for (int i = 0; i < rows - 1; i++) {
            if (tableau[i][pivotCol].compareTo(Rational.ZERO) > 0) {
                Rational ratio = tableau[i][cols - 1].divide(tableau[i][pivotCol]);
                if (min == null || ratio.compareTo(min) < 0) {
                    min = ratio;
                    pivotRow = i;
                }
            }
        }
        return pivotRow;
    }

    private void pivot(int row, int col) {
        Rational pivot = tableau[row][col];
        for (int j = 0; j < cols; j++) {
            tableau[row][j] = tableau[row][j].divide(pivot);
        }
        for (int i = 0; i < rows; i++) {
            if (i != row) {
                Rational factor = tableau[i][col];
                for (int j = 0; j < cols; j++) {
                    tableau[i][j] = tableau[i][j].subtract(factor.multiply(tableau[row][j]));
                }
            }
        }
        basis[row] = col;
    }

    private LPResult extractSolution(LinearProgram lp) {
        int numVars = lp.objective.length;
        Rational[] result = new Rational[numVars];
        Arrays.fill(result, Rational.ZERO);
        for (int i = 0; i < basis.length; i++) {
            int var = basis[i];
            if (var < numVars) {
                result[var] = tableau[i][cols - 1];
            }
        }
        Rational objective = tableau[rows - 1][cols - 1];
        if (!lp.maximize) {
            objective = objective.negate();
        }

        return new LPResult(result, objective);
    }

    private void printTableau(String title) {
        System.out.println("\n=== " + title + " ===");
        System.out.println("Basis: " + Arrays.toString(basis));
        for (Rational[] row : tableau) {
            for (Rational r : row) {
                System.out.print(r + "\t");
            }
            System.out.println();
        }
    }
}

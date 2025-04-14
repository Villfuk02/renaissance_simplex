package org.renaissance.mybenchmarks;

import java.math.BigInteger;
import java.util.Objects;

public final class Rational implements Comparable<Rational> {
    private final BigInteger numerator;
    private final BigInteger denominator;

    public static final Rational ZERO = new Rational(BigInteger.ZERO, BigInteger.ONE);
    public static final Rational ONE = new Rational(BigInteger.ONE, BigInteger.ONE);

    public Rational(BigInteger numerator, BigInteger denominator) {
        if (denominator.equals(BigInteger.ZERO)) {
            throw new ArithmeticException("Denominator cannot be zero.");
        }

        // Normalize sign and reduce fraction
        BigInteger gcd = numerator.gcd(denominator);
        BigInteger sign = denominator.signum() < 0 ? BigInteger.valueOf(-1) : BigInteger.ONE;

        this.numerator = numerator.divide(gcd).multiply(sign);
        this.denominator = denominator.divide(gcd).abs();
    }

    public Rational(long numerator, long denominator) {
        this(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public Rational(long number) {
        this(BigInteger.valueOf(number), BigInteger.ONE);
    }

    public Rational add(Rational other) {
        BigInteger num = this.numerator.multiply(other.denominator).add(other.numerator.multiply(this.denominator));
        BigInteger den = this.denominator.multiply(other.denominator);
        return new Rational(num, den);
    }

    public Rational subtract(Rational other) {
        BigInteger num = this.numerator.multiply(other.denominator)
                .subtract(other.numerator.multiply(this.denominator));
        BigInteger den = this.denominator.multiply(other.denominator);
        return new Rational(num, den);
    }

    public Rational multiply(Rational other) {
        return new Rational(this.numerator.multiply(other.numerator), this.denominator.multiply(other.denominator));
    }

    public Rational divide(Rational other) {
        if (other.numerator.equals(BigInteger.ZERO)) {
            throw new ArithmeticException("Division by zero.");
        }
        return new Rational(this.numerator.multiply(other.denominator), this.denominator.multiply(other.numerator));
    }

    public Rational negate() {
        return new Rational(numerator.negate(), denominator);
    }

    public BigInteger toBigInteger() {
        return numerator.divide(denominator);
    }

    public double toDouble() {
        return numerator.doubleValue() / denominator.doubleValue();
    }

    @Override
    public int compareTo(Rational other) {
        return this.numerator.multiply(other.denominator).compareTo(other.numerator.multiply(this.denominator));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Rational))
            return false;
        Rational other = (Rational) obj;
        return numerator.equals(other.numerator) && denominator.equals(other.denominator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator);
    }

    @Override
    public String toString() {
        return denominator.equals(BigInteger.ONE) ? numerator.toString() : numerator + "/" + denominator;
    }

    public static Rational parse(String s) {
        s = s.trim();
        if (s.contains("/")) {
            String[] parts = s.split("/");
            if (parts.length != 2)
                throw new NumberFormatException("Invalid rational format: " + s);
            BigInteger num = new BigInteger(parts[0].trim());
            BigInteger den = new BigInteger(parts[1].trim());
            return new Rational(num, den);
        } else {
            return new Rational(new BigInteger(s), BigInteger.ONE);
        }
    }
}

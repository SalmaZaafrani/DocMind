package com.docmind.agent;

/**
 * Évalue une expression arithmétique (+ - * / % ^, parenthèses, décimales avec point ou virgule).
 * Analyseur maison : aucun code n'est exécuté.
 */
public final class Calculator {

    private final String s;
    private int pos;

    private Calculator(String expression) {
        this.s = expression.replace(" ", "").replace(",", ".").replace("×", "*").replace("÷", "/");
    }

    public static double evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression vide");
        }
        Calculator c = new Calculator(expression);
        double value = c.expr();
        if (c.pos != c.s.length()) {
            throw new IllegalArgumentException("Caractère inattendu : " + c.s.charAt(c.pos));
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Résultat non défini (division par zéro ?)");
        }
        return value;
    }

    public static String format(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private double expr() {
        double v = term();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '+') { pos++; v += term(); }
            else if (c == '-') { pos++; v -= term(); }
            else break;
        }
        return v;
    }

    private double term() {
        double v = power();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '*') { pos++; v *= power(); }
            else if (c == '/') { pos++; v /= power(); }
            else if (c == '%') { pos++; v %= power(); }
            else break;
        }
        return v;
    }

    private double power() {
        double base = unary();
        if (pos < s.length() && s.charAt(pos) == '^') {
            pos++;
            return Math.pow(base, power());
        }
        return base;
    }

    private double unary() {
        if (pos < s.length() && s.charAt(pos) == '-') { pos++; return -unary(); }
        if (pos < s.length() && s.charAt(pos) == '+') { pos++; return unary(); }
        return primary();
    }

    private double primary() {
        if (pos < s.length() && s.charAt(pos) == '(') {
            pos++;
            double v = expr();
            if (pos >= s.length() || s.charAt(pos) != ')') {
                throw new IllegalArgumentException("Parenthèse fermante manquante");
            }
            pos++;
            return v;
        }
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("Nombre attendu en position " + (pos + 1));
        }
        return Double.parseDouble(s.substring(start, pos));
    }
}

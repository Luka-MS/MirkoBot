package com.musicbot.counting;

/**
 * Safe recursive-descent arithmetic expression evaluator.
 *
 * Supports: +  -  *  /  //  %  ** (or ^) and parentheses.
 * Functions: sqrt(), log() (base-10), ln() (natural), log2() (base-2).
 * Result must be a whole number (integer), otherwise an exception is thrown.
 * Exponents are capped at 100 to prevent denial-of-service via huge numbers.
 *
 * Grammar (informal):
 *   expr    = term   ( ('+' | '-') term   )*
 *   term    = power  ( ('*' | '/' | '//' | '%') power )*
 *   power   = unary  ( ('**' | '^') power )?   ← right-associative
 *   unary   = ('-' | '+')? primary
 *   primary = FUNC '(' expr ')' | '(' expr ')' | NUMBER
 */
public class MathEvaluator {

    private String input;
    private int pos;

    /**
     * Evaluates {@code expression} and returns the integer result.
     *
     * @throws ArithmeticException  if the expression is invalid, non-integer, or
     *                              contains an exponent > 100
     * @throws NumberFormatException if a numeric literal cannot be parsed
     */
    public long evaluate(String expression) {
        this.input = expression.trim();
        this.pos = 0;
        double result = parseExpr();
        skipSpaces();
        if (pos < input.length()) {
            throw new ArithmeticException("Unexpected character at position " + pos);
        }
        if (result != Math.floor(result) || Double.isInfinite(result) || Double.isNaN(result)) {
            throw new ArithmeticException("Result is not a whole integer");
        }
        return (long) result;
    }

    // ── Grammar rules ─────────────────────────────────────────────────────────

    private double parseExpr() {
        double left = parseTerm();
        skipSpaces();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '+') {
                pos++;
                left += parseTerm();
            } else if (c == '-') {
                pos++;
                left -= parseTerm();
            } else {
                break;
            }
            skipSpaces();
        }
        return left;
    }

    private double parseTerm() {
        double left = parsePower();
        skipSpaces();
        while (pos < input.length()) {
            // Floor division '//' must be checked before plain '/'
            if (pos + 1 < input.length() && input.charAt(pos) == '/' && input.charAt(pos + 1) == '/') {
                pos += 2;
                double right = parsePower();
                if (right == 0) throw new ArithmeticException("Division by zero");
                left = Math.floor(left / right);
            } else if (input.charAt(pos) == '*' && (pos + 1 >= input.length() || input.charAt(pos + 1) != '*')) {
                pos++;
                left *= parsePower();
            } else if (input.charAt(pos) == '/') {
                pos++;
                double right = parsePower();
                if (right == 0) throw new ArithmeticException("Division by zero");
                left /= right;
            } else if (input.charAt(pos) == '%') {
                pos++;
                double right = parsePower();
                if (right == 0) throw new ArithmeticException("Modulo by zero");
                left %= right;
            } else {
                break;
            }
            skipSpaces();
        }
        return left;
    }

    /** Right-associative: 2**3**2 == 2**(3**2) == 512.  Also accepts '^'. */
    private double parsePower() {
        double base = parseUnary();
        skipSpaces();
        if (pos + 1 < input.length() && input.charAt(pos) == '*' && input.charAt(pos + 1) == '*') {
            pos += 2;
            double exp = parsePower();   // right-recursive
            if (Math.abs(exp) > 100) throw new ArithmeticException("Exponent too large (> 100)");
            return Math.pow(base, exp);
        }
        if (pos < input.length() && input.charAt(pos) == '^') {
            pos++;
            double exp = parsePower();   // right-recursive
            if (Math.abs(exp) > 100) throw new ArithmeticException("Exponent too large (> 100)");
            return Math.pow(base, exp);
        }
        return base;
    }

    private double parseUnary() {
        skipSpaces();
        if (pos < input.length() && input.charAt(pos) == '-') {
            pos++;
            return -parsePrimary();
        }
        if (pos < input.length() && input.charAt(pos) == '+') {
            pos++;
            return parsePrimary();
        }
        return parsePrimary();
    }

    private static final String[] FUNCTIONS = {"sqrt", "log2", "log10", "log", "ln"};

    private double parsePrimary() {
        skipSpaces();
        if (pos >= input.length()) throw new ArithmeticException("Unexpected end of expression");

        // Function call: sqrt(...), log(...), ln(...), log2(...), log10(...)
        for (String func : FUNCTIONS) {
            if (input.startsWith(func, pos)) {
                int afterFunc = pos + func.length();
                // Make sure it's followed by '(' (with optional spaces)
                int peek = afterFunc;
                while (peek < input.length() && input.charAt(peek) == ' ') peek++;
                if (peek < input.length() && input.charAt(peek) == '(') {
                    pos = peek + 1;  // consume func and '('
                    double arg = parseExpr();
                    skipSpaces();
                    if (pos >= input.length() || input.charAt(pos) != ')') {
                        throw new ArithmeticException("Missing closing parenthesis for " + func);
                    }
                    pos++;  // consume ')'
                    skipSpaces();
                    return applyFunction(func, arg);
                }
            }
        }

        if (input.charAt(pos) == '(') {
            pos++;   // consume '('
            double val = parseExpr();
            skipSpaces();
            if (pos >= input.length() || input.charAt(pos) != ')') {
                throw new ArithmeticException("Missing closing parenthesis");
            }
            pos++;   // consume ')'
            skipSpaces();
            return val;
        }

        // Parse a numeric literal (integer or decimal)
        int start = pos;
        while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
            pos++;
        }
        if (pos == start) {
            throw new ArithmeticException("Expected a number at position " + pos);
        }
        skipSpaces();
        return Double.parseDouble(input.substring(start, pos));
    }

    private double applyFunction(String func, double arg) {
        switch (func) {
            case "sqrt":
                if (arg < 0) throw new ArithmeticException("sqrt of negative number");
                return Math.sqrt(arg);
            case "log":
            case "log10":
                if (arg <= 0) throw new ArithmeticException("log of non-positive number");
                return Math.log10(arg);
            case "ln":
                if (arg <= 0) throw new ArithmeticException("ln of non-positive number");
                return Math.log(arg);
            case "log2":
                if (arg <= 0) throw new ArithmeticException("log2 of non-positive number");
                return Math.log(arg) / Math.log(2);
            default:
                throw new ArithmeticException("Unknown function: " + func);
        }
    }

    private void skipSpaces() {
        while (pos < input.length() && input.charAt(pos) == ' ') pos++;
    }
}

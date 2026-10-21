package com.constsmodels;

/**
 * Writes numbers for text formats (OBJ, MTL): at most four decimals, no trailing zeros and never an exponent,
 * so files stay small and every reader takes them.
 */
final class DecimalText
{
    private static final int DECIMALS = 4;
    private static final long SCALE = 10_000;

    private DecimalText()
    {
    }

    /** Appends the value, rounded to four decimals; anything not finite is written as 0. */
    static StringBuilder append(StringBuilder out, double value)
    {
        long scaled = Double.isFinite(value) ? Math.round(value * SCALE) : 0;
        if (scaled < 0)
        {
            out.append('-');
            scaled = -scaled;
        }
        out.append(scaled / SCALE);
        long fraction = scaled % SCALE;
        if (fraction != 0)
        {
            out.append('.');
            int digits = DECIMALS;
            while (fraction % 10 == 0)
            {
                fraction /= 10;
                digits--;
            }
            String text = Long.toString(fraction);
            for (int pad = text.length(); pad < digits; pad++)
            {
                out.append('0');
            }
            out.append(text);
        }
        return out;
    }

    static String of(double value)
    {
        return append(new StringBuilder(), value).toString();
    }
}

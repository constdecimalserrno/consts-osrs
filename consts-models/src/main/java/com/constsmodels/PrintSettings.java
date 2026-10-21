package com.constsmodels;

/**
 * How a model is made ready to print.
 *
 * @param sizeMm the longest side of the print, in millimetres; STL and 3MF are scaled to it
 * @param base whether to stand the print on a solid base
 * @param maxColors how many filament colors a 3MF starts with, 1 to {@link PrintColors#MAX}, when the viewer hasn't
 *     chosen them
 */
final class PrintSettings
{
    final int sizeMm;
    final boolean base;
    final int maxColors;

    PrintSettings(int sizeMm, boolean base, int maxColors)
    {
        this.sizeMm = sizeMm;
        this.base = base;
        this.maxColors = maxColors;
    }

    /** Whether a print made with the other settings would come out the same shape as one made with these. */
    boolean sameShape(PrintSettings other)
    {
        return other != null && sizeMm == other.sizeMm && base == other.base;
    }
}

package com.constsmodels;

/**
 * How a saved file is made ready to print.
 *
 * @param sizeMm the longest side of the print, in millimetres; STL and 3MF are scaled to it
 * @param base whether to stand the print on a solid base
 * @param maxColors how many filament colors a 3MF starts with, 1 to {@link PrintColors#MAX}, when the viewer hasn't
 *     chosen them
 * @param solid whether to rebuild the model as one closed solid first ({@link SolidRemesh}), for STL and 3MF
 */
final class PrintSettings
{
    final int sizeMm;
    final boolean base;
    final int maxColors;
    final boolean solid;

    PrintSettings(int sizeMm, boolean base, int maxColors)
    {
        this(sizeMm, base, maxColors, false);
    }

    PrintSettings(int sizeMm, boolean base, int maxColors, boolean solid)
    {
        this.sizeMm = sizeMm;
        this.base = base;
        this.maxColors = maxColors;
        this.solid = solid;
    }

    PrintSettings withSolid(boolean solid)
    {
        return new PrintSettings(sizeMm, base, maxColors, solid);
    }
}

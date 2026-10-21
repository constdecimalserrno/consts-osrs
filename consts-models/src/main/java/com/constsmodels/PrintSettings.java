package com.constsmodels;

/**
 * How a saved file is made ready to print.
 *
 * @param sizeMm the longest side of the print, in millimetres; STL and 3MF are scaled to it
 * @param base whether to stand the print on a solid base
 * @param maxColors the most filament colors a 3MF uses
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
}

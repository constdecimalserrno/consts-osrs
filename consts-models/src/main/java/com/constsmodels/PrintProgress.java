package com.constsmodels;

/**
 * How far making a print has got, for the bar that fills while it's made. Told from the thread making it, from 0 as
 * it starts to 1 when it's done; a step that starts over, as with coarser voxels, may tell less than before, and the
 * bar doesn't go back for it.
 */
interface PrintProgress
{
    /** Tells no one. */
    PrintProgress NONE = share ->
    {
    };

    /** The share of the work done so far, 0 to 1. */
    void done(double share);

    /** A step of the work, from start to end of the whole: the step's own 0 to 1 is told as start to end. */
    default PrintProgress step(double start, double end)
    {
        return share -> done(start + (end - start) * Math.max(0, Math.min(1, share)));
    }
}

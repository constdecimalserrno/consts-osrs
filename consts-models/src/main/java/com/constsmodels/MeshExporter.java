package com.constsmodels;

import java.io.File;
import java.io.IOException;

/**
 * TICKET 2 (stub): writes a mesh to a file, ready to print. STL and 3MF are scaled so the longest side is
 * {@link PrintSettings#sizeMm} millimetres, stood on the build plate (lowest point at z = 0, centered on x and y) and
 * cleaned up for a slicer; 3MF carries colors merged down to {@link PrintSettings#maxColors}. OBJ (with MTL) and PLY
 * keep the game's units and colors for 3D software.
 */
final class MeshExporter
{
    private MeshExporter()
    {
    }

    /** Writes the mesh into the folder, named after it and the time, and returns the file written. */
    static File export(Mesh mesh, ExportFormat format, File folder, PrintSettings settings) throws IOException
    {
        throw new UnsupportedOperationException("Saving isn't built yet");
    }
}

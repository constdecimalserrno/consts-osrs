package com.constsmodels;

/** The files a mesh can be saved as. */
enum ExportFormat
{
    /** Shape only, in millimetres, for any slicer. */
    STL("STL", "stl"),
    /** Shape and colors, in millimetres, for multicolor printers and slicers that read 3MF. */
    THREE_MF("3MF", "3mf"),
    /** Shape with colors in a material file, for Blender and other 3D software. */
    OBJ("OBJ", "obj"),
    /** Shape with a color on every corner. */
    PLY("PLY", "ply");

    private final String label;
    private final String extension;

    ExportFormat(String label, String extension)
    {
        this.label = label;
        this.extension = extension;
    }

    String extension()
    {
        return extension;
    }

    @Override
    public String toString()
    {
        return label;
    }
}

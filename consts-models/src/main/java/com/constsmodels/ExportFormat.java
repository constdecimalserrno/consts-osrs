package com.constsmodels;

/** The files a mesh can be saved as. Public because it's a setting: RuneLite's config proxy has to see it. */
public enum ExportFormat
{
    /** Shape with colors in a material file, for Blender and other 3D software. */
    OBJ("OBJ", "obj"),
    /** Shape with a color on every corner. */
    PLY("PLY", "ply"),
    /** Binary glTF: shape, colors and an actor's animations, which Blender plays. */
    GLB("GLB", "glb");

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

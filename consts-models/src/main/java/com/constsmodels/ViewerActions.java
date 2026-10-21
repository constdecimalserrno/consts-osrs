package com.constsmodels;

import java.awt.image.BufferedImage;

/** What the viewer's buttons do. The plugin supplies them; every call comes from the Swing thread. */
interface ViewerActions
{
    /**
     * Saves the mesh in the chosen folder, off the Swing thread, and says where in chat. A 3MF prints in the colors
     * given, as the viewer shows them; null merges the model's colors down to the settings' count.
     */
    void save(Mesh mesh, ExportFormat format, PrintColors colors);

    /** Saves a picture of the viewer as a PNG in the chosen folder. */
    void saveImage(Mesh mesh, BufferedImage image);

    /** Opens the chosen folder in the computer's file browser. */
    void openFolder();

    /** The format the save button starts on, from the settings. */
    ExportFormat defaultFormat();

    /** The print settings, for the size the viewer shows. */
    PrintSettings printSettings();
}

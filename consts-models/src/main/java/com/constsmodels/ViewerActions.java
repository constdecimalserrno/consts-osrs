package com.constsmodels;

import java.awt.image.BufferedImage;

/** What the viewer's buttons do. The plugin supplies them; every call comes from the Swing thread. */
interface ViewerActions
{
    /**
     * Saves the model as the Game view shows it, in the chosen folder, off the Swing thread, and says where in chat.
     * A 3MF prints in the colors given; null merges the model's colors down to the settings' count.
     */
    void save(Mesh mesh, ExportFormat format, PrintColors colors);

    /** Saves the print as the Print view shows it: the closed solid, in millimetres, on the filaments given. */
    void savePrint(Printable printable, ExportFormat format, PrintColors colors);

    /** Saves a picture of the viewer as a PNG in the chosen folder. */
    void saveImage(Mesh mesh, BufferedImage image);

    /** Opens the chosen folder in the computer's file browser. */
    void openFolder();

    /** The format the save button starts on, from the settings. */
    ExportFormat defaultFormat();

    /** The print settings: the print's size and whether it stands on a base. */
    PrintSettings printSettings();

    /** The background viewers draw on: a color, or {@link ViewerCanvas#DEFAULT_BACKGROUND} for their dark fall. */
    int background();

    /** Remembers the background for every viewer from now on. */
    void setBackground(int rgb);
}

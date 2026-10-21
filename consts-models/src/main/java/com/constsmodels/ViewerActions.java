package com.constsmodels;

import java.awt.image.BufferedImage;

/** What the viewer's buttons do. The plugin supplies them; every call comes from the Swing thread. */
interface ViewerActions
{
    /** Saves the model in the chosen folder, off the Swing thread, and says where in chat. */
    void save(Mesh mesh, ExportFormat format);

    /** Saves a picture of the viewer as a PNG in the chosen folder. */
    void saveImage(Mesh mesh, BufferedImage image);

    /** Opens the chosen folder in the computer's file browser. */
    void openFolder();

    /** The format the save button starts on, from the settings. */
    ExportFormat defaultFormat();

    /** The background viewers draw on: a color, or {@link ViewerCanvas#DEFAULT_BACKGROUND} for their dark fall. */
    int background();

    /** Remembers the background for every viewer from now on. */
    void setBackground(int rgb);
}

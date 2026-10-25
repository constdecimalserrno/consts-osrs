package com.constsmodels;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

/**
 * A viewer's window: one for each capture, titled for what it shows, and not modal, so the game goes on behind it.
 * Closing it stops its drawing.
 */
final class ViewerWindow extends JFrame
{
    static final String TITLE = "consts-Models: ";

    private final ViewerPanel panel;

    ViewerWindow(Mesh mesh, ViewerActions actions)
    {
        super(title(mesh.name));
        panel = new ViewerPanel(mesh, actions);
        setContentPane(panel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        // Wide enough for the bar's buttons, and an animation's controls beside them
        setMinimumSize(new Dimension(Math.max(640, panel.minimumWidth()), 420));
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowActivated(WindowEvent e)
            {
                panel.refresh();
            }

            @Override
            public void windowClosed(WindowEvent e)
            {
                panel.stop();
            }
        });
        pack();

        // No bigger than most of the screen it opens on
        Rectangle screen = getGraphicsConfiguration().getBounds();
        int width = Math.min(getWidth(), screen.width * 4 / 5);
        int height = Math.min(getHeight(), screen.height * 4 / 5);
        setSize(Math.max(width, getMinimumSize().width), Math.max(height, getMinimumSize().height));

        panel.start();
    }

    /** "consts-Models: NPC Goblin", with any of the game's color tags taken out of the name. */
    static String title(String name)
    {
        String plain = name == null ? "" : name.replaceAll("<[^>]*>", "").trim();
        return TITLE + (plain.isEmpty() ? "Model" : plain);
    }
}

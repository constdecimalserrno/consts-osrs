package com.constsmodels;

import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The viewer, a window of its own over the game for one mesh: drag to turn it, scroll to zoom, and buttons to save
 * it (the formats in {@link ExportFormat}), save a picture of it and open the folder. Each capture gets its own
 * window, none of them modal; a new one opens over the game, or a little down and right of the last one still open.
 */
final class ModelViewer
{
    // How far each new window sits from the last one
    private static final int CASCADE = 28;

    // The windows open now, oldest first; only touched on the Swing thread
    private static final List<ViewerWindow> OPEN = new ArrayList<>();

    private ModelViewer()
    {
    }

    /** Opens a new viewer window on the mesh. Call it on the Swing thread. */
    static void open(Mesh mesh, ViewerActions actions)
    {
        ViewerWindow window = new ViewerWindow(mesh, actions);
        Window game = gameWindow();
        if (game != null)
        {
            window.setIconImages(game.getIconImages());
        }

        if (OPEN.isEmpty())
        {
            window.setLocationRelativeTo(game);
        }
        else
        {
            ViewerWindow previous = OPEN.get(OPEN.size() - 1);
            Rectangle place = new Rectangle(previous.getX() + CASCADE, previous.getY() + CASCADE,
                window.getWidth(), window.getHeight());
            GraphicsConfiguration screen = previous.getGraphicsConfiguration();
            if (screen != null && screen.getBounds().contains(place))
            {
                window.setLocation(place.x, place.y);
            }
            else
            {
                window.setLocationRelativeTo(game);
            }
        }

        OPEN.add(window);
        window.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosed(WindowEvent e)
            {
                OPEN.remove(window);
            }
        });
        window.setVisible(true);
        window.toFront();
    }

    /** Closes every viewer, as when the plugin is turned off. Call it on the Swing thread. */
    static void closeAll()
    {
        for (ViewerWindow window : new ArrayList<>(OPEN))
        {
            window.dispose();
        }
        OPEN.clear();
    }

    /** The game's window, to open over and to take the icon of: the active window, or else the first one showing. */
    private static Window gameWindow()
    {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active != null && !(active instanceof ViewerWindow))
        {
            return active;
        }
        for (Frame frame : Frame.getFrames())
        {
            if (frame.isShowing() && !(frame instanceof ViewerWindow))
            {
                return frame;
            }
        }
        return null;
    }
}

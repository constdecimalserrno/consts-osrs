package com.constsmodels;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The viewer's picture of a mesh, and the mouse on it: drag to turn it, scroll to zoom towards the cursor,
 * right-drag or Shift-drag to move it, double-click or R to see all of it again.
 *
 * <p>Pictures are drawn on a thread of the view's own, never on the Swing thread, and only the newest one asked for
 * is drawn. While the mouse is moving the model the picture is drawn smaller, as small as it takes to keep up, and
 * stretched to fit; once the mouse lets go it is drawn again at the screen's full resolution.
 */
final class ViewerCanvas extends JComponent
{
    private static final Logger log = LoggerFactory.getLogger(ViewerCanvas.class);

    /** The line of help along the bottom of the view. */
    static final String HINT =
        "Drag to turn  \u00b7  Scroll to zoom  \u00b7  Right-drag to move  \u00b7  Double-click to reset";

    /** The background that isn't one color: a dark fall from the top of the picture to the bottom. */
    static final int DEFAULT_BACKGROUND = -1;

    // How big a picture is drawn while the model is moving, as a share of the view's size in screen points
    private static final double[] MOVING_SCALES = {2, 1.5, 1, 0.75, 0.5, 0.35, 0.25};

    // A moving picture drawn faster than this can be bigger; one slower than this, smaller
    private static final long FAST_MS = 9;
    private static final long SLOW_MS = 26;

    // How long after the last scroll or resize the full picture is drawn
    private static final int SETTLE_MS = 160;

    private static final Color HINT_COLOR = new Color(
        ColorScheme.LIGHT_GRAY_COLOR.getRed(), ColorScheme.LIGHT_GRAY_COLOR.getGreen(),
        ColorScheme.LIGHT_GRAY_COLOR.getBlue(), 150);
    private static final Color INFO_COLOR = ColorScheme.LIGHT_GRAY_COLOR;
    // The same two lines on a light background
    private static final Color HINT_COLOR_ON_LIGHT = new Color(0, 0, 0, 140);
    private static final Color INFO_COLOR_ON_LIGHT = new Color(0x3A3A3A);

    private final Mesh mesh;
    private final OrbitCamera camera = new OrbitCamera();
    private final Timer settle;
    private final Thread thread;

    // Shared with the drawing thread, under the lock
    private final Object lock = new Object();
    private Request pending;
    private final ArrayDeque<Request> pictures = new ArrayDeque<>();
    private boolean stopped;
    private BufferedImage front;
    private boolean frontFull;

    // The drawing thread's own: pictures to draw into, kept between frames
    private final List<BufferedImage> spare = new ArrayList<>();

    // Which of MOVING_SCALES moving pictures are drawn at, learnt from how long they take, and the screen's scale
    private volatile int movingScale;
    private volatile double screenScaleSeen = 1;

    // What is shown, on the Swing thread: the mesh (the model, or its print), the colors it's drawn in (null for
    // its own), the background, and a line about it
    private Mesh shown;
    private int[] colors;
    private int background = DEFAULT_BACKGROUND;
    private String info = "";

    // The mouse, on the Swing thread
    private Point last;
    private boolean panning;
    private boolean dragged;
    private boolean touched;
    private boolean fitted;

    ViewerCanvas(Mesh mesh)
    {
        this.mesh = mesh;
        this.shown = mesh;
        movingScale = mesh.faceCount() > 100_000 ? 4 : mesh.faceCount() > 20_000 ? 2 : 0;

        setOpaque(true);
        setFocusable(true);
        setPreferredSize(new Dimension(960, 640));

        settle = new Timer(SETTLE_MS, this::settled);
        settle.setRepeats(false);

        MouseAdapter mouse = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                requestFocusInWindow();
                last = e.getPoint();
                dragged = false;
                panning = !SwingUtilities.isLeftMouseButton(e) || e.isShiftDown() || e.isControlDown();
            }

            @Override
            public void mouseDragged(MouseEvent e)
            {
                if (last == null)
                {
                    return;
                }
                int dx = e.getX() - last.x;
                int dy = e.getY() - last.y;
                last = e.getPoint();
                if (dx == 0 && dy == 0)
                {
                    return;
                }
                if (panning)
                {
                    camera.pan(dx, dy, getHeight());
                }
                else
                {
                    camera.orbit(dx, dy);
                }
                dragged = true;
                touched = true;
                draw(false);
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                // Only once every button is up
                int buttons = InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK
                    | InputEvent.BUTTON3_DOWN_MASK;
                if ((e.getModifiersEx() & buttons) != 0)
                {
                    return;
                }
                last = null;
                if (dragged)
                {
                    dragged = false;
                    settle.stop();
                    draw(true);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2)
                {
                    reset();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e)
            {
                double notches = e.getPreciseWheelRotation();
                if (notches == 0)
                {
                    return;
                }
                camera.zoom(notches, e.getX(), e.getY(), getWidth(), getHeight());
                touched = true;
                draw(false);
                settle.restart();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "reset");
        getActionMap().put("reset", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                reset();
            }
        });

        addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                if (getWidth() <= 0 || getHeight() <= 0)
                {
                    return;
                }
                // Until the model has been moved, keep all of it in view as the window settles on its size
                if (!touched)
                {
                    camera.fit(shown, getWidth(), getHeight());
                }
                if (!fitted)
                {
                    fitted = true;
                    draw(true);
                    return;
                }
                draw(false);
                settle.restart();
            }
        });

        thread = new Thread(this::drawLoop, "consts-Models viewer");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
    }

    /** Starts the drawing thread. */
    void start()
    {
        thread.start();
    }

    /** Stops the drawing thread and lets the pictures go. */
    void stop()
    {
        settle.stop();
        synchronized (lock)
        {
            stopped = true;
            pending = null;
            pictures.clear();
            front = null;
            lock.notifyAll();
        }
    }

    /** Back to the view it opened on, all of the model in sight. */
    void reset()
    {
        if (getWidth() > 0 && getHeight() > 0)
        {
            camera.fit(shown, getWidth(), getHeight());
            touched = false;
            draw(true);
        }
    }

    /**
     * Shows this mesh, where the view is looking now, its triangles in these colors (one for each), or null for its
     * own. The Print view's mesh sits where the model does, so the view stays put between the two.
     */
    void show(Mesh mesh, int[] colors)
    {
        this.shown = mesh;
        this.colors = colors;
        draw(true);
    }

    /** Draws the model on one color, or on {@link #DEFAULT_BACKGROUND}. */
    void setBackgroundColor(int rgb)
    {
        if (rgb == background)
        {
            return;
        }
        background = rgb;
        repaint();
        draw(true);
    }

    int backgroundColor()
    {
        return background;
    }

    /** Whether text on this background should be dark. */
    static boolean light(int background)
    {
        if (background == DEFAULT_BACKGROUND)
        {
            return false;
        }
        int r = background >> 16 & 0xFF;
        int g = background >> 8 & 0xFF;
        int b = background & 0xFF;
        return r * 299 + g * 587 + b * 114 > 150_000;
    }

    /** Sets the line of small print in the top left corner, about the model. */
    void setInfo(String info)
    {
        this.info = info == null ? "" : info;
        repaint();
    }

    /** Draws the current view at full resolution, off the Swing thread, then hands it over on the Swing thread. */
    void picture(Consumer<BufferedImage> then)
    {
        double scale = screenScale();
        int width = Math.max(1, (int) Math.round(Math.max(1, getWidth()) * scale));
        int height = Math.max(1, (int) Math.round(Math.max(1, getHeight()) * scale));
        synchronized (lock)
        {
            pictures.add(new Request(shown, camera.copy(), colors, background, width, height, true, then));
            lock.notifyAll();
        }
    }

    /** Asks for a new picture of the current view: full size, or the size moving pictures are drawn at. */
    private void draw(boolean full)
    {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0)
        {
            return;
        }
        double screen = screenScale();
        if (!full)
        {
            // Never bigger than the screen's own pixels: start learning from the biggest size that isn't
            while (movingScale < MOVING_SCALES.length - 1 && MOVING_SCALES[movingScale] > screen)
            {
                movingScale++;
            }
            screenScaleSeen = screen;
        }
        double scale = full ? screen : MOVING_SCALES[movingScale];
        Request request = new Request(shown, camera.copy(), colors, background,
            Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale)), full, null);
        synchronized (lock)
        {
            pending = request;
            lock.notifyAll();
        }
    }

    private void settled(ActionEvent e)
    {
        if (last == null)
        {
            draw(true);
        }
    }

    /** How many of the screen's pixels there are to one of Swing's points: 2 on a Retina screen. */
    private double screenScale()
    {
        GraphicsConfiguration config = getGraphicsConfiguration();
        double scale = config == null ? 1 : config.getDefaultTransform().getScaleX();
        return Math.max(1, Math.min(3, scale));
    }

    private void drawLoop()
    {
        // One renderer per mesh shown, made again when the view switches between the model and its print
        MeshRenderer renderer = null;
        Mesh rendered = null;
        while (true)
        {
            Request request;
            synchronized (lock)
            {
                while (!stopped && pending == null && pictures.isEmpty())
                {
                    try
                    {
                        lock.wait();
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (stopped)
                {
                    return;
                }
                if (!pictures.isEmpty())
                {
                    request = pictures.poll();
                }
                else
                {
                    request = pending;
                    pending = null;
                }
            }

            try
            {
                if (renderer == null || rendered != request.mesh)
                {
                    renderer = new MeshRenderer(request.mesh);
                    rendered = request.mesh;
                }
                renderer.useColors(request.colors);
                if (request.background == DEFAULT_BACKGROUND)
                {
                    renderer.setBackground(MeshRenderer.BACKGROUND_TOP, MeshRenderer.BACKGROUND_BOTTOM);
                }
                else
                {
                    renderer.setBackground(request.background, request.background);
                }
                if (request.then != null)
                {
                    BufferedImage image = renderer.render(request.camera, request.width, request.height);
                    Consumer<BufferedImage> then = request.then;
                    SwingUtilities.invokeLater(() -> then.accept(image));
                    continue;
                }

                BufferedImage image = spareImage(request.width, request.height);
                long started = System.nanoTime();
                // A full picture gives way as soon as the model moves again; a moving one is always finished
                boolean done = renderer.render(request.camera, image,
                    request.full ? this::newerWanted : () -> false);
                long ms = (System.nanoTime() - started) / 1_000_000;
                if (!done)
                {
                    spare.add(image);
                    continue;
                }
                if (!request.full)
                {
                    learn(ms);
                }
                synchronized (lock)
                {
                    if (front != null)
                    {
                        spare.add(front);
                    }
                    front = image;
                    frontFull = request.full;
                }
                repaint();
            }
            catch (RuntimeException e)
            {
                log.warn("Couldn't draw {}", mesh.name, e);
            }
        }
    }

    private boolean newerWanted()
    {
        synchronized (lock)
        {
            return pending != null || stopped;
        }
    }

    /** Draws moving pictures smaller when they are slow and bigger when they are quick. */
    private void learn(long ms)
    {
        int scale = movingScale;
        if (ms > SLOW_MS && scale < MOVING_SCALES.length - 1)
        {
            movingScale = scale + 1;
        }
        else if (ms < FAST_MS && scale > 0 && MOVING_SCALES[scale - 1] <= screenScaleSeen)
        {
            movingScale = scale - 1;
        }
    }

    /** A picture of this size to draw into, one kept from before if there is one. */
    private BufferedImage spareImage(int width, int height)
    {
        for (int i = 0; i < spare.size(); i++)
        {
            BufferedImage image = spare.get(i);
            if (image.getWidth() == width && image.getHeight() == height)
            {
                spare.remove(i);
                return image;
            }
        }
        while (spare.size() > 2)
        {
            spare.remove(0);
        }
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        int width = getWidth();
        int height = getHeight();
        synchronized (lock)
        {
            if (front == null)
            {
                if (background == DEFAULT_BACKGROUND)
                {
                    g2.setPaint(new GradientPaint(0, 0, new Color(MeshRenderer.BACKGROUND_TOP),
                        0, height, new Color(MeshRenderer.BACKGROUND_BOTTOM)));
                }
                else
                {
                    g2.setColor(new Color(background));
                }
                g2.fillRect(0, 0, width, height);
            }
            else
            {
                if (!frontFull)
                {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                }
                g2.drawImage(front, 0, 0, width, height, null);
            }
        }
        paintText(g2, width, height);
        g2.dispose();
    }

    /** The line about the model in the top left corner, and the line of help along the bottom, both quiet. */
    private void paintText(Graphics2D g, int width, int height)
    {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(FontManager.getRunescapeSmallFont());
        FontMetrics metrics = g.getFontMetrics();
        boolean light = light(background);
        g.setColor(light ? INFO_COLOR_ON_LIGHT : INFO_COLOR);
        g.drawString(info, 12, 10 + metrics.getAscent());
        if (metrics.stringWidth(HINT) <= width - 20)
        {
            g.setColor(light ? HINT_COLOR_ON_LIGHT : HINT_COLOR);
            g.drawString(HINT, (width - metrics.stringWidth(HINT)) / 2, height - 10 - metrics.getDescent());
        }
    }

    /**
     * A picture asked for: the mesh, the view, the colors, the background, its size in pixels, and whether it is a
     * full one or a picture to hand over.
     */
    private static final class Request
    {
        final Mesh mesh;
        final OrbitCamera camera;
        final int[] colors;
        final int background;
        final int width;
        final int height;
        final boolean full;
        final Consumer<BufferedImage> then;

        Request(Mesh mesh, OrbitCamera camera, int[] colors, int background, int width, int height, boolean full,
            Consumer<BufferedImage> then)
        {
            this.mesh = mesh;
            this.camera = camera;
            this.colors = colors;
            this.background = background;
            this.width = width;
            this.height = height;
            this.full = full;
            this.then = then;
        }
    }
}

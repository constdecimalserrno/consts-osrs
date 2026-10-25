package com.constsmodels;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * What a viewer window holds: the picture of the model, with a line about it in the corner, and a bar along the
 * bottom with what to do with it: copy the folder's path, pick the background, save a picture, and Save, in the format
 * picked beside it.
 *
 * <p>A click on the picture picks the piece of the model under it ({@link Pieces}), lit up in orange; Remove piece, or
 * Delete, takes it out of the model, for the view and Save alike, and Bring back puts every piece taken out back.
 * Those two show in the picture's top right corner while there's a piece picked or taken out.
 *
 * <p>A captured actor's animations ({@link Clip}) get a strip on the left of the bar: pick one, play or pause it
 * (Space), step a frame back or on (Left, Right), and its frame number. It opens on the first one, playing. A step
 * pauses and goes round; playing goes on from the clip's loop frame after its last, at the game's pace.
 */
final class ViewerPanel extends JPanel
{
    private static final int BAR_HEIGHT = 44;

    private final Mesh mesh;
    private final ViewerActions actions;
    private final ViewerCanvas canvas;
    private final JComboBox<ExportFormat> format = new JComboBox<>(ExportFormat.values());
    private final JButton save = new JButton("Save");
    private BackgroundButton background;
    private JComponent bar;

    // The model as it's shown and saved: the mesh with any pieces taken out, and each of its triangles' number in
    // the mesh
    private Mesh model;
    private int[] modelFaces;
    // The mesh's pieces, found when one is first picked; those taken out; the one picked, or -1
    private Pieces pieces;
    private boolean[] removed;
    private int picked = -1;
    private final JButton remove = quietButton("Remove piece",
        "Take the piece you picked out of the model, for the view and Save (Delete)");
    private final JButton restore = quietButton("Bring back", "Put back every piece you took out");

    // The animation shown, by its place in the mesh's clips, its frame, and whether it plays; the strip's dropdown,
    // Play/Pause and frame number, all null for a mesh without animations
    private int clip;
    private int frame;
    private boolean playing = true;
    private JComboBox<String> animations;
    private JButton play;
    private JLabel frameLabel;
    // Each frame's turn ends here, while the window is open and the animation plays
    private final Timer timer = new Timer(0, e -> advance());
    private boolean started;

    ViewerPanel(Mesh mesh, ViewerActions actions)
    {
        super(new BorderLayout());
        this.mesh = mesh;
        this.actions = actions;
        model = mesh;
        modelFaces = everyFace(mesh);
        canvas = new ViewerCanvas(mesh);
        canvas.setBackgroundColor(actions.background());
        canvas.onPick(this::picked);
        canvas.setCorner(pieceButtons());
        timer.setRepeats(false);

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(canvas, BorderLayout.CENTER);
        bar = bar();
        add(bar, BorderLayout.SOUTH);
        refresh();

        // Cmd on a Mac, Ctrl elsewhere; a headless JVM (tests over SSH) has no menu key to ask for, so Ctrl
        int menuKey = GraphicsEnvironment.isHeadless() ? InputEvent.CTRL_DOWN_MASK
            : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuKey), "save");
        getActionMap().put("save", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                save();
            }
        });
        key(KeyEvent.VK_DELETE, "remove piece", this::removePicked);
        key(KeyEvent.VK_BACK_SPACE, "remove piece", this::removePicked);
        key(KeyEvent.VK_ESCAPE, "let go", () -> picked(-1));
        if (animations != null)
        {
            key(KeyEvent.VK_SPACE, "play", this::togglePlay);
            key(KeyEvent.VK_LEFT, "back a frame", () -> step(-1));
            key(KeyEvent.VK_RIGHT, "on a frame", () -> step(1));
        }
    }

    /** Starts drawing, and the animation if it plays, once the window shows. */
    void start()
    {
        canvas.start();
        started = true;
        schedule();
    }

    /** Stops drawing and the animation, once the window has closed. */
    void stop()
    {
        started = false;
        timer.stop();
        canvas.stop();
    }

    private void key(int code, String name, Runnable action)
    {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(code, 0), name);
        getActionMap().put(name, new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                action.run();
            }
        });
    }

    /** Remove piece and Bring back, for the picture's top right corner; each shows only when there's call for it. */
    private JComponent pieceButtons()
    {
        remove.addActionListener(e -> removePicked());
        restore.addActionListener(e -> restoreAll());
        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        corner.setOpaque(false);
        corner.add(restore);
        corner.add(remove);
        updatePieceButtons();
        return corner;
    }

    private JComponent bar()
    {
        JButton copy = quietButton("Copy path", "Copy the path of the folder files are saved in");
        copy.addActionListener(e -> actions.copyPath());

        JButton image = quietButton("Save image", "Save this view as a PNG picture, on its background");
        image.addActionListener(e -> canvas.picture(picture -> actions.saveImage(mesh, picture)));

        background = new BackgroundButton(canvas.backgroundColor(), rgb ->
        {
            canvas.setBackgroundColor(rgb);
            actions.setBackground(rgb);
        });

        format.setSelectedItem(actions.defaultFormat());
        format.setFont(FontManager.getRunescapeFont());
        format.setFocusable(false);
        format.setPreferredSize(new Dimension(76, 28));
        format.setToolTipText(describe((ExportFormat) format.getSelectedItem()));
        format.addActionListener(e -> format.setToolTipText(describe((ExportFormat) format.getSelectedItem())));

        save.setFont(FontManager.getRunescapeBoldFont());
        save.setBackground(ColorScheme.BRAND_ORANGE);
        save.setForeground(ColorScheme.DARKER_GRAY_COLOR);
        save.setFocusPainted(false);
        save.setPreferredSize(new Dimension(84, 28));
        save.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        save.setToolTipText("Save the model as you see it, any pieces you took out left out");
        save.addActionListener(e -> save());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(copy);
        buttons.add(background);
        buttons.add(image);
        buttons.add(gap());
        buttons.add(format);
        buttons.add(save);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.BORDER_COLOR),
            new EmptyBorder((BAR_HEIGHT - 28) / 2, 12, (BAR_HEIGHT - 28) / 2 - 1, 8)));
        bar.add(buttons, BorderLayout.EAST);
        if (!mesh.clips.isEmpty())
        {
            bar.add(animationStrip(), BorderLayout.WEST);
        }
        return bar;
    }

    /** The animation picked, back a frame, Play/Pause, on a frame, and the frame number. */
    private JComponent animationStrip()
    {
        animations = new JComboBox<>(mesh.clips.stream().map(c -> c.label).toArray(String[]::new));
        animations.setFont(FontManager.getRunescapeFont());
        animations.setFocusable(false);
        animations.setToolTipText("The animation shown");
        animations.addActionListener(e -> choose(animations.getSelectedIndex()));
        animations.setPreferredSize(new Dimension(animations.getPreferredSize().width, 28));

        JButton back = quietButton("<", "Back a frame (Left)");
        back.addActionListener(e -> step(-1));
        play = quietButton("Pause", "Play or pause the animation (Space)");
        // As wide as its longer word, so it doesn't jump as it flips
        play.setPreferredSize(new Dimension(play.getPreferredSize().width + 8, 28));
        play.addActionListener(e -> togglePlay());
        JButton on = quietButton(">", "On a frame (Right)");
        on.addActionListener(e -> step(1));

        frameLabel = new JLabel();
        frameLabel.setFont(FontManager.getRunescapeSmallFont());
        frameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        strip.setOpaque(false);
        strip.add(animations);
        strip.add(back);
        strip.add(play);
        strip.add(on);
        strip.add(frameLabel);
        updateStrip();
        return strip;
    }

    /** Shows another animation, from its first frame, playing or paused as before. */
    private void choose(int index)
    {
        if (index < 0 || index == clip && frame == 0)
        {
            return;
        }
        clip = index;
        frame = 0;
        frameChanged();
    }

    /** One frame back (-1) or on (1), going round, and pauses. */
    void step(int by)
    {
        if (animations == null)
        {
            return;
        }
        int count = mesh.clips.get(clip).frameCount();
        frame = Math.floorMod(frame + by, count);
        playing = false;
        frameChanged();
    }

    void togglePlay()
    {
        if (animations == null)
        {
            return;
        }
        playing = !playing;
        updateStrip();
        schedule();
    }

    /** The frame after this one while playing: the next, or after the last the clip's loop frame. */
    static int next(Clip clip, int frame)
    {
        return frame + 1 < clip.frameCount() ? frame + 1 : clip.loopFrom;
    }

    /** How long a frame shows while playing, in milliseconds: its ticks, 20 ms each. */
    static int delay(Clip clip, int frame)
    {
        return clip.ticks[frame] * Clip.TICK_MS;
    }

    /** A frame's turn is up: on to the next one. */
    private void advance()
    {
        if (playing && started)
        {
            frame = next(mesh.clips.get(clip), frame);
            frameChanged();
        }
    }

    private void frameChanged()
    {
        updateStrip();
        showView();
        schedule();
    }

    /** Times the frame shown, while the window is open and the animation plays. */
    private void schedule()
    {
        timer.stop();
        if (started && playing && animations != null)
        {
            timer.setInitialDelay(delay(mesh.clips.get(clip), frame));
            timer.start();
        }
    }

    private void updateStrip()
    {
        if (animations == null)
        {
            return;
        }
        play.setText(playing ? "Pause" : "Play");
        frameLabel.setText(frameText());
    }

    /** "Frame 3/12": the frame shown, counted from 1, of the animation's. */
    String frameText()
    {
        return "Frame " + (frame + 1) + "/" + mesh.clips.get(clip).frameCount();
    }

    boolean playing()
    {
        return playing;
    }

    /** The animation dropdown, or null for a mesh without animations. */
    JComboBox<String> animations()
    {
        return animations;
    }

    /** What the picture shows: the model, at the animation's frame if it has any. */
    Mesh shown()
    {
        if (model.clips.isEmpty())
        {
            return model;
        }
        Clip shownClip = model.clips.get(clip);
        return new Mesh(model.name, shownClip.x[frame], shownClip.y[frame], shownClip.z[frame],
            model.a, model.b, model.c, model.color);
    }

    private static JButton quietButton(String text, String tip)
    {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeFont());
        button.setForeground(ColorScheme.TEXT_COLOR);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setFocusPainted(false);
        fit(button);
        button.setToolTipText(tip);
        return button;
    }

    /** As wide as the button's text needs, and as tall as the bar's controls. */
    private static void fit(JButton button)
    {
        button.setPreferredSize(null);
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, 28));
    }

    private static JComponent gap()
    {
        JPanel gap = new JPanel();
        gap.setOpaque(false);
        gap.setPreferredSize(new Dimension(6, 28));
        return gap;
    }

    /** Saves the model as the view shows it, without any pieces taken out. */
    private void save()
    {
        actions.save(model, (ExportFormat) format.getSelectedItem());
    }

    /** The mesh's pieces, found the first time they're needed. */
    private Pieces pieces()
    {
        if (pieces == null)
        {
            pieces = Pieces.of(mesh);
            removed = new boolean[pieces.count];
        }
        return pieces;
    }

    /**
     * A click on the picture, on a triangle of the model shown or -1 for none: picks the piece that triangle is part
     * of, or lets go of the piece picked if it's that one again or nothing.
     */
    void picked(int face)
    {
        int piece = face >= 0 && face < modelFaces.length ? pieces().piece[modelFaces[face]] : -1;
        int was = picked;
        picked = piece == picked ? -1 : piece;
        if (picked != was)
        {
            updatePieceButtons();
            showView();
        }
    }

    /** Takes the piece picked out of the model, unless it's all that's left. */
    void removePicked()
    {
        if (picked < 0 || pieces.count - removedCount() <= 1)
        {
            return;
        }
        removed[picked] = true;
        picked = -1;
        modelChanged();
    }

    /** Puts every piece taken out back. */
    void restoreAll()
    {
        if (removedCount() == 0)
        {
            return;
        }
        Arrays.fill(removed, false);
        picked = -1;
        modelChanged();
    }

    private int removedCount()
    {
        int count = 0;
        for (int p = 0; removed != null && p < removed.length; p++)
        {
            count += removed[p] ? 1 : 0;
        }
        return count;
    }

    /** Pieces were taken out or put back: the model is made again from what's left. */
    private void modelChanged()
    {
        if (removedCount() == 0)
        {
            model = mesh;
            modelFaces = everyFace(mesh);
        }
        else
        {
            modelFaces = pieces.kept(removed);
            model = Pieces.only(mesh, modelFaces);
        }
        updatePieceButtons();
        showView();
    }

    private void updatePieceButtons()
    {
        int out = removedCount();
        remove.setVisible(picked >= 0);
        remove.setEnabled(picked >= 0 && pieces.count - out > 1);
        restore.setVisible(out > 0);
        restore.setText(out == 1 ? "Bring back 1 piece" : "Bring back " + out + " pieces");
        fit(restore);
        canvas.revalidate();
        canvas.repaint();
    }

    private static int[] everyFace(Mesh mesh)
    {
        int[] faces = new int[mesh.faceCount()];
        for (int f = 0; f < faces.length; f++)
        {
            faces[f] = f;
        }
        return faces;
    }

    /** Puts the model into the picture, the piece picked lit up, and the line about it in the corner. */
    private void showView()
    {
        canvas.show(shown(), lightPicked());
        canvas.setInfo(sizeText(model));
    }

    /** The model's colors with the piece picked lit up, or null for its own when none is. */
    private int[] lightPicked()
    {
        if (picked < 0)
        {
            return null;
        }
        int[] out = model.color.clone();
        for (int f = 0; f < out.length; f++)
        {
            if (pieces.piece[modelFaces[f]] == picked)
            {
                out[f] = lit(out[f]);
            }
        }
        return out;
    }

    /** A color lit up, as a picked piece is: most of the way to the brand's orange. */
    static int lit(int rgb)
    {
        int orange = ColorScheme.BRAND_ORANGE.getRGB();
        int r = ((rgb >> 16 & 0xFF) * 2 + (orange >> 16 & 0xFF) * 3) / 5;
        int g = ((rgb >> 8 & 0xFF) * 2 + (orange >> 8 & 0xFF) * 3) / 5;
        int b = ((rgb & 0xFF) * 2 + (orange & 0xFF) * 3) / 5;
        return r << 16 | g << 8 | b;
    }

    /** How wide the window can get before the bar's controls would overlap. */
    int minimumWidth()
    {
        return bar.getPreferredSize().width;
    }

    ViewerCanvas canvas()
    {
        return canvas;
    }

    /** The model as shown and saved, any pieces taken out. */
    Mesh model()
    {
        return model;
    }

    /** Reads the background again, which may have been changed in another viewer. */
    void refresh()
    {
        int rgb = actions.background();
        canvas.setBackgroundColor(rgb);
        if (background != null)
        {
            background.setBackgroundColor(rgb);
        }
        showView();
    }

    /** "12,345 triangles": how many the model has, and its animations if it has any ("12 triangles, 3 animations"). */
    static String sizeText(Mesh mesh)
    {
        int faces = mesh.faceCount();
        int clips = mesh.clips.size();
        return String.format("%,d %s", faces, faces == 1 ? "triangle" : "triangles")
            + (clips == 0 ? "" : ", " + clips + (clips == 1 ? " animation" : " animations"));
    }

    private static String describe(ExportFormat format)
    {
        if (format == null)
        {
            return null;
        }
        switch (format)
        {
            case OBJ:
                return "OBJ: shape and colors, for Blender and other 3D software";
            case GLB:
                return "GLB (glTF): shape, colors and any animations, for Blender and other 3D software";
            default:
                return "PLY: shape with a color on every corner";
        }
    }
}

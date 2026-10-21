package com.constsmodels;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a viewer window holds: the picture of the model, with a line about it in the corner, and a bar along the
 * bottom. On the left of the bar, Game or Print, the print's base and its colors; on the right, what to do with it:
 * open the folder, pick the background, save a picture, and Save, in the format picked beside it.
 *
 * <p>Game shows the model as the game has it, and Save saves that. Print shows it made printable, one closed solid
 * on its base (if the Base switch is on) in the filaments' colors ({@link Printable}), and Save saves exactly that.
 * The print is made off the Swing thread the first time it's shown, which takes a few seconds, and again only when
 * the print size changes; until it's ready the view shows the model in the filaments' colors and Save waits. Turning
 * the base on or off only stands the same solid on the plate again, which is quick.
 *
 * <p>A click on the picture picks the piece of the model under it ({@link Pieces}), lit up in orange, in either view;
 * Remove piece, or Delete, takes it out of the model, for Game, Print and Save alike, and Bring back puts every piece
 * taken out back. Those two show in the picture's top right corner while there's a piece picked or taken out.
 */
final class ViewerPanel extends JPanel
{
    private static final Logger log = LoggerFactory.getLogger(ViewerPanel.class);

    private static final int BAR_HEIGHT = 44;

    private final Mesh mesh;
    private final ViewerActions actions;
    private final ViewerCanvas canvas;
    private FilamentControls filaments;
    private final JComboBox<ExportFormat> format = new JComboBox<>(ExportFormat.values());
    private final JButton save = new JButton("Save");
    private BackgroundButton background;

    // The print once made; the settings it was last tried with; whether it's being made; why it couldn't be
    private Printable printable;
    private PrintSettings tried;
    private boolean making;
    private String problem;
    // Which print was asked for last, so an older one finishing late is let go; how far the one being made has got
    private int asked;
    private double madeShare;
    // The Add a base setting as last seen, so the Base switch follows it when it changes but is the viewer's own
    // otherwise
    private boolean baseSetting;

    // The model as it's shown and saved: the mesh with any pieces taken out, and each of its triangles' number in
    // the mesh; the model the print was last tried with; the mesh the picture shows
    private Mesh model;
    private int[] modelFaces;
    private Mesh triedModel;
    private Mesh onCanvas;
    // The mesh's pieces, found when one is first picked; those taken out; the one picked, or -1
    private Pieces pieces;
    private boolean[] removed;
    private int picked = -1;
    private final JButton remove = quietButton("Remove piece",
        "Take the piece you picked out of the model, for Game, Print and Save (Delete)");
    private final JButton restore = quietButton("Bring back", "Put back every piece you took out");

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
        PrintSettings settings = actions.printSettings();
        baseSetting = settings != null && settings.base;
        filaments = new FilamentControls(mesh, settings == null ? 4 : settings.maxColors, baseSetting,
            this::viewChanged);

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(canvas, BorderLayout.CENTER);
        add(bar(), BorderLayout.SOUTH);
        refresh();

        int menuKey = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
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
        JButton folder = quietButton("Open folder", "Open the folder files are saved in");
        folder.addActionListener(e -> actions.openFolder());

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
        save.addActionListener(e -> save());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(folder);
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
        bar.add(filaments, BorderLayout.WEST);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
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

    /** Saves what the view shows: the game's model, or the print once it's made. */
    private void save()
    {
        ExportFormat chosen = (ExportFormat) format.getSelectedItem();
        PrintColors colors = filaments.colors();
        if (!filaments.showsPrint())
        {
            actions.save(model, chosen, colors == null || model == mesh ? colors : colors.forFaces(modelFaces),
                printSettings());
        }
        else if (printable != null && printable.model == model && !printable.isEmpty() && colors != null)
        {
            actions.savePrint(printable, chosen, colors);
        }
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
     * A click on the picture, on a triangle of what it shows or -1 for none: picks the piece that triangle is part
     * of, or lets go of the piece picked if it's that one again or nothing.
     */
    void picked(int face)
    {
        int piece = -1;
        if (face >= 0 && (onCanvas == model || printable != null && onCanvas == printable.shown))
        {
            int modelFace = onCanvas == model ? face : printable.modelFace(face);
            piece = modelFace < 0 ? -1 : pieces().piece[modelFaces[modelFace]];
        }
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

    /** Pieces were taken out or put back: the model is made again, and its print. */
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
        // The print was of the model as it was: it goes, along with any being made
        asked++;
        printable = null;
        making = false;
        problem = null;
        tried = null;
        triedModel = null;
        if (filaments.showsPrint())
        {
            makePrintIfNeeded(printSettings());
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

    /** Game or Print, or the filaments, changed. */
    private void viewChanged()
    {
        // The first merge of a small model is done before the controls are
        if (filaments == null)
        {
            return;
        }
        if (filaments.showsPrint())
        {
            makePrintIfNeeded(printSettings());
        }
        showView();
    }

    /** The settings the print is made with: the plugin's, with the base as the Base switch has it. */
    private PrintSettings printSettings()
    {
        PrintSettings settings = actions.printSettings();
        return settings == null ? null : new PrintSettings(settings.sizeMm, filaments.base(), settings.maxColors);
    }

    /**
     * Makes the print, unless it was already made, or tried, at this size and base. A print of the same size only
     * changing its base keeps its solid, so that's done at once, or as soon as the solid being made is ready.
     */
    private void makePrintIfNeeded(PrintSettings settings)
    {
        if (settings == null || settings.sameShape(tried) && triedModel == model)
        {
            return;
        }
        if (tried != null && triedModel == model && tried.sizeMm == settings.sizeMm
            && (making || printable != null || problem != null))
        {
            tried = settings;
            if (printable != null)
            {
                printable = printable.placed(settings);
            }
            return;
        }
        int ticket = ++asked;
        tried = settings;
        triedModel = model;
        Mesh target = model;
        making = true;
        madeShare = 0;
        printable = null;
        problem = null;
        Thread thread = new Thread(() ->
        {
            // A hundred steps at most reach the Swing thread
            double[] told = {0};
            PrintProgress progress = share ->
            {
                if (share >= told[0] + 0.01 || share >= 1 && told[0] < 1)
                {
                    told[0] = share;
                    SwingUtilities.invokeLater(() -> madeSoFar(ticket, share));
                }
            };
            Printable made = null;
            String failed = null;
            try
            {
                made = Printable.of(target, settings, progress);
                if (made.isEmpty())
                {
                    failed = "it's too thin to hold a wall";
                }
            }
            catch (RuntimeException | OutOfMemoryError e)
            {
                log.warn("Couldn't make {} printable", mesh.name, e);
                failed = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
            Printable result = made;
            String why = failed;
            SwingUtilities.invokeLater(() ->
            {
                if (ticket != asked)
                {
                    return;
                }
                making = false;
                // Stood on the plate again if the base was turned on or off while it was being made
                printable = why == null ? result.placed(tried) : null;
                problem = why;
                showView();
            });
        }, "consts-Models print");
        thread.setDaemon(true);
        thread.start();
    }

    /** How far the print asked for last has got, for the bar that fills while it's made. */
    private void madeSoFar(int ticket, double share)
    {
        if (ticket != asked || !making)
        {
            return;
        }
        madeShare = Math.max(madeShare, share);
        if (filaments.showsPrint())
        {
            canvas.setProgress(madeShare);
        }
    }

    /**
     * Puts what the view is on into the picture, the line about it in the corner, the bar while a print is made, and
     * Save in step with it.
     */
    private void showView()
    {
        boolean print = filaments.showsPrint();
        PrintColors colors = filaments.colors();
        if (print && printable != null && colors != null)
        {
            int[] rgb = printable.colors(colors);
            for (int t = 0; picked >= 0 && t < rgb.length; t++)
            {
                int face = printable.modelFace(t);
                if (face >= 0 && pieces.piece[modelFaces[face]] == picked)
                {
                    rgb[t] = lit(rgb[t]);
                }
            }
            show(printable.shown, rgb);
        }
        else if (print)
        {
            // Until the print is ready: the model in the filaments' colors
            int[] all = filaments.shownColors();
            int[] rgb = null;
            if (all != null)
            {
                rgb = new int[model.faceCount()];
                for (int f = 0; f < rgb.length; f++)
                {
                    rgb[f] = all[modelFaces[f]];
                }
            }
            show(model, lightPicked(rgb));
        }
        else
        {
            show(model, lightPicked(null));
        }
        // While the print is made, the bar says so and the corner what it will be
        canvas.setInfo(print && !making ? printText(printable, false, problem)
            : sizeText(model, actions.printSettings()));
        canvas.setProgress(print && making ? madeShare : -1);

        boolean ready = !print || printable != null && colors != null;
        save.setEnabled(ready);
        save.setToolTipText(!print ? "Save the model as the game has it, in the format picked beside it"
            : ready ? "Save the print as you see it, ready for a slicer"
            : making ? "Making it printable…" : "There's no print to save");
    }

    private void show(Mesh shown, int[] rgb)
    {
        onCanvas = shown;
        canvas.show(shown, rgb);
    }

    /** The model's colors, these or its own, with the piece picked lit up; null for its own when none is. */
    private int[] lightPicked(int[] rgb)
    {
        if (picked < 0)
        {
            return rgb;
        }
        int[] out = (rgb != null ? rgb : model.color).clone();
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

    ViewerCanvas canvas()
    {
        return canvas;
    }

    /** The model as shown and saved, any pieces taken out. */
    Mesh model()
    {
        return model;
    }

    /**
     * Reads the settings again, which may have changed: the print's size, the background, and the base, which the
     * Base switch takes up only if the setting itself changed.
     */
    void refresh()
    {
        int rgb = actions.background();
        canvas.setBackgroundColor(rgb);
        if (background != null)
        {
            background.setBackgroundColor(rgb);
        }
        PrintSettings settings = actions.printSettings();
        if (settings != null && settings.base != baseSetting)
        {
            baseSetting = settings.base;
            filaments.setBase(baseSetting);
        }
        if (filaments.showsPrint())
        {
            makePrintIfNeeded(printSettings());
        }
        showView();
    }

    /** The print as made so far, or null. */
    Printable printable()
    {
        return printable;
    }

    FilamentControls filaments()
    {
        return filaments;
    }

    /**
     * The line about the print: "Making it printable…" while it's being made, then "8,214 triangles  ·  one
     * closed solid  ·  100 × 62 × 80 mm", base and all, or why it couldn't be made.
     */
    static String printText(Printable printable, boolean making, String problem)
    {
        if (making)
        {
            return "Making it printable\u2026";
        }
        if (problem != null)
        {
            return "Couldn't make it printable: " + problem;
        }
        if (printable == null)
        {
            return "";
        }
        Mesh mesh = printable.print.mesh;
        int faces = mesh.faceCount();
        float[] box = mesh.bounds();
        int solids = printable.solids();
        return String.format("%,d %s", faces, faces == 1 ? "triangle" : "triangles") + "  \u00b7  "
            + (solids == 1 ? "one closed solid" : solids + " closed solids") + "  \u00b7  "
            + millimetres(box[3] - box[0]) + " \u00d7 " + millimetres(box[4] - box[1]) + " \u00d7 "
            + millimetres(box[5] - box[2]) + " mm";
    }

    /**
     * "12,345 triangles  ·  prints at 100 × 62 × 80 mm": how many triangles, and the print's width, depth and
     * height.
     */
    static String sizeText(Mesh mesh, PrintSettings settings)
    {
        int faces = mesh.faceCount();
        String text = String.format("%,d %s", faces, faces == 1 ? "triangle" : "triangles");
        float[] box = mesh.bounds();
        float wide = box[3] - box[0];
        float deep = box[4] - box[1];
        float high = box[5] - box[2];
        float longest = Math.max(wide, Math.max(deep, high));
        if (settings == null || settings.sizeMm <= 0 || longest <= 0)
        {
            return text;
        }
        float scale = settings.sizeMm / longest;
        return text + "  \u00b7  prints at " + millimetres(wide * scale) + " \u00d7 " + millimetres(deep * scale)
            + " \u00d7 " + millimetres(high * scale) + " mm";
    }

    /** Whole millimetres, or tenths below ten. */
    private static String millimetres(float mm)
    {
        return mm < 9.95f ? String.format("%.1f", mm) : String.format("%.0f", mm);
    }

    private static String describe(ExportFormat format)
    {
        if (format == null)
        {
            return null;
        }
        switch (format)
        {
            case STL:
                return "STL: shape only, for any slicer";
            case THREE_MF:
                return "3MF: shape and colors, for multicolor printers";
            case OBJ:
                return "OBJ: shape and colors, for Blender and other 3D software";
            default:
                return "PLY: shape with a color on every corner";
        }
    }
}

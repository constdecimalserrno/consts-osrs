package com.constsmodels;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * What a viewer window holds: the picture of the model, with a line about it in the corner (its triangles and how
 * big it prints), and a bar along the bottom. On the left of the bar, the print's colors; on the right, what to do
 * with the model: open the folder, save a picture, and Save, in the format picked beside it.
 */
final class ViewerPanel extends JPanel
{
    private static final int BAR_HEIGHT = 44;

    private final Mesh mesh;
    private final ViewerActions actions;
    private final ViewerCanvas canvas;
    private FilamentControls filaments;
    private final JComboBox<ExportFormat> format = new JComboBox<>(ExportFormat.values());
    private final JCheckBox solid = new JCheckBox("Make solid");

    ViewerPanel(Mesh mesh, ViewerActions actions)
    {
        super(new BorderLayout());
        this.mesh = mesh;
        this.actions = actions;
        canvas = new ViewerCanvas(mesh);
        PrintSettings settings = actions.printSettings();
        filaments = new FilamentControls(mesh, settings == null ? 4 : settings.maxColors, this::colorsChanged);

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
    }

    private JComponent bar()
    {
        JButton folder = quietButton("Open folder", "Open the folder files are saved in");
        folder.addActionListener(e -> actions.openFolder());

        JButton image = quietButton("Save image", "Save this view as a PNG picture");
        image.addActionListener(e -> canvas.picture(picture -> actions.saveImage(mesh, picture)));

        format.setSelectedItem(actions.defaultFormat());
        format.setFont(FontManager.getRunescapeFont());
        format.setFocusable(false);
        format.setPreferredSize(new Dimension(76, 28));
        format.setToolTipText(describe((ExportFormat) format.getSelectedItem()));
        format.addActionListener(e ->
        {
            format.setToolTipText(describe((ExportFormat) format.getSelectedItem()));
            solid.setEnabled(printable((ExportFormat) format.getSelectedItem()));
        });

        solid.setSelected(actions.printSettings().solid);
        solid.setEnabled(printable((ExportFormat) format.getSelectedItem()));
        solid.setFont(FontManager.getRunescapeFont());
        solid.setForeground(ColorScheme.TEXT_COLOR);
        solid.setOpaque(false);
        solid.setFocusable(false);
        solid.setToolTipText("<html>Save STL and 3MF as one closed solid that prints as is:<br>"
            + "thin parts get a 1 mm wall and small gaps close.<br>Takes a few seconds; detail under about 2 mm rounds off.</html>");

        JButton save = new JButton("Save");
        save.setFont(FontManager.getRunescapeBoldFont());
        save.setBackground(ColorScheme.BRAND_ORANGE);
        save.setForeground(ColorScheme.DARKER_GRAY_COLOR);
        save.setFocusPainted(false);
        save.setPreferredSize(new Dimension(84, 28));
        save.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        save.setToolTipText("Save the model in the format picked beside it, a 3MF in the print's colors");
        save.addActionListener(e -> save());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(folder);
        buttons.add(image);
        buttons.add(gap());
        buttons.add(solid);
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
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, 28));
        button.setToolTipText(tip);
        return button;
    }

    /** Whether a format is one Make solid applies to: the ones made to print. */
    static boolean printable(ExportFormat format)
    {
        return format == ExportFormat.STL || format == ExportFormat.THREE_MF;
    }

    private static JComponent gap()
    {
        JPanel gap = new JPanel();
        gap.setOpaque(false);
        gap.setPreferredSize(new Dimension(6, 28));
        return gap;
    }

    private void save()
    {
        ExportFormat chosen = (ExportFormat) format.getSelectedItem();
        actions.save(mesh, chosen, filaments.colors(), solid.isSelected() && printable(chosen));
    }

    private void colorsChanged()
    {
        // The first merge of a small model is done before the controls are
        if (filaments != null)
        {
            canvas.showColors(filaments.shownColors());
        }
    }

    ViewerCanvas canvas()
    {
        return canvas;
    }

    /** Reads the print size again, which the settings may have changed. */
    void refresh()
    {
        PrintSettings settings = actions.printSettings();
        canvas.setInfo(sizeText(mesh, settings));
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

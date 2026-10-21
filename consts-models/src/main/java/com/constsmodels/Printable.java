package com.constsmodels;

/**
 * A model as it prints, what the viewer's Print view shows and saves: its holes capped ({@link ShellCaps}), so open
 * shells fill in, then rebuilt as one closed solid by {@link SolidRemesh}, scaled to the print size, stood flat on
 * the plate, on a base if asked. Its triangles take the filaments the viewer chose, each the one nearest its color.
 * Making one takes a few seconds, so it's made once and kept for as long as the print size stays the same; standing
 * it on a base or taking the base away is quick.
 */
final class Printable
{
    /** The game model it was made from. */
    final Mesh model;
    final PrintSettings settings;
    /** The closed solid, in the game's units. */
    final Mesh solid;
    /** For each of the solid's triangles, the model's triangle nearest it, whose color it took. */
    final int[] solidFrom;
    /** The solid ready to print, in millimetres. */
    final PrintPrep.Print print;
    /**
     * The print put back where the model is, in the game's units, base and all: what the Print view shows, in the
     * colors from {@link #colors}, so the view doesn't jump between the two.
     */
    final Mesh shown;

    private Printable(Mesh model, PrintSettings settings, Mesh solid, int[] solidFrom, PrintPrep.Print print)
    {
        this.model = model;
        this.settings = settings;
        this.solid = solid;
        this.solidFrom = solidFrom;
        this.print = print;
        this.shown = print.inModelUnits(print.mesh.color);
    }

    /** Makes the model printable; this is the slow part. */
    static Printable of(Mesh model, PrintSettings settings)
    {
        // Made with each triangle's number in place of its color, which carries through as the color does, so each
        // of the solid's triangles knows the model's triangle it came from; then colored by those
        ShellCaps.Capped capped = ShellCaps.cap(model);
        Mesh closed = capped.mesh;
        int[] number = new int[closed.faceCount()];
        for (int f = 0; f < number.length; f++)
        {
            number[f] = f;
        }
        Mesh numbered = SolidRemesh.makeSolid(
            new Mesh(closed.name, closed.x, closed.y, closed.z, closed.a, closed.b, closed.c, number), settings.sizeMm);
        int[] from = new int[numbered.faceCount()];
        int[] color = new int[numbered.faceCount()];
        for (int t = 0; t < from.length; t++)
        {
            int f = numbered.color[t];
            from[t] = f < number.length ? capped.from[f] : -1;
            color[t] = f < number.length ? closed.color[f] : 0xFFFFFF;
        }
        Mesh solid = new Mesh(numbered.name, numbered.x, numbered.y, numbered.z, numbered.a, numbered.b, numbered.c,
            color);
        return new Printable(model, settings, solid, from, PrintPrep.prepareSolid(solid, settings));
    }

    /**
     * The same solid stood on the plate by other settings of the same print size, on a base or not: quick, since the
     * solid is kept.
     */
    Printable placed(PrintSettings settings)
    {
        if (settings.sizeMm != this.settings.sizeMm)
        {
            throw new IllegalArgumentException("a print of another size needs a solid of its own");
        }
        return settings.sameShape(this.settings) ? this
            : new Printable(model, settings, solid, solidFrom, PrintPrep.prepareSolid(solid, settings));
    }

    /** The model's triangle a triangle of the print came from, or -1 for the base's. */
    int modelFace(int printFace)
    {
        int source = print.source[printFace];
        return source < 0 ? -1 : solidFrom[source];
    }

    /** Whether there's anything to print: a model too thin to hold a wall can come out empty. */
    boolean isEmpty()
    {
        return print.modelFaces == 0;
    }

    /** Each print triangle's filament: the one nearest its color, and the base's the darkest. */
    int[] filaments(PrintColors colors)
    {
        int[] onSolid = colors.filamentsFor(solid);
        int base = PrintPrep.darkest(colors.palette());
        int[] filaments = new int[print.mesh.faceCount()];
        for (int f = 0; f < filaments.length; f++)
        {
            int source = print.source[f];
            filaments[f] = source < 0 ? base : onSolid[source];
        }
        return filaments;
    }

    /** Each print triangle in its filament's color. */
    int[] colors(PrintColors colors)
    {
        int[] filaments = filaments(colors);
        int[] palette = colors.palette();
        int[] rgb = new int[filaments.length];
        for (int f = 0; f < rgb.length; f++)
        {
            rgb[f] = palette[filaments[f]];
        }
        return rgb;
    }

    /** The print in millimetres, in its filaments' colors: what OBJ and PLY save from the Print view. */
    Mesh colored(PrintColors colors)
    {
        Mesh mesh = print.mesh;
        return new Mesh(mesh.name, mesh.x, mesh.y, mesh.z, mesh.a, mesh.b, mesh.c, colors(colors));
    }
}

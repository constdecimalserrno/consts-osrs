package com.constsmodels;

import static com.constsmodels.TestMeshes.BLACK;
import static com.constsmodels.TestMeshes.BLUE;
import static com.constsmodels.TestMeshes.GREEN;
import static com.constsmodels.TestMeshes.RED;
import static com.constsmodels.TestMeshes.box;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ThreeMfWriterTest
{
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** A 3MF read back: its parts, each parsed as XML, and the model's mesh and colors. */
    static final class ThreeMf
    {
        List<String> entries = new ArrayList<>();
        Document types;
        Document rels;
        Document model;
        float[][] vertices;
        int[][] triangles;
        int[] filament;
        String[] paint;
        List<String> baseColors = new ArrayList<>();
        List<String> groupColors = new ArrayList<>();
    }

    static ThreeMf read(File file) throws Exception
    {
        ThreeMf read = new ThreeMf();
        try (ZipFile zip = new ZipFile(file))
        {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements())
            {
                read.entries.add(entries.nextElement().getName());
            }
            read.types = parse(zip, "[Content_Types].xml");
            read.rels = parse(zip, "_rels/.rels");
            read.model = parse(zip, ThreeMfWriter.MODEL_PATH);
        }

        Element model = read.model.getDocumentElement();
        NodeList vertices = model.getElementsByTagNameNS(ThreeMfWriter.CORE, "vertex");
        read.vertices = new float[vertices.getLength()][3];
        for (int i = 0; i < vertices.getLength(); i++)
        {
            Element v = (Element) vertices.item(i);
            read.vertices[i] = new float[]{
                Float.parseFloat(v.getAttribute("x")), Float.parseFloat(v.getAttribute("y")),
                Float.parseFloat(v.getAttribute("z")),
            };
        }
        NodeList triangles = model.getElementsByTagNameNS(ThreeMfWriter.CORE, "triangle");
        read.triangles = new int[triangles.getLength()][3];
        read.filament = new int[triangles.getLength()];
        read.paint = new String[triangles.getLength()];
        for (int i = 0; i < triangles.getLength(); i++)
        {
            Element t = (Element) triangles.item(i);
            read.triangles[i] = new int[]{
                Integer.parseInt(t.getAttribute("v1")), Integer.parseInt(t.getAttribute("v2")),
                Integer.parseInt(t.getAttribute("v3")),
            };
            assertEquals("2", t.getAttribute("pid"));
            read.filament[i] = Integer.parseInt(t.getAttribute("p1"));
            read.paint[i] = t.getAttributeNS(ThreeMfWriter.SLIC3RPE, "mmu_segmentation");
        }
        NodeList bases = model.getElementsByTagNameNS(ThreeMfWriter.CORE, "base");
        for (int i = 0; i < bases.getLength(); i++)
        {
            read.baseColors.add(((Element) bases.item(i)).getAttribute("displaycolor"));
        }
        NodeList colors = model.getElementsByTagNameNS(ThreeMfWriter.MATERIAL, "color");
        for (int i = 0; i < colors.getLength(); i++)
        {
            read.groupColors.add(((Element) colors.item(i)).getAttribute("color"));
        }
        return read;
    }

    private static Document parse(ZipFile zip, String name) throws Exception
    {
        ZipEntry entry = zip.getEntry(name);
        assertTrue(name, entry != null);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try (InputStream in = zip.getInputStream(entry))
        {
            return factory.newDocumentBuilder().parse(in);
        }
    }

    /** Two boxes, one with sides in four colors, one in two blues, for merging. */
    private static Mesh model()
    {
        MeshBuilder b = new MeshBuilder("Goblin & <friends>");
        TestMeshes.box(b, 0, 0, 0, 100, 60, 80, true, RED, GREEN, 0xC83030, BLUE);
        TestMeshes.box(b, 150, 0, 0, 200, 50, 50, true, BLUE, 0x2838B8);
        return b.build();
    }

    @Test
    public void aSaved3mfIsAValidPackageInMillimetresWithItsColorsMerged() throws Exception
    {
        File file = MeshExporter.export(model(), ExportFormat.THREE_MF, folder.getRoot(), new PrintSettings(90, true, 3));
        assertTrue(file.getName().endsWith(".3mf"));
        ThreeMf read = read(file);

        assertEquals(3, read.entries.size());
        assertEquals("[Content_Types].xml", read.entries.get(0));
        Set<String> extensions = new HashSet<>();
        NodeList defaults = read.types.getElementsByTagName("Default");
        for (int i = 0; i < defaults.getLength(); i++)
        {
            extensions.add(((Element) defaults.item(i)).getAttribute("Extension"));
        }
        assertTrue(extensions.contains("rels") && extensions.contains("model"));
        Element rel = (Element) read.rels.getElementsByTagName("Relationship").item(0);
        assertEquals("/" + ThreeMfWriter.MODEL_PATH, rel.getAttribute("Target"));
        assertEquals("http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel", rel.getAttribute("Type"));

        Element model = read.model.getDocumentElement();
        assertEquals(ThreeMfWriter.CORE, model.getNamespaceURI());
        assertEquals("model", model.getLocalName());
        assertEquals("millimeter", model.getAttribute("unit"));
        Element title = (Element) model.getElementsByTagNameNS(ThreeMfWriter.CORE, "metadata").item(0);
        assertEquals("Title", title.getAttribute("name"));
        assertEquals("Goblin & <friends>", title.getTextContent());
        Element object = (Element) model.getElementsByTagNameNS(ThreeMfWriter.CORE, "object").item(0);
        assertEquals("model", object.getAttribute("type"));
        assertEquals("1", object.getAttribute("pid"));
        assertEquals("0", object.getAttribute("pindex"));

        // No more colors than asked for, the same in the basematerials and the color group
        assertTrue(read.baseColors.size() <= 3);
        assertTrue(read.baseColors.size() >= 2);
        assertEquals(read.baseColors, read.groupColors);
        for (String color : read.baseColors)
        {
            assertTrue(color, color.matches("#[0-9A-F]{6}"));
        }

        // Two boxes and a base, welded: every triangle on real, distinct corners and a real filament
        assertEquals(36, read.triangles.length);
        assertEquals(24, read.vertices.length);
        for (int f = 0; f < read.triangles.length; f++)
        {
            int[] t = read.triangles[f];
            for (int v : t)
            {
                assertTrue(v >= 0 && v < read.vertices.length);
            }
            assertTrue(t[0] != t[1] && t[1] != t[2] && t[0] != t[2]);
            assertTrue(read.filament[f] >= 0 && read.filament[f] < read.baseColors.size());
            assertEquals(ThreeMfWriter.paintCode(read.filament[f] + 1), read.paint[f]);
        }

        float low = Float.MAX_VALUE;
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] v : read.vertices)
        {
            for (int i = 0; i < 3; i++)
            {
                min[i] = Math.min(min[i], v[i]);
                max[i] = Math.max(max[i], v[i]);
            }
            low = Math.min(low, v[2]);
        }
        assertEquals(0, low, 0f);
        assertEquals(90, Math.max(max[0] - min[0], Math.max(max[1] - min[1], max[2] - min[2])), 1e-3);
    }

    @Test
    public void theColorsChosenAreTheColorsSaved() throws Exception
    {
        Mesh model = model();
        PrintColors colors = PrintColors.of(model, 2).withColor(0, 0xFF8800).withColor(1, BLACK);
        File file = MeshExporter.export(model, ExportFormat.THREE_MF, folder.getRoot(), new PrintSettings(90, true, 9),
            colors);
        ThreeMf read = read(file);
        assertEquals(2, read.baseColors.size());
        assertEquals("#FF8800", read.baseColors.get(0));
        assertEquals("#101010", read.baseColors.get(1));

        // Each model triangle keeps its filament, and the base takes the darkest one
        PrintPrep.Print print = PrintPrep.prepare(model, new PrintSettings(90, true, 9));
        for (int f = 0; f < print.modelFaces; f++)
        {
            assertEquals(colors.filament(print.source[f]), read.filament[f]);
        }
        for (int f = print.modelFaces; f < read.filament.length; f++)
        {
            assertEquals(1, read.filament[f]);
        }
    }

    @Test
    public void oneColorNeedsNoPainting() throws Exception
    {
        File file = MeshExporter.export(box(0, 0, 0, 10, 10, 10, RED, GREEN), ExportFormat.THREE_MF,
            folder.getRoot(), new PrintSettings(50, false, 1));
        ThreeMf read = read(file);
        assertEquals(1, read.baseColors.size());
        for (String paint : read.paint)
        {
            assertEquals("", paint);
        }
    }

    @Test
    public void paintCodesReadBackAsTheirExtruders()
    {
        assertEquals("4", ThreeMfWriter.paintCode(1));
        assertEquals("8", ThreeMfWriter.paintCode(2));
        assertEquals("0C", ThreeMfWriter.paintCode(3));
        assertEquals("6C", ThreeMfWriter.paintCode(9));
        for (int extruder = 1; extruder <= ThreeMfWriter.MAX_PAINTED; extruder++)
        {
            assertEquals(extruder, decode(ThreeMfWriter.paintCode(extruder)));
        }
    }

    @Test
    public void namesAreSafeInXml() throws Exception
    {
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot; &apos;e&apos;", ThreeMfWriter.xml("a & b <c> \"d\" 'e'"));
        assertEquals("tab gone", ThreeMfWriter.xml("tab\u0001 gone"));
        assertEquals("#00FF0A", ThreeMfWriter.hex(0x00FF0A));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MeshBuilder b = new MeshBuilder("\u0000Odd\u0007 name😀");
        b.addTriangle(b.addVertex(0, 0, 0), b.addVertex(1, 0, 0), b.addVertex(0, 1, 0), RED);
        ThreeMfWriter.write(b.build(), new int[]{RED}, new int[]{0}, out);
        File file = folder.newFile("odd.3mf");
        java.nio.file.Files.write(file.toPath(), out.toByteArray());
        ThreeMf read = read(file);
        Element title = (Element) read.model.getElementsByTagNameNS(ThreeMfWriter.CORE, "metadata").item(0);
        assertEquals("Odd name😀", title.getTextContent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void everyTriangleNeedsARealFilament() throws IOException
    {
        MeshBuilder b = new MeshBuilder("Bad");
        b.addTriangle(b.addVertex(0, 0, 0), b.addVertex(1, 0, 0), b.addVertex(0, 1, 0), RED);
        ThreeMfWriter.write(b.build(), new int[]{RED}, new int[]{1}, new ByteArrayOutputStream());
    }

    @Test
    public void theStreamIsLeftForTheCallerToClose() throws IOException
    {
        boolean[] closed = {false};
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        {
            @Override
            public void close()
            {
                closed[0] = true;
            }
        };
        MeshBuilder b = new MeshBuilder("Open");
        b.addTriangle(b.addVertex(0, 0, 0), b.addVertex(1, 0, 0), b.addVertex(0, 1, 0), RED);
        ThreeMfWriter.write(b.build(), new int[]{RED}, new int[]{0}, out);
        assertFalse(closed[0]);
        assertTrue(out.size() > 0);
    }

    /** Reads a painting code the way PrusaSlicer does: hex digits last first, each four bits low first. */
    private static int decode(String code)
    {
        List<Boolean> bits = new ArrayList<>();
        for (int i = code.length() - 1; i >= 0; i--)
        {
            int digit = Character.digit(code.charAt(i), 16);
            for (int bit = 0; bit < 4; bit++)
            {
                bits.add((digit & (1 << bit)) != 0);
            }
        }
        int first = nibble(bits, 0);
        assertEquals("not split", 0, first & 0b11);
        int state = first >> 2;
        return state < 3 ? state : nibble(bits, 4) + 3;
    }

    private static int nibble(List<Boolean> bits, int from)
    {
        int n = 0;
        for (int i = 0; i < 4; i++)
        {
            n |= (bits.get(from + i) ? 1 : 0) << i;
        }
        return n;
    }
}

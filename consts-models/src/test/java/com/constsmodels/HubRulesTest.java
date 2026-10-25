package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * The Plugin Hub refuses a plugin that reaches files by their path or opens the computer's file browser: files go
 * through RuneLite's Filepath, from the plugin's own folder or a folder the player picks, and nothing starts another
 * program. It refuses reflection, KeyboardFocusManager and Thread.interrupt too. The sources are read as text, so a
 * wildcard import counts too.
 */
public class HubRulesTest
{
    private static final List<String> FORBIDDEN = List.of("java.io.File", "java.io.*", "new File(", "FileInputStream",
        "FileOutputStream", "RandomAccessFile", "java.nio.file", "RUNELITE_DIR", "Filepath.Unchecked",
        "LinkBrowser.open", "JFileChooser", "FileDialog", "java.awt.Desktop", "Desktop.getDesktop",
        "Runtime.getRuntime", "ProcessBuilder", "java.lang.reflect", "java.lang.invoke", "getMethod(",
        "getDeclaredMethod", "getField(", "getDeclaredField", "getDeclaredConstructor", "setAccessible(", "getAnnotation",
        "Class.forName", "KeyboardFocusManager", ".interrupt()", "::interrupt");

    /** What Filepath's own methods take or throw, so a plugin may name them though they're java.nio.file's. */
    private static final List<String> ALLOWED = List.of("java.nio.file.StandardOpenOption",
        "java.nio.file.FileVisitOption", "java.nio.file.NoSuchFileException");

    @Test
    public void nothingTheHubRefuses() throws IOException
    {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(Paths.get("src/main")))
        {
            sources = files.filter(f -> f.toString().endsWith(".java")).collect(Collectors.toList());
        }
        assertFalse("no sources found", sources.isEmpty());

        List<String> found = new ArrayList<>();
        for (Path source : sources)
        {
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            for (String allowed : ALLOWED)
            {
                text = text.replace(allowed, "");
            }
            for (String api : FORBIDDEN)
            {
                if (text.contains(api))
                {
                    found.add(source.getFileName() + ": " + api);
                }
            }
        }
        assertEquals(List.of(), found);
    }
}

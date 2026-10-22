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
 * program. The sources are read as text, so a wildcard import counts too.
 */
public class HubRulesTest
{
    private static final List<String> FORBIDDEN = List.of("java.io.File", "java.io.*", "new File(", "FileInputStream",
        "FileOutputStream", "java.nio.file", "RUNELITE_DIR", "LinkBrowser.open", "JFileChooser", "java.awt.Desktop",
        "Desktop.getDesktop", "Runtime.getRuntime", "ProcessBuilder");

    @Test
    public void filesGoThroughRuneLitesFilepathOnly() throws IOException
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

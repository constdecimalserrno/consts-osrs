package com.constsdestination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class ConfigSidePanelCopyTest
{
    /**
     * ConfigSidePanel lives in consts-AFK, tested there; this copy has to match it, package line aside. Skipped when
     * this plugin is built alone, as the Plugin Hub builds it.
     */
    @Test
    public void itMatchesConstsAfksCopy() throws Exception
    {
        Path afk = Paths.get("../consts-afk/src/main/java/com/constsafk/ConfigSidePanel.java");
        assumeTrue(Files.exists(afk));
        String theirs = new String(Files.readAllBytes(afk), "UTF-8").replace("package com.constsafk;", "package com.constsdestination;");
        String ours = new String(Files.readAllBytes(Paths.get("src/main/java/com/constsdestination/ConfigSidePanel.java")), "UTF-8");
        assertEquals(theirs, ours);
    }
}

package com.constsdestination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

public class ConfigSidePanelCopyTest
{
    /**
     * ConfigSidePanel lives in consts-EXP, tested there; this copy has to match it, package line aside. Skipped when
     * this plugin is built alone, as the Plugin Hub builds it: no repo root above it. In the repo a missing consts-EXP
     * copy fails the test, so a moved plugin can't switch the check off.
     */
    @Test
    public void itMatchesConstsExpsCopy() throws Exception
    {
        assumeTrue(Files.exists(Paths.get("../settings.gradle")));
        String theirs = new String(Files.readAllBytes(Paths.get("../consts-exp/src/main/java/com/constsexp/ConfigSidePanel.java")), "UTF-8")
            .replace("package com.constsexp;", "package com.constsdestination;");
        String ours = new String(Files.readAllBytes(Paths.get("src/main/java/com/constsdestination/ConfigSidePanel.java")), "UTF-8");
        assertEquals(theirs, ours);
    }
}

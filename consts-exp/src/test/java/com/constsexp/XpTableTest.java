package com.constsexp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JLabel;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import org.junit.Test;

public class XpTableTest
{
    private static final long T0 = 1_000_000L;

    private final XpRates rates = new XpRates();
    private final XpTable table = new XpTable(rates, new SkillIconManager());

    @Test
    public void withNothingTrainedItSaysHowToFillIt()
    {
        table.refresh(T0);
        assertTrue(texts().toString(), texts().stream().anyMatch(t -> t.startsWith("Gain XP")));
        assertFalse(button("Reset all").isEnabled());
    }

    @Test
    public void oneRowPerSkillNewestFirstWithItsRateXpAndTime()
    {
        rates.gain(Skill.MINING, 100, T0);
        rates.gain(Skill.MINING, 400, T0 + 60_000);
        rates.gain(Skill.FISHING, 50, T0 + 60_000);
        table.refresh(T0 + 60_000);

        assertEquals(List.of("XP/h", "XP", "Time", "0", "50", "0:00", "24.0k", "500", "1:00"), texts());
        assertEquals("Fishing", icons().get(0).getToolTipText());
        assertEquals("Mining", icons().get(1).getToolTipText());

        // It follows the clock.
        table.refresh(T0 + 120_000);
        assertEquals("12.0k", texts().get(6));
    }

    @Test
    public void eachRowResetTakesOffItsOwnSkillAndResetAllClearsTheList()
    {
        rates.gain(Skill.MINING, 100, T0);
        rates.gain(Skill.FISHING, 50, T0);
        table.refresh(T0);

        buttons("Reset").get(1).doClick();
        assertEquals(List.of(Skill.FISHING), rates.skills());
        assertEquals("the row goes at once", List.of("Fishing"),
            icons().stream().map(JLabel::getToolTipText).collect(Collectors.toList()));

        button("Reset all").doClick();
        assertEquals(List.of(), rates.skills());
        assertTrue(texts().toString(), texts().stream().anyMatch(t -> t.startsWith("Gain XP")));
    }

    private List<String> texts()
    {
        return labels().stream().filter(l -> l.getIcon() == null).map(JLabel::getText).collect(Collectors.toList());
    }

    private List<JLabel> icons()
    {
        return labels().stream().filter(l -> l.getIcon() != null).collect(Collectors.toList());
    }

    private List<JLabel> labels()
    {
        return all(table).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
            .collect(Collectors.toList());
    }

    private List<JButton> buttons(String text)
    {
        return all(table).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
            .filter(b -> text.equals(b.getText())).collect(Collectors.toList());
    }

    private JButton button(String text)
    {
        return buttons(text).get(0);
    }

    private static List<Component> all(Container root)
    {
        List<Component> out = new ArrayList<>();
        for (Component c : root.getComponents())
        {
            out.add(c);
            if (c instanceof Container)
            {
                out.addAll(all((Container) c));
            }
        }
        return out;
    }
}

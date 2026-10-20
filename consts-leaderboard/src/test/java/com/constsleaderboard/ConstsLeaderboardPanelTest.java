package com.constsleaderboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.hiscore.Skill;
import org.junit.Test;

public class ConstsLeaderboardPanelTest
{
    @Test
    public void thePanelShowsEverySkillOnce()
    {
        Set<HiscoreSkill> skills = Arrays.stream(HiscoreSkill.values())
            .filter(s -> s.getType() == HiscoreSkillType.SKILL)
            .collect(Collectors.toSet());
        assertEquals(skills, new HashSet<>(ConstsLeaderboardPanel.SKILLS));
        assertEquals(skills.size(), ConstsLeaderboardPanel.SKILLS.size());
        assertEquals(24 * 99, ConstsLeaderboardPanel.MAX_TOTAL);
    }

    @Test
    public void aSkillPast99Shows99InYellow()
    {
        Skill skill = new Skill(1_234, 99, 200_000_000);
        int level = ConstsLeaderboardPanel.shownLevel(HiscoreSkill.SLAYER, skill);
        assertEquals(99, level);
        assertTrue(ConstsLeaderboardPanel.isMax(HiscoreSkill.SLAYER, level));
    }

    @Test
    public void aSkillUnder99IsNotYellow()
    {
        Skill skill = new Skill(50_000, 98, 13_034_430);
        int level = ConstsLeaderboardPanel.shownLevel(HiscoreSkill.SLAYER, skill);
        assertEquals(98, level);
        assertFalse(ConstsLeaderboardPanel.isMax(HiscoreSkill.SLAYER, level));
    }

    @Test
    public void anUnrankedSkillShowsNoLevel()
    {
        assertEquals(-1, ConstsLeaderboardPanel.shownLevel(HiscoreSkill.SAILING, new Skill(-1, 1, -1)));
        assertEquals(-1, ConstsLeaderboardPanel.shownLevel(HiscoreSkill.SAILING, null));
    }

    @Test
    public void theTotalLevelIsTheHiscoresOwnAndYellowOnlyWhenMaxed()
    {
        Skill total = new Skill(9, 2_376, 4_600_000_000L);
        assertEquals(2_376, ConstsLeaderboardPanel.shownLevel(HiscoreSkill.OVERALL, total));
        assertTrue(ConstsLeaderboardPanel.isMax(HiscoreSkill.OVERALL, 2_376));
        assertFalse(ConstsLeaderboardPanel.isMax(HiscoreSkill.OVERALL, 2_375));
        assertFalse(ConstsLeaderboardPanel.isMax(HiscoreSkill.OVERALL, 99));
    }

    @Test
    public void hoveringASkillGivesRankExperienceAndRemainingXp()
    {
        String html = ConstsLeaderboardPanel.detailsHtml(HiscoreSkill.WOODCUTTING, new Skill(12_345, 2, 100));
        assertTrue(html, html.contains("Skill:</span> Woodcutting"));
        assertTrue(html, html.contains("Rank:</span> 12,345"));
        assertTrue(html, html.contains("Experience:</span> 100"));
        assertTrue(html, html.contains("Remaining XP:</span> 74"));
        assertTrue(html, html.contains("width: 18%"));
    }

    @Test
    public void hoveringA99LeavesNothingRemaining()
    {
        String html = ConstsLeaderboardPanel.detailsHtml(HiscoreSkill.WOODCUTTING, new Skill(1, 99, 200_000_000));
        assertTrue(html, html.contains("Experience:</span> 200,000,000"));
        assertTrue(html, html.contains("Remaining XP:</span> 0"));
        assertTrue(html, html.contains("width: 100%"));
    }

    @Test
    public void hoveringAnUnrankedSkillSaysSo()
    {
        String html = ConstsLeaderboardPanel.detailsHtml(HiscoreSkill.SAILING, new Skill(-1, 1, -1));
        assertTrue(html, html.contains("Rank:</span> Unranked"));
        assertTrue(html, html.contains("Remaining XP:</span> Unranked"));
        assertFalse(html, html.contains("background: #dc8a00"));
    }

    @Test
    public void hoveringTheTotalGivesRankAndExperienceOnly()
    {
        String html = ConstsLeaderboardPanel.detailsHtml(HiscoreSkill.OVERALL, new Skill(9, 2_376, 4_600_000_000L));
        assertTrue(html, html.contains("Overall"));
        assertTrue(html, html.contains("Rank:</span> 9"));
        assertTrue(html, html.contains("Experience:</span> 4,600,000,000"));
        assertFalse(html, html.contains("Remaining XP"));
    }
}

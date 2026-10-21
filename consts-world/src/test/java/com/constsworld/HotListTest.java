package com.constsworld;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class HotListTest
{
    @Test
    public void theHotListStartsEmpty()
    {
        assertTrue(HotList.parse(null).isEmpty());
        assertTrue(HotList.parse("").isEmpty());
        assertEquals("", HotList.parse(null).format());
    }

    @Test
    public void worldsKeepTheOrderTheyWereAddedIn()
    {
        HotList list = new HotList();
        assertTrue(list.add(330));
        assertTrue(list.add(302));
        assertTrue(list.add(416));
        assertEquals(List.of(330, 302, 416), list.worlds());
        assertEquals("330,302,416", list.format());
        assertEquals(List.of(330, 302, 416), HotList.parse(list.format()).worlds());
    }

    @Test
    public void aWorldIsInTheListOnce()
    {
        HotList list = HotList.parse("330,302,330");
        assertEquals(List.of(330, 302), list.worlds());
        assertFalse(list.add(302));
        assertEquals(List.of(330, 302), list.worlds());
    }

    @Test
    public void removingAWorldKeepsTheRestInOrder()
    {
        HotList list = HotList.parse("330,302,416");
        assertTrue(list.remove(302));
        assertFalse(list.remove(302));
        assertFalse(list.contains(302));
        assertTrue(list.contains(330));
        assertEquals("330,416", list.format());
    }

    @Test
    public void anythingThatIsntAWorldNumberIsSkipped()
    {
        assertEquals(List.of(330, 302), HotList.parse(" 330 , x, -1, 0,,302 ").worlds());
    }
}

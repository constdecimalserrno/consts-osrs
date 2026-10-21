package com.constsmodels;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TripleIndexTest
{
    @Test
    public void storesAndFindsByThreeNumbersAsItGrows()
    {
        TripleIndex index = new TripleIndex(4);
        for (int i = 0; i < 5000; i++)
        {
            index.put(i, -i, i * 7, i);
        }
        assertEquals(5000, index.size());
        for (int i = 0; i < 5000; i++)
        {
            assertEquals(i, index.get(i, -i, i * 7));
        }
        assertEquals(-1, index.get(1, 1, 7));
        index.put(3, -3, 21, 99);
        assertEquals(99, index.get(3, -3, 21));
        assertEquals(5000, index.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void valuesAreNeverNegative()
    {
        new TripleIndex(1).put(0, 0, 0, -1);
    }
}

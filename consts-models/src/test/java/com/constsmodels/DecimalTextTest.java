package com.constsmodels;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DecimalTextTest
{
    @Test
    public void numbersAreShortWithNoExponent()
    {
        assertEquals("0", DecimalText.of(0));
        assertEquals("0", DecimalText.of(-0.0));
        assertEquals("0", DecimalText.of(-0.00001));
        assertEquals("12", DecimalText.of(12));
        assertEquals("-3.5", DecimalText.of(-3.5));
        assertEquals("1.05", DecimalText.of(1.05));
        assertEquals("0.0001", DecimalText.of(0.0001));
        assertEquals("2.0002", DecimalText.of(2.00016));
        assertEquals("123456789.25", DecimalText.of(123456789.25));
        assertEquals("0", DecimalText.of(1e-9));
        assertEquals("0", DecimalText.of(Double.NaN));
    }
}

package com.constsmodels;

import java.util.Arrays;

/**
 * Finds a number by three others, such as a vertex by the grid cell it's in. Open addressing over plain int arrays, so
 * the hundreds of thousands of entries an area has stay small and quick.
 */
final class TripleIndex
{
    private static final int EMPTY = -1;

    private int[] keys;
    private int[] values;
    private int mask;
    private int size;

    TripleIndex(int expected)
    {
        int capacity = 16;
        while (capacity < expected + expected / 2 && capacity < (1 << 30))
        {
            capacity <<= 1;
        }
        allocate(capacity);
    }

    /** What's stored under (i, j, k), or -1 for nothing. */
    int get(int i, int j, int k)
    {
        for (int slot = hash(i, j, k) & mask; ; slot = (slot + 1) & mask)
        {
            int value = values[slot];
            if (value == EMPTY)
            {
                return EMPTY;
            }
            int at = slot * 3;
            if (keys[at] == i && keys[at + 1] == j && keys[at + 2] == k)
            {
                return value;
            }
        }
    }

    /** Stores a value of 0 or more under (i, j, k), in place of whatever was there. */
    void put(int i, int j, int k, int value)
    {
        if (value < 0)
        {
            throw new IllegalArgumentException("values must be 0 or more");
        }
        if ((size + 1) * 10L > values.length * 7L)
        {
            grow();
        }
        if (insert(i, j, k, value))
        {
            size++;
        }
    }

    int size()
    {
        return size;
    }

    private boolean insert(int i, int j, int k, int value)
    {
        for (int slot = hash(i, j, k) & mask; ; slot = (slot + 1) & mask)
        {
            int at = slot * 3;
            if (values[slot] == EMPTY)
            {
                keys[at] = i;
                keys[at + 1] = j;
                keys[at + 2] = k;
                values[slot] = value;
                return true;
            }
            if (keys[at] == i && keys[at + 1] == j && keys[at + 2] == k)
            {
                values[slot] = value;
                return false;
            }
        }
    }

    private void grow()
    {
        int[] oldKeys = keys;
        int[] oldValues = values;
        allocate(values.length * 2);
        for (int slot = 0; slot < oldValues.length; slot++)
        {
            if (oldValues[slot] != EMPTY)
            {
                int at = slot * 3;
                insert(oldKeys[at], oldKeys[at + 1], oldKeys[at + 2], oldValues[slot]);
            }
        }
    }

    private void allocate(int capacity)
    {
        keys = new int[capacity * 3];
        values = new int[capacity];
        Arrays.fill(values, EMPTY);
        mask = capacity - 1;
    }

    private static int hash(int i, int j, int k)
    {
        int h = i * 0x9E3779B1 + j * 0x85EBCA77 + k * 0xC2B2AE3D;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        return h ^ (h >>> 16);
    }
}

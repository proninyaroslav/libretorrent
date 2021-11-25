/*
 * Copyright (C) 2017 Darshan Parajuli
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This code is licensed under the MIT License.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files(the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions :
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package org.proninyaroslav.libretorrent.core.collections;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class FixedRingBufferTest
{
    @Test(expected = IllegalArgumentException.class)
    public void testCapacityZero()
    {
        new FixedRingBuffer<Integer>(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInitCapacityNegative()
    {
        new FixedRingBuffer<Integer>(-1, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInitCapacityGreatCapacity()
    {
        new FixedRingBuffer<Integer>(11, 10);
    }

    @Test
    public void test()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(5);
        for (int i = 0; i < 100; i++)
            buffer.add(i);
    }

    @Test
    public void testAdd()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(5);
        assertTrue(buffer.isEmpty());
        assertEquals(5, buffer.getAllocatedSize());

        buffer.add(1);
        assertFalse(buffer.isEmpty());

        buffer.clear();
        assertTrue(buffer.isEmpty());

        for (int i = 0; i < 100; i++)
            buffer.add(i);
        assertEquals(5, buffer.size());
        assertTrue(buffer.isFull());

        Integer[] expectedValues = new Integer[]{95, 96, 97, 98, 99};
        for (int i = 0; i < buffer.size(); i++)
            assertEquals(expectedValues[i], buffer.get(i));
    }

    @Test
    public void testAllocation()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(1, 10);
        assertEquals(1, buffer.getAllocatedSize());

        buffer.add(1);
        assertEquals(1, buffer.size());
        assertEquals(1, buffer.getAllocatedSize());

        buffer.add(2);
        assertEquals(2, buffer.size());
        assertEquals(2, buffer.getAllocatedSize());

        buffer.add(3);
        assertEquals(3, buffer.size());
        assertEquals(4, buffer.getAllocatedSize());

        buffer.add(4);
        assertEquals(4, buffer.size());
        assertEquals(4, buffer.getAllocatedSize());

        buffer.add(5);
        assertEquals(5, buffer.size());
        assertEquals(8, buffer.getAllocatedSize());

        buffer.add(6);
        assertEquals(6, buffer.size());
        assertEquals(8, buffer.getAllocatedSize());

        buffer.add(7);
        assertEquals(7, buffer.size());
        assertEquals(8, buffer.getAllocatedSize());

        buffer.add(8);
        assertEquals(8, buffer.size());
        assertEquals(8, buffer.getAllocatedSize());

        buffer.add(9);
        assertEquals(9, buffer.size());
        assertEquals(10, buffer.getAllocatedSize());

        buffer.add(10);
        assertEquals(10, buffer.size());
        assertEquals(10, buffer.getAllocatedSize());

        assertTrue(buffer.isFull());

        for (int i = 0; i < buffer.size(); i++)
            assertEquals(new Integer(i + 1), buffer.get(i));

        buffer.clear();
        assertEquals(10, buffer.getAllocatedSize());
    }

    @Test
    public void testRemove()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(10);
        buffer.add(5);
        assertEquals(new Integer(5), buffer.remove(5));
        assertTrue(buffer.isEmpty());

        for (int i = 0; i < 100; i++)
            buffer.add(i);
        assertEquals(10, buffer.size());

        int index = 0;
        while (!buffer.isEmpty())
            assertEquals(new Integer(90 + index++), buffer.removeAt(0));
        assertTrue(buffer.isEmpty());
    }

    @Test
    public void testAddAfterRemove()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(5);

        for (int i = 0; i < 5; i++)
            buffer.add(i);

        buffer.removeAt(0);
        buffer.removeAt(1);
        buffer.removeAt(2);

        buffer.add(1);
        buffer.add(2);

        assertEquals(4, buffer.size());
        assertFalse(buffer.isFull());

        Integer[] expectedValues = new Integer[]{1, 3, 1, 2};
        for (int i = 0; i < buffer.size(); i++)
            assertEquals(expectedValues[i], buffer.get(i));
    }

    @Test
    public void testIndexOf()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(10);
        assertEquals(-1, buffer.indexOf(12));

        for (int i = 0; i < 1000; i++)
            buffer.add(i);
        assertEquals(8, buffer.indexOf(998));

        buffer.clear();
        assertTrue(buffer.isEmpty());

        buffer.add(1);
        buffer.add(2);
        assertEquals(1, buffer.indexOf(2));
    }

    @Test
    public void testGet()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(10);

        buffer.add(1);
        buffer.add(2);

        assertEquals(new Integer(1), buffer.get(0));
        assertEquals(new Integer(2), buffer.get(1));

        for (int i = 0; i < 100; i++)
            buffer.add(i);

        assertEquals(new Integer(99), buffer.get(buffer.size() - 1));
        assertEquals(new Integer(90), buffer.get(0));
    }

    @Test
    public void testIsFull()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(100);
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());

        for (int i = 0; i < 99; i++)
            buffer.add(i);
        assertFalse(buffer.isEmpty());
        assertFalse(buffer.isFull());

        buffer.add(1);
        assertTrue(buffer.isFull());
    }

    @Test
    public void testIterator()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(10);
        ArrayList<Integer> expected = new ArrayList<>(10);
        for (int i = 0; i < buffer.size(); i++) {
            buffer.add(i * i);
            expected.add(i * i);
        }

        ArrayList<Integer> actual = new ArrayList<>(10);
        for (int i : buffer)
            actual.add(i);

        assertEquals(expected, actual);
    }

    @Test
    public void testContains()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(100);
        buffer.add(1);
        buffer.add(2);

        assertTrue(buffer.contains(1));
        assertTrue(buffer.contains(2));
        assertFalse(buffer.contains(3));

        for (int i = 0; i < 1000; i++)
            buffer.add(i);

        assertTrue(buffer.contains(900));
        assertTrue(buffer.contains(999));
        assertFalse(buffer.contains(1000));
    }

    @Test
    public void testClear()
    {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<>(10);
        buffer.add(1);
        buffer.add(2);
        buffer.clear();
        assertTrue(buffer.isEmpty());

        for (int i = 0; i < 5; i++)
            buffer.add(i);
        buffer.clear();
        assertTrue(buffer.isEmpty());

        for (int i = 0; i < 10; i++)
            buffer.add(i);
        buffer.clear();
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
    }
}
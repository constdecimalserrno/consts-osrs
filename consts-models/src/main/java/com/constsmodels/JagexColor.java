/*
 * Copyright (c) 2020 Abex
 * Copyright (c) 2020, Bram91 (the average of three colors, from the Model Exporter plugin)
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: moved into consts-Models, colors as plain ints)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.constsmodels;

/**
 * The game's 16-bit HSL colors as RGB. Originally from RuneLite's cache, with the Model Exporter plugin's average
 * of three colors for a face shaded across its corners.
 */
final class JagexColor
{
    static final double BRIGHTNESS_MAX = .6;
    static final double BRIGHTNESS_HIGH = .7;
    static final double BRIGHTNESS_LOW = .8;
    static final double BRIGHTNESS_MIN = .9;

    private static final double HUE_OFFSET = (.5 / 64.D);
    private static final double SATURATION_OFFSET = (.5 / 8.D);

    private JagexColor()
    {
    }

    /** Takes 3 Jagex HSL colors and makes a single RGB color to represent them. */
    static int hslToRgbAverage(int hsl1, int hsl2, int hsl3)
    {
        int c1 = hslToRgb((short) hsl1, BRIGHTNESS_MIN);
        int c2 = hslToRgb((short) hsl2, BRIGHTNESS_MIN);
        int c3 = hslToRgb((short) hsl3, BRIGHTNESS_MIN);

        // compute color average using squares
        // uses squared approach from https://sighack.com/post/averaging-rgb-colors-the-right-way
        int r = average(c1 >> 16 & 255, c2 >> 16 & 255, c3 >> 16 & 255);
        int g = average(c1 >> 8 & 255, c2 >> 8 & 255, c3 >> 8 & 255);
        int b = average(c1 & 255, c2 & 255, c3 & 255);
        return r << 16 | g << 8 | b;
    }

    private static int average(int a, int b, int c)
    {
        return Math.min(255, (int) Math.round(Math.sqrt((a * a + b * b + c * c) / 3.0d)));
    }

    static int unpackHue(short hsl)
    {
        return hsl >> 10 & 63;
    }

    static int unpackSaturation(short hsl)
    {
        return hsl >> 7 & 7;
    }

    static int unpackLuminance(short hsl)
    {
        return hsl & 127;
    }

    static int hslToRgb(short hsl, double brightness)
    {
        double hue = (double) unpackHue(hsl) / 64.D + HUE_OFFSET;
        double saturation = (double) unpackSaturation(hsl) / 8.D + SATURATION_OFFSET;
        double luminance = (double) unpackLuminance(hsl) / 128.D;

        // This is just a standard hsl to rgb transform
        // the only difference is the offsets above and the brightness transform below
        double chroma = (1.D - Math.abs((2.D * luminance) - 1.D)) * saturation;
        double x = chroma * (1 - Math.abs(((hue * 6.D) % 2.D) - 1.D));
        double lightness = luminance - (chroma / 2);

        double r = lightness, g = lightness, b = lightness;
        switch ((int) (hue * 6.D))
        {
            case 0:
                r += chroma;
                g += x;
                break;
            case 1:
                g += chroma;
                r += x;
                break;
            case 2:
                g += chroma;
                b += x;
                break;
            case 3:
                b += chroma;
                g += x;
                break;
            case 4:
                b += chroma;
                r += x;
                break;
            default:
                r += chroma;
                b += x;
                break;
        }

        int rgb = (channel(r) << 16)
            | (channel(g) << 8)
            | channel(b);

        rgb = adjustForBrightness(rgb, brightness);

        if (rgb == 0)
        {
            rgb = 1;
        }
        return rgb;
    }

    static int adjustForBrightness(int rgb, double brightness)
    {
        double r = (double) (rgb >> 16) / 256.0D;
        double g = (double) (rgb >> 8 & 255) / 256.0D;
        double b = (double) (rgb & 255) / 256.0D;

        r = Math.pow(r, brightness);
        g = Math.pow(g, brightness);
        b = Math.pow(b, brightness);

        return (channel(r) << 16)
            | (channel(g) << 8)
            | channel(b);
    }

    // A 0-1 channel as 0-255; the original could reach 256 at full brightness and spill into the next channel
    private static int channel(double v)
    {
        return Math.max(0, Math.min(255, (int) (v * 256.0D)));
    }
}

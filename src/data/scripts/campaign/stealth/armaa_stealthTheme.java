package data.scripts.campaign.stealth;

import java.awt.Color;

/**
 * Works out the interface colours from one base colour (the player faction's). No game
 * classes in here, so the panel, the harness and the tests share it.
 *
 * Reds, oranges and yellows would be lost against the amber and red of the guards'
 * warnings, and greys have no hue to tint with, so those give no theme and the caller
 * keeps the radar green.
 */
public class armaa_stealthTheme {

    public final Color field, grid, line, ring;

    private armaa_stealthTheme(Color field, Color grid, Color line, Color ring) {
        this.field = field;
        this.grid = grid;
        this.line = line;
        this.ring = ring;
    }

    /** True if a colour is too close to the warning colours, or too grey, to build the interface from. */
    public static boolean clashes(Color base) {
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        float hue = hsb[0] * 360f;
        return hue < 81f || hue > 330f || hsb[1] < 0.15f;
    }

    /** The theme for a base colour, or null if it clashes (or is null). */
    public static armaa_stealthTheme of(Color base) {
        if (base == null || clashes(base)) {
            return null;
        }
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        float h = hsb[0];
        return new armaa_stealthTheme(
                Color.getHSBColor(h, 0.9f, 0.13f),
                Color.getHSBColor(h, Math.max(0.5f, Math.min(0.8f, hsb[1] + 0.1f)), 0.67f),
                Color.getHSBColor(h, Math.max(0.45f, Math.min(0.7f, hsb[1])), 1f),
                Color.getHSBColor(h, 0.3f, 1f));
    }
}

package com.white.dominantColor;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * k-means聚合算法算提取图片（bitmap）的主题色
 * 核心代码来自 https://github.com/jfeinstein10/DominantColors
 */
public class DominantColors {
    private static final String TAG = "DominantColors";

    public static final int DEFAULT_NUM_COLORS = 3;
    public static final double DEFAULT_MIN_DIFF = 0.5f;
    public static final int SIDE_SIZE = 50;

    public static DominantColor[] getDominantColors(Bitmap bitmap) {
        return getDominantColors(bitmap, DEFAULT_NUM_COLORS);
    }

    /**
     * 注意，输入的bitmap一定要是ARGB_8888
     *
     * @param bitmap
     * @param numColors
     * @return
     */
    @Nullable
    public static DominantColor[] getDominantColors(Bitmap bitmap, int numColors) {
        return getDominantColors(bitmap, numColors, DEFAULT_MIN_DIFF);
    }

    public static DominantColor[] getDominantColors(Bitmap bitmap, int numColors, double minDiff) {
        // scale down while maintaining aspect ratio
        bitmap = resizeToFitInSquare(bitmap, SIDE_SIZE);
        DominantColor[] cs = kmeans(bitmap, numColors);
        for (DominantColor c : cs){
            Log.d(TAG,"#getDominantColors over:" + c.toString());
        }
        return cs;
    }

    public static DominantColor[] getMeanShift(Bitmap bitmap, float radius) {
        int[] c = meanShift(bitmap, radius);
        DominantColor[] colors = new DominantColor[c.length];
        for (int i = 0; i < c.length; i++) {
            colors[i] = new DominantColor(c[i], 1);
        }
        return colors;
    }

    public static Bitmap resizeToFitInSquare(Bitmap bitmap, int side) {
        if (bitmap.getWidth() > side || bitmap.getHeight() > side) {
            if (bitmap.getWidth() > bitmap.getHeight()) {
                bitmap = Bitmap.createScaledBitmap(bitmap, side,
                        (int) (side * ((float) bitmap.getHeight() / bitmap.getWidth())), false);
            } else {
                bitmap = Bitmap.createScaledBitmap(bitmap, (int)
                        (side * ((float) bitmap.getWidth() / bitmap.getHeight())), side, false);
            }
        }
        return bitmap;
    }

    private static int[] getPoints(Bitmap bitmap) {
        int[] points = new int[bitmap.getWidth() * bitmap.getHeight()];
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        // create the points in row-major order
        for (int i = 0; i < height; i++)
            for (int j = 0; j < width; j++)
                points[j + i * width] = bitmap.getPixel(j, i);
        return points;
    }

    private static DominantColor[] kMeans(int[] points, int numColors, double minDiff) {
        // create the clusters
        int[] middles = getRandomMiddles(points, numColors);
        DominantColor[] colors = new DominantColor[numColors];

        while (true) {
            // resample and resort the points
            ArrayList<Integer>[] newClusters = new ArrayList[numColors];
            for (int i = 0; i < numColors; i++)
                newClusters[i] = new ArrayList<Integer>();

            for (int point : points) {
                double minDist = Double.MAX_VALUE;
                int minId = 0;
                for (int i = 0; i < middles.length; i++) {
                    double dist = calculateDistance(point, middles[i]);
                    if (dist < minDist) {
                        minDist = dist;
                        minId = i;
                    }
                }
                newClusters[minId].add(point);
            }
            // copy the new cluster data into the old clusters
            double diff = 0;
            for (int i = 0; i < middles.length; i++) {
                int newCenter = calculateCenter(newClusters[i]);
                diff = Math.max(diff, calculateDistance(newCenter, middles[i]));
                middles[i] = newCenter;
            }
            if (diff < minDiff) {
                for (int i = 0; i < middles.length; i++)
                    colors[i] = new DominantColor(middles[i], (float) newClusters[i].size() / (float) points.length);
                break;
            }
        }

        Arrays.sort(colors, new Comparator<DominantColor>() {
            @Override
            public int compare(DominantColor lhs, DominantColor rhs) {
                return (int) (100 * (lhs.percentage - rhs.percentage));
            }
        });

        return colors;
    }

    private static int[] getRandomMiddles(int[] points, int numColors) {
        ArrayList<Integer> indices = new ArrayList<Integer>();
        for (int i = 0; i < points.length; i++)
            indices.add(i);

        Collections.shuffle(indices);
        ArrayList<Integer> midArray = new ArrayList<Integer>();
        int[] middles = new int[numColors];
        int index = 0;
        while (midArray.size() < numColors) {
            int val = points[indices.get(index++)];
            if (!midArray.contains(val)) {
                middles[midArray.size()] = val;
                midArray.add(val);
            }
        }
        return middles;
    }

    private static int calculateCenter(List<Integer> points) {
        int rSum, gSum, bSum;
        rSum = gSum = bSum = 0;
        for (int i : points) {
            rSum += Color.red(i);
            gSum += Color.green(i);
            bSum += Color.blue(i);
        }
        if (points.size() == 0) {
            return 0;
        } else {
            return Color.rgb(rSum / points.size(),
                    gSum / points.size(),
                    bSum / points.size());
        }
    }

    private static double calculateDistance(int c1, int c2) {
        return Math.sqrt(
                0.9 * Math.pow(Color.red(c1) - Color.red(c2), 2) +
                        1.2 * Math.pow(Color.green(c1) - Color.green(c2), 2) +
                        0.9 * Math.pow(Color.blue(c1) - Color.blue(c2), 2));
    }

    static {
        System.loadLibrary("dominantcolors");
    }

    private native static DominantColor[] kmeans(Bitmap bmp, int numColors);

    /**
     * 均值漂移算法，目前尚未完成
     *
     * @param bmp
     * @param radius
     * @return
     */
    private native static int[] meanShift(Bitmap bmp, float radius);

}

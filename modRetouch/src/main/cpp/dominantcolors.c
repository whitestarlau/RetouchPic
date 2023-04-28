#include <jni.h>
#include <time.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include "kdtree.h"

typedef int bool;
#define true 1
#define false 0
#define alpha_mask 0xFF000000
#define blue_mask 0x00FF0000
#define blue_shift 16
#define green_mask 0x0000FF00
#define green_shift 8
#define red_mask 0x000000FF
#define mean_shift_threshold 1
#define SQ(x) ((x)*(x))

#define  LOG_TAG    "dominantcolors"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

typedef struct {
    double r;
    double g;
    double b;
} color_sum;

typedef struct {
    int length;
    int color;
    struct shift_response *next;
} shift_response;

static int rgb_clamp(int value) {
    if (value > 255) {
        return 255;
    }
    if (value < 0) {
        return 0;
    }
    return value;
}

static int blue(uint32_t color) {
    return (int) ((color & blue_mask) >> blue_shift);
}

static int green(uint32_t color) {
    return (int) ((color & green_mask) >> green_shift);
}

static int red(uint32_t color) {
    return (int) (color & red_mask);
}

static uint32_t color(int r, int g, int b) {
    return (alpha_mask | ((b << blue_shift) & blue_mask) |
            ((g << green_shift) & green_mask) |
            (r & red_mask));
}

static uint32_t rev_color(uint32_t c) {
    return color(blue(c), green(c), red(c));
}

/**
 * 计算两个颜色的距离。（0.3、0.59、0.11是经验值？）
 * @param c1
 * @param c2
 * @return
 */
static double distanceSq(uint32_t c1, uint32_t c2) {
    return pow((red(c1) - red(c2)) * 0.30f, 2) +
           pow((green(c1) - green(c2)) * 0.59f, 2) +
           pow((blue(c1) - blue(c2)) * 0.11f, 2);
}

/**
 * kmeans的真正方法
 * @param info 传入的Android bitmap的信息
 * @param pixels bitmap的像素信息
 * @param numColors kmeans的k值，即最后得到的集合的大小
 * @param centroids 质心数组。也就是最后得到的结果数组
 * @param precentages 得到的结果百分比数组。和centroids下标保持一致
 */
static void kmeans(AndroidBitmapInfo *info, void *pixels, int numColors, jint *centroids,
                   jfloat *precentages) {
    int xx, yy, c;
    //保存像素数组开始的位置
    uint32_t *start = (uint32_t *) pixels;
    uint32_t *line;

    color_sum sums[numColors];
    double members[numColors];
    int filled = 0;
    uint32_t new_color;
    while (filled < numColors) {
        //用rand方法产生随机种子坐标，从图片中取出这些种子坐标对应的数据。种子的数量由numColors决定
        xx = rand() % info->width;
        yy = rand() % info->height;
        new_color = ((uint32_t *) ((char *) pixels + yy * info->stride))[xx];
        bool contained = false;
        int i;
        for (i = 0; i < filled; contained |= (centroids[i++] == new_color));
        if (!contained) {
            centroids[filled++] = new_color;
        }
    }

    double max_error;
    int index = 0;

    do {
        // reset vars（初始化sum、members）
        for (c = 0; c < numColors; c++) {
            sums[c] = (color_sum) {0, 0, 0};
            members[c] = 0;
        }
        // start from the beginning of the image
        line = start;
        for (yy = 0; yy < info->height; yy++) {
            for (xx = 0; xx < info->width; xx++) {
                double min_distSq = DBL_MAX;
                int best_centroid = 0;
                for (c = 0; c < numColors; c++) {
                    //当前点和质心点的距离（质心点一开始是随机值，后续随着算法迭代被逐步替换）
                    double distSq = distanceSq(line[xx], centroids[c]);
                    if (distSq < min_distSq) {
                        min_distSq = distSq;
                        best_centroid = c;
                    }
                }
                sums[best_centroid].r += red(line[xx]);
                sums[best_centroid].g += green(line[xx]);
                sums[best_centroid].b += blue(line[xx]);
                members[best_centroid]++;
            }
            line = (uint32_t *) ((char *) line + info->stride);
        }
        max_error = 0;
        for (c = 0; c < numColors; c++) {
            uint32_t new_centroid;
            if (members[c] == 0) {
                new_centroid = 0xFFFFFFFF;
            } else {
                new_centroid = color(sums[c].r / members[c],
                                     sums[c].g / members[c],
                                     sums[c].b / members[c]);
            }
            double distSq = distanceSq(new_centroid, centroids[c]);
            if (distSq > max_error) {
                max_error = distSq;
            }
            centroids[c] = new_centroid;
        }
    } while (index++ < 100 && max_error > 1);

    double total = (info->width) * (info->height);
    for (int i = 0; i < numColors; ++i) {
        precentages[i] = members[i] / total;
        LOGE("precentages: %f", precentages[i]);
    }
}

//JNIEXPORT jintArray JNICALL
JNIEXPORT jobject JNICALL
Java_com_white_dominantColor_DominantColors_kmeans(JNIEnv *env, jclass obj, jobject bitmap,
                                                   jint numColors) {
    AndroidBitmapInfo info;
    int ret;
    void *pixels;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return NULL;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888!");
        return NULL;
    }
    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }


    jint fill[numColors];
    jfloat percentages[numColors];
    kmeans(&info, pixels, numColors, fill, percentages);
    for (int i = 0; i < numColors; i++) {
        fill[i] = rev_color(fill[i]);
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    //将结果放入DominantColor对象中
    jclass colorClass = (*env)->FindClass(env, "com/white/dominantColor/DominantColor");
    if (colorClass == NULL) {
        LOGE("com/white/dominantColor/DominantColor");
        return NULL;
    }
    jmethodID dominantColorInitId = (*env)->GetMethodID(env, colorClass, "<init>", "()V");
    jfieldID color_id = (*env)->GetFieldID(env, colorClass, "color", "I");
    jfieldID percentage_id = (*env)->GetFieldID(env, colorClass, "percentage", "F");

    jobjectArray result = (*env)->NewObjectArray(env, numColors,colorClass,0);

    for (int i = 0; i < numColors; i++) {
        jobject dominantColor = (*env)->NewObject(env, colorClass, dominantColorInitId);

        (*env)->SetIntField(env, dominantColor, color_id, fill[i]);
        (*env)->SetFloatField(env, dominantColor, percentage_id, percentages[i]);

        (*env)->SetObjectArrayElement(env, result,i, dominantColor);
    }

    return result;
}

static void get_center(struct kdres *set, double ret[3]) {
    int count = 0, i;
    double pos[3];
    char *pch;
    for (i = 0; i < 3; i++)
        ret[i] = 0;
    while (!kd_res_end(set)) {
        /* get the data and position of the current result item */
        pch = (char *) kd_res_item(set, pos);
        for (i = 0; i < 3; i++)
            ret[i] += pos[i];
        count += 1;
        /* go to the next entry */
        kd_res_next(set);
    }
    for (i = 0; i < 3; i++)
        ret[i] /= count;
    return;
}

static void mean_shift(AndroidBitmapInfo *info, void *pixels, float radius, shift_response *resp) {
    int xx, yy, i, j;
    uint32_t c;
    uint32_t *line;
    uint32_t *start = (uint32_t *) pixels;
    void *tree;
    struct kdres *set;
    // create the kd tree
    tree = kd_create(3);
    line = start;
    for (yy = 0; yy < info->height; yy++) {
        for (xx = 0; xx < info->width; xx++) {
            c = line[xx];
            kd_insert3(tree, red(c), green(c), blue(c), 0);
        }
        line = (uint32_t *) ((char *) line + info->stride);
    }
    // do some queries!
    shift_response temp = (shift_response) {0, 0, 0};
    resp = &temp;
    for (i = 0; i < 10; i++) {
        float moved;
        double old_pt[3], new_pt[3];
        old_pt[0] = DBL_MAX;
        old_pt[1] = DBL_MAX;
        old_pt[2] = DBL_MAX;
        do {
            set = kd_nearest_range3(tree, 0, 0, 0, radius);
            get_center(set, new_pt);
            moved = SQ(old_pt[0] - new_pt[0]) +
                    SQ(old_pt[1] - new_pt[1]) +
                    SQ(old_pt[2] - new_pt[2]);
        } while (moved > mean_shift_threshold);
        temp = (shift_response) {(resp->length) + 1, color(new_pt[0], new_pt[1], new_pt[2]), resp};
        resp = &temp;
    }
    // free the kd tree
    kd_free(tree);
}

JNIEXPORT jintArray JNICALL
Java_com_white_dominantColor_DominantColors_meanShift(JNIEnv *env, jclass obj, jobject bitmap,
                                                      jfloat radius) {
    AndroidBitmapInfo info;
    int ret;
    void *pixels;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
        return NULL;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmap format is not RGBA_8888!");
        return NULL;
    }
    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
    }

    shift_response *resp;
    LOGE("before call");
    mean_shift(&info, pixels, radius, resp);
    LOGE("after call");
    int length = resp->length;
    jint fill[length];
    int i = 0;
    while (resp->length != 0) {
        fill[i] = resp->color;
        i += 1;
        resp = resp->next;
    }

    jintArray result;
    result = (*env)->NewIntArray(env, length);
    if (result == NULL)
        return NULL;

    (*env)->SetIntArrayRegion(env, result, 0, length, fill);

    AndroidBitmap_unlockPixels(env, bitmap);
    return result;
}

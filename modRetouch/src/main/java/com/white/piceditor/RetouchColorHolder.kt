package com.white.piceditor

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.annotation.RequiresApi
import android.util.DisplayMetrics
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.white.dominantColor.DominantColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.HashSet


/**
 * 涂抹颜色的持有类
 * 考虑使用多个方法，目前尝试过摩尔投票法、kmeans聚合算法。未来考虑使用中位切分法
 */
object RetouchColorHolder : CoroutineScope by MainScope() {
    var weakViewHolder: WeakReference<View>? = null

    private const val TAG = "RetouchColorHolder"

    private var firstTouchColor: Int? = null

    private val cropRange = 100

    fun resetTouchColor() {
        firstTouchColor = null
    }

    /**
     * 此处的x、y是相对于view的位置,即使用getX()而不是getRawX()
     */
    fun getColor(x: Int, y: Int, colorCallBack: (Int) -> Unit) {
        return getColorNativeKMeans(x, y, colorCallBack)
    }

    /**
     * 采用kmeans聚合算法得到颜色,native层方法。耗时主要在绘制图片这一步
     */
    fun getColorNativeKMeans(x: Int, y: Int, colorCallBack: (Int) -> Unit) {
        firstTouchColor?.let {
            //如果不为空，直接进行返回
            colorCallBack.invoke(it)
        }
        Log.d(TAG, "start..")
        var result = -1
        weakViewHolder.let { weakIv ->
            var imgView = weakIv?.get()
            if (imgView == null) {
                Log.w(TAG, "getColor: img is null")
                result = -1
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    //算出在屏幕上的位置
                    val location = IntArray(2)
                    imgView.getLocationOnScreen(location)
                    val lx = x + location[0]
                    val ly = y + location[1]

                    val window: Window? = (imgView.context as? Activity)?.getWindow()

                    val wm = imgView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val dm = DisplayMetrics()
                    wm.defaultDisplay.getMetrics(dm)
                    var w = dm.widthPixels
                    var h = dm.heightPixels

                    window?.let {
                        val rect = Rect()
                        rect.left = if (lx - cropRange > 0) lx - cropRange else 0
                        rect.right = if (lx + cropRange < w) lx + cropRange else lx
                        rect.top = if (ly - cropRange > 0) ly - cropRange else 0
                        rect.bottom = if (ly + cropRange < h) ly + cropRange else ly

                        cropFromWindow(it, rect, { bmp ->
                            Log.d(TAG, "crop bitmap end,$rect")
                            val colors = DominantColors.getDominantColors(bmp, 5)
                            var maxPercent = -1f
                            for (i in colors.indices) {
                                val percent = colors[i].percentage
                                if (percent > maxPercent) {
                                    result = colors[i].color
                                    Log.d(TAG, "maxPercent:$percent,color:$result")
                                    maxPercent = percent
                                }
                            }
                            firstTouchColor = result
                            colorCallBack.invoke(result)
                        })
                    }
                } else {
                    val w = imgView.measuredWidth
                    val h = imgView.measuredHeight

                    val bitmap = Bitmap.createBitmap(
                        imgView.measuredWidth,
                        imgView.measuredHeight,
                        Bitmap.Config.ARGB_8888
                    )

                    val canvas = Canvas(bitmap)
                    canvas.save()
                    //主要耗时步骤，后续可以考虑将方法进行优化，不是每次都需要进行draw
                    imgView.draw(canvas)
                    canvas.restore()

                    Log.d(TAG, "draw bitmap end")
                    val rect = Rect()
                    rect.left = if (x - cropRange > 0) x - cropRange else 0
                    rect.right = if (x + cropRange < w) x + cropRange else x
                    rect.top = if (y - cropRange > 0) y - cropRange else 0
                    rect.bottom = if (y + cropRange < h) y + cropRange else y

                    Log.w(TAG, "rect$rect")
                    val bmpCrop = Bitmap.createBitmap(
                        bitmap,
                        rect.left,
                        rect.top,
                        rect.width(),
                        rect.height()
                    )

                    Log.d(TAG, "crop bitmap end")
                    val colors = DominantColors.getDominantColors(bmpCrop, 5)
                    var maxPercent = -1f
                    for (i in colors.indices) {
                        val percent = colors[i].percentage
                        if (percent > maxPercent) {
                            result = colors[i].color
                            Log.d(TAG, "maxPercent:$percent,color:$result")
                            maxPercent = percent
                        }
                    }

                    firstTouchColor = result
                    colorCallBack.invoke(result)
                    Log.d(TAG, "dominant bitmap color end")
                    bitmap.recycle()
                }
            }
        }
        Log.d(TAG, "end..")
    }

    fun captureView(view: View, window: Window, bitmapCallback: (Bitmap) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Above Android O, use PixelCopy
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val location = IntArray(2)
            view.getLocationInWindow(location)
            PixelCopy.request(
                window,
                Rect(location[0], location[1], location[0] + view.width, location[1] + view.height),
                bitmap,
                {
                    if (it == PixelCopy.SUCCESS) {
                        bitmapCallback.invoke(bitmap)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } else {
            val tBitmap = Bitmap.createBitmap(
                view.width, view.height, Bitmap.Config.RGB_565
            )
            val canvas = Canvas(tBitmap)
            view.draw(canvas)
            canvas.setBitmap(null)
            bitmapCallback.invoke(tBitmap)
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun cropFromWindow(window: Window, rect: Rect, bitmapCallback: (Bitmap) -> Unit) {
        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        val location = IntArray(2)

        PixelCopy.request(
            window,
            rect,
            bitmap,
            {
                if (it == PixelCopy.SUCCESS) {
                    bitmapCallback.invoke(bitmap)
                }
            },
            Handler(Looper.getMainLooper())
        )
    }

    /**
     * 释放资源
     */
    fun release() {
        weakViewHolder?.clear()
        resetTouchColor()
    }

    /**
     * 一下是java的摩尔投票法，最初的方法，效果一般，已弃用
     * ————————————————————————————————————————————————————————————————————————————————————————
     */
    //采样中最接近原点的点和原点之间的x\y坐标差
    private val sampleRange = 30
    /**
     * 采样中左右上下各会走多少步
     * 一般来说，得到的集合大小是([sampleStep]*2+1)^2
     * 最差的情况:x、y位于图片的四个角上，则集合只有([sampleStep]+1)^2个点可用
     */
    private val sampleStep = 2

    /**
     * java-摩尔投票法
     */
    fun getColorMoore(x: Int, y: Int): Int {
        firstTouchColor?.let {
            //如果不为空，直接进行返回
            return it
        }
        Log.d(TAG, "start..")
        var result = -1
        weakViewHolder.let { weakIv ->
            var imgView = weakIv?.get()
            if (imgView == null) {
                Log.w(TAG, "getColor: img is null")
                result = -1
            } else {
                var bitmap = Bitmap.createBitmap(
                    imgView.measuredWidth,
                    imgView.measuredHeight,
                    Bitmap.Config.RGB_565
                )

                var canvas = Canvas(bitmap)
                canvas.save()
                //主要耗时步骤，后续可以考虑将方法进行优化，不是每次都需要进行draw
                imgView.draw(canvas)
                canvas.restore()

                var bmpW = bitmap.width
                var bmpH = bitmap.height

                /**
                 * 取出一个集合来待用。
                 */
                var pointSet = HashSet<Point>()
                val sampleLeftStep = 0 - sampleStep
                for (xi in sampleLeftStep..sampleStep) {
                    for (yi in sampleLeftStep..sampleStep) {
                        var p = Point()
                        p.x = x + sampleRange * xi
                        p.y = y + sampleRange * yi
                        pointSet.add(p)
                    }
                }

                var redList = ArrayList<Int>()
                var blueList = ArrayList<Int>()
                var greenList = ArrayList<Int>()

                for (p in pointSet) {
                    if (p.x < 0 || p.y < 0 || p.x > bmpW || p.y > bmpH) {
                        //超出边界，此点舍弃
                        break
                    }
                    var color = bitmap.getPixel(p.x, p.y)
                    Log.d(TAG, "location:${p},color:${color}")

                    /**
                     * int red = (color & 0xff0000) >> 16;
                     * int green = (color & 0x00ff00) >> 8;
                     * int blue = (color & 0x0000ff);
                     */

                    val red = color and 0xff0000 shr 16
                    val green = color and 0x00ff00 shr 8
                    val blue = color and 0x0000ff
                    Log.d(TAG, "read:${red},green:${green},blue:${blue}")
                    redList.add(red)
                    greenList.add(green)
                    blueList.add(blue)
                }
                val majorRed = majorityElement(redList)
                val majorGreen = majorityElement(greenList)
                val majorBlue = majorityElement(blueList)
                if (majorRed != null && majorGreen != null && majorBlue != null) {
                    result = Color.argb(255, majorRed, majorGreen, majorBlue)
                    Log.d(TAG, "use major color:${result}")
                } else {
                    result = bitmap.getPixel(x, y)
                    Log.d(TAG, "dont use major color:${result}")
                }

                bitmap.recycle()
            }
        }
        Log.d(TAG, "end..")

        firstTouchColor = result
        return result
    }

    /**
     * 取众数，Boyer-Moore投票法
     * 时间复杂度：O(n)
     * Boyer-Moore 算法严格执行了 n 次循环，所以时间复杂度是线性时间的。
     * 空间复杂度：O(1)
     * Boyer-Moore 只需要常数级别的额外空间。
     * 如果传入的数据为空，则返回一定为空
     * @param nums
     * @return 集合中出现次数超过n/2的值
     */
    fun majorityElement(nums: List<Int>?): Int? {
        var count = 0
        var candidate: Int? = null
        if (nums.isNullOrEmpty()) {
            return candidate
        }
        for (num in nums) {
            if (count == 0) {
                candidate = num
            }
            count += if (num == candidate) 1 else -1
        }
        return candidate
    }

}
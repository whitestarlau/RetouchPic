package com.white.retouchdemo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import com.white.dominantColor.DominantColors
import kotlinx.android.synthetic.main.activity_domincolor_test.*
import kotlinx.android.synthetic.main.activity_domincolor_test.openGallery
import java.io.IOException

class DomincolorTestActivity : AppCompatActivity() {
    val TAG = DomincolorTestActivity::class.java.simpleName
    private val PICK_REQUEST = 251
    private var nowBmp: Bitmap? = null
        private set(value) {
            field = value
            imageView.setImageBitmap(value)
            startDomincolor.visibility = View.VISIBLE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_domincolor_test)

        openGallery.setOnClickListener {
            openGallery()
        }

        startDomincolor.setOnClickListener {
            nowBmp?.let {
                resultLayout.removeAllViews()

                val result = DominantColors.getDominantColors(nowBmp, 3)
                for (color in result) {
                    Log.d(TAG, "result: ${color}")
                    val layoutParams = RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams.topMargin = 10

                    val tv = TextView(this)
                    tv.layoutParams = layoutParams
                    tv.setBackgroundColor(color.color)
                    tv.setText(color.toString())

                    resultLayout.addView(tv)
                }
            }
        }
    }

    fun openGallery() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Select Picture"),
            PICK_REQUEST
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            when (requestCode) {

                PICK_REQUEST -> try {
                    val uri = data?.data

                    if (uri != null) {
                        if (android.os.Build.VERSION.SDK_INT >= 28) {
                            Log.d(TAG, "use ImageDecoder")
                            val source = ImageDecoder.createSource(contentResolver, uri)
                            val bitmap = ImageDecoder.decodeBitmap(source)
                            nowBmp = bitmap
                        } else {
                            Log.d(TAG, "use MediaStore")
                            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            nowBmp = bitmap
                        }
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

}
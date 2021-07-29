package com.white.retouchdemo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.Window
import com.white.piceditor.OnPhotoEditorListener
import com.white.piceditor.PhotoEditor
import com.white.piceditor.ViewType
import kotlinx.android.synthetic.main.activity_retouch.*
import java.io.IOException
import java.lang.ref.WeakReference

class RetouchActivity : AppCompatActivity() {
    val TAG = RetouchActivity::class.java.simpleName
    private val PICK_REQUEST = 251
    private var mPhotoEditor: PhotoEditor? = null

    var defaultSize = 50f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_retouch)
        initPhotoEditor()
        openGallery.setOnClickListener {
            openGallery()
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
                            photoEditorView.source.setImageBitmap(bitmap)

                            val drawable = ImageDecoder.decodeDrawable(source)
                            photoEditorView.source.setImageDrawable(drawable)
                            mPhotoEditor?.clearAllViews()
                        } else {
                            Log.d(TAG, "use MediaStore")
                            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            photoEditorView.source.setImageBitmap(bitmap)
                            mPhotoEditor?.clearAllViews()
                        }
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
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

    fun initPhotoEditor() {
        mPhotoEditor = PhotoEditor.Builder(this, photoEditorView)
            .setPinchTextScalable(true) // set flag to make text scalable when pinch
            .build()
        mPhotoEditor?.setOnPhotoEditorListener(WeakListener(this))

        mPhotoEditor?.setBrushDrawingMode(true)
        mPhotoEditor?.setReTouchModeMode(true)
        mPhotoEditor?.brushSize = defaultSize
    }

    private class WeakListener(activity: RetouchActivity) : OnPhotoEditorListener {
        private val mWfPresenter: WeakReference<RetouchActivity>?

        init {
            this.mWfPresenter = WeakReference(activity)
        }

        override fun onEditTextChangeListener(rootView: View?, text: String?, colorCode: Int) {

        }

        override fun onAddViewListener(viewType: ViewType?, numberOfAddedViews: Int) {

        }

        override fun onRemoveViewListener(viewType: ViewType?, numberOfAddedViews: Int) {

        }

        override fun onStartViewChangeListener(viewType: ViewType?) {

        }

        override fun onStopViewChangeListener(viewType: ViewType?) {

        }
    }
}
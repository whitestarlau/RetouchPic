package com.white.retouchdemo

//import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.white.piceditor.OnPhotoEditorListener
import com.white.piceditor.PhotoEditor
import com.white.piceditor.ViewType
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {
    private val PICK_REQUEST = 251
    private var mPhotoEditor: PhotoEditor? = null

    var defaultSize = 50f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        openGallery()
    }

    fun openGallery() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Select Picture"),
            PICK_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            when (requestCode) {

                PICK_REQUEST -> try {
                    val uri = data?.data

                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    photoEditorView.source.setImageBitmap(bitmap)

                    initPhotoEditor()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun initPhotoEditor(){
        mPhotoEditor = PhotoEditor.Builder(this, photoEditorView)
            .setPinchTextScalable(true) // set flag to make text scalable when pinch
            .build()
        mPhotoEditor?.setOnPhotoEditorListener(WeakListener(this))

        mPhotoEditor?.setBrushDrawingMode(true)
        mPhotoEditor?.setReTouchModeMode(true)
        mPhotoEditor?.brushSize = defaultSize
    }

    private class WeakListener(activity: MainActivity) : OnPhotoEditorListener {
        private val mWfPresenter: WeakReference<MainActivity>?

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

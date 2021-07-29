package com.white.retouchdemo

//import androidx.appcompat.app.AppCompatActivity

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    val TAG = MainActivity::class.java.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        retouchTest.setOnClickListener {
            startActivity(Intent(this,RetouchActivity::class.java))
        }
        domincolorTest.setOnClickListener {
            startActivity(Intent(this,DomincolorTestActivity::class.java))
        }
    }
}

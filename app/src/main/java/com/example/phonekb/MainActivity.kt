package com.example.phonekb

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "PhoneKB (test) — app is running\nIf you see this, the launcher and icon work." 
        tv.textSize = 18f
        val pad = (16 * resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad, pad, pad)
        setContentView(tv)
    }
}

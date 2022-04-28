package com.example.bt_combine_001

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main_selection.*

class MainActivitySelection : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_selection)

        btnSelectServ.setOnClickListener{
            //server need to show Arrow to point the direction
            var intentx = Intent(this,MainActivity::class.java)
            intentx.putExtra("type",getString(R.string.TYPE_SERVER))
            startActivity(intentx)
        }
        btnSelectCli.setOnClickListener {
            var intentx = Intent(this,MainActivity::class.java)
            intentx.putExtra("type",getString(R.string.TYPE_CLIENT))
            startActivity(intentx)
        }
    }
}
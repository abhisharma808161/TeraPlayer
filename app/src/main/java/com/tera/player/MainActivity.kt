package com.tera.player

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var link: EditText
    private lateinit var ndus: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Tera Player"; textSize = 24f })
        link = EditText(this).apply { hint = "Terabox link paste karo" }
        val play = Button(this).apply { text = "Play" }
        ndus = EditText(this).apply { hint = "ndus cookie (ek baar)"; setText(sp.getString("ndus", "")) }
        root.addView(link); root.addView(play)
        root.addView(TextView(this).apply { text = "\nSettings" })
        root.addView(ndus)
        setContentView(root)
        play.setOnClickListener { go(link.text.toString()) }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        link.setText(text)
        go(text)
    }

    private fun go(text: String) {
        val url = Regex("https?://\\S+").find(text)?.value
        if (url == null) { Toast.makeText(this, "Valid link daalo", Toast.LENGTH_SHORT).show(); return }
        val n = ndus.text.toString().trim()
        if (n.isEmpty()) { Toast.makeText(this, "Pehle ndus cookie daalo", Toast.LENGTH_LONG).show(); return }
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("ndus", n).apply()
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("link", url))
    }
}

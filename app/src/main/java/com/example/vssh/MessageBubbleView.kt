package com.example.vssh

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class MessageBubbleView(context: Context) : LinearLayout(context) {
    private val container = LinearLayout(context)
    private val button = Button(context)

    init {
        orientation = VERTICAL
        setPadding(0, 0, 0, 16)

        container.orientation = VERTICAL
        container.setPadding(24, 16, 24, 16)
        addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        button.text = "Send to SSH"
        button.setBackgroundColor(ContextCompat.getColor(context, R.color.vssh_red))
        button.setTextColor(ContextCompat.getColor(context, R.color.vssh_white))
        button.visibility = View.GONE
        addView(button, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(
        isUser: Boolean,
        text: String,
        command: String?,
        canSend: Boolean,
        onSend: (String) -> Unit
    ) {
        container.removeAllViews()
        val bg = if (isUser) 0xFFE50914.toInt() else 0xFF1F1F1F.toInt()
        container.setBackgroundColor(bg)
        val gravity = if (isUser) Gravity.END else Gravity.START
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        params.gravity = gravity
        layoutParams = params

        val parts = splitByCodeBlocks(text)
        parts.forEach { part ->
            val tv = TextView(context)
            tv.text = part.content
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.textSize = 13f
            if (part.isCode) {
                tv.typeface = Typeface.MONOSPACE
                tv.setBackgroundColor(0xFF0B0B0B.toInt())
                tv.setPadding(16, 12, 16, 12)
            }
            container.addView(tv)
        }

        if (!command.isNullOrBlank() && canSend) {
            button.visibility = View.VISIBLE
            val buttonParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            buttonParams.gravity = gravity
            button.layoutParams = buttonParams
            button.setOnClickListener { onSend(command) }
        } else {
            button.visibility = View.GONE
        }
    }

    private fun splitByCodeBlocks(text: String): List<Part> {
        val parts = mutableListOf<Part>()
        var remaining = text
        while (true) {
            val start = remaining.indexOf("```")
            if (start == -1) {
                if (remaining.isNotBlank()) parts.add(Part(remaining, false))
                break
            }
            if (start > 0) {
                parts.add(Part(remaining.substring(0, start), false))
            }
            val end = remaining.indexOf("```", start + 3)
            if (end == -1) {
                parts.add(Part(remaining.substring(start), false))
                break
            }
            val code = remaining.substring(start + 3, end).trim()
            if (code.isNotBlank()) parts.add(Part(code, true))
            remaining = remaining.substring(end + 3)
        }
        return parts
    }

    private data class Part(val content: String, val isCode: Boolean)
}

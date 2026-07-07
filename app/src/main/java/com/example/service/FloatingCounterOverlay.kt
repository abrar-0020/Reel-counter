package com.example.service

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.R
import com.example.manager.SessionState
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout

class FloatingCounterOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

    private val view: View = LayoutInflater.from(context).inflate(R.layout.floating_counter_overlay, null)
    private val cardView: FrameLayout = view.findViewById(R.id.card_view)
    private val countText: TextView = view.findViewById(R.id.count_text)
    private val timeText: TextView = view.findViewById(R.id.time_text)
    private val contentContainer: LinearLayout = view.findViewById(R.id.content_container)

    private var params: WindowManager.LayoutParams
    private var isAdded = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private var currentColor = Color.GRAY
    private var isExpanded = false
    private val handler = Handler(Looper.getMainLooper())
    private val collapseRunnable = Runnable { collapse() }

    init {
        val savedX = prefs.getInt("overlay_x", 0) // Default to 0, Gravity will be top-end initially
        val savedY = prefs.getInt("overlay_y", 100)
        val hasSavedPosition = prefs.contains("overlay_x")

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        
        if (hasSavedPosition) {
            params.gravity = Gravity.TOP or Gravity.START
            params.x = savedX
            params.y = savedY
        } else {
            params.x = 48 // Approx 16dp from right
            params.y = 48 // Approx 16dp from top
        }

        setupTouchListener()
    }

    fun show() {
        if (!isAdded) {
            windowManager.addView(view, params)
            isAdded = true
            
            // Fade in animation
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(200).start()
        }
    }

    fun hide() {
        if (isAdded) {
            windowManager.removeView(view)
            isAdded = false
            handler.removeCallbacks(collapseRunnable)
        }
    }

    fun updateCount(count: Int) {
        val oldText = countText.text.toString()
        val newText = count.toString()
        
        if (oldText != newText && isAdded) {
            countText.text = newText
            // Scale bounce animation
            view.animate()
                .scaleX(1.15f).scaleY(1.15f)
                .setDuration(90)
                .withEndAction {
                    view.animate()
                        .scaleX(1.0f).scaleY(1.0f)
                        .setDuration(90)
                        .start()
                }.start()
        } else {
            countText.text = newText
        }
    }

    fun updateTime(timeString: String) {
        timeText.text = timeString
    }

    fun updateState(state: SessionState) {
        val targetColor = when (state) {
            SessionState.RUNNING -> Color.parseColor("#4CAF50") // Green
            SessionState.STOPPED -> Color.parseColor("#F44336") // Red
            SessionState.PAUSED -> Color.parseColor("#FF9800") // Orange
            SessionState.IDLE -> Color.parseColor("#9E9E9E") // Gray
        }

        if (currentColor != targetColor) {
            val animator = ValueAnimator.ofObject(ArgbEvaluator(), currentColor, targetColor)
            animator.duration = 200
            animator.addUpdateListener { anim ->
                val color = anim.animatedValue as Int
                val background = cardView.background.mutate() as GradientDrawable
                background.setColor(color)
            }
            animator.start()
            currentColor = targetColor
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        cardView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    
                    // If gravity was END, switch to START so X coordinates work intuitively
                    if ((params.gravity and Gravity.END) == Gravity.END) {
                        val screenWidth = windowManager.defaultDisplay.width
                        params.x = screenWidth - params.x - view.width
                        params.gravity = Gravity.TOP or Gravity.START
                        windowManager.updateViewLayout(view, params)
                        initialX = params.x
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    
                    if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        if (isAdded) {
                            windowManager.updateViewLayout(view, params)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        prefs.edit()
                            .putInt("overlay_x", params.x)
                            .putInt("overlay_y", params.y)
                            .apply()
                    } else {
                        handleTap()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTap() {
        if (!isExpanded) {
            expand()
        } else {
            collapse()
        }
    }

    private fun expand() {
        isExpanded = true
        timeText.visibility = View.VISIBLE
        
        // Convert 56dp to wrap_content equivalent
        val anim = ValueAnimator.ofInt(cardView.width, dpToPx(140))
        anim.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Int
            val layoutParams = cardView.layoutParams
            layoutParams.width = value
            cardView.layoutParams = layoutParams
            if (isAdded) {
                windowManager.updateViewLayout(view, params)
            }
        }
        anim.duration = 200
        anim.start()

        handler.removeCallbacks(collapseRunnable)
        handler.postDelayed(collapseRunnable, 3000)
    }

    private fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        
        val anim = ValueAnimator.ofInt(cardView.width, dpToPx(56))
        anim.addUpdateListener { valueAnimator ->
            val value = valueAnimator.animatedValue as Int
            val layoutParams = cardView.layoutParams
            layoutParams.width = value
            cardView.layoutParams = layoutParams
            if (isAdded) {
                windowManager.updateViewLayout(view, params)
            }
        }
        anim.duration = 200
        anim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                timeText.visibility = View.GONE
            }
        })
        anim.start()
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

package org.jellyfin.androidtv.aeon

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.target
import org.jellyfin.androidtv.R

/**
 * Shared lean UI helpers for the AeonTV TV screens: classic Views built in
 * code, remote-friendly (focusable cards with a visible accent ring),
 * dark background #170A2E, accent #00C8F0, large text for TV distances.
 */
object AeonUi {
    const val BG = 0xFF170A2E.toInt()
    const val CARD = 0xFF241239.toInt()
    const val ACCENT = 0xFF00C8F0.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val SUB = 0xFFB8A8D8.toInt()

    val TILE_COLORS = intArrayOf(
        0xFF1565C0.toInt(), 0xFF6A1B9A.toInt(), 0xFF00695C.toInt(), 0xFFAD1457.toInt(),
        0xFF4527A0.toInt(), 0xFF00838F.toInt(), 0xFF4E342E.toInt(), 0xFF37474F.toInt(),
        0xFF827717.toInt(), 0xFFBF360C.toInt(),
    )

    fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** Full-screen root: dark background, TV-safe padding. */
    fun Context.screenRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BG)
        val p = dp(48)
        setPadding(p, dp(32), p, dp(32))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    /** Screen title, large and bold. */
    fun Context.titleView(text: String, sizeSp: Int = 44): TextView = TextView(this).apply {
        this.text = text
        setTextColor(TEXT)
        textSize = sizeSp.toFloat()
        typeface = Typeface.DEFAULT_BOLD
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(8) }
    }

    fun Context.subtitleView(text: String, sizeSp: Int = 22): TextView = TextView(this).apply {
        this.text = text
        setTextColor(SUB)
        textSize = sizeSp.toFloat()
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(24) }
    }

    /** A big focusable menu card (a "door"). */
    fun Context.menuCard(title: String, subtitle: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setBackgroundResource(R.drawable.aeon_card)
            val p = dp(36)
            setPadding(p, dp(28), p, dp(28))
            addView(TextView(context).apply {
                text = title
                setTextColor(TEXT)
                textSize = 34f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = subtitle
                setTextColor(SUB)
                textSize = 22f
                setPadding(0, dp(6), 0, 0)
            })
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(20) }
            setOnClickListener { onClick() }
        }

    /** A focusable list row with a title and an optional detail line. */
    fun Context.listRow(title: String, detail: String?, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setBackgroundResource(R.drawable.aeon_card)
            setPadding(dp(28), dp(18), dp(28), dp(18))
            addView(TextView(context).apply {
                text = title
                setTextColor(TEXT)
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
            })
            if (!detail.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = detail
                    setTextColor(SUB)
                    textSize = 20f
                    maxLines = 2
                    setPadding(0, dp(4), 0, 0)
                })
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) }
            setOnClickListener { onClick() }
        }

    fun Context.loadingView(text: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(BG)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        addView(ProgressBar(this@loadingView).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72)).apply { bottomMargin = dp(24) }
        })
        addView(TextView(this@loadingView).apply {
            this.text = text
            setTextColor(SUB)
            textSize = 24f
            gravity = Gravity.CENTER
        })
    }

    fun Context.errorView(message: String, retryLabel: String, onRetry: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(TextView(this@errorView).apply {
                text = message
                setTextColor(TEXT)
                textSize = 28f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(48), 0, dp(48), dp(16))
            })
            addView(accentButton(retryLabel, onRetry))
        }

    fun Context.accentButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 24f
        isFocusable = true
        setBackgroundResource(R.drawable.aeon_card)
        setTextColor(TEXT)
        setPadding(dp(48), dp(16), dp(48), dp(16))
        setOnClickListener { onClick() }
    }

    fun Context.toast(text: String) =
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    /** Rounded solid drawable used for channel initials tiles. */
    fun tileBackground(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 20f
        setColor(color)
    }

    fun tileColorFor(name: String): Int =
        TILE_COLORS[(name.hashCode() and 0x7fffffff) % TILE_COLORS.size]

    /** Grid RecyclerView tuned for TV focus navigation. */
    fun Context.posterGrid(spanCount: Int): RecyclerView = RecyclerView(this).apply {
        layoutManager = GridLayoutManager(this@posterGrid, spanCount)
        setBackgroundColor(BG)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
    }

    fun Context.verticalList(): RecyclerView = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(this@verticalList)
        setBackgroundColor(BG)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
    }

    /** A poster cell for grids: image (2:3) + title below. */
    open class PosterHolder(val root: LinearLayout, val image: ImageView, val label: TextView) :
        RecyclerView.ViewHolder(root)

    fun Context.posterHolder(parent: ViewGroup, onClick: (Int) -> Unit): PosterHolder {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            setBackgroundResource(R.drawable.aeon_card)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
            ).apply { weight = 1f }
            // 2:3 poster ratio is approximated by the grid column width and
            // the row height; the crop keeps tiles uniform.
            minimumHeight = dp(180)
        }
        val label = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 20f
            maxLines = 2
            setPadding(dp(6), dp(8), dp(6), dp(4))
        }
        root.addView(image)
        root.addView(label)
        root.setOnClickListener { onClick(root.tag as? Int ?: -1) }
        return PosterHolder(root, image, label)
    }

    fun ImageView.loadThumb(url: String?, imageLoader: ImageLoader) {
        if (url.isNullOrBlank()) {
            setImageDrawable(null)
            return
        }
        imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .target(this)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
        )
    }

    /** Initials tile used in channel rows. */
    fun Context.initialsTile(name: String, sizeDp: Int = 72): TextView {
        val initials = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
        return TextView(this).apply {
            text = initials
            setTextColor(TEXT)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = tileBackground(tileColorFor(name))
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
    }

    /** One channel row: initials tile, name, now/next + progress, focus ring. */
    fun buildChannelRow(
        context: Context,
        channel: LiveChannel,
        data: LiveTvData,
        onPlay: () -> Unit,
    ): View {
        val now = System.currentTimeMillis()
        val current = data.currentProgram(channel.id, now)
        val next = data.nextProgram(channel.id, now)
        return with(context) {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                setBackgroundResource(R.drawable.aeon_card)
                setPadding(dp(24), dp(16), dp(24), dp(16))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(10) }
                addView(initialsTile(channel.name))
                val middle = LinearLayout(this@with).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { leftMargin = dp(20) }
                }
                middle.addView(TextView(this@with).apply {
                    text = channel.name
                    setTextColor(TEXT)
                    textSize = 28f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                })
                if (current != null) {
                    middle.addView(TextView(this@with).apply {
                        text = current.title
                        setTextColor(ACCENT)
                        textSize = 22f
                        maxLines = 1
                        setPadding(0, dp(4), 0, 0)
                    })
                    val fraction = if (current.stopMs > current.startMs) {
                        ((now - current.startMs).toFloat() / (current.stopMs - current.startMs)).coerceIn(0f, 1f)
                    } else 0f
                    middle.addView(ProgressBar(this@with, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 1000
                        progress = (fraction * 1000).toInt()
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            dp(10),
                        ).apply { topMargin = dp(8) }
                    })
                    val until = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
                        .format(java.util.Date(current.stopMs))
                    val nextText = if (next != null) "  ·  Next: ${next.title}" else ""
                    middle.addView(TextView(this@with).apply {
                        text = "until $until$nextText"
                        setTextColor(SUB)
                        textSize = 18f
                        maxLines = 1
                        setPadding(0, dp(4), 0, 0)
                    })
                } else {
                    middle.addView(TextView(this@with).apply {
                        text = "LIVE"
                        setTextColor(ACCENT)
                        textSize = 22f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(0, dp(4), 0, 0)
                    })
                }
                addView(middle)
                setOnClickListener { onPlay() }
            }
        }
    }
}

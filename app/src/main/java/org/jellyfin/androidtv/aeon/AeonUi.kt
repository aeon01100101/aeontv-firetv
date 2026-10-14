package org.jellyfin.androidtv.aeon

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
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
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R

/**
 * Shared lean UI helpers for the AeonTV TV screens: classic Views built in
 * code, remote-friendly (unmistakable focus: scale + cyan ring + shadow,
 * idle cards slightly dimmed), deep-purple-to-black gradients, accent
 * #00C8F0, large text for TV distances.
 */
object AeonUi {
    const val BG = 0xFF170A2E.toInt()
    const val BG_BOTTOM = 0xFF0A0616.toInt()
    const val CARD = 0xFF241239.toInt()
    const val ACCENT = 0xFF00C8F0.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val SUB = 0xFFB8A8D8.toInt()

    private const val FOCUS_SCALE = 1.06f
    private const val FOCUS_MS = 200L
    private const val IDLE_ALPHA = 0.84f

    val TILE_COLORS = intArrayOf(
        0xFF1565C0.toInt(), 0xFF6A1B9A.toInt(), 0xFF00695C.toInt(), 0xFFAD1457.toInt(),
        0xFF4527A0.toInt(), 0xFF00838F.toInt(), 0xFF4E342E.toInt(), 0xFF37474F.toInt(),
        0xFF827717.toInt(), 0xFFBF360C.toInt(),
    )

    /** Avatar palette for local profiles (~8 colors). */
    val PROFILE_COLORS = intArrayOf(
        0xFF1565C0.toInt(), 0xFF6A1B9A.toInt(), 0xFF00695C.toInt(), 0xFFAD1457.toInt(),
        0xFF4527A0.toInt(), 0xFF00838F.toInt(), 0xFFBF360C.toInt(), 0xFF827717.toInt(),
    )

    fun profileColor(index: Int): Int =
        PROFILE_COLORS[(index % PROFILE_COLORS.size + PROFILE_COLORS.size) % PROFILE_COLORS.size]

    /** Circular avatar: profile color + initial letter. */
    fun Context.avatarView(color: Int, initial: String, sizeDp: Int = 96): TextView =
        TextView(this).apply {
            text = initial.take(1).uppercase()
            setTextColor(TEXT)
            textSize = (sizeDp * 0.42).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }

    fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /**
     * The shared focus feel, unmistakable from across the room: the card
     * grows to ~1.06x with a cyan ring (from the aeon_card selector) and a
     * soft shadow over 200ms; idle cards sit slightly dimmed so the focused
     * one pops. DPAD traversal order is untouched — this is purely visual.
     */
    fun View.applyAeonFocus(dimIdle: Boolean = true) {
        if (dimIdle && !isFocused) alpha = IDLE_ALPHA
        setOnFocusChangeListener { v, focused ->
            v.pivotX = (v.width / 2).toFloat()
            v.pivotY = (v.height / 2).toFloat()
            v.animate().cancel()
            v.animate()
                .scaleX(if (focused) FOCUS_SCALE else 1f)
                .scaleY(if (focused) FOCUS_SCALE else 1f)
                .alpha(if (focused || !dimIdle) 1f else IDLE_ALPHA)
                .setDuration(FOCUS_MS)
                .start()
            v.elevation = if (focused) v.context.dp(10).toFloat() else 0f
        }
    }

    /** Snap a recycled card back to the transform matching its focus state. */
    fun View.resetAeonFocus(dimIdle: Boolean = true) {
        val focused = isFocused
        scaleX = if (focused) FOCUS_SCALE else 1f
        scaleY = if (focused) FOCUS_SCALE else 1f
        alpha = if (focused || !dimIdle) 1f else IDLE_ALPHA
        elevation = 0f
    }

    /** Rounded-corner clipping for image views. */
    fun View.roundedCorners(radiusPx: Float) {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
    }

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

    /** Vertical gradient: deep purple up top fading to near-black. */
    fun homeGradient(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(BG, BG_BOTTOM),
    )

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

    /** Hero banner for the home screen: the AeonTV wordmark art. */
    fun Context.heroBanner(): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.app_banner_foreground)
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
        isFocusable = false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(200),
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
            bottomMargin = dp(8)
        }
    }

    /** Small footer line: AeonTV + the app version. */
    fun Context.footerLine(): TextView = TextView(this).apply {
        text = "AeonTV · ${appVersion()}"
        setTextColor(SUB)
        textSize = 18f
        gravity = Gravity.CENTER
        alpha = 0.8f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(16) }
    }

    fun appVersion(): String = try {
        "v${BuildConfig.VERSION_NAME}"
    } catch (e: Exception) {
        ""
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
            applyAeonFocus(dimIdle = true)
        }

    /**
     * A large door card with artwork on the left (Home / Other media):
     * artwork tile + big title + subtitle.
     */
    fun Context.doorCard(title: String, subtitle: String, art: View, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setBackgroundResource(R.drawable.aeon_card)
            setPadding(dp(32), dp(28), dp(40), dp(28))
            minimumHeight = dp(232)
            art.layoutParams = LinearLayout.LayoutParams(dp(176), dp(176)).apply {
                rightMargin = dp(32)
            }
            addView(art)
            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(context).apply {
                text = title
                setTextColor(TEXT)
                textSize = 40f
                typeface = Typeface.DEFAULT_BOLD
            })
            textCol.addView(TextView(context).apply {
                text = subtitle
                setTextColor(SUB)
                textSize = 24f
                setPadding(0, dp(8), 0, 0)
            })
            addView(textCol)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(24) }
            setOnClickListener { onClick() }
            applyAeonFocus(dimIdle = true)
        }

    /** Rounded art tile with a brand gradient behind the given content. */
    fun Context.artTile(child: View): FrameLayout = FrameLayout(this).apply {
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF2E1A4D.toInt(), 0xFF12081F.toInt()),
        ).apply { cornerRadius = dp(24).toFloat() }
        roundedCorners(dp(24).toFloat())
        addView(child)
    }

    /** The AeonTV logo mark as door artwork. */
    fun Context.doorArtLogo(): View = artTile(
        ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
        },
    )

    /** A big glyph (play, live dot, …) as door artwork. */
    fun Context.doorArtGlyph(glyph: String, glyphColor: Int = ACCENT): View = artTile(
        TextView(this).apply {
            text = glyph
            setTextColor(glyphColor)
            textSize = 84f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
        },
    )

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
            applyAeonFocus(dimIdle = false)
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
            indeterminateTintList = ColorStateList.valueOf(ACCENT)
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
        applyAeonFocus(dimIdle = true)
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

    /** Grid RecyclerView tuned for TV focus navigation, with cell spacing. */
    fun Context.posterGrid(spanCount: Int): RecyclerView = RecyclerView(this).apply {
        layoutManager = GridLayoutManager(this@posterGrid, spanCount)
        setBackgroundColor(BG)
        addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                val s = this@posterGrid.dp(16)
                outRect.set(s / 2, s / 2, s / 2, s / 2)
            }
        })
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

    /** Horizontal poster row for TV focus navigation, with cell spacing. */
    fun Context.horizontalRow(): RecyclerView = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(this@horizontalRow, LinearLayoutManager.HORIZONTAL, false)
        setBackgroundColor(BG)
        addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                val s = this@horizontalRow.dp(16)
                outRect.set(s / 2, 0, s / 2, 0)
            }
        })
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(8) }
    }

    /**
     * A poster cell: 2:3 artwork with rounded corners, title on a gradient
     * scrim across the bottom, year underneath.
     */
    open class PosterHolder(
        val root: FrameLayout,
        val image: ImageView,
        val label: TextView,
        val year: TextView,
    ) : RecyclerView.ViewHolder(root)

    fun Context.posterHolder(parent: ViewGroup, onClick: (Int) -> Unit): PosterHolder {
        val corner = dp(16).toFloat()
        val root = object : FrameLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec)
                super.onMeasure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec((width * 1.5f).toInt(), MeasureSpec.EXACTLY),
                )
            }
        }.apply {
            isFocusable = true
            isClickable = true
            setBackgroundResource(R.drawable.aeon_card)
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            roundedCorners(corner)
        }
        val scrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xE6000000.toInt(), 0x00000000.toInt()),
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(150),
                Gravity.BOTTOM,
            )
            roundedCorners(corner)
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(16), dp(8), dp(16), dp(14))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val label = TextView(this).apply {
            setTextColor(TEXT)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
        }
        val year = TextView(this).apply {
            setTextColor(SUB)
            textSize = 18f
            maxLines = 1
        }
        textCol.addView(label)
        textCol.addView(year)
        root.addView(image)
        root.addView(scrim)
        root.addView(textCol)
        root.setOnClickListener { onClick(root.tag as? Int ?: -1) }
        root.applyAeonFocus(dimIdle = true)
        return PosterHolder(root, image, label, year)
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

    /** Section bar for Live TV groups: cyan tick + label. */
    fun Context.groupHeader(name: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(4).toFloat()
                setColor(ACCENT)
            }
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(42)).apply {
                rightMargin = dp(16)
            }
        })
        addView(TextView(context).apply {
            text = name
            tag = "aeon_group_label"
            setTextColor(ACCENT)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
        })
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(28)
            bottomMargin = dp(10)
        }
    }

    /** One channel row: initials tile, bold name, now/next + cyan progress, focus glow. */
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
                    textSize = 30f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                })
                if (current != null) {
                    middle.addView(TextView(this@with).apply {
                        text = current.title
                        setTextColor(ACCENT)
                        textSize = 24f
                        maxLines = 1
                        setPadding(0, dp(4), 0, 0)
                    })
                    val fraction = if (current.stopMs > current.startMs) {
                        ((now - current.startMs).toFloat() / (current.stopMs - current.startMs)).coerceIn(0f, 1f)
                    } else 0f
                    middle.addView(ProgressBar(this@with, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 1000
                        progress = (fraction * 1000).toInt()
                        progressTintList = ColorStateList.valueOf(ACCENT)
                        progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
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
                applyAeonFocus(dimIdle = false)
            }
        }
    }

    /**
     * Full-bleed detail backdrop: the item's art dimmed with a dark scrim
     * so the content text on top stays readable.
     */
    fun Context.detailRoot(content: View, imageData: Any?, loader: ImageLoader?): FrameLayout {
        val root = FrameLayout(this)
        val art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.34f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        val scrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x55000000.toInt(), BG, BG),
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        root.addView(art)
        root.addView(scrim)
        root.addView(content)
        if (loader != null && imageData != null) {
            loader.enqueue(
                ImageRequest.Builder(this)
                    .data(imageData)
                    .target(art)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
            )
        }
        return root
    }
}

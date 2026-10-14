package org.jellyfin.androidtv.aeon

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.aeon.AeonUi.PosterHolder
import org.jellyfin.androidtv.aeon.AeonUi.dp
import org.jellyfin.androidtv.aeon.AeonUi.errorView
import org.jellyfin.androidtv.aeon.AeonUi.listRow
import org.jellyfin.androidtv.aeon.AeonUi.loadThumb
import org.jellyfin.androidtv.aeon.AeonUi.loadingView
import org.jellyfin.androidtv.aeon.AeonUi.posterGrid
import org.jellyfin.androidtv.aeon.AeonUi.posterHolder
import org.jellyfin.androidtv.aeon.AeonUi.screenRoot
import org.jellyfin.androidtv.aeon.AeonUi.titleView
import org.jellyfin.androidtv.aeon.AeonUi.toast

/**
 * Archive Mode: browse and search the public archive.org catalog.
 * Sphere files inside an item list and play like ordinary videos
 * (invisible .sphere support).
 */
private const val TYPE_POSTER = 0
private const val TYPE_MORE = 1

class ArchiveActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var imageLoader: ImageLoader

    private var mode: Mode = Mode.Category(ArchiveApi.CATEGORIES.first())
    private var page = 1
    private var canLoadMore = false
    private val items = mutableListOf<ArchiveItem>()
    private var adapter: ArchiveAdapter? = null
    private var grid: RecyclerView? = null
    private var loading = false

    private sealed interface Mode {
        data class Category(val category: Pair<String, String>) : Mode
        data class Search(val query: String) : Mode
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageLoader = OriginalThumbnails.get(this)
        val root = screenRoot()
        root.addView(titleView("Browse the Archive", 52))

        // Search row
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(16) }
        }
        val searchBox = EditText(this).apply {
            hint = "Search archive.org"
            textSize = 24f
            setTextColor(AeonUi.TEXT)
            setHintTextColor(AeonUi.SUB)
            isFocusable = true
            isFocusableInTouchMode = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { rightMargin = dp(16) }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    runSearch(text.toString())
                    true
                } else false
            }
        }
        searchRow.addView(searchBox)
        searchRow.addView(
            with(this) {
                listRow("Search", null as String?) {
                    runSearch(searchBox.text.toString())
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(dp(220), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
            },
        )
        root.addView(searchRow)

        // Category chips
        val chips = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(16) }
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (category in ArchiveApi.CATEGORIES) {
            chipRow.addView(
                with(this) {
                    listRow(category.first, null as String?) {
                        mode = Mode.Category(category)
                        reload()
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply { rightMargin = dp(12) }
                    }
                },
            )
        }
        chips.addView(chipRow)
        root.addView(chips)

        val gridView = posterGrid(4)
        grid = gridView
        adapter = ArchiveAdapter()
        gridView.adapter = adapter
        root.addView(gridView)
        setContentView(root)
        reload()
    }

    private fun runSearch(query: String) {
        if (query.isBlank()) {
            toast("Type something to search for")
            return
        }
        mode = Mode.Search(query.trim())
        reload()
    }

    private fun reload() {
        page = 1
        items.clear()
        adapter?.notifyDataSetChanged()
        loadPage()
    }

    private fun loadPage() {
        if (loading) return
        loading = true
        toast("Loading…")
        val currentMode = mode
        val nextPage = page
        scope.launch {
            val fresh = try {
                withContext(Dispatchers.IO) {
                    when (currentMode) {
                        is Mode.Category -> ArchiveApi.browse(currentMode.category.second, nextPage)
                        is Mode.Search -> ArchiveApi.search(currentMode.query, nextPage)
                    }
                }
            } catch (e: Exception) {
                loading = false
                toast("Couldn't reach archive.org")
                return@launch
            }
            loading = false
            canLoadMore = fresh.size >= ArchiveApi.PAGE_SIZE
            items.addAll(fresh)
            adapter?.notifyDataSetChanged()
            if (items.isEmpty()) toast("Nothing found")
        }
    }

    private inner class ArchiveAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = items.size + if (canLoadMore) 1 else 0

        override fun getItemViewType(position: Int): Int =
            if (position < items.size) TYPE_POSTER else TYPE_MORE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == TYPE_MORE) {
                val row = with(this@ArchiveActivity) {
                    listRow("More…", "Load the next page") {
                        page += 1
                        loadPage()
                    }
                }
                object : RecyclerView.ViewHolder(row) {}
            } else {
                with(this@ArchiveActivity) {
                    posterHolder(parent) { position ->
                        if (position in items.indices) {
                            val item = items[position]
                            ArchiveDetailActivity.start(
                                this@ArchiveActivity,
                                item.identifier,
                                item.title,
                                item.year,
                                item.creator,
                                item.description,
                            )
                        }
                    }
                }
            }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is PosterHolder && position < items.size) {
                val item = items[position]
                holder.root.tag = position
                holder.image.loadThumb(item.thumbnailUrl, imageLoader)
                val year = item.year?.let { " ($it)" } ?: ""
                holder.label.text = item.title + year
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ArchiveActivity::class.java))
        }
    }
}

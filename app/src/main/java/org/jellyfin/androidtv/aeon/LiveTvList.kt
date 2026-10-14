package org.jellyfin.androidtv.aeon

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.jellyfin.androidtv.aeon.AeonUi.buildChannelRow
import org.jellyfin.androidtv.aeon.AeonUi.dp
import org.jellyfin.androidtv.aeon.AeonUi.titleView

/**
 * Shared grouped channel list for both Live TV screens: one section per
 * group, rows with now/next programme info and progress.
 */
object LiveTvList {
    fun build(
        context: Context,
        data: LiveTvData,
        onPlay: (LiveChannel) -> Unit,
    ): View {
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            setBackgroundColor(AeonUi.BG)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        val grouped: List<Pair<String, List<LiveChannel>>> =
            data.channels.groupBy { it.group }.toList()
        list.adapter = ChannelAdapter(context, data, grouped, onPlay)
        return list
    }

    private class ChannelAdapter(
        private val context: Context,
        private val data: LiveTvData,
        private val grouped: List<Pair<String, List<LiveChannel>>>,
        private val onPlay: (LiveChannel) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val flat: List<Any> = buildList {
            for ((group, channels) in grouped) {
                add(group)
                addAll(channels)
            }
        }

        override fun getItemCount(): Int = flat.size

        override fun getItemViewType(position: Int): Int =
            if (flat[position] is String) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val header = with(context) {
                    titleView("", 28).apply {
                        setTextColor(AeonUi.ACCENT)
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = context.dp(20)
                            bottomMargin = context.dp(6)
                        }
                    }
                }
                object : RecyclerView.ViewHolder(header) {}
            } else {
                // Placeholder; replaced in onBindViewHolder.
                object : RecyclerView.ViewHolder(
                    android.widget.FrameLayout(context),
                ) {}
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = flat[position]
            if (item is String) {
                (holder.itemView as android.widget.TextView).text = item
            } else if (item is LiveChannel) {
                val row = with(context) {
                    buildChannelRow(context, item, data) { onPlay(item) }
                }
                val parent = holder.itemView as android.widget.FrameLayout
                parent.removeAllViews()
                parent.addView(
                    row,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        }
    }
}

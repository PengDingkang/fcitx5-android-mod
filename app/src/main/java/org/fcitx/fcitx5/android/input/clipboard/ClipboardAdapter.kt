/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.clipboard

import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.clipboard.db.ClipboardEntry
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.utils.DeviceUtil
import org.fcitx.fcitx5.android.utils.item
import splitties.dimensions.dp
import splitties.resources.styledColor
import splitties.views.setPaddingDp
import kotlin.math.min

sealed class ClipboardListItem {
    data class Header(@StringRes val title: Int) : ClipboardListItem()
    data class Entry(val entry: ClipboardEntry) : ClipboardListItem()
}

abstract class ClipboardAdapter(
    private val theme: Theme,
    private val entryRadius: Float,
    private val maskSensitive: Boolean
) : PagingDataAdapter<ClipboardListItem, ClipboardAdapter.ViewHolder>(diffCallback) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1

        private val diffCallback = object : DiffUtil.ItemCallback<ClipboardListItem>() {
            override fun areItemsTheSame(
                oldItem: ClipboardListItem,
                newItem: ClipboardListItem
            ): Boolean {
                return when {
                    oldItem is ClipboardListItem.Header && newItem is ClipboardListItem.Header ->
                        oldItem.title == newItem.title
                    oldItem is ClipboardListItem.Entry && newItem is ClipboardListItem.Entry ->
                        oldItem.entry.id == newItem.entry.id
                    else -> false
                }
            }

            override fun areContentsTheSame(
                oldItem: ClipboardListItem,
                newItem: ClipboardListItem
            ): Boolean {
                return oldItem == newItem
            }
        }

        /**
         * excerpt text to show on ClipboardEntryUi, to reduce render time of very long text
         * @param str text to excerpt
         * @param mask mask text content with "•"
         * @param lines max output lines
         * @param chars max chars per output line
         */
        fun excerptText(
            str: String,
            mask: Boolean = false,
            lines: Int = 4,
            chars: Int = 128
        ): String = buildString {
            val length = str.length
            var lineBreak = -1
            for (i in 1..lines) {
                val start = lineBreak + 1   // skip previous '\n'
                val excerptEnd = min(start + chars, length)
                lineBreak = str.indexOf('\n', start)
                if (lineBreak < 0) {
                    // no line breaks remaining, substring to end of text
                    if (mask) {
                        append(ClipboardEntry.BULLET.repeat(excerptEnd - start))
                    } else {
                        append(str.substring(start, excerptEnd))
                    }
                    break
                } else {
                    val end = min(excerptEnd, lineBreak)
                    // append one line exactly
                    if (mask) {
                        append(ClipboardEntry.BULLET.repeat(end - start))
                    } else {
                        appendLine(str.substring(start, end))
                    }
                }
            }
        }
    }

    private var popupMenu: PopupMenu? = null

    sealed class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        class Header(val textView: TextView) : ViewHolder(textView)
        class Entry(val entryUi: ClipboardEntryUi) : ViewHolder(entryUi.root)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ClipboardListItem.Header -> VIEW_TYPE_HEADER
        is ClipboardListItem.Entry -> VIEW_TYPE_ENTRY
        null -> VIEW_TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        when (viewType) {
            VIEW_TYPE_HEADER -> ViewHolder.Header(TextView(parent.context).apply {
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0f
                setTextColor(theme.altKeyTextColor)
                setPaddingDp(8, 8, 8, 2)
                minHeight = parent.context.dp(28)
            })
            else -> ViewHolder.Entry(ClipboardEntryUi(parent.context, theme, entryRadius))
        }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ClipboardListItem.Header -> bindHeader(holder as ViewHolder.Header, item)
            is ClipboardListItem.Entry -> bindEntry(holder as ViewHolder.Entry, item.entry)
            null -> return
        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        setFullSpan(holder, holder is ViewHolder.Header)
    }

    private fun setFullSpan(holder: ViewHolder, fullSpan: Boolean) {
        (holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan =
            fullSpan
    }

    private fun bindHeader(holder: ViewHolder.Header, item: ClipboardListItem.Header) {
        setFullSpan(holder, true)
        holder.textView.setText(item.title)
    }

    private fun bindEntry(holder: ViewHolder.Entry, entry: ClipboardEntry) {
        setFullSpan(holder, false)
        with(holder.entryUi) {
            setEntry(excerptText(entry.text, entry.sensitive && maskSensitive), entry.pinned)
            root.setOnClickListener {
                onPaste(entry)
            }
            root.setOnLongClickListener {
                val popup = PopupMenu(ctx, root)
                val menu = popup.menu
                val iconTint = ctx.styledColor(android.R.attr.colorControlNormal)
                if (entry.pinned) {
                    menu.item(R.string.unpin, R.drawable.ic_outline_push_pin_24, iconTint) {
                        onUnpin(entry.id)
                    }
                } else {
                    menu.item(R.string.pin, R.drawable.ic_baseline_push_pin_24, iconTint) {
                        onPin(entry.id)
                    }
                }
                menu.item(R.string.edit, R.drawable.ic_baseline_edit_24, iconTint) {
                    onEdit(entry.id)
                }
                menu.item(R.string.share, R.drawable.ic_baseline_share_24, iconTint) {
                    onShare(entry)
                }
                menu.item(R.string.delete, R.drawable.ic_baseline_delete_24, iconTint) {
                    onDelete(entry.id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !DeviceUtil.isSamsungOneUI && !DeviceUtil.isFlyme) {
                    popup.setForceShowIcon(true)
                }
                popup.setOnDismissListener {
                    if (it === popupMenu) popupMenu = null
                }
                popupMenu?.dismiss()
                popupMenu = popup
                popup.show()
                true
            }
        }
    }

    fun getEntryAt(position: Int) = (getItem(position) as? ClipboardListItem.Entry)?.entry

    fun onDetached() {
        popupMenu?.dismiss()
        popupMenu = null
    }

    abstract fun onPaste(entry: ClipboardEntry)

    abstract fun onPin(id: Int)

    abstract fun onUnpin(id: Int)

    abstract fun onEdit(id: Int)

    abstract fun onShare(entry: ClipboardEntry)

    abstract fun onDelete(id: Int)

}

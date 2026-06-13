/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.settings.theme

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.SeekBar
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.Theme.Custom.CustomBackground.KeyContrastMode
import org.fcitx.fcitx5.android.data.theme.ThemeFilesManager
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.ui.common.withLoadingDialog
import org.fcitx.fcitx5.android.ui.main.CropImageActivity.CropContract
import org.fcitx.fcitx5.android.ui.main.CropImageActivity.CropOption
import org.fcitx.fcitx5.android.ui.main.CropImageActivity.CropResult
import org.fcitx.fcitx5.android.utils.DarkenColorFilter
import org.fcitx.fcitx5.android.utils.item
import org.fcitx.fcitx5.android.utils.parcelable
import splitties.dimensions.dp
import splitties.resources.color
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.resources.styledDrawable
import splitties.views.backgroundColor
import splitties.views.bottomPadding
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.packed
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToTopOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.seekBar
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.core.wrapInScrollView
import splitties.views.gravityVerticalCenter
import splitties.views.horizontalPadding
import splitties.views.textAppearance
import splitties.views.topPadding
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class CustomThemeActivity : AppCompatActivity() {

    sealed interface BackgroundResult : Parcelable {
        @Parcelize
        data class Updated(val theme: Theme.Custom) : BackgroundResult

        @Parcelize
        data class Created(val theme: Theme.Custom) : BackgroundResult

        @Parcelize
        data class Deleted(val name: String) : BackgroundResult
    }

    class Contract : ActivityResultContract<Theme.Custom?, BackgroundResult?>() {
        override fun createIntent(context: Context, input: Theme.Custom?): Intent =
            Intent(context, CustomThemeActivity::class.java).apply {
                putExtra(ORIGIN_THEME, input)
            }

        override fun parseResult(resultCode: Int, intent: Intent?): BackgroundResult? =
            intent?.parcelable(RESULT)
    }

    private val toolbar by lazy {
        view(::Toolbar) {
            backgroundColor = styledColor(android.R.attr.colorPrimary)
            elevation = dp(4f)
        }
    }

    private lateinit var previewUi: KeyboardPreviewUi

    private fun createTextView(@StringRes string: Int? = null, ripple: Boolean = false) = textView {
        if (string != null) {
            setText(string)
        }
        gravity = gravityVerticalCenter
        textAppearance = resolveThemeAttribute(android.R.attr.textAppearanceListItem)
        horizontalPadding = dp(16)
        if (ripple) {
            background = styledDrawable(android.R.attr.selectableItemBackground)
        }
    }

    private val keyContrastLabel by lazy {
        createTextView(R.string.key_contrast_mode, ripple = true)
    }
    private val keyContrastValue by lazy {
        createTextView(ripple = true)
    }

    private val brightnessLabel by lazy {
        createTextView(R.string.brightness)
    }
    private val brightnessValue by lazy {
        createTextView()
    }
    private val brightnessSeekBar by lazy {
        seekBar {
            max = 100
        }
    }

    private val cropLabel by lazy {
        createTextView(R.string.recrop_image, ripple = true)
    }

    private val scrollView by lazy {
        val lineHeight = dp(48)
        val itemMargin = dp(30)
        constraintLayout {
            bottomPadding = dp(24)
            add(previewUi.root, lParams(wrapContent, wrapContent) {
                topOfParent()
                centerHorizontally()
                above(cropLabel, dp(8))
                verticalChainStyle = packed
            })
            add(cropLabel, lParams(matchConstraints, lineHeight) {
                below(previewUi.root)
                centerHorizontally(itemMargin)
                above(keyContrastLabel)
            })
            add(keyContrastLabel, lParams(matchConstraints, lineHeight) {
                below(cropLabel)
                startOfParent(itemMargin)
                before(keyContrastValue)
                above(brightnessLabel)
            })
            add(keyContrastValue, lParams(wrapContent, lineHeight) {
                topToTopOf(keyContrastLabel)
                endOfParent(itemMargin)
            })
            add(brightnessLabel, lParams(matchConstraints, lineHeight) {
                below(keyContrastLabel)
                startOfParent(itemMargin)
                before(brightnessValue)
                above(brightnessSeekBar)
            })
            add(brightnessValue, lParams(wrapContent, lineHeight) {
                topToTopOf(brightnessLabel)
                endOfParent(itemMargin)
            })
            add(brightnessSeekBar, lParams(matchConstraints, wrapContent) {
                below(brightnessLabel)
                centerHorizontally(itemMargin)
                bottomOfParent()
            })
        }.wrapInScrollView {
            isFillViewport = true
        }
    }

    private val ui by lazy {
        constraintLayout {
            add(toolbar, lParams(matchParent, wrapContent) {
                topOfParent()
                centerHorizontally()
            })
            add(scrollView, lParams {
                below(toolbar)
                centerHorizontally()
                bottomOfParent()
            })
        }
    }

    private var newCreated = true

    private lateinit var theme: Theme.Custom

    private class BackgroundStates {
        lateinit var launcher: ActivityResultLauncher<CropOption>
        var srcImageExtension: String? = null
        var srcImageBuffer: ByteArray? = null
        var cropRect: Rect? = null
        var cropRotation: Int = 0
        lateinit var croppedBitmap: Bitmap
        lateinit var filteredDrawable: BitmapDrawable
        lateinit var srcImageFile: File
        lateinit var croppedImageFile: File
    }

    private val backgroundStates by lazy { BackgroundStates() }

    private inline fun whenHasBackground(
        block: BackgroundStates.(Theme.Custom.CustomBackground) -> Unit,
    ) {
        if (theme.backgroundImage != null)
            block(backgroundStates, theme.backgroundImage!!)
    }

    private fun keyContrastModeLabel(mode: KeyContrastMode) = when (mode) {
        KeyContrastMode.Adaptive -> R.string.key_contrast_mode_adaptive
        KeyContrastMode.DarkText -> R.string.key_contrast_mode_dark_text
        KeyContrastMode.LightText -> R.string.key_contrast_mode_light_text
    }

    private fun updateKeyContrastValue(mode: KeyContrastMode) {
        keyContrastValue.setText(keyContrastModeLabel(mode))
    }

    private fun templateForKeyContrastMode(mode: KeyContrastMode, estimatedBackgroundColor: Int) =
        when (mode) {
            KeyContrastMode.DarkText -> ThemePreset.TransparentLight
            KeyContrastMode.LightText -> ThemePreset.TransparentDark
            KeyContrastMode.Adaptive -> {
                if (ColorUtils.calculateLuminance(estimatedBackgroundColor) > 0.46) {
                    ThemePreset.TransparentLight
                } else {
                    ThemePreset.TransparentDark
                }
            }
        }

    private fun BackgroundStates.estimateBackgroundColor(brightness: Int): Int {
        val bitmap = croppedBitmap
        val step = max(1, max(bitmap.width, bitmap.height) / 48)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel) / 255f
                red += (Color.red(pixel) * alpha).roundToInt()
                green += (Color.green(pixel) * alpha).roundToInt()
                blue += (Color.blue(pixel) * alpha).roundToInt()
                count++
                x += step
            }
            y += step
        }
        if (count == 0L) return Color.BLACK
        val factor = brightness.coerceIn(0, 100) / 100f
        return Color.rgb(
            (red.toFloat() / count * factor).roundToInt().coerceIn(0, 255),
            (green.toFloat() / count * factor).roundToInt().coerceIn(0, 255),
            (blue.toFloat() / count * factor).roundToInt().coerceIn(0, 255)
        )
    }

    private fun BackgroundStates.rebuildTheme(
        mode: KeyContrastMode = theme.backgroundImage?.keyContrastMode ?: KeyContrastMode.Adaptive
    ) {
        val brightness = brightnessSeekBar.progress
        val estimatedBackgroundColor = estimateBackgroundColor(brightness)
        val template = templateForKeyContrastMode(mode, estimatedBackgroundColor)
        theme = template.deriveCustomBackground(
            theme.name,
            croppedImageFile.path,
            srcImageFile.path,
            brightness,
            cropRect,
            cropRotation,
            mode,
            estimatedBackgroundColor
        )
        updateKeyContrastValue(mode)
        previewUi.setTheme(theme, filteredDrawable)
    }

    private fun BackgroundStates.showKeyContrastModeDialog() {
        val modes = KeyContrastMode.entries.toTypedArray()
        val currentMode = theme.backgroundImage?.keyContrastMode ?: KeyContrastMode.Adaptive
        AlertDialog.Builder(this@CustomThemeActivity)
            .setTitle(R.string.key_contrast_mode)
            .setSingleChoiceItems(
                modes.map { getString(keyContrastModeLabel(it)) }.toTypedArray(),
                modes.indexOf(currentMode)
            ) { dialog, which ->
                rebuildTheme(modes[which])
                dialog.dismiss()
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // recover from bundle
        val originTheme = intent?.parcelable<Theme.Custom>(ORIGIN_THEME)?.also { t ->
            theme = t
            whenHasBackground {
                croppedImageFile = File(it.croppedFilePath)
                srcImageFile = File(it.srcFilePath)
                cropRect = it.cropRect
                cropRotation = it.cropRotation
                croppedBitmap = BitmapFactory.decodeFile(it.croppedFilePath)
                filteredDrawable = BitmapDrawable(resources, croppedBitmap)
            }
            newCreated = false
        }
        // create new
        if (originTheme == null) {
            val (n, c, s) = ThemeFilesManager.newCustomBackgroundImages()
            backgroundStates.apply {
                croppedImageFile = c
                srcImageFile = s
            }
            // The first crop result will pick a readable key contrast mode.
            theme = ThemePreset.TransparentDark.deriveCustomBackground(n, c.path, s.path)
        }
        previewUi = KeyboardPreviewUi(this, theme)
        if (theme.backgroundImage == null) {
            brightnessLabel.visibility = View.GONE
            cropLabel.visibility = View.GONE
            keyContrastLabel.visibility = View.GONE
            keyContrastValue.visibility = View.GONE
            brightnessSeekBar.visibility = View.GONE
        }
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(ui) { _, windowInsets ->
            val statusBars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            ui.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = navBars.left
                rightMargin = navBars.right
            }
            toolbar.topPadding = statusBars.top
            scrollView.bottomPadding = navBars.bottom
            windowInsets
        }
        // show Activity label on toolbar
        setSupportActionBar(toolbar)
        // show back button
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        setContentView(ui)
        whenHasBackground { background ->
            brightnessSeekBar.progress = background.brightness
            updateKeyContrastValue(background.keyContrastMode)
            launcher = registerForActivityResult(CropContract()) {
                when (it) {
                    CropResult.Fail -> {
                        if (newCreated) {
                            cancel()
                        }
                    }
                    is CropResult.Success -> {
                        if (newCreated) {
                            srcImageExtension = MimeTypeMap.getSingleton()
                                .getExtensionFromMimeType(contentResolver.getType(it.srcUri))
                            srcImageBuffer =
                                contentResolver.openInputStream(it.srcUri)!!
                                    .use { x -> x.readBytes() }
                        }
                        cropRect = it.rect
                        cropRotation = it.rotation
                        croppedBitmap = it.bitmap
                        filteredDrawable = BitmapDrawable(resources, croppedBitmap)
                        updateState()
                    }
                }
            }
            cropLabel.setOnClickListener {
                launchCrop(previewUi.intrinsicWidth, previewUi.intrinsicHeight)
            }
            keyContrastLabel.setOnClickListener { showKeyContrastModeDialog() }
            keyContrastValue.setOnClickListener { showKeyContrastModeDialog() }
            brightnessSeekBar.setOnSeekBarChangeListener(object :
                SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}

                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) updateState()
                }
            })
        }

        if (newCreated) {
            cropLabel.visibility = View.GONE
            whenHasBackground {
                previewUi.onSizeMeasured = { w, h ->
                    launchCrop(w, h)
                }
            }
        } else {
            whenHasBackground {
                updateState()
            }
        }

        onBackPressedDispatcher.addCallback {
            cancel()
        }
    }

    private fun BackgroundStates.launchCrop(w: Int, h: Int) {
        if (newCreated) {
            launcher.launch(CropOption.New(w, h))
        } else {
            launcher.launch(
                CropOption.Edit(
                    width = w,
                    height = h,
                    Uri.fromFile(srcImageFile),
                    initialRect = cropRect,
                    initialRotation = cropRotation
                )
            )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun BackgroundStates.updateState() {
        val progress = brightnessSeekBar.progress
        brightnessValue.text = "$progress%"
        filteredDrawable.colorFilter = DarkenColorFilter(100 - progress)
        rebuildTheme()
    }

    private fun cancel() {
        setResult(
            RESULT_CANCELED,
            Intent().apply { putExtra(RESULT, null as BackgroundResult?) }
        )
        finish()
    }

    private fun done() {
        lifecycleScope.withLoadingDialog(this) {
            whenHasBackground {
                withContext(Dispatchers.IO) {
                    croppedImageFile.delete()
                    croppedImageFile.outputStream().use {
                        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    if (newCreated) {
                        if (srcImageExtension != null) {
                            srcImageFile = File("${srcImageFile.absolutePath}.$srcImageExtension")
                            theme = theme.copy(
                                backgroundImage = it.copy(
                                    srcFilePath = srcImageFile.absolutePath
                                )
                            )
                        }
                        srcImageFile.writeBytes(srcImageBuffer!!)
                    }
                }
            }
            setResult(
                RESULT_OK,
                Intent().apply {
                    var newTheme = theme
                    whenHasBackground {
                        newTheme = theme.copy(
                            backgroundImage = it.copy(
                                brightness = brightnessSeekBar.progress,
                                cropRect = cropRect,
                                cropRotation = cropRotation
                            )
                        )
                    }
                    putExtra(
                        RESULT,
                        if (newCreated)
                            BackgroundResult.Created(newTheme)
                        else
                            BackgroundResult.Updated(newTheme)
                    )
                })
            finish()
        }
    }

    private fun delete() {
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(RESULT, BackgroundResult.Deleted(theme.name))
            }
        )
        finish()
    }

    private fun promptDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_theme)
            .setMessage(getString(R.string.delete_theme_msg, theme.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                delete()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!newCreated) {
            val iconTint = color(R.color.red_400)
            menu.item(R.string.save, R.drawable.ic_baseline_delete_24, iconTint, true) {
                promptDelete()
            }
        }
        val iconTint = styledColor(android.R.attr.colorControlNormal)
        menu.item(R.string.save, R.drawable.ic_baseline_check_24, iconTint, true) {
            done()
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            cancel()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        const val RESULT = "result"
        const val ORIGIN_THEME = "origin_theme"
    }
}

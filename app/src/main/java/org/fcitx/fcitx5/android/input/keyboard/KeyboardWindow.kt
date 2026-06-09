/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.core.content.ContextCompat
import androidx.transition.Slide
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

class KeyboardWindow : InputWindow.SimpleInputWindow<KeyboardWindow>(), EssentialWindow,
    InputBroadcastReceiver {

    private val service by manager.inputMethodService()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val windowManager: InputWindowManager by manager.must()
    private val popup: PopupComponent by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()

    companion object : EssentialWindow.Key

    override val key: EssentialWindow.Key
        get() = KeyboardWindow

    override fun enterAnimation(lastWindow: InputWindow) = Slide().apply {
        slideEdge = Gravity.BOTTOM
    }.takeIf {
        // disable animation switching between picker
        lastWindow !is PickerWindow
    }

    override fun exitAnimation(nextWindow: InputWindow) =
        super.exitAnimation(nextWindow).takeIf {
            // disable animation switching between picker
            nextWindow !is PickerWindow
        }

    private lateinit var keyboardView: FrameLayout

    private val keyboardNumberRowMode = AppPrefs.getInstance().keyboard.keyboardNumberRowMode
    private var currentInputMethod: InputMethodEntry? = null
    private var currentPunctuationMapping: Map<String, String> = emptyMap()
    private var currentTextKeyboardNumberRowLayout: TextKeyboardNumberRowLayout? = null

    private val textKeyboardNumberRowLayout: TextKeyboardNumberRowLayout
        get() = TextKeyboardNumberRowLayout.from(
            currentInputMethod ?: fcitx.runImmediately { inputMethodEntryCached }
        )

    @Keep
    private val keyboardNumberRowModeListener = ManagedPreference.OnChangeListener<NumberRowMode> { _, _ ->
        ContextCompat.getMainExecutor(service).execute {
            recreateTextKeyboard()
        }
    }

    private val keyboards: HashMap<String, BaseKeyboard> by lazy {
        hashMapOf(
            TextKeyboard.Name to createTextKeyboard(),
            NumberKeyboard.Name to NumberKeyboard(context, theme)
        )
    }
    private var currentKeyboardName = ""
    private var lastSymbolType: String by AppPrefs.getInstance().internal.lastSymbolLayout

    private val currentKeyboard: BaseKeyboard? get() = keyboards[currentKeyboardName]

    private val keyActionListener = KeyActionListener { it, source ->
        if (it is KeyAction.LayoutSwitchAction) {
            switchLayout(it.act)
        } else {
            commonKeyActionListener.listener.onKeyAction(it, source)
        }
    }

    private val popupActionListener: PopupActionListener by lazy {
        popup.listener
    }

    // This will be called EXACTLY ONCE
    override fun onCreateView(): View {
        keyboardView = context.frameLayout(R.id.keyboard_view)
        keyboardNumberRowMode.registerOnChangeListener(keyboardNumberRowModeListener)
        attachLayout(TextKeyboard.Name)
        return keyboardView
    }

    private fun detachCurrentLayout() {
        currentKeyboard?.also {
            it.onDetach()
            keyboardView.removeView(it)
            it.keyActionListener = null
            it.popupActionListener = null
        }
    }

    private fun attachLayout(target: String) {
        currentKeyboardName = target
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            keyboardView.apply { add(it, lParams(matchParent, matchParent)) }
            it.onAttach()
            it.onReturnDrawableUpdate(returnKeyDrawable.resourceId)
            it.onPunctuationUpdate(currentPunctuationMapping)
            it.onInputMethodUpdate(currentInputMethod ?: fcitx.runImmediately { inputMethodEntryCached })
        }
    }

    private fun createTextKeyboard(): TextKeyboard {
        val layout = textKeyboardNumberRowLayout
        currentTextKeyboardNumberRowLayout = layout
        return TextKeyboard(context, theme, layout)
    }

    private fun recreateTextKeyboard() {
        val wasCurrent = currentKeyboardName == TextKeyboard.Name
        if (wasCurrent && this::keyboardView.isInitialized) {
            detachCurrentLayout()
        }
        keyboards[TextKeyboard.Name] = createTextKeyboard()
        if (wasCurrent && this::keyboardView.isInitialized) {
            attachLayout(TextKeyboard.Name)
            if (windowManager.isAttached(this)) {
                notifyBarLayoutChanged()
            }
        }
    }

    fun switchLayout(to: String, remember: Boolean = true) {
        val target = to.ifEmpty { lastSymbolType }
        ContextCompat.getMainExecutor(service).execute {
            if (keyboards.containsKey(target)) {
                if (remember && target != TextKeyboard.Name) {
                    lastSymbolType = target
                }
                if (target == currentKeyboardName) return@execute
                detachCurrentLayout()
                attachLayout(target)
                if (windowManager.isAttached(this)) {
                    notifyBarLayoutChanged()
                }
            } else {
                if (remember) {
                    lastSymbolType = PickerWindow.Key.Symbol.name
                }
                windowManager.attachWindow(PickerWindow.Key.Symbol)
            }
        }
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        val targetLayout = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> NumberKeyboard.Name
            InputType.TYPE_CLASS_PHONE -> NumberKeyboard.Name
            else -> TextKeyboard.Name
        }
        switchLayout(targetLayout, remember = false)
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        val oldNumberRowLayout = currentTextKeyboardNumberRowLayout ?: textKeyboardNumberRowLayout
        currentInputMethod = ime
        val newNumberRowLayout = textKeyboardNumberRowLayout
        if (
            keyboardNumberRowMode.getValue() == NumberRowMode.Always &&
            oldNumberRowLayout != newNumberRowLayout
        ) {
            recreateTextKeyboard()
            return
        }
        currentKeyboard?.onInputMethodUpdate(ime)
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        currentPunctuationMapping = mapping
        currentKeyboard?.onPunctuationUpdate(mapping)
    }

    override fun onReturnKeyDrawableUpdate(resourceId: Int) {
        currentKeyboard?.onReturnDrawableUpdate(resourceId)
    }

    override fun onAttached() {
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            it.onAttach()
        }
        notifyBarLayoutChanged()
    }

    override fun onDetached() {
        currentKeyboard?.let {
            it.onDetach()
            it.keyActionListener = null
            it.popupActionListener = null
        }
        popup.dismissAll()
    }

    // Call this when
    // 1) the keyboard window was newly attached
    // 2) currently keyboard window is attached and switchLayout was used
    private fun notifyBarLayoutChanged() {
        bar.onKeyboardLayoutSwitched(currentKeyboardName == NumberKeyboard.Name)
    }
}

/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ImageView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcaster
import org.fcitx.fcitx5.android.input.broadcast.PreeditEmptyStateComponent
import org.fcitx.fcitx5.android.input.broadcast.PunctuationComponent
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.keyboard.NumberRowMode
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import org.fcitx.fcitx5.android.input.picker.emojiPicker
import org.fcitx.fcitx5.android.input.picker.emoticonPicker
import org.fcitx.fcitx5.android.input.picker.symbolPicker
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.preedit.PreeditComponent
import org.fcitx.fcitx5.android.input.translation.TranslationComponent
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.unset
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.wrapToUniqueComponent
import org.mechdancer.dependency.plusAssign
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class InputView(
    service: FcitxInputMethodService,
    fcitx: FcitxConnection,
    theme: Theme
) : BaseInputView(service, fcitx, theme) {

    private data class CandidatePriorityTouch(
        val pointerId: Int,
        val candidateIndex: Int,
        val candidateView: View,
        val downRawX: Float,
        val downRawY: Float,
        var canceled: Boolean = false
    )

    private val keyBorder by ThemeManager.prefs.keyBorder

    private val customBackground = imageView {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private val placeholderOnClickListener = OnClickListener { }

    // use clickable view as padding, so MotionEvent can be split to padding view and keyboard view
    private val leftPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val rightPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val bottomPaddingSpace = view(::View) {
        // height as keyboardBottomPadding
        // bottomMargin as WindowInsets (Navigation Bar) offset
        setOnClickListener(placeholderOnClickListener)
    }

    private val scope = DynamicScope()
    private val themedContext = context.withTheme(R.style.Theme_InputViewTheme)
    private val broadcaster = InputBroadcaster()
    private val popup = PopupComponent()
    private val punctuation = PunctuationComponent()
    private val returnKeyDrawable = ReturnKeyDrawableComponent()
    private val preeditEmptyState = PreeditEmptyStateComponent()
    private val preedit = PreeditComponent()
    private val commonKeyActionListener = CommonKeyActionListener()
    private val windowManager = InputWindowManager()
    private val kawaiiBar = KawaiiBarComponent()
    private val translation = TranslationComponent()
    private val horizontalCandidate = HorizontalCandidateComponent()
    private val keyboardWindow = KeyboardWindow()
    private val symbolPicker = symbolPicker()
    private val emojiPicker = emojiPicker()
    private val emoticonPicker = emoticonPicker()

    private fun setupScope() {
        scope += this@InputView.wrapToUniqueComponent()
        scope += service.wrapToUniqueComponent()
        scope += fcitx.wrapToUniqueComponent()
        scope += theme.wrapToUniqueComponent()
        scope += themedContext.wrapToUniqueComponent()
        scope += broadcaster
        scope += popup
        scope += punctuation
        scope += returnKeyDrawable
        scope += preeditEmptyState
        scope += preedit
        scope += commonKeyActionListener
        scope += windowManager
        scope += translation
        scope += kawaiiBar
        scope += horizontalCandidate
        broadcaster.onScopeSetupFinished(scope)
    }

    private val keyboardPrefs = AppPrefs.getInstance().keyboard

    private val focusChangeResetKeyboard by keyboardPrefs.focusChangeResetKeyboard

    private val keyboardHeightPercent = keyboardPrefs.keyboardHeightPercent
    private val keyboardHeightPercentLandscape = keyboardPrefs.keyboardHeightPercentLandscape
    private val keyboardSidePadding = keyboardPrefs.keyboardSidePadding
    private val keyboardSidePaddingLandscape = keyboardPrefs.keyboardSidePaddingLandscape
    private val keyboardLeftPadding = keyboardPrefs.keyboardLeftPadding
    private val keyboardLeftPaddingLandscape = keyboardPrefs.keyboardLeftPaddingLandscape
    private val keyboardRightPadding = keyboardPrefs.keyboardRightPadding
    private val keyboardRightPaddingLandscape = keyboardPrefs.keyboardRightPaddingLandscape
    private val keyboardBottomPadding = keyboardPrefs.keyboardBottomPadding
    private val keyboardBottomPaddingLandscape = keyboardPrefs.keyboardBottomPaddingLandscape
    private val keyboardNumberRowMode = keyboardPrefs.keyboardNumberRowMode
    private val candidateTouchPriority by keyboardPrefs.candidateTouchPriority

    private val candidatePriorityTouchExtensionPx by lazy { dp(12) }
    private val candidatePriorityTouchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private val keyboardViewScreenLocation = IntArray(2)
    private var candidatePriorityTouchState: CandidatePriorityTouch? = null

    private val keyboardSizePrefs = listOf(
        keyboardHeightPercent,
        keyboardHeightPercentLandscape,
        keyboardSidePadding,
        keyboardSidePaddingLandscape,
        keyboardLeftPadding,
        keyboardLeftPaddingLandscape,
        keyboardRightPadding,
        keyboardRightPaddingLandscape,
        keyboardBottomPadding,
        keyboardBottomPaddingLandscape,
        keyboardNumberRowMode,
    )

    private val keyboardHeightPx: Int
        get() {
            val percent = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardHeightPercentLandscape
                else -> keyboardHeightPercent
            }.getValue()
            val baseHeight = resources.displayMetrics.heightPixels * percent / 100
            return if (keyboardNumberRowMode.getValue() == NumberRowMode.Always) {
                (baseHeight * TextKeyboard.NumberRowHeightScale).roundToInt()
            } else {
                baseHeight
            }
        }

    private fun ManagedPreference.PInt.getValueOrFallback(fallback: ManagedPreference.PInt): Int {
        return if (sharedPreferences.contains(key) || !sharedPreferences.contains(fallback.key)) {
            getValue()
        } else {
            fallback.getValue()
        }
    }

    private val keyboardLeftPaddingPx: Int
        get() {
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE ->
                    keyboardLeftPaddingLandscape.getValueOrFallback(keyboardSidePaddingLandscape)
                else -> keyboardLeftPadding.getValueOrFallback(keyboardSidePadding)
            }
            return dp(value)
        }

    private val keyboardRightPaddingPx: Int
        get() {
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE ->
                    keyboardRightPaddingLandscape.getValueOrFallback(keyboardSidePaddingLandscape)
                else -> keyboardRightPadding.getValueOrFallback(keyboardSidePadding)
            }
            return dp(value)
        }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardBottomPaddingLandscape
                else -> keyboardBottomPadding
            }.getValue()
            return dp(value)
        }

    @Keep
    private val onKeyboardSizeChangeListener = ManagedPreferenceProvider.OnChangeListener { key ->
        if (keyboardSizePrefs.any { it.key == key }) {
            updateKeyboardSize()
        }
    }

    val keyboardView: View
    val visibleInputView: View
        get() = if (translation.isActive) translation.view else keyboardView

    init {
        // MUST call before any operation
        setupScope()

        // restore punctuation mapping in case of InputView recreation
        fcitx.launchOnReady {
            punctuation.updatePunctuationMapping(it.statusAreaActionsCached)
        }

        // make sure KeyboardWindow's view has been created before it receives any broadcast
        windowManager.addEssentialWindow(keyboardWindow, createView = true)
        windowManager.addEssentialWindow(symbolPicker)
        windowManager.addEssentialWindow(emojiPicker)
        windowManager.addEssentialWindow(emoticonPicker)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        broadcaster.onImeUpdate(fcitx.runImmediately { inputMethodEntryCached })

        customBackground.imageDrawable = theme.backgroundDrawable(keyBorder)

        keyboardView = constraintLayout {
            // allow MotionEvent to be delivered to keyboard while pressing on padding views.
            // although it should be default for apps targeting Honeycomb (3.0, API 11) and higher,
            // but it's not the case on some devices ... just set it here
            isMotionEventSplittingEnabled = true
            add(customBackground, lParams {
                centerVertically()
                centerHorizontally()
            })
            add(kawaiiBar.view, lParams(matchParent, dp(KawaiiBarComponent.HEIGHT)) {
                topOfParent()
                centerHorizontally()
            })
            add(leftPaddingSpace, lParams {
                below(kawaiiBar.view)
                startOfParent()
                bottomOfParent()
            })
            add(rightPaddingSpace, lParams {
                below(kawaiiBar.view)
                endOfParent()
                bottomOfParent()
            })
            add(windowManager.view, lParams {
                below(kawaiiBar.view)
                above(bottomPaddingSpace)
                /**
                 * set start and end constrain in [updateKeyboardSize]
                 */
            })
            add(bottomPaddingSpace, lParams {
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
                bottomOfParent()
            })
        }

        updateKeyboardSize()

        add(preedit.ui.root, lParams(matchParent, wrapContent) {
            above(translation.view)
            centerHorizontally()
        })
        add(translation.view, lParams(matchParent, wrapContent) {
            above(keyboardView)
            centerHorizontally()
        })
        add(keyboardView, lParams(matchParent, wrapContent) {
            centerHorizontally()
            bottomOfParent()
        })
        add(popup.root, lParams(matchParent, matchParent) {
            centerVertically()
            centerHorizontally()
        })

        keyboardPrefs.registerOnChangeListener(onKeyboardSizeChangeListener)
    }

    private fun updateKeyboardSize() {
        windowManager.view.updateLayoutParams {
            height = keyboardHeightPx
        }
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }
        val leftPadding = keyboardLeftPaddingPx
        val rightPadding = keyboardRightPaddingPx
        if (leftPadding == 0 && rightPadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = GONE
            rightPaddingSpace.visibility = GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = if (leftPadding == 0) GONE else VISIBLE
            rightPaddingSpace.visibility = if (rightPadding == 0) GONE else VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = leftPadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = rightPadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        preedit.ui.root.setPadding(leftPadding, 0, rightPadding, 0)
        translation.setSidePadding(leftPadding, rightPadding)
        kawaiiBar.view.setPadding(leftPadding, 0, rightPadding, 0)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return if (handleCandidatePriorityTouch(event)) {
            true
        } else {
            super.dispatchTouchEvent(event)
        }
    }

    private fun handleCandidatePriorityTouch(event: MotionEvent): Boolean {
        val activeTouch = candidatePriorityTouchState
        if (activeTouch != null) {
            return continueCandidatePriorityTouch(event, activeTouch)
        }
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
        val target = candidatePriorityTargetAt(event.rawX, event.rawY) ?: return false
        pressCandidatePriorityView(target.view, true, target.localX)
        InputFeedbacks.hapticFeedback(target.view)
        InputFeedbacks.soundEffect(InputFeedbacks.SoundEffect.Standard)
        candidatePriorityTouchState = CandidatePriorityTouch(
            pointerId = event.getPointerId(event.actionIndex),
            candidateIndex = target.index,
            candidateView = target.view,
            downRawX = event.rawX,
            downRawY = event.rawY
        )
        return true
    }

    private fun continueCandidatePriorityTouch(
        event: MotionEvent,
        state: CandidatePriorityTouch
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(state.pointerId)
                if (pointerIndex < 0) {
                    cancelCandidatePriorityTouch(state)
                    return true
                }
                val dx = event.getX(pointerIndex) + event.rawX - event.x - state.downRawX
                val dy = event.getY(pointerIndex) + event.rawY - event.y - state.downRawY
                if (dx * dx + dy * dy > candidatePriorityTouchSlop * candidatePriorityTouchSlop) {
                    cancelCandidatePriorityTouch(state)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                pressCandidatePriorityView(state.candidateView, false)
                if (!state.canceled) {
                    InputFeedbacks.hapticFeedback(state.candidateView, longPress = true, keyUp = true)
                    horizontalCandidate.selectCandidate(state.candidateIndex)
                }
                candidatePriorityTouchState = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressCandidatePriorityView(state.candidateView, false)
                candidatePriorityTouchState = null
                return true
            }
            else -> return true
        }
    }

    private fun cancelCandidatePriorityTouch(state: CandidatePriorityTouch) {
        if (!state.canceled) {
            state.canceled = true
            pressCandidatePriorityView(state.candidateView, false)
        }
    }

    private fun pressCandidatePriorityView(view: View, pressed: Boolean, localX: Float? = null) {
        if (pressed && localX != null) {
            view.drawableHotspotChanged(localX, view.height / 2f)
        }
        view.isPressed = pressed
    }

    private fun candidatePriorityTargetAt(
        rawX: Float,
        rawY: Float
    ): HorizontalCandidateComponent.CandidateTouchTarget? {
        if (!candidateTouchPriority ||
            keyboardNumberRowMode.getValue() != NumberRowMode.Always ||
            translation.isActive ||
            !hasPreeditOrCandidates()
        ) {
            return null
        }
        keyboardView.getLocationOnScreen(keyboardViewScreenLocation)
        val keyboardTop = keyboardViewScreenLocation[1]
        val barBottom = keyboardTop + kawaiiBar.view.height
        if (rawY < barBottom || rawY >= barBottom + candidatePriorityTouchExtensionPx) {
            return null
        }
        return horizontalCandidate.candidateTouchTargetAtScreenX(rawX)
    }

    private fun hasPreeditOrCandidates(): Boolean {
        return !preeditEmptyState.isEmpty || horizontalCandidate.hasCandidates()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = getNavBarBottomInset(insets)
        }
        return insets
    }

    /**
     * called when [InputView] is about to show, or restart
     */
    fun startInput(info: EditorInfo, capFlags: CapabilityFlags, restarting: Boolean = false) {
        broadcaster.onStartInput(info, capFlags)
        returnKeyDrawable.updateDrawableOnEditorInfo(info)
        if (focusChangeResetKeyboard || !restarting) {
            windowManager.attachWindow(KeyboardWindow)
            translation.hide()
        }
    }

    override fun onStartHandleFcitxEvent() {
        val inputPanelData = fcitx.runImmediately { inputPanelCached }
        val inputMethodEntry = fcitx.runImmediately { inputMethodEntryCached }
        val statusAreaActions = fcitx.runImmediately { statusAreaActionsCached }
        arrayOf(
            FcitxEvent.InputPanelEvent(inputPanelData),
            FcitxEvent.IMChangeEvent(inputMethodEntry),
            FcitxEvent.StatusAreaEvent(
                FcitxEvent.StatusAreaEvent.Data(statusAreaActions, inputMethodEntry)
            )
        ).forEach { handleFcitxEvent(it) }
    }

    override fun handleFcitxEvent(it: FcitxEvent<*>) {
        when (it) {
            is FcitxEvent.CandidateListEvent -> {
                broadcaster.onCandidateUpdate(it.data)
            }
            is FcitxEvent.ClientPreeditEvent -> {
                preeditEmptyState.updatePreeditEmptyState(clientPreedit = it.data)
                broadcaster.onClientPreeditUpdate(it.data)
            }
            is FcitxEvent.InputPanelEvent -> {
                preeditEmptyState.updatePreeditEmptyState(preedit = it.data.preedit)
                broadcaster.onInputPanelUpdate(it.data)
            }
            is FcitxEvent.IMChangeEvent -> {
                broadcaster.onImeUpdate(it.data)
            }
            is FcitxEvent.StatusAreaEvent -> {
                punctuation.updatePunctuationMapping(it.data.actions)
                broadcaster.onStatusAreaUpdate(it.data.actions)
            }
            else -> {}
        }
    }

    fun updateSelection(start: Int, end: Int) {
        broadcaster.onSelectionUpdate(start, end)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        return kawaiiBar.handleInlineSuggestions(response)
    }

    override fun onDetachedFromWindow() {
        keyboardPrefs.unregisterOnChangeListener(onKeyboardSizeChangeListener)
        translation.onDetachedFromInputView()
        // clear DynamicScope, implies that InputView should not be attached again after detached.
        scope.clear()
        super.onDetachedFromWindow()
    }

}

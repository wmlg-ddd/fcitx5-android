/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.preedit

import android.view.View
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.UniqueComponent
import org.mechdancer.dependency.manager.ManagedHandler
import org.mechdancer.dependency.manager.managedHandler
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.horizontalPadding
import kotlin.math.abs

class PreeditComponent : UniqueComponent<PreeditComponent>(), Dependent, InputBroadcastReceiver,
    ManagedHandler by managedHandler() {

    private val context by manager.context()
    private val theme by manager.theme()
    private val service by manager.inputMethodService()

    private var inputPanelData = FcitxEvent.InputPanelEvent.Data()

    val ui by lazy {
        val keyBorder = ThemeManager.prefs.keyBorder.getValue()
        val bkgColor =
            if (!keyBorder && theme is Theme.Builtin) theme.barColor else theme.backgroundColor
        PreeditUi(
            context,
            theme,
            setupTextView = {
                backgroundColor = bkgColor
                horizontalPadding = dp(8)
            },
            onPreeditTapped = ::movePreeditCursor
        ).apply {
            // TODO make it customizable
            root.alpha = 0.8f
            root.visibility = View.INVISIBLE
        }
    }

    override fun onInputPanelUpdate(data: FcitxEvent.InputPanelEvent.Data) {
        inputPanelData = data
        ui.update(data)
        ui.root.visibility = if (ui.visible) View.VISIBLE else View.INVISIBLE
    }

    /**
     * Move the engine cursor inside the preedit string to [target] by sending
     * Left/Right key events, e.g. after the user tapped a position in the preedit.
     */
    private fun movePreeditCursor(target: Int) {
        val current = inputPanelData.preedit.cursor
        if (current < 0) return
        val diff = target - current
        if (diff == 0) return
        val sym = if (diff < 0) FcitxKeyMapping.FcitxKey_Left else FcitxKeyMapping.FcitxKey_Right
        val count = abs(diff)
        service.postFcitxJob {
            repeat(count) { sendKey(KeySym(sym), KeyStates.Virtual) }
        }
    }
}

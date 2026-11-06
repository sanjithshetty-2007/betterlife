package com.sanjith.betterlife.reminders.popup

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.sanjith.betterlife.domain.mascot.MascotMood
import com.sanjith.betterlife.domain.mascot.MascotStage
import com.sanjith.betterlife.ui.mascot.Mascot
import com.sanjith.betterlife.ui.mascot.MascotStatic
import com.sanjith.betterlife.ui.theme.Dimens

/**
 * Blip on the pop-up card: the real mascot (Prompt 16) in its curious mood, as a baby blob so it
 * always has a face. It blinks unless animations are off.
 */
@Composable
public fun PopupMascot(reduceMotion: Boolean, description: String, mascotSize: Dp = Dimens.mascotSmall) {
    val modifier = Modifier.size(mascotSize)
    if (reduceMotion) {
        MascotStatic(MascotMood.CURIOUS, MascotStage.BABY, description, modifier)
    } else {
        Mascot(MascotMood.CURIOUS, MascotStage.BABY, description, modifier)
    }
}

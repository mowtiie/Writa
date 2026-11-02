package com.mowtiie.writa.core.ui

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession

/**
 * Asks the keyboard not to learn from what is typed into the text fields inside [content]. Compose has no option for
 * this, so the flag is added when a field connects to the keyboard. For fields that hold note text.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WithoutPersonalizedLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(interceptor = NoPersonalizedLearning, content = content)
}

@OptIn(ExperimentalComposeUiApi::class)
private object NoPersonalizedLearning : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession,
    ): Nothing = nextHandler.startInputMethod(request.withoutPersonalizedLearning())
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun PlatformTextInputMethodRequest.withoutPersonalizedLearning() =
    object : PlatformTextInputMethodRequest {
        override fun createInputConnection(outAttrs: EditorInfo): InputConnection =
            this@withoutPersonalizedLearning.createInputConnection(outAttrs).also {
                outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
    }

package io.github.kdroidfilter.seforimapp

import androidx.compose.ui.window.ComposeUIViewController

/** iOS entry point: returns a UIViewController hosting the shared [App] composable. */
@Suppress("unused", "FunctionName")
fun MainViewController() = ComposeUIViewController { App() }

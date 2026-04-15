package com.u3coding.shaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ChatViewModelFactory {
    class ChatViewModelFactory(
        private val wifiProvider: WifiProvider
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel() as T
        }
    }
}
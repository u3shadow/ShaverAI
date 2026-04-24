package com.u3coding.shaver.action.model

class OperationList {
    companion object {
        private val validOperations = setOf("set_volume", "set_brightness", "open_bluetooth", "close_bluetooth")

        fun isValidOperation(operation: String?): Boolean {
            return operation != null && validOperations.contains(operation)
        }
    }
}

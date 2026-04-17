package com.u3coding.shaver.action

//一个工具类判断仅在范围内的字符串对象才是合规的，否则就返回不合法operation
class  OperationList {
    companion object {
        private val validOperations = setOf("set_volume", "set_brightness", "open_bluetooth", "close_bluetooth")
        fun isValidOperation(operation: String?): Boolean {
            return operation != null && validOperations.contains(operation)
        }
    }
}

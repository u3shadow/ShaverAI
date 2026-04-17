package com.u3coding.shaver.action

class ActionParser {
    fun parseActionDTO(json: String): ActionDTO {
        //能从json字符串中解析出ActionDTO对象，并能处理错误的情况
        return try {
            val gson = com.google.gson.Gson()
            gson.fromJson(json, ActionDTO::class.java)
        } catch (e: Exception) {
            //解析失败，返回一个默认的ActionDTO对象
            ActionDTO(null, null, null)
        }
    }
    fun ActionValidator(actionDTO: ActionDTO): Boolean {
         //验证ActionDTO对象是否合法,1.验证字段是否为空，2.检查operation是否在OperationList中，3.检查params是否符合要求,如果是蓝牙操作，params应该为空，如果是音量或亮度操作，params应该包含一个数值参数"value"
        if (actionDTO.triger == null || actionDTO.operation == null) {
            return false
        }
        if (!OperationList.isValidOperation(actionDTO.operation)) {
            return false
        }
        if ((actionDTO.operation == "open_bluetooth" || actionDTO.operation == "close_bluetooth") && actionDTO.params != null) {
            return false
        }
        if ((actionDTO.operation == "set_volume" || actionDTO.operation == "set_brightness") && (actionDTO.params == null || !actionDTO.params.containsKey("value") || actionDTO.params["value"] !is Number)) {
            return false
        }
        return true

    }
}
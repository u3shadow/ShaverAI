package com.u3coding.shaver.action.parser

import com.google.gson.Gson
import com.u3coding.shaver.action.model.ActionDTO
import com.u3coding.shaver.action.model.OperationList

class ActionParser {
    fun parseActionDTO(json: String): ActionDTO {
        return try {
            Gson().fromJson(json, ActionDTO::class.java)
        } catch (e: Exception) {
            ActionDTO(null, null, null)
        }
    }

    fun ActionValidator(actionDTO: ActionDTO): Boolean {
        if (actionDTO.trigger == null || actionDTO.operation == null) {
            return false
        }
        if (!OperationList.isValidOperation(actionDTO.operation)) {
            return false
        }

        if (actionDTO.operation == "set_volume" || actionDTO.operation == "set_brightness") {
            val value = actionDTO.params?.get("value")
            if (value !is Number) {
                return false
            }
        }

        return true
    }
}

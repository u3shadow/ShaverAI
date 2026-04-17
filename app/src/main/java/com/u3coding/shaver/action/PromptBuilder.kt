package com.u3coding.shaver.action

import android.service.autofill.FillEventHistory
import com.u3coding.shaver.data.remote.ChatMessage
import com.u3coding.shaver.model.UiMessage

//改造成一个工具类
class PromptBuilder {
    private val role = "你是一个 Android 端自动化规则解析器\n" +
            "你的任务是把用户输入解析为结构化 JSON，用于配置 Wi-Fi 场景和设备动作"
    private val rules = "请遵守以下规则：\n" +
            "\n" +
            "1. 只返回 JSON，不要返回解释、注释、markdown。\n" +
            "2. operation 只能是以下值之一：\n" +
            "- set_volume\n" +
            "- set_brightness\n" +
            "- open_bluetooth\n" +
            "- close_bluetooth\n" +
            "3. 返回格式固定为：\n" +
            "\n" +
            "{\n" +
            "  \"wifi\": \"string or null\",\n" +
            "  \"actions\": [\n" +
            "    {\n" +
            "      \"operation\": \"string\",\n" +
            "      \"params\": {\n" +
            "        \"value\": \"number or null\"\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" +
            "\n" +
            "4. 如果无法确定 Wi-Fi 名称，wifi 返回 null。\n" +
            "5. 如果无法确定动作，actions 返回空数组。\n" +
            "6. 如果动作不需要数值参数，params.value 返回 null。"
    private val sample = "示例1：\n" +
            "用户输入：公司 WiFi 下把音量调成 0\n" +
            "返回：\n" +
            "{\n" +
            "  \"wifi\": \"公司 WiFi\",\n" +
            "  \"actions\": [\n" +
            "    {\n" +
            "      \"operation\": \"set_volume\",\n" +
            "      \"params\": {\n" +
            "        \"value\": 0\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" +
            "\n" +
            "示例2：\n" +
            "用户输入：回家后打开蓝牙并把亮度调到 80\n" +
            "返回：\n" +
            "{\n" +
            "  \"wifi\": \"家里WiFi\",\n" +
            "  \"actions\": [\n" +
            "    {\n" +
            "      \"operation\": \"open_bluetooth\",\n" +
            "      \"params\": {\n" +
            "        \"value\": null\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"operation\": \"set_brightness\",\n" +
            "      \"params\": {\n" +
            "        \"value\": 80\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}"
    fun buildPrompt(input:String): String {
        return  role+rules+sample+"现在解析用户输入："+input
    }
}
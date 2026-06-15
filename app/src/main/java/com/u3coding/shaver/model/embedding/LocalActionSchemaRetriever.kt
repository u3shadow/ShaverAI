package com.u3coding.shaver.model.embedding

private fun buildSchemaText(schema: ActionSchema): String {
    return buildString {
        append(schema.actionId)
        append(" ")
        append(schema.description)
        append(" ")
        append(schema.examples.joinToString(" "))
        append(" ")
        append(schema.paramSchema.entries.joinToString(" ") { "${it.key}:${it.value}" })
    }
}
package io.legado.app.domain.usecase

import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiToolContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiToolCall
import io.legado.app.domain.model.AiToolApprovalState
import io.legado.app.utils.GSON
import com.google.gson.JsonObject

class AiToolAwareGenerationUseCase(
    private val aiTextGateway: AiTextGateway,
    private val aiToolGateway: AiToolGateway,
) {

    suspend fun generate(request: AiGenerateRequest): String {
        val output = StringBuilder()
        generateStream(request).collect { event ->
            if (event is AiStreamEvent.Content) {
                output.append(event.text)
            }
        }
        return output.toString().trim()
            .ifEmpty { error("AI returned empty text") }
    }

    fun generateStream(request: AiGenerateRequest): Flow<AiStreamEvent> = flow {
        var currentRequest = request.withReadOnlyTools()
        while (true) {
            val toolTrace = ToolTraceBuilder()
            val roundContent = StringBuilder()
            toolTrace.beginResponse()

            aiTextGateway.generateStream(currentRequest).collect { event ->
                when (event) {
                    is AiStreamEvent.Content -> {
                        roundContent.append(event.text)
                        emit(event)
                    }

                    is AiStreamEvent.Reasoning -> emit(event)
                    is AiStreamEvent.ToolCallDelta -> {
                        toolTrace.append(event)
                        emit(event)
                    }
                }
            }

            val toolCalls = toolTrace.pendingToolCalls()
            if (toolCalls.isEmpty()) {
                return@flow
            }

            val toolResultMessages = toolCalls.map { toolCall ->
                val result = aiToolGateway.execute(toolCall)
                AiMessage(
                    role = AiMessageRole.TOOL,
                    content = result.content.truncateToolOutput(),
                    toolCallId = result.callId,
                    name = result.name,
                )
            }
            currentRequest = currentRequest.copy(
                messages = currentRequest.messages +
                    AiMessage(
                        role = AiMessageRole.ASSISTANT,
                        content = roundContent.toString(),
                        toolCalls = toolCalls,
                    ) +
                    toolResultMessages,
            )
        }
    }

    private fun AiGenerateRequest.withReadOnlyTools(): AiGenerateRequest {
        val readOnlyTools = aiToolGateway.availableTools()
            .filterNot { aiToolGateway.requiresConfirmation(it.name) }
        if (readOnlyTools.isEmpty()) return this
        return copy(
            messages = buildList {
                add(TOOL_CONTEXT_MESSAGE)
                toolContext?.toMessage()?.let { add(it) }
                addAll(messages)
            },
            tools = readOnlyTools,
        )
    }

    private fun AiToolContext.toMessage(): AiMessage? {
        val lines = buildList {
            bookUrl?.takeIf { it.isNotBlank() }?.let { add("bookUrl: $it") }
            bookName?.takeIf { it.isNotBlank() }?.let { add("bookName: $it") }
            chapterIndex?.let { add("chapterIndex: $it") }
            chapterTitle?.takeIf { it.isNotBlank() }?.let { add("chapterTitle: $it") }
        }
        if (lines.isEmpty()) return null
        return AiMessage(
            role = AiMessageRole.SYSTEM,
            content = "Current local book context for read-only tools:\n" +
                lines.joinToString("\n") +
                "\nWhen calling book tools, prefer these exact identifiers unless the user explicitly asks for another book.",
        )
    }

    companion object {
        const val CACHE_PROMPT_VERSION = "tool-aware-generation-v1"

        private val TOOL_CONTEXT_MESSAGE = AiMessage(
            role = AiMessageRole.SYSTEM,
            content = "Read-only local book tools are available. Use them when needed to inspect the current book, list chapters, search cached chapter text by character or plot keyword, read cached neighboring chapters for continuity, or look up saved character profiles, relationships, world-book entries, and outlines. Use tools silently; the final response must still follow the original task output format.",
        )
    }
}

/**
 * Accumulates streaming tool call deltas into complete [AiMessagePart.Tool] parts.
 * Thread-safe for use within a single coroutine context.
 */
class ToolTraceBuilder {
    private val calls = linkedMapOf<String, ToolCallTrace>()
    private val indexKeys = mutableMapOf<Int, String>()

    fun beginResponse() {
        indexKeys.clear()
    }

    fun append(event: AiStreamEvent.ToolCallDelta): String {
        val eventId = event.id?.takeIf { it.isNotBlank() }
        if (eventId != null && event.index != null) {
            indexKeys[event.index] = eventId
        }
        val baseId = eventId
            ?: event.index?.let { indexKeys[it] ?: "tool_index_$it" }
            ?: "tool_${calls.size + 1}"
        val id = if (eventId == null && calls[baseId]?.result != null) {
            "${baseId}_${calls.size + 1}"
        } else {
            baseId
        }
        val call = calls.getOrPut(id) { ToolCallTrace(id = id, rawType = event.rawType) }
        event.name?.takeIf { it.isNotBlank() }?.let { call.name = it }
        event.argumentsDelta?.takeIf { it.isNotEmpty() }?.let { call.arguments.append(it) }
        if (call.rawType.isBlank()) call.rawType = event.rawType
        return toString()
    }

    fun appendResult(id: String, result: String): String {
        calls[id]?.result = result
        return toString()
    }

    fun pendingToolCalls(): List<AiToolCall> {
        return calls.values.filter { it.result == null }.mapNotNull { call ->
            val name = call.name.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AiToolCall(
                id = call.id,
                name = name,
                arguments = call.arguments.toString().ifBlank { "{}" }
            )
        }
    }

    fun toParts(): List<AiMessagePart> {
        val parts = mutableListOf<AiMessagePart>()
        calls.values.forEach { call ->
            val name = call.name.takeIf { it.isNotBlank() } ?: return@forEach
            parts += AiMessagePart.Tool(
                toolCallId = call.id,
                toolName = name,
                input = call.arguments.toString().ifBlank { "{}" },
                output = call.result ?: "",
                rawType = call.rawType.ifBlank { "tool_call" },
                approvalState = if (call.result == null) {
                    AiToolApprovalState.PENDING
                } else {
                    AiToolApprovalState.AUTO
                }
            )
        }
        return parts
    }

    fun bookResults(): List<AiMessagePart.BookResult> {
        val books = linkedMapOf<String, AiMessagePart.BookResult>()
        calls.values.mapNotNull { it.result }.forEach { result ->
            val root = runCatching {
                GSON.fromJson(result, JsonObject::class.java)
            }.getOrNull() ?: return@forEach
            root.getAsJsonArrayOrNull("books")?.forEach { element ->
                element.asJsonObjectOrNull()?.toBookResultPart()?.let {
                    books.putIfAbsent(it.bookUrl, it)
                }
            }
            root.getAsJsonObjectOrNull("book")?.toBookResultPart()?.let {
                books.putIfAbsent(it.bookUrl, it)
            }
        }
        return books.values.toList()
    }

    override fun toString(): String {
        return calls.values.joinToString("\n\n") { call ->
            buildString {
                append("Tool: ")
                append(call.name.ifBlank { call.rawType.ifBlank { call.id } })
                append('\n')
                append("ID: ")
                append(call.id)
                if (call.arguments.isNotBlank()) {
                    append('\n')
                    append(call.arguments)
                }
                call.result?.takeIf { it.isNotBlank() }?.let {
                    append('\n')
                    append("Result: ")
                    append(it.take(2000))
                }
            }
        }
    }
}

internal data class ToolCallTrace(
    val id: String,
    var rawType: String,
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
    var result: String? = null
)

/** Truncate tool output to 8000 chars to avoid overflowing context. */
internal fun String.truncateToolOutput(): String {
    if (length <= 8_000) return this
    return take(8_000) + "\n\n[...truncated from ${length} chars]"
}

internal fun JsonObject.toBookResultPart(): AiMessagePart.BookResult? {
    val bookUrl = string("bookUrl")?.takeIf { it.isNotBlank() } ?: return null
    return AiMessagePart.BookResult(
        bookUrl = bookUrl,
        name = string("name").orEmpty(),
        author = string("author").orEmpty(),
        origin = string("origin") ?: string("originName"),
        coverPath = string("coverPath") ?: string("coverUrl"),
        latestChapterTitle = string("latestChapterTitle"),
        currentChapterTitle = string("currentChapterTitle"),
        intro = string("intro")
    )
}

internal fun JsonObject.string(name: String): String? {
    return get(name)?.takeIf { !it.isJsonNull }?.asString
}

internal fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? {
    return get(name)?.let { if (it.isJsonObject) it.asJsonObject else null }
}

internal fun JsonObject.getAsJsonArrayOrNull(name: String) = runCatching {
    get(name)?.takeIf { !it.isJsonNull && it.isJsonArray }?.asJsonArray
}.getOrNull()

internal fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
    return takeIf { !it.isJsonNull && it.isJsonObject }?.asJsonObject
}

/** Truncate tool output to [AiChatGenerationUseCase.MAX_TOOL_OUTPUT_CHARS] to avoid overflowing context. */

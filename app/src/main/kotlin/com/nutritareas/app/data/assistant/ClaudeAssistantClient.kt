package com.nutritareas.app.data.assistant

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatRole
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

sealed interface AssistantStreamEvent {
    data class Delta(val textChunk: String) : AssistantStreamEvent
    data class Completed(val fullText: String) : AssistantStreamEvent
    data class Failed(val error: AssistantError) : AssistantStreamEvent
}

/**
 * Talks to Claude via the official Anthropic Java SDK. The Messages API is stateless per request,
 * so every call resends the full conversation; the PDF (if any) is re-attached as a cached
 * document block on the first user turn so it isn't re-billed as fresh input on every message.
 */
class ClaudeAssistantClient {

    fun streamTurn(
        apiKey: String,
        modelId: String,
        history: List<ChatMessage>,
        pdfBase64: String?,
        pdfFileName: String?,
        newUserText: String,
    ): Flow<AssistantStreamEvent> = callbackFlow {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

        val job = launch(Dispatchers.IO) {
            val textBuilder = StringBuilder()
            try {
                val params = buildParams(modelId, history, pdfBase64, pdfFileName, newUserText)
                client.messages().createStreaming(params).use { streamResponse ->
                    streamResponse.stream().forEach { event ->
                        event.contentBlockDelta().ifPresent { delta ->
                            delta.delta().text().ifPresent { textDelta ->
                                textBuilder.append(textDelta.text())
                                trySend(AssistantStreamEvent.Delta(textDelta.text()))
                            }
                        }
                    }
                }
                trySend(AssistantStreamEvent.Completed(textBuilder.toString()))
            } catch (e: UnauthorizedException) {
                trySend(AssistantStreamEvent.Failed(AssistantError.InvalidApiKey(e)))
            } catch (e: RateLimitException) {
                trySend(AssistantStreamEvent.Failed(AssistantError.RateLimited(e)))
            } catch (e: NotFoundException) {
                trySend(AssistantStreamEvent.Failed(AssistantError.ModelNotFound(e)))
            } catch (e: InternalServerException) {
                trySend(AssistantStreamEvent.Failed(AssistantError.ServerError(e)))
            } catch (e: AnthropicIoException) {
                trySend(AssistantStreamEvent.Failed(AssistantError.Network(e)))
            } catch (e: IOException) {
                trySend(AssistantStreamEvent.Failed(AssistantError.Network(e)))
            } catch (e: AnthropicServiceException) {
                trySend(AssistantStreamEvent.Failed(AssistantError.Unknown(e)))
            } catch (e: Exception) {
                trySend(AssistantStreamEvent.Failed(AssistantError.Unknown(e)))
            } finally {
                client.close()
            }
            close()
        }
        awaitClose { job.cancel() }
    }

    private fun buildParams(
        modelId: String,
        history: List<ChatMessage>,
        pdfBase64: String?,
        pdfFileName: String?,
        newUserText: String,
    ): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(modelId)
            .maxTokens(MAX_TOKENS)
            .system(AssistantPersona.systemPrompt)
            .thinking(ThinkingConfigAdaptive.builder().build())
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.HIGH).build())

        var pdfAttached = false
        for (message in history) {
            val isFirstUserTurn = !pdfAttached && message.role == ChatRole.USER && pdfBase64 != null
            if (isFirstUserTurn) {
                pdfAttached = true
                builder.addMessage(userMessageWithPdf(message.text, pdfBase64!!, pdfFileName))
            } else {
                builder.addMessage(plainMessage(message.role, message.text))
            }
        }

        if (!pdfAttached && pdfBase64 != null) {
            // The PDF was attached but hasn't appeared in `history` yet (first turn of the
            // conversation): carry it on the new message being sent right now.
            builder.addMessage(userMessageWithPdf(newUserText, pdfBase64, pdfFileName))
        } else {
            builder.addMessage(plainMessage(ChatRole.USER, newUserText))
        }

        return builder.build()
    }

    private fun plainMessage(role: ChatRole, text: String): MessageParam =
        MessageParam.builder()
            .role(if (role == ChatRole.USER) MessageParam.Role.USER else MessageParam.Role.ASSISTANT)
            .content(text)
            .build()

    private fun userMessageWithPdf(text: String, pdfBase64: String, pdfFileName: String?): MessageParam {
        val document = DocumentBlockParam.builder()
            .source(Base64PdfSource.builder().data(pdfBase64).build())
            .title(pdfFileName ?: "documento.pdf")
            .cacheControl(CacheControlEphemeral.builder().build())
            .build()
        return MessageParam.builder()
            .role(MessageParam.Role.USER)
            .contentOfBlockParams(
                listOf(
                    ContentBlockParam.ofDocument(document),
                    ContentBlockParam.ofText(TextBlockParam.builder().text(text).build()),
                )
            )
            .build()
    }

    companion object {
        private const val MAX_TOKENS = 64000L
    }
}

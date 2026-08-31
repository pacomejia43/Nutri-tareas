package com.nutritareas.app.data.assistant

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.NotFoundException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.nutritareas.app.data.chat.ChatImageAttachment
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatPdfAttachment
import com.nutritareas.app.data.chat.ChatRole
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Talks to Claude via the official Anthropic Java SDK. The Messages API is stateless per request,
 * so every call resends the full conversation; every attached PDF is re-attached as a cached
 * document block on the first user turn so it isn't re-billed as fresh input on every message,
 * and any screenshots a user turn carried are re-attached as image blocks on that same turn.
 */
class ClaudeAssistantClient : AssistantClient {

    override fun streamTurn(
        apiKey: String,
        modelId: String,
        history: List<ChatMessage>,
        pdfAttachments: List<ChatPdfAttachment>,
        newUserText: String,
        newUserImages: List<ChatImageAttachment>,
    ): Flow<AssistantStreamEvent> = callbackFlow {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()

        val job = launch(Dispatchers.IO) {
            val textBuilder = StringBuilder()
            try {
                val params = buildParams(modelId, history, pdfAttachments, newUserText, newUserImages)
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
        // trySend() above doesn't suspend - on a full channel it just drops the element. Text
        // deltas can arrive faster than the UI consumes them (a slow recomposition, a busy main
        // thread), and callbackFlow's default buffer is only 64, so without this a long response
        // could silently lose chunks partway through and render as truncated with no error at all.
        .buffer(Channel.UNLIMITED)

    private fun buildParams(
        modelId: String,
        history: List<ChatMessage>,
        pdfAttachments: List<ChatPdfAttachment>,
        newUserText: String,
        newUserImages: List<ChatImageAttachment>,
    ): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(modelId)
            .maxTokens(MAX_TOKENS)
            .system(AssistantPersona.systemPrompt)
            .thinking(ThinkingConfigAdaptive.builder().build())
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.HIGH).build())

        var pdfsAttached = false
        for (message in history) {
            val attachPdfsHere = !pdfsAttached && message.role == ChatRole.USER && pdfAttachments.isNotEmpty()
            if (attachPdfsHere) pdfsAttached = true
            builder.addMessage(
                toMessageParam(
                    role = message.role,
                    text = message.text,
                    images = message.imageAttachments,
                    pdfs = if (attachPdfsHere) pdfAttachments else emptyList(),
                ),
            )
        }

        val attachPdfsOnNewTurn = !pdfsAttached && pdfAttachments.isNotEmpty()
        builder.addMessage(
            toMessageParam(
                role = ChatRole.USER,
                text = newUserText,
                images = newUserImages,
                pdfs = if (attachPdfsOnNewTurn) pdfAttachments else emptyList(),
            ),
        )

        return builder.build()
    }

    /** Builds one turn. Plain text when there's nothing else to attach; multi-block content otherwise. */
    private fun toMessageParam(
        role: ChatRole,
        text: String,
        images: List<ChatImageAttachment>,
        pdfs: List<ChatPdfAttachment>,
    ): MessageParam {
        val sdkRole = if (role == ChatRole.USER) MessageParam.Role.USER else MessageParam.Role.ASSISTANT
        if (pdfs.isEmpty() && images.isEmpty()) {
            return MessageParam.builder().role(sdkRole).content(text).build()
        }
        val blocks = mutableListOf<ContentBlockParam>()
        for (pdf in pdfs) {
            if (pdf.markdown != null) {
                blocks += ContentBlockParam.ofText(TextBlockParam.builder().text(pdf.markdown).build())
            } else if (pdf.base64 != null) {
                val document = DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder().data(pdf.base64).build())
                    .title(pdf.fileName)
                    .cacheControl(CacheControlEphemeral.builder().build())
                    .build()
                blocks += ContentBlockParam.ofDocument(document)
            }
        }
        for (image in images) {
            val source = Base64ImageSource.builder()
                .data(image.base64)
                .mediaType(imageMediaType(image.mimeType))
                .build()
            blocks += ContentBlockParam.ofImage(ImageBlockParam.builder().source(source).build())
        }
        blocks += ContentBlockParam.ofText(TextBlockParam.builder().text(text).build())
        return MessageParam.builder().role(sdkRole).contentOfBlockParams(blocks).build()
    }

    private fun imageMediaType(mimeType: String): Base64ImageSource.MediaType = when (mimeType) {
        "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG
        "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF
        "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP
        else -> Base64ImageSource.MediaType.IMAGE_JPEG
    }

    companion object {
        private const val MAX_TOKENS = 64000L
    }
}

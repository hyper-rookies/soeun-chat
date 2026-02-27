package nhnad.soeun_chat.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import nhnad.soeun_chat.domain.chat.dto.ChatRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Chat", description = "AI 광고 성과 분석 채팅")
@RequestMapping("/api/chat")
public interface ChatApi {

    @Operation(summary = "채팅 메시지 전송 (SSE 스트리밍)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "스트리밍 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping(value = "/{conversationId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter chat(
            @Parameter(description = "대화 ID") @PathVariable String conversationId,
            @RequestBody ChatRequest request,
            @Parameter(hidden = true) String userId
    );
}

package nhnad.soeun_chat.domain.chat.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ConversationRepository {

    private static final String TABLE_NAME = "se_conversations";

    private final DynamoDbClient dynamoDbClient;

    public Optional<Map<String, AttributeValue>> findById(String conversationId) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("conversationId", AttributeValue.fromS(conversationId)))
                .build());
        boolean found = response.hasItem();
        log.debug("대화 조회 - conversationId: {}, found: {}", conversationId, found);
        return found ? Optional.of(response.item()) : Optional.empty();
    }

    public List<Map<String, AttributeValue>> findByUserId(String userId) {
        // GSI: userId-updatedAt-index (AWS Console에서 추가 필요)
        QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("userId-updatedAt-index")
                .keyConditionExpression("userId = :uid")
                .expressionAttributeValues(Map.of(":uid", AttributeValue.fromS(userId)))
                .scanIndexForward(false) // 최신순
                .build());
        log.debug("대화 목록 조회 - userId: {}, count: {}", userId, response.count());
        return response.items();
    }

    public List<Map<String, AttributeValue>> findReportsByUserId(String userId) {
        // GSI: userId-updatedAt-index, conversationId prefix 필터로 리포트만 추출
        QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("userId-updatedAt-index")
                .keyConditionExpression("userId = :uid")
                .filterExpression("begins_with(conversationId, :prefix)")
                .expressionAttributeValues(Map.of(
                        ":uid", AttributeValue.fromS(userId),
                        ":prefix", AttributeValue.fromS("report_")
                ))
                .scanIndexForward(false)
                .build());
        log.debug("리포트 목록 조회 - userId: {}, count: {}", userId, response.count());
        return response.items();
    }

    public void save(String conversationId, String userId) {
        save(conversationId, userId, null);
    }

    public void save(String conversationId, String userId, String title) {
        long now = Instant.now().toEpochMilli();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("conversationId", AttributeValue.fromS(conversationId));
        item.put("userId", AttributeValue.fromS(userId != null ? userId : "anonymous"));
        item.put("createdAt", AttributeValue.fromN(String.valueOf(now)));
        item.put("updatedAt", AttributeValue.fromN(String.valueOf(now)));
        if (title != null && !title.isBlank()) {
            item.put("title", AttributeValue.fromS(title));
        }

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());
        log.info("대화 생성 - conversationId: {}, userId: {}", conversationId, userId);
    }

    /**
     * 리포트 대화 저장: messages는 S3에 위임하고 reportS3Key만 기록
     */
    public void saveWithS3Key(String conversationId, String userId, String title, String reportS3Key) {
        long now = Instant.now().toEpochMilli();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("conversationId", AttributeValue.fromS(conversationId));
        item.put("userId", AttributeValue.fromS(userId != null ? userId : "system"));
        item.put("title", AttributeValue.fromS(title));
        item.put("createdAt", AttributeValue.fromN(String.valueOf(now)));
        item.put("updatedAt", AttributeValue.fromN(String.valueOf(now)));
        item.put("reportS3Key", AttributeValue.fromS(reportS3Key));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());
        log.info("리포트 대화 생성 - conversationId: {}, userId: {}, s3Key: {}", conversationId, userId, reportS3Key);
    }

    public void delete(String conversationId) {
        dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("conversationId", AttributeValue.fromS(conversationId)))
                .build());
        log.info("대화 삭제 - conversationId: {}", conversationId);
    }

    public void updateTitle(String conversationId, String title) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("conversationId", AttributeValue.fromS(conversationId)))
                .updateExpression("SET title = :title")
                .expressionAttributeValues(Map.of(":title", AttributeValue.fromS(title)))
                .build());
        log.debug("대화 제목 갱신 - conversationId: {}, title: {}", conversationId, title);
    }

    public void updateUpdatedAt(String conversationId, long updatedAt) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("conversationId", AttributeValue.fromS(conversationId)))
                .updateExpression("SET updatedAt = :updatedAt")
                .expressionAttributeValues(Map.of(":updatedAt", AttributeValue.fromN(String.valueOf(updatedAt))))
                .build());
        log.debug("대화 updatedAt 갱신 - conversationId: {}, updatedAt: {}", conversationId, updatedAt);
    }
}

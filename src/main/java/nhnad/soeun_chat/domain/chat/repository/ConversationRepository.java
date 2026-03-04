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

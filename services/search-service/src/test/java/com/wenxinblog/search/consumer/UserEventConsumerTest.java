package com.wenxinblog.search.consumer;

import com.wenxinblog.search.model.UserDocument;
import com.wenxinblog.search.repository.UserSearchRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    private UserSearchRepository userRepo;

    @Mock
    private ObjectMapper objectMapper;

    private UserEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UserEventConsumer(userRepo, objectMapper);
    }

    @Test
    void consume_WithCreateEvent_ShouldIndexUser() throws Exception {
        String jsonPayload = """
                {
                    "eventType": "CREATE",
                    "data": {
                        "id": "user123",
                        "displayName": "John Doe",
                        "username": "johndoe",
                        "bio": "Test bio",
                        "avatarUrl": "http://example.com/avatar.jpg",
                        "followerCount": 100,
                        "postCount": 50
                    }
                }
                """;

        JsonNode node = mock(JsonNode.class);
        JsonNode eventTypeNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);

        when(objectMapper.readTree(jsonPayload)).thenReturn(node);
        when(node.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("CREATE");
        when(node.get("data")).thenReturn(dataNode);

        setupJsonFieldMock(dataNode, "id", "user123");
        setupJsonFieldMock(dataNode, "displayName", "John Doe");
        setupJsonFieldMock(dataNode, "username", "johndoe");
        setupJsonFieldMock(dataNode, "bio", "Test bio");
        setupJsonFieldMock(dataNode, "avatarUrl", "http://example.com/avatar.jpg");
        setupJsonIntMock(dataNode, "followerCount", 100);
        setupJsonIntMock(dataNode, "postCount", 50);

        when(userRepo.indexUser(any(UserDocument.class))).thenReturn(Mono.empty());

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.user.events", 0, 0, "key", jsonPayload);

        StepVerifier.create(consumer.consume(record)).verifyComplete();

        verify(userRepo).indexUser(any(UserDocument.class));
    }

    @Test
    void consume_WithProfileUpdateEvent_ShouldUpdateUser() throws Exception {
        String jsonPayload = """
                {
                    "eventType": "PROFILE_UPDATE",
                    "data": {
                        "id": "user123",
                        "displayName": "Updated Name",
                        "bio": "Updated bio"
                    }
                }
                """;

        JsonNode node = mock(JsonNode.class);
        JsonNode eventTypeNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);

        when(objectMapper.readTree(jsonPayload)).thenReturn(node);
        when(node.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("PROFILE_UPDATE");
        when(node.get("data")).thenReturn(dataNode);

        setupJsonFieldMock(dataNode, "id", "user123");
        setupJsonFieldMock(dataNode, "displayName", "Updated Name");
        setupJsonFieldMock(dataNode, "bio", "Updated bio");

        // Mock missing fields to return null
        when(dataNode.has("username")).thenReturn(false);
        when(dataNode.has("avatarUrl")).thenReturn(false);
        when(dataNode.has("followerCount")).thenReturn(false);
        when(dataNode.has("postCount")).thenReturn(false);

        when(userRepo.indexUser(any(UserDocument.class))).thenReturn(Mono.empty());

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.user.events", 0, 0, "key", jsonPayload);

        StepVerifier.create(consumer.consume(record)).verifyComplete();

        verify(userRepo).indexUser(any(UserDocument.class));
    }

    @Test
    void consume_WithUpdateEvent_ShouldUpdateUser() throws Exception {
        String jsonPayload = """
                {
                    "eventType": "UPDATE",
                    "data": {
                        "id": "user123",
                        "followerCount": 200
                    }
                }
                """;

        JsonNode node = mock(JsonNode.class);
        JsonNode eventTypeNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);

        when(objectMapper.readTree(jsonPayload)).thenReturn(node);
        when(node.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("UPDATE");
        when(node.get("data")).thenReturn(dataNode);

        setupJsonFieldMock(dataNode, "id", "user123");
        setupJsonIntMock(dataNode, "followerCount", 200);

        // Mock missing fields to return null
        when(dataNode.has("displayName")).thenReturn(false);
        when(dataNode.has("username")).thenReturn(false);
        when(dataNode.has("bio")).thenReturn(false);
        when(dataNode.has("avatarUrl")).thenReturn(false);
        when(dataNode.has("postCount")).thenReturn(false);

        when(userRepo.indexUser(any(UserDocument.class))).thenReturn(Mono.empty());

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.user.events", 0, 0, "key", jsonPayload);

        StepVerifier.create(consumer.consume(record)).verifyComplete();

        verify(userRepo).indexUser(any(UserDocument.class));
    }

    @Test
    void consume_WithDeleteEvent_ShouldSkipIndexRemoval() throws Exception {
        String jsonPayload = """
                {
                    "eventType": "DELETE",
                    "data": {
                        "id": "user123"
                    }
                }
                """;

        JsonNode node = mock(JsonNode.class);
        JsonNode eventTypeNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);

        when(objectMapper.readTree(jsonPayload)).thenReturn(node);
        when(node.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("DELETE");
        when(node.get("data")).thenReturn(dataNode);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.user.events", 0, 0, "key", jsonPayload);

        StepVerifier.create(consumer.consume(record)).verifyComplete();

        // Delete events skip search index removal
        verify(userRepo, never()).indexUser(any());
    }

    @Test
    void consume_WithUnknownEventType_ShouldLogWarning() throws Exception {
        String jsonPayload = """
                {
                    "eventType": "UNKNOWN",
                    "data": {}
                }
                """;

        JsonNode node = mock(JsonNode.class);
        JsonNode eventTypeNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);

        when(objectMapper.readTree(jsonPayload)).thenReturn(node);
        when(node.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("UNKNOWN");
        when(node.get("data")).thenReturn(dataNode);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.user.events", 0, 0, "key", jsonPayload);

        StepVerifier.create(consumer.consume(record)).verifyComplete();

        verify(userRepo, never()).indexUser(any());
    }

    @Test
    void consume_WithInvalidJson_ShouldLogErrorAndSkip() throws Exception {
        String invalidJson = "{ invalid json";

        when(objectMapper.readTree(anyString())).thenThrow(new RuntimeException("Invalid JSON"));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.user.events", 0, 0, "key", invalidJson);

        // Invalid JSON cannot be processed: logged and skipped (offset committed, no retry)
        StepVerifier.create(consumer.consume(record)).verifyComplete();

        verify(userRepo, never()).indexUser(any());
    }

    private void setupJsonFieldMock(JsonNode node, String field, String value) {
        JsonNode fieldNode = mock(JsonNode.class);
        when(node.has(field)).thenReturn(true);
        when(node.get(field)).thenReturn(fieldNode);
        when(fieldNode.isNull()).thenReturn(false);
        when(fieldNode.asText()).thenReturn(value);
    }

    private void setupJsonIntMock(JsonNode node, String field, int value) {
        JsonNode fieldNode = mock(JsonNode.class);
        when(node.has(field)).thenReturn(true);
        when(node.get(field)).thenReturn(fieldNode);
        when(fieldNode.isNull()).thenReturn(false);
        when(fieldNode.asInt()).thenReturn(value);
    }
}

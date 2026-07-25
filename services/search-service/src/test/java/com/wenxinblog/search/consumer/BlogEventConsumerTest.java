package com.wenxinblog.search.consumer;

import com.wenxinblog.search.model.BlogDocument;
import com.wenxinblog.search.repository.BlogSearchRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlogEventConsumerTest {

    @Mock
    private BlogSearchRepository blogRepo;

    @Mock
    private ObjectMapper objectMapper;

    private BlogEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BlogEventConsumer(blogRepo, objectMapper);
    }

    @Test
    void consume_WithCreateEvent_ShouldIndexBlog() throws Exception {
        String jsonPayload = """
                {
                    "eventType": "CREATE",
                    "data": {
                        "id": "blog123",
                        "title": "Test Blog",
                        "content": "Test content",
                        "summary": "Test summary",
                        "authorId": "author1",
                        "authorName": "Author Name",
                        "tags": ["java", "spring"],
                        "category": "tech",
                        "status": "PUBLISHED",
                        "viewCount": 0,
                        "likeCount": 0,
                        "commentCount": 0,
                        "publishedAt": "2024-01-01T00:00:00",
                        "_eventType": "CREATE"
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

        // Mock field getters
        setupJsonFieldMock(dataNode, "id", "blog123");
        setupJsonFieldMock(dataNode, "title", "Test Blog");
        setupJsonFieldMock(dataNode, "content", "Test content");
        setupJsonFieldMock(dataNode, "summary", "Test summary");
        setupJsonFieldMock(dataNode, "authorId", "author1");
        setupJsonFieldMock(dataNode, "authorName", "Author Name");
        setupJsonFieldMock(dataNode, "category", "tech");
        setupJsonFieldMock(dataNode, "status", "PUBLISHED");
        setupJsonIntMock(dataNode, "viewCount", 0);
        setupJsonIntMock(dataNode, "likeCount", 0);
        setupJsonIntMock(dataNode, "commentCount", 0);
        setupJsonFieldMock(dataNode, "publishedAt", "2024-01-01T00:00:00");
        setupJsonFieldMock(dataNode, "_eventType", "CREATE");

        // Mock tags array
        JsonNode tagsNode = mock(JsonNode.class);
        when(dataNode.has("tags")).thenReturn(true);
        when(dataNode.get("tags")).thenReturn(tagsNode);
        when(tagsNode.isArray()).thenReturn(true);

        JsonNode tag1 = mock(JsonNode.class);
        JsonNode tag2 = mock(JsonNode.class);
        when(tag1.asText()).thenReturn("java");
        when(tag2.asText()).thenReturn("spring");
        when(tagsNode.iterator()).thenReturn(List.of(tag1, tag2).iterator());

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.blog.events", 0, 0, "key", jsonPayload);

        consumer.consume(record);

        verify(blogRepo).indexBlog(any(BlogDocument.class));
    }

    @Test
    void consume_WithUpdateEvent_ShouldUpdateBlog() throws Exception {
        String jsonPayload = """
                {
                    "eventType": "UPDATE",
                    "data": {
                        "id": "blog123",
                        "title": "Updated Blog",
                        "_eventType": "UPDATE"
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

        setupJsonFieldMock(dataNode, "id", "blog123");
        setupJsonFieldMock(dataNode, "title", "Updated Blog");
        setupJsonFieldMock(dataNode, "_eventType", "UPDATE");

        // Mock missing fields to return null/missing
        when(dataNode.has("content")).thenReturn(false);
        when(dataNode.has("summary")).thenReturn(false);
        when(dataNode.has("authorId")).thenReturn(false);
        when(dataNode.has("authorName")).thenReturn(false);
        when(dataNode.has("category")).thenReturn(false);
        when(dataNode.has("status")).thenReturn(false);
        when(dataNode.has("viewCount")).thenReturn(false);
        when(dataNode.has("likeCount")).thenReturn(false);
        when(dataNode.has("commentCount")).thenReturn(false);
        when(dataNode.has("tags")).thenReturn(false);
        when(dataNode.has("publishedAt")).thenReturn(false);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.blog.events", 0, 0, "key", jsonPayload);

        consumer.consume(record);

        verify(blogRepo).updateBlog(any(BlogDocument.class));
    }

    @Test
    void consume_WithDeleteEvent_ShouldDeleteBlog() throws Exception {
        String jsonPayload = """
                {
                    "eventType": "DELETE",
                    "data": {
                        "id": "blog123"
                    }
                }
                """;

        JsonNode node = mock(JsonNode.class);
        JsonNode eventTypeNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        JsonNode idNode = mock(JsonNode.class);

        when(objectMapper.readTree(jsonPayload)).thenReturn(node);
        when(node.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("DELETE");
        when(node.get("data")).thenReturn(dataNode);
        when(dataNode.get("id")).thenReturn(idNode);
        when(idNode.asText()).thenReturn("blog123");
        when(idNode.asString()).thenReturn("blog123"); // Jackson 3

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.blog.events", 0, 0, "key", jsonPayload);

        consumer.consume(record);

        verify(blogRepo).deleteBlog("blog123");
    }

    @Test
    void consume_WithInvalidJson_ShouldLogErrorAndNotThrow() throws Exception {
        String invalidJson = "{ invalid json";

        when(objectMapper.readTree(anyString())).thenThrow(new RuntimeException("Invalid JSON"));

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.blog.events", 0, 0, "key", invalidJson);

        // Should not throw exception
        consumer.consume(record);

        verify(blogRepo, never()).indexBlog(any());
        verify(blogRepo, never()).updateBlog(any());
        verify(blogRepo, never()).deleteBlog(any());
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

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.blog.events", 0, 0, "key", jsonPayload);

        consumer.consume(record);

        verify(blogRepo, never()).indexBlog(any());
        verify(blogRepo, never()).updateBlog(any());
        verify(blogRepo, never()).deleteBlog(any());
    }

    @Test
    void consume_WithTags_ShouldParseTagsArray() throws Exception {
        String jsonPayload = """
                {
                    "eventType": "CREATE",
                    "data": {
                        "id": "blog123",
                        "tags": ["java", "spring", "kotlin"]
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

        setupJsonFieldMock(dataNode, "id", "blog123");

        JsonNode tagsNode = mock(JsonNode.class);
        when(dataNode.has("tags")).thenReturn(true);
        when(dataNode.get("tags")).thenReturn(tagsNode);
        when(tagsNode.isArray()).thenReturn(true);

        JsonNode tag1 = mock(JsonNode.class);
        JsonNode tag2 = mock(JsonNode.class);
        JsonNode tag3 = mock(JsonNode.class);
        when(tag1.asText()).thenReturn("java");
        when(tag2.asText()).thenReturn("spring");
        when(tag3.asText()).thenReturn("kotlin");
        when(tagsNode.iterator()).thenReturn(List.of(tag1, tag2, tag3).iterator());

        // Mock _eventType to trigger CREATE
        setupJsonFieldMock(dataNode, "_eventType", "CREATE");

        // Mock missing fields to return null/missing
        when(dataNode.has("content")).thenReturn(false);
        when(dataNode.has("summary")).thenReturn(false);
        when(dataNode.has("authorId")).thenReturn(false);
        when(dataNode.has("authorName")).thenReturn(false);
        when(dataNode.has("category")).thenReturn(false);
        when(dataNode.has("status")).thenReturn(false);
        when(dataNode.has("viewCount")).thenReturn(false);
        when(dataNode.has("likeCount")).thenReturn(false);
        when(dataNode.has("commentCount")).thenReturn(false);
        when(dataNode.has("publishedAt")).thenReturn(false);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("wenxinblog.blog.events", 0, 0, "key", jsonPayload);

        consumer.consume(record);

        verify(blogRepo).indexBlog(any(BlogDocument.class));
    }

    private void setupJsonFieldMock(JsonNode node, String field, String value) {
        JsonNode fieldNode = mock(JsonNode.class);
        when(node.has(field)).thenReturn(true);
        when(node.get(field)).thenReturn(fieldNode);
        when(fieldNode.isNull()).thenReturn(false);
        when(fieldNode.asText()).thenReturn(value);
        when(fieldNode.asString()).thenReturn(value); // Jackson 3 用 asString
    }

    private void setupJsonIntMock(JsonNode node, String field, int value) {
        JsonNode fieldNode = mock(JsonNode.class);
        when(node.has(field)).thenReturn(true);
        when(node.get(field)).thenReturn(fieldNode);
        when(fieldNode.isNull()).thenReturn(false);
        when(fieldNode.asInt()).thenReturn(value);
    }
}

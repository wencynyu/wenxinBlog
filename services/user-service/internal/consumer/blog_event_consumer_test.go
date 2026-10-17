package consumer

import (
	"errors"
	"testing"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

type mockCounter struct {
	incremented []uuid.UUID
	decremented []uuid.UUID
	err         error
}

func (m *mockCounter) IncrementPostCount(id uuid.UUID) error {
	if m.err != nil {
		return m.err
	}
	m.incremented = append(m.incremented, id)
	return nil
}

func (m *mockCounter) DecrementPostCount(id uuid.UUID) error {
	if m.err != nil {
		return m.err
	}
	m.decremented = append(m.decremented, id)
	return nil
}

func event(eventType, authorID, status, previousStatus string) []byte {
	prev := ""
	if previousStatus != "" {
		prev = `,"previousStatus":"` + previousStatus + `"`
	}
	return []byte(`{"eventType":"` + eventType + `","data":{"id":"p1","authorId":"` +
		authorID + `","status":"` + status + `"` + prev + `}}`)
}

func TestApplyBlogEvent_table(t *testing.T) {
	author := uuid.New().String()
	cases := []struct {
		name       string
		payload    []byte
		increments int
		decrements int
	}{
		{"create published +1", event("CREATE", author, "published", ""), 1, 0},
		{"create draft no-op", event("CREATE", author, "draft", ""), 0, 0},
		{"update draft->published +1", event("UPDATE", author, "published", "draft"), 1, 0},
		{"update published->draft -1", event("UPDATE", author, "draft", "published"), 0, 1},
		{"update published edit no-op", event("UPDATE", author, "published", "published"), 0, 0},
		{"delete published -1", event("DELETE", author, "published", ""), 0, 1},
		{"delete draft no-op", event("DELETE", author, "draft", ""), 0, 0},
		// 老版本 DELETE 事件只有 {"id"}：无法归属 → 跳过且不报错
		{"legacy delete no authorId", []byte(`{"eventType":"DELETE","data":{"id":"p1"}}`), 0, 0},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			c := &mockCounter{}
			assert.NoError(t, ApplyBlogEvent(tc.payload, c))
			assert.Len(t, c.incremented, tc.increments)
			assert.Len(t, c.decremented, tc.decrements)
		})
	}
}

func TestApplyBlogEvent_invalidJson_returnsError(t *testing.T) {
	c := &mockCounter{}
	assert.Error(t, ApplyBlogEvent([]byte("not json"), c))
	assert.Empty(t, c.incremented)
}

func TestApplyBlogEvent_counterErrorPropagates(t *testing.T) {
	author := uuid.New().String()
	c := &mockCounter{err: errors.New("db down")}
	assert.ErrorIs(t, ApplyBlogEvent(event("CREATE", author, "published", ""), c), c.err)
}

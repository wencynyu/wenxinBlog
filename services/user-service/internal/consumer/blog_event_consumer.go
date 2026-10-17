// Package consumer 提供 user-service 的 Kafka 消费者。
// 目前仅消费 wenxinblog.blog.events 维护博文计数（user_stats.post_count），
// 与 search-service / recommendation-service 的消费者组互不干扰（groupId=user-service）。
package consumer

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"time"

	"github.com/google/uuid"
	"github.com/segmentio/kafka-go"
)

const (
	// BlogEventsTopic 与 blog-service BlogEventPublisher.TOPIC 一致。
	BlogEventsTopic = "wenxinblog.blog.events"
	// BlogEventsGroupID user-service 消费者组（独立 offset，不影响 search/rec）。
	BlogEventsGroupID = "user-service"
)

// PostCounter 博文计数写入接口（*repository.StatsRepository 实现）。
type PostCounter interface {
	IncrementPostCount(userID uuid.UUID) error
	DecrementPostCount(userID uuid.UUID) error
}

// blogEvent wenxinblog.blog.events 信封；只声明 user-service 关心的字段，
// 其余（title/content/tags...）由 json.Unmarshal 忽略，契约向后兼容。
type blogEvent struct {
	EventType string `json:"eventType"`
	Data      struct {
		ID             string `json:"id"`
		AuthorID       string `json:"authorId"`
		Status         string `json:"status"`
		PreviousStatus string `json:"previousStatus"`
	} `json:"data"`
}

// ApplyBlogEvent 把一条博客事件映射为博文计数变更：
//
//	CREATE + published                      → +1
//	UPDATE  draft→published                 → +1
//	UPDATE  published→draft（取消发布）      → -1
//	UPDATE  published→published（普通编辑）  → no-op（previousStatus 区分）
//	DELETE  + status=published              → -1
//
// 草稿操作/缺 authorId（老事件）/无法归属 → no-op。
// 注意 at-least-once 语义下重复投递可能重复计数；计数为派生数据，可对账重建，v1 接受。
func ApplyBlogEvent(payload []byte, counter PostCounter) error {
	var ev blogEvent
	if err := json.Unmarshal(payload, &ev); err != nil {
		return fmt.Errorf("parse blog event: %w", err)
	}
	authorID, err := uuid.Parse(ev.Data.AuthorID)
	if err != nil {
		return nil // 无 authorId 无法归属（如旧版 DELETE 事件），跳过
	}
	switch ev.EventType {
	case "CREATE":
		if ev.Data.Status == "published" {
			return counter.IncrementPostCount(authorID)
		}
	case "UPDATE":
		prev, cur := ev.Data.PreviousStatus, ev.Data.Status
		if prev != "published" && cur == "published" {
			return counter.IncrementPostCount(authorID)
		}
		if prev == "published" && cur != "published" {
			return counter.DecrementPostCount(authorID)
		}
	case "DELETE":
		if ev.Data.Status == "published" {
			return counter.DecrementPostCount(authorID)
		}
	}
	return nil
}

// BlogEventConsumer 消费循环。处理失败也提交 offset：计数是派生数据，
// 毒丸事件（坏 JSON/非法 UUID）重放也不会成功，不应阻塞后续事件。
type BlogEventConsumer struct {
	reader  *kafka.Reader
	counter PostCounter
}

func NewBlogEventConsumer(brokers []string, counter PostCounter) *BlogEventConsumer {
	return &BlogEventConsumer{
		reader: kafka.NewReader(kafka.ReaderConfig{
			Brokers:     brokers,
			GroupID:     BlogEventsGroupID,
			Topic:       BlogEventsTopic,
			MinBytes:    1,
			MaxBytes:    10e6, // 10MB，与事件体量（含博文全文）留足余量
			MaxWait:     500 * time.Millisecond,
			StartOffset: kafka.FirstOffset,
		}),
		counter: counter,
	}
}

// Run 阻塞消费直到 ctx 取消（进程退出即终止，与现有服务无优雅停机的风格一致）。
func (c *BlogEventConsumer) Run(ctx context.Context) {
	for {
		m, err := c.reader.FetchMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("[blog-event-consumer] fetch error: %v", err)
			continue
		}
		if err := ApplyBlogEvent(m.Value, c.counter); err != nil {
			log.Printf("[blog-event-consumer] apply failed (partition=%d offset=%d): %v",
				m.Partition, m.Offset, err)
		}
		if err := c.reader.CommitMessages(ctx, m); err != nil {
			log.Printf("[blog-event-consumer] commit error: %v", err)
		}
	}
}

// Close 释放底层连接。
func (c *BlogEventConsumer) Close() error {
	return c.reader.Close()
}

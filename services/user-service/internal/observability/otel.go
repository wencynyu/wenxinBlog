// Package observability 初始化 OTel TracerProvider + MeterProvider + LoggerProvider，
// 经 OTLP gRPC 导出到 OTEL_EXPORTER_OTLP_ENDPOINT（默认 localhost:4317）。
// traces/metrics/logs 走同一 collector 管道；标准库 log.Printf 同时被桥接为 OTel LogRecord。
package observability

import (
	"context"
	"io"
	"log"
	"os"
	"strings"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlplog/otlploggrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	otellog "go.opentelemetry.io/otel/log"
	"go.opentelemetry.io/otel/propagation"
	logsdk "go.opentelemetry.io/otel/sdk/log"
	"go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	"go.opentelemetry.io/otel/sdk/trace"
)

// Setup 初始化 OTel，返回 shutdown 函数（main 中 defer 调用）。
func Setup(serviceName string) func(context.Context) error {
	ctx := context.Background()

	endpoint := os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
	if endpoint == "" {
		endpoint = "localhost:4317"
	}
	// gRPC WithEndpoint 需要 host:port（去掉 scheme）。
	grpcEndpoint := strings.TrimPrefix(strings.TrimPrefix(endpoint, "https://"), "http://")

	res, err := resource.New(ctx,
		resource.WithAttributes(attribute.String("service.name", serviceName)),
	)
	if err != nil {
		log.Printf("[otel] resource: %v", err)
	}

	tExp, err := otlptracegrpc.New(ctx,
		otlptracegrpc.WithEndpoint(grpcEndpoint),
		otlptracegrpc.WithInsecure(),
	)
	if err != nil {
		log.Printf("[otel] trace exporter: %v", err)
	}
	tp := trace.NewTracerProvider(
		trace.WithBatcher(tExp),
		trace.WithResource(res),
	)
	otel.SetTracerProvider(tp)

	mExp, err := otlpmetricgrpc.New(ctx,
		otlpmetricgrpc.WithEndpoint(grpcEndpoint),
		otlpmetricgrpc.WithInsecure(),
	)
	if err != nil {
		log.Printf("[otel] metric exporter: %v", err)
	}
	mp := metric.NewMeterProvider(
		metric.WithReader(metric.NewPeriodicReader(mExp, metric.WithInterval(15*time.Second))),
		metric.WithResource(res),
	)
	otel.SetMeterProvider(mp)

	// 日志：OTLP exporter + LoggerProvider，并桥接标准库 log → OTel LogRecord。
	lExp, err := otlploggrpc.New(ctx,
		otlploggrpc.WithEndpoint(grpcEndpoint),
		otlploggrpc.WithInsecure(),
	)
	if err != nil {
		log.Printf("[otel] log exporter: %v", err)
	}
	lp := logsdk.NewLoggerProvider(
		logsdk.WithResource(res),
		logsdk.WithProcessor(logsdk.NewBatchProcessor(lExp)),
	)
	// 标准库 log 每行 → 一条 OTel 日志（severity Info），同时保留 stderr 输出。
	log.SetOutput(io.MultiWriter(os.Stderr, &otelLogWriter{logger: lp.Logger(serviceName)}))

	// traceparent 传播（跨服务串联 trace）
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{}, propagation.Baggage{},
	))

	return func(ctx context.Context) error {
		_ = tp.Shutdown(ctx)
		_ = mp.Shutdown(ctx)
		return lp.Shutdown(ctx)
	}
}

// otelLogWriter 把标准库 log 的每行输出转成一条 OTel LogRecord。
// 实现 io.Writer，挂在 log.SetOutput 的 MultiWriter 上，对所有现有 log.Printf 零侵入。
type otelLogWriter struct {
	logger otellog.Logger
}

func (w *otelLogWriter) Write(p []byte) (int, error) {
	msg := strings.TrimRight(string(p), "\n")
	var record otellog.Record
	record.SetTimestamp(time.Now())
	record.SetObservedTimestamp(time.Now())
	record.SetSeverity(otellog.SeverityInfo)
	record.SetBody(attribute.StringValue(msg))
	w.logger.Emit(context.Background(), record)
	return len(p), nil
}

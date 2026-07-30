// Package observability 初始化 OTel TracerProvider + MeterProvider，
// 经 OTLP gRPC 导出到 OTEL_EXPORTER_OTLP_ENDPOINT（默认 localhost:4317）。
// traces/metrics 走同一 collector 管道；Go 日志暂留控制台。
package observability

import (
	"context"
	"log"
	"os"
	"strings"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/propagation"
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

	// traceparent 传播（跨服务串联 trace）
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{}, propagation.Baggage{},
	))

	return func(ctx context.Context) error {
		_ = tp.Shutdown(ctx)
		return mp.Shutdown(ctx)
	}
}

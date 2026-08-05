package service

import (
	"context"
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"

	"github.com/google/uuid"
)

// SmsSender 发送短信验证码。生产用 AliyunSmsSender，开发/测试用 MockSmsSender。
type SmsSender interface {
	Send(ctx context.Context, phone, code string) error
}

// MockSmsSender 仅在日志打印验证码，不真正发送（开发/测试用）。
type MockSmsSender struct{}

func (m *MockSmsSender) Send(_ context.Context, phone, code string) error {
	log.Printf("[MOCK SMS] phone=%s code=%s", phone, code)
	return nil
}

// NewSmsSender 按配置选择实现；未知 provider 或缺密钥时回退到 Mock。
func NewSmsSender(provider, accessKeyID, accessKeySecret, signName, templateCode string) SmsSender {
	if provider == "aliyun" && accessKeyID != "" && accessKeySecret != "" && signName != "" && templateCode != "" {
		return &AliyunSmsSender{
			accessKeyID:     accessKeyID,
			accessKeySecret: accessKeySecret,
			signName:        signName,
			templateCode:    templateCode,
			client:          &http.Client{Timeout: 10 * time.Second},
			endpoint:        "https://dysmsapi.aliyuncs.com",
		}
	}
	if provider == "aliyun" {
		log.Println("WARNING: sms provider=aliyun but config incomplete, falling back to mock sender")
	}
	return &MockSmsSender{}
}

// AliyunSmsSender 手写阿里云短信签名调用（HMAC-SHA1，不引 SDK，贴合本仓库离线/最小依赖风格）。
// 上线前请用真实 AccessKey/签名/模板验证一次（见 docs/provider-setup）。
type AliyunSmsSender struct {
	accessKeyID     string
	accessKeySecret string
	signName        string
	templateCode    string
	client          *http.Client
	endpoint        string
}

func (a *AliyunSmsSender) Send(ctx context.Context, phone, code string) error {
	params := map[string]string{
		"SignatureMethod":  "HMAC-SHA1",
		"SignatureNonce":   uuid.New().String(),
		"AccessKeyId":      a.accessKeyID,
		"SignatureVersion": "1.0",
		"Timestamp":        time.Now().UTC().Format("2006-01-02T15:04:05Z"),
		"Format":           "JSON",
		"Version":          "2017-05-25",
		"Action":           "SendSms",
		"RegionId":         "cn-hangzhou",
		"PhoneNumbers":     phone,
		"SignName":         a.signName,
		"TemplateCode":     a.templateCode,
		"TemplateParam":    fmt.Sprintf(`{"code":"%s"}`, code),
	}

	// 1) 计算签名（不含 Signature 字段）
	canonical := canonicalQuery(params)
	stringToSign := "GET&" + percentEncode("/") + "&" + percentEncode(canonical)
	mac := hmac.New(sha1.New, []byte(a.accessKeySecret+"&"))
	mac.Write([]byte(stringToSign))
	signature := base64.StdEncoding.EncodeToString(mac.Sum(nil))
	params["Signature"] = signature

	// 2) 组装完整请求 URL（含 Signature）
	finalQuery := canonicalQuery(params)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, a.endpoint+"/?"+finalQuery, nil)
	if err != nil {
		return err
	}
	resp, err := a.client.Do(req)
	if err != nil {
		return fmt.Errorf("aliyun sms request: %w", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("aliyun sms: status %d body=%s", resp.StatusCode, string(body))
	}
	var res struct {
		Code    string `json:"Code"`
		Message string `json:"Message"`
	}
	if err := json.Unmarshal(body, &res); err != nil {
		return fmt.Errorf("aliyun sms decode: %w", err)
	}
	if res.Code != "OK" {
		return fmt.Errorf("aliyun sms failed: code=%s message=%s", res.Code, res.Message)
	}
	return nil
}

// canonicalQuery 按 key 排序，逐项 RFC3986 百分号编码，拼成 key=value&... 。
func canonicalQuery(params map[string]string) string {
	keys := make([]string, 0, len(params))
	for k := range params {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	parts := make([]string, 0, len(keys))
	for _, k := range keys {
		parts = append(parts, percentEncode(k)+"="+percentEncode(params[k]))
	}
	return strings.Join(parts, "&")
}

// percentEncode 阿里云要求的 RFC3986 编码（空格 %20、* %2A、~ 不编码）。
func percentEncode(s string) string {
	s = url.QueryEscape(s)
	s = strings.ReplaceAll(s, "+", "%20")
	s = strings.ReplaceAll(s, "*", "%2A")
	s = strings.ReplaceAll(s, "%7E", "~")
	return s
}

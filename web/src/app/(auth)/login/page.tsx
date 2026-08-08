'use client';

import { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { Form, Input, Button, Toast, Tabs, Divider, Card, Typography } from '@douyinfe/semi-ui';
import { useLogin } from '@/hooks/useAuth';
import BrandLogo from '@/components/common/BrandLogo';
import { useAuthStore } from '@/store/authStore';
import { sendPhoneCode, oauthLoginURL } from '@/lib/api/auth';

const SOCIAL_PROVIDERS = [{ key: 'google', label: 'Google' }];

export default function LoginPage() {
  const loginMutation = useLogin();
  const loginWithPhone = useAuthStore((s) => s.loginWithPhone);

  // 手机号登录状态
  const [countdown, setCountdown] = useState(0);
  const [phoneSending, setPhoneSending] = useState(false);
  const [phoneSubmitting, setPhoneSubmitting] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const phoneFormRef = useRef<any>(null);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  const handleEmailSubmit = async (values: any) => {
    try {
      await loginMutation.mutateAsync({ email: values.email, password: values.password });
      Toast.success('登录成功');
    } catch (error: any) {
      Toast.error(error?.message || '登录失败，请检查邮箱和密码');
    }
  };

  const handleSendCode = async (values: any) => {
    const phone = values?.phone;
    if (!phone) {
      Toast.warning('请先输入手机号');
      return;
    }
    setPhoneSending(true);
    try {
      await sendPhoneCode(phone);
      Toast.success('验证码已发送');
      setCountdown(60);
      timerRef.current = setInterval(() => {
        setCountdown((c) => {
          if (c <= 1 && timerRef.current) {
            clearInterval(timerRef.current);
            timerRef.current = null;
            return 0;
          }
          return c - 1;
        });
      }, 1000);
    } catch (error: any) {
      // 后端对成功/限流统一返回，这里仅兜底网络错误
      Toast.error(error?.message || '发送失败，请稍后重试');
    } finally {
      setPhoneSending(false);
    }
  };

  const handlePhoneSubmit = async (values: any) => {
    setPhoneSubmitting(true);
    try {
      await loginWithPhone({ phone: values.phone, code: values.code });
      Toast.success('登录成功');
    } catch (error: any) {
      Toast.error(error?.message || '验证码错误或已失效');
    } finally {
      setPhoneSubmitting(false);
    }
  };

  const handleSocial = (provider: string) => {
    // 顶层导航到网关发起 OAuth（后端设 oauth_state cookie，同源回调）
    window.location.href = oauthLoginURL(provider);
  };

  return (
    <Card bodyStyle={{ padding: 32 }} style={{ borderRadius: 16 }}>
      {/* Logo */}
      <div className="text-center mb-8">
        <BrandLogo size="md" />
        <p className="eyebrow mt-3">{'// sign in'}</p>
      </div>

      <Tabs type="line">
        <Tabs.TabPane tab="邮箱登录" itemKey="email">
          <Form onSubmit={handleEmailSubmit} key="email-form">
            <Form.Input
              field="email"
              label="邮箱"
              placeholder="请输入邮箱"
              rules={[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '请输入有效的邮箱地址' },
              ]}
              size="large"
            />
            <Form.Input
              field="password"
              label="密码"
              placeholder="请输入密码"
              mode="password"
              rules={[{ required: true, message: '请输入密码' }]}
              size="large"
            />
            <Button
              theme="solid"
              type="primary"
              htmlType="submit"
              block
              size="large"
              loading={loginMutation.isPending}
              style={{ marginTop: 24 }}
            >
              登录
            </Button>
          </Form>
        </Tabs.TabPane>

        <Tabs.TabPane tab="手机号登录" itemKey="phone">
          <Form
            onSubmit={handlePhoneSubmit}
            key="phone-form"
            getFormApi={(api: any) => {
              phoneFormRef.current = api;
            }}
          >
            <Form.Input
              field="phone"
              label="手机号"
              placeholder="请输入手机号"
              rules={[
                { required: true, message: '请输入手机号' },
                { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号' },
              ]}
              size="large"
            />
            <div className="flex items-end gap-2">
              <div className="flex-1">
                <Form.Input
                  field="code"
                  label="验证码"
                  placeholder="请输入验证码"
                  rules={[{ required: true, message: '请输入验证码' }]}
                  size="large"
                />
              </div>
              <Button
                theme="light"
                size="large"
                disabled={countdown > 0 || phoneSending}
                loading={phoneSending}
                onClick={() => {
                  const phone = phoneFormRef.current?.getValue('phone');
                  handleSendCode({ phone });
                }}
                style={{ whiteSpace: 'nowrap', marginBottom: 0 }}
              >
                {countdown > 0 ? `${countdown}s` : '获取验证码'}
              </Button>
            </div>
            <Button
              theme="solid"
              type="primary"
              htmlType="submit"
              block
              size="large"
              loading={phoneSubmitting}
              style={{ marginTop: 24 }}
            >
              登录
            </Button>
          </Form>
        </Tabs.TabPane>
      </Tabs>

      {/* 第三方登录 */}
      <Divider margin={24}>
        <span className="text-ink-muted text-sm">或使用第三方登录</span>
      </Divider>
      <div className="flex gap-3">
        {SOCIAL_PROVIDERS.map((p) => (
          <Button key={p.key} theme="light" block size="large" onClick={() => handleSocial(p.key)}>
            {p.label} 登录
          </Button>
        ))}
      </div>

      <div className="text-center mt-6">
        <Typography.Text type="tertiary">还没有账号？</Typography.Text>{' '}
        <Link href="/register" className="ml-1 font-medium">
          立即注册
        </Link>
      </div>
    </Card>
  );
}

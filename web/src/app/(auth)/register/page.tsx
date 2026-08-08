'use client';

import Link from 'next/link';
import { Form, Input, Button, Toast, Divider, Card, Typography } from '@douyinfe/semi-ui';
import { useRegister } from '@/hooks/useAuth';
import { oauthLoginURL } from '@/lib/api/auth';
import BrandLogo from '@/components/common/BrandLogo';

export default function RegisterPage() {
  const registerMutation = useRegister();

  const handleSubmit = async (values: any) => {
    if (values.password !== values.confirmPassword) {
      Toast.error('两次输入的密码不一致');
      return;
    }
    try {
      await registerMutation.mutateAsync({
        username: values.username,
        email: values.email,
        password: values.password,
        displayName: values.displayName,
      });
      Toast.success('注册成功');
    } catch (error: any) {
      Toast.error(error?.message || '注册失败，请稍后再试');
    }
  };

  return (
    <Card bodyStyle={{ padding: 32 }} style={{ borderRadius: 16 }}>
      {/* Logo */}
      <div className="text-center mb-8">
        <BrandLogo size="md" />
        <p className="eyebrow mt-3">{'// create account'}</p>
      </div>

      <Form onSubmit={handleSubmit}>
        <Form.Input
          field="username"
          label="用户名"
          placeholder="请输入用户名"
          rules={[
            { required: true, message: '请输入用户名' },
            { min: 3, message: '用户名至少3个字符' },
            { max: 20, message: '用户名最多20个字符' },
          ]}
          size="large"
        />

        <Form.Input
          field="displayName"
          label="昵称"
          placeholder="请输入昵称（可选）"
          size="large"
        />

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
          rules={[
            { required: true, message: '请输入密码' },
            { min: 6, message: '密码至少6个字符' },
          ]}
          size="large"
        />

        <Form.Input
          field="confirmPassword"
          label="确认密码"
          placeholder="请再次输入密码"
          mode="password"
          rules={[{ required: true, message: '请确认密码' }]}
          size="large"
        />

        <Button
          theme="solid"
          type="primary"
          htmlType="submit"
          block
          size="large"
          loading={registerMutation.isPending}
          style={{ marginTop: 24 }}
        >
          注册
        </Button>
      </Form>

      <Divider margin={24}>
        <span className="text-ink-muted text-sm">或使用 Google 注册</span>
      </Divider>
      <Button
        theme="light"
        block
        size="large"
        onClick={() => (window.location.href = oauthLoginURL('google'))}
      >
        Google 注册
      </Button>

      <div className="text-center mt-6">
        <Typography.Text type="tertiary">已有账号？</Typography.Text>{' '}
        <Link href="/login" className="ml-1 font-medium">
          立即登录
        </Link>
      </div>
    </Card>
  );
}

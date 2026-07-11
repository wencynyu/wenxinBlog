'use client';

import Link from 'next/link';
import { Form, Input, Button, Toast } from '@douyinfe/semi-ui';
import { useLogin } from '@/hooks/useAuth';

export default function LoginPage() {
  const loginMutation = useLogin();

  const handleSubmit = async (values: any) => {
    try {
      await loginMutation.mutateAsync({
        email: values.email,
        password: values.password,
      });
      Toast.success('登录成功');
    } catch (error: any) {
      Toast.error(error?.message || '登录失败，请检查邮箱和密码');
    }
  };

  return (
    <div className="bg-surface rounded-2xl shadow-card p-8">
      {/* Logo */}
      <div className="text-center mb-8">
        <Link href="/" className="inline-flex items-center space-x-2">
          <div className="h-10 w-10 rounded-xl bg-primary-600 flex items-center justify-center">
            <span className="text-white font-bold text-xl font-mono">W</span>
          </div>
          <span className="font-serif font-bold text-2xl text-ink">WenxinBlog</span>
        </Link>
        <p className="eyebrow mt-3">{'// sign in'}</p>
      </div>

      <Form onSubmit={handleSubmit}>
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

      <div className="text-center mt-6 text-sm text-ink-muted">
        还没有账号？
        <Link href="/register" className="text-primary-700 hover:text-primary-700 ml-1 font-medium">
          立即注册
        </Link>
      </div>
    </div>
  );
}

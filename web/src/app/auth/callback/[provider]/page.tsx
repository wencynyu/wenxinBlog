'use client';

import { useEffect, useState } from 'react';
import { useParams, useSearchParams, useRouter } from 'next/navigation';
import { Spin, Toast } from '@douyinfe/semi-ui';
import { exchangeOAuthCode } from '@/lib/api/auth';
import { useAuthStore } from '@/store/authStore';

// React StrictMode（dev）会把 effect 执行两次 → 同一中间码被兑换两次，
// 第二次必 401（码已被首次消费）→ 触发全局 401 拦截器清 token、覆盖成功结果。
// 用模块级 Set 对「已处理的 code」去重，跨 StrictMode remount 保留。
const processedCodes = new Set<string>();

/**
 * OAuth 回调处理页（BFF 中间码模式）。
 * provider → 网关 → auth-service 换取后，302 到本页带 ?code=<中间码>（或 ?error=...）。
 * 本页用中间码兑换登录态：login→建立会话并跳首页；link→提示后回设置页。
 */
export default function OAuthCallbackPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const router = useRouter();
  const setSession = useAuthStore((s) => s.setSession);
  const [status, setStatus] = useState('正在完成登录…');

  useEffect(() => {
    const provider = String(params?.provider || '');
    const code = searchParams.get('code');
    const error = searchParams.get('error');

    if (error) {
      Toast.error(`授权失败：${error}`);
      router.replace('/login');
      return;
    }
    if (!code || !provider) {
      Toast.error('授权参数缺失');
      router.replace('/login');
      return;
    }
    // 同一 code 只处理一次（防 StrictMode 双触发 / 用户刷新重放）
    if (processedCodes.has(code)) return;
    processedCodes.add(code);

    exchangeOAuthCode(code)
      .then((res) => {
        if (res.outcome === 'login' && res.user && res.tokens) {
          setSession({ user: res.user, tokens: res.tokens });
          Toast.success('登录成功');
          // 硬跳转，确保整页用新 token 重新初始化（避免部分组件读到旧状态）
          window.location.href = '/';
        } else if (res.outcome === 'link') {
          Toast.success(`已绑定 ${res.provider || ''}`);
          router.replace('/settings');
        } else {
          Toast.error('登录失败，请重试');
          router.replace('/login');
        }
      })
      .catch((e: any) => {
        Toast.error(e?.message || '登录失败，请重试');
        router.replace('/login');
      })
      .finally(() => setStatus('处理中…'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-canvas gap-4">
      <Spin size="large" />
      <span className="text-ink-muted text-sm">{status}</span>
    </div>
  );
}

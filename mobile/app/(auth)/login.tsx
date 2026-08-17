import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';
import { Link } from 'expo-router';
import { BrandMark, GlyphWatermark } from '@/components/BrandMark';
import { FormInput } from '@/components/FormInput';
import { PrimaryButton } from '@/components/PrimaryButton';
import { useLogin } from '@/hooks/useAuth';
import { getErrorMessage } from '@/lib/api/client';

export default function LoginScreen() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const login = useLogin();

  const submit = () => {
    if (!email.trim() || !password) return;
    login.mutate({ email: email.trim(), password });
  };

  return (
    <View className="flex-1 overflow-hidden bg-canvas">
      <GlyphWatermark />
      <KeyboardAvoidingView
        className="flex-1"
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          className="flex-1"
          contentContainerClassName="grow px-6 pb-10 pt-24"
          keyboardShouldPersistTaps="handled"
        >
          {/* 品牌区：印章字标 + 一句话定位 */}
          <View className="gap-3">
            <BrandMark />
            <Text className="text-sm text-ink-faint">WenxinBlog · 记录与阅读</Text>
          </View>

          {/* 标题区 */}
          <View className="mt-12 gap-1.5">
            <Text className="text-[26px] font-bold leading-tight text-ink">欢迎回来</Text>
            <Text className="text-sm text-ink-muted">继续你的阅读与写作</Text>
          </View>

          {/* 表单 */}
          <View className="mt-9 gap-5">
            <FormInput
              label="邮箱"
              value={email}
              onChangeText={setEmail}
              placeholder="you@example.com"
              keyboardType="email-address"
            />
            <FormInput
              label="密码"
              value={password}
              onChangeText={setPassword}
              placeholder="请输入密码"
              secureTextEntry
            />
            {login.isError ? (
              <Text className="text-sm" style={{ color: '#ef4444' }}>
                {getErrorMessage(login.error, '登录失败，请稍后重试')}
              </Text>
            ) : null}
            <PrimaryButton
              label="登录"
              onPress={submit}
              loading={login.isPending}
              disabled={!email.trim() || !password}
            />
          </View>

          <View className="mt-9 flex-row justify-center gap-1">
            <Text className="text-sm text-ink-muted">还没有账号？</Text>
            <Link href="/(auth)/register" asChild>
              <Pressable accessibilityRole="link" accessibilityLabel="去注册">
                <Text className="text-sm font-semibold text-primary">去注册</Text>
              </Pressable>
            </Link>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

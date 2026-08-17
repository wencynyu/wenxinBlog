import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';
import { Link } from 'expo-router';
import { BrandMark, GlyphWatermark } from '@/components/BrandMark';
import { FormInput } from '@/components/FormInput';
import { PrimaryButton } from '@/components/PrimaryButton';
import { useRegister } from '@/hooks/useAuth';
import { getErrorMessage } from '@/lib/api/client';

interface FieldErrors {
  username?: string;
  email?: string;
  password?: string;
  confirm?: string;
}

export default function RegisterScreen() {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const register = useRegister();

  const validate = (): boolean => {
    const next: FieldErrors = {};
    if (username.trim().length < 2) next.username = '用户名至少 2 个字符';
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) next.email = '邮箱格式不正确';
    if (password.length < 6) next.password = '密码至少 6 位';
    if (confirm !== password) next.confirm = '两次输入的密码不一致';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = () => {
    if (!validate()) return;
    register.mutate({
      username: username.trim(),
      email: email.trim(),
      password,
    });
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
          contentContainerClassName="grow px-6 pb-10 pt-16"
          keyboardShouldPersistTaps="handled"
        >
          <BrandMark />

          <View className="mt-10 gap-1.5">
            <Text className="text-[26px] font-bold leading-tight text-ink">创建你的账号</Text>
            <Text className="text-sm text-ink-muted">注册后即可发布博文、评论与收藏</Text>
          </View>

          <View className="mt-8 gap-4">
            <FormInput
              label="用户名"
              value={username}
              onChangeText={setUsername}
              placeholder="至少 2 个字符"
              error={errors.username}
            />
            <FormInput
              label="邮箱"
              value={email}
              onChangeText={setEmail}
              placeholder="you@example.com"
              keyboardType="email-address"
              error={errors.email}
            />
            <FormInput
              label="密码"
              value={password}
              onChangeText={setPassword}
              placeholder="至少 6 位"
              secureTextEntry
              error={errors.password}
            />
            <FormInput
              label="确认密码"
              value={confirm}
              onChangeText={setConfirm}
              placeholder="再输入一次密码"
              secureTextEntry
              error={errors.confirm}
            />
            {register.isError ? (
              <Text className="text-sm" style={{ color: '#ef4444' }}>
                {getErrorMessage(register.error, '注册失败，请稍后重试')}
              </Text>
            ) : null}
            <PrimaryButton
              label="注册并登录"
              onPress={submit}
              loading={register.isPending}
              disabled={!username || !email || !password || !confirm}
            />
          </View>

          <View className="mt-8 flex-row justify-center gap-1">
            <Text className="text-sm text-ink-muted">已有账号？</Text>
            <Link href="/(auth)/login" asChild>
              <Pressable accessibilityRole="link" accessibilityLabel="去登录">
                <Text className="text-sm font-semibold text-primary">去登录</Text>
              </Pressable>
            </Link>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

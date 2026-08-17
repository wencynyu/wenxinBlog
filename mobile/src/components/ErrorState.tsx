import { Pressable, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

export function ErrorState({ message, onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <View className="flex-1 items-center justify-center gap-3 bg-canvas px-8">
      <Ionicons name="cloud-offline-outline" size={48} color="#8a919c" />
      <Text className="text-ink font-medium text-base">{message || '加载失败'}</Text>
      <Text className="text-ink-faint text-sm text-center">请检查网络连接或后端服务是否可用</Text>
      {onRetry ? (
        <Pressable
          onPress={onRetry}
          className="mt-2 rounded-lg bg-primary px-5 py-2.5 active:opacity-70"
          accessibilityRole="button"
          accessibilityLabel="重试"
        >
          <Text className="text-white font-medium">重试</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

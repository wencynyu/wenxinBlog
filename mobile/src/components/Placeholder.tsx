import { Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

/** 尚未开发的功能页占位（发现/发布/通知等，见 PROGRESS 规划） */
export function Placeholder({ title, description }: { title: string; description?: string }) {
  return (
    <View className="flex-1 items-center justify-center gap-2 bg-canvas px-8">
      <Ionicons name="construct-outline" size={48} color="#8a919c" />
      <Text className="text-ink font-medium text-base">{title}·开发中</Text>
      <Text className="text-ink-faint text-sm text-center">
        {description || '该功能在后续版本中提供'}
      </Text>
    </View>
  );
}

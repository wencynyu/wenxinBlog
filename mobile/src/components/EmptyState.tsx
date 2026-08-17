import { Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

export function EmptyState({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <View className="items-center justify-center gap-2 px-8 py-16">
      <Ionicons name="file-tray-outline" size={48} color="#8a919c" />
      <Text className="text-ink font-medium text-base">{title}</Text>
      {subtitle ? <Text className="text-ink-faint text-sm text-center">{subtitle}</Text> : null}
    </View>
  );
}

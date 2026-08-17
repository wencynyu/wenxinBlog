import { ActivityIndicator, Text, View } from 'react-native';

export function Spinner({ label }: { label?: string }) {
  return (
    <View className="flex-1 items-center justify-center bg-canvas gap-3">
      <ActivityIndicator size="large" color="#0077fa" />
      {label ? <Text className="text-ink-faint text-sm">{label}</Text> : null}
    </View>
  );
}

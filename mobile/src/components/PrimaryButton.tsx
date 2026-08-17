import { ActivityIndicator, Pressable, Text } from 'react-native';

export function PrimaryButton({
  label,
  onPress,
  loading = false,
  disabled = false,
}: {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled || loading}
      className="flex-row items-center justify-center gap-2 rounded-xl bg-primary py-3.5 active:opacity-80 disabled:opacity-60"
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: disabled || loading, busy: loading }}
    >
      {loading ? <ActivityIndicator size="small" color="#ffffff" /> : null}
      <Text className="text-base font-semibold text-white">{label}</Text>
    </Pressable>
  );
}

import { useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';

export function FormInput({
  label,
  value,
  onChangeText,
  placeholder,
  error,
  secureTextEntry = false,
  autoCapitalize = 'none',
  keyboardType = 'default',
}: {
  label: string;
  value: string;
  onChangeText: (text: string) => void;
  placeholder?: string;
  error?: string;
  secureTextEntry?: boolean;
  autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
  keyboardType?: 'default' | 'email-address' | 'numeric';
}) {
  const [hidden, setHidden] = useState(secureTextEntry);
  const [focused, setFocused] = useState(false);

  // 错误 > 聚焦（品牌蓝）> 默认（hairline）
  const borderColor = error ? '#ef4444' : focused ? '#0077fa' : undefined;

  return (
    <View className="gap-1.5">
      <Text className="text-[13px] font-medium text-ink-muted">{label}</Text>
      <View
        className="flex-row items-center rounded-xl border bg-surface px-3.5"
        style={[borderColor ? { borderColor, borderWidth: focused ? 1.5 : 1 } : undefined]}
      >
        <TextInput
          value={value}
          onChangeText={onChangeText}
          placeholder={placeholder}
          placeholderTextColor="#8a919c"
          secureTextEntry={hidden}
          autoCapitalize={autoCapitalize}
          autoCorrect={false}
          keyboardType={keyboardType}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          className="flex-1 py-3 text-base text-ink"
          accessibilityLabel={label}
        />
        {secureTextEntry ? (
          <Pressable
            onPress={() => setHidden((v) => !v)}
            hitSlop={8}
            accessibilityRole="button"
            accessibilityLabel={hidden ? '显示密码' : '隐藏密码'}
          >
            <Ionicons name={hidden ? 'eye-off-outline' : 'eye-outline'} size={20} color="#8a919c" />
          </Pressable>
        ) : null}
      </View>
      {error ? (
        <Text className="text-[13px]" style={{ color: '#ef4444' }}>
          {error}
        </Text>
      ) : null}
    </View>
  );
}

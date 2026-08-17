import { Text, View } from 'react-native';
import { Image } from 'expo-image';

interface AvatarUser {
  username: string;
  displayName?: string;
  avatar?: string;
}

export function Avatar({ user, size = 24 }: { user?: AvatarUser; size?: number }) {
  const name = user?.displayName || user?.username || '?';

  if (user?.avatar) {
    return (
      <Image
        source={{ uri: user.avatar }}
        style={{ width: size, height: size, borderRadius: size / 2 }}
        contentFit="cover"
        placeholder={undefined}
        accessibilityLabel={`${name} 的头像`}
      />
    );
  }

  return (
    <View
      className="items-center justify-center rounded-full bg-primary-100"
      style={{ width: size, height: size, borderRadius: size / 2 }}
    >
      <Text className="font-semibold text-primary-700" style={{ fontSize: size * 0.45 }}>
        {name.charAt(0).toUpperCase()}
      </Text>
    </View>
  );
}

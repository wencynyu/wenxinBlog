import {
  Pressable,
  useColorScheme,
  type ColorValue,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import type { ReactNode } from 'react';
import { Tabs, useRouter } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { BrandMark } from '@/components/BrandMark';

interface TabIconProps {
  color: ColorValue;
  size: number;
}

function HomeHeaderTitle() {
  return <BrandMark compact />;
}

function SearchHeaderButton() {
  const router = useRouter();
  const dark = useColorScheme() === 'dark';

  return (
    <Pressable
      onPress={() => router.push('/search')}
      hitSlop={10}
      accessibilityRole="button"
      accessibilityLabel="搜索"
    >
      <Ionicons name="search" size={22} color={dark ? '#e8eaed' : '#1c1e21'} />
    </Pressable>
  );
}

function HomeIcon({ color, size }: TabIconProps) {
  return <Ionicons name="home-outline" size={size} color={color} />;
}

function DiscoverIcon({ color, size }: TabIconProps) {
  return <Ionicons name="compass-outline" size={size} color={color} />;
}

function CreateIcon({ color, size }: TabIconProps) {
  return <Ionicons name="add-circle-outline" size={size} color={color} />;
}

/**
 * "发布"是动作按钮而非页面：点击 push 编辑器（保持 (tabs) 在栈里），
 * 这样编辑器始终有返回路径。不能用 <Redirect>——它是 replace 语义，
 * 会把 (tabs) 从栈里替换掉，导致保存后 router.back() 无处可退（GO_BACK 报错）。
 *
 * props 类型只声明与 Pressable 传播相关的字段（tab 栏传入的其余字段透传无害）；
 * @react-navigation/bottom-tabs 嵌套在 expo-router 内，无法直接 import 类型。
 */
interface CreateTabButtonProps {
  children?: ReactNode;
  style?: StyleProp<ViewStyle>;
  accessibilityState?: { disabled?: boolean; selected?: boolean };
}

function CreateTabButton(props: CreateTabButtonProps) {
  const router = useRouter();

  return (
    <Pressable
      {...props}
      onPress={() => router.push('/posts/new')}
      accessibilityRole="button"
      accessibilityLabel="写博文"
    >
      {props.children}
    </Pressable>
  );
}

function NotificationsIcon({ color, size }: TabIconProps) {
  return <Ionicons name="notifications-outline" size={size} color={color} />;
}

function ProfileIcon({ color, size }: TabIconProps) {
  return <Ionicons name="person-outline" size={size} color={color} />;
}

export default function TabsLayout() {
  const dark = useColorScheme() === 'dark';
  const bg = dark ? '#1a1d21' : '#ffffff';
  const fg = dark ? '#e8eaed' : '#1c1e21';

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: '#0077fa',
        headerStyle: { backgroundColor: bg },
        headerTintColor: fg,
        headerShadowVisible: false,
        tabBarStyle: { backgroundColor: bg },
        sceneStyle: { backgroundColor: dark ? '#101214' : '#f7f8fa' },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: '首页',
          headerTitle: HomeHeaderTitle,
          headerRight: SearchHeaderButton,
          tabBarIcon: HomeIcon,
        }}
      />
      <Tabs.Screen name="discover" options={{ title: '发现', tabBarIcon: DiscoverIcon }} />
      <Tabs.Screen
        name="create"
        options={{
          title: '发布',
          tabBarIcon: CreateIcon,
          tabBarButton: CreateTabButton,
        }}
      />
      <Tabs.Screen
        name="notifications"
        options={{ title: '通知', tabBarIcon: NotificationsIcon }}
      />
      <Tabs.Screen name="profile" options={{ title: '我的', tabBarIcon: ProfileIcon }} />
    </Tabs>
  );
}

# iOS Mobile

iOS客户端架构文档 - React Native + Expo

> ⚠️ 本文档为早期规划/理想态，与当前实现出入很大（Semi-Design Mobile / Flash List / Axios / 推送 / 生物识别均未引入，`src/` 为空目录）。导航用 **Expo Router**（非 React Navigation）。**权威现状见 `docs/frontend/mobile.md`**。

## 技术栈

| 技术               | 版本 | 用途                  |
| ------------------ | ---- | --------------------- |
| React Native       | 0.74 | 移动框架              |
| Expo               | 51.0 | 开发工具链            |
| Expo Router        | 3.5  | 文件路由 (File-based) |
| Semi-Design Mobile | 2.12 | UI组件库 (Halation)   |
| Zustand            | 5.0  | 状态管理              |
| TanStack Query     | 5.0  | 数据请求              |
| Axios              | 1.7  | HTTP客户端            |
| Flash List         | 1.6  | 高性能列表            |
| Day.js             | 1.11 | 日期处理              |
| Expo SecureStore   | 1.12 | 安全存储              |

## 项目结构

```
src/
├── app/                    # Expo Router页面
│   ├── (auth)/            # 认证相关 (无Tab导航)
│   │   ├── login.tsx
│   │   ├── register.tsx
│   │   └── oauth.tsx
│   │
│   ├── (main)/            # 主应用 (带Tab导航)
│   │   ├── _layout.tsx    # Tab导航配置
│   │   ├── index.tsx      # 首页 (推荐流)
│   │   ├── search.tsx     # 搜索页
│   │   ├── notifications.tsx # 通知页
│   │   └── profile.tsx    # 个人中心
│   │
│   ├── blog/              # 博文相关
│   │   ├── [id].tsx       # 博文详情
│   │   ├── new.tsx        # 发布博文
│   │   └── edit.tsx       # 编辑博文
│   │
│   └── user/              # 用户相关
│       ├── [id].tsx       # 用户主页
│       └── settings.tsx   # 设置
│
├── components/             # 组件
│   ├── common/            # 通用组件
│   │   ├── Button.tsx
│   │   ├── Input.tsx
│   │   ├── Modal.tsx
│   │   ├── Toast.tsx
│   │   └── Loading.tsx
│   │
│   ├── blog/              # 博文组件
│   │   ├── PostCard.tsx
│   │   ├── PostListItem.tsx
│   │   ├── PostDetail.tsx
│   │   ├── CommentList.tsx
│   │   ├── RichText.tsx   # Markdown渲染
│   │   └── ImageGallery.tsx
│   │
│   ├── user/              # 用户组件
│   │   ├── Avatar.tsx
│   │   ├── UserCard.tsx
│   │   └── FollowButton.tsx
│   │
│   └── navigation/        # 导航组件
│       └── TabBar.tsx
│
├── screens/               # 页面组件 (复杂页面)
│   ├── FeedScreen.tsx
│   ├── PostDetailScreen.tsx
│   ├── PublishScreen.tsx
│   └── ProfileScreen.tsx
│
├── navigation/            # 导航配置
│   └── RootNavigation.tsx
│
├── hooks/                 # 自定义Hooks
│   ├── useAuth.ts
│   ├── useInfiniteScroll.ts
│   ├── useRefresh.ts
│   └── useDebounce.ts
│
├── services/              # API服务
│   ├── api/               # API客户端
│   │   ├── client.ts
│   │   ├── auth.ts
│   │   ├── posts.ts
│   │   └── users.ts
│   └── storage/           # 本地存储
│       ├── secure.ts      # SecureStore
│       └── async.ts       # AsyncStorage
│
├── store/                 # Zustand状态
│   ├── authStore.ts
│   ├── userStore.ts
│   └── uiStore.ts
│
├── types/                 # TypeScript类型
│   ├── auth.ts
│   ├── post.ts
│   ├── user.ts
│   └── navigation.ts
│
├── utils/                 # 工具函数
│   ├── format.ts
│   ├── validation.ts
│   └── constants.ts
│
└── assets/                # 资源文件
    ├── images/
    ├── fonts/
    └── icons/
```

## 核心页面

### 首页 (推荐流)

```tsx
// app/(main)/index.tsx
import { FlashList } from '@shopify/flash-list';
import { useInfiniteQuery } from '@tanstack/react-query';

export default function FeedScreen() {
  const { data, fetchNextPage, hasNextPage } = useInfiniteQuery({
    queryKey: ['feed'],
    queryFn: ({ pageParam = 1 }) => api.posts.getFeed({ page: pageParam }),
    getNextPageParam: (lastPage) => (lastPage.hasMore ? lastPage.page + 1 : undefined),
  });

  const posts = data?.pages.flatMap((page) => page.data) || [];

  return (
    <FlashList
      data={posts}
      renderItem={({ item }) => <PostListItem post={item} />}
      estimatedItemSize={300}
      onEndReached={hasNextPage ? fetchNextPage : undefined}
      onEndReachedThreshold={0.5}
      refreshControl={<RefreshControl onRefresh={refetch} />}
    />
  );
}
```

### 博文详情

```tsx
// app/blog/[id].tsx
import { ScrollView } from 'react-native';
import { useQuery } from '@tanstack/react-query';

export default function PostDetailScreen({ route }) {
  const { id } = route.params;
  const { data: post } = useQuery({
    queryKey: ['post', id],
    queryFn: () => api.posts.get(id),
  });

  return (
    <ScrollView>
      <PostDetail post={post} />
      <CommentList postId={id} />
    </ScrollView>
  );
}
```

### 发布博文

```tsx
// app/blog/new.tsx
import { useState } from 'react';
import { useRouter } from 'expo-router';
import * as ImagePicker from 'expo-image-picker';

export default function NewPostScreen() {
  const router = useRouter();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [images, setImages] = useState<string[]>([]);

  const pickImage = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [16, 9],
    });
    if (!result.canceled) {
      setImages([...images, result.assets[0].uri]);
    }
  };

  const publish = async () => {
    await api.posts.create({ title, content, images });
    router.back();
  };

  return (
    <KeyboardAvoidingView behavior="padding">
      <Input value={title} onChange={setTitle} placeholder="标题" />
      <RichTextEditor value={content} onChange={setContent} />
      <Button onPress={pickImage} icon="image">
        添加图片
      </Button>
      <Button onPress={publish}>发布</Button>
    </KeyboardAvoidingView>
  );
}
```

## 状态管理

### AuthStore

```typescript
// store/authStore.ts
import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  hydrate: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isAuthenticated: false,

  login: async (email, password) => {
    const { user, token } = await api.auth.login(email, password);
    await SecureStore.setItemAsync('token', token);
    set({ user, token, isAuthenticated: true });
  },

  logout: async () => {
    await SecureStore.deleteItemAsync('token');
    set({ user: null, token: null, isAuthenticated: false });
  },

  hydrate: async () => {
    const token = await SecureStore.getItemAsync('token');
    if (token) {
      const user = await api.auth.me();
      set({ user, token, isAuthenticated: true });
    }
  },
}));
```

## 导航配置

### Tab导航

```tsx
// app/(main)/_layout.tsx
import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

export default function TabLayout() {
  return (
    <Tabs screenOptions={{ headerShown: false }}>
      <Tabs.Screen
        name="index"
        options={{
          title: '首页',
          tabBarIcon: ({ color }) => <Ionicons name="home" color={color} />,
        }}
      />
      <Tabs.Screen
        name="search"
        options={{
          title: '搜索',
          tabBarIcon: ({ color }) => <Ionicons name="search" color={color} />,
        }}
      />
      <Tabs.Screen
        name="notifications"
        options={{
          title: '通知',
          tabBarIcon: ({ color }) => <Ionicons name="notifications" color={color} />,
          tabBarBadge: 5,
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: '我的',
          tabBarIcon: ({ color }) => <Ionicons name="person" color={color} />,
        }}
      />
    </Tabs>
  );
}
```

### 根导航

```tsx
// app/_layout.tsx
import { Stack } from 'expo-router';
import { useAuthStore } from '@/store/authStore';

export default function RootLayout() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="(auth)" options={{ gestureEnabled: false }} />
      <Stack.Screen name="(main)" options={{ gestureEnabled: false }} />
      <Stack.Screen name="blog/[id]" options={{ presentation: 'modal' }} />
    </Stack>
  );
}
```

## 性能优化

### 图片优化

```tsx
// 使用expo-image优化图片
import { Image } from 'expo-image';

<Image
  source={{ uri: post.coverImage }}
  style={{ width: '100%', height: 200 }}
  contentFit="cover"
  transition={200}
  placeholder="blurhash"
  placeholderBlurhash={post.blurhash}
/>;
```

### 列表优化

```tsx
// Flash List配置
<FlashList
  data={posts}
  renderItem={({ item }) => <PostListItem post={item} />}
  estimatedItemSize={300}
  getItemType={(item) => item.type} // 不同类型不同高度
  removeClippedSubviews={true}
  windowSize={5} // 渲染窗口大小
  initialNumToRender={10}
  maxToRenderPerBatch={5}
/>
```

### 数据预取

```tsx
// 预取下一篇博文
const prefetchPost = (nextId: string) => {
  queryClient.prefetchQuery({
    queryKey: ['post', nextId],
    queryFn: () => api.posts.get(nextId),
  });
};
```

## 原生功能

### 图片上传

```typescript
import * as ImagePicker from 'expo-image-picker';
import * as FileSystem from 'expo-file-system';

const uploadImage = async (uri: string) => {
  const formData = new FormData();
  formData.append('file', {
    uri,
    type: 'image/jpeg',
    name: 'photo.jpg',
  } as any);

  const response = await api.content.upload(formData);
  return response.url;
};
```

### 推送通知

```typescript
import * as Notifications from 'expo-notifications';

// 配置通知
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  }),
});

// 注册推送
const registerForPushNotifications = async () => {
  const { status } = await Notifications.getPermissionsAsync();
  if (status !== 'granted') {
    const { status } = await Notifications.requestPermissionsAsync();
    if (status !== 'granted') return;
  }

  const token = await Notifications.getExpoPushTokenAsync();
  await api.users.updatePushToken(token.data);
};

// 监听通知
Notifications.addNotificationReceivedListener((notification) => {
  // 处理接收到的通知
});

Notifications.addNotificationResponseReceivedListener((response) => {
  // 处理通知点击
});
```

### 分享

```typescript
import { shareAsync } from 'expo-sharing';

const sharePost = async (post: Post) => {
  await shareAsync(`https://wenxinblog.com/blog/${post.id}`, {
    mimeType: 'text/html',
    dialogTitle: `分享: ${post.title}`,
  });
};
```

## 环境配置

```javascript
// app.config.js
export default {
  expo: {
    extra: {
      apiUrl: process.env.API_URL || 'http://localhost:8080',
      ossUrl: process.env.OSS_URL || 'http://localhost:9000',
    },
  },
};

// 使用
import Constants from 'expo-constants';
const apiUrl = Constants.expoConfig.extra.apiUrl;
```

## 开发

```bash
cd mobile
npm install
npm start
```

按 `i` 打开iOS模拟器

## 构建

### EAS Build (推荐)

```bash
# 安装EAS CLI
npm install -g eas-cli

# 登录Expo
eas login

# 构建iOS
eas build --platform ios

# 本地构建 (需要Mac)
eas build --platform ios --local
```

### TestFlight发布

```bash
eas submit --platform ios --latest
```

### App Store发布

```bash
# 构建生产版本
eas build --platform ios --profile production

# 提交到App Store Connect
eas submit --platform ios --latest
```

## 后续计划

### Android (v2.0)

- 复用iOS代码 (90%+)
- 适配Android特有UI规范
- Material Design集成

### visionOS (v3.0)

- 空间计算适配
- 3D交互设计
- 新的展示形式

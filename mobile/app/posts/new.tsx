import { Alert } from 'react-native';
import { useRouter } from 'expo-router';
import { useCreatePost } from '@/hooks/usePosts';
import { PostEditor } from '@/components/PostEditor';
import { getErrorMessage } from '@/lib/api/client';
import type { CreatePostRequest } from '@/types/post';

export default function NewPostScreen() {
  const router = useRouter();
  const create = useCreatePost();

  const onSubmit = (data: CreatePostRequest) => {
    create.mutate(data, {
      onSuccess: () => {
        Alert.alert(data.status === 'draft' ? '草稿已保存' : '已发布', data.title);
        // 正常入口是 tab 动作按钮 push 进来的，可以 back；兜底直接回首页
        if (router.canGoBack()) {
          router.back();
        } else {
          router.replace('/(tabs)');
        }
      },
    });
  };

  return (
    <PostEditor
      submitting={create.isPending}
      error={create.isError ? getErrorMessage(create.error, '发布失败，请稍后重试') : null}
      onSubmit={onSubmit}
    />
  );
}

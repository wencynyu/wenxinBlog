import { Alert } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { usePost, useUpdatePost } from '@/hooks/usePosts';
import { PostEditor } from '@/components/PostEditor';
import { Spinner } from '@/components/Spinner';
import { ErrorState } from '@/components/ErrorState';
import { getErrorMessage } from '@/lib/api/client';
import type { CreatePostRequest } from '@/types/post';

export default function EditPostScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const postQuery = usePost(id);
  const update = useUpdatePost();

  if (postQuery.isLoading) {
    return <Spinner label="正在加载博文…" />;
  }

  if (postQuery.isError || !postQuery.data) {
    return (
      <ErrorState message={getErrorMessage(postQuery.error)} onRetry={() => postQuery.refetch()} />
    );
  }

  const post = postQuery.data;

  const onSubmit = (data: CreatePostRequest) => {
    update.mutate(
      { id: post.id, data },
      {
        onSuccess: () => {
          Alert.alert('已保存', post.title);
          if (router.canGoBack()) {
            router.back();
          } else {
            router.replace(`/posts/${post.id}`);
          }
        },
      },
    );
  };

  return (
    <PostEditor
      initial={{
        title: post.title,
        content: post.content,
        summary: post.summary,
        tags: post.tags,
      }}
      submitting={update.isPending}
      error={update.isError ? getErrorMessage(update.error, '保存失败，请稍后重试') : null}
      onSubmit={onSubmit}
    />
  );
}

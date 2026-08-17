import { useState } from 'react';
import { Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { PrimaryButton } from '@/components/PrimaryButton';
import type { CreatePostRequest } from '@/types/post';

interface PostEditorProps {
  initial?: {
    title: string;
    content: string;
    summary?: string;
    tags?: string[];
  };
  submitting: boolean;
  error?: string | null;
  onSubmit: (data: CreatePostRequest) => void;
}

/**
 * 发博文/编辑共用的 Markdown 编辑器（纯文本编辑 + 发布/存草稿）。
 * 图片上传（content-service）后续版本接入，当前封面/图片请在正文用 URL。
 */
export function PostEditor({ initial, submitting, error, onSubmit }: PostEditorProps) {
  const [title, setTitle] = useState(initial?.title ?? '');
  const [summary, setSummary] = useState(initial?.summary ?? '');
  const [tagsInput, setTagsInput] = useState(initial?.tags?.join(', ') ?? '');
  const [content, setContent] = useState(initial?.content ?? '');
  const [validation, setValidation] = useState<string | null>(null);

  const buildRequest = (status: 'draft' | 'published'): CreatePostRequest => ({
    title: title.trim(),
    content: content.trim(),
    summary: summary.trim() || undefined,
    tags: tagsInput
      .split(/[,，\s]+/)
      .map((tag) => tag.trim())
      .filter(Boolean),
    status,
  });

  const submit = (status: 'draft' | 'published') => {
    if (!title.trim() || !content.trim()) {
      setValidation('标题和正文都不能为空');
      return;
    }
    setValidation(null);
    onSubmit(buildRequest(status));
  };

  return (
    <ScrollView
      className="flex-1 bg-canvas"
      contentContainerClassName="gap-4 px-4 pb-8 pt-4"
      keyboardShouldPersistTaps="handled"
      keyboardDismissMode="on-drag"
    >
      <View className="gap-1.5">
        <Text className="text-[13px] font-medium text-ink-muted">标题</Text>
        <TextInput
          value={title}
          onChangeText={setTitle}
          placeholder="一句话说清这篇博文"
          placeholderTextColor="#8a919c"
          className="rounded-xl border border-hairline bg-surface px-3.5 py-3 text-base text-ink"
          accessibilityLabel="博文标题"
        />
      </View>

      <View className="gap-1.5">
        <Text className="text-[13px] font-medium text-ink-muted">摘要（可选）</Text>
        <TextInput
          value={summary}
          onChangeText={setSummary}
          placeholder="列表页展示的摘要，留空自动截取正文"
          placeholderTextColor="#8a919c"
          multiline
          className="rounded-xl border border-hairline bg-surface px-3.5 py-3 text-sm text-ink"
          accessibilityLabel="博文摘要"
        />
      </View>

      <View className="gap-1.5">
        <Text className="text-[13px] font-medium text-ink-muted">标签（逗号分隔）</Text>
        <TextInput
          value={tagsInput}
          onChangeText={setTagsInput}
          placeholder="例如：云原生, K8s"
          placeholderTextColor="#8a919c"
          className="rounded-xl border border-hairline bg-surface px-3.5 py-3 text-sm text-ink"
          accessibilityLabel="博文标签"
        />
      </View>

      <View className="gap-1.5">
        <View className="flex-row items-center gap-1.5">
          <Text className="text-[13px] font-medium text-ink-muted">正文（Markdown）</Text>
          <View className="flex-1" />
          <Text className="text-xs text-ink-faint">{content.length} 字</Text>
        </View>
        <TextInput
          value={content}
          onChangeText={setContent}
          placeholder={'# 标题\n\n支持 Markdown 语法'}
          placeholderTextColor="#8a919c"
          multiline
          textAlignVertical="top"
          className="min-h-[280px] rounded-xl border border-hairline bg-surface px-3.5 py-3 text-[15px] leading-6 text-ink"
          style={{ fontFamily: undefined }}
          accessibilityLabel="博文正文"
        />
      </View>

      {validation ? (
        <Text className="text-sm" style={{ color: '#ef4444' }}>
          {validation}
        </Text>
      ) : null}
      {error ? (
        <Text className="text-sm" style={{ color: '#ef4444' }}>
          {error}
        </Text>
      ) : null}

      <View className="flex-row gap-3">
        <Pressable
          onPress={() => submit('draft')}
          disabled={submitting}
          className="flex-1 items-center justify-center rounded-xl border border-hairline bg-surface py-3.5 active:opacity-70 disabled:opacity-50"
          accessibilityRole="button"
          accessibilityLabel="存草稿"
        >
          <View className="flex-row items-center gap-1.5">
            <Ionicons name="document-outline" size={16} color="#5c6470" />
            <Text className="text-base font-medium text-ink-muted">存草稿</Text>
          </View>
        </Pressable>
        <View className="flex-[2]">
          <PrimaryButton label="发布" onPress={() => submit('published')} loading={submitting} />
        </View>
      </View>
    </ScrollView>
  );
}

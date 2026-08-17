import { Text, View } from 'react-native';

/**
 * 品牌标识：琥珀"文"字印章 + "文心"汉字字标。
 * 印章取中文博客的"落款"意象；琥珀色与 web 端 accent 色板一致。
 */
export function BrandMark({ compact = false }: { compact?: boolean }) {
  const seal = compact ? 26 : 36;
  const wordSize = compact ? 18 : 32;

  return (
    <View className="flex-row items-center gap-2.5">
      <View
        className="items-center justify-center rounded-[8px] bg-accent"
        style={{ width: seal, height: seal }}
        accessibilityLabel="文心印章图标"
      >
        <Text className="font-bold leading-none text-white" style={{ fontSize: seal * 0.58 }}>
          文
        </Text>
      </View>
      <Text
        className="font-bold leading-none tracking-[0.08em] text-ink"
        style={{ fontSize: wordSize }}
      >
        文心
      </Text>
    </View>
  );
}

/** 登录/注册页背景的巨型"文"字水印，呼应写作题材。 */
export function GlyphWatermark() {
  return (
    <Text
      className="absolute -top-10 -right-8 text-[280px] leading-none font-bold text-ink opacity-[0.04] dark:text-white dark:opacity-[0.06]"
      pointerEvents="none"
    >
      文
    </Text>
  );
}

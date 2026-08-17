import { View } from 'react-native';

/**
 * 占位路由：真正的入口是 tab 栏上的 CreateTabButton（直接 push /posts/new），
 * 正常情况下不会停留在此页。
 */
export default function CreateScreen() {
  return <View className="flex-1 bg-canvas" />;
}

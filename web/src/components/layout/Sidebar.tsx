// recommendation-service 和 search-service 未运行时，Sidebar 无数据源。
// 暂停渲染，避免 500 报错。服务恢复后取消注释即可。

export default function Sidebar() {
  return null;
}

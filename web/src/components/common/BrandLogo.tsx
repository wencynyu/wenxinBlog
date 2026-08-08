import Link from 'next/link';

/** 统一品牌 Logo：Semi 蓝圆角块 + "W" + 站名。替换 Header/Footer/Auth 三处手搓 div。 */
export default function BrandLogo({
  size = 'sm',
  showText = true,
}: {
  size?: 'sm' | 'md';
  showText?: boolean;
}) {
  const box = size === 'md' ? 'h-10 w-10 text-xl' : 'h-8 w-8 text-lg';
  return (
    <Link href="/" className="flex items-center space-x-2" aria-label="WenxinBlog 首页">
      <div className={`${box} rounded-lg bg-primary-600 flex items-center justify-center`}>
        <span className="text-white font-bold leading-none">W</span>
      </div>
      {showText && <span className="font-bold text-ink text-lg">WenxinBlog</span>}
    </Link>
  );
}

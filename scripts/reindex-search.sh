#!/bin/bash
# 重建 search-service 的 wenxinblog-blog 索引（从 blog_db 拉博文 index 到 Elasticsearch）
# 用法：./scripts/reindex-search.sh
# 场景：ES 重建 / 新环境部署后，wenxinblog-blog 索引为空时跑一次
set -e

ES="${ES_URL:-http://localhost:9200}"
PSQL="docker exec wenxinblog-postgres-blog psql -U postgres -d blog_db -t -A"

echo "[1/3] 从 blog_db 导出 published 博文..."
$PSQL -c "
SELECT json_build_object(
  'id', p.id, 'title', p.title, 'content', COALESCE(p.content,''), 'summary', COALESCE(p.summary,''),
  'author_id', p.author_id, 'author_name', COALESCE(a.display_name, a.username),
  'view_count', p.view_count, 'like_count', p.like_count, 'comment_count', p.comment_count,
  'published_at', to_char(p.published_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'),
  'created_at', to_char(p.created_at, 'YYYY-MM-DD\"T\"HH24:MI:SS'),
  'status', p.status
) FROM posts p LEFT JOIN authors a ON p.author_id = a.id WHERE p.status = 'published'
" > /tmp/posts.json

echo "[2/3] 构造 ES bulk ndjson..."
python3 -c "
import json
lines=[l.strip() for l in open('/tmp/posts.json') if l.strip()]
with open('/tmp/bulk.ndjson','w') as out:
    for l in lines:
        doc=json.loads(l)
        out.write(json.dumps({'index':{'_index':'wenxinblog-blog','_id':doc['id']}})+'\n')
        out.write(json.dumps(doc)+'\n')
print(f'  {len(lines)} docs')
"

echo "[3/3] ES bulk index..."
curl -s -X POST "$ES/_bulk" -H 'Content-Type: application/x-ndjson' --data-binary @/tmp/bulk.ndjson \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('  indexed:', len(d.get('items',[])), '| errors:', d.get('errors'))"

echo "done: $(curl -s $ES/wenxinblog-blog/_count | python3 -c 'import sys,json;print(json.load(sys.stdin)["count"])') docs in wenxinblog-blog"

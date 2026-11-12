#!/usr/bin/env bash
# Run from the project root with `docker compose up -d` and `./gradlew run` already going.
set -euo pipefail
API=${API:-http://localhost:8080}
BOOK=OL45883W

echo "== seed one book"
docker compose exec -T db psql -U estante -c \
  "INSERT INTO books (work_id, title, author, cover_id) VALUES ('$BOOK','O Farol de Papel','Helena Marques',1) ON CONFLICT DO NOTHING;"

echo "== no token -> 401"
curl -s -o /dev/null -w "%{http_code}\n" -X PUT "$API/v1/books/$BOOK/rating" \
  -H 'Content-Type: application/json' -d '{"score":5,"clientUpdatedAt":1}'

echo "== three users rate the book (the ranking needs 3 votes)"
for score in 5 4 5; do
  token=$(curl -s -X POST "$API/v1/users" | sed -E 's/.*"token":"([^"]+)".*/\1/')
  curl -s -o /dev/null -w "rate $score -> %{http_code}\n" -X PUT "$API/v1/books/$BOOK/rating" \
    -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"score\":$score,\"clientUpdatedAt\":$(date +%s)000}"
done

echo "== bad inputs -> 400, unknown book -> 404"
curl -s -w " %{http_code}\n" -X PUT "$API/v1/books/OL1W/rating" -H "Authorization: Bearer $token" \
  -H 'Content-Type: application/json' -d '{"score":9,"clientUpdatedAt":1}'
curl -s -w " %{http_code}\n" -X PUT "$API/v1/books/OL999999W/rating" -H "Authorization: Bearer $token" \
  -H 'Content-Type: application/json' -d '{"score":5,"clientUpdatedAt":1}'

echo "== recompute ranking (m = 10)"
docker compose exec -T db psql -U estante -v m=10 < db/recompute.sql

echo "== ranking (check the Cache-Control header)"
curl -si "$API/v1/ranking?limit=5" | grep -iE '^(HTTP|cache-control)|items'

WITH global AS (
  SELECT COALESCE(AVG(score), 3.5) AS c FROM ratings
),
reader AS (
  SELECT work_id, COUNT(*) AS v, AVG(score) AS r
  FROM ratings
  GROUP BY work_id
),
critic AS (
  SELECT work_id, COUNT(score) AS cc, AVG(score) AS ca
  FROM critic_reviews
  GROUP BY work_id
)
INSERT INTO book_stats AS s
  (work_id, rating_count, rating_avg, critic_count, critic_avg, bayes_score, updated_at)
SELECT
  b.work_id,
  COALESCE(reader.v, 0),
  ROUND(reader.r, 2),
  COALESCE(critic.cc, 0),
  ROUND(critic.ca, 2),
  CASE
    WHEN reader.v IS NULL THEN NULL
    ELSE ROUND(
      (reader.v::numeric / (reader.v + :m)) * reader.r
      + (:m::numeric / (reader.v + :m)) * COALESCE(critic.ca, global.c),
      3)
  END,
  now()
FROM books b
CROSS JOIN global
LEFT JOIN reader ON reader.work_id = b.work_id
LEFT JOIN critic ON critic.work_id = b.work_id
ON CONFLICT (work_id) DO UPDATE
  SET rating_count = EXCLUDED.rating_count,
      rating_avg   = EXCLUDED.rating_avg,
      critic_count = EXCLUDED.critic_count,
      critic_avg   = EXCLUDED.critic_avg,
      bayes_score  = EXCLUDED.bayes_score,
      updated_at   = EXCLUDED.updated_at;

CREATE TABLE users (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  token_hash  text NOT NULL,              -- hash of the device token, never the token
  google_sub  text UNIQUE,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE books (
  work_id            text PRIMARY KEY,    -- Open Library work ID
  title              text NOT NULL,
  author             text,
  cover_id           integer,             -- cover URL built on the client
  first_publish_year smallint,
  updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ratings (
  user_id           uuid     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  work_id           text     NOT NULL REFERENCES books(work_id),
  score             smallint NOT NULL CHECK (score BETWEEN 1 AND 5),
  client_updated_at timestamptz NOT NULL, -- used for last-write-wins
  updated_at        timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, work_id)          -- one rating per user per book
);
CREATE INDEX ratings_work_idx ON ratings (work_id);

CREATE TABLE critic_reviews (
  id          bigserial PRIMARY KEY,
  work_id     text NOT NULL REFERENCES books(work_id),
  excerpt     text NOT NULL,              -- short excerpt only
  author      text,
  outlet      text NOT NULL,
  url         text NOT NULL,              -- link to the original
  score       numeric(2,1) CHECK (score BETWEEN 0 AND 5),
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX critic_reviews_work_idx ON critic_reviews (work_id);

CREATE TABLE book_stats (
  work_id       text PRIMARY KEY REFERENCES books(work_id),
  rating_count  integer NOT NULL DEFAULT 0,
  rating_avg    numeric(3,2),
  critic_count  integer NOT NULL DEFAULT 0,
  critic_avg    numeric(3,2),
  bayes_score   numeric(4,3),             -- the value the ranking sorts by
  updated_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX book_stats_rank_idx ON book_stats (bayes_score DESC, work_id);

CREATE TABLE search_cache (
  query_key   text PRIMARY KEY,           -- normalized query + page
  response    jsonb NOT NULL,
  fetched_at  timestamptz NOT NULL DEFAULT now()
);


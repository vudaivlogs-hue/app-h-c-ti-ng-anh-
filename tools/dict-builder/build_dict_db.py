#!/usr/bin/env python3
"""Builds the pre-packaged SQLite database (engdict.db) for Enzii Dict.

The DDL here intentionally mirrors Room's generated schema in EnziiDatabase
(without SQLite DEFAULT clauses so Room's createFromAsset validation succeeds cleanly).
"""

from __future__ import annotations

import json
import sqlite3
import time
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parents[2]
SCRIPT_DIR = Path(__file__).resolve().parent
OUTPUT_DB = ROOT_DIR / "app" / "src" / "main" / "assets" / "databases" / "engdict.db"
SEED_DICT_PATH = SCRIPT_DIR / "seed_dictionary.json"
SEED_ARTICLES_PATH = SCRIPT_DIR / "seed_articles.json"

SCHEMA_SQL = """
CREATE TABLE dictionary_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    word TEXT NOT NULL,
    word_normalized TEXT NOT NULL COLLATE NOCASE,
    ipa_uk TEXT NOT NULL,
    ipa_us TEXT NOT NULL,
    pos TEXT NOT NULL,
    meaning_vi TEXT NOT NULL,
    examples_json TEXT NOT NULL,
    synonyms_json TEXT NOT NULL,
    collocations_json TEXT NOT NULL,
    cefr_level TEXT,
    frequency_rank INTEGER NOT NULL
);

CREATE INDEX idx_dict_word_prefix
ON dictionary_entries (word_normalized, frequency_rank);

CREATE INDEX idx_dict_exact_word_pos
ON dictionary_entries (word_normalized, pos);

CREATE INDEX idx_dict_cefr_pos
ON dictionary_entries (cefr_level, pos, frequency_rank);

CREATE VIRTUAL TABLE dictionary_entries_fts USING FTS4(
    word TEXT NOT NULL,
    meaning_vi TEXT NOT NULL,
    collocations_json TEXT NOT NULL,
    synonyms_json TEXT NOT NULL,
    tokenize=unicode61 `remove_diacritics=1`,
    content=`dictionary_entries`
);

CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_entries_fts_BEFORE_UPDATE
BEFORE UPDATE ON `dictionary_entries` BEGIN
    DELETE FROM `dictionary_entries_fts` WHERE `docid`=OLD.`rowid`;
END;

CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_entries_fts_BEFORE_DELETE
BEFORE DELETE ON `dictionary_entries` BEGIN
    DELETE FROM `dictionary_entries_fts` WHERE `docid`=OLD.`rowid`;
END;

CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_entries_fts_AFTER_UPDATE
AFTER UPDATE ON `dictionary_entries` BEGIN
    INSERT INTO `dictionary_entries_fts`(`docid`, `word`, `meaning_vi`, `collocations_json`, `synonyms_json`)
    VALUES (NEW.`rowid`, NEW.`word`, NEW.`meaning_vi`, NEW.`collocations_json`, NEW.`synonyms_json`);
END;

CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_entries_fts_AFTER_INSERT
AFTER INSERT ON `dictionary_entries` BEGIN
    INSERT INTO `dictionary_entries_fts`(`docid`, `word`, `meaning_vi`, `collocations_json`, `synonyms_json`)
    VALUES (NEW.`rowid`, NEW.`word`, NEW.`meaning_vi`, NEW.`collocations_json`, NEW.`synonyms_json`);
END;

CREATE TABLE wordbooks (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    color_hex TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE srs_cards (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    entry_id INTEGER NOT NULL,
    wordbook_id INTEGER NOT NULL,
    custom_note TEXT NOT NULL,
    easiness_factor REAL NOT NULL,
    interval_days INTEGER NOT NULL,
    repetitions INTEGER NOT NULL,
    lapses INTEGER NOT NULL,
    state TEXT NOT NULL,
    due_at INTEGER NOT NULL,
    last_reviewed_at INTEGER,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(entry_id) REFERENCES dictionary_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY(wordbook_id) REFERENCES wordbooks(id) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_srs_unique_word_in_book
ON srs_cards (wordbook_id, entry_id);

CREATE INDEX idx_srs_due_queue
ON srs_cards (wordbook_id, due_at);

CREATE INDEX idx_srs_entry_id
ON srs_cards (entry_id);

CREATE TABLE srs_review_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    card_id INTEGER NOT NULL,
    quality INTEGER NOT NULL,
    reviewed_at INTEGER NOT NULL,
    time_taken_ms INTEGER NOT NULL,
    prev_interval INTEGER NOT NULL,
    new_interval INTEGER NOT NULL,
    FOREIGN KEY(card_id) REFERENCES srs_cards(id) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX idx_review_logs_time
ON srs_review_logs (reviewed_at);

CREATE INDEX idx_review_logs_card_id
ON srs_review_logs (card_id);

CREATE TABLE search_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    query TEXT NOT NULL,
    entry_id INTEGER,
    searched_at INTEGER NOT NULL
);

CREATE INDEX idx_search_history_time
ON search_history (searched_at);

CREATE TABLE translation_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    source_text TEXT NOT NULL,
    target_text TEXT NOT NULL,
    source_lang TEXT NOT NULL,
    target_lang TEXT NOT NULL,
    grammar_analysis_json TEXT,
    is_favorite INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_translation_created
ON translation_history (created_at);

CREATE TABLE bilingual_articles (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title_en TEXT NOT NULL,
    title_vi TEXT NOT NULL,
    cefr_level TEXT NOT NULL,
    category TEXT NOT NULL,
    paragraphs_json TEXT NOT NULL,
    read_progress REAL NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX idx_articles_level
ON bilingual_articles (cefr_level, updated_at);

CREATE TABLE speaking_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    topic TEXT NOT NULL,
    voice_name TEXT NOT NULL,
    mode TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    ended_at INTEGER,
    duration_sec INTEGER NOT NULL,
    analysis_json TEXT
);

CREATE INDEX idx_speaking_sessions_time
ON speaking_sessions (started_at);

CREATE TABLE speaking_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    session_id INTEGER NOT NULL,
    speaker TEXT NOT NULL,
    transcript TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(session_id) REFERENCES speaking_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX idx_speaking_messages_session
ON speaking_messages (session_id, created_at);
"""


def build_database() -> None:
    OUTPUT_DB.parent.mkdir(parents=True, exist_ok=True)
    if OUTPUT_DB.exists():
        OUTPUT_DB.unlink()

    entries = json.loads(SEED_DICT_PATH.read_text(encoding="utf-8"))
    articles = json.loads(SEED_ARTICLES_PATH.read_text(encoding="utf-8"))
    now_ms = int(time.time() * 1000)

    conn = sqlite3.connect(str(OUTPUT_DB))
    try:
        conn.executescript(SCHEMA_SQL)

        for item in entries:
            word = item["word"].strip()
            conn.execute(
                """
                INSERT INTO dictionary_entries (
                    word, word_normalized, ipa_uk, ipa_us, pos, meaning_vi,
                    examples_json, synonyms_json, collocations_json, cefr_level, frequency_rank
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    word,
                    word.lower(),
                    item.get("ipa_uk", ""),
                    item.get("ipa_us", ""),
                    item["pos"],
                    item["meaning_vi"],
                    json.dumps(item.get("examples", []), ensure_ascii=False),
                    json.dumps(item.get("synonyms", []), ensure_ascii=False),
                    json.dumps(item.get("collocations", []), ensure_ascii=False),
                    item.get("cefr_level"),
                    int(item.get("frequency_rank", 999999)),
                ),
            )

        # Rebuild FTS4 external content index from dictionary_entries
        conn.execute("INSERT INTO dictionary_entries_fts(dictionary_entries_fts) VALUES('rebuild')")

        # Seed default wordbook + 3 starter SRS cards so user can test review immediately
        conn.execute(
            """
            INSERT INTO wordbooks (name, description, color_hex, created_at)
            VALUES (?, ?, ?, ?)
            """,
            ("Sổ tay mặc định", "Từ vựng cốt lõi đã lưu khi tra cứu", "#0D7377", now_ms),
        )
        starter_words = ["ubiquitous", "resilient", "decision"]
        for w in starter_words:
            row = conn.execute(
                "SELECT id FROM dictionary_entries WHERE word_normalized = ? LIMIT 1", (w,)
            ).fetchone()
            if row:
                conn.execute(
                    """
                    INSERT INTO srs_cards (
                        entry_id, wordbook_id, custom_note, easiness_factor, interval_days,
                        repetitions, lapses, state, due_at, last_reviewed_at, created_at
                    ) VALUES (?, 1, '', 2.5, 0, 0, 0, 'NEW', ?, NULL, ?)
                    """,
                    (row[0], now_ms, now_ms),
                )

        for article in articles:
            conn.execute(
                """
                INSERT INTO bilingual_articles (
                    title_en, title_vi, cefr_level, category, paragraphs_json, read_progress, updated_at
                ) VALUES (?, ?, ?, ?, ?, 0.0, ?)
                """,
                (
                    article["title_en"],
                    article["title_vi"],
                    article["cefr_level"],
                    article["category"],
                    json.dumps(article["paragraphs"], ensure_ascii=False),
                    now_ms,
                ),
            )

        conn.commit()
        conn.execute("ANALYZE")
        conn.execute("VACUUM")
        print(f"Built {OUTPUT_DB} with {len(entries)} entries and {len(articles)} articles.")
    finally:
        conn.close()


if __name__ == "__main__":
    build_database()

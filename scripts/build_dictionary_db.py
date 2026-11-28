#!/usr/bin/env python3
"""Build pre-packaged SQLite database (app/src/main/assets/dictionary.db) for Enzii Dict.

Supports:
  1. FVDP text/dict format (--fvdp path/to/anhviet109K.dict)
  2. Wiktionary JSON/JSONL format (--wiktionary path/to/kaikki.jsonl)
  3. Built-in ~500 common English words + curated rich entries (default / fallback)

Usage:
  python scripts/build_dictionary_db.py
  python scripts/build_dictionary_db.py --fvdp data/anhviet.dict --wiktionary data/en.jsonl
"""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
import time
from pathlib import Path
from typing import Any

ROOT_DIR = Path(__file__).resolve().parents[1]
OUTPUT_DB = ROOT_DIR / "app" / "src" / "main" / "assets" / "dictionary.db"
SEED_DICT_PATH = ROOT_DIR / "tools" / "dict-builder" / "seed_dictionary.json"
SEED_ARTICLES_PATH = ROOT_DIR / "tools" / "dict-builder" / "seed_articles.json"
DEFAULT_FVDP_PATH = ROOT_DIR / "tools" / "dict-builder" / "anhviet109K.txt"

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

CREATE VIRTUAL TABLE dictionary_fts USING FTS4(
    word TEXT NOT NULL,
    meaning_vi TEXT NOT NULL,
    collocations_json TEXT NOT NULL,
    synonyms_json TEXT NOT NULL,
    tokenize=unicode61 `remove_diacritics=1`,
    content=`dictionary_entries`
);

CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_fts_BEFORE_UPDATE
BEFORE UPDATE ON `dictionary_entries` BEGIN
    DELETE FROM `dictionary_fts` WHERE `docid`=OLD.`rowid`;
END;

CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_fts_BEFORE_DELETE
BEFORE DELETE ON `dictionary_entries` BEGIN
    DELETE FROM `dictionary_fts` WHERE `docid`=OLD.`rowid`;
END;

CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_fts_AFTER_UPDATE
AFTER UPDATE ON `dictionary_entries` BEGIN
    INSERT INTO `dictionary_fts`(`docid`, `word`, `meaning_vi`, `collocations_json`, `synonyms_json`)
    VALUES (NEW.`rowid`, NEW.`word`, NEW.`meaning_vi`, NEW.`collocations_json`, NEW.`synonyms_json`);
END;

CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_fts_AFTER_INSERT
AFTER INSERT ON `dictionary_entries` BEGIN
    INSERT INTO `dictionary_fts`(`docid`, `word`, `meaning_vi`, `collocations_json`, `synonyms_json`)
    VALUES (NEW.`rowid`, NEW.`word`, NEW.`meaning_vi`, NEW.`collocations_json`, NEW.`synonyms_json`);
END;

CREATE TABLE word_folders (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    icon_name TEXT NOT NULL,
    color_hex TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX idx_word_folders_name_unique
ON word_folders (name);

CREATE TABLE saved_words (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    entry_id INTEGER NOT NULL,
    folder_id INTEGER NOT NULL,
    folder_name TEXT NOT NULL,
    ease_factor REAL NOT NULL,
    interval_days INTEGER NOT NULL,
    repetitions INTEGER NOT NULL,
    lapses INTEGER NOT NULL,
    state TEXT NOT NULL,
    next_review_at INTEGER NOT NULL,
    last_reviewed_at INTEGER,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(entry_id) REFERENCES dictionary_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY(folder_id) REFERENCES word_folders(id) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_saved_words_unique_entry_folder_id
ON saved_words (folder_id, entry_id);

CREATE INDEX idx_saved_words_due_queue
ON saved_words (folder_id, next_review_at);

CREATE INDEX idx_saved_words_entry_id
ON saved_words (entry_id);

CREATE TABLE speaking_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    topic TEXT NOT NULL,
    voice_name TEXT NOT NULL,
    mode TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    ended_at INTEGER,
    duration_sec INTEGER NOT NULL,
    correction_suggestions_json TEXT
);

CREATE INDEX idx_speaking_sessions_time
ON speaking_sessions (started_at);

CREATE TABLE speaking_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    session_id INTEGER NOT NULL,
    speaker TEXT NOT NULL,
    transcript TEXT NOT NULL,
    suggestion_note TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY(session_id) REFERENCES speaking_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX idx_speaking_messages_session
ON speaking_messages (session_id, created_at);

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

CREATE TABLE daily_study_logs (
    date_yyyy_mm_dd TEXT NOT NULL,
    words_reviewed INTEGER NOT NULL,
    words_learned INTEGER NOT NULL,
    speaking_minutes INTEGER NOT NULL,
    shadowing_count INTEGER NOT NULL,
    xp_earned INTEGER NOT NULL,
    goal_completed INTEGER NOT NULL,
    PRIMARY KEY(date_yyyy_mm_dd)
);

CREATE TABLE user_streak_state (
    id INTEGER NOT NULL,
    current_streak INTEGER NOT NULL,
    longest_streak INTEGER NOT NULL,
    freeze_count INTEGER NOT NULL,
    last_active_date TEXT,
    daily_xp_goal INTEGER NOT NULL,
    PRIMARY KEY(id)
);
"""

# ~460+ common English words (paired with 40 curated entries -> ~500 total entries)
COMMON_WORDS: list[tuple[str, str, str, str, str]] = [
    ("ability", "/əˈbɪl.ə.ti/", "noun", "khả năng, năng lực", "A2"),
    ("able", "/ˈeɪ.bəl/", "adjective", "có thể, có năng lực", "A2"),
    ("about", "/əˈbaʊt/", "preposition", "về, khoảng chừng", "A1"),
    ("above", "/əˈbʌv/", "preposition", "ở trên, phía trên", "A1"),
    ("accept", "/əkˈsept/", "verb", "chấp nhận, đồng ý", "A2"),
    ("accident", "/ˈæk.sɪ.dənt/", "noun", "tai nạn, sự cố", "A2"),
    ("account", "/əˈkaʊnt/", "noun", "tài khoản; bản tường trình", "B1"),
    ("accurate", "/ˈæk.jə.rət/", "adjective", "chính xác, đúng đắn", "B1"),
    ("achieve", "/əˈtʃiːv/", "verb", "đạt được, hoàn thành", "B1"),
    ("across", "/əˈkrɒs/", "preposition", "băng qua, ngang qua", "A1"),
    ("action", "/ˈæk.ʃən/", "noun", "hành động, hoạt động", "A2"),
    ("active", "/ˈæk.tɪv/", "adjective", "tích cực, năng động", "A2"),
    ("activity", "/ækˈtɪv.ə.ti/", "noun", "hoạt động", "A1"),
    ("actual", "/ˈæk.tʃu.əl/", "adjective", "thực tế, có thật", "B1"),
    ("adapt", "/əˈdæpt/", "verb", "thích nghi, điều chỉnh", "B2"),
    ("address", "/əˈdres/", "noun", "địa chỉ; bài diễn văn", "A1"),
    ("adjust", "/əˈdʒʌst/", "verb", "điều chỉnh, thích nghi", "B2"),
    ("admire", "/ədˈmaɪər/", "verb", "ngưỡng mộ, khâm phục", "B1"),
    ("admit", "/ədˈmɪt/", "verb", "thừa nhận, cho phép vào", "B1"),
    ("adult", "/ˈæd.ʌlt/", "noun", "người lớn, người trưởng thành", "A1"),
    ("advance", "/ədˈvɑːns/", "verb", "tiến bộ, tiến lên", "B2"),
    ("advantage", "/ədˈvɑːn.tɪdʒ/", "noun", "lợi thế, ưu điểm", "B1"),
    ("adventure", "/ədˈven.tʃər/", "noun", "cuộc phiêu lưu", "A2"),
    ("advice", "/ədˈvaɪs/", "noun", "lời khuyên", "A2"),
    ("advise", "/ədˈvaɪz/", "verb", "khuyên bảo, tư vấn", "B1"),
    ("afford", "/əˈfɔːd/", "verb", "đủ khả năng chi trả", "B1"),
    ("afraid", "/əˈfreɪd/", "adjective", "sợ hãi, e ngại", "A1"),
    ("after", "/ˈɑːf.tər/", "preposition", "sau, sau khi", "A1"),
    ("afternoon", "/ˌɑːf.təˈnuːn/", "noun", "buổi chiều", "A1"),
    ("again", "/əˈɡen/", "adverb", "lại, một lần nữa", "A1"),
    ("against", "/əˈɡenst/", "preposition", "chống lại, phản đối", "A2"),
    ("agency", "/ˈeɪ.dʒən.si/", "noun", "cơ quan, đại lý", "B2"),
    ("agree", "/əˈɡriː/", "verb", "đồng ý, tán thành", "A1"),
    ("ahead", "/əˈhed/", "adverb", "phía trước, trước", "B1"),
    ("airport", "/ˈeə.pɔːt/", "noun", "sân bay", "A1"),
    ("alarm", "/əˈlɑːm/", "noun", "báo thức, chuông báo động", "B1"),
    ("allow", "/əˈlaʊ/", "verb", "cho phép", "A2"),
    ("almost", "/ˈɔːl.məʊst/", "adverb", "hầu như, suýt nữa", "A2"),
    ("alone", "/əˈləʊn/", "adjective", "một mình, cô đơn", "A2"),
    ("already", "/ɔːlˈred.i/", "adverb", "đã, rồi", "A2"),
    ("always", "/ˈɔːl.weɪz/", "adverb", "luôn luôn", "A1"),
    ("amazing", "/əˈmeɪ.zɪŋ/", "adjective", "tuyệt vời, kinh ngạc", "A2"),
    ("ambition", "/æmˈbɪʃ.ən/", "noun", "hoài bão, tham vọng", "B1"),
    ("amount", "/əˈmaʊnt/", "noun", "số lượng, lượng", "B1"),
    ("analyze", "/ˈæn.ə.laɪz/", "verb", "phân tích", "B2"),
    ("ancient", "/ˈeɪn.ʃənt/", "adjective", "cổ đại, xưa cũ", "B1"),
    ("anger", "/ˈæŋ.ɡər/", "noun", "sự tức giận", "B1"),
    ("animal", "/ˈæn.ɪ.məl/", "noun", "động vật", "A1"),
    ("announce", "/əˈnaʊns/", "verb", "thông báo, công bố", "B1"),
    ("annual", "/ˈæn.ju.əl/", "adjective", "hằng năm", "B2"),
    ("another", "/əˈnʌð.ər/", "determiner", "khác, một cái khác", "A1"),
    ("answer", "/ˈɑːn.sər/", "noun", "câu trả lời", "A1"),
    ("anxious", "/ˈæŋk.ʃəs/", "adjective", "lo âu, bồn chồn", "B2"),
    ("anyone", "/ˈen.i.wʌn/", "pronoun", "bất kỳ ai", "A1"),
    ("anything", "/ˈen.i.θɪŋ/", "pronoun", "bất cứ thứ gì", "A1"),
    ("apart", "/əˈpɑːt/", "adverb", "tách rời, cách nhau", "B1"),
    ("apologize", "/əˈpɒl.ə.dʒaɪz/", "verb", "xin lỗi", "B1"),
    ("appear", "/əˈpɪər/", "verb", "xuất hiện, dường như", "A2"),
    ("apply", "/əˈplaɪ/", "verb", "ứng tuyển; áp dụng", "B1"),
    ("appoint", "/əˈpɔɪnt/", "verb", "bổ nhiệm, chỉ định", "B2"),
    ("appreciate", "/əˈpriː.ʃi.eɪt/", "verb", "trân trọng, đánh giá cao", "B1"),
    ("appropriate", "/əˈprəʊ.pri.ət/", "adjective", "phù hợp, thích đáng", "B2"),
    ("approve", "/əˈpruːv/", "verb", "phê duyệt, tán thành", "B2"),
    ("area", "/ˈeə.ri.ə/", "noun", "khu vực, diện tích", "A2"),
    ("argue", "/ˈɑːɡ.juː/", "verb", "tranh luận, lập luận", "A2"),
    ("argument", "/ˈɑːɡ.jə.mənt/", "noun", "cuộc tranh luận, lý lẽ", "B1"),
    ("arrange", "/əˈreɪndʒ/", "verb", "sắp xếp, thu xếp", "A2"),
    ("arrive", "/əˈraɪv/", "verb", "đến nơi", "A1"),
    ("article", "/ˈɑː.tɪ.kəl/", "noun", "bài báo; mạo từ", "A2"),
    ("artist", "/ˈɑː.tɪst/", "noun", "nghệ sĩ, họa sĩ", "A1"),
    ("aspect", "/ˈæs.pekt/", "noun", "khía cạnh, mặt", "B2"),
    ("assess", "/əˈses/", "verb", "đánh giá, định giá", "B2"),
    ("assign", "/əˈsaɪn/", "verb", "phân công, giao việc", "B2"),
    ("assist", "/əˈsɪst/", "verb", "hỗ trợ, giúp đỡ", "B1"),
    ("assume", "/əˈsjuːm/", "verb", "giả định, cho rằng", "B2"),
    ("attach", "/əˈtætʃ/", "verb", "đính kèm, gắn vào", "B1"),
    ("attempt", "/əˈtempt/", "verb", "cố gắng, nỗ lực thử", "B1"),
    ("attend", "/əˈtend/", "verb", "tham dự, có mặt", "A2"),
    ("attention", "/əˈten.ʃən/", "noun", "sự chú ý", "A2"),
    ("attitude", "/ˈæt.ɪ.tʃuːd/", "noun", "thái độ, quan điểm", "B1"),
    ("attract", "/əˈtrækt/", "verb", "thu hút, hấp dẫn", "B1"),
    ("audience", "/ˈɔː.di.əns/", "noun", "khán giả, thính giả", "B1"),
    ("author", "/ˈɔː.θər/", "noun", "tác giả", "A2"),
    ("available", "/əˈveɪ.lə.bəl/", "adjective", "có sẵn, rảnh rỗi", "A2"),
    ("average", "/ˈæv.ər.ɪdʒ/", "adjective", "trung bình", "B1"),
    ("avoid", "/əˈvɔɪd/", "verb", "tránh, né tránh", "B1"),
    ("award", "/əˈwɔːd/", "noun", "giải thưởng", "B1"),
    ("aware", "/əˈweər/", "adjective", "nhận thức, biết rõ", "B1"),
    ("awesome", "/ˈɔː.səm/", "adjective", "tuyệt vời", "A2"),
    ("background", "/ˈbæk.ɡraʊnd/", "noun", "nền tảng, bối cảnh", "B1"),
    ("balance", "/ˈbæl.əns/", "noun", "sự cân bằng, số dư", "B1"),
    ("barrier", "/ˈbær.i.ər/", "noun", "rào cản", "B2"),
    ("basic", "/ˈbeɪ.sɪk/", "adjective", "cơ bản, căn bản", "A2"),
    ("battery", "/ˈbæt.ər.i/", "noun", "pin, ắc quy", "B1"),
    ("beach", "/biːtʃ/", "noun", "bãi biển", "A1"),
    ("beautiful", "/ˈbjuː.tɪ.fəl/", "adjective", "xinh đẹp", "A1"),
    ("because", "/bɪˈkɒz/", "conjunction", "bởi vì", "A1"),
    ("become", "/bɪˈkʌm/", "verb", "trở thành, trở nên", "A1"),
    ("before", "/bɪˈfɔːr/", "preposition", "trước khi", "A1"),
    ("begin", "/bɪˈɡɪn/", "verb", "bắt đầu", "A1"),
    ("behavior", "/bɪˈheɪ.vjər/", "noun", "hành vi, cách cư xử", "B1"),
    ("believe", "/bɪˈliːv/", "verb", "tin tưởng", "A1"),
    ("belong", "/bɪˈlɒŋ/", "verb", "thuộc về", "A2"),
    ("benefit", "/ˈben.ɪ.fɪt/", "noun", "lợi ích, phúc lợi", "B1"),
    ("beside", "/bɪˈsaɪd/", "preposition", "bên cạnh", "A2"),
    ("better", "/ˈbet.ər/", "adjective", "tốt hơn", "A1"),
    ("between", "/bɪˈtwiːn/", "preposition", "ở giữa", "A1"),
    ("beyond", "/bɪˈjɒnd/", "preposition", "vượt ra ngoài", "B2"),
    ("bicycle", "/ˈbaɪ.sɪ.kəl/", "noun", "xe đạp", "A1"),
    ("billion", "/ˈbɪl.jən/", "number", "một tỷ", "A2"),
    ("biology", "/baɪˈɒl.ə.dʒi/", "noun", "sinh học", "B1"),
    ("birthday", "/ˈbɜːθ.deɪ/", "noun", "sinh nhật", "A1"),
    ("blanket", "/ˈblæŋ.kɪt/", "noun", "cái chăn, mền", "A2"),
    ("board", "/bɔːd/", "noun", "bảng; hội đồng", "A2"),
    ("book", "/bʊk/", "noun", "quyển sách", "A1"),
    ("border", "/ˈbɔː.dər/", "noun", "biên giới, đường viền", "B1"),
    ("borrow", "/ˈbɒr.əʊ/", "verb", "vay, mượn", "A2"),
    ("bottle", "/ˈbɒt.əl/", "noun", "cái chai, lọ", "A1"),
    ("bottom", "/ˈbɒt.əm/", "noun", "đáy, phần dưới cùng", "A2"),
    ("branch", "/brɑːntʃ/", "noun", "cành cây; chi nhánh", "B1"),
    ("brave", "/breɪv/", "adjective", "dũng cảm, gan dạ", "B1"),
    ("bread", "/bred/", "noun", "bánh mì", "A1"),
    ("breakfast", "/ˈbrek.fəst/", "noun", "bữa sáng", "A1"),
    ("breathe", "/briːð/", "verb", "hít thở", "B1"),
    ("bridge", "/brɪdʒ/", "noun", "cây cầu", "A2"),
    ("brief", "/briːf/", "adjective", "ngắn gọn, vắn tắt", "B2"),
    ("bright", "/braɪt/", "adjective", "sáng sủa, thông minh", "A2"),
    ("bring", "/brɪŋ/", "verb", "mang lại, đem theo", "A1"),
    ("broad", "/brɔːd/", "adjective", "rộng lớn, bao quát", "B2"),
    ("brother", "/ˈbrʌð.ər/", "noun", "anh/em trai", "A1"),
    ("budget", "/ˈbʌdʒ.ɪt/", "noun", "ngân sách", "B2"),
    ("build", "/bɪld/", "verb", "xây dựng", "A1"),
    ("building", "/ˈbɪl.dɪŋ/", "noun", "tòa nhà", "A1"),
    ("burden", "/ˈbɜː.dən/", "noun", "gánh nặng", "B2"),
    ("business", "/ˈbɪz.nɪs/", "noun", "kinh doanh, doanh nghiệp", "A2"),
    ("busy", "/ˈbɪz.i/", "adjective", "bận rộn", "A1"),
    ("button", "/ˈbʌt.ən/", "noun", "nút bấm; cúc áo", "A2"),
    ("calculate", "/ˈkæl.kjə.leɪt/", "verb", "tính toán", "B2"),
    ("calendar", "/ˈkæl.ɪn.dər/", "noun", "lịch", "A2"),
    ("calm", "/kɑːm/", "adjective", "bình tĩnh, êm đềm", "B1"),
    ("camera", "/ˈkæm.rə/", "noun", "máy ảnh", "A1"),
    ("campaign", "/kæmˈpeɪn/", "noun", "chiến dịch", "B2"),
    ("campus", "/ˈkæm.pəs/", "noun", "khuôn viên trường đại học", "B1"),
    ("cancel", "/ˈkæn.səl/", "verb", "hủy bỏ", "B1"),
    ("candidate", "/ˈkæn.dɪ.dət/", "noun", "ứng cử viên, thí sinh", "B2"),
    ("capable", "/ˈkeɪ.pə.bəl/", "adjective", "có khả năng, đủ năng lực", "B2"),
    ("capacity", "/kəˈpæs.ə.ti/", "noun", "sức chứa, năng lực", "B2"),
    ("capital", "/ˈkæp.ɪ.təl/", "noun", "thủ đô; vốn", "A2"),
    ("career", "/kəˈrɪər/", "noun", "sự nghiệp, nghề nghiệp", "B1"),
    ("careful", "/ˈkeə.fəl/", "adjective", "cẩn thận", "A1"),
    ("carry", "/ˈkær.i/", "verb", "mang, vác", "A1"),
    ("category", "/ˈkæt.ə.ɡri/", "noun", "hạng mục, thể loại", "B2"),
    ("cause", "/kɔːz/", "noun", "nguyên nhân", "A2"),
    ("celebrate", "/ˈsel.ə.breɪt/", "verb", "ăn mừng, kỷ niệm", "A2"),
    ("center", "/ˈsen.tər/", "noun", "trung tâm", "A1"),
    ("central", "/ˈsen.trəl/", "adjective", "trung tâm, chủ chốt", "B1"),
    ("century", "/ˈsen.tʃər.i/", "noun", "thế kỷ", "A2"),
    ("certain", "/ˈsɜː.tən/", "adjective", "chắc chắn, nhất định", "A2"),
    ("challenge", "/ˈtʃæl.ɪndʒ/", "noun", "thử thách, thách thức", "B1"),
    ("champion", "/ˈtʃæm.pi.ən/", "noun", "nhà vô địch", "B1"),
    ("chance", "/tʃɑːns/", "noun", "cơ hội, sự may rủi", "A2"),
    ("change", "/tʃeɪndʒ/", "verb", "thay đổi", "A1"),
    ("chapter", "/ˈtʃæp.tər/", "noun", "chương (sách)", "B1"),
    ("character", "/ˈkær.ək.tər/", "noun", "nhân vật; tính cách", "B1"),
    ("charge", "/tʃɑːdʒ/", "noun", "chi phí; sự sạc pin", "B1"),
    ("charity", "/ˈtʃær.ə.ti/", "noun", "từ thiện", "B1"),
    ("cheap", "/tʃiːp/", "adjective", "rẻ tiền", "A1"),
    ("check", "/tʃek/", "verb", "kiểm tra", "A1"),
    ("chemical", "/ˈkem.ɪ.kəl/", "noun", "hóa chất", "B1"),
    ("childhood", "/ˈtʃaɪld.hʊd/", "noun", "tuổi thơ", "B1"),
    ("choice", "/tʃɔɪs/", "noun", "sự lựa chọn", "A2"),
    ("choose", "/tʃuːz/", "verb", "lựa chọn", "A1"),
    ("circle", "/ˈsɜː.kəl/", "noun", "hình tròn, vòng tròn", "A2"),
    ("citizen", "/ˈsɪt.ɪ.zən/", "noun", "công dân", "B2"),
    ("city", "/ˈsɪt.i/", "noun", "thành phố", "A1"),
    ("claim", "/kleɪm/", "verb", "tuyên bố, yêu cầu", "B2"),
    ("classic", "/ˈklæs.ɪk/", "adjective", "kinh điển, cổ điển", "B2"),
    ("clean", "/kliːn/", "adjective", "sạch sẽ", "A1"),
    ("clear", "/klɪər/", "adjective", "rõ ràng, trong trẻo", "A2"),
    ("climate", "/ˈklaɪ.mət/", "noun", "khí hậu", "B1"),
    ("climb", "/klaɪm/", "verb", "leo trèo", "A1"),
    ("clock", "/klɒk/", "noun", "đồng hồ", "A1"),
    ("close", "/kləʊz/", "verb", "đóng lại", "A1"),
    ("clothes", "/kləʊðz/", "noun", "quần áo", "A1"),
    ("cloud", "/klaʊd/", "noun", "đám mây", "A2"),
    ("coach", "/kəʊtʃ/", "noun", "huấn luyện viên; xe khách", "A2"),
    ("coast", "/kəʊst/", "noun", "bờ biển", "A2"),
    ("coffee", "/ˈkɒf.i/", "noun", "cà phê", "A1"),
    ("collect", "/kəˈlekt/", "verb", "thu thập, sưu tầm", "A2"),
    ("college", "/ˈkɒl.ɪdʒ/", "noun", "trường cao đẳng/đại học", "A2"),
    ("combine", "/kəmˈbaɪn/", "verb", "kết hợp", "B1"),
    ("comfort", "/ˈkʌm.fət/", "noun", "sự thoải mái, an ủi", "B1"),
    ("command", "/kəˈmɑːnd/", "noun", "mệnh lệnh, câu lệnh", "B2"),
    ("comment", "/ˈkɒm.ent/", "noun", "bình luận, nhận xét", "A2"),
    ("commit", "/kəˈmɪt/", "verb", "cam kết; phạm (lỗi)", "B2"),
    ("common", "/ˈkɒm.ən/", "adjective", "phổ biến, chung", "A2"),
    ("communicate", "/kəˈmjuː.nɪ.keɪt/", "verb", "giao tiếp, truyền đạt", "B1"),
    ("community", "/kəˈmjuː.nə.ti/", "noun", "cộng đồng", "B1"),
    ("company", "/ˈkʌm.pə.ni/", "noun", "công ty; bạn đồng hành", "A1"),
    ("compare", "/kəmˈpeər/", "verb", "so sánh", "A2"),
    ("compete", "/kəmˈpiːt/", "verb", "cạnh tranh, thi đấu", "B1"),
    ("complain", "/kəmˈpleɪn/", "verb", "phàn nàn, khiếu nại", "B1"),
    ("complete", "/kəmˈpliːt/", "adjective", "hoàn chỉnh, đầy đủ", "A2"),
    ("complex", "/ˈkɒm.pleks/", "adjective", "phức tạp", "B2"),
    ("computer", "/kəmˈpjuː.tər/", "noun", "máy tính", "A1"),
    ("concentrate", "/ˈkɒn.sən.treɪt/", "verb", "tập trung", "B1"),
    ("concept", "/ˈkɒn.sept/", "noun", "khái niệm", "B2"),
    ("concern", "/kənˈsɜːn/", "noun", "mối quan tâm, lo ngại", "B2"),
    ("conclude", "/kənˈkluːd/", "verb", "kết luận", "B2"),
    ("condition", "/kənˈdɪʃ.ən/", "noun", "điều kiện, tình trạng", "B1"),
    ("conduct", "/kənˈdʌkt/", "verb", "tiến hành, thực hiện", "B2"),
    ("conference", "/ˈkɒn.fər.əns/", "noun", "hội nghị", "B1"),
    ("confirm", "/kənˈfɜːm/", "verb", "xác nhận", "B1"),
    ("conflict", "/ˈkɒn.flɪkt/", "noun", "xung đột, mâu thuẫn", "B2"),
    ("confuse", "/kənˈfjuːz/", "verb", "gây bối rối, nhầm lẫn", "B1"),
    ("connect", "/kəˈnekt/", "verb", "kết nối", "A2"),
    ("conscious", "/ˈkɒn.ʃəs/", "adjective", "có ý thức, tỉnh táo", "B2"),
    ("consequence", "/ˈkɒn.sɪ.kwəns/", "noun", "hậu quả, hệ quả", "B2"),
    ("consider", "/kənˈsɪd.ər/", "verb", "cân nhắc, xem xét", "B1"),
    ("consist", "/kənˈsɪst/", "verb", "bao gồm", "B1"),
    ("constant", "/ˈkɒn.stənt/", "adjective", "liên tục, không đổi", "B2"),
    ("construct", "/kənˈstrʌkt/", "verb", "xây dựng, kiến tạo", "B2"),
    ("consume", "/kənˈsjuːm/", "verb", "tiêu thụ, tiêu dùng", "B2"),
    ("contact", "/ˈkɒn.tækt/", "noun", "sự liên lạc, liên hệ", "A2"),
    ("contain", "/kənˈteɪn/", "verb", "chứa đựng", "B1"),
    ("content", "/ˈkɒn.tent/", "noun", "nội dung", "B1"),
    ("continue", "/kənˈtɪn.juː/", "verb", "tiếp tục", "A2"),
    ("contract", "/ˈkɒn.trækt/", "noun", "hợp đồng", "B2"),
    ("contrast", "/ˈkɒn.trɑːst/", "noun", "sự tương phản", "B2"),
    ("contribute", "/kənˈtrɪb.juːt/", "verb", "đóng góp", "B2"),
    ("control", "/kənˈtrəʊl/", "noun", "sự kiểm soát", "A2"),
    ("convenient", "/kənˈviː.ni.ənt/", "adjective", "tiện lợi, thuận tiện", "B1"),
    ("conversation", "/ˌkɒn.vəˈseɪ.ʃən/", "noun", "cuộc trò chuyện, hội thoại", "A1"),
    ("convince", "/kənˈvɪns/", "verb", "thuyết phục", "B2"),
    ("cook", "/kʊk/", "verb", "nấu ăn", "A1"),
    ("cooperate", "/kəʊˈɒp.ər.eɪt/", "verb", "hợp tác", "B2"),
    ("copy", "/ˈkɒp.i/", "verb", "sao chép", "A2"),
    ("corner", "/ˈkɔː.nər/", "noun", "góc", "A2"),
    ("correct", "/kəˈrekt/", "adjective", "đúng, chính xác", "A1"),
    ("cost", "/kɒst/", "noun", "chi phí, giá cả", "A2"),
    ("count", "/kaʊnt/", "verb", "đếm", "A1"),
    ("country", "/ˈkʌn.tri/", "noun", "đất nước, quốc gia", "A1"),
    ("couple", "/ˈkʌp.əl/", "noun", "cặp đôi; một vài", "A2"),
    ("courage", "/ˈkʌr.ɪdʒ/", "noun", "lòng dũng cảm", "B2"),
    ("course", "/kɔːs/", "noun", "khóa học; tiến trình", "A1"),
    ("court", "/kɔːt/", "noun", "tòa án; sân thể thao", "B1"),
    ("cover", "/ˈkʌv.ər/", "verb", "che phủ, bao gồm", "A2"),
    ("create", "/kriˈeɪt/", "verb", "tạo ra, sáng tạo", "A2"),
    ("creative", "/kriˈeɪ.tɪv/", "adjective", "sáng tạo", "B1"),
    ("credit", "/ˈkred.ɪt/", "noun", "tín dụng; sự công nhận", "B1"),
    ("crime", "/kraɪm/", "noun", "tội phạm", "B1"),
    ("crisis", "/ˈkraɪ.sɪs/", "noun", "khủng hoảng", "B2"),
    ("criticize", "/ˈkrɪt.ɪ.saɪz/", "verb", "chỉ trích, phê bình", "B2"),
    ("crowd", "/kraʊd/", "noun", "đám đông", "A2"),
    ("crucial", "/ˈkruː.ʃəl/", "adjective", "mang tính quyết định, tối quan trọng", "B2"),
    ("culture", "/ˈkʌl.tʃər/", "noun", "văn hóa", "A2"),
    ("curious", "/ˈkjʊə.ri.əs/", "adjective", "tò mò, hiếu kỳ", "B1"),
    ("current", "/ˈkʌr.ənt/", "adjective", "hiện tại, hiện hành", "B1"),
    ("custom", "/ˈkʌs.təm/", "noun", "phong tục, thói quen", "B1"),
    ("customer", "/ˈkʌs.tə.mər/", "noun", "khách hàng", "A2"),
    ("daily", "/ˈdeɪ.li/", "adjective", "hằng ngày", "A2"),
    ("damage", "/ˈdæm.ɪdʒ/", "noun", "thiệt hại, hư hỏng", "B1"),
    ("danger", "/ˈdeɪn.dʒər/", "noun", "mối nguy hiểm", "A2"),
    ("data", "/ˈdeɪ.tə/", "noun", "dữ liệu", "B1"),
    ("daughter", "/ˈdɔː.tər/", "noun", "con gái", "A1"),
    ("debate", "/dɪˈbeɪt/", "noun", "cuộc tranh luận", "B2"),
    ("decade", "/ˈdek.eɪd/", "noun", "thập kỷ", "B2"),
    ("decide", "/dɪˈsaɪd/", "verb", "quyết định", "A1"),
    ("decline", "/dɪˈklaɪn/", "verb", "từ chối; suy giảm", "B2"),
    ("decorate", "/ˈdek.ə.reɪt/", "verb", "trang trí", "B1"),
    ("decrease", "/dɪˈkriːs/", "verb", "giảm xuống", "B2"),
    ("deep", "/diːp/", "adjective", "sâu sắc, sâu", "A2"),
    ("defeat", "/dɪˈfiːt/", "verb", "đánh bại", "B1"),
    ("defend", "/dɪˈfend/", "verb", "bảo vệ, phòng thủ", "B2"),
    ("define", "/dɪˈfaɪn/", "verb", "định nghĩa, xác định", "B2"),
    ("degree", "/dɪˈɡriː/", "noun", "bằng cấp; độ", "A2"),
    ("delay", "/dɪˈleɪ/", "verb", "trì hoãn", "B1"),
    ("deliver", "/dɪˈlɪv.ər/", "verb", "giao hàng; truyền tải", "B1"),
    ("demand", "/dɪˈmɑːnd/", "noun", "nhu cầu, đòi hỏi", "B2"),
    ("demonstrate", "/ˈdem.ən.streɪt/", "verb", "chứng minh, minh họa", "B2"),
    ("dentist", "/ˈden.tɪst/", "noun", "nha sĩ", "A2"),
    ("deny", "/dɪˈnaɪ/", "verb", "phủ nhận, từ chối", "B2"),
    ("department", "/dɪˈpɑːt.mənt/", "noun", "phòng ban, khoa", "B1"),
    ("depend", "/dɪˈpend/", "verb", "phụ thuộc", "A2"),
    ("describe", "/dɪˈskraɪb/", "verb", "mô tả, diễn tả", "A1"),
    ("desert", "/ˈdez.ət/", "noun", "sa mạc", "A2"),
    ("deserve", "/dɪˈzɜːv/", "verb", "xứng đáng", "B2"),
    ("design", "/dɪˈzaɪn/", "verb", "thiết kế", "A2"),
    ("desire", "/dɪˈzaɪər/", "noun", "khát khao, mong muốn", "B2"),
    ("desktop", "/ˈdesk.tɒp/", "noun", "máy tính để bàn", "B1"),
    ("despite", "/dɪˈspaɪt/", "preposition", "mặc dù", "B1"),
    ("destination", "/ˌdes.tɪˈneɪ.ʃən/", "noun", "điểm đến", "B1"),
    ("destroy", "/dɪˈstrɔɪ/", "verb", "phá hủy", "B1"),
    ("detail", "/ˈdiː.teɪl/", "noun", "chi tiết", "A2"),
    ("detect", "/dɪˈtekt/", "verb", "phát hiện", "B2"),
    ("determine", "/dɪˈtɜː.mɪn/", "verb", "xác định, quyết tâm", "B2"),
    ("develop", "/dɪˈvel.əp/", "verb", "phát triển", "A2"),
    ("device", "/dɪˈvaɪs/", "noun", "thiết bị", "B1"),
    ("dialogue", "/ˈdaɪ.ə.lɒɡ/", "noun", "đối thoại, hội thoại", "B1"),
    ("dictionary", "/ˈdɪk.ʃən.ər.i/", "noun", "từ điển", "A1"),
    ("difference", "/ˈdɪf.ər.əns/", "noun", "sự khác biệt", "A2"),
    ("difficult", "/ˈdɪf.ɪ.kəlt/", "adjective", "khó khăn", "A1"),
    ("digital", "/ˈdɪdʒ.ɪ.təl/", "adjective", "kỹ thuật số", "B1"),
    ("dinner", "/ˈdɪn.ər/", "noun", "bữa tối", "A1"),
    ("direct", "/dəˈrekt/", "adjective", "trực tiếp", "B1"),
    ("direction", "/dəˈrek.ʃən/", "noun", "phương hướng, chỉ dẫn", "A2"),
    ("disappear", "/ˌdɪs.əˈpɪər/", "verb", "biến mất", "A2"),
    ("discipline", "/ˈdɪs.ə.plɪn/", "noun", "kỷ luật", "B2"),
    ("discount", "/ˈdɪs.kaʊnt/", "noun", "giảm giá", "B1"),
    ("discover", "/dɪˈskʌv.ər/", "verb", "khám phá, phát hiện", "A2"),
    ("discuss", "/dɪˈskʌs/", "verb", "thảo luận", "A2"),
    ("disease", "/dɪˈziːz/", "noun", "bệnh tật", "B1"),
    ("display", "/dɪˈspleɪ/", "verb", "hiển thị, trưng bày", "B1"),
    ("distance", "/ˈdɪs.təns/", "noun", "khoảng cách", "A2"),
    ("distinct", "/dɪˈstɪŋkt/", "adjective", "riêng biệt, rõ rệt", "B2"),
    ("distribute", "/dɪˈstrɪb.juːt/", "verb", "phân phối", "B2"),
    ("district", "/ˈdɪs.trɪkt/", "noun", "quận, huyện, khu vực", "B1"),
    ("divide", "/dɪˈvaɪd/", "verb", "chia, phân chia", "B1"),
    ("doctor", "/ˈdɒk.tər/", "noun", "bác sĩ", "A1"),
    ("document", "/ˈdɒk.jə.mənt/", "noun", "tài liệu, văn bản", "A2"),
    ("domestic", "/dəˈmes.tɪk/", "adjective", "nội địa, trong gia đình", "B2"),
    ("dominate", "/ˈdɒm.ɪ.neɪt/", "verb", "thống trị, chiếm ưu thế", "B2"),
    ("double", "/ˈdʌb.əl/", "adjective", "gấp đôi", "A2"),
    ("doubt", "/daʊt/", "noun", "sự nghi ngờ", "B1"),
    ("download", "/ˌdaʊnˈləʊd/", "verb", "tải xuống", "A2"),
    ("drama", "/ˈdrɑː.mə/", "noun", "kịch; sự kịch tính", "B1"),
    ("dream", "/driːm/", "noun", "giấc mơ", "A1"),
    ("drive", "/draɪv/", "verb", "lái xe", "A1"),
    ("driver", "/ˈdraɪ.vər/", "noun", "tài xế", "A1"),
    ("during", "/ˈdjʊə.rɪŋ/", "preposition", "trong suốt", "A2"),
    ("dynamic", "/daɪˈnæm.ɪk/", "adjective", "năng động, linh hoạt", "B2"),
    ("eager", "/ˈiː.ɡər/", "adjective", "háo hức, mong mỏi", "B2"),
    ("early", "/ˈɜː.li/", "adjective", "sớm", "A1"),
    ("earn", "/ɜːn/", "verb", "kiếm được (tiền, sự tôn trọng)", "A2"),
    ("earth", "/ɜːθ/", "noun", "trái đất, mặt đất", "A2"),
    ("easily", "/ˈiː.zəl.i/", "adverb", "một cách dễ dàng", "A2"),
    ("economy", "/ɪˈkɒn.ə.mi/", "noun", "nền kinh tế", "B1"),
    ("educate", "/ˈedʒ.u.keɪt/", "verb", "giáo dục, đào tạo", "B1"),
    ("effect", "/ɪˈfekt/", "noun", "tác động, hiệu ứng", "B1"),
    ("effort", "/ˈef.ət/", "noun", "nỗ lực, cố gắng", "B1"),
    ("either", "/ˈaɪ.ðər/", "determiner", "một trong hai", "A2"),
    ("elderly", "/ˈel.dəl.i/", "adjective", "lớn tuổi, cao tuổi", "B1"),
    ("elect", "/ɪˈlekt/", "verb", "bầu chọn", "B2"),
    ("electric", "/ɪˈlek.trɪk/", "adjective", "chạy bằng điện", "A2"),
    ("element", "/ˈel.ɪ.mənt/", "noun", "yếu tố, nguyên tố", "B2"),
    ("elevator", "/ˈel.ɪ.veɪ.tər/", "noun", "thang máy", "A2"),
    ("eliminate", "/ɪˈlɪm.ɪ.neɪt/", "verb", "loại bỏ", "B2"),
    ("email", "/ˈiː.meɪl/", "noun", "thư điện tử", "A1"),
    ("emerge", "/ɪˈmɜːdʒ/", "verb", "nổi lên, xuất hiện", "B2"),
    ("emotion", "/ɪˈməʊ.ʃən/", "noun", "cảm xúc", "B1"),
    ("employ", "/ɪmˈplɔɪ/", "verb", "tuyển dụng, sử dụng", "B1"),
    ("employee", "/ɪmˈplɔɪ.iː/", "noun", "nhân viên", "B1"),
    ("employer", "/ɪmˈplɔɪ.ər/", "noun", "nhà tuyển dụng", "B1"),
    ("empty", "/ˈemp.ti/", "adjective", "trống rỗng", "A2"),
    ("enable", "/ɪˈneɪ.bəl/", "verb", "cho phép, kích hoạt", "B2"),
    ("encounter", "/ɪnˈkaʊn.tər/", "verb", "bắt gặp, đối mặt", "B2"),
    ("encourage", "/ɪnˈkʌr.ɪdʒ/", "verb", "khuyến khích, động viên", "B1"),
    ("energy", "/ˈen.ə.dʒi/", "noun", "năng lượng", "A2"),
    ("engage", "/ɪnˈɡeɪdʒ/", "verb", "thu hút, tham gia", "B2"),
    ("engineer", "/ˌen.dʒɪˈnɪər/", "noun", "kỹ sư", "A2"),
    ("enhance", "/ɪnˈhɑːns/", "verb", "nâng cao, tăng cường", "B2"),
    ("enjoy", "/ɪnˈdʒɔɪ/", "verb", "tận hưởng, thích thú", "A1"),
    ("enormous", "/ɪˈnɔː.məs/", "adjective", "to lớn, khổng lồ", "B1"),
    ("enough", "/ɪˈnʌf/", "determiner", "đủ", "A1"),
    ("ensure", "/ɪnˈʃʊər/", "verb", "đảm bảo", "B2"),
    ("enter", "/ˈen.tər/", "verb", "đi vào, nhập vào", "A2"),
    ("entertain", "/ˌen.təˈteɪn/", "verb", "giải trí, tiếp đãi", "B1"),
    ("enthusiasm", "/ɪnˈθjuː.zi.æz.əm/", "noun", "sự nhiệt huyết", "B2"),
    ("entire", "/ɪnˈtaɪər/", "adjective", "toàn bộ", "B2"),
    ("environment", "/ɪnˈvaɪ.rən.mənt/", "noun", "môi trường", "B1"),
    ("equal", "/ˈiː.kwəl/", "adjective", "bình đẳng, bằng nhau", "B1"),
    ("equipment", "/ɪˈkwɪp.mənt/", "noun", "trang thiết bị", "B1"),
    ("error", "/ˈer.ər/", "noun", "lỗi, sai sót", "B1"),
    ("escape", "/ɪˈskeɪp/", "verb", "trốn thoát", "B1"),
    ("especially", "/ɪˈspeʃ.əl.i/", "adverb", "đặc biệt là", "A2"),
    ("essential", "/ɪˈsen.ʃəl/", "adjective", "thiết yếu, cốt lõi", "B2"),
    ("establish", "/ɪˈstæb.lɪʃ/", "verb", "thiết lập, thành lập", "B2"),
    ("estimate", "/ˈes.tɪ.meɪt/", "verb", "ước tính", "B2"),
    ("evaluate", "/ɪˈvæl.ju.eɪt/", "verb", "đánh giá", "B2"),
    ("evening", "/ˈiːv.nɪŋ/", "noun", "buổi tối", "A1"),
    ("event", "/ɪˈvent/", "noun", "sự kiện", "A2"),
    ("eventually", "/ɪˈven.tʃu.ə.li/", "adverb", "cuối cùng thì", "B1"),
    ("every", "/ˈev.ri/", "determiner", "mỗi, mọi", "A1"),
    ("evidence", "/ˈev.ɪ.dəns/", "noun", "bằng chứng", "B2"),
    ("exact", "/ɪɡˈzækt/", "adjective", "chính xác", "A2"),
    ("example", "/ɪɡˈzɑːm.pəl/", "noun", "ví dụ", "A1"),
    ("excellent", "/ˈek.səl.ənt/", "adjective", "xuất sắc", "A2"),
    ("except", "/ɪkˈsept/", "preposition", "ngoại trừ", "A2"),
    ("exchange", "/ɪksˈtʃeɪndʒ/", "verb", "trao đổi", "B1"),
    ("excited", "/ɪkˈsaɪ.tɪd/", "adjective", "hào hứng, phấn khích", "A1"),
    ("excuse", "/ɪkˈskjuːs/", "noun", "lý do bào chữa", "B1"),
    ("exercise", "/ˈek.sə.saɪz/", "noun", "bài tập, thể dục", "A1"),
    ("exist", "/ɪɡˈzɪst/", "verb", "tồn tại", "B1"),
    ("expand", "/ɪkˈspænd/", "verb", "mở rộng", "B2"),
    ("expect", "/ɪkˈspekt/", "verb", "mong đợi, kỳ vọng", "A2"),
    ("expensive", "/ɪkˈspen.sɪv/", "adjective", "đắt đỏ", "A1"),
    ("experience", "/ɪkˈspɪə.ri.əns/", "noun", "kinh nghiệm, trải nghiệm", "A2"),
    ("experiment", "/ɪkˈsper.ɪ.mənt/", "noun", "thí nghiệm, thử nghiệm", "B1"),
    ("expert", "/ˈek.spɜːt/", "noun", "chuyên gia", "B1"),
    ("explain", "/ɪkˈspleɪn/", "verb", "giải thích", "A2"),
    ("explore", "/ɪkˈsplɔːr/", "verb", "khám phá, thám hiểm", "B1"),
    ("export", "/ˈek.spɔːt/", "verb", "xuất khẩu", "B1"),
    ("express", "/ɪkˈspres/", "verb", "bày tỏ, diễn đạt", "B1"),
    ("extend", "/ɪkˈstend/", "verb", "kéo dài, mở rộng", "B2"),
    ("external", "/ɪkˈstɜː.nəl/", "adjective", "bên ngoài", "B2"),
    ("extra", "/ˈek.strə/", "adjective", "thêm, phụ trội", "A2"),
    ("extreme", "/ɪkˈstriːm/", "adjective", "cực độ, khắc nghiệt", "B2"),
    ("facility", "/fəˈsɪl.ə.ti/", "noun", "cơ sở vật chất, tiện nghi", "B2"),
    ("factor", "/ˈfæk.tər/", "noun", "nhân tố, yếu tố", "B2"),
    ("factory", "/ˈfæk.tər.i/", "noun", "nhà máy", "A2"),
    ("fail", "/feɪl/", "verb", "thất bại, trượt", "A2"),
    ("fair", "/feər/", "adjective", "công bằng, hợp lý", "B1"),
    ("faith", "/feɪθ/", "noun", "niềm tin", "B2"),
    ("familiar", "/fəˈmɪl.i.ər/", "adjective", "quen thuộc", "B1"),
    ("family", "/ˈfæm.əl.i/", "noun", "gia đình", "A1"),
    ("famous", "/ˈfeɪ.məs/", "adjective", "nổi tiếng", "A1"),
    ("fantastic", "/fænˈtæs.tɪk/", "adjective", "tuyệt vời", "A2"),
    ("fashion", "/ˈfæʃ.ən/", "noun", "thời trang", "A2"),
    ("fast", "/fɑːst/", "adjective", "nhanh", "A1"),
    ("fault", "/fɔːlt/", "noun", "lỗi lầm, khuyết điểm", "B1"),
    ("favor", "/ˈfeɪ.vər/", "noun", "sự giúp đỡ, ân huệ", "B1"),
    ("favorite", "/ˈfeɪ.vər.ɪt/", "adjective", "yêu thích", "A1"),
    ("fear", "/fɪər/", "noun", "nỗi sợ hãi", "B1"),
    ("feature", "/ˈfiː.tʃər/", "noun", "tính năng, đặc điểm", "B1"),
    ("feedback", "/ˈfiːd.bæk/", "noun", "phản hồi, góp ý", "B2"),
    ("feeling", "/ˈfiː.lɪŋ/", "noun", "cảm giác", "A2"),
    ("festival", "/ˈfes.tɪ.vəl/", "noun", "lễ hội", "A2"),
    ("fiction", "/ˈfɪk.ʃən/", "noun", "tiểu thuyết, hư cấu", "B1"),
    ("field", "/fiːld/", "noun", "cánh đồng; lĩnh vực", "A2"),
    ("figure", "/ˈfɪɡ.ər/", "noun", "con số; nhân vật", "B1"),
    ("file", "/faɪl/", "noun", "tệp tin, hồ sơ", "A2"),
    ("final", "/ˈfaɪ.nəl/", "adjective", "cuối cùng", "A2"),
    ("finance", "/ˈfaɪ.næns/", "noun", "tài chính", "B2"),
    ("find", "/faɪnd/", "verb", "tìm thấy", "A1"),
    ("finish", "/ˈfɪn.ɪʃ/", "verb", "hoàn thành, kết thúc", "A1"),
    ("flexible", "/ˈflek.sə.bəl/", "adjective", "linh hoạt", "B2"),
    ("flight", "/flaɪt/", "noun", "chuyến bay", "A2"),
    ("floor", "/flɔːr/", "noun", "sàn nhà, tầng", "A1"),
    ("flower", "/flaʊər/", "noun", "bông hoa", "A1"),
    ("focus", "/ˈfəʊ.kəs/", "verb", "tập trung", "B1"),
    ("folder", "/ˈfəʊl.dər/", "noun", "thư mục", "B1"),
    ("follow", "/ˈfɒl.əʊ/", "verb", "theo dõi, làm theo", "A1"),
    ("food", "/fuːd/", "noun", "thức ăn, thực phẩm", "A1"),
    ("force", "/fɔːs/", "noun", "lực lượng, sức mạnh", "B1"),
    ("foreign", "/ˈfɒr.ən/", "adjective", "nước ngoài", "A2"),
    ("forest", "/ˈfɒr.ɪst/", "noun", "khu rừng", "A2"),
    ("forget", "/fəˈɡet/", "verb", "quên", "A1"),
    ("forgive", "/fəˈɡɪv/", "verb", "tha thứ", "B1"),
    ("formal", "/ˈfɔː.məl/", "adjective", "trang trọng, chính thức", "B1"),
    ("format", "/ˈfɔː.mæt/", "noun", "định dạng", "B2"),
    ("former", "/ˈfɔː.mər/", "adjective", "trước đây, cũ", "B2"),
    ("fortune", "/ˈfɔː.tʃuːn/", "noun", "vận may, tài sản lớn", "B2"),
    ("forward", "/ˈfɔː.wəd/", "adverb", "về phía trước", "A2"),
    ("foundation", "/faʊnˈdeɪ.ʃən/", "noun", "nền tảng, sự thành lập", "B2"),
    ("freedom", "/ˈfriː.dəm/", "noun", "sự tự do", "B1"),
    ("frequent", "/ˈfriː.kwənt/", "adjective", "thường xuyên", "B2"),
    ("fresh", "/freʃ/", "adjective", "tươi mới, trong lành", "A2"),
    ("friend", "/frend/", "noun", "người bạn", "A1"),
    ("friendly", "/ˈfrend.li/", "adjective", "thân thiện", "A1"),
    ("front", "/frʌnt/", "noun", "phía trước", "A1"),
    ("fruit", "/fruːt/", "noun", "trái cây", "A1"),
    ("frustrate", "/frʌsˈtreɪt/", "verb", "làm nản lòng, bực bội", "B2"),
    ("function", "/ˈfʌŋk.ʃən/", "noun", "chức năng, hàm", "B2"),
    ("fundamental", "/ˌfʌn.dəˈmen.təl/", "adjective", "cơ bản, nền tảng", "B2"),
    ("furniture", "/ˈfɜː.nɪ.tʃər/", "noun", "đồ nội thất", "A2"),
    ("further", "/ˈfɜː.ðər/", "adverb", "xa hơn, thêm nữa", "B1"),
    ("future", "/ˈfjuː.tʃər/", "noun", "tương lai", "A1"),
    ("gain", "/ɡeɪn/", "verb", "đạt được, thu được", "B1"),
    ("gallery", "/ˈɡæl.ər.i/", "noun", "phòng trưng bày", "A2"),
    ("garden", "/ˈɡɑː.dən/", "noun", "khu vườn", "A1"),
    ("gather", "/ˈɡæð.ər/", "verb", "tụ họp, thu thập", "B1"),
    ("general", "/ˈdʒen.ər.əl/", "adjective", "chung, tổng quát", "B1"),
    ("generate", "/ˈdʒen.ə.reɪt/", "verb", "tạo ra, phát sinh", "B2"),
    ("generation", "/ˌdʒen.əˈreɪ.ʃən/", "noun", "thế hệ", "B1"),
    ("generous", "/ˈdʒen.ər.əs/", "adjective", "hào phóng", "B1"),
    ("gentle", "/ˈdʒen.təl/", "adjective", "nhẹ nhàng, dịu dàng", "B1"),
    ("genuine", "/ˈdʒen.ju.ɪn/", "adjective", "chân thật, chính hãng", "B2"),
    ("global", "/ˈɡləʊ.bəl/", "adjective", "toàn cầu", "B1"),
    ("goal", "/ɡəʊl/", "noun", "mục tiêu; bàn thắng", "A2"),
    ("government", "/ˈɡʌv.ən.mənt/", "noun", "chính phủ", "B1"),
    ("gradual", "/ˈɡrædʒ.u.əl/", "adjective", "từng bước, dần dần", "B2"),
    ("graduate", "/ˈɡrædʒ.u.ət/", "verb", "tốt nghiệp", "B1"),
    ("grateful", "/ˈɡreɪt.fəl/", "adjective", "biết ơn", "B1"),
    ("great", "/ɡreɪt/", "adjective", "tuyệt vời, vĩ đại", "A1"),
    ("grocery", "/ˈɡrəʊ.sər.i/", "noun", "cửa hàng tạp hóa", "B1"),
    ("group", "/ɡruːp/", "noun", "nhóm", "A1"),
    ("growth", "/ɡrəʊθ/", "noun", "sự tăng trưởng, phát triển", "B2"),
    ("guarantee", "/ˌɡær.ənˈtiː/", "verb", "bảo đảm, bảo hành", "B2"),
    ("guest", "/ɡest/", "noun", "khách mời", "A2"),
    ("guide", "/ɡaɪd/", "noun", "hướng dẫn viên, tài liệu hướng dẫn", "A2"),
    ("guilty", "/ˈɡɪl.ti/", "adjective", "có tội, cảm thấy tội lỗi", "B1"),
    ("habit", "/ˈhæb.ɪt/", "noun", "thói quen", "A2"),
    ("handle", "/ˈhæn.dəl/", "verb", "xử lý, giải quyết", "B2"),
    ("happen", "/ˈhæp.ən/", "verb", "xảy ra", "A1"),
    ("happy", "/ˈhæp.i/", "adjective", "vui vẻ, hạnh phúc", "A1"),
    ("hardly", "/ˈhɑːd.li/", "adverb", "hầu như không", "B1"),
    ("harmony", "/ˈhɑː.mə.ni/", "noun", "sự hài hòa", "B2"),
    ("health", "/helθ/", "noun", "sức khỏe", "A1"),
    ("healthy", "/ˈhel.θi/", "adjective", "khỏe mạnh, lành mạnh", "A1"),
    ("heart", "/hɑːt/", "noun", "trái tim", "A1"),
    ("heavy", "/ˈhev.i/", "adjective", "nặng", "A2"),
    ("helpful", "/ˈhelp.fəl/", "adjective", "hữu ích, hay giúp đỡ", "A2"),
    ("heritage", "/ˈher.ɪ.tɪdʒ/", "noun", "di sản", "B2"),
    ("highlight", "/ˈhaɪ.laɪt/", "verb", "làm nổi bật, tô sáng", "B2"),
    ("history", "/ˈhɪs.tər.i/", "noun", "lịch sử", "A1"),
    ("hobby", "/ˈhɒb.i/", "noun", "sở thích", "A1"),
    ("holiday", "/ˈhɒl.ə.deɪ/", "noun", "kỳ nghỉ, ngày lễ", "A1"),
    ("honest", "/ˈɒn.ɪst/", "adjective", "trung thực, thật thà", "B1"),
    ("hope", "/həʊp/", "verb", "hy vọng", "A1"),
    ("horizon", "/həˈraɪ.zən/", "noun", "chân trời", "B2"),
    ("hospital", "/ˈhɒs.pɪ.təl/", "noun", "bệnh viện", "A1"),
    ("hotel", "/həʊˈtel/", "noun", "khách sạn", "A1"),
    ("house", "/haʊs/", "noun", "ngôi nhà", "A1"),
    ("however", "/haʊˈev.ər/", "adverb", "tuy nhiên", "A2"),
    ("huge", "/hjuːdʒ/", "adjective", "to lớn, khổng lồ", "A2"),
    ("human", "/ˈhjuː.mən/", "noun", "con người", "A2"),
    ("humor", "/ˈhjuː.mər/", "noun", "sự hài hước", "B1"),
    ("hungry", "/ˈhʌŋ.ɡri/", "adjective", "đói bụng", "A1"),
    ("hurry", "/ˈhʌr.i/", "verb", "vội vã, khẩn trương", "A2"),
    ("idea", "/aɪˈdɪə/", "noun", "ý tưởng", "A1"),
    ("identify", "/aɪˈden.tɪ.faɪ/", "verb", "nhận diện, xác định", "B1"),
    ("ignore", "/ɪɡˈnɔːr/", "verb", "phớt lờ, bỏ qua", "B1"),
    ("illegal", "/ɪˈliː.ɡəl/", "adjective", "bất hợp pháp", "B1"),
    ("illustrate", "/ˈɪl.ə.streɪt/", "verb", "minh họa", "B2"),
    ("image", "/ˈɪm.ɪdʒ/", "noun", "hình ảnh", "A2"),
    ("imagine", "/ɪˈmædʒ.ɪn/", "verb", "tưởng tượng", "A2"),
    ("immediate", "/ɪˈmiː.di.ət/", "adjective", "ngay lập tức", "B1"),
    ("impact", "/ˈɪm.pækt/", "noun", "tác động, ảnh hưởng", "B2"),
    ("important", "/ɪmˈpɔː.tənt/", "adjective", "quan trọng", "A1"),
    ("impress", "/ɪmˈpres/", "verb", "gây ấn tượng", "B1"),
    ("include", "/ɪnˈkluːd/", "verb", "bao gồm", "A2"),
    ("income", "/ˈɪn.kʌm/", "noun", "thu nhập", "B2"),
    ("increase", "/ɪnˈkriːs/", "verb", "tăng lên", "A2"),
    ("incredible", "/ɪnˈkred.ə.bəl/", "adjective", "đáng kinh ngạc, tuyệt vời", "B1"),
    ("independent", "/ˌɪn.dɪˈpen.dənt/", "adjective", "độc lập", "B1"),
    ("indicate", "/ˈɪn.dɪ.keɪt/", "verb", "chỉ ra, biểu thị", "B2"),
    ("individual", "/ˌɪn.dɪˈvɪdʒ.u.əl/", "noun", "cá nhân", "B1"),
    ("industry", "/ˈɪn.də.stri/", "noun", "ngành công nghiệp", "B1"),
    ("influence", "/ˈɪn.flu.əns/", "noun", "sự ảnh hưởng", "B1"),
    ("inform", "/ɪnˈfɔːm/", "verb", "thông báo, báo cho biết", "B1"),
    ("information", "/ˌɪn.fəˈmeɪ.ʃən/", "noun", "thông tin", "A1"),
    ("initial", "/ɪˈnɪʃ.əl/", "adjective", "ban đầu", "B2"),
    ("initiative", "/ɪˈnɪʃ.ə.tɪv/", "noun", "sáng kiến", "B2"),
    ("injure", "/ˈɪn.dʒər/", "verb", "làm bị thương", "B1"),
    ("innovate", "/ˈɪn.ə.veɪt/", "verb", "đổi mới, sáng tạo", "B2"),
    ("input", "/ˈɪn.pʊt/", "noun", "đầu vào", "B2"),
    ("insight", "/ˈɪn.saɪt/", "noun", "cái nhìn sâu sắc", "B2"),
    ("insist", "/ɪnˈsɪst/", "verb", "khăng khăng, cố nài", "B2"),
    ("inspect", "/ɪnˈspekt/", "verb", "kiểm tra, thanh tra", "B2"),
    ("inspire", "/ɪnˈspaɪər/", "verb", "truyền cảm hứng", "B2"),
    ("install", "/ɪnˈstɔːl/", "verb", "cài đặt, lắp đặt", "B1"),
    ("instant", "/ˈɪn.stənt/", "adjective", "tức thì, ngay lập tức", "B2"),
    ("instead", "/ɪnˈsted/", "adverb", "thay vào đó", "A2"),
    ("instruct", "/ɪnˈstrʌkt/", "verb", "hướng dẫn, chỉ dẫn", "B2"),
    ("instrument", "/ˈɪn.strə.mənt/", "noun", "nhạc cụ; dụng cụ", "A2"),
    ("integrate", "/ˈɪn.tɪ.ɡreɪt/", "verb", "tích hợp, hòa nhập", "B2"),
    ("intelligent", "/ɪnˈtel.ɪ.dʒənt/", "adjective", "thông minh", "A2"),
    ("intend", "/ɪnˈtend/", "verb", "dự định, có ý định", "B1"),
    ("interact", "/ˌɪn.təˈrækt/", "verb", "tương tác", "B2"),
    ("interest", "/ˈɪn.trəst/", "noun", "sự hứng thú; lãi suất", "A2"),
    ("interface", "/ˈɪn.tə.feɪs/", "noun", "giao diện", "B2"),
    ("internal", "/ɪnˈtɜː.nəl/", "adjective", "bên trong, nội bộ", "B2"),
    ("internet", "/ˈɪn.tə.net/", "noun", "mạng internet", "A1"),
    ("interpret", "/ɪnˈtɜː.prɪt/", "verb", "phiên dịch, diễn giải", "B2"),
    ("interrupt", "/ˌɪn.təˈrʌpt/", "verb", "ngắt lời, làm gián đoạn", "B1"),
    ("introduce", "/ˌɪn.trəˈdjuːs/", "verb", "giới thiệu", "A1"),
    ("invest", "/ɪnˈvest/", "verb", "đầu tư", "B2"),
    ("investigate", "/ɪnˈves.tɪ.ɡeɪt/", "verb", "điều tra, nghiên cứu", "B2"),
    ("invite", "/ɪnˈvaɪt/", "verb", "mời", "A1"),
    ("involve", "/ɪnˈvɒlv/", "verb", "liên quan, bao gồm", "B1"),
    ("island", "/ˈaɪ.lənd/", "noun", "hòn đảo", "A1"),
    ("issue", "/ˈɪʃ.uː/", "noun", "vấn đề; số phát hành", "B1"),
    ("journey", "/ˈdʒɜː.ni/", "noun", "hành trình, chuyến đi", "A2"),
    ("judge", "/dʒʌdʒ/", "verb", "đánh giá, xét xử", "B1"),
    ("justice", "/ˈdʒʌs.tɪs/", "noun", "công lý, sự công bằng", "B2"),
    ("justify", "/ˈdʒʌs.tɪ.faɪ/", "verb", "biện minh, chứng minh là đúng", "B2"),
    ("keyboard", "/ˈkiː.bɔːd/", "noun", "bàn phím", "A2"),
    ("kitchen", "/ˈkɪtʃ.ən/", "noun", "nhà bếp", "A1"),
    ("knowledge", "/ˈnɒl.ɪdʒ/", "noun", "kiến thức, sự hiểu biết", "B1"),
    ("label", "/ˈleɪ.bəl/", "noun", "nhãn dán", "B1"),
    ("language", "/ˈlæŋ.ɡwɪdʒ/", "noun", "ngôn ngữ", "A1"),
    ("laptop", "/ˈlæp.tɒp/", "noun", "máy tính xách tay", "A2"),
    ("large", "/lɑːdʒ/", "adjective", "rộng lớn", "A1"),
    ("later", "/ˈleɪ.tər/", "adverb", "sau này, lát nữa", "A1"),
    ("laugh", "/lɑːf/", "verb", "cười", "A1"),
    ("launch", "/lɔːntʃ/", "verb", "ra mắt, khởi chạy", "B2"),
    ("lawyer", "/ˈlɔː.jər/", "noun", "luật sư", "A2"),
    ("leader", "/ˈliː.dər/", "noun", "người lãnh đạo", "A2"),
    ("learn", "/lɜːn/", "verb", "học, tìm hiểu", "A1"),
    ("lecture", "/ˈlek.tʃər/", "noun", "bài giảng", "B1"),
    ("legal", "/ˈliː.ɡəl/", "adjective", "hợp pháp, pháp lý", "B1"),
    ("leisure", "/ˈleʒ.ər/", "noun", "thời gian rảnh rỗi", "B1"),
    ("lesson", "/ˈles.ən/", "noun", "bài học", "A1"),
    ("letter", "/ˈlet.ər/", "noun", "bức thư; chữ cái", "A1"),
    ("level", "/ˈlev.əl/", "noun", "trình độ, mức độ", "A2"),
    ("library", "/ˈlaɪ.brər.i/", "noun", "thư viện", "A1"),
    ("license", "/ˈlaɪ.səns/", "noun", "giấy phép", "B1"),
    ("lifestyle", "/ˈlaɪf.staɪl/", "noun", "lối sống", "B1"),
    ("limit", "/ˈlɪm.ɪt/", "noun", "giới hạn", "B1"),
    ("listen", "/ˈlɪs.ən/", "verb", "lắng nghe", "A1"),
    ("literature", "/ˈlɪt.rə.tʃər/", "noun", "văn học", "B1"),
    ("local", "/ˈləʊ.kəl/", "adjective", "địa phương, cục bộ", "A2"),
    ("locate", "/ləʊˈkeɪt/", "verb", "định vị, đặt tại", "B1"),
    ("logic", "/ˈlɒdʒ.ɪk/", "noun", "tư duy logic", "B2"),
    ("lonely", "/ˈləʊn.li/", "adjective", "cô đơn", "A2"),
    ("lookup", "/ˈlʊk.ʌp/", "noun", "sự tra cứu", "B1"),
    ("machine", "/məˈʃiːn/", "noun", "máy móc", "A2"),
    ("magazine", "/ˌmæɡ.əˈziːn/", "noun", "tạp chí", "A2"),
    ("maintain", "/meɪnˈteɪn/", "verb", "duy trì, bảo trì", "B2"),
    ("major", "/ˈmeɪ.dʒər/", "adjective", "chủ yếu, quan trọng", "B1"),
    ("manage", "/ˈmæn.ɪdʒ/", "verb", "quản lý, xoay xở", "A2"),
    ("manager", "/ˈmæn.ɪ.dʒər/", "noun", "người quản lý", "A2"),
    ("manner", "/ˈmæn.ər/", "noun", "cách thức, thái độ", "B1"),
    ("market", "/ˈmɑː.kɪt/", "noun", "chợ, thị trường", "A1"),
    ("master", "/ˈmɑː.stər/", "verb", "làm chủ, thành thạo", "B2"),
    ("material", "/məˈtɪə.ri.əl/", "noun", "vật liệu, tài liệu", "B1"),
    ("matter", "/ˈmæt.ər/", "noun", "vấn đề, vật chất", "A2"),
    ("meaning", "/ˈmiː.nɪŋ/", "noun", "ý nghĩa", "A2"),
    ("measure", "/ˈmeʒ.ər/", "verb", "đo lường", "B1"),
    ("media", "/ˈmiː.di.ə/", "noun", "truyền thông", "B1"),
    ("medical", "/ˈmed.ɪ.kəl/", "adjective", "thuộc y tế", "B1"),
    ("meeting", "/ˈmiː.tɪŋ/", "noun", "cuộc họp", "A1"),
    ("member", "/ˈmem.bər/", "noun", "thành viên", "A2"),
    ("memory", "/ˈmem.ər.i/", "noun", "trí nhớ, kỷ niệm", "A2"),
    ("mention", "/ˈmen.ʃən/", "verb", "đề cập, nhắc đến", "B1"),
    ("message", "/ˈmes.ɪdʒ/", "noun", "tin nhắn, thông điệp", "A1"),
    ("method", "/ˈmeθ.əd/", "noun", "phương pháp", "B1"),
    ("minute", "/ˈmɪn.ɪt/", "noun", "phút", "A1"),
    ("mirror", "/ˈmɪr.ər/", "noun", "cái gương", "A2"),
    ("mission", "/ˈmɪʃ.ən/", "noun", "sứ mệnh, nhiệm vụ", "B2"),
    ("mistake", "/mɪˈsteɪk/", "noun", "lỗi lầm, sai sót", "A2"),
    ("mobile", "/ˈməʊ.baɪl/", "adjective", "di động", "A1"),
    ("modern", "/ˈmɒd.ən/", "adjective", "hiện đại", "A2"),
    ("moment", "/ˈməʊ.mənt/", "noun", "khoảnh khắc", "A2"),
    ("money", "/ˈmʌn.i/", "noun", "tiền bạc", "A1"),
    ("monitor", "/ˈmɒn.ɪ.tər/", "verb", "theo dõi, giám sát", "B2"),
    ("month", "/mʌnθ/", "noun", "tháng", "A1"),
    ("morning", "/ˈmɔː.nɪŋ/", "noun", "buổi sáng", "A1"),
    ("motivate", "/ˈməʊ.tɪ.veɪt/", "verb", "tạo động lực", "B2"),
    ("mountain", "/ˈmaʊn.tɪn/", "noun", "ngọn núi", "A1"),
    ("movie", "/ˈmuː.vi/", "noun", "bộ phim", "A1"),
    ("museum", "/mjuːˈziː.əm/", "noun", "bảo tàng", "A1"),
    ("music", "/ˈmjuː.zɪk/", "noun", "âm nhạc", "A1"),
    ("mystery", "/ˈmɪs.tər.i/", "noun", "bí ẩn", "B1"),
    ("narrow", "/ˈnær.əʊ/", "adjective", "hẹp", "B1"),
    ("nation", "/ˈneɪ.ʃən/", "noun", "quốc gia", "B1"),
    ("native", "/ˈneɪ.tɪv/", "adjective", "bản xứ, bản địa", "B1"),
    ("natural", "/ˈnætʃ.ər.əl/", "adjective", "tự nhiên", "A2"),
    ("nature", "/ˈneɪ.tʃər/", "noun", "thiên nhiên, bản chất", "A2"),
    ("necessary", "/ˈnes.ə.ser.i/", "adjective", "cần thiết", "A2"),
    ("negative", "/ˈneɡ.ə.tɪv/", "adjective", "tiêu cực, phủ định", "B1"),
    ("neighbor", "/ˈneɪ.bər/", "noun", "hàng xóm", "A2"),
    ("network", "/ˈnet.wɜːk/", "noun", "mạng lưới", "B1"),
    ("neutral", "/ˈnjuː.trəl/", "adjective", "trung lập", "B2"),
    ("never", "/ˈnev.ər/", "adverb", "không bao giờ", "A1"),
    ("newspaper", "/ˈnjuːzˌpeɪ.pər/", "noun", "báo giấy", "A1"),
    ("normal", "/ˈnɔː.məl/", "adjective", "bình thường", "A2"),
    ("notebook", "/ˈnəʊt.bʊk/", "noun", "sổ tay, vở ghi chép", "A1"),
    ("notice", "/ˈnəʊ.tɪs/", "verb", "nhận thấy, chú ý", "A2"),
    ("novel", "/ˈnɒv.əl/", "noun", "tiểu thuyết", "B1"),
    ("number", "/ˈnʌm.bər/", "noun", "con số", "A1"),
    ("object", "/ˈɒb.dʒɪkt/", "noun", "đồ vật; mục tiêu", "A2"),
    ("observe", "/əbˈzɜːv/", "verb", "quan sát", "B2"),
    ("obtain", "/əbˈteɪn/", "verb", "đạt được, thu được", "B2"),
    ("obvious", "/ˈɒb.vi.əs/", "adjective", "rõ ràng, hiển nhiên", "B1"),
    ("occasion", "/əˈkeɪ.ʒən/", "noun", "dịp, cơ hội", "B1"),
    ("occur", "/əˈkɜːr/", "verb", "xảy ra, xuất hiện", "B2"),
    ("ocean", "/ˈəʊ.ʃən/", "noun", "đại dương", "A2"),
    ("offer", "/ˈɒf.ər/", "verb", "đề nghị, cung cấp", "A2"),
    ("office", "/ˈɒf.ɪs/", "noun", "văn phòng", "A1"),
    ("official", "/əˈfɪʃ.əl/", "adjective", "chính thức", "B1"),
    ("often", "/ˈɒf.ən/", "adverb", "thường xuyên", "A1"),
    ("online", "/ˌɒnˈlaɪn/", "adjective", "trực tuyến", "A1"),
    ("open", "/ˈəʊ.pən/", "verb", "mở ra", "A1"),
    ("operate", "/ˈɒp.ər.eɪt/", "verb", "vận hành, hoạt động", "B2"),
    ("opinion", "/əˈpɪn.jən/", "noun", "ý kiến, quan điểm", "A2"),
    ("option", "/ˈɒp.ʃən/", "noun", "tùy chọn, phương án", "B1"),
    ("orange", "/ˈɒr.ɪndʒ/", "noun", "quả cam; màu cam", "A1"),
    ("order", "/ˈɔː.dər/", "noun", "đơn hàng; thứ tự", "A2"),
    ("ordinary", "/ˈɔː.dən.ər.i/", "adjective", "bình thường, thông thường", "B1"),
    ("organize", "/ˈɔː.ɡən.aɪz/", "verb", "tổ chức, sắp xếp", "A2"),
    ("origin", "/ˈɒr.ɪ.dʒɪn/", "noun", "nguồn gốc", "B2"),
    ("outcome", "/ˈaʊt.kʌm/", "noun", "kết quả, đầu ra", "B2"),
    ("outside", "/ˌaʊtˈsaɪd/", "adverb", "bên ngoài", "A1"),
    ("overall", "/ˌəʊ.vərˈɔːl/", "adjective", "tổng thể, nhìn chung", "B2"),
    ("overcome", "/ˌəʊ.vəˈkʌm/", "verb", "vượt qua", "B2"),
    ("package", "/ˈpæk.ɪdʒ/", "noun", "gói hàng, gói phần mềm", "B1"),
    ("page", "/peɪdʒ/", "noun", "trang sách", "A1"),
    ("pain", "/peɪn/", "noun", "nỗi đau", "A2"),
    ("paint", "/peɪnt/", "verb", "sơn, vẽ", "A1"),
    ("paper", "/ˈpeɪ.pər/", "noun", "giấy", "A1"),
    ("paragraph", "/ˈpær.ə.ɡrɑːf/", "noun", "đoạn văn", "B1"),
    ("parent", "/ˈpeə.rənt/", "noun", "cha/mẹ", "A1"),
    ("park", "/pɑːk/", "noun", "công viên", "A1"),
    ("participate", "/pɑːˈtɪs.ɪ.peɪt/", "verb", "tham gia", "B1"),
    ("particular", "/pəˈtɪk.jə.lər/", "adjective", "cụ thể, đặc biệt", "B1"),
    ("partner", "/ˈpɑːt.nər/", "noun", "đối tác, bạn đồng hành", "B1"),
    ("party", "/ˈpɑː.ti/", "noun", "bữa tiệc; đảng phái", "A1"),
    ("passenger", "/ˈpæs.ən.dʒər/", "noun", "hành khách", "A2"),
    ("passport", "/ˈpɑːs.pɔːt/", "noun", "hộ chiếu", "A2"),
    ("patient", "/ˈpeɪ.ʃənt/", "adjective", "kiên nhẫn", "B1"),
    ("pattern", "/ˈpæt.ən/", "noun", "mẫu hình, hoa văn", "B1"),
    ("pause", "/pɔːz/", "verb", "tạm dừng", "B1"),
    ("peace", "/piːs/", "noun", "hòa bình, sự bình yên", "A2"),
    ("people", "/ˈpiː.pəl/", "noun", "mọi người", "A1"),
    ("percent", "/pəˈsent/", "noun", "phần trăm", "A2"),
    ("perfect", "/ˈpɜː.fekt/", "adjective", "hoàn hảo", "A2"),
    ("perform", "/pəˈfɔːm/", "verb", "biểu diễn, thực hiện", "B1"),
    ("perhaps", "/pəˈhæps/", "adverb", "có lẽ", "A2"),
    ("period", "/ˈpɪə.ri.əd/", "noun", "giai đoạn, thời kỳ", "B1"),
    ("permanent", "/ˈpɜː.mə.nənt/", "adjective", "vĩnh viễn, lâu dài", "B2"),
    ("permission", "/pəˈmɪʃ.ən/", "noun", "sự cho phép", "B1"),
    ("person", "/ˈpɜː.sən/", "noun", "con người, cá nhân", "A1"),
    ("personal", "/ˈpɜː.sən.əl/", "adjective", "cá nhân", "A2"),
    ("perspective", "/pəˈspek.tɪv/", "noun", "góc nhìn, quan điểm", "B2"),
    ("phone", "/fəʊn/", "noun", "điện thoại", "A1"),
    ("photo", "/ˈfəʊ.təʊ/", "noun", "bức ảnh", "A1"),
    ("phrase", "/freɪz/", "noun", "cụm từ", "A2"),
    ("physical", "/ˈfɪz.ɪ.kəl/", "adjective", "thuộc thể chất, vật lý", "B1"),
    ("picture", "/ˈpɪk.tʃər/", "noun", "bức tranh, bức ảnh", "A1"),
    ("piece", "/piːs/", "noun", "mảnh, miếng", "A2"),
    ("pilot", "/ˈpaɪ.lət/", "noun", "phi công", "A2"),
    ("place", "/pleɪs/", "noun", "địa điểm, nơi chốn", "A1"),
    ("plan", "/plæn/", "noun", "kế hoạch", "A1"),
    ("planet", "/ˈplæn.ɪt/", "noun", "hành tinh", "A2"),
    ("plant", "/plɑːnt/", "noun", "thực vật, cây cối", "A1"),
    ("platform", "/ˈplæt.fɔːm/", "noun", "nền tảng; sân ga", "B2"),
    ("player", "/ˈpleɪ.ər/", "noun", "người chơi, cầu thủ", "A1"),
    ("pleasant", "/ˈplez.ənt/", "adjective", "dễ chịu, thú vị", "B1"),
    ("please", "/pliːz/", "adverb", "làm ơn, vui lòng", "A1"),
    ("pleasure", "/ˈpleʒ.ər/", "noun", "niềm vui, sự hài lòng", "B1"),
    ("plenty", "/ˈplen.ti/", "pronoun", "nhiều, dồi dào", "B1"),
    ("podcast", "/ˈpɒd.kɑːst/", "noun", "tệp âm thanh podcast", "B1"),
    ("poem", "/ˈpəʊ.ɪm/", "noun", "bài thơ", "B1"),
    ("point", "/pɔɪnt/", "noun", "điểm, luận điểm", "A2"),
    ("police", "/pəˈliːs/", "noun", "cảnh sát", "A1"),
    ("policy", "/ˈpɒl.ə.si/", "noun", "chính sách", "B2"),
    ("polite", "/pəˈlaɪt/", "adjective", "lịch sự", "A2"),
    ("pollution", "/pəˈluː.ʃən/", "noun", "sự ô nhiễm", "B1"),
    ("popular", "/ˈpɒp.jə.lər/", "adjective", "phổ biến, được yêu thích", "A2"),
    ("population", "/ˌpɒp.jəˈleɪ.ʃən/", "noun", "dân số", "B1"),
    ("position", "/pəˈzɪʃ.ən/", "noun", "vị trí, chức vụ", "B1"),
    ("positive", "/ˈpɒz.ə.tɪv/", "adjective", "tích cực", "B1"),
    ("possible", "/ˈpɒs.ə.bəl/", "adjective", "có thể xảy ra", "A2"),
    ("potential", "/pəˈten.ʃəl/", "adjective", "tiềm năng", "B2"),
    ("power", "/paʊər/", "noun", "sức mạnh, quyền lực, điện năng", "B1"),
    ("practical", "/ˈpræk.tɪ.kəl/", "adjective", "thực tế, thiết thực", "B1"),
    ("practice", "/ˈpræk.tɪs/", "verb", "luyện tập, thực hành", "A1"),
    ("praise", "/preɪz/", "verb", "khen ngợi", "B2"),
    ("predict", "/prɪˈdɪkt/", "verb", "dự đoán", "B1"),
    ("prefer", "/prɪˈfɜːr/", "verb", "thích hơn", "A2"),
    ("prepare", "/prɪˈpeər/", "verb", "chuẩn bị", "A2"),
    ("present", "/ˈprez.ənt/", "noun", "hiện tại; món quà", "A1"),
    ("preserve", "/prɪˈzɜːv/", "verb", "bảo tồn, giữ gìn", "B2"),
    ("pressure", "/ˈpreʃ.ər/", "noun", "áp lực", "B1"),
    ("prevent", "/prɪˈvent/", "verb", "ngăn ngừa, phòng tránh", "B1"),
    ("previous", "/ˈpriː.vi.əs/", "adjective", "trước đó", "B1"),
    ("price", "/praɪs/", "noun", "giá cả", "A1"),
    ("primary", "/ˈpraɪ.mər.i/", "adjective", "chính, cơ bản, tiểu học", "B1"),
    ("principle", "/ˈprɪn.sə.pəl/", "noun", "nguyên tắc, nguyên lý", "B2"),
    ("priority", "/praɪˈɒr.ə.ti/", "noun", "sự ưu tiên", "B2"),
    ("private", "/ˈpraɪ.vət/", "adjective", "riêng tư", "B1"),
    ("problem", "/ˈprɒb.ləm/", "noun", "vấn đề", "A1"),
    ("process", "/ˈprəʊ.ses/", "noun", "quy trình, quá trình", "B1"),
    ("produce", "/prəˈdjuːs/", "verb", "sản xuất, tạo ra", "B1"),
    ("product", "/ˈprɒd.ʌkt/", "noun", "sản phẩm", "A2"),
    ("profession", "/prəˈfeʃ.ən/", "noun", "nghề nghiệp chuyên môn", "B1"),
    ("profile", "/ˈprəʊ.faɪl/", "noun", "hồ sơ, tiểu sử", "B1"),
    ("profit", "/ˈprɒf.ɪt/", "noun", "lợi nhuận", "B2"),
    ("program", "/ˈprəʊ.ɡræm/", "noun", "chương trình", "A2"),
    ("progress", "/ˈprəʊ.ɡres/", "noun", "sự tiến bộ", "B1"),
    ("project", "/ˈprɒdʒ.ekt/", "noun", "dự án", "A2"),
    ("promise", "/ˈprɒm.ɪs/", "verb", "hứa hẹn", "A2"),
    ("promote", "/prəˈməʊt/", "verb", "thúc đẩy, thăng chức", "B2"),
    ("protect", "/prəˈtekt/", "verb", "bảo vệ", "B1"),
    ("proud", "/praʊd/", "adjective", "tự hào", "B1"),
    ("prove", "/pruːv/", "verb", "chứng minh", "B1"),
    ("provide", "/prəˈvaɪd/", "verb", "cung cấp", "B1"),
    ("public", "/ˈpʌb.lɪk/", "adjective", "công cộng, công khai", "A2"),
    ("publish", "/ˈpʌb.lɪʃ/", "verb", "xuất bản, công bố", "B1"),
    ("purpose", "/ˈpɜː.pəs/", "noun", "mục đích", "B1"),
    ("pursue", "/pəˈsjuː/", "verb", "theo đuổi", "B2"),
    ("quality", "/ˈkwɒl.ə.ti/", "noun", "chất lượng", "B1"),
    ("quantity", "/ˈkwɒn.tə.ti/", "noun", "số lượng", "B1"),
    ("quarter", "/ˈkwɔː.tər/", "noun", "một phần tư; quý", "A2"),
    ("question", "/ˈkwes.tʃən/", "noun", "câu hỏi", "A1"),
    ("quick", "/kwɪk/", "adjective", "nhanh chóng", "A1"),
    ("quiet", "/ˈkwaɪ.ət/", "adjective", "yên tĩnh", "A1"),
    ("quite", "/kwaɪt/", "adverb", "khá, hoàn toàn", "A2"),
    ("radio", "/ˈreɪ.di.əʊ/", "noun", "đài phát thanh", "A1"),
    ("raise", "/reɪz/", "verb", "nâng lên, nuôi dưỡng", "A2"),
    ("random", "/ˈræn.dəm/", "adjective", "ngẫu nhiên", "B2"),
    ("range", "/reɪndʒ/", "noun", "phạm vi, dãy", "B1"),
    ("rapid", "/ˈræp.ɪd/", "adjective", "nhanh chóng, mau lẹ", "B2"),
    ("rare", "/reər/", "adjective", "hiếm có", "B1"),
    ("rather", "/ˈrɑː.ðər/", "adverb", "khá là; thà rằng", "B1"),
    ("react", "/riˈækt/", "verb", "phản ứng", "B2"),
    ("reader", "/ˈriː.dər/", "noun", "độc giả", "A2"),
    ("ready", "/ˈred.i/", "adjective", "sẵn sàng", "A1"),
    ("realize", "/ˈrɪə.laɪz/", "verb", "nhận ra", "B1"),
    ("reason", "/ˈriː.zən/", "noun", "lý do", "A2"),
    ("receive", "/rɪˈsiːv/", "verb", "nhận được", "A2"),
    ("recent", "/ˈriː.sənt/", "adjective", "gần đây", "B1"),
    ("recognize", "/ˈrek.əɡ.naɪz/", "verb", "nhận ra, công nhận", "B1"),
    ("recommend", "/ˌrek.əˈmend/", "verb", "gợi ý, khuyên dùng", "B1"),
    ("record", "/ˈrek.ɔːd/", "noun", "bản ghi âm, kỷ lục", "B1"),
    ("recover", "/rɪˈkʌv.ər/", "verb", "hồi phục", "B2"),
    ("reduce", "/rɪˈdjuːs/", "verb", "giảm bớt", "B1"),
    ("refer", "/rɪˈfɜːr/", "verb", "tham khảo, đề cập đến", "B2"),
    ("reflect", "/rɪˈflekt/", "verb", "phản ánh, suy ngẫm", "B2"),
    ("refresh", "/rɪˈfreʃ/", "verb", "làm mới, làm sảng khoái", "B2"),
    ("refuse", "/rɪˈfjuːz/", "verb", "từ chối", "B1"),
    ("regard", "/rɪˈɡɑːd/", "verb", "coi như, đánh giá", "B2"),
    ("region", "/ˈriː.dʒən/", "noun", "vùng, khu vực", "B1"),
    ("register", "/ˈredʒ.ɪ.stər/", "verb", "đăng ký", "B1"),
    ("regular", "/ˈreɡ.jə.lər/", "adjective", "đều đặn, thường xuyên", "B1"),
    ("reject", "/rɪˈdʒekt/", "verb", "bác bỏ, từ chối", "B2"),
    ("relate", "/rɪˈleɪt/", "verb", "liên quan, kể lại", "B1"),
    ("relax", "/rɪˈlæks/", "verb", "thư giãn", "A2"),
    ("release", "/rɪˈliːs/", "verb", "phát hành, giải phóng", "B2"),
    ("relevant", "/ˈrel.ə.vənt/", "adjective", "liên quan, thích đáng", "B2"),
    ("reliable", "/rɪˈlaɪ.ə.bəl/", "adjective", "đáng tin cậy", "B1"),
    ("relief", "/rɪˈliːf/", "noun", "sự nhẹ nhõm", "B2"),
    ("remain", "/rɪˈmeɪn/", "verb", "duy trì, vẫn còn", "B1"),
    ("remember", "/rɪˈmem.bər/", "verb", "nhớ", "A1"),
    ("remind", "/rɪˈmaɪnd/", "verb", "nhắc nhở", "B1"),
    ("remote", "/rɪˈməʊt/", "adjective", "xa xôi, từ xa", "B1"),
    ("remove", "/rɪˈmuːv/", "verb", "xóa bỏ, tháo ra", "B1"),
    ("repair", "/rɪˈpeər/", "verb", "sửa chữa", "A2"),
    ("repeat", "/rɪˈpiːt/", "verb", "lặp lại", "A1"),
    ("replace", "/rɪˈpleɪs/", "verb", "thay thế", "B1"),
    ("reply", "/rɪˈplaɪ/", "verb", "trả lời, hồi âm", "A2"),
    ("report", "/rɪˈpɔːt/", "noun", "báo cáo", "A2"),
    ("represent", "/ˌrep.rɪˈzent/", "verb", "đại diện cho", "B2"),
    ("request", "/rɪˈkwest/", "noun", "yêu cầu, đề nghị", "B1"),
    ("require", "/rɪˈkwaɪər/", "verb", "đòi hỏi, yêu cầu", "B1"),
    ("research", "/rɪˈsɜːtʃ/", "noun", "nghiên cứu", "B1"),
    ("reserve", "/rɪˈzɜːv/", "verb", "đặt trước, dự trữ", "B1"),
    ("resolve", "/rɪˈzɒlv/", "verb", "giải quyết, quyết tâm", "B2"),
    ("resource", "/rɪˈzɔːs/", "noun", "tài nguyên, nguồn lực", "B2"),
    ("respect", "/rɪˈspekt/", "noun", "sự tôn trọng", "B1"),
    ("respond", "/rɪˈspɒnd/", "verb", "phản hồi, đáp lại", "B1"),
    ("responsible", "/rɪˈspɒn.sə.bəl/", "adjective", "có trách nhiệm", "B1"),
    ("restaurant", "/ˈres.trɒnt/", "noun", "nhà hàng", "A1"),
    ("result", "/rɪˈzʌlt/", "noun", "kết quả", "A2"),
    ("retail", "/ˈriː.teɪl/", "noun", "bán lẻ", "B2"),
    ("return", "/rɪˈtɜːn/", "verb", "trở lại, trả lại", "A2"),
    ("reveal", "/rɪˈviːl/", "verb", "tiết lộ", "B2"),
    ("review", "/rɪˈvjuː/", "verb", "ôn tập, đánh giá", "B1"),
    ("reward", "/rɪˈwɔːd/", "noun", "phần thưởng", "B1"),
    ("rhythm", "/ˈrɪð.əm/", "noun", "nhịp điệu", "B2"),
    ("right", "/raɪt/", "adjective", "đúng; bên phải", "A1"),
    ("river", "/ˈrɪv.ər/", "noun", "dòng sông", "A1"),
    ("road", "/rəʊd/", "noun", "con đường", "A1"),
    ("role", "/rəʊl/", "noun", "vai trò", "B1"),
    ("routine", "/ruːˈtiːn/", "noun", "thói quen hằng ngày", "B1"),
    ("rule", "/ruːl/", "noun", "quy tắc, luật lệ", "A2"),
    ("salary", "/ˈsæl.ər.i/", "noun", "tiền lương", "B1"),
    ("sample", "/ˈsɑːm.pəl/", "noun", "mẫu thử", "B1"),
    ("satisfy", "/ˈsæt.ɪs.faɪ/", "verb", "làm hài lòng, đáp ứng", "B2"),
    ("save", "/seɪv/", "verb", "lưu lại, tiết kiệm, cứu", "A2"),
    ("school", "/skuːl/", "noun", "trường học", "A1"),
    ("science", "/ˈsaɪ.əns/", "noun", "khoa học", "A2"),
    ("screen", "/skriːn/", "noun", "màn hình", "A2"),
    ("search", "/sɜːtʃ/", "verb", "tìm kiếm, tra cứu", "A2"),
    ("season", "/ˈsiː.zən/", "noun", "mùa", "A1"),
    ("second", "/ˈsek.ənd/", "noun", "giây; thứ hai", "A1"),
    ("secret", "/ˈsiː.krət/", "noun", "bí mật", "A2"),
    ("section", "/ˈsek.ʃən/", "noun", "phần, mục", "B1"),
    ("secure", "/sɪˈkjʊər/", "adjective", "an toàn, bảo mật", "B2"),
    ("select", "/sɪˈlekt/", "verb", "lựa chọn", "B1"),
    ("sentence", "/ˈsen.təns/", "noun", "câu văn", "A1"),
    ("separate", "/ˈsep.ər.ət/", "adjective", "riêng biệt", "B1"),
    ("series", "/ˈsɪə.riːz/", "noun", "chuỗi, loạt", "B1"),
    ("serious", "/ˈsɪə.ri.əs/", "adjective", "nghiêm túc, nghiêm trọng", "A2"),
    ("service", "/ˈsɜː.vɪs/", "noun", "dịch vụ", "A2"),
    ("session", "/ˈseʃ.ən/", "noun", "phiên làm việc, buổi học", "B2"),
    ("several", "/ˈsev.ər.əl/", "determiner", "một vài", "A2"),
    ("shadow", "/ˈʃæd.əʊ/", "noun", "cái bóng", "B1"),
    ("share", "/ʃeər/", "verb", "chia sẻ", "A2"),
    ("simple", "/ˈsɪm.pəl/", "adjective", "đơn giản", "A2"),
    ("simply", "/ˈsɪm.pli/", "adverb", "đơn giản là", "B1"),
    ("sincere", "/sɪnˈsɪər/", "adjective", "chân thành", "B2"),
    ("single", "/ˈsɪŋ.ɡəl/", "adjective", "đơn lẻ, độc thân", "A2"),
    ("sister", "/ˈsɪs.tər/", "noun", "chị/em gái", "A1"),
    ("situation", "/ˌsɪtʃ.uˈeɪ.ʃən/", "noun", "tình huống, hoàn cảnh", "B1"),
    ("skill", "/skɪl/", "noun", "kỹ năng", "A2"),
    ("sleep", "/sliːp/", "verb", "ngủ", "A1"),
    ("smart", "/smɑːt/", "adjective", "thông minh, gọn gàng", "A2"),
    ("smile", "/smaɪl/", "verb", "mỉm cười", "A1"),
    ("smooth", "/smuːð/", "adjective", "mượt mà, trôi chảy", "B1"),
    ("social", "/ˈsəʊ.ʃəl/", "adjective", "thuộc xã hội", "A2"),
    ("society", "/səˈsaɪ.ə.ti/", "noun", "xã hội", "B1"),
    ("software", "/ˈsɒft.weər/", "noun", "phần mềm", "B1"),
    ("solution", "/səˈluː.ʃən/", "noun", "giải pháp", "B1"),
    ("solve", "/sɒlv/", "verb", "giải quyết", "B1"),
    ("someone", "/ˈsʌm.wʌn/", "pronoun", "ai đó", "A1"),
    ("source", "/sɔːs/", "noun", "nguồn", "B1"),
    ("speaker", "/ˈspiː.kər/", "noun", "người nói, loa", "A2"),
    ("special", "/ˈspeʃ.əl/", "adjective", "đặc biệt", "A1"),
    ("specific", "/spəˈsɪf.ɪk/", "adjective", "cụ thể, đặc thù", "B1"),
    ("speech", "/spiːtʃ/", "noun", "bài phát biểu, lời nói", "B1"),
    ("speed", "/spiːd/", "noun", "tốc độ", "A2"),
    ("spend", "/spend/", "verb", "tiêu xài, dành thời gian", "A1"),
    ("spirit", "/ˈspɪr.ɪt/", "noun", "tinh thần", "B1"),
    ("standard", "/ˈstæn.dəd/", "noun", "tiêu chuẩn", "B1"),
    ("station", "/ˈsteɪ.ʃən/", "noun", "nhà ga, trạm", "A1"),
    ("status", "/ˈsteɪ.təs/", "noun", "trạng thái, địa vị", "B2"),
    ("steady", "/ˈsted.i/", "adjective", "ổn định, đều đặn", "B2"),
    ("store", "/stɔːr/", "noun", "cửa hàng", "A1"),
    ("story", "/ˈstɔː.ri/", "noun", "câu chuyện", "A1"),
    ("street", "/striːt/", "noun", "đường phố", "A1"),
    ("strength", "/streŋθ/", "noun", "sức mạnh, điểm mạnh", "B1"),
    ("stress", "/stres/", "noun", "căng thẳng; trọng âm", "B1"),
    ("structure", "/ˈstrʌk.tʃər/", "noun", "cấu trúc", "B2"),
    ("student", "/ˈstjuː.dənt/", "noun", "học sinh, sinh viên", "A1"),
    ("study", "/ˈstʌd.i/", "verb", "học tập, nghiên cứu", "A1"),
    ("style", "/staɪl/", "noun", "phong cách", "A2"),
    ("subject", "/ˈsʌb.dʒekt/", "noun", "chủ đề, môn học", "A2"),
    ("submit", "/səbˈmɪt/", "verb", "nộp, đệ trình", "B2"),
    ("success", "/səkˈses/", "noun", "sự thành công", "A2"),
    ("sudden", "/ˈsʌd.ən/", "adjective", "đột ngột", "B1"),
    ("suffer", "/ˈsʌf.ər/", "verb", "chịu đựng, mắc phải", "B1"),
    ("suggest", "/səˈdʒest/", "verb", "gợi ý, đề xuất", "A2"),
    ("summary", "/ˈsʌm.ər.i/", "noun", "bản tóm tắt", "B1"),
    ("summer", "/ˈsʌm.ər/", "noun", "mùa hè", "A1"),
    ("support", "/səˈpɔːt/", "verb", "hỗ trợ, ủng hộ", "B1"),
    ("suppose", "/səˈpəʊz/", "verb", "cho rằng, giả sử", "B1"),
    ("surface", "/ˈsɜː.fɪs/", "noun", "bề mặt", "B1"),
    ("surprise", "/səˈpraɪz/", "noun", "sự ngạc nhiên", "A2"),
    ("survive", "/səˈvaɪv/", "verb", "sống sót, tồn tại", "B1"),
    ("system", "/ˈsɪs.təm/", "noun", "hệ thống", "B1"),
    ("table", "/ˈteɪ.bəl/", "noun", "cái bàn; bảng biểu", "A1"),
    ("talent", "/ˈtæl.ənt/", "noun", "tài năng", "B1"),
    ("target", "/ˈtɑː.ɡɪt/", "noun", "mục tiêu", "B1"),
    ("task", "/tɑːsk/", "noun", "nhiệm vụ, công việc", "A2"),
    ("teacher", "/ˈtiː.tʃər/", "noun", "giáo viên", "A1"),
    ("team", "/tiːm/", "noun", "đội, nhóm", "A1"),
    ("technique", "/tekˈniːk/", "noun", "kỹ thuật, thủ thuật", "B1"),
    ("technology", "/tekˈnɒl.ə.dʒi/", "noun", "công nghệ", "A2"),
    ("television", "/ˈtel.ɪ.vɪʒ.ən/", "noun", "ti vi, truyền hình", "A1"),
    ("temperature", "/ˈtem.prə.tʃər/", "noun", "nhiệt độ", "A2"),
    ("temporary", "/ˈtem.pər.ər.i/", "adjective", "tạm thời", "B2"),
    ("theory", "/ˈθɪə.ri/", "noun", "lý thuyết", "B2"),
    ("therefore", "/ˈðeə.fɔːr/", "adverb", "do đó, vì vậy", "B1"),
    ("thought", "/θɔːt/", "noun", "suy nghĩ, ý nghĩ", "A2"),
    ("ticket", "/ˈtɪk.ɪt/", "noun", "vé", "A1"),
    ("today", "/təˈdeɪ/", "adverb", "hôm nay", "A1"),
    ("together", "/təˈɡeð.ər/", "adverb", "cùng nhau", "A1"),
    ("tomorrow", "/təˈmɒr.əʊ/", "adverb", "ngày mai", "A1"),
    ("topic", "/ˈtɒp.ɪk/", "noun", "chủ đề", "A2"),
    ("total", "/ˈtəʊ.təl/", "adjective", "tổng cộng, toàn bộ", "B1"),
    ("toward", "/təˈwɔːd/", "preposition", "hướng về phía", "B1"),
    ("tradition", "/trəˈdɪʃ.ən/", "noun", "truyền thống", "B1"),
    ("traffic", "/ˈtræf.ɪk/", "noun", "giao thông", "A2"),
    ("train", "/treɪn/", "noun", "tàu hỏa; huấn luyện", "A1"),
    ("transfer", "/trænsˈfɜːr/", "verb", "chuyển nhượng, chuyển tiếp", "B2"),
    ("travel", "/ˈtræv.əl/", "verb", "du lịch, di chuyển", "A1"),
    ("treat", "/triːt/", "verb", "đối xử; điều trị", "B1"),
    ("trend", "/trend/", "noun", "xu hướng", "B1"),
    ("trouble", "/ˈtrʌb.əl/", "noun", "rắc rối, vấn đề", "A2"),
    ("trust", "/trʌst/", "verb", "tin tưởng", "B1"),
    ("truth", "/truːθ/", "noun", "sự thật", "B1"),
    ("understand", "/ˌʌn.dəˈstænd/", "verb", "hiểu", "A1"),
    ("unique", "/juːˈniːk/", "adjective", "độc đáo, duy nhất", "B2"),
    ("university", "/ˌjuː.nɪˈvɜː.sə.ti/", "noun", "trường đại học", "A1"),
    ("unless", "/ənˈles/", "conjunction", "trừ khi", "B1"),
    ("until", "/ənˈtɪl/", "preposition", "cho đến khi", "A1"),
    ("update", "/ʌpˈdeɪt/", "verb", "cập nhật", "B1"),
    ("useful", "/ˈjuːs.fəl/", "adjective", "hữu ích", "A2"),
    ("usual", "/ˈjuː.ʒu.əl/", "adjective", "thông thường", "A2"),
    ("vacation", "/veɪˈkeɪ.ʃən/", "noun", "kỳ nghỉ", "A2"),
    ("value", "/ˈvæl.juː/", "noun", "giá trị", "B1"),
    ("variety", "/vəˈraɪ.ə.ti/", "noun", "sự đa dạng", "B1"),
    ("various", "/ˈveə.ri.əs/", "adjective", "đa dạng, khác nhau", "B1"),
    ("vehicle", "/ˈviː.ə.kəl/", "noun", "phương tiện giao thông", "B1"),
    ("version", "/ˈvɜː.ʃən/", "noun", "phiên bản", "B1"),
    ("victory", "/ˈvɪk.tər.i/", "noun", "chiến thắng", "B1"),
    ("video", "/ˈvɪd.i.əʊ/", "noun", "video", "A1"),
    ("village", "/ˈvɪl.ɪdʒ/", "noun", "ngôi làng", "A2"),
    ("virtual", "/ˈvɜː.tʃu.əl/", "adjective", "ảo, trực tuyến", "B2"),
    ("vision", "/ˈvɪʒ.ən/", "noun", "tầm nhìn, thị lực", "B2"),
    ("visit", "/ˈvɪz.ɪt/", "verb", "thăm viếng", "A1"),
    ("voice", "/vɔɪs/", "noun", "giọng nói", "A2"),
    ("volume", "/ˈvɒl.juːm/", "noun", "âm lượng; tập sách", "B1"),
    ("volunteer", "/ˌvɒl.ənˈtɪər/", "noun", "tình nguyện viên", "B1"),
    ("wait", "/weɪt/", "verb", "chờ đợi", "A1"),
    ("water", "/ˈwɔː.tər/", "noun", "nước", "A1"),
    ("wealth", "/welθ/", "noun", "sự giàu có", "B2"),
    ("weather", "/ˈweð.ər/", "noun", "thời tiết", "A1"),
    ("website", "/ˈweb.saɪt/", "noun", "trang web", "A1"),
    ("weekend", "/ˌwiːkˈend/", "noun", "cuối tuần", "A1"),
    ("welcome", "/ˈwel.kəm/", "verb", "chào đón", "A1"),
    ("window", "/ˈwɪn.dəʊ/", "noun", "cửa sổ", "A1"),
    ("winter", "/ˈwɪn.tər/", "noun", "mùa đông", "A1"),
    ("wisdom", "/ˈwɪz.dəm/", "noun", "sự khôn ngoan", "B2"),
    ("without", "/wɪˈðaʊt/", "preposition", "không có", "A1"),
    ("wonder", "/ˈwʌn.dər/", "verb", "tự hỏi, ngạc nhiên", "B1"),
    ("wonderful", "/ˈwʌn.də.fəl/", "adjective", "tuyệt vời", "A1"),
    ("world", "/wɜːld/", "noun", "thế giới", "A1"),
    ("worry", "/ˈwʌr.i/", "verb", "lo lắng", "A2"),
    ("worth", "/wɜːθ/", "adjective", "đáng giá", "B1"),
    ("write", "/raɪt/", "verb", "viết", "A1"),
    ("writer", "/ˈraɪ.tər/", "noun", "nhà văn, người viết", "A2"),
    ("yesterday", "/ˈjes.tə.deɪ/", "adverb", "hôm qua", "A1"),
    ("young", "/jʌŋ/", "adjective", "trẻ tuổi", "A1"),
]


def normalize_fvdp_pos(raw_pos: str, word: str) -> str:
    lowered = raw_pos.strip().lower()
    mappings = (
        ("thành ngữ", "idiom"),
        ("cụm động từ", "phrasal verb"),
        ("ngoại động từ", "verb"),
        ("nội động từ", "verb"),
        ("trợ động từ", "verb"),
        ("động từ", "verb"),
        ("danh từ", "noun"),
        ("tính từ", "adjective"),
        ("phó từ", "adverb"),
        ("trạng từ", "adverb"),
        ("giới từ", "preposition"),
        ("liên từ", "conjunction"),
        ("đại từ", "pronoun"),
        ("thán từ", "interjection"),
        ("mạo từ", "determiner"),
        ("số từ", "number"),
        ("tiền tố", "prefix"),
        ("hậu tố", "suffix"),
    )
    for vn_tag, en_pos in mappings:
        if vn_tag in lowered:
            return en_pos
    return "phrase" if " " in word else "noun"


def parse_fvdp(path: Path) -> list[dict[str, Any]]:
    """Parse Free Vietnamese Dictionary Project (.dict/.txt) entries."""
    entries: list[dict[str, Any]] = []
    if not path.exists():
        return entries

    current_word = ""
    current_ipa = ""
    current_pos = "noun"
    meanings: list[str] = []
    examples: list[dict[str, str]] = []
    collocations: list[dict[str, str]] = []

    def compute_fvdp_rank(word: str) -> int:
        if " " not in word and word.isalpha():
            return 5000 + min(len(word), 20) * 150
        return 15000 + min(len(word), 30) * 100

    def flush() -> None:
        if not current_word or current_word.startswith("00-database") or not meanings:
            return
        joined_vi = "; ".join(meanings[:4])
        if len(joined_vi) > 240:
            joined_vi = joined_vi[:237].rstrip(" ;,") + "..."
        entries.append(
            {
                "word": current_word,
                "ipa_uk": current_ipa,
                "ipa_us": current_ipa,
                "pos": current_pos,
                "meaning_vi": joined_vi,
                "examples": examples[:2],
                "synonyms": [],
                "collocations": collocations[:3],
                "cefr_level": None,
                "frequency_rank": compute_fvdp_rank(current_word),
            }
        )

    with path.open("r", encoding="utf-8", errors="ignore") as fh:
        for raw_line in fh:
            line = raw_line.strip()
            if not line:
                flush()
                current_word = ""
                current_ipa = ""
                current_pos = "noun"
                meanings = []
                examples = []
                collocations = []
                continue
            if line.startswith("@"):
                flush()
                meanings = []
                examples = []
                collocations = []
                match = re.match(r"^@([^/]+?)\s*(/[^/]+/)?$", line)
                if match:
                    current_word = re.sub(r"\s+", " ", match.group(1)).strip()
                    current_ipa = (match.group(2) or "").strip()
                else:
                    current_word = re.sub(r"\s+", " ", line[1:]).strip()
                    current_ipa = ""
                current_pos = normalize_fvdp_pos("", current_word)
            elif line.startswith("*"):
                if meanings:
                    flush()
                    meanings = []
                    examples = []
                current_pos = normalize_fvdp_pos(line[1:], current_word)
            elif line.startswith("-"):
                m = line[1:].strip()
                if m:
                    meanings.append(m)
            elif line.startswith("="):
                parts = line[1:].split("+", 1)
                en = parts[0].replace("_", " ").strip()
                vi = parts[1].strip() if len(parts) > 1 else ""
                if en:
                    examples.append({"en": en, "vi": vi})
            elif line.startswith("!"):
                idiom_text = line[1:].strip()
                if idiom_text:
                    collocations.append({"phrase": idiom_text, "meaningVi": ""})

    flush()
    return entries


def parse_wiktionary_jsonl(path: Path, limit: int = 50000) -> list[dict[str, Any]]:
    """Parse Wiktextract / Kaikki Wiktionary JSONL entries."""
    entries: list[dict[str, Any]] = []
    if not path.exists():
        return entries

    with path.open("r", encoding="utf-8") as fh:
        for idx, line in enumerate(fh):
            if idx >= limit:
                break
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            word = (obj.get("word") or "").strip()
            if not word:
                continue
            ipa_uk = ""
            ipa_us = ""
            for s in obj.get("sounds", []):
                ipa = s.get("ipa", "")
                if not ipa:
                    continue
                tags = s.get("tags", [])
                if "UK" in tags or "Received-Pronunciation" in tags:
                    ipa_uk = ipa_uk or ipa
                elif "US" in tags or "General-American" in tags:
                    ipa_us = ipa_us or ipa
                else:
                    ipa_uk = ipa_uk or ipa
                    ipa_us = ipa_us or ipa

            senses = obj.get("senses", [])
            glosses = []
            examples = []
            synonyms = []
            for sense in senses[:3]:
                for g in sense.get("glosses", []):
                    if g not in glosses:
                        glosses.append(g)
                for ex in sense.get("examples", [])[:2]:
                    txt = ex.get("text", "")
                    if txt:
                        examples.append({"en": txt, "vi": ""})
                for syn in sense.get("synonyms", [])[:5]:
                    w = syn.get("word", "")
                    if w and w not in synonyms:
                        synonyms.append(w)

            if not glosses:
                continue

            entries.append(
                {
                    "word": word,
                    "ipa_uk": ipa_uk,
                    "ipa_us": ipa_us or ipa_uk,
                    "pos": (obj.get("pos") or "noun").lower(),
                    "meaning_vi": "; ".join(glosses[:3]),
                    "examples": examples[:3],
                    "synonyms": synonyms[:5],
                    "collocations": [],
                    "cefr_level": None,
                    "frequency_rank": 5000 + idx,
                }
            )
    return entries


def build_seed_entries() -> list[dict[str, Any]]:
    entries: list[dict[str, Any]] = []
    seen: set[tuple[str, str]] = set()

    if SEED_DICT_PATH.exists():
        curated = json.loads(SEED_DICT_PATH.read_text(encoding="utf-8"))
        for item in curated:
            key = (item["word"].strip().lower(), item["pos"].strip().lower())
            seen.add(key)
            entries.append(item)

    for idx, (word, ipa, pos, meaning_vi, cefr) in enumerate(COMMON_WORDS, start=100):
        key = (word.lower(), pos.lower())
        if key in seen:
            continue
        seen.add(key)
        entries.append(
            {
                "word": word,
                "ipa_uk": ipa,
                "ipa_us": ipa,
                "pos": pos,
                "meaning_vi": meaning_vi,
                "examples": [
                    {
                        "en": f"Practice using '{word}' in a natural sentence.",
                        "vi": f"Hãy luyện đặt câu tự nhiên với từ '{word}'.",
                    }
                ],
                "synonyms": [],
                "collocations": [],
                "cefr_level": cefr,
                "frequency_rank": idx,
            }
        )
    return entries


def build_database(fvdp_path: Path | None, wiktionary_path: Path | None, use_default_fvdp: bool = True) -> None:
    OUTPUT_DB.parent.mkdir(parents=True, exist_ok=True)
    if OUTPUT_DB.exists():
        OUTPUT_DB.unlink()

    raw_entries = build_seed_entries()
    resolved_fvdp = fvdp_path or (DEFAULT_FVDP_PATH if use_default_fvdp and DEFAULT_FVDP_PATH.exists() else None)
    if resolved_fvdp:
        fvdp_entries = parse_fvdp(resolved_fvdp)
        print(f"Parsed {len(fvdp_entries):,} entries from FVDP: {resolved_fvdp}")
        raw_entries.extend(fvdp_entries)
    if wiktionary_path:
        raw_entries.extend(parse_wiktionary_jsonl(wiktionary_path))

    # Deduplicate by (word_normalized, pos), preserving earlier higher-priority seed entries
    all_entries: list[dict[str, Any]] = []
    seen_keys: set[tuple[str, str]] = set()
    for item in raw_entries:
        word = item.get("word", "").strip()
        pos = item.get("pos", "noun").strip().lower() or "noun"
        if not word:
            continue
        key = (word.lower(), pos)
        if key in seen_keys:
            continue
        seen_keys.add(key)
        item["word"] = word
        item["pos"] = pos
        all_entries.append(item)

    articles = (
        json.loads(SEED_ARTICLES_PATH.read_text(encoding="utf-8"))
        if SEED_ARTICLES_PATH.exists()
        else []
    )
    now_ms = int(time.time() * 1000)

    conn = sqlite3.connect(str(OUTPUT_DB))
    try:
        conn.execute("PRAGMA page_size = 4096")
        conn.execute("PRAGMA synchronous = OFF")
        conn.execute("PRAGMA journal_mode = MEMORY")
        conn.executescript(SCHEMA_SQL)

        # Drop AFTER_INSERT trigger during bulk load, rebuild FTS once, then restore trigger
        conn.execute("DROP TRIGGER IF EXISTS room_fts_content_sync_dictionary_fts_AFTER_INSERT")

        rows_to_insert = [
            (
                item["word"],
                item["word"].lower(),
                item.get("ipa_uk", ""),
                item.get("ipa_us", ""),
                item["pos"],
                item["meaning_vi"],
                json.dumps(item.get("examples", []), ensure_ascii=False),
                json.dumps(item.get("synonyms", []), ensure_ascii=False),
                json.dumps(item.get("collocations", []), ensure_ascii=False),
                item.get("cefr_level"),
                int(item.get("frequency_rank", 999999)),
            )
            for item in all_entries
        ]
        conn.executemany(
            """
            INSERT INTO dictionary_entries (
                word, word_normalized, ipa_uk, ipa_us, pos, meaning_vi,
                examples_json, synonyms_json, collocations_json, cefr_level, frequency_rank
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            rows_to_insert,
        )

        conn.execute("INSERT INTO dictionary_fts(dictionary_fts) VALUES('rebuild')")
        conn.execute(
            """
            CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_dictionary_fts_AFTER_INSERT
            AFTER INSERT ON `dictionary_entries` BEGIN
                INSERT INTO `dictionary_fts`(`docid`, `word`, `meaning_vi`, `collocations_json`, `synonyms_json`)
                VALUES (NEW.`rowid`, NEW.`word`, NEW.`meaning_vi`, NEW.`collocations_json`, NEW.`synonyms_json`);
            END
            """
        )

        preset_folders = [
            ("Mặc định", "folder", "#1D4ED8", now_ms),
            ("Oxford 3000", "school", "#0F766E", now_ms + 1),
            ("Từ lưu từ hội thoại", "chat", "#7C3AED", now_ms + 2),
        ]
        folder_ids: dict[str, int] = {}
        for fname, icon_name, color_hex, created_at in preset_folders:
            cur = conn.execute(
                """
                INSERT OR IGNORE INTO word_folders (name, icon_name, color_hex, created_at)
                VALUES (?, ?, ?, ?)
                """,
                (fname, icon_name, color_hex, created_at),
            )
            row_f = conn.execute("SELECT id FROM word_folders WHERE name = ? LIMIT 1", (fname,)).fetchone()
            if row_f:
                folder_ids[fname] = int(row_f[0])

        starter_cards = [
            ("ubiquitous", "Mặc định"),
            ("resilient", "Mặc định"),
            ("decision", "Oxford 3000"),
            ("approach", "Oxford 3000"),
            ("consistent", "Oxford 3000"),
            ("clarify", "Từ lưu từ hội thoại"),
            ("negotiate", "Từ lưu từ hội thoại"),
        ]
        for w, folder in starter_cards:
            row = conn.execute(
                "SELECT id FROM dictionary_entries WHERE word_normalized = ? LIMIT 1", (w,)
            ).fetchone()
            fid = folder_ids.get(folder)
            if row and fid is not None:
                conn.execute(
                    """
                    INSERT OR IGNORE INTO saved_words (
                        entry_id, folder_id, folder_name, ease_factor, interval_days,
                        repetitions, lapses, state, next_review_at, last_reviewed_at, created_at
                    ) VALUES (?, ?, ?, 2.5, 0, 0, 0, 'NEW', ?, NULL, ?)
                    """,
                    (row[0], fid, folder, now_ms, now_ms),
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

        conn.execute(
            """
            INSERT OR IGNORE INTO user_streak_state (
                id, current_streak, longest_streak, freeze_count, last_active_date, daily_xp_goal
            ) VALUES (1, 0, 0, 1, NULL, 50)
            """
        )

        conn.commit()
        conn.execute("ANALYZE")
        conn.execute("VACUUM")
        print(f"Built {OUTPUT_DB} with {len(all_entries)} dictionary entries and {len(articles)} articles.")
    finally:
        conn.close()


def main() -> None:
    parser = argparse.ArgumentParser(description="Build Enzii Dict offline SQLite database.")
    parser.add_argument("--fvdp", type=Path, default=None, help="Optional FVDP .dict/.txt file path")
    parser.add_argument("--no-fvdp", action="store_true", help="Build compact seed database without FVDP 109K")
    parser.add_argument("--wiktionary", type=Path, default=None, help="Optional Wiktionary JSONL file path")
    args = parser.parse_args()
    build_database(args.fvdp, args.wiktionary, use_default_fvdp=not args.no_fvdp)


if __name__ == "__main__":
    main()

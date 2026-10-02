# COBIP SQL 외래 키 관계 목록

`schema.sql`의 실제 `REFERENCES` / `FOREIGN KEY` 정의에서 추출했다. 한 줄은 FK 제약 하나이며 복합 FK는 컬럼 쌍을 모두 표시한다.

표기: 부모 쪽 `1`은 자식 행에 부모가 필수, `0..1`은 FK가 NULL일 수 있음을 뜻한다. 자식 쪽은 이 부모에 연결된 행이 없을 수도 있으므로 최소 0이다.

테이블 10개 · 컬럼 98개 · 외래 키 제약 23개

1. `categories.(category_id)` (PK) `0..1` ─ `0..N` `categories.(parent_category_id)` (FK: `categories_parent_category_id_fkey`; NULL 가능; 자식 FK 비고유)
2. `categories.(category_id)` (PK) `1` ─ `0..N` `templates.(category_id)` (FK: `templates_category_id_fkey`; NOT NULL; 자식 FK 비고유)
3. `languages.(language_id)` (PK) `0..1` ─ `0..N` `templates.(language_id)` (FK: `templates_language_id_fkey`; NULL 가능; 자식 FK 비고유)
4. `users.(user_id)` (PK) `1` ─ `0..N` `templates.(created_by)` (FK: `templates_created_by_fkey`; NOT NULL; 자식 FK 비고유)
5. `templates.(template_id)` (PK) `1` ─ `0..N` `template_sections.(template_id)` (FK: `template_sections_template_id_fkey`; NOT NULL; 자식 FK 비고유)
6. `template_sections.(section_id, template_id)` (UNIQUE) `0..1` ─ `0..N` `template_sections.(parent_section_id, template_id)` (FK: `template_sections_parent_fk`; NULL 가능; 자식 FK 비고유)
7. `categories.(category_id)` (PK) `1` ─ `0..N` `questions.(category_id)` (FK: `questions_category_id_fkey`; NOT NULL; 자식 FK 비고유)
8. `templates.(template_id)` (PK) `0..1` ─ `0..N` `questions.(related_template_id)` (FK: `questions_related_template_id_fkey`; NULL 가능; 자식 FK 비고유)
9. `users.(user_id)` (PK) `1` ─ `0..N` `questions.(created_by)` (FK: `questions_created_by_fkey`; NOT NULL; 자식 FK 비고유)
10. `languages.(language_id)` (PK) `0..1` ─ `0..N` `questions.(language_id)` (FK: `questions_language_id_fkey`; NULL 가능; 자식 FK 비고유)
11. `template_sections.(section_id, template_id)` (UNIQUE) `0..1` ─ `0..N` `questions.(related_section_id, related_template_id)` (FK: `questions_section_template_fk`; NULL 가능; 자식 FK 비고유)
12. `users.(user_id)` (PK) `1` ─ `0..N` `media_files.(uploaded_by)` (FK: `media_files_uploaded_by_fkey`; NOT NULL; 자식 FK 비고유)
13. `templates.(template_id)` (PK) `0..1` ─ `0..N` `media_files.(template_id)` (FK: `media_files_template_id_fkey`; NULL 가능; 자식 FK 비고유)
14. `template_sections.(section_id)` (PK) `0..1` ─ `0..N` `media_files.(section_id)` (FK: `media_files_section_id_fkey`; NULL 가능; 자식 FK 비고유)
15. `questions.(question_id)` (PK) `0..1` ─ `0..N` `media_files.(question_id)` (FK: `media_files_question_id_fkey`; NULL 가능; 자식 FK 비고유)
16. `users.(user_id)` (PK) `1` ─ `0..N` `submissions.(user_id)` (FK: `submissions_user_id_fkey`; NOT NULL; 자식 FK 비고유)
17. `questions.(question_id)` (PK) `1` ─ `0..N` `submissions.(question_id)` (FK: `submissions_question_id_fkey`; NOT NULL; 자식 FK 비고유)
18. `users.(user_id)` (PK) `1` ─ `0..N` `user_progress.(user_id)` (FK: `user_progress_user_id_fkey`; NOT NULL; 자식 FK 비고유)
19. `templates.(template_id)` (PK) `1` ─ `0..N` `user_progress.(template_id)` (FK: `user_progress_template_id_fkey`; NOT NULL; 자식 FK 비고유)
20. `template_sections.(section_id, template_id)` (UNIQUE) `0..1` ─ `0..N` `user_progress.(last_section_id, template_id)` (FK: `user_progress_last_section_fk`; NULL 가능; 자식 FK 비고유)
21. `users.(user_id)` (PK) `1` ─ `0..N` `template_bookmarks.(user_id)` (FK: `template_bookmarks_user_id_fkey`; NOT NULL; 자식 FK 비고유)
22. `templates.(template_id)` (PK) `1` ─ `0..N` `template_bookmarks.(template_id)` (FK: `template_bookmarks_template_id_fkey`; NOT NULL; 자식 FK 비고유)
23. `media_files.(media_id)` (PK) `0..1` ─ `0..1` `users.(profile_media_id)` (FK: `users_profile_media_fk`; NULL 가능; 자식 FK UNIQUE)

## UNIQUE 인덱스/제약

- `users.users_pkey`: `(user_id)` — PK
- `users.users_nickname_key`: `(nickname)`
- `users.users_profile_media_id_key`: `(profile_media_id)`
- `users.users_email_lower_uq`: `(lower(email))` — 표현식
- `languages.languages_pkey`: `(language_id)` — PK
- `languages.languages_code_key`: `(code)`
- `languages.languages_name_key`: `(name)`
- `languages.languages_display_order_key`: `(display_order)`
- `categories.categories_pkey`: `(category_id)` — PK
- `categories.categories_code_key`: `(code)`
- `categories.categories_root_name_uq`: `(name)` — 부분 인덱스
- `categories.categories_child_name_uq`: `(parent_category_id, name)` — 부분 인덱스
- `categories.categories_root_order_uq`: `(display_order)` — 부분 인덱스
- `categories.categories_child_order_uq`: `(parent_category_id, display_order)` — 부분 인덱스
- `templates.templates_pkey`: `(template_id)` — PK
- `templates.templates_display_order_uq`: `(category_id, language_id, display_order)`
- `template_sections.template_sections_pkey`: `(section_id)` — PK
- `template_sections.template_sections_template_identity_uq`: `(section_id, template_id)`
- `template_sections.template_sections_order_uq`: `(template_id, parent_section_id, display_order)`
- `questions.questions_pkey`: `(question_id)` — PK
- `questions.questions_section_order_uq`: `(related_section_id, display_order)` — 부분 인덱스
- `media_files.media_files_pkey`: `(media_id)` — PK
- `media_files.media_files_object_key_key`: `(object_key)`
- `submissions.submissions_pkey`: `(submission_id)` — PK
- `user_progress.user_progress_pkey`: `(user_id, template_id)` — PK
- `template_bookmarks.template_bookmarks_pkey`: `(user_id, template_id)` — PK

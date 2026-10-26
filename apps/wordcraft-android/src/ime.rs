//! Android's editable buffer changes use logical UTF-16 offsets, not glyph positions.
use super::*;
use wordcraft_engine::Selection;

fn relative(doc: &Document, from: &Pos, delta: i32) -> Pos {
    let mut at = doc.clamp(from);
    let mut left = delta.unsigned_abs();
    while left > 0 {
        let Some(para) = doc.para_at(&at) else { break };
        if delta < 0 {
            if let Some(ch) = para.text.get(..at.off).and_then(|t| t.chars().next_back()) {
                at.off -= ch.len_utf8();
                left = left.saturating_sub(ch.len_utf16() as u32);
            } else if let Some(path) = doc.prev_para(at.story, &at.path) {
                at.path = path;
                at.off = doc.para_at(&at).map_or(0, |p| p.len());
                left -= 1;
            } else {
                break;
            }
        } else if let Some(ch) = para.text.get(at.off..).and_then(|t| t.chars().next()) {
            at.off += ch.len_utf8();
            left = left.saturating_sub(ch.len_utf16() as u32);
        } else if let Some(path) = doc.next_para(at.story, &at.path) {
            at.path = path;
            at.off = 0;
            left -= 1;
        } else {
            break;
        }
    }
    at
}

#[uniffi::export]
impl MobileSession {
    /// Replace only the buffer's changed range. Both ends are relative to the old ordered
    /// selection ends; the new selection is relative to the inserted text's end (or old ends
    /// for a selection-only operation). Intermediate composing text is real, laid-out text.
    pub fn edit_ime(
        &self,
        replace: bool,
        start: i32,
        end: i32,
        text: String,
        selection_start: i32,
        selection_end: i32,
        composing: bool,
    ) -> Result<(), MobileError> {
        if [start, end, selection_start, selection_end].iter().any(|v| v.unsigned_abs() > 1_000_000) {
            return Err(MobileError::Command("IME range is too large".into()));
        }
        let mut s = self.lock();
        if s.math.is_some() {
            if replace {
                // Equations have their own caret; the buffer starts empty on entering one.
                for _ in 0..start.saturating_neg().clamp(0, 10_000) {
                    Self::run(&mut s, "equation.backspace", json!({}))?;
                }
                for _ in 0..end.clamp(0, 10_000) {
                    Self::run(&mut s, "equation.delete", json!({}))?;
                }
                Self::type_in_equation(&mut s, &text)?;
            }
            return Ok(());
        }
        let snapshot = s.edit_snapshot();
        let replaces_selection = s.prefs.typing_replaces_selection;
        // IME corrections name an exact replacement range, irrespective of the hardware
        // keyboard preference for typing over a selected range.
        s.prefs.typing_replaces_selection = true;
        let result = (|| {
            let (a, b) = s.sel_ends().ordered();
            let (a, b) = (a.clone(), b.clone());
            if replace {
                s.sel = Selection { anchor: relative(&s.doc, &a, start), focus: relative(&s.doc, &b, end) };
                if text.is_empty() {
                    Self::run(&mut s, "text.insert", json!({"text": "", "raw": true}))?;
                } else {
                    Self::type_locked(&mut s, &text, composing)?;
                }
                let at = s.sel.focus.clone();
                s.sel = Selection { anchor: relative(&s.doc, &at, selection_start), focus: relative(&s.doc, &at, selection_end) };
            } else {
                s.sel = Selection { anchor: relative(&s.doc, &a, selection_start), focus: relative(&s.doc, &b, selection_end) };
                s.pending = None;
                s.close_typing();
            }
            s.goal_x = None;
            Ok(())
        })();
        s.prefs.typing_replaces_selection = replaces_selection;
        if result.is_err() {
            s.restore(snapshot);
        }
        result
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn session(text: &str) -> MobileSession {
        let m = MobileSession::default();
        let mut s = Session::new(Document::from_text(text));
        s.sel = Selection::caret(s.doc.end_of(wordcraft_engine::doc::StoryRef::Body));
        *m.lock() = s;
        m
    }
    fn text(m: &MobileSession) -> String {
        m.lock().doc.plain_text(wordcraft_engine::doc::StoryRef::Body).trim_end_matches('\n').into()
    }

    #[test]
    fn arabic_composing_is_real_text_without_overlay_or_duplicate_commit() {
        let old = "بسم الله الرحمن الرحيم";
        let m = session(old);
        m.edit_ime(true, 0, 0, " ".into(), 0, 0, false).unwrap();
        let mut length = 0;
        for word in ["ا", "ال", "الح", "الحمد"] {
            m.edit_ime(true, -length, 0, word.into(), 0, 0, true).unwrap();
            assert_eq!(text(&m), format!("{old} {word}"));
            length = word.encode_utf16().count() as i32;
            assert!(!m.document_info().pages.is_empty());
        }
        // finishComposingText changes spans only: it does not insert the word again.
        assert_eq!(m.text_around(500, 500).before, format!("{old} الحمد"));
        m.execute("edit.undo".into(), "".into()).unwrap();
        assert_eq!(text(&m), old);
        m.execute("edit.redo".into(), "".into()).unwrap();
        assert_eq!(text(&m), format!("{old} الحمد"));
    }

    #[test]
    fn recomposing_an_existing_word_replaces_it_in_arabic_and_english() {
        for (old, length, replacement, expected) in
            [("بسم الله الرحمن الرحيم", 6, "الكريم", "بسم الله الرحمن الكريم"), ("Hello world", 5, "words", "Hello words")]
        {
            let m = session(old);
            m.edit_ime(true, -length, 0, replacement.into(), 0, 0, true).unwrap();
            assert_eq!(text(&m), expected);
            assert_eq!(m.text_around(500, 500).before, expected);
        }
    }

    #[test]
    fn uncommitted_ime_word_is_in_saved_docx_and_pdf() {
        let m = session("بسم الله الرحمن الرحيم ");
        m.edit_ime(true, 0, 0, "الحمد".into(), 0, 0, true).unwrap();
        let bytes = m.save_bytes("ime.docx".into()).unwrap();
        let doc = io::open_bytes("ime.docx", &bytes).unwrap();
        assert_eq!(doc.plain_text(wordcraft_engine::doc::StoryRef::Body).trim_end(), "بسم الله الرحمن الرحيم الحمد");
        assert!(m.export_bytes("ime.pdf".into()).unwrap().starts_with(b"%PDF"));
    }

    #[test]
    fn inserting_in_the_middle_preserves_both_sides_and_cursor_position() {
        let m = session("مرحبا بالعالم");
        m.lock().sel = Selection::caret(Pos::body(0, "مرحبا ".len()));
        m.edit_ime(true, 0, 0, "يا ".into(), -3, -3, true).unwrap();
        assert_eq!(text(&m), "مرحبا يا بالعالم");
        assert_eq!(m.text_around(500, 500).before, "مرحبا ");
        assert_eq!(m.text_around(500, 500).after, "يا بالعالم");
    }

    #[test]
    fn utf16_replacements_preserve_surrogates_and_arabic_combining_marks() {
        let m = session("ab😀cd");
        m.lock().sel = Selection::caret(Pos::body(0, "ab😀".len()));
        m.edit_ime(true, -2, 0, "😁".into(), 0, 0, true).unwrap();
        assert_eq!(text(&m), "ab😁cd");
        m.edit_ime(true, -2, 0, "".into(), 0, 0, false).unwrap();
        assert_eq!(text(&m), "abcd");
        let m = session("بِ");
        m.edit_ime(true, -1, 0, "".into(), 0, 0, false).unwrap();
        assert_eq!(text(&m), "ب");
    }

    #[test]
    fn ime_cursor_and_deletion_cross_paragraph_boundaries() {
        let m = session("One");
        m.edit_ime(true, 0, 0, "\ntwo".into(), 0, 0, false).unwrap();
        m.edit_ime(true, -3, 0, "three".into(), 0, 0, true).unwrap();
        assert_eq!(text(&m), "One\nthree");
        m.edit_ime(true, -6, 0, "!".into(), 0, 0, false).unwrap();
        assert_eq!(text(&m), "One!");
    }

    #[test]
    fn protected_text_is_not_changed_by_composition_and_exact_ranges_override_typing_preference() {
        let m = session("Keep");
        m.lock().prefs.typing_replaces_selection = false;
        m.edit_ime(true, -4, 0, "Kept".into(), 0, 0, true).unwrap();
        assert_eq!(text(&m), "Kept");
        assert!(!m.lock().prefs.typing_replaces_selection);
        m.execute("review.restrict".into(), r#"{"mode":"readOnly"}"#.into()).unwrap();
        assert!(m.edit_ime(true, -4, 0, "Lost".into(), 0, 0, true).is_err());
        assert_eq!(text(&m), "Kept");
    }
}

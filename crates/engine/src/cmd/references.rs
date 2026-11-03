//! References tab: table of contents, footnotes/endnotes, captions; field updates.

use serde_json::{Value, json};
use wordcraft_doc::numbering::{Counters, LevelSuffix};
use wordcraft_doc::para::{InlineObject, NoteKind, OBJ};
use wordcraft_doc::props::{CharProps, TabAlign, TabLeader, TabStop};
use wordcraft_doc::section::NumFormat;
use wordcraft_doc::{Block, Paragraph, PartKind, Path, Pos, StoryRef, para_block};

use super::{delete_selection, sel_result};
use crate::{CmdError, CmdResult, CommandSpec, Selection, Session, p};

mod fields;
mod notes;
mod properties;
mod toc;

use fields::caption;
pub use fields::{CHAPTER_SEPARATORS, refresh_date_fields, seq_format, update_fields, update_fields_in, update_seq};
pub(super) use notes::note_options;
use notes::{note, note_refs};
pub use properties::{bookmark_text, date_picture};
#[cfg(test)]
use toc::cached_toc_tabs;
pub use toc::{caret_in_toc, leader, level_range, toc_instr, update_toc};
pub(crate) use toc::{headings, toc_bookmark};
use toc::{insert_toc, remove_toc, toc_range};

pub fn specs() -> Vec<CommandSpec> {
    vec![
        CommandSpec::new("navigation.headings", "Headings", "View › Navigation Pane", navigation_headings)
            .params(r#"{"detect"?: bool (default true: when the document has no headings, paragraphs that look like headings)}  (answers {"headings": [{"block", "level", "text"}], "detected": bool})"#)
            .pure(),
        CommandSpec::new("references.toc", "Table of Contents", "References › Table of Contents", insert_toc).params(
            r#"{"levels"?: 1-9, "title"?: string, "pageNumbers"?: bool, "rightAlign"?: bool, "tabLeader"?: "dot|hyphen|underscore|none", "hyperlinks"?: bool, "headingStyles"?: bool, "outlineLevels"?: bool, "tcFields"?: bool, "styleLevels"?: {style name: 1-9}} (replaces an existing table; {} updates it)"#,
        ),
        CommandSpec::new("references.updateToc", "Update Table", "References › Table of Contents", |s, _| {
            update_toc(s)?;
            sel_result(s)
        }),
        CommandSpec::new("references.removeToc", "Remove Table of Contents", "References › Table of Contents", |s, _| {
            if let Some((start, end)) = toc_range(s) {
                remove_toc(s, start, end)?;
                s.clamp_selection();
            }
            sel_result(s)
        }),
        CommandSpec::new("references.addText", "Add Text", "References › Table of Contents", |s, v| {
            let level = p::u64(v, "level").unwrap_or(0).min(9) as u8;
            super::para::fmt(s, &|pp| pp.outline_level = if level == 0 { Some(9) } else { Some(level - 1) })
        })
        .params(r#"{"level": 0 (do not show) | 1-9}"#),
        CommandSpec::new("references.footnote", "Insert Footnote", "References › Footnotes", |s, v| note(s, v, NoteKind::Footnote))
            .key("Mod+Alt+F")
            .params(r#"{"text"?: string, "mark"?: string (a custom mark instead of the number)}"#),
        CommandSpec::new("references.endnote", "Insert Endnote", "References › Footnotes", |s, v| note(s, v, NoteKind::Endnote))
            // macOS takes ⌘⌥D to show and hide the Dock; Word for Mac uses ⌘⌥E.
            .key(super::mac_or("Mod+Alt+E", "Mod+Alt+D"))
            .params(r#"{"text"?: string, "mark"?: string (a custom mark instead of the number)}"#),
        CommandSpec::new("references.convertNotes", "Convert Notes", "References › Footnotes", |s, v| {
            let (foot, end) = match p::req_str(v, "to")? {
                "endnote" | "endnotes" => (true, false),
                "footnote" | "footnotes" => (false, true),
                "swap" => (true, true),
                t => return Err(CmdError::Params(format!("unknown `to` {t:?}; use endnote, footnote or swap"))),
            };
            let n = s.doc.convert_notes(foot, end);
            Ok(json!({"converted": n}))
        })
        .params(r#"{"to": "endnote" (footnotes become endnotes) | "footnote" (endnotes become footnotes) | "swap"}"#),
        CommandSpec::new("references.nextFootnote", "Next Footnote", "References › Footnotes", |s, _| {
            let caret = s.sel.focus.clone();
            let refs = note_refs(s, Some(NoteKind::Footnote));
            if let Some(p) = refs.iter().find(|p| **p > caret).or(refs.first()) {
                s.sel = Selection::caret(p.clone());
            }
            sel_result(s)
        })
        .key(super::non_mac("Alt+Shift+> / Alt+Shift+."))
        .pure(),
        CommandSpec::new("references.goToNote", "Go to Note", "References › Footnotes", go_to_note),
        CommandSpec::new("references.notes", "Show Notes", "References › Footnotes", |s, _| {
            let mut out = Vec::new();
            for (id, part) in &s.doc.parts {
                if matches!(part.kind, PartKind::Footnote | PartKind::Endnote) {
                    out.push(json!({"id": id, "kind": part.kind, "text": s.doc.plain_text(StoryRef::Part(*id))}));
                }
            }
            Ok(Value::Array(out))
        })
        .pure(),
        CommandSpec::new("references.caption", "Insert Caption", "References › Captions", caption)
            .params(r#"{"label"?: "Figure|Table|Equation|any label", "text"?: string, "position"?: "below|above", "excludeLabel"?: bool, "format"?: "ARABIC|alphabetic|ALPHABETIC|roman|ROMAN", "chapter"?: heading level 1-9, "separator"?: "-|.|:|—|–"}"#),
        // F9: update fields; the selected ones that showed their code show their result again.
        CommandSpec::new("references.updateFields", "Update Field", "References", |s, v| {
            update_fields_in(s, v.get("all").and_then(Value::as_bool).unwrap_or(false))?;
            super::fields::show_results(s)?;
            sel_result(s)
        })
        .params(r#"{"all"?: bool (the body and every header, footer, note and text box, wherever the caret is; default: the story the caret is in)}"#)
        .key("F9"),
    ]
}

/// A field instruction split into its name, positional arguments and switches (ECMA-376
/// §17.16.1): `TOC \o "1-3" \h` → name `TOC`, switches `o` = `1-3` and `h`. Quoted text stays
/// whole (without the quotes; `\"` and `\\` inside it are a quote and a backslash). A switch
/// takes the word after it as its argument unless that word is another switch.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct FieldCode {
    pub name: String,
    pub args: Vec<String>,
    /// Switch letter (lower-cased; `*`, `@`, `#` for the general switches) and argument.
    pub switches: Vec<(char, Option<String>)>,
}

impl FieldCode {
    pub fn parse(instr: &str) -> FieldCode {
        let mut words: Vec<(String, bool)> = Vec::new();
        let mut chars = instr.chars().peekable();
        while let Some(&c) = chars.peek() {
            if c.is_whitespace() {
                chars.next();
            } else if c == '"' {
                chars.next();
                let mut w = String::new();
                while let Some(c) = chars.next() {
                    match c {
                        '"' => break,
                        '\\' if matches!(chars.peek(), Some('"' | '\\')) => w.extend(chars.next()),
                        c => w.push(c),
                    }
                }
                words.push((w, true));
            } else {
                let mut w = String::new();
                while let Some(&c) = chars.peek() {
                    if c.is_whitespace() || (c == '"' && !w.is_empty()) {
                        break;
                    }
                    w.push(c);
                    chars.next();
                }
                words.push((w, false));
            }
        }
        let mut words = words.into_iter().peekable();
        let mut f =
            FieldCode { name: words.next().map(|(w, _)| w.trim_start_matches('=').to_ascii_uppercase()).unwrap_or_default(), ..Default::default() };
        while let Some((w, quoted)) = words.next() {
            let sw = if quoted { None } else { w.strip_prefix('\\').and_then(|r| r.chars().next()) };
            match sw {
                Some(c) => {
                    let arg = match words.peek() {
                        Some((n, q)) if *q || !n.starts_with('\\') => words.next().map(|(n, _)| n),
                        _ => None,
                    };
                    f.switches.push((c.to_ascii_lowercase(), arg));
                }
                None => f.args.push(w),
            }
        }
        f
    }

    pub fn has(&self, c: char) -> bool {
        self.switches.iter().any(|(k, _)| *k == c)
    }

    /// A table of figures (`TOC \c` or `TOC \a`, ECMA-376 §17.16.5.68), not a table of contents.
    pub fn is_table_of_figures(&self) -> bool {
        self.name == "TOC" && (self.has('c') || self.has('a'))
    }

    /// The argument of switch `c`, if the switch is there with one.
    pub fn arg(&self, c: char) -> Option<&str> {
        self.switches.iter().find(|(k, _)| *k == c).and_then(|(_, a)| a.as_deref())
    }
}

#[cfg(test)]
mod tests;

pub(super) fn navigation_headings(s: &mut Session, v: &Value) -> CmdResult {
    let own: Vec<(usize, u8, String)> = headings(s).into_iter().map(|(b, l, t, _)| (b, l, t)).collect();
    let detected = own.is_empty() && p::bool(v, "detect").unwrap_or(true);
    let list = if detected { detected_headings(s) } else { own };
    let headings: Vec<Value> = list.into_iter().map(|(block, level, text)| json!({"block": block, "level": level, "text": text})).collect();
    Ok(json!({"headings": headings, "detected": detected && !headings.is_empty()}))
}

pub(super) fn go_to_note(s: &mut Session, _: &Value) -> CmdResult {
    let obj = wordcraft_doc::para::OBJ.len_utf8();
    let f = s.sel.focus.clone();
    let in_note = |id: u32| s.doc.parts.get(&id).is_some_and(|p| matches!(p.kind, PartKind::Footnote | PartKind::Endnote));
    if let StoryRef::Part(id) = f.story
        && in_note(id)
    {
        // back to the mark, in the body or a story that holds marks
        let stories = std::iter::once(StoryRef::Body)
            .chain(s.doc.parts.iter().filter(|(k, _)| !in_note(**k)).map(|(k, _)| StoryRef::Part(*k)))
            .collect::<Vec<_>>();
        for story in stories {
            for path in s.doc.para_paths(story) {
                let Some(p) = s.doc.para(story, &path) else { continue };
                if let Some(off) =
                    p.object_offsets().into_iter().find(|o| matches!(p.object_at(*o), Some(InlineObject::NoteRef { id: n, .. }) if *n == id))
                {
                    s.sel = Selection::caret(Pos { story, path, off: off + obj });
                    return sel_result(s);
                }
            }
        }
        return Err(CmdError::Failed("the note's mark isn't in the document".into()));
    }
    let p = s.doc.para_at(&f).ok_or_else(|| CmdError::Disabled("no footnote or endnote here".into()))?;
    let id = [f.off, f.off.saturating_sub(obj)]
        .into_iter()
        .find_map(|o| match p.object_at(o) {
            Some(InlineObject::NoteRef { id, .. }) => Some(*id),
            _ => None,
        })
        .ok_or_else(|| CmdError::Disabled("no footnote or endnote here".into()))?;
    let story = StoryRef::Part(id);
    let first = s.doc.para_paths(story).into_iter().next().ok_or_else(|| CmdError::Failed("the note is empty".into()))?;
    let para = s.doc.para(story, &first).ok_or_else(|| CmdError::Failed("the note is empty".into()))?;
    // past the note's own mark and the space after it
    let mut off = 0;
    while matches!(para.object_at(off), Some(InlineObject::NoteRef { .. })) {
        off += obj;
    }
    if para.text.get(off..).is_some_and(|t| t.starts_with(' ')) {
        off += 1;
    }
    s.sel = Selection::caret(Pos { story, path: first, off });
    sel_result(s)
}

pub(crate) fn detected_headings(s: &Session) -> Vec<(usize, u8, String)> {
    struct Line {
        block: usize,
        text: String,
        size: f32,
        strong: bool,
    }
    let mut lines: Vec<Line> = Vec::new();
    // the body text's size: the size most of the text is in
    let mut sizes: Vec<(u32, usize)> = Vec::new();
    for (block, b) in s.doc.body.iter().enumerate() {
        let Block::Para(p) = b.as_ref() else { continue };
        let text = p.plain_text().replace(['\u{000C}', '\n', '\t', OBJ], " ").trim().to_string();
        if text.is_empty() {
            continue;
        }
        let style = p.props.style.as_deref();
        let (mut size, mut bold, mut caps) = (0.0f32, true, true);
        for (range, props) in p.run_ranges() {
            let Some(run) = p.text.get(range) else { continue };
            if run.trim().is_empty() {
                continue;
            }
            let rc = s.doc.styles.resolve_char_in(style, None, props);
            size = size.max(rc.size);
            bold &= rc.bold;
            caps &= rc.caps || !run.chars().any(char::is_lowercase);
            let weight = run.chars().count();
            let key = (rc.size * 2.0).round() as u32;
            match sizes.iter_mut().find(|(k, _)| *k == key) {
                Some(e) => e.1 += weight,
                None => sizes.push((key, weight)),
            }
        }
        let letters = text.chars().filter(|c| c.is_alphabetic()).count();
        let short = text.chars().count() <= DETECT_CHARS && text.split_whitespace().count() <= DETECT_WORDS;
        let sentence = text.ends_with(['.', ',', ';', '!', '?']);
        if short && !sentence {
            lines.push(Line { block, text, size, strong: bold || (caps && letters >= 3) });
        }
    }
    let body = sizes.iter().max_by_key(|(_, n)| *n).map_or(11.0, |(k, _)| *k as f32 / 2.0);
    // standing out, with something after it
    let last = s.doc.body.len().saturating_sub(1);
    let picked: Vec<&Line> = lines.iter().filter(|l| l.block < last && (l.size >= body + 1.5 || l.strong)).collect();
    // the larger sizes, largest first: a level each, at most two
    let mut big: Vec<u32> = picked.iter().filter(|l| l.size >= body + 1.5).map(|l| (l.size * 2.0).round() as u32).collect();
    big.sort_unstable_by(|a, b| b.cmp(a));
    big.dedup();
    big.truncate(2);
    picked
        .into_iter()
        .filter_map(|l| {
            let key = (l.size * 2.0).round() as u32;
            let level = match big.iter().position(|k| *k == key) {
                Some(i) => i + 1,
                // smaller than the two largest sizes, or the body text's size made to stand out
                None => big.len() + 1,
            };
            Some((l.block, u8::try_from(level).ok()?.min(3), l.text.clone()))
        })
        .take(MAX_DETECTED)
        .collect()
}

const DETECT_CHARS: usize = 100;

const DETECT_WORDS: usize = 12;

const MAX_DETECTED: usize = 500;

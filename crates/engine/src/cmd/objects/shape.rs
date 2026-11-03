use super::Selection;
use super::picture::picture_extent;
use super::select::{all_objects, edit_obj, picked_objects, selected, selected_all};
use super::{CmdError, CmdResult, InlineObject, Pos, Rgb, Session, StoryRef, TextBody, Value, json, p};
use wordcraft_doc::props::VAlign;

pub(super) const NO_TEXT_BOX: &str = "select a text box first";

pub(super) fn has_text_box(s: &Session) -> Option<&'static str> {
    if text_box_target(s).is_some() { None } else { Some(NO_TEXT_BOX) }
}

fn is_text_box(s: &Session, id: u32) -> bool {
    s.doc.parts.get(&id).is_some_and(|p| p.kind == wordcraft_doc::PartKind::TextBox)
}

/// The text box the Shape Format › Text commands work on: the selected one, or the one whose
/// text the caret is in. Its U+FFFC's position and its story.
pub fn text_box_target(s: &Session) -> Option<(Pos, u32)> {
    if let Some((pos, InlineObject::Shape { story: Some(id), .. })) = selected(s)
        && is_text_box(s, id)
    {
        return Some((pos, id));
    }
    let StoryRef::Part(id) = s.sel.focus.story else { return None };
    if !is_text_box(s, id) {
        return None;
    }
    let after = s.doc.text_box_anchor(id)?;
    let off = after.off.checked_sub(wordcraft_doc::para::OBJ.len_utf8())?;
    Some((Pos { off, ..after }, id))
}

/// Change the target text box's text direction, alignment or link.
pub(crate) fn set_text_body(s: &mut Session, f: impl Fn(&mut TextBody)) -> CmdResult {
    let (pos, id) = text_box_target(s).ok_or_else(|| CmdError::Disabled(NO_TEXT_BOX.into()))?;
    let part = s.doc.parts.get_mut(&id).ok_or_else(|| CmdError::Failed("text box vanished".into()))?;
    f(&mut part.body);
    let body = part.body;
    // Its paragraph is laid out again.
    edit_obj(s, &pos, |_| {})?;
    Ok(json!({"textBox": id, "body": body}))
}

pub(super) fn can_link(s: &Session) -> Option<&'static str> {
    match text_box_target(s) {
        None => Some(NO_TEXT_BOX),
        Some((_, id)) if s.doc.parts.get(&id).is_some_and(|p| p.body.next.is_some()) => Some("this text box is already linked: break the link first"),
        Some(_) => None,
    }
}

/// `shape.link`: the selected text box's text continues in the target box, which must be an
/// empty text box no other box links to, and not one of those the text already runs through.
pub(super) fn link_text_box(s: &mut Session, v: &Value) -> CmdResult {
    let (pos, from) = text_box_target(s).ok_or_else(|| CmdError::Disabled(NO_TEXT_BOX.into()))?;
    let target = v.get("target").ok_or_else(|| CmdError::Params("`target`: the text box to link to".into()))?;
    let at = match target.as_u64() {
        Some(n) => all_objects(s).into_iter().nth(usize::try_from(n).unwrap_or(usize::MAX)).map(|(p, _)| p),
        None => crate::cmd::parse_pos(target),
    }
    .ok_or_else(|| CmdError::Params("no object there".into()))?;
    let to = match s.doc.para_at(&at).and_then(|q| q.object_at(at.off)) {
        Some(InlineObject::Shape { story: Some(id), .. }) if is_text_box(s, *id) => *id,
        _ => return Err(CmdError::Params("the target isn't a text box".into())),
    };
    if s.doc.parts.get(&from).is_some_and(|p| p.body.next.is_some()) {
        return Err(CmdError::Failed("this text box is already linked: break the link first".into()));
    }
    if to == from || s.doc.text_box_chain(from).is_some_and(|c| c.contains(&to)) {
        return Err(CmdError::Failed("a text box can't link to itself or to a box its text already runs through".into()));
    }
    if s.doc.text_box_chains().iter().any(|c| c.iter().skip(1).any(|id| *id == to)) {
        return Err(CmdError::Failed("another text box already links to that one".into()));
    }
    if !s.doc.plain_text(StoryRef::Part(to)).trim().is_empty() {
        return Err(CmdError::Failed("the text box to link to must be empty".into()));
    }
    if let Some(p) = s.doc.parts.get_mut(&from) {
        p.body.next = Some(to);
    }
    edit_obj(s, &pos, |_| {})?;
    edit_obj(s, &at, |_| {})?;
    Ok(json!({"textBox": from, "next": to}))
}

/// Most shapes one `shape.effects` changes.
const MAX_EFFECT_SHAPES: usize = 10_000;

/// `shape.effects`: set or clear the shadow, glow and soft edges of the selected shapes.
pub(super) fn shape_effects(s: &mut Session, v: &Value) -> CmdResult {
    use wordcraft_doc::effects::{Glow, Shadow, ShapeEffects};
    let color = |o: &Value| p::str(o, "color").and_then(Rgb::parse);
    // Each change is `None` (leave as it is) or `Some(new value)`.
    let shadow: Option<Option<Shadow>> = match v.get("shadow") {
        None => None,
        Some(Value::Null) => Some(None),
        Some(Value::Bool(b)) => Some(b.then(Shadow::default)),
        Some(Value::String(id)) if id == "none" => Some(None),
        Some(Value::String(id)) => Some(Some(Shadow::preset(id).ok_or_else(|| CmdError::Params(format!("unknown shadow preset `{id}`")))?)),
        Some(o @ Value::Object(_)) => {
            let mut sh = match p::str(o, "preset") {
                Some(id) => Shadow::preset(id).ok_or_else(|| CmdError::Params(format!("unknown shadow preset `{id}`")))?,
                None => Shadow::default(),
            };
            sh.color = color(o).unwrap_or(sh.color);
            sh.transparency = p::f32(o, "transparency").unwrap_or(sh.transparency);
            sh.blur = p::f32(o, "blur").unwrap_or(sh.blur);
            sh.distance = p::f32(o, "distance").unwrap_or(sh.distance);
            sh.angle = p::f32(o, "angle").unwrap_or(sh.angle);
            Some(Some(sh.sanitized()))
        }
        Some(_) => return Err(CmdError::Params("`shadow`: a preset name, an object or null".into())),
    };
    let glow: Option<Option<Glow>> = match v.get("glow") {
        None => None,
        Some(Value::Null) => Some(None),
        Some(Value::String(id)) if id == "none" => Some(None),
        Some(n @ Value::Number(_)) => Some(Some(Glow { size: n.as_f64().unwrap_or(0.0) as f32, ..Glow::default() }.sanitized())),
        Some(o @ Value::Object(_)) => {
            let d = Glow::default();
            Some(Some(
                Glow {
                    color: color(o).unwrap_or(d.color),
                    size: p::f32(o, "size").unwrap_or(d.size),
                    transparency: p::f32(o, "transparency").unwrap_or(d.transparency),
                }
                .sanitized(),
            ))
        }
        Some(_) => return Err(CmdError::Params("`glow`: a size in points, an object or null".into())),
    };
    let inner: Option<Option<wordcraft_doc::effects::InnerShadow>> = match v.get("innerShadow") {
        None => None,
        Some(Value::Null) => Some(None),
        Some(Value::String(id)) if id == "none" => Some(None),
        Some(Value::String(id)) => Some(Some(
            wordcraft_doc::effects::InnerShadow::preset(id).ok_or_else(|| CmdError::Params(format!("unknown inner shadow preset `{id}`")))?,
        )),
        Some(_) => return Err(CmdError::Params("`innerShadow`: a preset name or null".into())),
    };
    let reflection: Option<Option<wordcraft_doc::effects::Reflection>> = match v.get("reflection") {
        None => None,
        Some(Value::Null) => Some(None),
        Some(Value::String(id)) if id == "none" => Some(None),
        Some(Value::String(id)) => {
            Some(Some(wordcraft_doc::effects::Reflection::preset(id).ok_or_else(|| CmdError::Params(format!("unknown reflection preset `{id}`")))?))
        }
        Some(_) => return Err(CmdError::Params("`reflection`: a preset name or null".into())),
    };
    let soft: Option<Option<f32>> = match v.get("softEdge") {
        None => None,
        Some(Value::Null) => Some(None),
        Some(n @ Value::Number(_)) => Some(n.as_f64().map(|x| x as f32)),
        Some(_) => return Err(CmdError::Params("`softEdge`: a radius in points or null".into())),
    };
    // The shapes and pictures in the selection and those added to it (Shift+click).
    let takes = |o: &InlineObject| matches!(o, InlineObject::Shape { .. } | InlineObject::Image { .. });
    let mut targets: Vec<Pos> = selected_all(s, MAX_EFFECT_SHAPES).into_iter().filter(|(_, o)| takes(o)).map(|(pos, _)| pos).collect();
    for pos in picked_objects(s) {
        if !targets.contains(&pos) && s.doc.para_at(&pos).and_then(|q| q.object_at(pos.off)).is_some_and(takes) {
            targets.push(pos);
        }
    }
    if targets.is_empty() {
        return Err(CmdError::Disabled("no shape or picture selected".into()));
    }
    let mut last = ShapeEffects::default();
    for pos in &targets {
        let o = edit_obj(s, pos, |o| {
            if let InlineObject::Shape { effects, float, h, .. } | InlineObject::Image { effects, float, h, .. } = o {
                let mut e = *effects;
                if let Some(x) = shadow {
                    e.shadow = x;
                }
                if let Some(x) = glow {
                    e.glow = x;
                }
                if let Some(x) = reflection {
                    e.reflection = x;
                }
                if let Some(x) = inner {
                    e.inner_shadow = x;
                }
                if let Some(x) = soft {
                    e.soft_edge = x;
                }
                *effects = e.sanitized();
                // Room around it for its shadow, glow and reflection, as Word's effect extent.
                float.effect = effects.extent_for(*h);
            }
            // and for a picture's outline
            if let InlineObject::Image { effects, framing, float, h, .. } = o {
                float.effect = picture_extent(effects, framing, *h);
            }
        })?;
        if let InlineObject::Shape { effects, .. } | InlineObject::Image { effects, .. } = o {
            last = effects;
        }
    }
    Ok(json!({"shapes": targets.len(), "effects": serde_json::to_value(last).unwrap_or(Value::Null)}))
}

pub(super) fn add_text(s: &mut Session, _: &Value) -> CmdResult {
    use wordcraft_doc::props::{Align, CharProps, ParaProps, TextColor};
    use wordcraft_doc::{Paragraph, PartKind, para_block};
    let (pos, obj) = selected(s).ok_or_else(|| CmdError::Disabled("select a shape first".into()))?;
    let InlineObject::Shape { kind, story, .. } = obj else {
        return Err(CmdError::Disabled("select a shape first".into()));
    };
    if kind.is_open() {
        return Err(CmdError::Disabled("lines and connectors can't hold text".into()));
    }
    let id = match story {
        Some(id) if s.doc.parts.contains_key(&id) => id,
        _ => {
            // as a new shape's text: centred, in the theme's light colour
            let light = s.doc.settings.theme_colors.get(1).copied().unwrap_or(Rgb::WHITE);
            let chars = CharProps { color: Some(TextColor::Rgb(light)), ..CharProps::default() };
            let mut para = Paragraph::with_text("", chars);
            para.props = ParaProps { align: Some(Align::Center), ..para.props.clone() };
            let id = s.doc.try_add_part(PartKind::TextBox, vec![para_block(para)])?;
            if let Some(part) = s.doc.parts.get_mut(&id) {
                part.body.anchor = VAlign::Center;
            }
            edit_obj(s, &pos, |o| {
                if let InlineObject::Shape { story, .. } = o {
                    *story = Some(id);
                }
            })?;
            id
        }
    };
    s.sel = Selection::caret(s.doc.end_of(StoryRef::Part(id)));
    Ok(json!({"story": id}))
}

pub(super) fn shape_style(s: &mut Session, v: &Value) -> CmdResult {
    use wordcraft_doc::props::TextColor;
    let colors = &s.doc.settings.theme_colors;
    let theme = |i: usize, fallback: Rgb| colors.get(i).copied().unwrap_or(fallback);
    let color = match p::str(v, "color").unwrap_or("accent1") {
        "dark1" => theme(0, Rgb::BLACK),
        c => match c.strip_prefix("accent").and_then(|n| n.parse::<usize>().ok()).filter(|n| (1..=6).contains(n)) {
            Some(n) => theme(3 + n, Rgb(0x15, 0x60, 0x82)),
            None => return Err(CmdError::Params(format!("`color`: dark1 or accent1 to accent6, not `{c}`"))),
        },
    };
    let (light, dark) = (theme(1, Rgb::WHITE), theme(0, Rgb::BLACK));
    // half as bright: the outline of a coloured fill
    let darker = Rgb(color.0 / 2, color.1 / 2, color.2 / 2);
    let look = |fill: Option<Rgb>, stroke: Option<(Rgb, f32)>, text: Rgb| StyleLook {
        fill,
        stroke,
        text,
        transparency: None,
        gradient: false,
        shadow: false,
    };
    let style = p::str(v, "style").unwrap_or("coloredFill");
    let l = match style {
        "coloredOutline" => look(Some(light), Some((color, 1.0)), dark),
        "coloredFill" => look(Some(color), Some((darker, 1.0)), light),
        "lightOutline" => look(Some(color), Some((light, 1.0)), light),
        "subtleEffect" => look(Some(color), None, dark),
        "moderateEffect" | "solid" => look(Some(color), None, light),
        "intenseEffect" => StyleLook { shadow: true, ..look(Some(color), None, light) },
        "transparent" => look(None, None, color),
        "transparentOutline" => look(None, Some((color, 1.5)), color),
        "semitransparent" => StyleLook { transparency: Some(50.0), ..look(Some(color), None, light) },
        "gradient" => StyleLook { gradient: true, ..look(Some(color), None, light) },
        x => return Err(CmdError::Params(format!("`style`: one of {SHAPE_STYLES}, not `{x}`"))),
    };
    let (pos, obj) = selected(s).ok_or_else(|| CmdError::Disabled("no picture or shape selected".into()))?;
    let story = match &obj {
        InlineObject::Shape { story, .. } => *story,
        _ => None,
    };
    let o = edit_obj(s, &pos, |o| {
        if let InlineObject::Shape { kind, fill, stroke, stroke_width, effects, extra, float, h, .. } = o {
            if kind.is_open() {
                // a line takes the style's colour only
                *stroke = Some(color);
            } else {
                *fill = l.fill.map(wordcraft_doc::fill::Fill::solid);
                extra.fill_transparency = l.transparency;
                extra.fill_gradient = l.gradient.then(|| wordcraft_doc::connector::ShapeGradient::shaded(color));
                *stroke = l.stroke.map(|(c, _)| c);
            }
            *stroke_width = l.stroke.map_or(1.0, |(_, w)| w);
            *effects = wordcraft_doc::effects::ShapeEffects {
                shadow: l.shadow.then_some(wordcraft_doc::effects::Shadow {
                    color: Rgb::BLACK,
                    transparency: 63.0,
                    blur: 4.5,
                    distance: 1.5,
                    angle: 90.0,
                    rot_with_shape: false,
                    ..Default::default()
                }),
                ..Default::default()
            };
            float.effect = effects.extent_for(*h);
        }
    })?;
    // its text in the style's text colour
    if let Some(id) = story.filter(|id| s.doc.parts.contains_key(id)) {
        let (a, b) = (s.doc.start_of(StoryRef::Part(id)), s.doc.end_of(StoryRef::Part(id)));
        s.doc.format_range(&a, &b, &|c| c.color = Some(TextColor::Rgb(l.text)))?;
    }
    Ok(json!({"object": serde_json::to_value(o).unwrap_or(Value::Null)}))
}

struct StyleLook {
    fill: Option<Rgb>,
    stroke: Option<(Rgb, f32)>,
    text: Rgb,
    transparency: Option<f32>,
    gradient: bool,
    shadow: bool,
}

const SHAPE_STYLES: &str = "coloredOutline, coloredFill, lightOutline, subtleEffect, moderateEffect, intenseEffect, transparent, transparentOutline, semitransparent, solid, gradient";

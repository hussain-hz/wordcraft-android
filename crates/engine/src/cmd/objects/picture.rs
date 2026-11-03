use super::select::{obj_size, selected, with_obj};
use super::{CmdError, CmdResult, InlineObject, Rgb, Session, Value, Wrap, p};
use wordcraft_metafile::Recolor;

/// `picture.crop`: the crop, and optionally the new display size, as one edit. With a size, a
/// floating picture positioned by offsets moves by what the crop on its left/top side on the page
/// trims (or restores) — for a flipped picture that's the source's right/bottom — so what stays
/// visible keeps its place on the page: a crop handle drop is one command.
pub(super) fn crop(s: &mut Session, v: &Value) -> CmdResult {
    let mut c = [p::f32(v, "left"), p::f32(v, "top"), p::f32(v, "right"), p::f32(v, "bottom")].map(|x| x.unwrap_or(0.0).clamp(0.0, 0.95));
    // The two sides of an axis keep at least 5% of the source between them, as the renderer
    // draws it: the second side stops where the first one already left less than that.
    c[2] = c[2].min(0.95 - c[0]);
    c[3] = c[3].min(0.95 - c[1]);
    let size = |k: &str| p::f32(v, k).map(|x| x.clamp(1.0, 4000.0));
    let (nw, nh) = (size("width"), size("height"));
    with_obj(s, |o| {
        if let InlineObject::Image { crop, w, h, float, .. } = o {
            // The source's full size at the new display size (the visible part is 1-lo-hi of it).
            let full = |len: f32, lo: f32, hi: f32| len / (1.0 - lo - hi).max(0.05);
            // Flipped, the source's right (bottom) edge is the one on the page's left (top).
            let (left, top) = (if float.flip_h { 2 } else { 0 }, if float.flip_v { 3 } else { 1 });
            if let Some(nw) = nw {
                if float.wrap != Wrap::Inline && float.h_align.is_none() {
                    float.x += (c[left] - crop[left]) * full(nw, c[0], c[2]);
                }
                *w = nw;
            }
            if let Some(nh) = nh {
                if float.wrap != Wrap::Inline && float.v_align.is_none() {
                    float.y += (c[top] - crop[top]) * full(nh, c[1], c[3]);
                }
                *h = nh;
            }
            *crop = c;
        }
    })
}

/// A picture's original size: its image's pixels at 96 ppi (the size Insert › Pictures gives it)
/// or a metafile's own frame, before any crop.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct OriginalSize {
    /// Points.
    pub w: f32,
    pub h: f32,
    /// A bitmap's pixels (None for a metafile).
    pub pixels: Option<(u32, u32)>,
}

/// The original size of the picture showing `media`, read from the image its Picture Format
/// adjustments started from (Compress Pictures may have resampled the current one); None when the
/// image can't be read.
pub fn original_size(s: &Session, media: &str) -> Option<OriginalSize> {
    let key = s.originals.get(media).map_or(media, String::as_str);
    let bytes = s.doc.media.get(key).or_else(|| s.doc.media.get(media))?;
    if let Some((pw, ph)) = wordcraft_render::image_size(bytes) {
        return Some(OriginalSize { w: pw as f32 * 0.75, h: ph as f32 * 0.75, pixels: Some((pw, ph)) });
    }
    let (w, h, _) = wordcraft_render::metafile_size(bytes)?;
    Some(OriginalSize { w, h, pixels: None })
}

/// What a picture's crop leaves of its original size: the size Reset gives back and what a scale
/// of the original counts from. None for anything but a picture whose image can be read.
pub fn cropped_original_size(s: &Session, o: &InlineObject) -> Option<(f32, f32)> {
    let InlineObject::Image { media, crop, .. } = o else { return None };
    let orig = original_size(s, media)?;
    let part = |lo: f32, hi: f32| (1.0 - lo - hi).max(0.05);
    Some((orig.w * part(crop[0], crop[2]), orig.h * part(crop[1], crop[3])))
}

/// Size: `scale` is a percentage of the current size, or with `original` of what the crop leaves
/// of the original size (`original` alone gives that back: the Size dialog's Reset); then `width`
/// and `height`, where one alone keeps the proportions unless `lockAspect` is false.
pub(super) fn size(s: &mut Session, v: &Value) -> CmdResult {
    let (_, o) = selected(s).ok_or_else(|| CmdError::Disabled("no object".into()))?;
    let lock = p::bool(v, "lockAspect").unwrap_or(true);
    let (mut w, mut h) = obj_size(&o);
    let scale = p::f32(v, "scale");
    if p::bool(v, "original").unwrap_or(false) {
        let (ow, oh) =
            cropped_original_size(s, &o).ok_or_else(|| CmdError::Disabled("only a picture whose image can be read has an original size".into()))?;
        let k = scale.unwrap_or(100.0) / 100.0;
        (w, h) = (ow * k, oh * k);
    } else if let Some(sc) = scale {
        w *= sc / 100.0;
        h *= sc / 100.0;
    }
    let (nw, nh) = (p::f32(v, "width"), p::f32(v, "height"));
    match (nw, nh) {
        (Some(nw), Some(nh)) => {
            w = nw;
            h = nh;
        }
        (Some(nw), None) => {
            if lock && w > 0.0 {
                h *= nw / w;
            }
            w = nw;
        }
        (None, Some(nh)) => {
            if lock && h > 0.0 {
                w *= nh / h;
            }
            h = nh;
        }
        _ => {}
    }
    // A size that keeps the proportions shrinks on both sides to fit, rather than flattening.
    let keep = match (nw, nh) {
        (Some(_), Some(_)) => false,
        (None, None) => true,
        _ => lock,
    };
    let (w, h) = fit_size(w, h, keep);
    // Any drawing: a group's members scale with its frame (its child space is kept); a chart or
    // SmartArt graphic lays itself out again at the new size.
    let theme = s.doc.settings.theme_colors.clone();
    with_obj(s, |o| {
        o.set_size(w, h);
        crate::cmd::chart::redraw(o, &theme);
        crate::cmd::smart_art::redraw(o, &theme);
    })
}

/// An object size within 1–4000 pt. With `keep`, both sides scale by one factor, the one nearest
/// 1 that brings them within the limits, so the proportions hold; a shape too long and thin for any
/// such factor is clamped side by side.
pub fn fit_size(w: f32, h: f32, keep: bool) -> (f32, f32) {
    let side = |x: f32| if x.is_nan() { 1.0 } else { x.clamp(1.0, 4000.0) };
    if keep && w.is_finite() && h.is_finite() && w > 0.0 && h > 0.0 {
        let (lo, hi) = ((1.0 / w).max(1.0 / h), (4000.0 / w).min(4000.0 / h));
        if lo <= hi {
            let k = if lo > 1.0 {
                lo
            } else if hi < 1.0 {
                hi
            } else {
                1.0
            };
            return (side(w * k), side(h * k));
        }
    }
    (side(w), side(h))
}

/// Apply an image operation to the selected picture's bitmap (stored as a new PNG).
pub(super) fn adjust(s: &mut Session, f: impl Fn(image::DynamicImage) -> Result<image::DynamicImage, CmdError>) -> CmdResult {
    let (_, o) = selected(s).ok_or_else(|| CmdError::Disabled("no picture".into()))?;
    let InlineObject::Image { media, .. } = o else { return Err(CmdError::Disabled("not a picture".into())) };
    adjust_media(s, &media, f)
}

/// Like [`adjust`], but decodes `base` (a media key, usually the pre-edit original) instead
/// of the picture's current bitmap. The result still chains onto the originals map, so Reset
/// Picture keeps working.
pub(super) fn adjust_media(s: &mut Session, base: &str, f: impl Fn(image::DynamicImage) -> Result<image::DynamicImage, CmdError>) -> CmdResult {
    let bytes = s.doc.media.get(base).cloned().ok_or_else(|| CmdError::Failed("picture data missing".into()))?;
    // Within the budget pictures are drawn within, checked from the header first (#3644, #3647).
    let img = wordcraft_geom::picture::decode(&bytes, wordcraft_geom::MAX_DECODED_BYTES).map_err(|e| match e {
        wordcraft_geom::picture::Refusal::TooLarge { .. } => too_large(),
        e => CmdError::Failed(format!("can't decode the picture: {e}")),
    })?;
    let out = f(img)?;
    let mut png = Vec::new();
    out.write_to(&mut std::io::Cursor::new(&mut png), image::ImageFormat::Png).map_err(|e| CmdError::Failed(e.to_string()))?;
    let key = s.doc.add_media(png, "png");
    // Chain onto the ultimate original (the base's, or the current picture's when the base
    // is itself derived), so Reset Picture always finds it.
    let cur = selected(s).and_then(|(_, o)| match o {
        InlineObject::Image { media, .. } => Some(media),
        _ => None,
    });
    let orig = s
        .originals
        .get(base)
        .cloned()
        .or_else(|| cur.as_ref().and_then(|m| s.originals.get(m).cloned()))
        .unwrap_or_else(|| cur.clone().unwrap_or_else(|| key.clone()));
    s.originals.entry(key.clone()).or_insert(orig);
    with_obj(s, |o| {
        // new pixels: the SVG original no longer matches them
        if let InlineObject::Image { media, svg, .. } = o {
            *media = key.clone();
            *svg = None;
        }
    })
}

/// A paint parameter: absent (`None`), `null` (`Some(None)`: none) or an RRGGBB colour.
pub(super) fn paint(v: &Value, key: &str) -> Result<Option<Option<[u8; 3]>>, CmdError> {
    match v.get(key) {
        None => Ok(None),
        Some(Value::Null) => Ok(Some(None)),
        Some(c) => match c.as_str().and_then(Rgb::parse) {
            Some(c) => Ok(Some(Some([c.0, c.1, c.2]))),
            None => Err(CmdError::Params(format!("`{key}` is RRGGBB or null"))),
        },
    }
}

/// Repaints the selected icon's SVG (`r` from its picture and the picture's units in a point) and
/// draws the picture kept beside it again from the new SVG, at its pixel size. Reset Picture finds
/// the first picture and SVG through the originals.
pub(super) fn repaint(s: &mut Session, r: impl Fn(&wordcraft_metafile::Picture, f32) -> Recolor) -> CmdResult {
    let Some((_, InlineObject::Image { media, svg: Some(svg), w, h, .. })) = selected(s) else {
        return Err(CmdError::Disabled("select an icon first".into()));
    };
    let bytes = s.doc.media.get(&svg).cloned().ok_or_else(|| CmdError::Failed("icon data missing".into()))?;
    let pic = wordcraft_metafile::parse(&bytes).map_err(|e| CmdError::Failed(format!("can't read the icon: {e}")))?;
    let per_pt = if w > 0.0 && pic.width.is_finite() { pic.width / w } else { 1.0 };
    let new = wordcraft_metafile::recolor(&bytes, r(&pic, per_pt)).ok_or_else(|| CmdError::Failed("the icon has nothing to change".into()))?;
    // the picture's pixel size, or about 300 pixels per inch
    let px = |pt: f32| (pt * 300.0 / 72.0).round().clamp(16.0, 1024.0) as u32;
    let (pw, ph) = s.doc.media.get(&media).and_then(|b| wordcraft_render::image_size(b)).unwrap_or((px(w), px(h)));
    let png = wordcraft_render::vector_image(&new, pw, ph).map(|r| r.to_png()).filter(|p| !p.is_empty());
    let png = png.ok_or_else(|| CmdError::Failed("couldn't draw the icon".into()))?;
    let (png, new) = (s.doc.add_media(png, "png"), s.doc.add_media(new, "svg"));
    let first = s.originals.get(&media).cloned().unwrap_or(media);
    s.originals.entry(png.clone()).or_insert(first);
    let first = s.originals.get(&svg).cloned().unwrap_or(svg);
    s.originals.entry(new.clone()).or_insert(first);
    with_obj(s, |o| {
        if let InlineObject::Image { media, svg, .. } = o {
            *media = png.clone();
            *svg = Some(new.clone());
        }
    })
}

fn too_large() -> CmdError {
    CmdError::Failed("picture is too large to edit".into())
}

/// A `w` × `h` bitmap of `fill` for an edit to draw on, refused when the picture it makes would be
/// too large to draw (two RGBA buffers within the decode budget), before it is allocated (#3611).
fn canvas(w: u32, h: u32, fill: [u8; 4]) -> Result<image::RgbaImage, CmdError> {
    if !wordcraft_geom::decode_fits(w, h, wordcraft_geom::MAX_DECODED_BYTES) {
        return Err(too_large());
    }
    Ok(image::RgbaImage::from_pixel(w, h, image::Rgba(fill)))
}

/// `w` and `h` grown by `pad` on every side, None past `u32`.
pub(super) fn grown(w: u32, h: u32, pad: u32) -> Option<(u32, u32)> {
    let twice = pad.checked_mul(2)?;
    Some((w.checked_add(twice)?, h.checked_add(twice)?))
}

/// What Artistic Effects, Picture Styles and Picture Border drew on a picture's bitmap: the
/// bitmap they started from and the effect, style and border on it, so the next one draws again
/// from that bitmap instead of over the last result. Picking a second style replaces the first,
/// as in Word, instead of framing the frame (#2052). Kept in [`Session::picture_looks`] under the
/// media key of the drawn bitmap.
#[derive(Clone, Debug, Default)]
pub struct PictureLook {
    /// The media key of the bitmap before any of them.
    pub base: String,
    pub effect: Option<String>,
    pub style: Option<String>,
    pub border: Option<(Rgb, u32)>,
    /// The drawn bitmap: a media key that names other bytes later (undo then a new picture
    /// reusing the key) is not this look's.
    pub drawn: std::sync::Arc<Vec<u8>>,
}

/// Change the selected picture's effect, style or border and draw it again from the bitmap
/// under them (effect, then style, then border).
pub(super) fn relook(s: &mut Session, change: impl FnOnce(&mut PictureLook)) -> CmdResult {
    let (_, o) = selected(s).ok_or_else(|| CmdError::Disabled("no picture".into()))?;
    let InlineObject::Image { media, .. } = o else { return Err(CmdError::Disabled("not a picture".into())) };
    let cur = s.doc.media.get(&media).cloned();
    let mut look = s
        .picture_looks
        .get(&media)
        .filter(|l| cur.as_ref().is_some_and(|c| std::sync::Arc::ptr_eq(c, &l.drawn)) && s.doc.media.contains_key(&l.base))
        .cloned()
        .unwrap_or_else(|| PictureLook { base: media.clone(), ..Default::default() });
    change(&mut look);
    if look.effect.is_none() && look.style.is_none() && look.border.is_none() {
        // Nothing left on it: the bitmap as it was.
        let base = look.base.clone();
        return with_obj(s, |o| {
            if let InlineObject::Image { media, .. } = o {
                *media = base.clone();
            }
        });
    }
    let l = look.clone();
    let out = adjust_media(s, &look.base, move |img| {
        let mut img = match &l.effect {
            Some(e) => artistic_effect(img, e),
            None => img,
        };
        if let Some(st) = &l.style {
            img = raster_picture_style(img, st)?;
        }
        if let Some((c, w)) = l.border {
            img = frame(img, w, [c.0, c.1, c.2, 255])?;
        }
        Ok(img)
    })?;
    if let Some((_, InlineObject::Image { media, .. })) = selected(s)
        && let Some(drawn) = s.doc.media.get(&media).cloned()
    {
        s.picture_looks.insert(media, PictureLook { drawn, ..look });
    }
    Ok(out)
}

fn artistic_effect(img: image::DynamicImage, effect: &str) -> image::DynamicImage {
    match effect {
        "blur" => img.blur(3.0),
        "sharpen" => img.unsharpen(2.0, 2),
        "invert" => {
            let mut i = img;
            i.invert();
            i
        }
        "posterize" => {
            let mut rgba = img.to_rgba8();
            for px in rgba.pixels_mut() {
                for c in px.0.iter_mut().take(3) {
                    *c = (*c / 64) * 85;
                }
            }
            image::DynamicImage::ImageRgba8(rgba)
        }
        "pixelate" => {
            let (w, h) = (img.width().max(1), img.height().max(1));
            img.resize_exact((w / 12).max(1), (h / 12).max(1), image::imageops::FilterType::Nearest).resize_exact(
                w,
                h,
                image::imageops::FilterType::Nearest,
            )
        }
        _ => img,
    }
}

pub(super) fn reset(s: &mut Session, _: &Value) -> CmdResult {
    let (_, o) = selected(s).ok_or_else(|| CmdError::Disabled("no picture".into()))?;
    let InlineObject::Image { media, svg: first_svg, .. } = o else { return Err(CmdError::Disabled("not a picture".into())) };
    let orig = s.originals.get(&media).cloned().unwrap_or(media);
    // an icon's first colours
    let first_svg = first_svg.map(|k| s.originals.get(&k).cloned().unwrap_or(k));
    with_obj(s, |o| {
        // its own pixels again, uncropped, without its style
        if let InlineObject::Image { media, svg, crop, framing, effects, float, .. } = o {
            *media = orig.clone();
            *svg = first_svg.clone();
            *crop = [0.0; 4];
            *framing = Default::default();
            *effects = Default::default();
            float.effect = [0.0; 4];
        }
    })
}

/// Picture Styles, in the gallery's order (three to a row): white frames, black frames, black
/// frames of two or three lines, then soft edges, shadows, reflections and the other shapes.
pub const PICTURE_STYLES: [&str; 24] = [
    "simpleFrameWhite",
    "moderateFrameWhite",
    "thickMatteWhite",
    "simpleFrameBlack",
    "thickMatteBlack",
    "moderateFrameBlack",
    "doubleFrameBlack",
    "compoundFrameBlack",
    "tripleFrameBlack",
    "softEdgeRectangle",
    "dropShadowWhite",
    "metalFrame",
    "dropShadowRectangle",
    "reflectedRectangle",
    "perspectiveShadowWhite",
    "softEdgeOval",
    "metalOval",
    "ovalBlack",
    "roundedDiagonalWhite",
    "snipDiagonalWhite",
    "reflectedRoundedRectangle",
    "centerShadowRectangle",
    "metalRoundedRectangle",
    "roundedShadowWhite",
];

/// How Picture Style `id` frames a picture: its outline and the shape it is cut to, and its
/// effects. The first five style names (simpleFrame, thickFrame, rounded, softEdge, shadow)
/// still work.
pub fn picture_style(id: &str) -> Option<(wordcraft_doc::picture::PictureFrame, wordcraft_doc::effects::ShapeEffects)> {
    use wordcraft_doc::effects::{Reflection, Shadow, ShapeEffects};
    use wordcraft_doc::picture::{Compound, FrameLine, PictureFrame};
    let line = |color: Rgb, width: f32, compound: Compound| Some(FrameLine { color, width, compound });
    let (white, black, metal) = (Rgb::WHITE, Rgb::BLACK, Rgb(0xA6, 0xA6, 0xA6));
    // a soft shadow down and to the right; a deeper one; one all round
    let soft = Shadow { color: Rgb::BLACK, transparency: 60.0, blur: 4.0, distance: 2.5, angle: 45.0, rot_with_shape: false, ..Shadow::default() };
    let deep = Shadow { transparency: 50.0, blur: 9.0, distance: 5.0, ..soft };
    let round = Shadow { transparency: 45.0, blur: 12.0, distance: 0.0, ..soft };
    let shadow = |sh: Shadow| ShapeEffects { shadow: Some(sh), ..Default::default() };
    let soft_edge = ShapeEffects { soft_edge: Some(9.0), ..Default::default() };
    let reflection = ShapeEffects { reflection: Reflection::preset("tightTouching"), ..Default::default() };
    let id = match id {
        "simpleFrame" => "simpleFrameWhite",
        "thickFrame" => "thickMatteWhite",
        "rounded" => "metalRoundedRectangle",
        "softEdge" => "softEdgeRectangle",
        "shadow" => "dropShadowRectangle",
        id => id,
    };
    let (outline, shape, effects) = match id {
        "simpleFrameWhite" => (line(white, 7.0, Compound::Single), None, shadow(soft)),
        "moderateFrameWhite" => (line(white, 4.0, Compound::Single), None, shadow(soft)),
        "thickMatteWhite" => (line(white, 13.0, Compound::Single), None, shadow(soft)),
        "simpleFrameBlack" => (line(black, 7.0, Compound::Single), None, ShapeEffects::default()),
        "thickMatteBlack" => (line(black, 13.0, Compound::Single), None, ShapeEffects::default()),
        "moderateFrameBlack" => (line(black, 9.0, Compound::Single), None, shadow(round)),
        "doubleFrameBlack" => (line(black, 7.0, Compound::Double), None, ShapeEffects::default()),
        "compoundFrameBlack" => (line(black, 10.0, Compound::ThickThin), None, ShapeEffects::default()),
        "tripleFrameBlack" => (line(black, 13.0, Compound::Triple), None, shadow(soft)),
        "softEdgeRectangle" => (None, None, soft_edge),
        "dropShadowWhite" => (line(white, 5.0, Compound::Single), None, shadow(deep)),
        "metalFrame" => (line(metal, 9.0, Compound::ThinThick), None, shadow(soft)),
        "dropShadowRectangle" => (None, None, shadow(deep)),
        "reflectedRectangle" => (None, None, reflection),
        "perspectiveShadowWhite" => {
            (line(white, 4.0, Compound::Single), None, ShapeEffects { shadow: Shadow::preset("perspectiveUpperLeft"), ..Default::default() })
        }
        "softEdgeOval" => (None, Some("ellipse"), soft_edge),
        "metalOval" => (line(metal, 9.0, Compound::ThinThick), Some("ellipse"), shadow(soft)),
        "ovalBlack" => (line(black, 6.0, Compound::Single), Some("ellipse"), shadow(soft)),
        "roundedDiagonalWhite" => (line(white, 7.0, Compound::Single), Some("round2DiagRect"), shadow(soft)),
        "snipDiagonalWhite" => (line(white, 7.0, Compound::Single), Some("snip2DiagRect"), shadow(soft)),
        "reflectedRoundedRectangle" => (None, Some("roundRect"), reflection),
        "centerShadowRectangle" => (None, None, shadow(round)),
        "metalRoundedRectangle" => (line(metal, 9.0, Compound::ThinThick), Some("roundRect"), shadow(soft)),
        "roundedShadowWhite" => (line(white, 5.0, Compound::Single), Some("roundRect"), shadow(deep)),
        _ => return None,
    };
    Some((PictureFrame { line: outline, shape: shape.map(str::to_string) }.sanitized(), effects.sanitized()))
}

/// Room around a picture for its effects and its outline, as Word's effect extent.
pub(super) fn picture_extent(effects: &wordcraft_doc::effects::ShapeEffects, framing: &wordcraft_doc::picture::PictureFrame, h: f32) -> [f32; 4] {
    let reach = framing.reach();
    effects.extent_for(h).map(|e| e + reach)
}

/// Picture Styles: the style's outline, shape and effects replace the selected picture's own.
pub(super) fn picture_style_cmd(s: &mut Session, v: &Value) -> CmdResult {
    let id = p::str(v, "style").unwrap_or("simpleFrameWhite");
    let (look, fx) = picture_style(id).ok_or_else(|| CmdError::Params(format!("unknown picture style `{id}`")))?;
    with_obj(s, |o| {
        if let InlineObject::Image { framing, effects, float, h, .. } = o {
            *framing = look.clone();
            *effects = fx;
            float.effect = picture_extent(effects, framing, *h);
        }
    })
}

/// Picture Border: the selected picture's outline, each value given replacing its own.
pub(super) fn picture_border(s: &mut Session, v: &Value) -> CmdResult {
    use wordcraft_doc::picture::{Compound, FrameLine};
    let off = p::str(v, "color") == Some("none");
    let color = match p::str(v, "color").filter(|_| !off) {
        Some(c) => Some(Rgb::parse(c).ok_or_else(|| CmdError::Params(format!("`color`: RRGGBB or none, not `{c}`")))?),
        None => None,
    };
    let compound = match p::str(v, "compound") {
        Some(c) => {
            Some(Compound::from_id(c).ok_or_else(|| CmdError::Params(format!("`compound`: sng, dbl, thickThin, thinThick or tri, not `{c}`")))?)
        }
        None => None,
    };
    let width = p::f32(v, "width");
    with_obj(s, |o| {
        if let InlineObject::Image { framing, effects, float, h, .. } = o {
            framing.line = if off {
                None
            } else {
                let was = framing.line.unwrap_or(FrameLine { color: Rgb::BLACK, width: 0.75, compound: Compound::Single });
                Some(
                    FrameLine { color: color.unwrap_or(was.color), width: width.unwrap_or(was.width), compound: compound.unwrap_or(was.compound) }
                        .sanitized(),
                )
            };
            float.effect = picture_extent(effects, framing, *h);
        }
    })
}

/// A small picture of WordCraft's own (a sky, a sun and two hills), PNG: the Picture Styles
/// gallery draws each style on it.
pub fn sample_picture_png() -> Vec<u8> {
    let (w, h) = (96u32, 72u32);
    let img = image::RgbaImage::from_fn(w, h, |x, y| {
        let (fx, fy) = (x as f32 / w as f32, y as f32 / h as f32);
        let sun = ((fx - 0.72).powi(2) + ((fy - 0.28) * 0.75).powi(2)).sqrt() < 0.09;
        let far = fy > 0.6 + 0.1 * (fx * 3.0).sin();
        let near = fy > 0.74 - 0.1 * (fx * 2.2 + 1.0).sin();
        let [r, g, b] = if near {
            [62, 140, 72]
        } else if far {
            [104, 178, 96]
        } else if sun {
            [250, 204, 64]
        } else {
            [(110.0 + 60.0 * fy) as u8, (165.0 + 40.0 * fy) as u8, (230.0 + 15.0 * fy) as u8]
        };
        image::Rgba([r, g, b, 255])
    });
    let mut png = Vec::new();
    let _ = image::DynamicImage::ImageRgba8(img).write_to(&mut std::io::Cursor::new(&mut png), image::ImageFormat::Png);
    png
}

pub(super) fn recolor(img: image::DynamicImage, mode: &str, sat: f32) -> image::DynamicImage {
    let mut rgba = img.to_rgba8();
    for px in rgba.pixels_mut() {
        let [r, g, b, a] = px.0;
        let (rf, gf, bf) = (r as f32, g as f32, b as f32);
        let l = 0.299 * rf + 0.587 * gf + 0.114 * bf;
        let out = match mode {
            "grayscale" => [l, l, l],
            "sepia" => [
                (0.393 * rf + 0.769 * gf + 0.189 * bf).min(255.0),
                (0.349 * rf + 0.686 * gf + 0.168 * bf).min(255.0),
                (0.272 * rf + 0.534 * gf + 0.131 * bf).min(255.0),
            ],
            "washout" => [rf * 0.35 + 255.0 * 0.65, gf * 0.35 + 255.0 * 0.65, bf * 0.35 + 255.0 * 0.65],
            "blackAndWhite" => {
                let v = if l > 127.0 { 255.0 } else { 0.0 };
                [v, v, v]
            }
            "tint" => [l * 0.6 + 0.4 * 0x3B as f32, l * 0.6 + 0.4 * 0x5B as f32, l * 0.6 + 0.4 * 0xDB as f32],
            _ => {
                let k = sat / 100.0;
                [l + (rf - l) * k, l + (gf - l) * k, l + (bf - l) * k]
            }
        };
        px.0 = [out[0].clamp(0.0, 255.0) as u8, out[1].clamp(0.0, 255.0) as u8, out[2].clamp(0.0, 255.0) as u8, a];
    }
    image::DynamicImage::ImageRgba8(rgba)
}

/// Flood the border-connected region close to the corner colour with transparency.
pub(super) fn remove_background(img: image::DynamicImage, tol: f32) -> image::DynamicImage {
    let mut rgba = img.to_rgba8();
    clear_background(&mut rgba, tol);
    image::DynamicImage::ImageRgba8(rgba)
}

/// [`remove_background`] on `rgba`, a row span at a time: besides the border's seeds, the stack
/// holds one for each run of matching pixels beside a filled span, instead of four for every
/// pixel filled (#3647). Returns the most seeds it held.
pub(super) fn clear_background(rgba: &mut image::RgbaImage, tol: f32) -> usize {
    let (w, h) = rgba.dimensions();
    let Some(bg) = rgba.get_pixel_checked(0, 0).map(|p| p.0) else { return 0 };
    let close = |p: [u8; 4]| (0..3).map(|i| (p[i] as f32 - bg[i] as f32).abs()).fold(0.0f32, f32::max) <= tol;
    let at = |x: u32, y: u32| y as usize * w as usize + x as usize;
    // Not cleared yet, and close to the corner colour.
    let open = |rgba: &image::RgbaImage, seen: &[bool], x: u32, y: u32| {
        !seen.get(at(x, y)).copied().unwrap_or(true) && rgba.get_pixel_checked(x, y).is_some_and(|p| close(p.0))
    };
    let mut seen = vec![false; (w as usize) * (h as usize)];
    let mut stack = border_pixels(w, h);
    let mut most = stack.len();
    while let Some((x, y)) = stack.pop() {
        if !open(rgba, &seen, x, y) {
            continue;
        }
        let (x0, x1) = run_span(x, y, w, |xi, yi| open(rgba, &seen, xi, yi));
        for xi in x0..=x1 {
            if let Some(s) = seen.get_mut(at(xi, y)) {
                *s = true;
            }
            if let Some(p) = rgba.get_pixel_mut_checked(xi, y) {
                p.0[3] = 0;
            }
        }
        for ny in [y.checked_sub(1), y.checked_add(1).filter(|&n| n < h)].into_iter().flatten() {
            push_open_runs(&mut stack, x0, x1, ny, |xi, yi| open(rgba, &seen, xi, yi));
        }
        most = most.max(stack.len());
    }
    most
}

/// Every pixel on the image's edge, the flood-fill seeds.
fn border_pixels(w: u32, h: u32) -> Vec<(u32, u32)> {
    let mut stack = Vec::new();
    for x in 0..w {
        stack.push((x, 0));
        stack.push((x, h - 1));
    }
    for y in 0..h {
        stack.push((0, y));
        stack.push((w - 1, y));
    }
    stack
}

/// The horizontal run of open pixels around `(x, y)`, both ends included.
fn run_span(x: u32, y: u32, w: u32, open: impl Fn(u32, u32) -> bool) -> (u32, u32) {
    let (mut x0, mut x1) = (x, x);
    while x0 > 0 && open(x0 - 1, y) {
        x0 -= 1;
    }
    while x1 + 1 < w && open(x1 + 1, y) {
        x1 += 1;
    }
    (x0, x1)
}

/// Queues the first pixel of each open run in row `ny` between `x0` and `x1`.
fn push_open_runs(stack: &mut Vec<(u32, u32)>, x0: u32, x1: u32, ny: u32, open: impl Fn(u32, u32) -> bool) {
    let mut xi = x0;
    while xi <= x1 {
        if open(xi, ny) {
            stack.push((xi, ny));
            while xi <= x1 && open(xi, ny) {
                xi += 1;
            }
        } else {
            xi += 1;
        }
    }
}

pub(super) fn frame(img: image::DynamicImage, border: u32, color: [u8; 4]) -> Result<image::DynamicImage, CmdError> {
    let (w, h) = grown(img.width(), img.height(), border).ok_or_else(too_large)?;
    let mut out = canvas(w, h, color)?;
    image::imageops::overlay(&mut out, &img.to_rgba8(), border as i64, border as i64);
    Ok(image::DynamicImage::ImageRgba8(out))
}

pub(super) fn raster_picture_style(img: image::DynamicImage, style: &str) -> Result<image::DynamicImage, CmdError> {
    let m = (img.width().max(img.height()) / 40).max(4);
    Ok(match style {
        "thickFrame" => frame(frame(img, m * 2, [255, 255, 255, 255])?, m / 2, [40, 40, 40, 255])?,
        "rounded" | "softEdge" => {
            let mut rgba = img.to_rgba8();
            let (w, h) = rgba.dimensions();
            let r = (w.min(h) as f32) * if style == "rounded" { 0.12 } else { 0.0 };
            let feather = if style == "softEdge" { (w.min(h) as f32 * 0.08).max(2.0) } else { 1.5 };
            for y in 0..h {
                for x in 0..w {
                    let (fx, fy) = (x as f32 + 0.5, y as f32 + 0.5);
                    let dx = (r - fx).max(fx - (w as f32 - r)).max(0.0);
                    let dy = (r - fy).max(fy - (h as f32 - r)).max(0.0);
                    let edge = (fx.min(w as f32 - fx)).min(fy.min(h as f32 - fy));
                    let inside = if r > 0.0 && dx > 0.0 && dy > 0.0 { r - (dx * dx + dy * dy).sqrt() } else { edge };
                    let k = (inside / feather).clamp(0.0, 1.0);
                    let p = rgba.get_pixel_mut(x, y);
                    p.0[3] = (p.0[3] as f32 * k) as u8;
                }
            }
            image::DynamicImage::ImageRgba8(rgba)
        }
        "shadow" => {
            let (w, h) = (img.width(), img.height());
            let off = m;
            let (pw, ph) = grown(w, h, off).ok_or_else(too_large)?;
            let mut out = canvas(pw, ph, [0, 0, 0, 0])?;
            // A rectangle of alpha 90 the picture's size, one and a half margins in, blurred. Its
            // blur is the product of its sides' blurs along each axis, so it is drawn from two
            // profiles, not by blurring every pixel with a kernel that grows with the picture (#3647).
            let (sigma, start) = (off as f32 / 2.0, off + off / 2);
            let (xs, ys) = (blurred_span(pw, start, w, sigma), blurred_span(ph, start, h, sigma));
            for (x, y, p) in out.enumerate_pixels_mut() {
                let k = xs.get(x as usize).copied().unwrap_or(0.0) * ys.get(y as usize).copied().unwrap_or(0.0);
                p.0[3] = (90.0 * k).round() as u8;
            }
            image::imageops::overlay(&mut out, &img.to_rgba8(), 0, 0);
            image::DynamicImage::ImageRgba8(out)
        }
        _ => frame(img, m, [255, 255, 255, 255])?,
    })
}

/// How much of a span `len` long from `start` covers each of `n` positions once blurred with the
/// Gaussian `image::imageops::blur` uses for `sigma` (outside the `n` positions counts as clear):
/// 0 to 1. Each position is one difference of the kernel's running sums.
fn blurred_span(n: u32, start: u32, len: u32, sigma: f32) -> Vec<f32> {
    let kernel = gaussian_kernel(sigma);
    let mut sums = vec![0.0f32];
    for k in &kernel {
        sums.push(sums.last().copied().unwrap_or(0.0) + k);
    }
    let (size, r) = (kernel.len() as i64, (kernel.len() / 2) as i64);
    let sum_to = |k: i64| sums.get(k.clamp(0, size) as usize).copied().unwrap_or(0.0);
    let (start, end) = (i64::from(start), i64::from(start) + i64::from(len));
    // Position i takes weight k from i + k - r, inside the span for k in [start - i + r, end - i + r).
    (0..i64::from(n)).map(|i| sum_to(end - i + r) - sum_to(start - i + r)).collect()
}

/// The normalized kernel `image::imageops::blur` (0.25) uses for `sigma`: odd, about 6.7 σ wide.
fn gaussian_kernel(sigma: f32) -> Vec<f32> {
    let size = ((((sigma - 0.8) / 0.3 + 1.0) * 2.0 + 1.0).max(3.0) as usize) | 1;
    let mean = (size / 2) as f32;
    let weights: Vec<f32> = (0..size).map(|x| (-0.5 * ((x as f32 - mean) / sigma).powi(2)).exp()).collect();
    let total: f32 = weights.iter().sum();
    weights.into_iter().map(|v| v / total).collect()
}

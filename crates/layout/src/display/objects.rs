use super::options::DisplayOptions;
use super::paint::{Paint, paint_for};
use super::{BezPath, Document, Draw, FaceRef, Graphic, GraphicItem, GraphicKind, InlineObject, PathSeg, Rect, Rgb, ShapeKind, Spin, TextAlign};
use wordcraft_doc::connector::ArrowStyle;

/// The draws of a chart or diagram's items inside `rect` (page coordinates). The items were built
/// for the size `g.w` × `g.h` (0: `rect`'s own size), so they are scaled to `rect`; strokes and text
/// by the mean of the two scales.
pub(super) fn graphic_draws(doc: &Document, g: &Graphic, rect: Rect, alpha: f32) -> Vec<Draw> {
    let (sx, sy) = (scale_of(rect.w, g.w), scale_of(rect.h, g.h));
    let k = (sx + sy) / 2.0;
    let inside = |r: &[f32; 4]| Rect::new(rect.x + r[0] * sx, rect.y + r[1] * sy, r[2] * sx, r[3] * sy);
    let mut out = Vec::with_capacity(g.items.len());
    for it in &g.items {
        match it {
            GraphicItem::Shape { rect: r, kind, fill, stroke, stroke_width } => out.push(Draw::Shape {
                rect: inside(r),
                kind: *kind,
                fill: fill.map(Paint::solid),
                stroke: *stroke,
                stroke_width: stroke_width * k,
                effects: Default::default(),
                corner: None,
                dash: Vec::new(),
                cap: Default::default(),
            }),
            GraphicItem::Path { segs, fill, stroke, stroke_width } => {
                let segs = page_segs(segs, |x, y| (rect.x + x * sx, rect.y + y * sy));
                out.push(Draw::Path {
                    segs,
                    fill: fill.map(Paint::solid),
                    stroke: *stroke,
                    stroke_width: stroke_width * k,
                    dash: Vec::new(),
                    cap: Default::default(),
                })
            }
            GraphicItem::Text { rect: r, .. } => out.extend(text_glyphs(doc, inside(r), it, alpha, k)),
            GraphicItem::Image { rect: r, media } => out.push(Draw::Image {
                rect: inside(r),
                media: media.clone(),
                crop: [0.0; 4],
                alpha,
                alt: String::new(),
                decorative: false,
                svg: None,
            }),
        }
    }
    out
}

/// Scale from the size `built` points a graphic's items were made for to `len` points; 1 when
/// the built size is unknown (0).
fn scale_of(len: f32, built: f32) -> f32 {
    if built.is_finite() && built > 0.0 && len.is_finite() { (len / built).max(0.0) } else { 1.0 }
}

/// Path segments mapped to page coordinates by `map`, cleaned for the rasterizer and PDF: a segment
/// with a non-finite number is dropped, and so is anything before a move (or after a broken one).
fn page_segs(segs: &[PathSeg], map: impl Fn(f32, f32) -> (f32, f32)) -> Vec<PathSeg> {
    let fin = |p: (f32, f32)| p.0.is_finite() && p.1.is_finite();
    let mut out = Vec::with_capacity(segs.len());
    let mut open = false;
    for s in segs {
        match *s {
            PathSeg::Move(x, y) => {
                let p = map(x, y);
                open = fin(p);
                if open {
                    out.push(PathSeg::Move(p.0, p.1));
                }
            }
            PathSeg::Line(x, y) => {
                let p = map(x, y);
                if open && fin(p) {
                    out.push(PathSeg::Line(p.0, p.1));
                }
            }
            PathSeg::Cubic(a, b, c, d, e, f) => {
                let (p1, p2, p3) = (map(a, b), map(c, d), map(e, f));
                if open && fin(p1) && fin(p2) && fin(p3) {
                    out.push(PathSeg::Cubic(p1.0, p1.1, p2.0, p2.1, p3.0, p3.1));
                }
            }
            PathSeg::Close => {
                if open {
                    out.push(PathSeg::Close);
                }
                open = false;
            }
        }
    }
    out
}

/// A path of clean segments (see [`page_segs`]) as a kurbo path, for the rasteriser and PDF.
/// How a shape looks: its preset `kind`, fill, outline, effects, freeform paths and connector extras.
#[derive(Clone, Copy)]
pub(super) struct ShapeLook<'a> {
    pub kind: ShapeKind,
    pub fill: Option<&'a wordcraft_doc::fill::Fill>,
    pub stroke: Option<Rgb>,
    pub stroke_width: f32,
    pub effects: wordcraft_doc::effects::ShapeEffects,
    pub freeform: Option<&'a wordcraft_doc::freeform::Freeform>,
    pub extra: &'a wordcraft_doc::connector::ShapeExtra,
}

/// The draws of a shape at `rect`: a preset outline, or a freeform's paths (an ink stroke left out
/// under Review › Hide Ink).
pub(super) fn shape_draws(rect: Rect, look: &ShapeLook, opts: &DisplayOptions, out: &mut Vec<Draw>) {
    let ShapeLook { kind, fill, stroke, stroke_width, effects, freeform, extra } = *look;
    // Freeforms are painted across the shape's frame, not each path's bounds.
    let fill = extra.effective_fill(fill).as_ref().and_then(|f| paint_for(f, rect));
    if freeform.is_none()
        && let Some(o) = extra.preset.as_deref().and_then(|p| wordcraft_doc::preset::outline(p, rect.w, rect.h, &extra.adj))
    {
        preset_draws(rect, &o, fill, stroke, stroke_width, effects, &extra.dash.pattern(stroke_width), out);
        return;
    }
    if kind.is_open() {
        line_draws(rect, kind, stroke, stroke_width, effects, extra, out);
        return;
    }
    let Some(f) = freeform.filter(|_| kind == ShapeKind::Freeform) else {
        out.push(Draw::Shape {
            rect,
            kind,
            fill,
            stroke,
            stroke_width,
            effects,
            corner: extra.corner,
            dash: extra.dash.pattern(stroke_width),
            cap: extra.cap,
        });
        return;
    };
    if f.is_ink() && opts.hide_ink {
        return;
    }
    let width = if stroke_width.is_finite() { stroke_width.clamp(0.0, 200.0) } else { 0.75 };
    for (pts, closed) in f.placed(rect.x, rect.y, rect.w, rect.h) {
        if f.is_ink() || (!closed && fill.is_none()) {
            if let Some(color) = stroke {
                let mut pts: Vec<(f32, f32)> = pts.iter().map(|[x, y]| (*x, *y)).collect();
                if closed && let Some(first) = pts.first().copied() {
                    pts.push(first);
                }
                if !closed
                    && extra.arrow_end
                    && let Some(arrow) = path_arrowhead(&pts, color, width, extra.end_style)
                {
                    out.push(arrow);
                }
                out.push(Draw::Ink { pts, color, width: width.max(0.25), alpha: f.alpha });
            }
            continue;
        }
        let mut segs: Vec<PathSeg> = Vec::with_capacity(pts.len() + 1);
        for (i, [x, y]) in pts.iter().enumerate() {
            segs.push(if i == 0 { PathSeg::Move(*x, *y) } else { PathSeg::Line(*x, *y) });
        }
        if closed {
            segs.push(PathSeg::Close);
        }
        let width = path_stroke_width(width);
        out.push(Draw::Path {
            segs,
            fill: if closed { fill.clone() } else { None },
            stroke,
            stroke_width: width,
            dash: extra.dash.pattern(width),
            cap: extra.cap,
        });
        if !closed
            && extra.arrow_end
            && let Some(color) = stroke
        {
            let points: Vec<(f32, f32)> = pts.iter().map(|[x, y]| (*x, *y)).collect();
            if let Some(arrow) = path_arrowhead(&points, color, width, extra.end_style) {
                out.push(arrow);
            }
        }
    }
}

/// A preset shape's outline (see [`wordcraft_doc::preset`]) at `rect`: its fill, its shaded
/// faces, then its lines.
fn preset_draws(
    rect: Rect,
    o: &wordcraft_doc::preset::Outline,
    fill: Option<Paint>,
    stroke: Option<Rgb>,
    stroke_width: f32,
    effects: wordcraft_doc::effects::ShapeEffects,
    dash: &[f32],
    out: &mut Vec<Draw>,
) {
    let width = if stroke_width.is_finite() { stroke_width.clamp(0.0, 200.0) } else { 0.75 };
    let path = |rings: &mut dyn Iterator<Item = &Vec<[f32; 2]>>, close: bool| -> Vec<PathSeg> {
        let mut segs = Vec::new();
        for ring in rings {
            for (i, [x, y]) in ring.iter().enumerate() {
                let (x, y) = (rect.x + x, rect.y + y);
                segs.push(if i == 0 { PathSeg::Move(x, y) } else { PathSeg::Line(x, y) });
            }
            if close && !ring.is_empty() {
                segs.push(PathSeg::Close);
            }
        }
        segs
    };
    let edge = stroke.filter(|_| o.stroke_fill);
    // its shadow and glow, from the filled outline (or the lines, when nothing is filled)
    if !effects.is_empty() {
        let filled = fill.is_some() && !o.fill.is_empty();
        let segs = if filled { path(&mut o.fill.iter(), true) } else { path(&mut o.lines.iter().chain(o.fill.iter()), false) };
        if filled || stroke.is_some() {
            out.push(Draw::Effects { segs, effects, filled, stroke_width: width });
        }
    }
    if !o.fill.is_empty() {
        out.push(Draw::Path {
            segs: path(&mut o.fill.iter(), true),
            fill: fill.clone(),
            stroke: edge,
            stroke_width: width,
            dash: dash.to_vec(),
            cap: Default::default(),
        });
    }
    for (shade, ring) in &o.faces {
        let face = fill.as_ref().map(|paint| {
            let (c, alpha) = paint.at(rect.x + rect.w / 2.0, rect.y + rect.h / 2.0);
            let [r, g, b] = shade.apply([c.0, c.1, c.2]);
            Paint::Solid(Rgb(r, g, b), alpha)
        });
        out.push(Draw::Path {
            segs: path(&mut std::iter::once(ring), true),
            fill: face,
            stroke: edge,
            stroke_width: width,
            dash: dash.to_vec(),
            cap: Default::default(),
        });
    }
    if stroke.is_some() && !o.lines.is_empty() {
        out.push(Draw::Path {
            segs: path(&mut o.lines.iter(), false),
            fill: None,
            stroke,
            stroke_width: width,
            dash: dash.to_vec(),
            cap: Default::default(),
        });
    }
}

/// How many strips a reflection fades in: about one for each 2 points of its length, between
/// these (fewer show as steps where the reference's fade is smooth).
const REFLECTION_STRIPS: (usize, usize) = (12, 48);

/// `items` (a picture or shape drawn in `rect`, its effects first) and then its reflection, if
/// its `effects` have one: the drawing (not its shadow or glow) mirrored about a line half the
/// reflection's distance below its bottom, in strips that fade from the start opacity at the
/// top to the end opacity the reflection's size down.
pub(super) fn reflected(rect: Rect, effects: &wordcraft_doc::effects::ShapeEffects, mut items: Vec<Draw>) -> Vec<Draw> {
    let Some(r) = effects.sanitized().reflection else { return items };
    let len = rect.h * r.size / 100.0;
    if !(len > 0.0 && rect.w > 0.0) {
        return items;
    }
    // the drawing without its shadows and glow (they aren't reflected; an inner shadow is)
    let shadow = |d: &Draw| match d {
        Draw::Effects { .. } => true,
        Draw::Kept { items } => items.iter().all(|i| matches!(i, Draw::Effects { .. })),
        _ => false,
    };
    let body: Vec<Draw> = items.iter().filter(|d| !shadow(d)).cloned().collect();
    let mirrored = Draw::Rotated { cx: rect.x + rect.w / 2.0, cy: rect.bottom() + r.distance / 2.0, spin: Spin::new(0.0, false, true), items: body };
    let top = rect.bottom() + r.distance;
    let strips = ((len / 2.0).ceil() as usize).clamp(REFLECTION_STRIPS.0, REFLECTION_STRIPS.1);
    let n = strips as f32;
    for k in 0..strips {
        let (a, b) = (k as f32 / n, (k + 1) as f32 / n);
        let alpha = (r.start + (r.end - r.start) * (a + b) / 2.0) / 100.0;
        // a little wider than the drawing, for its outline
        let strip = Rect::new(rect.x - 2.0, top + len * a, rect.w + 4.0, len * (b - a));
        items.push(Draw::Clip { rect: strip, items: vec![Draw::Faded { alpha, items: vec![mirrored.clone()] }] });
    }
    items
}

/// A rectangle's outline as a path, clockwise (`hole`: the other way, to cut it out of another).
fn rect_segs_of(r: Rect, hole: bool) -> Vec<PathSeg> {
    let mut pts = [(r.x, r.y), (r.right(), r.y), (r.right(), r.bottom()), (r.x, r.bottom())];
    if hole {
        pts.reverse();
    }
    let mut segs: Vec<PathSeg> = pts.iter().enumerate().map(|(i, &(x, y))| if i == 0 { PathSeg::Move(x, y) } else { PathSeg::Line(x, y) }).collect();
    segs.push(PathSeg::Close);
    segs
}

/// A picture's shadow and glow, behind it (nothing without any): bands of its silhouette `segs`
/// (its frame, `rect`, cut to its shape). A perspective shadow is cast from the silhouette
/// flattened and leaning from its bottom edge.
fn picture_effects(segs: Vec<PathSeg>, rect: Rect, effects: wordcraft_doc::effects::ShapeEffects) -> Vec<Draw> {
    if effects.shadow.is_none() && effects.glow.is_none() {
        return Vec::new();
    }
    match effects.shadow.filter(|s| s.is_perspective()) {
        Some(sh) => {
            let (cx, bottom) = (rect.x + rect.w / 2.0, rect.bottom());
            let flat: Vec<PathSeg> = segs
                .iter()
                .map(|s| match *s {
                    PathSeg::Move(x, y) => {
                        let (x, y) = sh.project(x, y, cx, bottom);
                        PathSeg::Move(x, y)
                    }
                    PathSeg::Line(x, y) => {
                        let (x, y) = sh.project(x, y, cx, bottom);
                        PathSeg::Line(x, y)
                    }
                    other => other,
                })
                .collect();
            let plain = wordcraft_doc::effects::Shadow { scale_y: 100.0, scale_x: 100.0, skew_x: 0.0, ..sh };
            let mut out = vec![Draw::Effects {
                segs: flat,
                effects: wordcraft_doc::effects::ShapeEffects { shadow: Some(plain), ..Default::default() },
                filled: true,
                stroke_width: 0.0,
            }];
            if effects.glow.is_some() {
                out.push(Draw::Effects {
                    segs,
                    effects: wordcraft_doc::effects::ShapeEffects { shadow: None, ..effects },
                    filled: true,
                    stroke_width: 0.0,
                });
            }
            out
        }
        None => vec![Draw::Effects { segs, effects, filled: true, stroke_width: 0.0 }],
    }
}

/// A picture's inner shadow, over it and inside its frame (cut to its shape): in bands, the
/// outside of the frame (grown or shrunk by each band's step) moved by the shadow's offset.
fn inner_shadow_draws(rect: Rect, effects: &wordcraft_doc::effects::ShapeEffects, framing: &wordcraft_doc::picture::PictureFrame) -> Vec<Draw> {
    let Some(s) = effects.sanitized().inner_shadow else { return Vec::new() };
    if !(rect.w > 0.0 && rect.h > 0.0) {
        return Vec::new();
    }
    let (dx, dy) = s.offset();
    let reach = s.blur + s.distance + 2.0;
    let outer = Rect::new(rect.x - reach, rect.y - reach, rect.w + 2.0 * reach, rect.h + 2.0 * reach);
    let bands = wordcraft_doc::effects::bands(-s.blur / 2.0, s.blur / 2.0, s.opacity())
        .into_iter()
        .filter_map(|(g, a)| {
            // the inside left lit: the frame shrunk by the band's step, moved against the
            // shadow's direction, so the edges it points to darken
            let lit = frame_polys(framing, Rect::new(rect.x - dx + g, rect.y - dy + g, rect.w - 2.0 * g, rect.h - 2.0 * g));
            if lit.is_empty() {
                return None;
            }
            let mut segs = rect_segs_of(outer, false);
            segs.extend(poly_segs(&lit, true));
            Some(Draw::Faded {
                alpha: a,
                items: vec![Draw::Path {
                    segs,
                    fill: Some(Paint::solid(s.color)),
                    stroke: None,
                    stroke_width: 0.0,
                    dash: Vec::new(),
                    cap: Default::default(),
                }],
            })
        })
        .collect();
    match framing.shape {
        Some(_) => vec![Draw::ClipPath { segs: poly_segs(&frame_polys(framing, rect), false), items: bands }],
        None => vec![Draw::Clip { rect, items: bands }],
    }
}

/// How many steps a picture's soft edge fades in over.
const SOFT_EDGE_STEPS: usize = 10;

/// `rect` grown by `d` points on every side (shrunk when `d` is negative).
fn grown(rect: Rect, d: f32) -> Rect {
    Rect::new(rect.x - d, rect.y - d, rect.w + 2.0 * d, rect.h + 2.0 * d)
}

/// The outline of a picture in `rect` cut to `framing`'s shape (its rectangle without one): its
/// polygons, the first wound clockwise on the page (the others, holes, the other way). Empty
/// when `rect` is.
fn frame_polys(framing: &wordcraft_doc::picture::PictureFrame, rect: Rect) -> Vec<Vec<(f32, f32)>> {
    if !(rect.w > 0.0 && rect.h > 0.0 && rect.x.is_finite() && rect.y.is_finite() && rect.w.is_finite() && rect.h.is_finite()) {
        return Vec::new();
    }
    let cut = framing.shape.as_deref().and_then(|p| wordcraft_doc::preset::outline(p, rect.w, rect.h, &[])).filter(|o| !o.fill.is_empty());
    let Some(o) = cut else {
        return vec![vec![(rect.x, rect.y), (rect.right(), rect.y), (rect.right(), rect.bottom()), (rect.x, rect.bottom())]];
    };
    let mut polys: Vec<Vec<(f32, f32)>> = o.fill.iter().map(|poly| poly.iter().map(|[x, y]| (rect.x + x, rect.y + y)).collect()).collect();
    // twice the first polygon's area: positive when it winds clockwise (y down)
    let area: f32 = polys.first().map_or(0.0, |p| p.iter().zip(p.iter().cycle().skip(1)).map(|(a, b)| a.0 * b.1 - b.0 * a.1).sum());
    if area < 0.0 {
        for p in &mut polys {
            p.reverse();
        }
    }
    polys
}

/// `polys` as a path; `hole`: each wound the other way, to cut it out of another.
fn poly_segs(polys: &[Vec<(f32, f32)>], hole: bool) -> Vec<PathSeg> {
    let mut segs = Vec::new();
    for poly in polys {
        let mut pts = poly.clone();
        if hole {
            pts.reverse();
        }
        for (i, (x, y)) in pts.into_iter().enumerate() {
            segs.push(if i == 0 { PathSeg::Move(x, y) } else { PathSeg::Line(x, y) });
        }
        segs.push(PathSeg::Close);
    }
    segs
}

/// A picture drawn in `rect` as its frame says: its shadow and glow behind it (cast by the
/// picture with its outline), the picture itself (cut to the frame's shape and fading out at its
/// soft edges), its inner shadow and its outline over it, then its reflection. `fx` are its
/// effects with the shadow turned as it is drawn ([`effects_in`]). `media` is the picture and its
/// SVG original, if any.
#[allow(clippy::too_many_arguments)]
pub(super) fn picture_draws(
    rect: Rect,
    media: (&str, Option<&str>),
    crop: [f32; 4],
    alpha: f32,
    alt: &str,
    effects: &wordcraft_doc::effects::ShapeEffects,
    fx: wordcraft_doc::effects::ShapeEffects,
    framing: &wordcraft_doc::picture::PictureFrame,
    decorative: bool,
) -> Vec<Draw> {
    let framing = framing.sanitized();
    let outer = grown(rect, framing.reach());
    let shadows = picture_effects(poly_segs(&frame_polys(&framing, outer), false), outer, fx);
    let mut items = if shadows.is_empty() { Vec::new() } else { vec![Draw::Kept { items: shadows }] };
    let (media, svg) = media;
    let image = Draw::Image { rect, media: media.to_string(), svg: svg.map(str::to_string), crop, alpha, alt: alt.to_string(), decorative };
    let cut = poly_segs(&frame_polys(&framing, rect), false);
    match effects.sanitized().soft_edge {
        // in steps, each a little further in and more opaque than the last, adding up to a fade
        // from the edge to fully opaque the radius in
        Some(r) => {
            let r = r.min(rect.w.min(rect.h) / 2.0);
            let n = SOFT_EDGE_STEPS;
            let steps = (0..n)
                .filter_map(|k| {
                    let polys = frame_polys(&framing, grown(rect, -r * (k as f32 + 0.5) / n as f32));
                    if polys.is_empty() {
                        return None;
                    }
                    let step = Draw::ClipPath { segs: poly_segs(&polys, false), items: vec![image.clone()] };
                    Some(if k + 1 == n { step } else { Draw::Faded { alpha: 1.0 / (n - k) as f32, items: vec![step] } })
                })
                .collect();
            items.push(Draw::ClipPath { segs: cut, items: steps });
        }
        None if framing.shape.is_some() => items.push(Draw::ClipPath { segs: cut, items: vec![image] }),
        None => items.push(image),
    }
    let inner = inner_shadow_draws(rect, effects, &framing);
    if !inner.is_empty() {
        items.push(Draw::Kept { items: inner });
    }
    if let Some(line) = framing.line {
        items.extend(outline_draws(rect, line, &framing));
    }
    reflected(outer, effects, items)
}

/// A picture's outline, centred on its edge: each of its lines a ring between two copies of the
/// frame's shape, grown and shrunk to the line's place across the outline's width.
fn outline_draws(rect: Rect, line: wordcraft_doc::picture::FrameLine, framing: &wordcraft_doc::picture::PictureFrame) -> Vec<Draw> {
    let w = line.width;
    line.compound
        .lines()
        .iter()
        .filter_map(|&(a, b)| {
            let outside = frame_polys(framing, grown(rect, w / 2.0 - a * w));
            if outside.is_empty() {
                return None;
            }
            let mut segs = poly_segs(&outside, false);
            segs.extend(poly_segs(&frame_polys(framing, grown(rect, w / 2.0 - b * w)), true));
            Some(Draw::Path {
                segs,
                fill: Some(Paint::solid(line.color)),
                stroke: None,
                stroke_width: 0.0,
                dash: Vec::new(),
                cap: Default::default(),
            })
        })
        .collect()
}

/// `segs` grown by `g` points on every side, scaled about the middle of their bounds, and moved
/// by (`dx`, `dy`): one band of a shadow or glow (see [`Draw::Effects`]).
pub fn grown_segs(segs: &[PathSeg], g: f32, dx: f32, dy: f32) -> Vec<PathSeg> {
    let (mut x0, mut y0, mut x1, mut y1) = (f32::MAX, f32::MAX, f32::MIN, f32::MIN);
    let mut see = |x: f32, y: f32| {
        if x.is_finite() && y.is_finite() {
            (x0, y0, x1, y1) = (x0.min(x), y0.min(y), x1.max(x), y1.max(y));
        }
    };
    for s in segs {
        match *s {
            PathSeg::Move(x, y) | PathSeg::Line(x, y) => see(x, y),
            PathSeg::Cubic(a, b, c, d, x, y) => {
                see(a, b);
                see(c, d);
                see(x, y);
            }
            PathSeg::Close => {}
        }
    }
    if x0 > x1 || y0 > y1 {
        return Vec::new();
    }
    let (cx, cy) = ((x0 + x1) / 2.0, (y0 + y1) / 2.0);
    let scale = |len: f32| if len > 0.01 { ((len + 2.0 * g) / len).max(0.0) } else { 1.0 };
    let (sx, sy) = (scale(x1 - x0), scale(y1 - y0));
    let at = |x: f32, y: f32| (cx + (x - cx) * sx + dx, cy + (y - cy) * sy + dy);
    segs.iter()
        .map(|s| match *s {
            PathSeg::Move(x, y) => {
                let (x, y) = at(x, y);
                PathSeg::Move(x, y)
            }
            PathSeg::Line(x, y) => {
                let (x, y) = at(x, y);
                PathSeg::Line(x, y)
            }
            PathSeg::Cubic(a, b, c, d, x, y) => {
                let ((a, b), (c, d), (x, y)) = (at(a, b), at(c, d), at(x, y));
                PathSeg::Cubic(a, b, c, d, x, y)
            }
            PathSeg::Close => PathSeg::Close,
        })
        .collect()
}

// Dash lengths use the effective width used by both path painters.
fn path_stroke_width(width: f32) -> f32 {
    if width.is_finite() && width > 0.0 { width.clamp(0.25, 200.0) } else { 0.75 }
}

/// Make a filled triangular arrowhead at the last point, in the points' coordinate space.
/// The last distinct segment supplies its direction; `style` and the stroke width set its size.
/// Returns `None` if there is no usable final segment. The triangle has no outline, so the path's
/// cap setting does not affect its appearance.
fn path_arrowhead(points: &[(f32, f32)], color: Rgb, stroke_width: f32, style: ArrowStyle) -> Option<Draw> {
    let tip = *points.last()?;
    let (prev_x, prev_y) = points.iter().rev().skip(1).copied().find(|p| *p != tip)?;
    let dx = tip.0 - prev_x;
    let dy = tip.1 - prev_y;
    let length = dx.hypot(dy);
    if !length.is_finite() || length <= f32::EPSILON {
        return None;
    }
    Some(arrowhead(tip, (dx / length, dy / length), stroke_width, style, color))
}

/// A filled triangular arrowhead with its tip at `tip`, pointing along unit vector `dir`: as long
/// and as wide as `style` makes it for a `stroke_width` line ([`ArrowStyle::dims`]).
fn arrowhead(tip: (f32, f32), (dx, dy): (f32, f32), stroke_width: f32, style: ArrowStyle, color: Rgb) -> Draw {
    let (len, wide) = style.dims(stroke_width);
    let (bx, by) = (tip.0 - dx * len, tip.1 - dy * len);
    let (nx, ny) = (-dy * wide / 2.0, dx * wide / 2.0);
    let segs = vec![PathSeg::Move(tip.0, tip.1), PathSeg::Line(bx + nx, by + ny), PathSeg::Line(bx - nx, by - ny), PathSeg::Close];
    Draw::Path { segs, fill: Some(Paint::solid(color)), stroke: None, stroke_width: 0.0, dash: Vec::new(), cap: Default::default() }
}

/// A line or connector at `rect` (its unflipped frame: flips are the frame's turn), with its
/// arrowheads.
fn line_draws(
    rect: Rect,
    kind: ShapeKind,
    stroke: Option<Rgb>,
    stroke_width: f32,
    effects: wordcraft_doc::effects::ShapeEffects,
    extra: &wordcraft_doc::connector::ShapeExtra,
    out: &mut Vec<Draw>,
) {
    use wordcraft_doc::connector::{connector_segs, end_direction};
    match connector_segs(kind, rect.w, rect.h) {
        Some(segs) => {
            let segs = segs
                .into_iter()
                .map(|s| match s {
                    PathSeg::Move(x, y) => PathSeg::Move(rect.x + x, rect.y + y),
                    PathSeg::Line(x, y) => PathSeg::Line(rect.x + x, rect.y + y),
                    PathSeg::Cubic(a, b, c, d, e, f) => PathSeg::Cubic(rect.x + a, rect.y + b, rect.x + c, rect.y + d, rect.x + e, rect.y + f),
                    PathSeg::Close => PathSeg::Close,
                })
                .collect();
            let width = path_stroke_width(stroke_width);
            out.push(Draw::Path { segs, fill: None, stroke, stroke_width: width, dash: extra.dash.pattern(width), cap: extra.cap });
        }
        None => out.push(Draw::Shape {
            rect,
            kind,
            fill: None,
            stroke,
            stroke_width,
            effects,
            corner: None,
            dash: extra.dash.pattern(stroke_width),
            cap: extra.cap,
        }),
    }
    let Some(color) = stroke else { return };
    // Our line runs from the bottom-left corner to the top-right; connectors from the top-left
    // to the bottom-right.
    let (start, end) =
        if kind == ShapeKind::Line { ((rect.x, rect.bottom()), (rect.right(), rect.y)) } else { ((rect.x, rect.y), (rect.right(), rect.bottom())) };
    for (on, tip, at_end, style) in [(extra.arrow_start, start, false, extra.start_style), (extra.arrow_end, end, true, extra.end_style)] {
        if on {
            out.push(arrowhead(tip, end_direction(kind, rect.w, rect.h, at_end), stroke_width, style, color));
        }
    }
}

pub fn seg_path(segs: &[PathSeg]) -> BezPath {
    let mut p = BezPath::new();
    for s in segs {
        match *s {
            PathSeg::Move(x, y) => p.move_to((x as f64, y as f64)),
            PathSeg::Line(x, y) => p.line_to((x as f64, y as f64)),
            PathSeg::Cubic(a, b, c, d, e, f) => p.curve_to((a as f64, b as f64), (c as f64, d as f64), (e as f64, f as f64)),
            PathSeg::Close => p.close_path(),
        }
    }
    p
}

/// One line of a `GraphicItem::Text` in `r`, shaped in its font (the document's body font when
/// unset) and aligned horizontally, centred vertically, at its size times `k`. Characters the font
/// lacks (Chinese in a chart drawn in Calibri's substitute, #4720) are drawn in a fallback face, as
/// in body text: one draw per run of a face, in reading order. Empty when there is nothing to draw,
/// or the item's rectangle or size isn't a finite number (a degenerate scale: `r` itself is always
/// finite, as [`Rect::new`] makes it).
fn text_glyphs(doc: &Document, r: Rect, it: &GraphicItem, alpha: f32, k: f32) -> Vec<Draw> {
    let GraphicItem::Text { rect, text, size, color, bold, align, font } = it else { return Vec::new() };
    let size = *size * k;
    if !rect.iter().chain([&size]).all(|v| v.is_finite()) {
        return Vec::new();
    }
    let family = font.as_deref().filter(|f| !f.is_empty()).unwrap_or(&doc.settings.minor_font);
    let size = size.clamp(1.0, 400.0);
    let resolved = wordcraft_fonts::word::resolve(family, *bold, false);
    let primary = resolved.face;
    let scale = |f: FaceRef| size / f.upem.max(1.0) as f32;
    let runs: Vec<_> = face_runs(text, primary, *bold)
        .into_iter()
        .map(|(face, s)| (face, s, wordcraft_fonts::shape(face.get(), s, &[], |c| c)))
        .filter(|(_, _, shaped)| !shaped.is_empty())
        .collect();
    let width: f32 = runs.iter().map(|(f, _, shaped)| shaped.iter().map(|g| g.x_advance as f32).sum::<f32>() * scale(*f)).sum();
    // The font's own line metrics centre the line, whichever faces draw it.
    let (ascent, descent) = wordcraft_fonts::word::line_metrics(primary.get());
    let baseline = r.y + r.h / 2.0 + (ascent - descent) as f32 * scale(primary) / 2.0;
    let mut pen = match align {
        TextAlign::Left => r.x,
        TextAlign::Center => r.x + (r.w - width) / 2.0,
        TextAlign::Right => r.x + r.w - width,
    };
    let mut out = Vec::with_capacity(runs.len());
    for (face, s, shaped) in runs {
        let k = scale(face);
        let mut glyphs = Vec::with_capacity(shaped.len());
        for g in &shaped {
            glyphs.push((g.gid, pen + g.x_offset as f32 * k, baseline - g.y_offset as f32 * k));
            pen += g.x_advance as f32 * k;
        }
        // A fallback face is emboldened when it has no bold of its own, as in body text.
        let synth_bold = if face.id() == primary.id() { resolved.synth_bold } else { *bold && face.weight < 600.0 };
        out.push(Draw::Glyphs {
            face,
            size,
            glyphs,
            color: *color,
            alpha,
            synth_bold,
            synth_italic: false,
            text: s.to_string(),
            link: None,
            ranges: Vec::new(),
            text_bg: None,
            x_scale: 1.0,
        });
    }
    out
}

/// `text` split into runs of the face that draws them: `primary` where it has the character, else a
/// fallback face. A run the primary lacks takes one face covering all of it when one does (so a
/// Chinese label doesn't mix serif and sans glyphs), else each character its own fallback. Spaces
/// and combining marks stay in the run before them; a character no face has stays in `primary`.
fn face_runs(text: &str, primary: FaceRef, bold: bool) -> Vec<(FaceRef, &str)> {
    let joins = |c: char| c.is_whitespace() || wordcraft_doc::math_linear::is_combining(c);
    // (start, end, drawn by `primary`)
    let mut spans: Vec<(usize, usize, bool)> = Vec::new();
    for (i, c) in text.char_indices() {
        let end = i + c.len_utf8();
        let own = primary.covers(c);
        match spans.last_mut() {
            Some(last) if last.2 == own || joins(c) => last.1 = end,
            _ => spans.push((i, end, own)),
        }
    }
    let db = wordcraft_fonts::FontDb::global();
    let mut out: Vec<(FaceRef, &str)> = Vec::with_capacity(spans.len());
    for (start, end, own) in spans {
        let Some(s) = text.get(start..end) else { continue };
        if own {
            out.push((primary, s));
            continue;
        }
        let chars: Vec<char> = s.chars().filter(|c| !joins(*c)).collect();
        if let Some(f) = db.fallback_for_run(&chars, primary.id(), bold, false) {
            out.push((FaceRef::of(&f), s));
            continue;
        }
        // No one face has them all: each character in its own fallback, neighbours sharing a face
        // in one run.
        let mut run: Option<(FaceRef, usize)> = None;
        for (i, c) in s.char_indices() {
            let face =
                if joins(c) { None } else { Some(db.fallback_styled(c, primary.id(), bold, false).map(|f| FaceRef::of(&f)).unwrap_or(primary)) };
            match (run, face) {
                (Some((cur, from)), Some(f)) if f.id() != cur.id() => {
                    out.extend(s.get(from..i).map(|t| (cur, t)));
                    run = Some((f, i));
                }
                (None, f) => run = Some((f.unwrap_or(primary), i)),
                _ => {}
            }
        }
        if let Some((cur, from)) = run {
            out.extend(s.get(from..).map(|t| (cur, t)));
        }
    }
    out
}

/// `items` turned by `spin` about the centre of `rect` (as they are, unturned).
pub(super) fn spun(spin: Spin, rect: Rect, items: Vec<Draw>, out: &mut Vec<Draw>) {
    if spin.is_identity() {
        out.extend(items);
    } else {
        out.push(Draw::Rotated { cx: rect.x + rect.w / 2.0, cy: rect.y + rect.h / 2.0, spin, items });
    }
}

/// A picture, shape or group drawn in `rect` (its unturned frame), turned by its own spin about
/// the rect's centre; a group's members are turned inside it. `outer` is the spin it is already
/// drawn inside (its group's). Nothing for anything else.
pub(super) fn object_draws(o: &InlineObject, rect: Rect, outer: Spin, alpha: f32, opts: &DisplayOptions, out: &mut Vec<Draw>) {
    let own = o.frame().map(|(_, _, f)| f.spin()).unwrap_or_default();
    match o {
        InlineObject::Image { media, svg, crop, alt, effects, framing, .. } => {
            let fx = effects_in(*effects, own.within(outer));
            let items = picture_draws(rect, (media, svg.as_deref()), *crop, alpha, alt, effects, fx, framing, o.is_decorative());
            spun(own, rect, items, out)
        }
        InlineObject::Shape { kind, fill, stroke, stroke_width, effects, freeform, extra, .. } => {
            let effects = effects_in(*effects, own.within(outer));
            let mut items = Vec::new();
            shape_draws(
                rect,
                &ShapeLook {
                    kind: *kind,
                    fill: fill.as_ref(),
                    stroke: *stroke,
                    stroke_width: *stroke_width,
                    effects,
                    freeform: freeform.as_deref(),
                    extra,
                },
                opts,
                &mut items,
            );
            let items = super::page::kept_shape(freeform.as_deref(), reflected(rect, &effects, items));
            match crate::shape_figure_alt(o) {
                // A described shape is one figure with its description (tagged PDF).
                Some(alt) => {
                    let mut draws = Vec::new();
                    spun(own, rect, items, &mut draws);
                    out.push(Draw::Figure { alt: alt.to_string(), kind: None, draws })
                }
                None => spun(own, rect, items, out),
            }
        }
        InlineObject::Group { .. } => {
            let mut members = Vec::new();
            for ([x, y, w, h], c) in o.group_rects(rect.x, rect.y, rect.w, rect.h) {
                if c.frame().is_some_and(|(_, _, f)| f.hidden) {
                    continue;
                }
                object_draws(c, Rect::new(x, y, w, h), own.within(outer), alpha, opts, &mut members);
            }
            match o.canvas_style() {
                // A Drawing Canvas: its background and outline, then its members clipped to its
                // frame, all inside its turn.
                Some(c) => spun(own, rect, vec![canvas_background(rect, c), Draw::Clip { rect, items: members }], out),
                None => spun(own, rect, members, out),
            }
        }
        // Native ink in a group (kept opaque): drawn as drawing-level ink is, from its pictures.
        InlineObject::Graphic { graphic, .. } if graphic.kind == GraphicKind::Ink => {
            let (sx, sy) = (scale_of(rect.w, graphic.w), scale_of(rect.h, graphic.h));
            let items = graphic
                .items
                .iter()
                .filter_map(|it| match it {
                    GraphicItem::Image { rect: r, media } => Some(Draw::Image {
                        rect: Rect::new(rect.x + r[0] * sx, rect.y + r[1] * sy, r[2] * sx, r[3] * sy),
                        media: media.clone(),
                        crop: [0.0; 4],
                        alpha,
                        alt: String::new(),
                        decorative: false,
                        svg: None,
                    }),
                    _ => None,
                })
                .collect();
            spun(own, rect, items, out)
        }
        _ => {}
    }
}

/// A shape's `effects` as drawn turned by `spin` (all the turns it is drawn inside): glows and
/// soft edges turn with the shape, and so does a shadow's offset when it rotates with the shape
/// (`rotWithShape`); one that doesn't keeps its direction on the page, so its angle is turned back.
pub(crate) fn effects_in(effects: wordcraft_doc::effects::ShapeEffects, spin: Spin) -> wordcraft_doc::effects::ShapeEffects {
    let mut e = effects;
    if let Some(s) = e.shadow.as_mut().filter(|s| !s.rot_with_shape && !spin.is_identity()) {
        let a = s.angle.to_radians();
        let (dx, dy) = spin.unapply(0.0, 0.0, a.cos(), a.sin());
        s.angle = wordcraft_geom::normalize_degrees(dy.atan2(dx).to_degrees());
    }
    e
}

/// Where inline object `obj` is drawn, given its cluster's box (`adv` × `obj_h` standing on the
/// baseline at `cx`): inside the room kept for its effects.
/// A rotated one's unrotated frame, centred in that room.
///
/// `line` is the room a horizontal rule is placed in, from `cx`.
pub(crate) fn inline_rect(obj: Option<&InlineObject>, cx: f32, base: f32, adv: f32, obj_h: f32, line: f32) -> Rect {
    let frame = obj.and_then(InlineObject::frame);
    let [l, t, r, b] = frame.map_or([0.0; 4], |(_, _, float)| float.effect_extent());
    let room = Rect::new(cx + l, base - obj_h + t, (adv - l - r).max(0.0), (obj_h - t - b).max(0.0));
    // A horizontal rule is as wide as its share of the paragraph's width, whatever narrows its
    // line, and is placed by its alignment in what the line has left: it runs past the margin
    // beside a float or a first-line indent, as in Word (see `ParaLayout::rule_room`).
    if let Some(InlineObject::Shape { w, extra, .. }) = obj
        && let Some(hr) = extra.hr
    {
        let rw = hr.width(room.w, *w);
        return Rect::new(hr.left(room.x, line, rw), room.y, rw, room.h);
    }
    let Some((w, h, float)) = frame.filter(|(_, _, f)| f.rot != 0.0) else { return room };
    // The room is the rotated bounds of the frame: undo the rotation.
    let (w, h) = (wordcraft_geom::finite(w).clamp(1.0, 4000.0), wordcraft_geom::finite(h).clamp(1.0, 4000.0));
    let (bw, _) = float.spin().extent(w, h);
    let k = if bw > 0.0 { room.w / bw } else { 1.0 };
    let (fw, fh) = (w * k, h * k);
    Rect::new(room.x + (room.w - fw) / 2.0, room.y + (room.h - fh) / 2.0, fw, fh)
}

/// A Drawing Canvas's background and outline over its frame `rect`.
fn canvas_background(rect: Rect, c: &wordcraft_doc::para::CanvasStyle) -> Draw {
    Draw::Shape {
        rect,
        kind: ShapeKind::Rectangle,
        fill: c.fill.as_ref().and_then(|f| paint_for(f, rect)),
        stroke: c.stroke,
        stroke_width: c.stroke_width,
        effects: Default::default(),
        corner: None,
        dash: Vec::new(),
        cap: Default::default(),
    }
}

#[cfg(test)]
mod fill_alpha_tests;

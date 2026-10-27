//! Regression coverage for Android features across upstream engine migrations.
use crate::{Session, cmd::objects};
use serde_json::json;
use wordcraft_doc::fill::Fill;
use wordcraft_doc::{Document, InlineObject, Rgb};

#[test]
fn legacy_shape_styles_keep_gradient_and_opacity_through_docx() {
    let mut s = Session::new(Document::new());
    s.run("insert.shape", &json!({"kind": "rectangle"})).unwrap();
    let (pos, _) = objects::selected(&s).unwrap();
    objects::edit_obj(&mut s, &pos, |o| {
        if let InlineObject::Shape { fill, extra, .. } = o {
            *fill = Some(Fill::solid(Rgb(20, 60, 120)));
            extra.fill_gradient = Some(wordcraft_doc::connector::ShapeGradient::shaded(Rgb(20, 60, 120)));
            extra.fill_transparency = Some(40.0);
        }
    })
    .unwrap();
    let expected = match objects::selected(&s).unwrap().1 {
        InlineObject::Shape { fill, extra, .. } => extra.effective_fill(fill.as_ref()),
        _ => panic!("shape"),
    };
    let bytes = wordcraft_docx::write(&s.doc).unwrap();
    let reopened = Session::new(wordcraft_docx::read(&bytes).unwrap());
    let (_, obj) = objects::all_objects(&reopened).into_iter().next().unwrap();
    let InlineObject::Shape { fill, extra, .. } = obj else { panic!("shape") };
    let Some(Fill::Gradient(expected)) = expected else { panic!("gradient") };
    let Some(Fill::Gradient(actual)) = extra.effective_fill(fill.as_ref()) else { panic!("gradient") };
    assert_eq!(actual.stops.len(), expected.stops.len());
    for (a, b) in actual.stops.iter().zip(&expected.stops) {
        assert_eq!(a.color, b.color);
        assert!((a.transparency - b.transparency).abs() < 0.01);
    }
    s.run("shape.fill", &json!({"color": "FF0000", "transparency": 0})).unwrap();
    let InlineObject::Shape { fill, extra, .. } = objects::selected(&s).unwrap().1 else { panic!("shape") };
    assert_eq!(extra.effective_fill(fill.as_ref()), Some(Fill::solid(Rgb(255, 0, 0))));
}

#[test]
fn android_picture_frames_and_svg_survive_docx() {
    let mut s = Session::new(Document::new());
    s.run("insert.icon", &json!({"id": "heart"})).unwrap();
    s.run("select.objects", &json!({"index": 0})).unwrap();
    for style in ["metalOval", "softEdgeRectangle", "reflectedRoundedRectangle"] {
        s.run("picture.style", &json!({"style": style})).unwrap();
        let InlineObject::Image { framing, effects, svg, .. } = objects::selected(&s).unwrap().1 else { panic!("image") };
        assert!(svg.is_some());
        let doc = wordcraft_docx::read(&wordcraft_docx::write(&s.doc).unwrap()).unwrap();
        let reopened = Session::new(doc);
        let (_, obj) = objects::all_objects(&reopened).into_iter().next().unwrap();
        let InlineObject::Image { framing: f, effects: e, svg, .. } = obj else { panic!("image") };
        assert_eq!(f, framing, "{style}");
        assert_eq!(e, effects, "{style}");
        assert!(svg.is_some(), "SVG is kept alongside the raster fallback");
    }
}

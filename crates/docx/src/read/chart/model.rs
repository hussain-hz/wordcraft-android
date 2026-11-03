use crate::xml::El;
fn truthy(v: &str) -> bool {
    matches!(v, "1" | "true" | "on")
}
use super::Rgb;

/// A data label to draw once the chart is: its text at (`x`, `y`), placed there as `at` says, in
/// white on a fill (`white`) or in the text colour.
pub(super) struct DataLabel {
    pub(super) x: f32,
    pub(super) y: f32,
    pub(super) text: String,
    pub(super) at: At,
    pub(super) white: bool,
}

/// Where a data label goes against its point: centred on it, above or below it, or ending left of
/// it or starting right of it.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum At {
    Centre,
    Above,
    Below,
    Left,
    Right,
}

/// How the chart type's data labels (`c:dLbls`) look: their place (`c:dLblPos`), what they show,
/// and whether they sit in a box (a fill of their own: a callout).
#[derive(Clone, Copy, Debug, Default)]
pub(super) struct LabelStyle {
    pub(super) pos: Option<&'static str>,
    pub(super) value: bool,
    pub(super) category: bool,
    pub(super) percent: bool,
    pub(super) boxed: bool,
}

impl LabelStyle {
    pub(super) fn parse(d: Option<&El>) -> LabelStyle {
        let Some(d) = d else { return LabelStyle { value: true, ..LabelStyle::default() } };
        let flag = |n: &str| d.child(n).map(|v| v.attr("val").is_none_or(truthy));
        let (category, percent) = (flag("c:showCatName").unwrap_or(false), flag("c:showPercent").unwrap_or(false));
        let pos = d
            .child("c:dLblPos")
            .and_then(|p| p.attr("val"))
            .and_then(|v| ["ctr", "inEnd", "inBase", "outEnd", "bestFit", "l", "r", "t", "b"].into_iter().find(|x| *x == v));
        LabelStyle {
            pos,
            // a label shows its value unless it says what else it shows instead
            value: flag("c:showVal").unwrap_or(!(category || percent)),
            category,
            percent,
            boxed: d.child("c:spPr").is_some_and(|p| p.child("a:solidFill").is_some()),
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum Kind {
    Bar,
    Line,
    Area,
    Pie,
    Doughnut,
    Scatter,
    Radar,
    Stock,
}

impl Kind {
    pub(super) fn of(name: &str) -> Option<Kind> {
        Some(match name {
            "c:barChart" => Kind::Bar,
            "c:lineChart" => Kind::Line,
            "c:areaChart" => Kind::Area,
            "c:pieChart" => Kind::Pie,
            "c:doughnutChart" => Kind::Doughnut,
            "c:scatterChart" => Kind::Scatter,
            "c:radarChart" => Kind::Radar,
            "c:stockChart" => Kind::Stock,
            _ => return None,
        })
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum Grouping {
    Clustered,
    Stacked,
    Percent,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) enum Pos {
    Right,
    Left,
    Top,
    Bottom,
}

/// One value axis (or category axis) as the file states it.
#[derive(Default)]
pub(super) struct Axis {
    pub(super) deleted: bool,
    pub(super) grid: bool,
    pub(super) line: bool,
    /// `c:crossBetween val="midCat"`: categories sit on the tick marks, not between them.
    pub(super) mid: bool,
    pub(super) min: Option<f64>,
    pub(super) max: Option<f64>,
    /// Number format of the tick labels, when not linked to the source data.
    pub(super) fmt: Option<String>,
}

pub(super) struct Series {
    pub(super) name: String,
    pub(super) color: Option<Rgb>,
    pub(super) line_w: f32,
    /// Whether the series is drawn as a line (scatter series without one get markers).
    pub(super) line: bool,
    pub(super) values: Vec<Option<f64>>,
    /// Scatter x values (index + 1 when the file has none).
    pub(super) xs: Vec<Option<f64>>,
    /// Per-point colours from `c:dPt`.
    pub(super) points: Vec<Option<Rgb>>,
    pub(super) fmt: Option<String>,
    /// Format code of the scatter x values' cache.
    pub(super) x_fmt: Option<String>,
    /// Data labels on or off for this series (`c:dLbls` of its own), when it says.
    pub(super) labels: Option<bool>,
    /// A marker at each point (`c:marker` with a symbol other than none).
    pub(super) marker: bool,
    pub(super) marker_disabled: bool,
    /// The outline round its bars, areas or slices (its `a:ln`), colour and width; for lines,
    /// that is the line itself.
    pub(super) outline: Option<(Rgb, f32)>,
    /// The values as the file has them, for data labels (`values` become shares in a 100%
    /// stacked chart); empty when the series shows no labels.
    pub(super) raw: Vec<Option<f64>>,
}

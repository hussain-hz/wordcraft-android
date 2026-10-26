package io.github.artcraftandroid.wordcraft.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.artcraftandroid.wordcraft.R

/** Translate application labels only, at the presentation boundary. Never pass document text,
 * file names, font names, command IDs or user-entered values through this function. English
 * labels remain stable identifiers in the existing palette and command models. */
@Composable
internal fun uiText(source: String, vararg args: Any): String {
    val id = labelResource(source) ?: return source
    return if (args.isEmpty()) stringResource(id) else stringResource(id, *args)
}

internal fun localizedUiText(context: Context, source: String, vararg args: Any): String {
    val id = labelResource(source) ?: return source
    return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
}

private fun labelResource(source: String): Int? = when (source) {
    "%1\$s can't be opened any more." -> R.string.ui_5aa22e2b218b
    "%1\$s mm" -> R.string.ui_9667c1b7ab39
    "%1\$s of %2\$s" -> R.string.ui_8f4594535134
    "%1\$s: %2\$s" -> R.string.ui_13326cace603
    "A WordCraft template" -> R.string.ui_1bb3583d74db
    "A WordCraft update is available" -> R.string.ui_a832a817d125
    "A, B, C, …" -> R.string.ui_68e8be91edf7
    "Aa" -> R.string.ui_81acaafba961
    "Abc" -> R.string.ui_06d90109c8cc
    "About" -> R.string.ui_4efca0d10c5f
    "Above Chart" -> R.string.ui_825ca214f188
    "Accent 1" -> R.string.ui_64de30eda978
    "Accent 2" -> R.string.ui_fc5916a883a5
    "Accent 3" -> R.string.ui_270a9c304a32
    "Accent 4" -> R.string.ui_c80992337de8
    "Accent 5" -> R.string.ui_2d88dac49024
    "Accent 6" -> R.string.ui_138530c2b8d2
    "Accept" -> R.string.ui_89713b9c9c1b
    "Accept All Changes" -> R.string.ui_87aac2fa0049
    "Accept and Move to Next" -> R.string.ui_a31e0063d30f
    "Add Bullet" -> R.string.ui_a1f086fa11d6
    "Add Shape" -> R.string.ui_d6df5f11e527
    "Add Shape After" -> R.string.ui_eccac39abfc5
    "Add Shape Below" -> R.string.ui_75ff72a2cad4
    "Add Space After Paragraph" -> R.string.ui_e4f3584a2944
    "Add Space Before Paragraph" -> R.string.ui_e3d55c93a691
    "Add to Dictionary" -> R.string.ui_e0edefaca0c3
    "Add to Home screen" -> R.string.ui_ded4397257c6
    "Address" -> R.string.ui_56ef8f20955f
    "Align Left" -> R.string.ui_94a69331c723
    "Align Right" -> R.string.ui_9b0274c13c1c
    "Alignment" -> R.string.ui_9d597870c4b3
    "All Fonts" -> R.string.ui_c15ea5330c20
    "All Markup" -> R.string.ui_e1efe4e8a789
    "Alt Text" -> R.string.ui_77055f48eb4c
    "An empty page" -> R.string.ui_7d238e75e701
    "An open-source word processor for Android, built on the WordCraft engine. It is not affiliated with or endorsed by Microsoft; Word is a trademark of Microsoft." -> R.string.ui_8bbaaa91a691
    "An update is available" -> R.string.ui_caa6a5920fe4
    "Android may ask you to allow updates from WordCraft. Your documents and settings will be kept." -> R.string.ui_0668a5e3f5db
    "App updates" -> R.string.ui_5808deaecd58
    "App version" -> R.string.ui_80a7db3b236f
    "Area" -> R.string.ui_024dc204d7ba
    "Arrange" -> R.string.ui_d557a972ab06
    "Author" -> R.string.ui_d95082a2ee57
    "Auto-Detected Headings" -> R.string.ui_a178e3d2f6d1
    "AutoFit" -> R.string.ui_e8119f56c8a3
    "AutoFit Contents" -> R.string.ui_04e4b2bce069
    "AutoFit Window" -> R.string.ui_59b0944a52b5
    "AutoSave" -> R.string.ui_db3532cbed95
    "Automatic" -> R.string.ui_d461a493a375
    "Automatically save files" -> R.string.ui_24c51c32778b
    "B4 (JIS)" -> R.string.ui_46825e420b59
    "B5 (JIS)" -> R.string.ui_63d31610e286
    "Banded Columns" -> R.string.ui_e086c8f00e9b
    "Banded Rows" -> R.string.ui_97f854ecb2d2
    "Bar" -> R.string.ui_95d64cacce0f
    "Basic List" -> R.string.ui_8a61b311e8ca
    "Basic Shapes" -> R.string.ui_fb72fc26e3d2
    "Behind Text" -> R.string.ui_a7ddfa05b06e
    "Below" -> R.string.ui_a53dffffb7e5
    "Black" -> R.string.ui_c6cfe6e4f129
    "Blank Page" -> R.string.ui_2bd70e4d3926
    "Blank document" -> R.string.ui_b45da2d68c4a
    "Block Arrows" -> R.string.ui_9449dd42a91b
    "Bold" -> R.string.ui_94fee62e68e2
    "Book Title" -> R.string.ui_f729197112fb
    "Bottom" -> R.string.ui_479ad21ac909
    "Bottom: %1\$s" -> R.string.ui_16b1a8553739
    "Box and Whisker" -> R.string.ui_9de7b4ffb9d1
    "Breaks" -> R.string.ui_6d027fce254d
    "Bring Forward" -> R.string.ui_1816a3240fe1
    "Bring in Front of Text" -> R.string.ui_b01fa4d5591c
    "Bring to Front" -> R.string.ui_ce67c6954ace
    "Browse" -> R.string.ui_3227aa966625
    "Bullets" -> R.string.ui_20c579c3da98
    "Callouts" -> R.string.ui_e7aae9333ef2
    "Camera" -> R.string.ui_03494b0d1f80
    "Cancel" -> R.string.ui_19766ed6ccb2
    "Cancel download" -> R.string.ui_de5b23e69cd1
    "Capitalize Each Word" -> R.string.ui_b50e0a3cb3d9
    "Carlito and Caladea typefaces: SIL Open Font License 1.1" -> R.string.ui_f137bacf1e8d
    "Center" -> R.string.ui_d946067427e9
    "Centered Overlay" -> R.string.ui_112993b42e28
    "Change Case" -> R.string.ui_4f245fcf6e76
    "Changes couldn't be saved to %1\$s. Use Save As to keep them." -> R.string.ui_c224d1f6b17d
    "Characters (no spaces)" -> R.string.ui_d811846cd569
    "Characters (with spaces)" -> R.string.ui_26edfe8e1326
    "Chart" -> R.string.ui_3e5b90ae053f
    "Chart Title" -> R.string.ui_0e4485a648e3
    "Check for updates" -> R.string.ui_f26f3275e735
    "Checking for updates…" -> R.string.ui_497a19986d82
    "Choose an item" -> R.string.ui_11997590030a
    "Choose the app appearance" -> R.string.ui_e51295496c5b
    "Choose the app language" -> R.string.ui_dbc72a80ad3e
    "Choose the encoding that makes the text read correctly." -> R.string.ui_2d062f090f13
    "Clear Formatting" -> R.string.ui_804cf3352c42
    "Close" -> R.string.ui_7d9eb7acb13e
    "Close Header & Footer" -> R.string.ui_f10deadb11db
    "Clustered Bar" -> R.string.ui_8e0c496136d0
    "Clustered Column" -> R.string.ui_a73b320b935e
    "Color" -> R.string.ui_6b73191a0a4b
    "Colorful" -> R.string.ui_e02654696bff
    "Colors" -> R.string.ui_88c45d9e526c
    "Column" -> R.string.ui_3fdc9a587693
    "Columns" -> R.string.ui_53aade77cd69
    "Comment" -> R.string.ui_44f5e3fbec57
    "Comment Reference" -> R.string.ui_6190b96e3a01
    "Comment Subject" -> R.string.ui_b6e21f4593cb
    "Comment Text" -> R.string.ui_718e35bb9fb1
    "Comments" -> R.string.ui_355f79f29d7d
    "Continuous" -> R.string.ui_04f13cecbc50
    "Copy" -> R.string.ui_e21f935f11d7
    "Couldn't check for updates. Try again when connected." -> R.string.ui_db037a3559ae
    "Couldn't download or verify the update. Please try again." -> R.string.ui_b63c7ea6144b
    "Crop Graphic" -> R.string.ui_d235c78b73d3
    "Crop Picture" -> R.string.ui_b4db66a2b745
    "Crop Position" -> R.string.ui_19676013a0a9
    "Custom Margins" -> R.string.ui_2c637f78a180
    "Custom Margins..." -> R.string.ui_7cd51359ac95
    "Cut" -> R.string.ui_1f45f02561f4
    "Cycle" -> R.string.ui_518653530449
    "Dark" -> R.string.ui_60acc53f13a5
    "Dark Gray" -> R.string.ui_350b555a4f4a
    "Data Labels" -> R.string.ui_78023df4505c
    "Decrease Indent" -> R.string.ui_889313b9b63a
    "Default" -> R.string.ui_21b111cbfe6e
    "Default View" -> R.string.ui_f8cf1b71c026
    "Delete" -> R.string.ui_e2d0a54968ea
    "Delete All Comments in Document" -> R.string.ui_a859fb5902ff
    "Delete Columns" -> R.string.ui_84a7980a5b18
    "Delete Comment" -> R.string.ui_534716e70e41
    "Delete Rows" -> R.string.ui_af3d82c21398
    "Delete Table" -> R.string.ui_8e5eac659f83
    "Demote" -> R.string.ui_bf0692a7035a
    "Describe this object for someone who can't see it." -> R.string.ui_16072bd26a50
    "Dictation" -> R.string.ui_25ce1a09ddc1
    "Different First Page" -> R.string.ui_3e28d5a1d21d
    "Different Odd & Even Pages" -> R.string.ui_fd5e04e7ee86
    "Display Preferences" -> R.string.ui_d8579568b391
    "Display for Review" -> R.string.ui_411b5bc0b0f1
    "Distribute" -> R.string.ui_b0585a6eba7b
    "Distribute Columns" -> R.string.ui_f3f332ef9736
    "Distribute Rows" -> R.string.ui_62c00159ce77
    "Do you want to keep the changes to %1\$s?" -> R.string.ui_282f14387ddc
    "Document" -> R.string.ui_d6bd8c0aeee8
    "Document Info" -> R.string.ui_758cc7adf666
    "Documents on this phone and in your file apps" -> R.string.ui_4bc79e89f8a8
    "Documents you open show here." -> R.string.ui_526a8ed429fa
    "Don't Save" -> R.string.ui_20961bde9480
    "Doughnut" -> R.string.ui_5d821b815f53
    "Download update" -> R.string.ui_4f494b22379b
    "Downloading update: %1\$d%%" -> R.string.ui_9437db71b92f
    "Draw" -> R.string.ui_61517c08b3bf
    "Draw with Pen" -> R.string.ui_a16bcc337125
    "Draw with Touch" -> R.string.ui_40dbfbab7d57
    "Earlier" -> R.string.ui_e10ae9907401
    "Edit Footer" -> R.string.ui_fccd11e8306c
    "Edit Header" -> R.string.ui_4ab2645dc4c7
    "Edit Link" -> R.string.ui_f0d155f6ac36
    "Edit Text" -> R.string.ui_eb25bd7c6059
    "Elements" -> R.string.ui_e1ff7a906103
    "Emphasis" -> R.string.ui_0ab4b2c43447
    "Endnote" -> R.string.ui_150c8fdadd59
    "Engine" -> R.string.ui_8e75ebbdb215
    "Equation" -> R.string.ui_560fa5972253
    "Equation Shapes" -> R.string.ui_a29285c93777
    "Even Page" -> R.string.ui_9cb248d4dcc3
    "Fast" -> R.string.ui_6c582b62e0e5
    "Fill" -> R.string.ui_033955d469b6
    "Find" -> R.string.ui_822b2ae4542b
    "Find settings" -> R.string.ui_fc622cc0fe20
    "Find..." -> R.string.ui_dec802b13c9c
    "First Column" -> R.string.ui_3e287123e00c
    "First Line" -> R.string.ui_8488547d55e2
    "Fixed Column Width" -> R.string.ui_e3fd5ebe8a19
    "Flowchart" -> R.string.ui_83ed55693dfc
    "Font" -> R.string.ui_64d0b3adcd2d
    "Font Color" -> R.string.ui_b3567e7a3805
    "Font Size" -> R.string.ui_4dd6ff92e0d9
    "Footer" -> R.string.ui_26c87bb51e69
    "Footnote" -> R.string.ui_aa8885bcba3b
    "For more details, " -> R.string.ui_6b30cb1b6d6c
    "Funnel" -> R.string.ui_fc063af055a3
    "General" -> R.string.ui_c910d474dcd7
    "Go to Endnote" -> R.string.ui_41a1b0d65eb6
    "Go to Footnote" -> R.string.ui_65b35fa5b8e6
    "Graphics" -> R.string.ui_a874fca87cdd
    "Graphics Effects" -> R.string.ui_8a763140d74c
    "Grayscale" -> R.string.ui_93638ea9e0b0
    "Hanging" -> R.string.ui_ebd52c8ba1fa
    "Header" -> R.string.ui_ba5caa4285a8
    "Header & Footer" -> R.string.ui_aaf783850031
    "Header Row" -> R.string.ui_42b9e2612ed8
    "Heading 1" -> R.string.ui_d95e9370ef74
    "Heading 2" -> R.string.ui_3291955e9005
    "Heading 3" -> R.string.ui_c6845f11c2d3
    "Headings" -> R.string.ui_a3e7821cf681
    "Height" -> R.string.ui_bfe2eca40179
    "Hide All Proofing Marks" -> R.string.ui_b7718b632afa
    "Hide Proofing Marks in Selected Text" -> R.string.ui_1fc46b007f4f
    "Hierarchy" -> R.string.ui_23be567534ab
    "Highlight" -> R.string.ui_07ccd15df32a
    "Highlighter" -> R.string.ui_314410c8b86e
    "Histogram" -> R.string.ui_b32dc489fba8
    "History" -> R.string.ui_0e7696009337
    "Home" -> R.string.ui_3a78695388b3
    "Horizontal" -> R.string.ui_0abba441f16e
    "I, II, III, …" -> R.string.ui_55e314195e4d
    "Ignore All" -> R.string.ui_5b9628750aec
    "In Front of Text" -> R.string.ui_32a73ccd6b9a
    "In Line with Text" -> R.string.ui_f839680c404a
    "Include Page Count" -> R.string.ui_a892daea47d1
    "Include textboxes, footnotes and endnotes" -> R.string.ui_357100c18e4a
    "Increase Indent" -> R.string.ui_7547adbc0da1
    "Ink Color" -> R.string.ui_4babde986022
    "Ink Editor" -> R.string.ui_80a4a667894f
    "Ink Thickness" -> R.string.ui_ebf063a16829
    "Inner" -> R.string.ui_fa338a5ea201
    "Insert" -> R.string.ui_8aa318eb7eaa
    "Insert Column Left" -> R.string.ui_ca764f106c30
    "Insert Column Right" -> R.string.ui_c56e17b4d400
    "Insert Row Above" -> R.string.ui_a6c1f35ccb5a
    "Insert Row Below" -> R.string.ui_928ae5db553d
    "Insert Rows and Columns" -> R.string.ui_6c0125167d16
    "Insert Table" -> R.string.ui_7660cc9246f7
    "Inside" -> R.string.ui_123a3ebc57e3
    "Install update" -> R.string.ui_cada4215d344
    "Intense Emphasis" -> R.string.ui_e50e9fc6c20d
    "Intense Quote" -> R.string.ui_b4d82d8253a0
    "Intense Reference" -> R.string.ui_1002c6ef0436
    "Inter typeface: SIL Open Font License 1.1" -> R.string.ui_c42912efa3ea
    "Italic" -> R.string.ui_9bf37cb58a69
    "JIS B4" -> R.string.ui_adf1250bb5ed
    "JIS B5" -> R.string.ui_4b9ae9b75590
    "Justify" -> R.string.ui_f3bd3473d7d8
    "Keywords" -> R.string.ui_8bfdb93cafaa
    "Landscape" -> R.string.ui_e658d3ae034a
    "Language" -> R.string.ui_a4fe65264ef7
    "Last Column" -> R.string.ui_1f875464bfd5
    "Later" -> R.string.ui_73b6e48a1b55
    "Layout" -> R.string.ui_a511909161e1
    "Left" -> R.string.ui_58eb9032e3bb
    "Left-to-Right Text Direction" -> R.string.ui_223e0fda7514
    "Legal" -> R.string.ui_4787eaf7c938
    "Legend" -> R.string.ui_7482e374b5bd
    "Letter" -> R.string.ui_1bf163f269ae
    "Letter (paper)" -> R.string.ui_202cbd005e98
    "Liberation typefaces: SIL Open Font License 1.1" -> R.string.ui_bcd0f5fc0066
    "Light" -> R.string.ui_dbcd5e7bb7a0
    "Line" -> R.string.ui_d7852cd0d245
    "Line Spacing" -> R.string.ui_45e098284e7f
    "Line Spacing:" -> R.string.ui_19c810152c05
    "Linear" -> R.string.ui_e6950b45d25c
    "Lines" -> R.string.ui_3b26a5427458
    "Link" -> R.string.ui_a6a32dbc5618
    "Lucide icons: ISC License" -> R.string.ui_4d9fdac7c6ba
    "Maps" -> R.string.ui_119e66d117ae
    "Margins" -> R.string.ui_eea8d245aa44
    "Mark as decorative" -> R.string.ui_f78a301c8274
    "Match Case" -> R.string.ui_f3766f1ddd69
    "Match Word" -> R.string.ui_1739f7bc5665
    "Matrix" -> R.string.ui_76a2171c057b
    "Merge Cells" -> R.string.ui_398456d6a82b
    "Mirrored" -> R.string.ui_ed059fe4e0d0
    "Mobile View" -> R.string.ui_d7bd8b5225b7
    "Moderate" -> R.string.ui_5c42afc7a230
    "Mon" -> R.string.ui_f40d7f51f69e
    "Monochromatic" -> R.string.ui_276a54ae7bb8
    "More Colors" -> R.string.ui_42ef0e721fd2
    "More Formatting" -> R.string.ui_73ec32ba5069
    "More languages are coming." -> R.string.ui_2e7839d9a1e9
    "More options" -> R.string.ui_bc79cdffbaa8
    "Narrow" -> R.string.ui_ba227109ba7e
    "New" -> R.string.ui_18fdd549b2ed
    "New Comment" -> R.string.ui_d244fabdef89
    "New blank document" -> R.string.ui_1503e41d1bb4
    "New comment" -> R.string.ui_cf9c25738241
    "Next Change" -> R.string.ui_7888235a7abc
    "Next Comment" -> R.string.ui_9bbfde029e9d
    "Next Page" -> R.string.ui_6ead5dfdc11e
    "No Color" -> R.string.ui_bceab57becfa
    "No Fill" -> R.string.ui_6bc87d2a9a50
    "No Markup" -> R.string.ui_578caa2c1e99
    "No Reflection" -> R.string.ui_edc8c12dd2d8
    "No Shadow" -> R.string.ui_60214436e13e
    "No Spacing" -> R.string.ui_2fb30737a609
    "No app on this phone can do that." -> R.string.ui_d38d7faf0624
    "No comments yet. Select some text and add one below." -> R.string.ui_e833cba0678d
    "No headings yet. Use the Heading styles to add some." -> R.string.ui_05580123d88b
    "No matching documents" -> R.string.ui_4e0ae55148f9
    "No results" -> R.string.ui_a43619f32117
    "None" -> R.string.ui_dc937b598926
    "Normal" -> R.string.ui_a7248eeb45eb
    "North" -> R.string.ui_43f853bbb592
    "Nothing matches “%1\$s”." -> R.string.ui_f56de6ecabe6
    "Number Format" -> R.string.ui_b953d78018d8
    "Number Format:" -> R.string.ui_ae643eb1d8c3
    "Numbering" -> R.string.ui_7b3ffc4c2065
    "Odd Page" -> R.string.ui_7330f192898f
    "Off" -> R.string.ui_ca7981b46ecf
    "Offset X" -> R.string.ui_57e52e87202a
    "Offset Y" -> R.string.ui_a320e52b9cd2
    "On" -> R.string.ui_130011756125
    "One" -> R.string.ui_8b12507783d5
    "One Page" -> R.string.ui_cd36ef5ef251
    "Open" -> R.string.ui_ed077f3d8125
    "Open Link" -> R.string.ui_61fb677d3203
    "Open a document" -> R.string.ui_624e71f237d1
    "Open in WordCraft" -> R.string.ui_320c6ae84a9e
    "Open material" -> R.string.ui_f4174a4c9ac4
    "Options" -> R.string.ui_d0db8b5e364b
    "Orientation" -> R.string.ui_e3d11242200f
    "Original" -> R.string.ui_88d759ea02ce
    "Other" -> R.string.ui_f97e9da0e3b8
    "Outer" -> R.string.ui_5e426f92df90
    "Outline" -> R.string.ui_eabbf3abaf8d
    "Outside" -> R.string.ui_dacf15163a11
    "Page" -> R.string.ui_0a30a815d67d
    "Page %1\$s" -> R.string.ui_482a6cdaa409
    "Page Breaks" -> R.string.ui_38c14be52f54
    "Page Number" -> R.string.ui_5ffac0c9dcdc
    "Page Width" -> R.string.ui_58e8f03d0851
    "Pages" -> R.string.ui_9046da16aea9
    "Paragraph Formatting" -> R.string.ui_49f7566d1056
    "Paste" -> R.string.ui_f3380f7b44bd
    "Pen" -> R.string.ui_a04bd35be167
    "Perspective" -> R.string.ui_e3ad1bcc2cbd
    "Photos" -> R.string.ui_5e3147ab51e0
    "Pick up where you left off" -> R.string.ui_47173482314e
    "Picture" -> R.string.ui_9a1c54f55e7c
    "Picture Effects" -> R.string.ui_71cd241b3535
    "Picture Position" -> R.string.ui_2f968713ffc5
    "Pictures" -> R.string.ui_1e6245009d67
    "Pie" -> R.string.ui_5c9659bd1a6d
    "Pin" -> R.string.ui_ff1cee744146
    "Pin documents to find them here." -> R.string.ui_6bcc94085099
    "Pinned" -> R.string.ui_f20c87946555
    "Portrait" -> R.string.ui_f71094c30160
    "Post" -> R.string.ui_a5554622c655
    "Presets" -> R.string.ui_954f93fe1c66
    "Previous Change" -> R.string.ui_27321e67f7ea
    "Previous Comment" -> R.string.ui_b166c5cebce7
    "Print" -> R.string.ui_df0fe79898ef
    "Print Layout" -> R.string.ui_d1293c46e11b
    "Print View" -> R.string.ui_1029a1a7a81b
    "Process" -> R.string.ui_e083bd83e9d3
    "Professional" -> R.string.ui_19c73a5cdf34
    "Promote" -> R.string.ui_5834dab08544
    "Proofing and Language" -> R.string.ui_135c22ca27eb
    "Pyramid" -> R.string.ui_b5c3abbc6906
    "Quote" -> R.string.ui_eb4cdebd82bf
    "Radial" -> R.string.ui_02693d5af964
    "Read Aloud" -> R.string.ui_38e07b2a0ca5
    "Reading Speed" -> R.string.ui_dbc16f1702cb
    "Recent" -> R.string.ui_690dbe9dc099
    "Recent Shapes" -> R.string.ui_a93df2ae2c4f
    "Recent Styles" -> R.string.ui_40dfa346fd02
    "Recently Used" -> R.string.ui_d61031acb443
    "Rectangles" -> R.string.ui_183bc1aabbc9
    "Redo" -> R.string.ui_74273989b096
    "Reflection" -> R.string.ui_7538c2135b03
    "Reflection Variations" -> R.string.ui_93f3d37260ee
    "Reject" -> R.string.ui_ab604a360777
    "Reject All Changes" -> R.string.ui_e445fc8ed6c0
    "Reject and Move to Next" -> R.string.ui_67dae4ca2958
    "Release notes are not available." -> R.string.ui_644d0520a9b5
    "Remove Footer" -> R.string.ui_c61ab19c1753
    "Remove Header" -> R.string.ui_2f2f09b9ec77
    "Remove Link" -> R.string.ui_e3e0591b816f
    "Remove Page Numbers" -> R.string.ui_8e13ce2e3131
    "Remove Space After Paragraph" -> R.string.ui_7bf634db2abe
    "Remove Space Before Paragraph" -> R.string.ui_fb127e3a415a
    "Remove from list" -> R.string.ui_82963c765306
    "Reopen" -> R.string.ui_a886d1dc4f12
    "Repeat Header Rows" -> R.string.ui_e219c08d75aa
    "Replace" -> R.string.ui_95e154398a4b
    "Replace All" -> R.string.ui_4bcc68df375a
    "Replace with..." -> R.string.ui_b15834545c24
    "Replaced items: %1\$s" -> R.string.ui_a113ba075366
    "Reply" -> R.string.ui_c253f451bdd5
    "Report" -> R.string.ui_b6ce788d9786
    "Reset" -> R.string.ui_daee7606b339
    "Resolve" -> R.string.ui_c8f193b315c8
    "Resolve Comment" -> R.string.ui_c106b3cb97f2
    "Results: %1\$s" -> R.string.ui_5c1820b8cce3
    "Resume" -> R.string.ui_d640c7421da0
    "Review" -> R.string.ui_aff0766a5290
    "Right" -> R.string.ui_883361d5d682
    "Right-to-Left Text Direction" -> R.string.ui_036bc76473ae
    "Rotate Left 90°" -> R.string.ui_6477117825c8
    "Rotate Right 90°" -> R.string.ui_d3553e241b23
    "Rotate all text 270°" -> R.string.ui_53cace9af8cb
    "Rotate all text 90°" -> R.string.ui_119fbf67c8e7
    "Rows" -> R.string.ui_101f2ff3de22
    "Sample" -> R.string.ui_95a2f1edb08a
    "Save" -> R.string.ui_1509f561f241
    "Save As" -> R.string.ui_088203b4cd88
    "Save changes?" -> R.string.ui_a241799a18cc
    "Scan Document" -> R.string.ui_fb2fb5787dcd
    "Scatter" -> R.string.ui_4720c0c3df2e
    "Search documents" -> R.string.ui_62706a35ade9
    "Section Breaks" -> R.string.ui_4874d6ec8fd6
    "Send Backward" -> R.string.ui_64a872d15087
    "Send Behind Text" -> R.string.ui_dfb82074c192
    "Send to Back" -> R.string.ui_33e1c6b9be0f
    "Sentence case." -> R.string.ui_e3033f08f2d4
    "Settings" -> R.string.ui_74a883a037bc
    "Shading" -> R.string.ui_495acbf9ec2c
    "Shadow" -> R.string.ui_4c764cb6248e
    "Shape" -> R.string.ui_e0e492707f85
    "Shape Styles" -> R.string.ui_21bc23db20c2
    "Shapes" -> R.string.ui_75e8028a7913
    "Share" -> R.string.ui_29887a5ff984
    "Share as PDF" -> R.string.ui_ee00ac0fd74a
    "Show" -> R.string.ui_0df6f1cad36c
    "Simple Frame White" -> R.string.ui_12f9d05d6de5
    "Simple Markup" -> R.string.ui_b14afc9fc328
    "Size" -> R.string.ui_1af851907331
    "Slow" -> R.string.ui_4b2bebbf33ae
    "SmartArt" -> R.string.ui_fe46ea12fa77
    "Sounds Like (English)" -> R.string.ui_e9ed07976002
    "Source Sans 3, Source Serif 4, JetBrains Mono: SIL Open Font License 1.1" -> R.string.ui_1b0b66be9ace
    "South" -> R.string.ui_0dd2dae4dd83
    "Special Indent" -> R.string.ui_d80792fcbbe0
    "Speech isn't available on this phone." -> R.string.ui_70997000a0b7
    "Spelling" -> R.string.ui_3274f5642387
    "Split Cells..." -> R.string.ui_b495689dd999
    "Square" -> R.string.ui_c11092bc0861
    "Stacked Bar" -> R.string.ui_76c7392a8a3f
    "Stacked Column" -> R.string.ui_29f063f20a96
    "Standard Colors" -> R.string.ui_b1b8d0222c6a
    "Stars and Banners" -> R.string.ui_2f263fce0211
    "Start WordCraft in Mobile View" -> R.string.ui_e318723c490c
    "Start writing" -> R.string.ui_b94c5ac62b87
    "Strikethrough" -> R.string.ui_9aabbcb2c18d
    "Stroke Eraser" -> R.string.ui_71f297ce530f
    "Strong" -> R.string.ui_30a04fdcb6bb
    "Style" -> R.string.ui_7e744141b40e
    "Style Options" -> R.string.ui_3dcaa4145d09
    "Styles" -> R.string.ui_3626e7fdea1c
    "Subject" -> R.string.ui_68971283841a
    "Subscript" -> R.string.ui_cd512a33cc6f
    "Subtitle" -> R.string.ui_383cd6c0b871
    "Subtle Emphasis" -> R.string.ui_5efe83c74852
    "Subtle Reference" -> R.string.ui_0434fb03ecc4
    "Suggested" -> R.string.ui_93b63bab4c3a
    "Sunburst" -> R.string.ui_78a6fbadb431
    "Superscript" -> R.string.ui_31a0bab13ba8
    "Switch to Dark Background" -> R.string.ui_902ab2e84c38
    "Switch to Light Background" -> R.string.ui_40aa6b8c5f83
    "System Default" -> R.string.ui_d8081c667c73
    "Table" -> R.string.ui_16d1c9050a0b
    "Table Styles" -> R.string.ui_a68a131cef41
    "Tell me what you want to do" -> R.string.ui_4ed7b3c73e47
    "Text" -> R.string.ui_71988c4d8e08
    "Text Box" -> R.string.ui_dc7ec1ed1293
    "Text Encoding" -> R.string.ui_ab5ad1dc20c4
    "Text Rotation" -> R.string.ui_f554803c263b
    "Text Wrapping" -> R.string.ui_c16ddd67fb8c
    "Text to display" -> R.string.ui_9b5f5a9f96df
    "That document can't be opened any more." -> R.string.ui_2e4ea3af6fc9
    "The %1\$s tab is on its way." -> R.string.ui_9f504bdae859
    "The document can't be opened: %1\$s" -> R.string.ui_df46b8a2d60d
    "The document can't be saved there." -> R.string.ui_990843d46cd8
    "The picture can't be read." -> R.string.ui_31390ccb9d73
    "Theme" -> R.string.ui_efb52e7172b7
    "Theme Colors" -> R.string.ui_61a90ad013ec
    "Theme Fonts" -> R.string.ui_8e68ca5719f2
    "Theme Styles" -> R.string.ui_8dce82837722
    "There's nothing to read." -> R.string.ui_5fd67d9e5ac9
    "Thickness" -> R.string.ui_7b5944144275
    "This document has pictures, shapes or drawings that Mobile View can't show the way the pages do." -> R.string.ui_3ce24977da66
    "This document is protected with a password." -> R.string.ui_062670e83372
    "This home screen doesn't take shortcuts." -> R.string.ui_a0da784a9724
    "This week" -> R.string.ui_8c4eef5ab253
    "Three" -> R.string.ui_926f52d1c1e1
    "Through" -> R.string.ui_afc6abac44c4
    "Tight" -> R.string.ui_ce0cc8415d93
    "Title" -> R.string.ui_7e8cd2056da7
    "Today" -> R.string.ui_2b065c7c9ce4
    "Top" -> R.string.ui_d5cdfcf7ff75
    "Top and Bottom" -> R.string.ui_18b165204085
    "Top: %1\$s" -> R.string.ui_df934e8d9405
    "Total Row" -> R.string.ui_a242c7539faf
    "Track Changes" -> R.string.ui_32457b2aee5c
    "Treemap" -> R.string.ui_66ba877f1741
    "Tue" -> R.string.ui_d1eb39b09bf5
    "Two" -> R.string.ui_94a72c074cfe
    "Type" -> R.string.ui_baaddf70fb5d
    "Underline" -> R.string.ui_02f843261112
    "Undo" -> R.string.ui_a8283ade3185
    "Unpin" -> R.string.ui_ee3c71613054
    "Update" -> R.string.ui_c1c1009d3f37
    "Update details" -> R.string.ui_91ea7f2353f6
    "Upper" -> R.string.ui_ada6d7a662f0
    "Venn" -> R.string.ui_faeee26be226
    "Version %1\$s" -> R.string.ui_b65bfa7f1eb0
    "View" -> R.string.ui_dcc839a4015c
    "View release" -> R.string.ui_db51f7282b31
    "Voice" -> R.string.ui_87bf2bc08589
    "Waterfall" -> R.string.ui_6b53bc225e0a
    "Wed" -> R.string.ui_58339f45df96
    "West" -> R.string.ui_41a6741e9a0f
    "What's new" -> R.string.ui_135e912e8905
    "White" -> R.string.ui_3495e757855a
    "Wide" -> R.string.ui_147170e793c6
    "Width" -> R.string.ui_3019947b6daf
    "Word Count" -> R.string.ui_dfe17006f6a2
    "WordCraft" -> R.string.ui_6553c656f2d5
    "WordCraft couldn't start: %1\$s" -> R.string.ui_01cb90e3a1fe
    "WordCraft isn't allowed to do that." -> R.string.ui_da53eb9ec94e
    "Words" -> R.string.ui_0a8be89a52a8
    "Wrap Text" -> R.string.ui_bd4ea610add7
    "X Y (Scatter)" -> R.string.ui_81dbe633e0d7
    "Yesterday" -> R.string.ui_566181254b29
    "Your app is up to date" -> R.string.ui_9ad8837fdf66
    "Zoom In" -> R.string.ui_5a982cf9dc11
    "Zoom Out" -> R.string.ui_24649ee0c7a9
    "click here" -> R.string.ui_9e1e4d616556
    "cm" -> R.string.ui_b5039c325d71
    "in" -> R.string.ui_582967534d0f
    else -> null
}

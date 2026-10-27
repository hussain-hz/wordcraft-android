//! Compatibility tests for the 0.10 bounded-read backport used by Android.
#[test]
fn file_reads_preserve_bytes_and_reject_directories() {
    use wordcraft_engine::io;
    let dir = std::env::temp_dir().join(format!(
        "wordcraft-read-{}-{}",
        std::process::id(),
        std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()
    ));
    std::fs::create_dir(&dir).unwrap();
    let path = dir.join("sample.txt");
    let bytes = "Arabic: \u{645}\u{631}\u{62d}\u{628}\u{627}".as_bytes();
    std::fs::write(&path, bytes).unwrap();
    assert_eq!(io::read_path(&path).unwrap(), bytes);
    assert_eq!(io::read_file(path.to_str().unwrap()).unwrap(), bytes);
    assert!(io::read_path(&dir).unwrap_err().contains("not a file"));
    assert!(io::read_file(dir.to_str().unwrap()).unwrap_err().contains("not a file"));
    assert!(io::read_path(&dir.join("missing")).is_err());
    std::fs::remove_file(path).unwrap();
    std::fs::remove_dir(dir).unwrap();
}

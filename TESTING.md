# Testing Strategy and Results

**Assignment To Do 5:** design the testing strategy and demonstrate the exact NRRD + CSV → MP4 CLI behavior required by To Do 7.

## Test pyramid

| Level | Test classes | What is checked |
| --- | --- | --- |
| Geometry / projection unit tests | `ProjectionTest`, `Stage5SliceCorrectnessTest` | Isosceles validity, triangle/trapezoid hand-calculated pixels, XZ/slanted planes, rounding, bounds, unsigned extrema. |
| Input parser tests | `NrrdReaderTest`, `CsvPlaneReaderTest`, `ProfessorInputCompatibilityTest` | NRRD raw/gzip and bit depths, signed-short handling, endianness, malformed input; CSV formats, row counts, source-line diagnostics, professor CSV invalidity. |
| Image and codec unit tests | `IntensityMapperTest`, `FrameComposerTest`, `VideoWriterTest`, `Stage6DirectRgbTest` | Global gray range, padding, video dimensions/order, MP4 decodability and experimental RGB-path sanity. |
| End-to-end integration | `MainTest`, `Stage5PipelineAcceptanceTest`, `Stage6TimingBreakdownTest` | Read NRRD and CSV, produce MP4 in correct order, preserve existing output when inputs are invalid, print separate timing measures. |
| Manual acceptance / experiment | `Main`, `FullPipelineBenchmark` | Render actual MRI at 60 planes; generate and encode 500³ phantom; inspect MP4; measure per-stage FPS. |

The final uploaded project contains **12 active JUnit test classes**. Its archived Surefire reports for these classes contain **77 tests, 0 failures, 0 errors, 0 skipped**. An old failed `Stage6DirectYuvTest` report appears only in `target/surefire-reports` (a stale build artifact); the corresponding test source has already been removed. The user independently confirmed a successful subsequent Maven test run. A fresh `mvn clean test` removes obsolete reports before verification.

## Automated reproduction

1. Open the project in **IntelliJ IDEA**.
2. Open **View → Tool Windows → Maven**.
3. Execute **Lifecycle → clean**, then **Lifecycle → test**.
4. Confirm `BUILD SUCCESS`, and that `target/surefire-reports` contains only active test classes. A fresh run should report 77 tests for this source snapshot.

Alternatively, from a terminal with Maven installed: `mvn clean test`.

## Manual acceptance scenarios

1. **MRI demo:** Run `Main` with `samples/MRHead_1.nrrd samples/mrhead-demo-60.csv target/mrhead-demo-60.mp4`. Expect exit 0; 256×256×130 loaded; exactly 60 frames, canvas 196×196 at 30 playback FPS. Video shows varying brain slices rather than static/black frames.
2. **500³ pipeline:** Run `FullPipelineBenchmark 500 120`. Expect successful source generation/loading, 120 frames, 500×500 canvas, 4-second playback at 30 FPS, and projection timing printed separately from encoding.
3. **Invalid original CSV:** Run `Main` using `samples/ex0-professor-original.csv`. Expect a clear error pointing at physical line 4 (invalid triangle geometry), and no newly published video. The actual professor CSV is **not** expected to produce a valid 60-frame MRI video.
4. **Input failures:** Verify missing NRRD/CSV, malformed header, negative signed-short voxel and unsupported NRRD type produce explanatory failures.
5. **Frame integrity:** Check MP4 stream metadata and visual progression. Archived output videos were independently inspected with `ffprobe`: MRI is H.264 196×196 30 FPS 60 frames/2s; synthetic is H.264 500×500 30 FPS 120 frames/4s.

## Performance methodology

Report the **processing throughput of `PlaneSampler`** independently from grayscale, composition, loading, encode, and total wall time. Use a 500³ *byte* volume for the course performance requirement. The archived experimental measurements were obtained in a single run; repeat runs and JIT warm-up are recommended for robust cross-machine comparisons. Do not confuse the **30 playback FPS** in the video with the number of slices created per second.

See [EXPERIMENT.md](EXPERIMENT.md).

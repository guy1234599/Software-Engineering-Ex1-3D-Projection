# Experiment Report — Plane Projection from a 3D NRRD Volume

**Course:** Introduction to Software Engineering (OOP/OOD) — Exercise 1
**Date of report:** 2026-11-07
**Implemented in:** Java 21-compatible source, run using OpenJDK 24.0.1; JCodec 0.2.5 H.264 encoding.

## 1. Purpose

Evaluate whether the plane-projection algorithm meets the assignment's speed expectations and whether the complete NRRD + CSV → MP4 pipeline works for both a large synthetic dataset and the course's real MRI example. The assignment specifies **over 30 processing frames per second** as compulsory and **100+** as the expected rate for a 500³-byte volume.

## 2. Equipment and procedure

**Machine (user-reported):** Lenovo IdeaPad Flex 5 14ALC7, AMD Ryzen 5 5500U, 16 GB RAM, Windows 11. **JVM:** OpenJDK 24.0.1. **Tools:** IntelliJ IDEA Community 2025.1 / Maven / JUnit 5.11.4.

`FullPipelineBenchmark` generates or reuses a deterministic 500×500×500 unsigned-byte NRRD phantom outside its timed interval, creates 120 geometrically valid smooth planes, and runs the actual `Main` implementation at 30 *playback* FPS. The benchmark reports projection, gray conversion, canvas composition, MP4 encoding, finalization, volume load/range scan, and full wall-clock duration separately. A separate `Main` run loads the lecturer's gzip-compressed `MRHead_1.nrrd` with 60 valid axial-like cutting planes.

**Timing limitation:** The following are observed single-run outputs from the project run on the user's computer, not a statistical average with confidence intervals or controlled JIT warm-up. Re-running may yield different values.

## 3. Results — synthetic performance workload

**Input:** 500×500×500 unsigned 8-bit NRRD (125,000,000 voxel bytes); **planes:** 120; **frame size:** 500×500; **video:** 30 playback FPS / 4.00 s.

| Timed operation | Observed |
| --- | ---: |
| Volume loading + intensity range scan | 714.98 ms |
| Projection calculation for 120 slices | **399.53 ms** |
| Projection-only rate | **300.4 slices/s** |
| Grayscale conversion | 117.60 ms |
| Frame canvas composition | 165.22 ms |
| Video encoding (`JCodec writeFrame`) | 4667.96 ms |
| MP4 finalization | 39.28 ms |
| End-to-end wall clock | **6.283 s** |
| End-to-end output rate | **19.1 frames/s** |

**Produced video:** [`results/pipeline-phantom-v2-smooth-500-120.mp4`](results/pipeline-phantom-v2-smooth-500-120.mp4). Independent `ffprobe` inspection of the archived output confirmed **H.264, 500×500, 120 frames, 30 fps, 4.00 s**.

Projection exceeds both the assignment's **30 mandatory** and **100 expected** slice/s targets when measuring the projection algorithm alone. MP4 encoding is the largest time component; wall-clock processing at 19.1 frames/s would **not** reach a 30 frames/s target if the lecturer interprets FPS as the entire MP4 pipeline. The handout does not explicitly settle that interpretation.

## 4. Results — lecturer's MRI

**Input:** `samples/MRHead_1.nrrd` (256×256×130, 16-bit signed `short`, gzip compression, nonnegative voxel values, observed range 0–279).  **Planes:** 60 geometrically valid depth-varying trapezoids in `samples/mrhead-demo-60.csv`. **Output:** 196×196 H.264 MP4, 30 playback FPS, 2.00 s.

The executed `Main` command reported:

```text
Video pipeline mode: awt
Loaded volume: 256 x 256 x 130
Frames: 60, video: 196 x 196, playback: 30 FPS
Projection: 40.44 ms total, 1483.8 FPS (excluding image conversion and video encoding)
Grayscale conversion: 24.32 ms total
Frame composition: 54.49 ms total
Video encoding (JCodec writeFrame): 456.51 ms total
MP4 finalization (writer.close): 35.53 ms
Frame conversion + encoding: 535.31 ms total
Volume loading + range scan: 114.64 ms
Created: target/mrhead-demo-60.mp4
```

**Produced video:** [`results/mrhead-demo-60.mp4`](results/mrhead-demo-60.mp4). The archived MP4 was independently checked with `ffprobe`: **H.264, 196×196, 60 frames, 30 fps, 2.00 s**. Its frames exhibit changing brain slices with no blank/video-wide failure. The 1,483.8 FPS figure is **projection-only** on a smaller volume and smaller frame size; it must **not** be substituted for the 500³ requirement. A separate end-to-end wall-clock measurement for this MRI run was not recorded.

## 5. Validation and design caveats

- Automated JUnit acceptance tests exercise NRRD formats, plane geometry, pixel values, CSV ordering, error handling, grayscale, and MP4. The source snapshot's active Surefire reports contain 77 passing tests; a stale historical failing Direct-YUV report under `target/` was excluded because no such test source remains.
- The original professor spreadsheet contains coordinates and/or invalid geometry that do not match our MRI demonstration. We preserved it unchanged in `samples/ex0-professor-original.csv` for error-handling checks and created a separate valid 60-plane CSV for the MRI movie.
- Our nearest-neighbour pixel-centre, orthogonal-basis algorithm is a documented design choice. It can produce different individual pixel values from the handout's unit-step side/scanline pseudocode. Exact numerical equivalence is **not** claimed.
- Measurements were made on a single laptop and are subject to JVM warm-up, available memory, file-system caching and encoder costs.

## 6. Conclusion

The implemented **projection algorithm** achieves **300.4 slices/s** on the course's 500³-byte performance workload, exceeding the mandatory >30 and expected ≥100 processing targets under the projection-only definition. The complete NRRD/CSV/MP4 chain also successfully produces visually meaningful, ordered video from the instructor-linked MRI. Video encoding, not slice computation, dominates end-to-end runtime. The submission includes both MP4 outputs to reproduce and inspect the results.

**Acceptance question left explicit:** If the instructor requires either *end-to-end encoding at >30 FPS* or *pixel-identical adherence to the supplied unit-step scanline code*, this implementation requires further work or approval. It must not be presented as already meeting either interpretation.

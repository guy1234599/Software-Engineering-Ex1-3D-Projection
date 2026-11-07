# Software Design (OOD) — Ex1: 3D Volume Plane Projection

**Version:** 1.0 — implementation-aligned (2026-11-07)
**Status:** Implemented design. See the source under `src/main/java/org/example/projection/`.

## 1. Design goals

Separate input decoding, geometry validation, nearest-neighbour voxel sampling, 2D image creation, grayscale conversion, canvas composition, and MP4 encoding. Make each component testable in isolation, keep voxel data in compact primitive arrays, avoid per-pixel object allocations, and preserve input frame ordering.

## 2. Implemented processing flow

```text
Main (3 CLI paths)
   |-- CsvPlaneReader -> List<PlaneDefinition> -> GeometryValidation
   |-- NrrdReader     -> Volume -> IntensityMapper (global min/max)
   |-- Main.determineCanvas (once)
   `-- for each plane, in order:
          PlaneSampler(NearestNeighborSampler) -> Image2D
          IntensityMapper -> TYPE_BYTE_GRAY BufferedImage
          FrameComposer -> uniform black TYPE_3BYTE_BGR BufferedImage
          VideoWriter (JCodec AWTSequenceEncoder) -> H.264 MP4

The MP4 is written to a staging path and published only after closing the encoder.
```

The Direct RGB encoder path (`-Dprojection.videoMode=direct-rgb`) remains an experimental alternative; the user-tested default is `awt`.

## 3. Actual class responsibilities

| Component | Type | Contract |
| --- | --- | --- |
| `Volume` | interface | `sizeX/Y/Z`, `getVoxel(x,y,z): long` for unsigned sample access. |
| `ArrayVolume` | package-private abstract class | Bounds checking and x-fastest array indexing. |
| `ByteVolume`, `ShortVolume`, `IntVolume` | final classes | `byte[]`, `short[]`, `int[]` storage with unsigned reads. Arrays are owned by instances and not copied. |
| `Point3D` | record | Immutable finite `(x,y,z)`; vector arithmetic. |
| `PlaneDefinition` | interface | Shared plane endpoint access. |
| `TrianglePlane`, `TrapezoidPlane` | records | Validate ordered, isosceles, nondegenerate shapes at construction. |
| `GeometryValidation` | package-private utility | `EPS=1e-6` relative side/base/plane checks. |
| `VoxelSampler` | interface | Continuous-position sample strategy. |
| `NearestNeighborSampler` | final class | Reject outside `[0,size)` first; round with `Math.round`; return 0 if rounded index falls outside. |
| `PlaneSampler` | final class | Generate `Image2D` using geometry's orthogonal pixel-centre basis and scanline index bounds. |
| `Image2D` | final class | `int[]` stores raw 32-bit patterns; `getUnsigned` returns unsigned `long`. |
| `NrrdReader` | final class | Attached 3D NRRD raw/gzip, 8/16/32-bit unsigned, nonnegative signed short; descriptive errors. |
| `CsvPlaneReader` | final class | Either 9/12 numeric columns, or professor header with count column; preserves ordering and line numbers. |
| `IntensityMapper` | final class | Single min/max scan, maps raw intensities to `TYPE_BYTE_GRAY` at 8 bits. |
| `FrameComposer` | final utility | Centre a source image on black even-sized RGB canvas, without rescaling. |
| `VideoWriter` | final `AutoCloseable` class | JCodec `AWTSequenceEncoder`, H.264 MP4; publishes after successful encoding and close. |
| `Main` | final class | Validates positional args, plans canvas, processes frames, prints timings and reports errors. |
| `ProjectionBenchmark` | stand-alone entry point | Slice-only performance. |
| `FullPipelineBenchmark` | stand-alone entry point | Generate/reuse synthetic NRRD, run actual `Main`, measure full pipeline. |
| `Stage6EncodingABBenchmark` | stand-alone entry point | Experimental repeated AWT vs Direct RGB comparison (not a production dependency). |

## 4. Data model and memory

For dimensions `sx × sy × sz`, values are stored with **x fastest**:

```text
index(x,y,z) = x + sx * (y + sy*z)
```

The input volume remains loaded once for all slices. A 500³ byte fixture uses 125 million bytes of voxel payload (about 119.2 MiB); output memory per slice grows with `O(W×H)`. `PlaneSampler` allocates one `Image2D` and increments voxel positions with primitive `double` values inside each row instead of new `Point3D` instances. Both image and video canvas are capped at 25 million pixels.

`Image2D` values remain at source bit depth; the grayscale palette is a separate display conversion. The same volume-wide min and max are used for every frame, avoiding brightness flicker caused by per-frame normalization.

## 5. Geometry and rasterization

Only triangle `A,B,C` with `|AB|≈|AC|`, and trapezoid `A1,A2,B,C` with parallel same-direction bases and equal legs, are valid. Construction checks for positive dimensions, coplanarity and nondegeneracy. `PlaneSampler` uses a basis `u` along the lower base and perpendicular `v` toward it, scans valid row intervals and samples each pixel centre at a 3D point. Pixels outside the patch are zero. See [ALGORITHM.md](ALGORITHM.md) for equations, sample matrices and complexity.

**Explicit design choice:** this method preserves unit distances in voxel-index coordinates, but is **not** the handout's unit-step along-triangle-legs scan; exact sample equality is not assured.

## 6. Error handling and output stability

- `Main.run` accepts exactly 3 paths and returns status `0` on success, `1/2` for input/usage failures. `Main.main` delegates and uses a nonzero process exit for errors.
- Parse/validate **all** plane definitions before volume loading or output creation. A CSV record with unknown fields or invalid geometry fails with source line number.
- NRRD malformed metadata/payload is rejected with filename and cause.
- Output MP4 is initially staged in temporary files; publication occurs only after the encoder successfully closes.
- Existing destination is preserved on validation failure. Parent folders are created when necessary.
- A 128×128 even canvas floor avoids extremely small H.264 streams that some Windows decoders reject.

## 7. Testability and benchmark separation

Unit and integration tests are under `src/test/java/org/example/projection/`. The current snapshot has 12 active test classes and 77 passing tests in its last archived Surefire reports; one obsolete failed Direct-YUV report survives **only inside the ignored `target/` build directory**, not in source tests. A clean Maven test run should regenerate only the active reports.

`Main` prints projection, grayscale, composition, encoder, finalization and volume-load timings separately. `FullPipelineBenchmark` adds measured wall-clock duration (including additional orchestration and I/O overhead). MP4 playback rate is not processing throughput.

## 8. Design choices and limitations

- Java 21+ / Maven; JCodec 0.2.5 (default AWT path).
- Nearest neighbour (not trilinear); 3/4-point isosceles shapes, not general polygons.
- CSV coordinates represent voxel indices, regardless of NRRD spatial orientation or voxel spacing.
- Attached raw/gzip NRRD only; reject unsupported types/encodings/detached headers and negative signed-short voxels.
- The lecturer's full original spreadsheet has geometric invalidity in some records; a separate valid 60-plane MRI demo CSV is used for the deliverable.
- The current Maven project builds a non-executable ordinary JAR by default. The Windows `run-demo.cmd` or an IntelliJ `Main` configuration provides an executable three-argument demo without requiring extra Maven packaging plugins.

# Software Requirements Specification (SRS)

**Project:** Software Engineering Ex1 — 3D Volume Plane Projection  
**Version:** 0.1 (Draft)  
**Status:** Requirements approved in discussion; unresolved design choices are explicitly marked **TBD**.

## 1. Purpose and scope

The application accepts a 3D raster volume stored in an NRRD file and an ordered list of 3D plane definitions stored in a CSV file. It generates a two-dimensional sampled image for each plane and exports those images, in the same order, as frames in an MP4 video.

The project prioritizes correctness, robust input handling, clear object-oriented design, automated testing, and efficient slice generation.

**In scope:** isosceles triangles, isosceles trapezoids, unsigned 8/16/32-bit volume data, voxel-index coordinates, nearest-neighbor sampling, RAW/GZIP NRRD payloads, and MP4 output.

**Not in the initial scope:** general polygons, arbitrary non-isosceles triangles, physical-coordinate CSV inputs, or trilinear interpolation.

## 2. Input requirements

| ID | Requirement |
| --- | --- |
| FR-01 | The system shall load a three-dimensional raster volume from an NRRD file. |
| FR-02 | The system shall support unsigned 8-bit, 16-bit, and 32-bit integer voxel values without discarding their original bit patterns during storage and sampling. |
| FR-03 | The system shall support NRRD payload encodings `raw` and `gzip` (`gz` where applicable). |
| FR-04 | The reader shall interpret NRRD `dimension`, `sizes`, `type`, `encoding`, and relevant `endian` metadata, and report invalid or unsupported values. |
| FR-05 | The system shall reject unsupported dimensions, truncated payloads, and inconsistent declared data sizes with a descriptive error. |
| FR-06 | The system shall read an ordered CSV list of plane definitions without a header. Each nonempty record shall contain 9 numeric coordinates (three points) or 12 numeric coordinates (four points). |
| FR-07 | The CSV coordinates shall be interpreted in voxel-index coordinates `(x, y, z)`, not physical millimetres. Decimal coordinates are valid. |
| FR-08 | The system shall determine the plane shape from the number of coordinates: 9 for an isosceles triangle and 12 for an isosceles trapezoid. |
| FR-09 | The system shall validate that coordinates are finite numbers and that plane geometry is nondegenerate and satisfies the specified geometric constraints. |

### 2.1 CSV coordinate ordering

- Triangle: `Ax,Ay,Az,Bx,By,Bz,Cx,Cy,Cz`.
- Trapezoid: `A1x,A1y,A1z,A2x,A2y,A2z,Bx,By,Bz,Cx,Cy,Cz`.
- One plane definition per CSV record; records are processed in file order.
- **TBD:** policy for blank lines and comments, numeric tolerance for equal side lengths and parallelism.

### 2.2 NRRD scope notes

- The initial reader targets three-dimensional, scalar, attached-data `.nrrd` files.
- Correctly interpret raw byte order for 16- and 32-bit values; decompress GZIP payloads before decoding the values.
- **TBD:** detached-header `.nhdr` / external `data file` support, depending on the chosen dataset.
- Spatial metadata, if present, may be retained, but the initial CSV coordinate convention is voxel indices.

## 3. Processing requirements

| ID | Requirement |
| --- | --- |
| FR-10 | The system shall generate one 2D sampled image per valid plane. |
| FR-11 | The initial sampling strategy shall be nearest neighbour. |
| FR-12 | Sampling coordinates shall be rounded consistently to discrete voxel indices; samples outside the volume shall return zero. |
| FR-13 | For triangles, the logical output matrix dimensions shall initially follow the assignment's dimensions: `width = ceil(distance(B,C))`, `height = ceil(distance(A,B))`. |
| FR-14 | Plane rasterization shall be deterministic for fixed input data and plane coordinates. |
| FR-15 | The sampling and output-image components shall remain independent from file parsing and video encoding. |

**TBD — geometric mapping:** The precise placement of pixel centres, triangle boundaries, and trapezoid dimensions remains under design. An aspect-ratio-preserving alternative was discussed but is not yet finalized. The final algorithm specification must explicitly distinguish choices that differ from the lecturer's reference pseudocode.

## 4. MP4 output requirements

| ID | Requirement |
| --- | --- |
| FR-16 | The system shall generate an `.mp4` video with exactly one frame for each valid plane in the CSV input, in the same order. |
| FR-17 | The default playback frame rate shall be **30 FPS**. This is separate from the processing throughput requirement. |
| FR-18 | Every encoded video frame shall have the same dimensions. Images of different sizes shall be centred on a uniform black canvas, without nonuniform stretching. |
| FR-19 | The canvas shall be large enough for all generated images, with any additional even-dimension padding required by the selected encoder. |
| FR-20 | The video shall display sampled intensities as 8-bit grayscale, using one fixed linear intensity mapping for all frames. |
| FR-21 | The source voxel values shall remain unchanged by the grayscale conversion. The mapping range shall be calculated once from the source volume; the case `min == max` shall be handled safely. |

## 5. Validation and error handling

| ID | Requirement |
| --- | --- |
| FR-22 | The entire CSV list shall be validated before video generation begins. |
| FR-23 | On invalid input, processing shall stop, return a nonzero process exit code, and explain the error. |
| FR-24 | CSV errors shall include the file name, source line number, reason, and relevant values or constraints where feasible. |
| FR-25 | The program shall not leave an incomplete output video after an input-validation or encoding failure. |
| FR-26 | Missing files, inaccessible paths, unsupported NRRD data, and output-write failures shall produce actionable error messages. |

A plane that extends beyond the volume is not inherently invalid: sampled points outside the volume produce zero, as required by the assignment.

## 6. Non-functional requirements

| ID | Requirement |
| --- | --- |
| NFR-01 | Slice generation on a `500 × 500 × 500` 8-bit volume shall achieve **more than 30 slices/second** (mandatory target stated by the assignment); **at least 100 slices/second** is the expected performance goal. |
| NFR-02 | Processing throughput shall be benchmarked independently from MP4 encoding and disk I/O; the experimental report shall describe the hardware and measurement method. |
| NFR-03 | Volume samples shall be stored in contiguous primitive arrays appropriate to their bit depth (`byte[]`, `short[]`, or `int[]`) with a documented axis order. |
| NFR-04 | The design shall separate responsibilities such as volume loading, geometry, sampling, intensity conversion, and video writing. |
| NFR-05 | Unit and integration tests shall cover normal inputs, invalid data, geometric boundaries, bit depths, NRRD encoding variants, and output-frame ordering. |
| NFR-06 | The source shall be version-controlled with meaningful commits and documented build/run instructions. |

**Performance interpretation:** The lecturer refers to FPS for algorithm runtime, but does not explicitly specify whether video encoding is included. The project will report both slice-generation throughput and end-to-end execution time, clearly labelled.

## 7. Proposed architecture

- `Volume`: read-only access to the 3D raster and dimensions.
- `ByteVolume`, `ShortVolume`, `IntVolume`: bit-depth-specific contiguous storage.
- `Point3D`: 3D coordinate value type.
- `PlaneDefinition`: validated 3- or 4-point shape description.
- `VoxelSampler` / `NearestNeighborSampler`: sampling strategy.
- `PlaneSampler`: geometry-to-image sampling algorithm.
- `Image2D`: sampled plane image.
- `NrrdReader`: NRRD header, decoding, and payload loading.
- `CsvPlaneReader`: ordered CSV parsing and diagnostics.
- `IntensityMapper`: source intensity to 8-bit grayscale mapping.
- `VideoWriter`: constant-size video frame output.
- `Main`: command-line wiring and error reporting.

This section documents the approved preliminary architecture, not completed implementation.

## 8. Interface and deliverables

**Proposed Java command (not yet implemented):**

```sh
java -jar target/volume-projection.jar input.nrrd planes.csv output.mp4
```

**TBD:** The assignment's example uses `python Ex0.py ...`, while also explicitly permitting Java. Confirm whether the demo entry point must match that exact invocation or whether an equivalent Java CLI is acceptable.

Required project deliverables:

1. Repository and README defining the problem and scope.
2. SRS document.
3. Detailed main-algorithm design.
4. Documented dataset of NRRD files.
5. Testing strategy including demo coverage.
6. Implementation with frequent commits.
7. Working demonstration that outputs an MP4 for the ordered CSV planes.
8. Experiment summary and resulting MP4 file.

## 9. Acceptance tests (initial plan)

| ID | Acceptance criterion |
| --- | --- |
| AT-01 | Load small synthetic NRRD volumes for each supported unsigned bit depth and verify known voxel values. |
| AT-02 | Load equivalent RAW and GZIP volumes and verify identical sampled values. |
| AT-03 | Parse a CSV containing mixed 9- and 12-number records and preserve their order. |
| AT-04 | Reject a malformed plane with an error identifying the correct file and line number. |
| AT-05 | Verify nearest-neighbour rounding and zero for out-of-bounds samples. |
| AT-06 | Verify that every video frame uses a common canvas, without aspect-ratio distortion. |
| AT-07 | Verify MP4 frame count and order using a dataset with identifiable planes. |
| AT-08 | Benchmark repeated slice generation on an 8-bit `500³` volume and report measured throughput, hardware, and methodology. |

## 10. Open questions before SRS version 1.0

1. Final triangle rasterization convention and compatibility with the lecturer's reference pseudocode.
2. Exact output dimensions and coordinate ordering for trapezoids.
3. Numeric tolerance for isosceles geometry and collinearity/parallelism checks.
4. Handling blank lines, comments, and CSV quoting.
5. Whether datasets require detached NRRD headers (`.nhdr`).
6. Whether a Java command-line invocation fully satisfies the demo interface requirement.
7. Encoder/library selection and output behavior if no valid planes are present.

---

**Traceability:** This draft captures the decisions accepted so far. It intentionally avoids claiming that unresolved mathematical and interface choices have been approved.

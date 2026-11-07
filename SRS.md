# Software Requirements Specification (SRS)

**Project:** Software Engineering Ex1 — 3D Volume Plane Projection
**Version:** 1.0 — implementation-aligned, 2026-11-07
**Status:** Implemented/tested scope and explicitly documented exclusions. This SRS reflects the delivered Java source, not a future proposal.

## 1. Goal and boundaries

Given a scalar 3D volume `M` and an ordered list of isosceles-triangle or isosceles-trapezoid patches in 3D, sample one 2D image per patch and produce a corresponding H.264 MP4 frame. The geometry sampling method is deterministic; all points outside the source volume have value zero. The educational assignment values algorithm design, code reliability and runtime performance.

Input point coordinates represent **voxel array indices**. NRRD physical spacing and orientation fields are not used to transform the CSV coordinates.

## 2. Requirements traceability

| ID | Requirement and current implementation |
| --- | --- |
| FR-01 | Read attached scalar, 3D NRRD (`NRRD0001`–`0005`), x-fastest data storage. |
| FR-02 | Support unsigned 8-, 16- and 32-bit integers without loss of bit patterns. Also accept signed 16-bit voxels **only when all samples are nonnegative** (compatibility with the course MRI). |
| FR-03 | Support NRRD `raw`, `gzip` and `gz` encodings. |
| FR-04 | Parse `dimension`, `sizes`, `type`, `encoding`, and `endian` where applicable. Reject inconsistent/unsupported headers, truncated and excessive data. |
| FR-05 | Accept sizes `1 <= sx,sy,sz < 1024`, subject to Java array/memory limits. |
| FR-06 | Read ordered CSV either with 9/12 numeric coordinates per row or with the **exact course header** and a point-count column (`3` or `4`) followed by coordinates; ignore blank lines. |
| FR-07 | Require finite decimal point coordinates in `(x,y,z)` voxel indices. Preserve CSV order. |
| FR-08 | Require 3 points to be a nondegenerate isosceles triangle, or 4 points to be a coplanar isosceles trapezoid with same-direction parallel bases and equal legs; reject other geometries. |
| FR-09 | Report invalid records with filename, physical source-line number and reason; do not silently ignore errors. |
| FR-10 | Produce exactly one sampled `Image2D` per valid plane. |
| FR-11 | Sample by nearest neighbour using `Math.round` after continuous half-open bounds checking; return zero when outside. |
| FR-12 | For triangles, output width `ceil(|BC|)` and height `ceil(|AB|)` (minimum 1); trapezoids use bounding span and common-leg length rounded up. |
| FR-13 | Use the documented pixel-centre in-plane orthogonal mapping; all rows are deterministic for fixed data. |
| FR-14 | Keep voxel loading, CSV parsing, geometry, sampling, image mapping, composition and MP4 encoding as separate modules. |
| FR-15 | Generate H.264 MP4 with one video frame per CSV plane, in order. |
| FR-16 | Use default playback rate 30 FPS, independent of processing throughput. |
| FR-17 | Put varying-size sampled images on one even-dimensioned black canvas, centred and **without scaling**; minimum 128 pixels each dimension for decoder compatibility. |
| FR-18 | Map source intensities to 8-bit grayscale using one global source-volume min/max range; preserve original sampled voxel values in `Image2D`. |
| FR-19 | Validate the plane list before loading/rendering and do not publish a partial MP4 on processing failure. |
| FR-20 | Report file/read/geometry/encode problems and return nonzero process exit status. |

## 3. Input conventions

### NRRD

The `NrrdReader` handles attached payload NRRD with raw and gzip encodings. 16-/32-bit types must declare `endian: little` or `endian: big`. Volume array index:

```text
index(x,y,z) = x + sx * (y + sy*z)
```

Data may be too large for available RAM, despite valid dimension limits. The reader explicitly rejects volumes larger than the supported single Java array bound.

### CSV

**Coordinate-only:** each nonempty record has 9 coordinates (`Ax,Ay,Az,Bx,By,Bz,Cx,Cy,Cz`) or 12 (`A1x,A1y,A1z,A2x,A2y,A2z,Bx,By,Bz,Cx,Cy,Cz`).

**Official spreadsheet:** exact header `Number of Points (3 or 4),x1,y1,z1,...,x4,y4,z4`; each record begins with 3/4, followed by 9/12 coordinates. Blank unused cells for 3-point rows are accepted. Unknown headings and nonblank extra values are rejected.

The full original `samples/ex0-professor-original.csv` is preserved as test data. Some rows are intentionally/inherently invalid under the isosceles constraints and must result in a diagnostic; the valid demo uses `samples/mrhead-demo-60.csv`.

## 4. Geometry/sampling

See [ALGORITHM.md](ALGORITHM.md) for complete derivation. The pixel-centre mapping uses two orthogonal unit axes in the plane, covers the bounding trapezoid, and tests whether a pixel centre lies inside the patch before sampling the NRRD volume. `GeometryValidation.EPS = 1e-6` is used for relative validity tests; `PlaneSampler.PIXEL_EPS = 1e-9` is used around raster interval endpoints. The scanline reference algorithm in the handout is **not followed pixel-for-pixel**; this is an explicit design alternative and an assessment caveat.

## 5. Output, failure and security constraints

- MP4 codec: H.264 via JCodec 0.2.5, default AWT pipeline.
- Destination must be `.mp4` and distinct from the input paths.
- Validation failures leave an existing output undisturbed. The program stages the video before publication.
- Maximum slice and video canvas area: 25,000,000 pixels. Video canvas dimensions even and at least 128×128.
- Incomplete/empty frame list must not result in a published partial MP4.
- The experimental Direct RGB option is not the default and has no promised speed-up.

## 6. Nonfunctional requirements

| ID | Requirement/status |
| --- | --- |
| NFR-01 | **Required:** more than 30 processing slices per second on 500³-byte input; **expected:** 100+ slices/s. Measured projection-only **300.4 FPS** on 120 500×500 frames. |
| NFR-02 | Separate measurement of projection, grayscale conversion, frame composition, codec, source loading and end-to-end time. Achieved in `FullPipelineBenchmark`/`Main`; see [EXPERIMENT.md](EXPERIMENT.md). |
| NFR-03 | Object-oriented separation, primitive contiguous memory, no per-pixel 3D object allocation. |
| NFR-04 | JUnit tests cover positive and negative cases, geometry, NRRD/CSV parsing, video frame count and ordering, and course-file compatibility. |
| NFR-05 | Repository uses meaningful Git commits; Stage 6 changes and final documents must be committed and pushed before submission. |

**FPS interpretation caveat:** the handout does not explicitly say whether MP4 encoding is included in the 30/100 FPS limits. The projection-only figure clears the stated targets, while full synthetic MP4 processing was **19.1 frames/s** in one run. If full-pipeline FPS is required, further optimization or a lecturer clarification is necessary.

## 7. Submission deliverables (assignment To Do 1–8)

1. Repository and README defining the problem/scope.
2. SRS (this document).
3. Detailed algorithm ([ALGORITHM.md](ALGORITHM.md)).
4. NRRD dataset and documentation ([DATASET.md](DATASET.md), `samples/`).
5. Testing strategy ([TESTING.md](TESTING.md), `src/test/`).
6. Implementation with incremental Git commits (`src/main/`).
7. Executable Java `Main` using NRRD/CSV/MP4 positional arguments (IntelliJ or `run-demo.cmd`).
8. Experiment description ([EXPERIMENT.md](EXPERIMENT.md)) and corresponding MP4 files (`results/`).

## 8. Known exclusions / external acceptance questions

- Detached `.nhdr` payloads, nonzero byte/line skip, floating-point NRRD types, negative signed 16-bit voxels and general polygons are unsupported by design.
- Exact pixel-by-pixel equivalence to the unit-step scanline pseudocode has **not** been established; seek approval if the instructor expects literal output.
- Whether the instructor requires one particular *Python-style* command spelling despite expressly permitting Java is not stated. The shipped Java equivalent accepts the same three file paths.
- The lecturer's unmodified CSV contains invalid geometry; rejecting it is intentional input validation, not a parsing error.

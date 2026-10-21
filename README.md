
# Software Engineering Ex1 — 3D Volume Plane Projection

## Overview

This project is Exercise 1 for the course **Introduction to Software Engineering (OOP/OOD)**.

The goal is to design and implement an efficient and reliable algorithm for extracting 2D images from 3D volumetric data.

The project emphasizes software design, object-oriented programming, correctness, testing, and runtime efficiency.

## Problem Definition

Given:

- A 3D volume `M` of dimensions `sx × sy × sz`.
- Each voxel contains an unsigned 8-bit, 16-bit, or 32-bit integer.
- A plane defined by three or four 3D points.

The system computes a 2D image representing a slice of the volume along the specified plane.

Supported geometric shapes:

- Isosceles triangles (3 points).
- Isosceles trapezoids (4 points).

Points outside the volume are assigned a value of zero.

## Project Scope

### Functional Requirements

1. Read 3D volumetric data from NRRD files.
2. Read plane definitions from CSV files.
3. Validate input data and plane geometry.
4. Generate a 2D image for each plane.
5. Support unsigned 8-bit, 16-bit, and 32-bit values.
6. Generate an MP4 video with one frame per plane.
7. Preserve the order of planes in the CSV file.

### Performance Requirements

For a volume of size `500 × 500 × 500` bytes:

- Minimum required performance: **30+ FPS**.
- Expected performance: **100+ FPS**.
- Optimize processing time and memory usage.

Performance measurements will distinguish between slice generation and video encoding.

## Design Decisions

### Programming Language

Java.

### Volume Representation

The 3D matrix will be represented using a contiguous one-dimensional array.

Depending on the input data type:

- `byte[]` for 8-bit data.
- `short[]` for 16-bit data.
- `int[]` for 32-bit data.

For an x-fastest memory layout, the indexing formula is:

`index = x + sx * (y + sy * z)`

The NRRD reader will verify the relevant data layout before applying this mapping.

### Sampling Method

The initial implementation uses **Nearest Neighbor interpolation**.

Trilinear interpolation may be considered as a future extension to improve image quality.

### Geometric Mapping

The initial design supports isosceles triangles and trapezoids.

The implementation will investigate aspect-ratio-preserving sampling while documenting differences from the reference algorithm supplied in the assignment.

Final coordinate and pixel-boundary conventions will be specified in the algorithm design document.

## Planned Architecture

| Component | Responsibility |
| --- | --- |
| Volume | Abstract 3D volume access |
| ByteVolume | 8-bit volume implementation |
| ShortVolume | 16-bit volume implementation |
| IntVolume | 32-bit volume implementation |
| Point3D | 3D point representation |
| PlaneDefinition | Plane geometry representation |
| VoxelSampler | Sampling strategy interface |
| NearestNeighborSampler | Nearest Neighbor implementation |
| PlaneSampler | Plane projection algorithm |
| Image2D | 2D output image |
| NrrdReader | Read NRRD files |
| CsvPlaneReader | Read CSV plane definitions |
| VideoWriter | Generate MP4 output |
| Main | Application entry point |

## Planned Usage

The final application will accept three arguments:

```text
java -jar target/volume-projection.jar input.nrrd planes.csv output.mp4
```

This is the proposed Java command-line interface. It is not yet implemented.

### Inputs

- `input.nrrd` — 3D volumetric data.
- `planes.csv` — List of planes defined by 3 or 4 points.

### Output

- `output.mp4` — Video containing the generated slices in CSV order.

## Testing Strategy

The project will include:

- Unit tests using JUnit.
- Tests with synthetic 3D volumes.
- Mathematical correctness tests.
- Boundary and invalid-input tests.
- NRRD and CSV integration tests.
- MP4 output verification.
- Runtime performance benchmarks.

Performance experiments will measure FPS on large volumes.

## Dataset

The project will use NRRD datasets from the open-source **3D Slicer** ecosystem.

Small synthetic datasets will also be created to verify algorithm correctness.

The selected datasets and their sources will be documented.

## Project Status

**Current phase: Requirements Analysis and Software Design**

### Progress

- [x] Create Git repository
- [x] Define initial project scope
- [x] Select programming language
- [x] Select volume representation
- [x] Select initial sampling method
- [x] Approve preliminary architecture
- [ ] Complete SRS document
- [ ] Finalize algorithm design
- [ ] Define datasets
- [ ] Implement Java classes
- [ ] Implement projection algorithm
- [ ] Implement NRRD and CSV readers
- [ ] Implement MP4 generation
- [ ] Complete automated testing
- [ ] Optimize performance
- [ ] Perform experiment and document results

## Deliverables

1. Git repository and README.
2. Software Requirements Specification (SRS).
3. Detailed algorithm design.
4. Documented NRRD datasets.
5. Testing strategy and automated tests.
6. Java implementation with Git history.
7. Command-line demonstration.
8. Experimental report and generated MP4 file.

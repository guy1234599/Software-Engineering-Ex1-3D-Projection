# Algorithm Design — 3D Volume Plane Projection (Ex1)

**Status:** Reviewed design (2026-10-21); numerical examples checked independently. Implementation, performance measurements, and compatibility confirmation with the assignment reference remain pending.

## 1. Purpose and scope

Given a 3D unsigned-integer volume `M` and a plane patch defined by an isosceles triangle (3 points) or an isosceles trapezoid (4 points), construct a 2D raster slice. The output preserves geometric scale in **voxel-index coordinates**; it does not claim to preserve millimeter distances when NRRD voxel spacing is anisotropic.

This design refines the assignment's illustrative pseudocode. It retains the assignment's triangle output dimensions (up to integer rounding), but replaces unit steps along the legs with pixel-center sampling on an orthogonal basis. The difference is intentional and must be documented in the experiment.

## 2. Inputs and outputs

- `Volume`: sizes `sx,sy,sz`, supported unsigned data types 8/16/32 bits, accessed through `getVoxel(x,y,z)`.
- `PlaneDefinition`: `Triangle(A,B,C)` with `|A-B| ≈ |A-C|`, or `Trapezoid(A1,A2,B,C)` with parallel bases and equal-length legs.
- `Image2D`: unsigned values in a compact `int[]` bit-pattern representation, initialized to 0. Rendering to 8-bit grayscale is performed by a separate component.
- For a triangle, `W = max(1,ceil(|C-B|))`, `H = max(1,ceil(|B-A|))`. For a trapezoid, width additionally covers any horizontal offset between bases; height is the rounded-up common leg length.

### Canonical geometry representation

Use upper base endpoints `U0,U1` and lower base endpoints `L0,L1`:

- **Triangle:** `U0 = U1 = A`, `L0 = B`, `L1 = C`.
- **Trapezoid:** `U0 = A1`, `U1 = A2`, `L0 = B`, `L1 = C`.

The endpoints are ordered consistently from left to right along their bases. They must describe a nondegenerate planar patch. A triangle's upper base has length zero.

## 3. Geometry and pixel mapping

Let

- `w0 = |U1-U0|`, `w1 = |L1-L0|` (require `w1 > 0`),
- `u = (L1-L0)/w1`, a unit vector along the bases,
- `m0 = (U0+U1)/2`, `m1 = (L0+L1)/2`, `q=m1-m0`,
- `d = dot(q,u)`, the horizontal displacement of the lower-base center,
- `qPerp = q - d*u`, `h = |qPerp|`, `v=qPerp/h`, the perpendicular unit vector toward the lower base,
- `D = |L0-U0| ≈ |L1-U1|`, the common leg length.

For a standard isosceles triangle or a non-parallelogram isosceles trapezoid, `d ≈ 0`. Supporting `d != 0` also handles the equal-width, shifted-base case without clipping.

The extreme horizontal coordinates, measured along `u` from `m0`, are:

```
xMin = min(-w0/2, d-w1/2)
xMax = max( w0/2, d+w1/2)
span = xMax-xMin
W = max(1,ceil(span))
H = max(1,ceil(D))
padX = (W-span)/2
padY = (H-h)/2
```

For the pixel center `(x,y)`, with `0 <= x < W` and `0 <= y < H`:

```
s = xMin + x + 0.5 - padX
t = y + 0.5 - padY
```

A row is valid only if `0 <= t <= h`. At height `t`, the cross-section has

```
center(t) = d*t/h
width(t)  = w0 + (w1-w0)*t/h
```

A pixel lies inside the plane patch if `abs(s-center(t)) <= width(t)/2` (within a documented numerical tolerance). Its 3D location is

```
P(x,y) = m0 + t*v + s*u
```

`u` and `v` are perpendicular unit vectors in voxel-index coordinates; therefore moving one pixel vertically or horizontally corresponds to one voxel-index distance unit. Output background is 0.

**Resolution limitation:** Pixel-center sampling does not guarantee that an apex, edge, or a very thin shape is sampled. A valid tiny triangle may produce a black image at this resolution. A minimum image size of 1×1 prevents zero-sized arrays but does not guarantee a nonzero sample.

## 4. Detailed pseudocode

```text
Algorithm ProjectPlane(Volume M, PlaneDefinition plane):
    ValidateGeometry(plane)               // once, before rendering
    (U0, U1, L0, L1) = CanonicalEndpoints(plane)

    w0 = distance(U0,U1)
    w1 = distance(L0,L1)
    u = (L1-L0)/w1
    m0 = (U0+U1)/2
    m1 = (L0+L1)/2
    q = m1-m0
    d = dot(q,u)
    perpendicular = q-d*u
    h = length(perpendicular)
    v = perpendicular/h
    D = distance(U0,L0)

    xMin = min(-w0/2, d-w1/2)
    xMax = max( w0/2, d+w1/2)
    span = xMax-xMin
    W = max(1, ceil(span))
    H = max(1, ceil(D))
    ValidateOutputAllocation(W,H)
    E = NewImage2D(W,H, fill=0)

    padX = (W-span)/2
    padY = (H-h)/2

    for y = 0 to H-1:
        t = y + 0.5 - padY
        if t < 0 or t > h:
            continue

        rowCenter = d*t/h
        halfWidth = (w0+(w1-w0)*t/h)/2
        left = rowCenter-halfWidth
        right = rowCenter+halfWidth

        // Convert allowed horizontal positions to an index interval.
        first = max(0, ceil(left - xMin - 0.5 + padX - EPS))
        last  = min(W-1, floor(right - xMin - 0.5 + padX + EPS))

        for x = first to last:
            s = xMin + x + 0.5 - padX
            P = m0 + t*v + s*u
            E[x,y] = NearestNeighbor(M,P)

    return E

Algorithm NearestNeighbor(Volume M, Point P):
    // Design choice: validate continuous coordinates before rounding.
    // Assignment coordinates are valid in [0, size) on each axis.
    if not (0 <= P.x < M.sx and
            0 <= P.y < M.sy and
            0 <= P.z < M.sz):
        return 0

    ix = roundToNearest(P.x)
    iy = roundToNearest(P.y)
    iz = roundToNearest(P.z)
    if not (0 <= ix < M.sx and 0 <= iy < M.sy and 0 <= iz < M.sz):
        return 0
    return M.getVoxel(ix,iy,iz)
```

For a Java implementation, `Math.round()` supplies the tie-breaking rule (ties round toward positive infinity). The initial continuous validity check uses the assignment's half-open bounds `[0,sx) × [0,sy) × [0,sz)`. The **second** check rejects any rounded index that falls outside the array. For example, in a 500-wide dimension, `x=499.2` is inside the continuous domain and rounds to voxel 499, whereas `x=499.7` rounds to 500 and returns zero. This is our explicitly documented NN boundary policy; how to treat continuous points close to borders should also be tested. Numerical errors near these limits require a consistent tolerance policy, without accidentally admitting truly out-of-range indices.

## 5. Validation and error policy

The CSV input is validated before producing a video. Reject nonfinite coordinates, incorrect value counts (anything other than 9 or 12), incompatible ordering, zero-length required bases, nonplanar or degenerate patches, unequal legs (beyond tolerance), and nonparallel trapezoid bases. Validate positive height and practical output size before allocation. Use a combined absolute/relative numerical tolerance, defined consistently in implementation and tests.

On invalid input: report filename, CSV line, shape type, explanatory error and (where useful) relevant measured values, then exit nonzero without a partial MP4. A geometrically valid plane may lie outside the volume; this is **not** an input error and gives zero where samples are out of bounds.

## 6. Complexity and performance

For an image of width `W` and height `H`, one projected slice takes `O(W*H)` time in the worst case and `O(W*H)` output space. The geometry setup uses `O(1)` auxiliary space. The input volume occupies `O(sx*sy*sz)` storage; bytes per voxel depend on its unsigned type. Reuse scratch buffers where possible, avoid allocating per-pixel objects, and prefer incremental vector updates in the implementation.

Benchmark projection separately from NRRD loading, grayscale normalization and MP4 encoding. The assignment requires over 30 processing FPS and expects around 100 processing FPS for a 500³-byte volume; video playback FPS is a separate decision (30).

## 7. Preliminary synthetic test vectors

These are manually interpretable examples independently reproduced with a small mathematical implementation on 2026-10-21. They have **not** yet been run as Java JUnit tests. Define an unsigned **16-bit** synthetic volume by `M[x,y,z] = x + 10*y + 100*z`, with dimensions `8×8×8` and all queried samples within range.

### Test T1 — Triangle

```
A=(2,0,0), B=(0,4,0), C=(4,4,0)
W=4, H=5, h=4
Expected rows, top to bottom:
[ 0,  0,  0,  0]
[ 0, 12, 13,  0]
[ 0, 22, 23,  0]
[31, 32, 33, 34]
[41, 42, 43, 44]
```

### Test T2 — Trapezoid

```
A1=(1,0,0), A2=(3,0,0), B=(0,4,0), C=(4,4,0)
W=4, H=5, h=4
Expected rows:
[ 0,  2,  3,  0]
[ 0, 12, 13,  0]
[21, 22, 23, 24]
[31, 32, 33, 34]
[41, 42, 43, 44]
```

### Further automated tests to implement

- A plane fully outside the volume => all-zero image.
- A nonisosceles triangle => validation error.
- A degenerate triangle / zero-height trapezoid => validation error.
- Nonfinite CSV coordinate => line-specific validation error.
- Coordinates close to a voxel boundary and exact `.5` ties => documented NN behavior.
- Very small valid shapes => nonzero dimensions, no out-of-bounds writes.
- Unsigned 8/16/32-bit extremes => preserved bit patterns.
- Cropping and fixed-canvas placement => no stretching and correct CSV frame order.
- End-to-end NRRD RAW/GZIP loading and MP4 output; measure actual processing FPS.

## 8. Review against the assignment (2026-10-21)

| Assignment condition | Review outcome |
| --- | --- |
| 3D scalar raster with unsigned 8/16/32-bit values | Addressed in the volume architecture; implementation pending. |
| Plane described by 3 points forming an isosceles triangle | Supported; degeneracy and numeric tolerances must be tested. |
| Optional 4-point isosceles trapezoid | Supported through two equal-length sides and parallel bases; point ordering/coplanarity validated. |
| Output triangle matrix sized by `Dab × Dbc` | Preserved as `H=ceil(Dab)`, `W=ceil(Dbc)` (integer rounding is our design choice). |
| Sample outside volume becomes zero | Covered by bounds checks before and after NN rounding. |
| Reference unit-step sampling along triangle sides | **Deliberately differs.** Pixel-center orthogonal sampling preserves proportions but produces different pixels. Document and compare against the reference. |
| Minimum 30+ processing FPS; target 100+ on 500³-byte input | Not yet measured; cannot claim compliance before benchmarking. |
| NRRD + CSV inputs, MP4 frame per CSV plane in order | Planned in other components; end-to-end validation pending. |

**Approval caveat:** The source assignment explicitly gives a sampling procedure. The geometry-preserving method is a documented extension/alternative, not a pixel-for-pixel reimplementation of that procedure. If assessment expects exact reference samples, add a compatible reference strategy or obtain lecturer approval for the alternative. Do not silently substitute one for the other.

**Verified synthetic test calculations:** T1 and T2 match the matrices above under pixel-center positioning, Java-compatible half-up rounding for nonnegative ties, and the specified geometric inclusion rule. More tests are needed for tilted planes, translated/rotated geometry, decimals, boundary conditions and tiny patches.

## 9. Open points before implementation

- Numerical tolerance values (`EPS`) and treatment of floating-point values at geometric/volume boundaries, including `x` near `size-0.5`.
- Precise restrictions on NRRD variants (including detached data files).
- Output size limits to protect memory.
- Whether automated assessment requires exact pixel-by-pixel correspondence with the assignment's unit-step reference pseudocode, rather than the documented geometry-preserving variant.

This document is a **design proposal** until these items and the final implementation are verified.

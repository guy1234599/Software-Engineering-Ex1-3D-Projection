# Dataset Definition — NRRD and CSV Inputs

**Purpose:** cover NRRD parsing, hand-verifiable slice correctness, large-volume timing, and a real course-linked MRI demonstration. This fulfils assignment To Do 4.

## Input NRRD files

| File/source | Shape | Scalar type / encoding | Purpose |
| --- | --- | --- | --- |
| `samples/tiny-2x2x2.nrrd` | 2³ | Attached `raw` | Smoke checks and simple expected values. |
| `samples/synthetic-32x32x32.nrrd` | 32³ | Attached unsigned 8-bit `raw` | Reproducible small-slice pipeline tests. |
| `samples/synthetic-32x32x32-gzip.nrrd` | 32³ | Attached unsigned 8-bit `gzip` | Ensure equivalent decoding to raw. |
| `samples/MRHead_1.nrrd` | **256×256×130** | Attached signed `short` (16-bit), little-endian, `gzip`; observed values 0–279 | Real MRI sample linked by the lecturer in the exercise instructions. |
| `FullPipelineBenchmark` generated `target/stage6/phantom-v2-500-uint8.nrrd` | **500³** | Attached unsigned 8-bit `raw` | Main required large-volume performance experiment. Generated automatically, not shipped as a 125 MB input blob. |

The lecturer-provided MRI was obtained from the course link:

`https://drive.google.com/file/d/1cqqOgSf8k4e14K5CBs-V_EvMmATX1K-J/view`

The original MRI contains **nonnegative** signed-short values. `NrrdReader` handles this file only because it validates and rejects negative signed values; it does **not** support arbitrary signed negative MRI samples.

The synthetic large phantom is deliberately illustrative and is **not** real medical imaging. Its generator creates changing ellipsoidal intensity features so consecutive planes are visually distinguishable.

## CSV plane files

- `samples/planes-example.csv`: simple legacy coordinate-only row.
- `samples/planes-mixed-6.csv`: mixed valid triangle/trapezoid cases for synthetic demonstrations.
- `samples/planes-90.csv`: 90 synthetic demo plane definitions.
- `samples/mrhead-demo-60.csv`: **60 valid, parallel trapezoid planes** through the course MRI, progressing in depth. This is the CSV paired with `results/mrhead-demo-60.mp4`.
- `samples/ex0-professor-original.csv`: unmodified, four-row lecturer spreadsheet exported as CSV, with 13-column header and explicit point-count values. Spreadsheet source:

`https://docs.google.com/spreadsheets/d/1Tfy1yMYhAJjRBA8ZpRp_07pZXqGtxWhWoP02yZW1FQE/edit`

### Why the original professor CSV is not used directly for the MRI demonstration

The original spreadsheet contains plane coordinates as high as 500, whereas the example MRI dimensions are only 256×256×130. Moreover, its fourth physical CSV line (third plane row) does **not** define an exact isosceles triangle under the project's geometry validation; subsequent input rows may also be invalid. The loader intentionally stops and identifies the invalid line rather than silently accepting a non-isosceles shape. The supplied `mrhead-demo-60.csv` is a separate **valid demonstration sequence**, not a covert alteration of the original professor spreadsheet.

## Data format and assumptions

The NRRD first axis varies fastest: `index=x+sx(y+sy*z)`. The implementation uses CSV coordinates directly in voxel indices and does not apply physical `space directions`, `space origin`, or resampling for anisotropic spacing. Nearest-neighbour voxel sampling returns zero outside the source volume. Every CSV row is validated before MP4 creation.

## Reproducing the datasets and outputs

1. Run Maven tests to validate fixture parsing.
2. Run `org.example.projection.FullPipelineBenchmark` with `500 120` program arguments to generate/reuse the 500³ phantom in `target/stage6` and emit 120-frame MP4.
3. Run `org.example.projection.Main` with:

   ```text
   samples/MRHead_1.nrrd samples/mrhead-demo-60.csv target/mrhead-demo-60.mp4
   ```

4. Compare outputs with the supplied `results/` MP4s and the measurements in [EXPERIMENT.md](EXPERIMENT.md).

# Final Submission Checklist — Exercise 1

This checklist maps the **eight To Do tasks stated by the lecturer** to real project deliverables. It distinguishes completed work from steps that must be run on your own laptop/GitHub.

| # | Lecturer's task | Deliverable/current state |
| --- | --- | --- |
| 1 | Repository, problem and scope in README | `README.md` updated to reflect the working implementation. Existing Git history contains meaningful incremental Stage 1–5 commits. |
| 2 | Define SRS | `SRS.md` version 1.0, implementation-aligned. |
| 3 | Define detailed algorithm | `ALGORITHM.md` with derivation, pseudocode, sample matrices, complexity, limitations. |
| 4 | Define NRRD dataset | `DATASET.md`, `samples/*.nrrd`, generated 500³ fixture description. |
| 5 | Define testing strategy covering demo | `TESTING.md`, 12 active JUnit classes / 77 passing cases in uploaded reports. |
| 6 | Implement and commit frequently | `src/main/`, `src/test/`; **Stage 6 code remains uncommitted in the uploaded working tree and requires a final commit + push.** |
| 7 | Main demo: input NRRD, CSV, output MP4 | `org.example.projection.Main` and Windows `run-demo.cmd`, using three positional paths. |
| 8 | Experiment, short description, MP4 | `EXPERIMENT.md` plus both `results/*.mp4` files. |

## Verify on Windows before pushing

1. Make a backup of the current project directory, then copy the delivered files into the existing working directory **without replacing the `.git` history**. Do not copy an old `target/` or `.idea/` from an archive.
2. In **IntelliJ IDEA**, open the Maven panel and run **Lifecycle → clean** then **Lifecycle → test**. A fresh run is important because the uploaded ZIP includes a stale historical `Stage6DirectYuvTest` *report* under `target` even though the test source was deleted. Expected current source tests: **77**, all passing.
3. Run the MRI Main configuration, with arguments:

   ```text
   samples/MRHead_1.nrrd samples/mrhead-demo-60.csv target/mrhead-demo-60.mp4
   ```

4. Confirm the generated MP4 opens and shows moving/varied brain slices; compare it to `results/mrhead-demo-60.mp4`.
5. Optionally run `FullPipelineBenchmark 500 120` again to capture current-machine benchmark values. Do not promise a fixed FPS on every run.
6. Check `git status` to ensure only intended source/docs/samples/results are staged. **Remove the accidental files `git` and the file whose name begins `ersGuyProjects...`** from the uploaded working tree; do not stage them.
7. In the IntelliJ terminal (PowerShell), check and commit:

   ```powershell
   git status --short
   git add .gitignore README.md SRS.md DESIGN.md ALGORITHM.md DATASET.md TESTING.md EXPERIMENT.md SUBMISSION_CHECKLIST.md START_HERE.md run-demo.cmd pom.xml src samples results
   git diff --cached --check
   git diff --cached --stat
   git commit -m "docs: finalize projection experiment and submission"
   git push origin main
   git status --short
   ```

   The sample MRI is ~6.9 MB and both videos total ~1.55 MB; check the lecturer's submission rules before committing binaries if they restrict binary files. The packaged `results/` videos are explicitly unignored in `.gitignore` so normal `git add` includes them.

## Remaining instructor-interpretation risks (not concealed)

- **Sampling equivalence:** our pixel-centre orthogonal mapping is not pixel-identical to the lecturer's unit-step reference pseudocode. The assignment notes the algorithm is underspecified, but whether this alternative is acceptable for evaluation remains a question.
- **FPS scope:** projection achieves 300.4 slices/s on the 500³ workload, while end-to-end MP4 generation achieves 19.1 frames/s. If the instructor measures the full pipeline, additional speedup is required to reach the >30 compulsory threshold.
- **Original CSV invalid geometry:** the instructor's sheet has rows rejected by isosceles validation. The separate MRI demo CSV was intentionally generated for a meaningful, valid movie; the original sheet is preserved for audit.
- **Packaging:** Java is permitted, and `Main` accepts the same three inputs/outputs as the illustrated Python command. The ordinary Maven JAR is not an executable fat JAR; for Windows use `run-demo.cmd` or the documented IntelliJ run configuration.

## Submission packet structure

```text
README.md
SRS.md
DESIGN.md
ALGORITHM.md
DATASET.md
TESTING.md
EXPERIMENT.md
SUBMISSION_CHECKLIST.md
START_HERE.md
run-demo.cmd
pom.xml
.gitignore
src/main/java/...  src/test/java/...
samples/...         results/mrhead-demo-60.mp4
                    results/pipeline-phantom-v2-smooth-500-120.mp4
```

**Do not submit** generated `target/`, `.idea/`, or `.git` as part of the portable ZIP. Keep `.git` in the existing local repository so you can commit and push changes.

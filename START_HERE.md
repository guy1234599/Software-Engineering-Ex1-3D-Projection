# Start Here — Completed Java Projection Project

This document replaces the obsolete Stage 4 starter note.

1. Read [README.md](README.md) for build instructions and the three-file Java CLI.
2. Run **IntelliJ IDEA → Maven → Lifecycle → clean → test** to verify the 77 active regression tests.
3. Run `org.example.projection.Main` with arguments:

   ```text
   samples/MRHead_1.nrrd samples/mrhead-demo-60.csv target/mrhead-demo-60.mp4
   ```

4. Open `target/mrhead-demo-60.mp4` and compare with the shipped `results/mrhead-demo-60.mp4`.
5. Read [EXPERIMENT.md](EXPERIMENT.md) and [SUBMISSION_CHECKLIST.md](SUBMISSION_CHECKLIST.md) before committing and pushing.

The instructor's original CSV is retained but contains invalid isosceles geometry, so the `mrhead-demo-60.csv` **valid demo sequence** is used for the submission video. The pixel-centre projection mapping is documented and is **not** claimed to be pixel-identical to the handout's unit-step pseudocode.

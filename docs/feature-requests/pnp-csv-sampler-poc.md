# Feature Request: PnP CSV Sampler PoC

## Goal

Build a small Java 21 prototype that reads Pick-and-Place CSV files and produces a structured sample that can be sent to an LLM in a later phase.

The LLM integration is not part of this PoC.

## Problem

PnP machine and CAD exports can contain:

- metadata lines before the table
- different delimiters
- different headers
- different decimal separators
- footer or summary lines
- machine-specific formatting

Before asking an LLM to infer the parsing configuration, the system needs a reliable sampler that extracts representative raw lines from the file.

## Required behavior

The sampler must:

1. Accept a CSV file path.
2. Read the file as raw text.
3. Preserve original line numbers.
4. Preserve original line content exactly.
5. Return:
   - total number of lines
   - first N lines
   - last M lines
6. Output the result as JSON.

## Suggested defaults

- first lines: 80
- last lines: 20
- charset: UTF-8

## Example simple PnP CSV

```csv
# Pick and Place Export
# Source: Example CAD Tool
# Units: mm
Designator,Comment,Footprint,Mid X,Mid Y,Rotation,Layer
R1,10k,0603,12.500,33.100,90,Top
C1,100n,0402,14.800,31.900,180,Top
U1,STM32F103,LQFP-48,40.000,25.000,0,Top
D1,LED RED,0603,8.250,10.500,270,Bottom
```

## Example messy PnP CSV

```csv
Job Name: BOARD-1234
Generated: 2026-06-25
Machine: Example-PnP-9000
Coordinate Unit: millimeter

No.;RefDes;PartNo;Package;X-Pos;Y-Pos;Angle;MountSide;Feeder
1;R1;RES-10K-0603;0603;12,500;33,100;90;T;F01
2;C1;CAP-100N-0402;0402;14,800;31,900;180;T;F02
3;U1;STM32F103C8T6;LQFP-48;40,000;25,000;0;T;TRAY1
4;D1;LED-RED-0603;0603;8,250;10,500;270;B;F08

Total placements: 4
End of file
```

## Expected JSON shape

```json
{
  "totalLines": 8,
  "firstLines": [
    {
      "index": 0,
      "text": "# Pick and Place Export"
    }
  ],
  "lastLines": [
    {
      "index": 7,
      "text": "D1,LED RED,0603,8.250,10.500,270,Bottom"
    }
  ]
}
```

## Out of scope

Do not implement:

- Spring Boot
- REST API
- LLM calls
- RAG
- database
- UI
- CSV parsing
- delimiter inference
- header detection
- column mapping
- unit detection
- fine-tuning

## Acceptance idea

The PoC is acceptable when:

- `mvn test` passes
- the CLI outputs valid JSON for both example files
- sampled line text is identical to original file lines
- line indexes are zero-based
- large files are streamed

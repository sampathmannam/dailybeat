# Training data samples

| File | Purpose |
|------|---------|
| `diary_train.sample.jsonl` | 30 synthetic IPS diary pairs for bootstrapping fine-tune |
| `example_raw_day.txt` | Example `data/raw/` text format for `parse_diaries.py` |

## Build training JSONL

1. Copy your past diaries into `data/raw/` (gitignored) using the format in `example_raw_day.txt`
2. Run:

```bash
python scripts/parse_diaries.py --merge data/samples/diary_train.sample.jsonl
```

Output: `data/diary_train.jsonl` (gitignored — never commit real diaries)

## Regenerate synthetic samples

```bash
python scripts/generate_synthetic_samples.py --count 30
```
